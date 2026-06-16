package main.java;

import java.io.IOException;
import java.io.PrintWriter;

public class TestWeb {
     public void doGet(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
            String url= SampleServlet.
        res.setContentType("text/plain");
        PrintWriter out = res.getWriter();
        out.println("URL complète : " + req.getRequestURL());
    }
}
