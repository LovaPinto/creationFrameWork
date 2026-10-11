<%@ page contentType="text/html; charset=UTF-8" language="java" %>
    <%@ page import="java.util.List" %>
    <% User user = (User) request.getAttribute("user");
        if (user == null) {
            user = new User();
        }
        %>
        <!DOCTYPE html>
        <html>

        <head>
            <meta charset="UTF-8">
            <title>Utilisateur</title>
        </head>

        <body>
            <h1>Utilisateur</h1>
            <p>Nom : <%= user.getName() %></p>
            <p>Age : <%= user.getAge() %></p>
        </body>

        </html>