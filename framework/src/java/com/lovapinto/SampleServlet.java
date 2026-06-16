package com.lovapinto;


import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
     

public class SampleServlet extends HttpServlet {
    public void doGet(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
       String url= processeRequest(req, res);
       res.getWriter().write(url);
    }

    public void doPost(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
            String url= processeRequest(req, res);
            res.getWriter().write(url);
    }

    public static String processeRequest(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
            String url=req.getRequestURI();
            return url;
    }
    
}
