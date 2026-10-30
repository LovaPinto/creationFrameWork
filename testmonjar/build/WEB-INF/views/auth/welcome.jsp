<%@ page contentType="text/html; charset=UTF-8" language="java" %>
    <!DOCTYPE html>
    <html>

    <head>
        <meta charset="UTF-8">
        <title>Connecté</title>
    </head>

    <body>
        <h1>Connexion réussie</h1>
        <p>Bienvenue <%= request.getAttribute("user") %></p>
        <ul>
            <li><a href="${pageContext.request.contextPath}/controller/secure">Page protégée @Auth</a></li>
            <li><a href="${pageContext.request.contextPath}/controller/api/secure">API protégée @Auth</a></li>
            <li><a href="${pageContext.request.contextPath}/controller/logout">Se déconnecter</a></li>
        </ul>
    </body>

    </html>
