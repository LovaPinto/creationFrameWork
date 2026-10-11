<%@ page contentType="text/html; charset=UTF-8" language="java" %>
    <!DOCTYPE html>
    <html>

    <head>
        <meta charset="UTF-8">
        <title>Test Binding DTO</title>
    </head>

    <body>
        <h1>Test Binding avec objet User (DTO)</h1>
        <form action="${pageContext.request.contextPath}/controller/test/binding-dto" method="post">
            <label>Nom : <input type="text" name="user.name" required></label><br>
            <label>Age : <input type="number" name="user.age" required></label><br>
            <label>Actif : <input type="checkbox" name="user.active" value="true"></label><br>
            <label>Role :
                <select name="user.role">
                    <option value="ADMIN">ADMIN</option>
                    <option value="USER">USER</option>
                </select>
            </label><br>
            <h3>Adresse</h3>
            <label>Ville : <input type="text" name="user.address.city"></label><br>
            <label>Code Postal : <input type="number" name="user.address.cp"></label><br>
            <button type="submit">Envoyer</button>
        </form>
    </body>

    </html>