## Copile avec le dependance necessaire
javac -cp "C:\apache-tomcat-10.1.55-windows-x64\apache-tomcat-10.1.55\lib\servlet-api.jar" `
-d build\classes `
src\java\com\lovapinto\SampleServlet.java

## pour obtenir le .jar
jar cvf monPointJar.jar build\classes\com\lovapinto\SampleServlet.class