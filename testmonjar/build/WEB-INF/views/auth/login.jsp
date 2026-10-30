<%@ page contentType="text/html; charset=UTF-8" language="java" %>
    <!DOCTYPE html>
    <html>

    <head>
        <meta charset="UTF-8">
        <title>Connexion</title>
    </head>

    <body>
        <h1>Connexion</h1>
        <% Object error=(Object) request.getAttribute("error"); if (error !=null) { %>
            <p style="color: red;"><%= error %></p>
            <% } %>
                <form action="${pageContext.request.contextPath}/controller/login" method="post">
                    <label>Nom d'utilisateur : <input type="text" name="username" required></label><br>
                    <label>Mot de passe : <input type="password" name="password" required></label><br>
                    <button type="submit">Se connecter</button>
                </form>
                <p>Identifiants de test : <strong>admin</strong> / <strong>1234</strong></p>
                <p><a href="${pageContext.request.contextPath}/controller/secure">Tester une page protégée</a></p>
    </body>

    </html>
