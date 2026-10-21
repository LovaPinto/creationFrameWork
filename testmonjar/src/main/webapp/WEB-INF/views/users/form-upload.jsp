<%@ page contentType="text/html; charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Test Upload Fichier</title>
</head>
<body>
    <h1>Test Upload avec multipart/form-data</h1>
    <form action="${pageContext.request.contextPath}/controller/upload/test" method="post" enctype="multipart/form-data">
        <label>Fichier : <input type="file" name="file" required></label><br>
        <button type="submit">Uploader</button>
    </form>
</body>
</html>
