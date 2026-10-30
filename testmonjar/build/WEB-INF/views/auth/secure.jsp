<%@ page contentType="text/html; charset=UTF-8" language="java" %>
    <!DOCTYPE html>
    <html>

    <head>
        <meta charset="UTF-8">
        <title>Page protégée</title>
    </head>

    <body>
        <h1>Page protégée</h1>
        <p>Cette méthode est annotée <code>@Auth</code> et n'est accessible qu'à un utilisateur connecté.</p>
        <p>Utilisateur : <%= request.getAttribute("user") %></p>
        <p>Rôle : <%= request.getAttribute("role") %></p>
        <p><a href="${pageContext.request.contextPath}/controller/logout">Se déconnecter</a></p>
    </body>

    </html>
