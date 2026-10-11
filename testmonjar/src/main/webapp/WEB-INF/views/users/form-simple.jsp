<%@ page contentType="text/html; charset=UTF-8" language="java" %>
    <!DOCTYPE html>
    <html>

    <head>
        <meta charset="UTF-8">
        <title>Test Binding Simple</title>
    </head>

    <body>
        <h1>Test Binding sans objet DTO</h1>
        <form action="${pageContext.request.contextPath}/controller/test/binding-simple" method="post">
            <label>Nom : <input type="text" name="name" required></label><br>
            <label>Age : <input type="number" name="age" required></label><br>
            <button type="submit">Envoyer</button>
        </form>
    </body>

    </html>