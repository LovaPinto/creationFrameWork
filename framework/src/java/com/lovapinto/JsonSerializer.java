package com.lovapinto;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class JsonSerializer {

    private static final int MAX_DEPTH = 32;

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private static final ClassValue<Map<String, Accessor>> ACCESSORS = new ClassValue<Map<String, Accessor>>() {
        @Override
        protected Map<String, Accessor> computeValue(Class<?> type) {
            return introspect(type);
        }
    };

    public static String toJson(Object value) {
        StringBuilder out = new StringBuilder();
        write(out, value, Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>()), 0);
        return out.toString();
    }

    private static void write(StringBuilder out, Object value, Set<Object> path, int depth) {
        if (value == null) {
            out.append("null");
            return;
        }
        if (depth > MAX_DEPTH) {
            out.append("null");
            return;
        }

        if (value instanceof String text) {
            writeString(out, text);
            return;
        }
        if (value instanceof Character character) {
            writeString(out, String.valueOf(character));
            return;
        }
        if (value instanceof Boolean flag) {
            out.append(flag.booleanValue() ? "true" : "false");
            return;
        }
        if (value instanceof Enum<?> constant) {
            writeString(out, constant.name());
            return;
        }
        if (value instanceof Number number) {
            writeNumber(out, number);
            return;
        }
        if (value instanceof Optional<?> optional) {
            write(out, optional.orElse(null), path, depth + 1);
            return;
        }
        if (value instanceof Map<?, ?> map) {
            writeMap(out, map, path, depth);
            return;
        }
        if (value instanceof Iterable<?> values) {
            writeCollection(out, values, path, depth);
            return;
        }
        if (value.getClass().isArray()) {
            writeArray(out, value, path, depth);
            return;
        }
        if (isJdkType(value.getClass())) {
            writeString(out, String.valueOf(value));
            return;
        }
        writePojo(out, value, path, depth);
    }

    private static void writeString(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (character < 0x20) {
                        out.append("\\u")
                                .append(HEX[(character >> 12) & 0xF])
                                .append(HEX[(character >> 8) & 0xF])
                                .append(HEX[(character >> 4) & 0xF])
                                .append(HEX[character & 0xF]);
                    } else {
                        out.append(character);
                    }
                }
            }
        }
        out.append('"');
    }

    private static void writeNumber(StringBuilder out, Number number) {
        if (number instanceof Double decimal) {
            out.append(decimal.isNaN() || decimal.isInfinite() ? "null" : decimal.toString());
            return;
        }
        if (number instanceof Float decimal) {
            out.append(decimal.isNaN() || decimal.isInfinite() ? "null" : decimal.toString());
            return;
        }
        out.append(number.toString());
    }

    private static void writeCollection(StringBuilder out, Iterable<?> values, Set<Object> path, int depth) {
        if (!path.add(values)) {
            out.append("null");
            return;
        }
        try {
            out.append('[');
            boolean first = true;
            for (Object item : values) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                write(out, item, path, depth + 1);
            }
            out.append(']');
        } finally {
            path.remove(values);
        }
    }

    private static void writeMap(StringBuilder out, Map<?, ?> map, Set<Object> path, int depth) {
        if (!path.add(map)) {
            out.append("null");
            return;
        }
        try {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                writeString(out, String.valueOf(entry.getKey()));
                out.append(':');
                write(out, entry.getValue(), path, depth + 1);
            }
            out.append('}');
        } finally {
            path.remove(map);
        }
    }

    private static void writeArray(StringBuilder out, Object array, Set<Object> path, int depth) {
        if (!path.add(array)) {
            out.append("null");
            return;
        }
        try {
            out.append('[');
            int length = Array.getLength(array);
            for (int i = 0; i < length; i++) {
                if (i > 0) {
                    out.append(',');
                }
                write(out, Array.get(array, i), path, depth + 1);
            }
            out.append(']');
        } finally {
            path.remove(array);
        }
    }

    private static void writePojo(StringBuilder out, Object value, Set<Object> path, int depth) {
        if (!path.add(value)) {
            out.append("null");
            return;
        }
        try {
            out.append('{');
            boolean first = true;
            for (Map.Entry<String, Accessor> property : accessorsOf(value.getClass()).entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                writeString(out, property.getKey());
                out.append(':');
                write(out, property.getValue().read(value), path, depth + 1);
            }
            out.append('}');
        } finally {
            path.remove(value);
        }
    }

    private static Map<String, Accessor> accessorsOf(Class<?> type) {
        return ACCESSORS.get(type);
    }

    private static Map<String, Accessor> introspect(Class<?> type) {
        Map<String, Accessor> accessors = new LinkedHashMap<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            collectFields(current, accessors);
            collectGetters(current, accessors);
        }
        return accessors;
    }

    private static void collectFields(Class<?> type, Map<String, Accessor> accessors) {
        for (Field field : type.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) || field.isSynthetic()) {
                continue;
            }
            if (accessors.containsKey(field.getName())) {
                continue;
            }
            if (!makeAccessible(field)) {
                continue;
            }
            accessors.put(field.getName(), target -> readField(field, target));
        }
    }

    private static void collectGetters(Class<?> type, Map<String, Accessor> accessors) {
        for (Method method : type.getDeclaredMethods()) {
            if (method.getParameterCount() != 0 || method.getReturnType() == void.class) {
                continue;
            }
            if (Modifier.isStatic(method.getModifiers()) || method.isSynthetic() || method.isBridge()) {
                continue;
            }
            String name = propertyName(method);
            if (name == null) {
                continue;
            }
            if (!makeAccessible(method)) {
                continue;
            }
            accessors.put(name, target -> invokeGetter(method, target));
        }
    }

    private static boolean makeAccessible(AccessibleObject member) {
        try {
            member.setAccessible(true);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static String propertyName(Method method) {
        String name = method.getName();
        if (name.startsWith("get") && name.length() > 3) {
            return decapitalize(name.substring(3));
        }
        if (name.startsWith("is") && name.length() > 2
                && (method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class)) {
            return decapitalize(name.substring(2));
        }
        return null;
    }

    private static String decapitalize(String name) {
        if (name.length() > 1 && Character.isUpperCase(name.charAt(0)) && Character.isUpperCase(name.charAt(1))) {
            return name;
        }
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    private static Object invokeGetter(Method method, Object target) {
        try {
            return method.invoke(target);
        } catch (IllegalAccessException | InvocationTargetException e) {
            return null;
        }
    }

    private static Object readField(Field field, Object target) {
        try {
            return field.get(target);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    private static boolean isJdkType(Class<?> type) {
        String name = type.getName();
        return name.startsWith("java.") || name.startsWith("javax.")
                || name.startsWith("jdk.") || name.startsWith("sun.");
    }

    private interface Accessor {
        Object read(Object target);
    }
}
