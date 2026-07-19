package com.lovapinto;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ApplicationContext {

    private final Map<Class<?>, Object> beans = new HashMap<>();
    private final Map<String, Class<?>> beanNames = new HashMap<>();
    private final List<Class<?>> beanClasses = new ArrayList<>();
    private final DatabaseConfig databaseConfig;

    public ApplicationContext(String packagesList, DatabaseConfig databaseConfig) {
        this.databaseConfig = databaseConfig;
        try {
            String[] packages = packagesList.split(",");
            List<Class<?>> allClasses = new ArrayList<>();
            for (String pkg : packages) {
                String trimmed = pkg.trim();
                if (!trimmed.isEmpty()) {
                    allClasses.addAll(scanPackage(trimmed));
                }
            }
            instantiateBeans(allClasses);
            injectDependencies();
        } catch (Exception e) {
            throw new RuntimeException("Erreur initialisation ApplicationContext", e);
        }
    }

    private List<Class<?>> scanPackage(String packageName) throws Exception {
        List<Class<?>> result = new ArrayList<>();
        String path = packageName.replace('.', '/');
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        URL resource = classLoader.getResource(path);
        if (resource == null) {
            return result;
        }

        File directory = new File(resource.toURI());
        File[] files = directory.listFiles();
        if (files == null) {
            return result;
        }

        for (File file : files) {
            if (file.getName().endsWith(".class")) {
                String className = packageName + "." + file.getName().replace(".class", "");
                Class<?> clazz = Class.forName(className);
                if (clazz.isAnnotationPresent(MyController.class)
                        || clazz.isAnnotationPresent(FWController.class)
                        || clazz.isAnnotationPresent(RepositoryAnnotation.class)) {
                    result.add(clazz);
                }
            }
        }
        return result;
    }

    private void instantiateBeans(List<Class<?>> classes) throws Exception {
        for (Class<?> clazz : classes) {
            Constructor<?> constructor = clazz.getDeclaredConstructor();
            if (!Modifier.isPublic(constructor.getModifiers())) {
                constructor.setAccessible(true);
            }
            Object instance = constructor.newInstance();
            beans.put(clazz, instance);
            beanNames.put(clazz.getName(), clazz);
            beanClasses.add(clazz);

            if (databaseConfig != null && clazz.isAnnotationPresent(RepositoryAnnotation.class)) {
                injectConnection(instance);
            }
        }
    }

    private void injectConnection(Object instance) {
        for (Field field : instance.getClass().getDeclaredFields()) {
            if (field.getType().getName().equals("java.sql.Connection")) {
                try {
                    field.setAccessible(true);
                    field.set(instance, databaseConfig.getConnection());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void injectDependencies() {
        for (Object bean : beans.values()) {
            for (Field field : bean.getClass().getDeclaredFields()) {
                if (field.isAnnotationPresent(Autowired.class)) {
                    Object dependency = findBeanByType(field.getType());
                    if (dependency != null) {
                        try {
                            field.setAccessible(true);
                            field.set(bean, dependency);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    private Object findBeanByType(Class<?> type) {
        for (Object bean : beans.values()) {
            if (type.isInstance(bean)) {
                return bean;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public <T> T getBean(Class<T> clazz) {
        Object bean = beans.get(clazz);
        if (bean != null) {
            return (T) bean;
        }
        for (Object instance : beans.values()) {
            if (clazz.isInstance(instance)) {
                return (T) instance;
            }
        }
        return null;
    }

    public Object getBean(String className) {
        Class<?> clazz = beanNames.get(className);
        if (clazz != null) {
            return beans.get(clazz);
        }
        return null;
    }

    public List<Class<?>> getBeanClasses() {
        return beanClasses;
    }

    public Collection<Object> getAllBeans() {
        return beans.values();
    }

    public DatabaseConfig getDatabaseConfig() {
        return databaseConfig;
    }
}
