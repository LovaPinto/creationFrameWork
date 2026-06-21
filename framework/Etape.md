## Copile avec le dependance necessaire
javac -cp "C:\apache-tomcat-10.1.55-windows-x64\apache-tomcat-10.1.55\lib\servlet-api.jar" `
-d build\classes `
src\java\com\lovapinto\*.java

## pour obtenir le .jar
jar cvf sprint1.jar build\classes\com\lovapinto\*.class


Get-ChildItem -Path src\java -Filter *.java -Recurse |
Select-Object -ExpandProperty FullName > sources.txt

javac -cp "C:\apache-tomcat-10.1.55-windows-x64\apache-tomcat-10.1.55\lib\servlet-api.jar" `
-d build\classes `
@sources.txt