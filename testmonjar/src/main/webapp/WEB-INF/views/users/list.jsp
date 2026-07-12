<%@ page contentType="text/html; charset=UTF-8" language="java" %>
    <%@ page import="java.util.List" %>
        <% List<String> users = (List<String>) request.getAttribute("users");
                if (users == null) {
                users = java.util.Collections.emptyList();
                }
                %>
                <!DOCTYPE html>
                <html>

                <head>
                    <meta charset="UTF-8">
                    <title>Liste des utilisateurs</title>
                </head>

                <body>
                    <h1>Liste des utilisateurs</h1>
                    <ul>
                        <% for (String user : users) { %>
                            <li>
                                <%= user %>
                            </li>
                            <% } %>
                    </ul>
                </body>

                </html>