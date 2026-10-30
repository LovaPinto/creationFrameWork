package com.lovapinto;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@MultipartConfig(
        fileSizeThreshold = 1024 * 1024,
        maxFileSize = 1024 * 1024 * 10,
        maxRequestSize = 1024 * 1024 * 15
)
public class FrontServlet extends HttpServlet {

    private static final int MAX_BINDING_DEPTH = 32;
    private static final int MAX_BINDING_PREFIXES = 4;
    private static final String SESSION_COOKIE = "MYSESSIONID";

    private Map<UrlMethod, Method> urlMappings = new HashMap<>();
    private ApplicationContext applicationContext;

    @Override
    public void init() throws ServletException {
        super.init();
        applicationContext = (ApplicationContext) getServletContext().getAttribute("applicationContext");
        rebuildRegistry();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        handleRequest(req, res);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        handleRequest(req, res);
    }

    private void rebuildRegistry() throws ServletException {
        try {
            urlMappings = new HashMap<>();

            for (Class<?> controllerClass : applicationContext.getBeanClasses()) {
                if (controllerClass.isAnnotationPresent(RepositoryAnnotation.class)) {
                    continue;
                }

                Map<UrlMethod, Method> classMappings = getUrlMappings(controllerClass);
                for (Map.Entry<UrlMethod, Method> entry : classMappings.entrySet()) {
                    if (urlMappings.containsKey(entry.getKey())) {
                        Method existing = urlMappings.get(entry.getKey());
                        throw new ServletException(
                                "UrlMapping dupliqué : " + entry.getKey()
                                + " (déjà déclaré dans " + existing.getDeclaringClass().getName()
                                + "." + existing.getName()
                                + ") en conflit avec "
                                + entry.getValue().getDeclaringClass().getName()
                                + "." + entry.getValue().getName());
                    }
                    urlMappings.put(entry.getKey(), entry.getValue());
                }
            }

            getServletContext().setAttribute("urlMappings", urlMappings);
        } catch (ServletException e) {
            getServletContext().setAttribute("initError", e.getMessage());
            throw e;
        } catch (Exception e) {
            throw new ServletException("Erreur initialisation registre URL", e);
        }
    }

    private Map<UrlMethod, Method> getUrlMappings(Class<?> controllerClass) {
        Map<UrlMethod, Method> mappings = new HashMap<>();
        for (Method method : controllerClass.getDeclaredMethods()) {
            UrlMapping mapping = method.getAnnotation(UrlMapping.class);
            if (mapping == null) {
                continue;
            }

            String url = resolveUrl(mapping);
            if (url == null || url.isBlank()) {
                continue;
            }

            UrlMethod key = new UrlMethod(normalizePath(url), resolveHttpMethod(mapping));
            if (mappings.containsKey(key)) {
                Method existing = mappings.get(key);
                throw new RuntimeException(
                        "UrlMapping dupliqué : " + key
                        + " (déjà déclaré dans " + existing.getDeclaringClass().getName()
                        + "." + existing.getName()
                        + ") en conflit avec "
                        + controllerClass.getName() + "." + method.getName());
            }
            mappings.put(key, method);
        }
        return mappings;
    }

    private String resolveUrl(UrlMapping mapping) {
        if (mapping.path() != null && !mapping.path().isBlank()) {
            return mapping.path();
        }
        return mapping.value();
    }

    private String resolveHttpMethod(UrlMapping mapping) {
        if (mapping.method() == null || mapping.method().isBlank()) {
            return "GET";
        }
        return mapping.method().toUpperCase();
    }

    private void handleRequest(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        String error = (String) getServletContext().getAttribute("initError");
        if (error != null) {
            res.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, error);
            return;
        }

        String requestPath = normalizePath(req.getPathInfo());
        if ("/".equals(requestPath)) {
            renderControllerIndex(res);
            return;
        }

        String httpMethod = req.getMethod();
        Method method = getMethodForUrl(requestPath, httpMethod);
        if (method == null) {
            res.sendError(HttpServletResponse.SC_NOT_FOUND, "Aucune route trouvée pour " + requestPath);
            return;
        }

