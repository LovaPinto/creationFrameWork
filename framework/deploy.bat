@echo off
setlocal

set APP_NAME=testmonjar
set SRC_DIR=src\main\java
set WEB_DIR=src\main\webapp
set BUILD_DIR=build
set LIB_DIR=C:\apache-tomcat-10.1.55-windows-x64\apache-tomcat-10.1.55\lib
set TOMCAT_WEBAPPS=C:\apache-tomcat-10.1.55-windows-x64\apache-tomcat-10.1.55\webapps
set SERVLET_API_JAR=%LIB_DIR%\servlet-api.jar

echo =========================
echo CLEAN BUILD
echo =========================

if exist %BUILD_DIR% rmdir /s /q %BUILD_DIR%
mkdir %BUILD_DIR%\WEB-INF\classes

echo =========================
echo COMPILATION JAVA
echo =========================

dir /s /b %SRC_DIR%\*.java > sources.txt

javac -cp "%SERVLET_API_JAR%" ^
-d %BUILD_DIR%\WEB-INF\classes ^
@sources.txt

if errorlevel 1 (
    echo ERREUR COMPILATION
    pause
    exit /b 1
)

del sources.txt

echo =========================
echo COPIE WEB
echo =========================

xcopy %WEB_DIR% %BUILD_DIR% /E /I /Y

echo =========================
echo CREATION WAR
echo =========================

cd %BUILD_DIR%
jar -cvf %APP_NAME%.war *
cd ..

echo =========================
echo DEPLOIEMENT TOMCAT
echo =========================

copy /Y %BUILD_DIR%\%APP_NAME%.war %TOMCAT_WEBAPPS%

echo =========================
echo DONE
echo =========================

pause