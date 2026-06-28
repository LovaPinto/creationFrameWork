package com.lovapinto;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.lang.reflect.Method;

public class FrontServlet extends HttpServlet {

    @Override
    public void init() throws ServletException {
        super.init();
        try {
            List<Class<?>> controllers = scanControllers("Controller");
            getServletContext().setAttribute("controllers", controllers);
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        // Récupérer la liste depuis le contexte
        List<Class<?>> controllers = (List<Class<?>>) getServletContext().getAttribute("controllers");

        res.setContentType("text/html");
        PrintWriter out = res.getWriter();
        out.println("<html><body>");

        if (controllers != null) {
            out.println("<h1>Controllers trouvés :</h1>");


            for (Class<?> c : controllers) {
                out.println("<h2>" + c.getName() + "</h2>");

                // 🔹 Ici tu parcours les méthodes du contrôleur
                for (Method method : c.getDeclaredMethods()) {
                    if (method.isAnnotationPresent(MyMethode.class)) {
                        MyMethode ann = method.getAnnotation(MyMethode.class);
                        out.println("<p>Méthode : " + method.getName() +
                                " → path = " + ann.path() + "</p>");
                    }
                }
            }
        } else {
            out.println("<p>Aucun contrôleur trouvé.</p>");
        }
        out.println("</body></html>");
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
        for (File file : directory.listFiles()) {
            if (file.getName().endsWith(".class")) {
                String className = packageName + "." + file.getName().replace(".class", "");
                Class<?> clazz = Class.forName(className);
                if (clazz.isAnnotationPresent(MyController.class)) {
                    controllers.add(clazz);
                }
            }
        }
        return controllers;
    }
   private void scanMethode(String className) throws Exception {
    // Charger la classe par son nom
    Class<?> clazz = Class.forName(className);

    // Parcourir toutes les méthodes déclarées
    for (Method method : clazz.getDeclaredMethods()) {
        // Vérifier si la méthode est annotée avec @MyMethode
        if (method.isAnnotationPresent(MyMethode.class)) {
            // Récupérer l’annotation
            MyMethode ann = method.getAnnotation(MyMethode.class);

            // Afficher les infos
            System.out.println("Classe : " + clazz.getName());
            System.out.println("Méthode : " + method.getName());
            System.out.println("Path : " + ann.path());
        }
    }
}

}
