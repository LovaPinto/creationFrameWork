package com.lovapinto;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class FrontServlet extends HttpServlet {

    private Map<UrlMethod, Method> urlMappings = new HashMap<>();
    private Map<String, Object> controllerInstances = new HashMap<>();

    @Override
    public void init() throws ServletException {
        super.init();
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
            List<Class<?>> controllers = resolveControllers();
            urlMappings = new HashMap<>();
            controllerInstances = new HashMap<>();

            for (Class<?> controllerClass : controllers) {
                controllerInstances.put(controllerClass.getName(), instantiateController(controllerClass));

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

            getServletContext().setAttribute("controllers", controllers);
            getServletContext().setAttribute("urlMappings", urlMappings);
        } catch (ServletException e) {
            getServletContext().setAttribute("initError", e.getMessage());
            throw e;
        } catch (Exception e) {
            throw new ServletException("Erreur initialisation registre URL", e);
        }
    }

    private List<Class<?>> resolveControllers() throws Exception {
        Object storedControllers = getServletContext().getAttribute("controllers");
        if (storedControllers instanceof List<?>) {
            List<Class<?>> controllers = new ArrayList<>();
            for (Object item : (List<?>) storedControllers) {
                if (item instanceof Class<?>) {
                    controllers.add((Class<?>) item);
                }
            }
            if (!controllers.isEmpty()) {
                return controllers;
            }
        }

        String packageName = getServletContext().getInitParameter("controllers-package");
        if (packageName == null || packageName.isBlank()) {
            packageName = "Controller";
        }
        return scanControllers(packageName);
    }

    private List<Class<?>> scanControllers(String packageName) throws Exception {
        List<Class<?>> controllers = new ArrayList<>();
        String path = packageName.replace('.', '/');
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        URL resource = classLoader.getResource(path);
        if (resource == null) {
            return controllers;
        }

        File directory = new File(resource.toURI());
        File[] files = directory.listFiles();
        if (files == null) {
            return controllers;
        }

        for (File file : files) {
            if (file.getName().endsWith(".class")) {
                String className = packageName + "." + file.getName().replace(".class", "");
                Class<?> clazz = Class.forName(className);
                if (clazz.isAnnotationPresent(MyController.class) || clazz.isAnnotationPresent(FWController.class)) {
                    controllers.add(clazz);
                }
            }
        }

        return controllers;
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

        try {
            Object controller = getControllerInstance(method.getDeclaringClass().getName(), httpMethod, requestPath);
            if (controller == null) {
                res.sendError(HttpServletResponse.SC_NOT_FOUND, "Contrôleur introuvable pour " + requestPath);
                return;
            }

            Object result = method.invoke(controller, buildArguments(method, req, res));
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

    protected Method getMethodForUrl(String path, String httpMethod) {
        if (urlMappings == null) {
            return null;
        }
        return urlMappings.get(new UrlMethod(path, httpMethod));
    }

    protected Map<UrlMethod, Method> getAllMappings() {
        return urlMappings;
    }

    protected Object getControllerInstance(String className, String httpMethod, String path) {
        if (controllerInstances == null) {
            return null;
        }
        Method method = getMethodForUrl(path, httpMethod);
        if (method == null) {
            return null;
        }
        return controllerInstances.get(className);
    }

    private void renderControllerIndex(HttpServletResponse res) throws IOException {
        List<String> controllers = new ArrayList<>();
        if (getServletContext().getAttribute("controllers") instanceof List<?>) {
            for (Object item : (List<?>) getServletContext().getAttribute("controllers")) {
                if (item instanceof Class<?>) {
                    controllers.add(((Class<?>) item).getSimpleName());
                }
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

    private Object instantiateController(Class<?> controllerClass) {
        try {
            Constructor<?> constructor = controllerClass.getDeclaredConstructor();
            if (!Modifier.isPublic(constructor.getModifiers())) {
                constructor.setAccessible(true);
            }
            return constructor.newInstance();
        } catch (Exception e) {
            return null;
        }
    }

    private Object[] buildArguments(Method method, HttpServletRequest req, HttpServletResponse res) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        Object[] arguments = new Object[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> parameterType = parameterTypes[i];
            if (HttpServletRequest.class.isAssignableFrom(parameterType)) {
                arguments[i] = req;
            } else if (HttpServletResponse.class.isAssignableFrom(parameterType)) {
                arguments[i] = res;
            } else if (Model.class.isAssignableFrom(parameterType)) {
                arguments[i] = new Model();
            } else if (Map.class.isAssignableFrom(parameterType)) {
                arguments[i] = new Model();
            } else {
                arguments[i] = null;
            }
        }
        return arguments;
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
