package com.lovapinto;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

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

            String dbUrl = context.getInitParameter("db-url");
            String dbUser = context.getInitParameter("db-user");
            String dbPassword = context.getInitParameter("db-password");

            DatabaseConfig databaseConfig = null;
            if (dbUrl != null && !dbUrl.isBlank()) {
                databaseConfig = new DatabaseConfig(dbUrl, dbUser, dbPassword);
            }

            ApplicationContext applicationContext = new ApplicationContext(packageName, databaseConfig);
            context.setAttribute("applicationContext", applicationContext);
        } catch (Exception e) {
            throw new RuntimeException("Erreur initialisation ApplicationContext", e);
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        ServletContext context = sce.getServletContext();
        Object obj = context.getAttribute("applicationContext");
        if (obj instanceof ApplicationContext) {
            ApplicationContext appCtx = (ApplicationContext) obj;
            if (appCtx.getDatabaseConfig() != null) {
                appCtx.getDatabaseConfig().close();
            }
        }
    }
}
