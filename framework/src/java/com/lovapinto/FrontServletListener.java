package com.lovapinto;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

@WebListener
public class FrontServletListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        try {
            ServletContext context = sce.getServletContext();
            String packageName = context.getInitParameter("controllers-package");
            if (packageName == null || packageName.isBlank()) {
                packageName = "Controller";
            }

            List<Class<?>> controllers = scanControllers(packageName);
            context.setAttribute("controllers", controllers);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
}