        if (isAuthRequired(method) && !isAuthenticated(method, req)) {
            handleUnauthorized(method, req, res);
            return;
        }

        try {
            Object controller = getControllerInstance(method.getDeclaringClass().getName());
            if (controller == null) {
                res.sendError(HttpServletResponse.SC_NOT_FOUND, "Contrôleur introuvable pour " + requestPath);
                return;
            }

            Object result = method.invoke(controller, buildArguments(method, req, res));
            if (isWebApi(method)) {
                renderJson(res, result);
                return;
            }

            if (result instanceof ModelAndView) {
                renderModelAndView(req, res, (ModelAndView) result);
                return;
            }

            if (result instanceof String) {
                renderView(req, res, (String) result, Map.of());
                return;
            }

            res.sendError(HttpServletResponse.SC_NO_CONTENT);
        } catch (Exception e) {
            throw new ServletException("Erreur invocation de " + method.getName(), e);
        }
    }

    private boolean isWebApi(Method method) {
        return method.isAnnotationPresent(WebApi.class)
                || method.getDeclaringClass().isAnnotationPresent(WebApi.class);
    }
//authentification
    private boolean isAuthRequired(Method method) {
        return method.isAnnotationPresent(Auth.class)
                || method.getDeclaringClass().isAnnotationPresent(Auth.class);
    }

    private Auth resolveAuth(Method method) {
        Auth auth = method.getAnnotation(Auth.class);
        if (auth == null) {
            auth = method.getDeclaringClass().getAnnotation(Auth.class);
        }
        return auth;
    }

    private boolean isAuthenticated(Method method, HttpServletRequest req) {
        Auth auth = resolveAuth(method);
        if (auth == null) {
            return true;
        }

        MySession session = findSession(req);
        if (session == null) {
            return false;
        }

        String attribute = auth.value() == null || auth.value().isBlank() ? "user" : auth.value();
        try {
            return session.getAttribute(attribute) != null;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private MySession findSession(HttpServletRequest req) {
        SessionManager sessionManager =
                (SessionManager) getServletContext().getAttribute("sessionManager");
        if (sessionManager == null) {
            return null;
        }

        String sessionId = getSessionId(req);
        if (sessionId == null) {
            return null;
        }

        MySession session = sessionManager.getSession(sessionId);
        if (session == null || !session.isValid()) {
            return null;
        }

        session.updateLastAccessedTime();
        return session;
    }

    private String getSessionId(HttpServletRequest req) {
        Cookie[] cookies = req.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (SESSION_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void handleUnauthorized(Method method, HttpServletRequest req, HttpServletResponse res)
            throws IOException {
        if (isWebApi(method)) {
            res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentification requise");
            return;
        }

        Auth auth = resolveAuth(method);
        String redirect = auth == null ? "" : auth.redirect();
        if (redirect == null || redirect.isBlank()) {
            redirect = getServletContext().getInitParameter("auth-redirect");
        }

        if (redirect == null || redirect.isBlank()) {
            res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentification requise");
            return;
        }

        if (redirect.startsWith("http://") || redirect.startsWith("https://")) {
            res.sendRedirect(redirect);
            return;
        }

        String contextPath = req.getContextPath();
        String target = (contextPath == null ? "" : contextPath)
                + (redirect.startsWith("/") ? redirect : "/" + redirect);
        res.sendRedirect(target);
    }
    //fin authentification

    //json rendering

    private void renderJson(HttpServletResponse res, Object result) throws IOException {
        Object value = result instanceof ModelAndView modelAndView ? modelAndView.getModel() : result;

        res.setContentType("application/json; charset=UTF-8");
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write(JsonSerializer.toJson(value));
    }

    protected Method getMethodForUrl(String path, String httpMethod) {
        if (urlMappings == null) {
            return null;
        }
        return urlMappings.get(new UrlMethod(path, httpMethod));
    }

    protected Map<UrlMethod, Method> getAllMappings() {
        return urlMappings;
    }

    protected Object getControllerInstance(String className) {
        if (applicationContext == null) {
            return null;
        }
        return applicationContext.getBean(className);
    }

    private void renderControllerIndex(HttpServletResponse res) throws IOException {
        List<String> controllers = new ArrayList<>();
        for (Class<?> clazz : applicationContext.getBeanClasses()) {
            if (!clazz.isAnnotationPresent(RepositoryAnnotation.class)) {
                controllers.add(clazz.getSimpleName());
            }
        }

        controllers = controllers.stream()
                .sorted(String::compareToIgnoreCase)
                .collect(Collectors.toList());

        res.setContentType("text/html; charset=UTF-8");
        PrintWriter writer = res.getWriter();
        writer.println("<!DOCTYPE html>");
        writer.println("<html><head><meta charset=\"UTF-8\"><title>Contrôleurs annotés</title></head><body>");
        writer.println("<h1>Liste des contrôleurs @MyController</h1>");
        writer.println("<ul>");
        for (String controller : controllers) {
            writer.println("<li>" + controller + "</li>");
        }
        writer.println("</ul>");
        writer.println("</body></html>");
    }

    private Object[] buildArguments(Method method, HttpServletRequest req, HttpServletResponse res)
            throws ServletException {
        Parameter[] parameters = method.getParameters();
        Object[] arguments = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            arguments[i] = resolveArgument(parameters[i], req, res);
        }
        return arguments;
    }
//binding

    private Object resolveArgument(Parameter parameter, HttpServletRequest req, HttpServletResponse res)
            throws ServletException {
        MyRequestParam requestParam = parameter.getAnnotation(MyRequestParam.class);
        if (requestParam != null) {
            return resolveRequestParam(parameter, requestParam.value(), req);
        }

        Class<?> parameterType = parameter.getType();
        if (HttpServletRequest.class.isAssignableFrom(parameterType)) {
            return req;
        }
        if (HttpServletResponse.class.isAssignableFrom(parameterType)) {
            return res;
        }
        if (Model.class.isAssignableFrom(parameterType)) {
            return new Model();
        }
        if (MySession.class.isAssignableFrom(parameterType)) {
            return resolveSession(req, res);
        }
        if (Map.class.isAssignableFrom(parameterType)) {
            return new LinkedHashMap<String, Object>();
        }
        if (Part.class.isAssignableFrom(parameterType)) {
            return resolvePart(parameter, null, req);
        }
        if (parameterType.getName().equals("com.lovapinto.PartFile") || parameterType.getSimpleName().equals("PartFile")) {
            Part part = resolvePart(parameter, null, req);
            if (part == null) {
                return null;
            }
            try {
                return instantiatePartFile(parameterType, part);
            } catch (ServletException e) {
                throw e;
            } catch (Exception e) {
                throw new ServletException("Erreur lors de la création du PartFile", e);
            }
        }
        return null;
    }

    private MySession resolveSession(HttpServletRequest req, HttpServletResponse res) {
        SessionManager sessionManager =
                (SessionManager) getServletContext().getAttribute("sessionManager");
        if (sessionManager == null) {
            sessionManager = new SessionManager();
            getServletContext().setAttribute("sessionManager", sessionManager);
        }

        String sessionId = getSessionId(req);

        MySession session = sessionId == null ? null : sessionManager.getSession(sessionId);
        if (session == null || !session.isValid()) {
            session = sessionManager.createSession();

            Cookie cookie = new Cookie(SESSION_COOKIE, session.getId());
            cookie.setHttpOnly(true);
            String path = req.getContextPath();
            cookie.setPath(path == null || path.isEmpty() ? "/" : path);
            res.addCookie(cookie);
        } else {
            session.updateLastAccessedTime();
        }

        return session;
    }

    private Object resolveRequestParam(Parameter parameter, String name, HttpServletRequest req)
            throws ServletException {
        Class<?> targetType = parameter.getType();

        if (isFileType(targetType)) {
            Part part = resolvePart(parameter, name, req);
            if (part == null) {
                throw new ServletException("Fichier manquant : " + name + describe(parameter));
            }
            if (Part.class.isAssignableFrom(targetType)) {
                return part;
            }
            try {
                return instantiatePartFile(targetType, part);
            } catch (ServletException e) {
                throw e;
            } catch (Exception e) {
                throw new ServletException("Erreur lors de la création du PartFile", e);
            }
        }

        if (isSimpleType(targetType)) {
            String value = req.getParameter(name);
            if (value == null) {
                throw new ServletException("Paramètre manquant : " + name + describe(parameter));
            }
            return convertParamValue(value, targetType, name);
        }

        return bindObject(targetType, new LinkedHashSet<>(List.of(name)), req, parameter, 0);
    }
//simple type binding

    private boolean isSimpleType(Class<?> type) {
        return type == String.class
                || type == CharSequence.class
                || type == Object.class
                || type.isPrimitive()
                || type.isEnum()
                || type == Boolean.class
                || type == Character.class
                || Number.class.isAssignableFrom(type);
    }
//object binding
    private Object bindObject(Class<?> targetType, Set<String> prefixes, HttpServletRequest req, Parameter parameter,
            int depth) throws ServletException {
        if (depth > MAX_BINDING_DEPTH) {
            throw new ServletException("Paramètre " + prefixes.iterator().next()
                    + " : imbrication d'objets trop profonde (max " + MAX_BINDING_DEPTH + ")" + describe(parameter));
        }

        Object instance = instantiate(targetType, prefixes.iterator().next(), parameter);

        for (Field field : targetType.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers) || field.isSynthetic()) {
                continue;
            }

            String fieldName = field.getName();
            Set<String> candidates = new LinkedHashSet<>();
            for (String prefix : prefixes) {
                candidates.add(prefix + "." + fieldName);
            }
            candidates.add(fieldName);

            String value = null;
            String label = candidates.iterator().next();
            for (String candidate : candidates) {
                value = req.getParameter(candidate);
                if (value != null) {
                    label = candidate;
                    break;
                }
            }

            if (value != null) {
                setField(field, instance, convertFieldValue(value, field.getType(), label), label);
            } else if (!isSimpleType(field.getType()) && hasDefaultConstructor(field.getType())) {
                setField(field, instance, bindObject(field.getType(), trim(candidates), req, parameter, depth + 1),
                        label);
            }
        }
        return instance;
    }

    private Set<String> trim(Set<String> candidates) {
        Set<String> kept = new LinkedHashSet<>();
        for (String candidate : candidates) {
            if (kept.size() >= MAX_BINDING_PREFIXES) {
                break;
            }
            kept.add(candidate);
        }
        return kept;
    }

    private void setField(Field field, Object instance, Object value, String label) throws ServletException {
        try {
            field.setAccessible(true);
            field.set(instance, value);
        } catch (IllegalAccessException e) {
            throw new ServletException("Paramètre " + label + " : champ inaccessible " + field.getName(), e);
        }
    }

    private boolean isFileType(Class<?> type) {
        if (type == null) {
            return false;
        }
        if (Part.class.isAssignableFrom(type)) {
            return true;
        }
        if (type.getName().equals("com.lovapinto.PartFile") || type.getSimpleName().equals("PartFile")) {
            return true;
        }
        return false;
    }

    private Part resolvePart(Parameter parameter, String name, HttpServletRequest req) throws ServletException {
        if (req == null) {
            return null;
        }
        String contentType = req.getContentType();
        if (contentType == null || !contentType.toLowerCase().contains("multipart/")) {
            return null;
        }
        try {
            if (name == null || name.isBlank()) {
                Collection<Part> parts = req.getParts();
                if (parts == null || parts.isEmpty()) {
                    return null;
                }
                for (Part part : parts) {
                    if (part.getSubmittedFileName() != null) {
                        return part;
                    }
                }
                return parts.iterator().next();
            }
            Part part = req.getPart(name);
            if (part != null && part.getSubmittedFileName() != null && !part.getSubmittedFileName().isBlank()) {
                return part;
            }
            if (part != null) {
                if (part.getSize() > 0) {
                    return part;
                }
                return null;
            }
            Collection<Part> parts = req.getParts();
            if (parts == null) {
                return null;
            }
            for (Part p : parts) {
                String fieldName = p.getName();
                if (name.equals(fieldName)) {
                    if (p.getSubmittedFileName() != null && !p.getSubmittedFileName().isBlank()) {
                        return p;
                    }
                    if (p.getSize() > 0) {
                        return p;
                    }
                }
            }
            for (Part p : parts) {
                String fieldName = p.getName();
                if (fieldName != null && fieldName.equals(name + ".file")) {
                    if (p.getSubmittedFileName() != null && !p.getSubmittedFileName().isBlank()) {
                        return p;
                    }
                    if (p.getSize() > 0) {
                        return p;
                    }
                }
            }
            return null;
        } catch (Exception e) {
            throw new ServletException("Erreur lors de la récupération du fichier", e);
        }
    }

    private Object instantiatePartFile(Class<?> targetType, Part part) throws Exception {
        try {
            Object instance = instantiate(targetType, part.getName(), null);
            Field nameField = findField(targetType, "name", "fileName", "filename");
            if (nameField != null) {
                setFieldIfPossible(nameField, instance, part.getSubmittedFileName());
            }
            Field contentTypeField = findField(targetType, "contentType", "mimeType", "type");
            if (contentTypeField != null) {
                setFieldIfPossible(contentTypeField, instance, part.getContentType());
            }
            Field sizeField = findField(targetType, "size", "length");
            if (sizeField != null) {
                setFieldIfPossible(sizeField, instance, part.getSize());
            }
            Field bytesField = findField(targetType, "bytes", "data", "content");
            if (bytesField != null) {
                byte[] bytes = part.getInputStream().readAllBytes();
                setFieldIfPossible(bytesField, instance, bytes);
            }
            Field inputStreamField = findField(targetType, "inputStream", "stream");
            if (inputStreamField != null) {
                setFieldIfPossible(inputStreamField, instance, part.getInputStream());
            }
            Field partField = findField(targetType, "part", "originalPart");
            if (partField != null) {
                setFieldIfPossible(partField, instance, part);
            }
            return instance;
        } catch (Exception e) {
            throw e;
        }
    }

    private Field findField(Class<?> targetType, String... names) {
        for (String name : names) {
            try {
                Field field = targetType.getDeclaredField(name);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    private void setFieldIfPossible(Field field, Object instance, Object value) {
        try {
            field.setAccessible(true);
            field.set(instance, value);
        } catch (Exception ignored) {
        }
    }
//instantiation et conversion en object apres 
    private Object instantiate(Class<?> targetType, String name, Parameter parameter) throws ServletException {
        try {
            Constructor<?> constructor = targetType.getDeclaredConstructor();
            if (!Modifier.isPublic(constructor.getModifiers())) {
                constructor.setAccessible(true);
            }
            return constructor.newInstance();
        } catch (NoSuchMethodException e) {
            throw new ServletException("Paramètre " + name + " : la classe " + targetType.getName()
                    + " doit avoir un constructeur vide pour etre liée" + describe(parameter), e);
        } catch (Exception e) {
            throw new ServletException("Paramètre " + name + " : impossible d'instancier " + targetType.getName()
                    + describe(parameter), e);
        }
    }

    private boolean hasDefaultConstructor(Class<?> type) {
        try {
            type.getDeclaredConstructor();
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    private Object convertFieldValue(String value, Class<?> fieldType, String label) throws ServletException {
        if (!isSimpleType(fieldType)) {
            throw new ServletException("Paramètre " + label + " : impossible de convertir \"" + value + "\" en "
                    + fieldType.getName() + ". Pour un objet, utilisez un préfixe (ex. " + label + ".*)");
        }
        return convertParamValue(value, fieldType, label);
    }

    private String describe(Parameter parameter) {
        return " (paramètre " + parameter.getName()
                + " de " + parameter.getDeclaringExecutable().getDeclaringClass().getName()
                + "." + parameter.getDeclaringExecutable().getName() + ")";
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private Object convertParamValue(String value, Class<?> targetType, String name) throws ServletException {
        String raw = value.trim();

        try {
            if (targetType == String.class || targetType == CharSequence.class || targetType == Object.class) {
                return value;
            }
            if (targetType == boolean.class || targetType == Boolean.class) {
                return parseBoolean(raw);
            }
            if (targetType == char.class || targetType == Character.class) {
                if (raw.length() != 1) {
                    throw new ServletException("Paramètre " + name + " : un seul caractère est attendu, reçu \""
                            + value + "\"");
                }
                return raw.charAt(0);
            }
            if (targetType == int.class || targetType == Integer.class) {
                return (int) checkRange(Long.parseLong(raw), Integer.MIN_VALUE, Integer.MAX_VALUE, name, value);
            }
            if (targetType == long.class || targetType == Long.class) {
                return Long.parseLong(raw);
            }
            if (targetType == short.class || targetType == Short.class) {
                return (short) checkRange(Long.parseLong(raw), Short.MIN_VALUE, Short.MAX_VALUE, name, value);
            }
            if (targetType == byte.class || targetType == Byte.class) {
                return (byte) checkRange(Long.parseLong(raw), Byte.MIN_VALUE, Byte.MAX_VALUE, name, value);
            }
            if (targetType == double.class || targetType == Double.class) {
                return Double.parseDouble(raw);
            }
            if (targetType == float.class || targetType == Float.class) {
                return Float.parseFloat(raw);
            }
            if (targetType == BigInteger.class) {
                return new BigInteger(raw);
            }
            if (targetType == BigDecimal.class) {
                return new BigDecimal(raw);
            }
            if (targetType.isEnum()) {
                return Enum.valueOf((Class<Enum>) targetType, raw.toUpperCase());
            }
        } catch (ServletException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new ServletException("Paramètre " + name + " : impossible de convertir \"" + value
                    + "\" en " + targetType.getSimpleName(), e);
        }

        throw new ServletException("Paramètre " + name + " : type non supporté " + targetType.getName()
                + ". Utilisez @MyRequestParam uniquement avec un type simple ou une énumération.");
    }

    private boolean parseBoolean(String raw) {
        if ("true".equalsIgnoreCase(raw) || "on".equalsIgnoreCase(raw) || "yes".equalsIgnoreCase(raw)
                || "1".equals(raw)) {
            return true;
        }
        return false;
    }

    private long checkRange(long parsed, long min, long max, String name, String value) throws ServletException {
        if (parsed < min || parsed > max) {
            throw new ServletException("Paramètre " + name + " : la valeur \"" + value + "\" est hors limites ("
                    + min + " à " + max + ")");
        }
        return parsed;
    }

    private void renderModelAndView(HttpServletRequest req, HttpServletResponse res, ModelAndView modelAndView)
            throws ServletException, IOException {
        Model model = modelAndView.getModelContainer();
        if (model != null) {
            for (Map.Entry<String, Object> entry : model.asMap().entrySet()) {
                req.setAttribute(entry.getKey(), entry.getValue());
            }
        }
        renderView(req, res, modelAndView.getViewName(), modelAndView.getModel());
    }

    private void renderView(HttpServletRequest req, HttpServletResponse res, String viewName, Map<String, Object> model)
            throws ServletException, IOException {
        for (Map.Entry<String, Object> entry : model.entrySet()) {
            req.setAttribute(entry.getKey(), entry.getValue());
        }

        String prefix = getServletContext().getInitParameter("viewPrefix");
        String suffix = getServletContext().getInitParameter("viewSuffix");
        if (prefix == null) {
            prefix = "/WEB-INF/views/";
        }
        if (suffix == null) {
            suffix = ".jsp";
        }

        RequestDispatcher dispatcher = req.getRequestDispatcher(prefix + viewName + suffix);
        dispatcher.forward(req, res);
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        return path.startsWith("/") ? path : "/" + path;
    }

}
