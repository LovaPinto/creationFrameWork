@echo off
setlocal

set SERVLET_API=C:\apache-tomcat-10.1.55-windows-x64\apache-tomcat-10.1.55\lib\servlet-api.jar

echo =========================
echo CLEAN BUILD
echo =========================

if exist build rmdir /s /q build
mkdir build\classes
mkdir lib

echo =========================
echo 1. COMPILATION FRAMEWORK
echo =========================

dir /s /b src\java\com\lovapinto\*.java > sources_fw.txt

javac -cp "%SERVLET_API%" -d build\classes @sources_fw.txt

if errorlevel 1 (
    echo ERREUR FRAMEWORK
    pause
    exit /b 1
)

del sources_fw.txt

echo =========================
echo 2. CREATION JAR FRAMEWORK
echo =========================

jar cvf lib\sprint1.jar -C build\classes .


if errorlevel 1 (
    echo ERREUR JAR
    pause
    exit /b 1
)

echo =========================
echo 3. COMPILATION APPLICATION
echo =========================

cd ..\testmonjar

if exist build rmdir /s /q build
mkdir build\classes

dir /s /b src\main\java\*.java > sources_app.txt

javac -cp "..\framework\lib\sprint1.jar;%SERVLET_API%" ^
-d build\classes ^
@sources_app.txt

if errorlevel 1 (
    echo ERREUR APPLICATION
    pause
    exit /b 1
)

del sources_app.txt

echo =========================
echo BUILD TERMINE
echo =========================

pause