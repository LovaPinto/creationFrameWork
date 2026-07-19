@echo off
setlocal
pushd "%~dp0"

set APP_NAME=s5
set SRC_DIR=src\main\java
set WEB_DIR=src\main\webapp
set BUILD_DIR=build
set LIB_DIR=C:\apache-tomcat-10.1.55-windows-x64\apache-tomcat-10.1.55\lib
set TOMCAT_WEBAPPS=C:\apache-tomcat-10.1.55-windows-x64\apache-tomcat-10.1.55\webapps
set SERVLET_API_JAR=%LIB_DIR%\servlet-api.jar
set FRAMEWORK_JAR=..\framework\lib\sprint1.jar

if not exist "%FRAMEWORK_JAR%" (
	set FRAMEWORK_JAR=lib\sprint1.jar
)

if not exist "%FRAMEWORK_JAR%" (
	echo ERREUR: jar du framework introuvable.
	echo Cherche: ..\framework\lib\sprint1.jar ou lib\sprint1.jar
	popd
	endlocal
	exit /b 1
)

set MYSQL_JAR=..\framework\lib\mysql-connector-j-9.5.0.jar
if not exist "%MYSQL_JAR%" (
	set MYSQL_JAR=lib\mysql-connector-j-9.5.0.jar
)

if exist %BUILD_DIR% rmdir /s /q %BUILD_DIR%
mkdir %BUILD_DIR%\WEB-INF\classes
mkdir %BUILD_DIR%\WEB-INF\lib

dir /s /b %SRC_DIR%\*.java > sources.txt
javac -cp "%SERVLET_API_JAR%;%FRAMEWORK_JAR%;%MYSQL_JAR%" -d %BUILD_DIR%\WEB-INF\classes @sources.txt
if errorlevel 1 (
	del sources.txt
	popd
	endlocal
	exit /b 1
)
del sources.txt

xcopy %WEB_DIR% %BUILD_DIR% /E /I /Y >nul
copy /Y "%FRAMEWORK_JAR%" "%BUILD_DIR%\WEB-INF\lib\sprint1.jar" >nul
copy /Y "%MYSQL_JAR%" "%BUILD_DIR%\WEB-INF\lib\mysql-connector-j-9.5.0.jar" >nul

cd %BUILD_DIR%
jar -cvf %APP_NAME%.war *
if errorlevel 1 (
	cd ..
	popd
	endlocal
	exit /b 1
)
cd ..

copy /Y %BUILD_DIR%\%APP_NAME%.war %TOMCAT_WEBAPPS% >nul

echo Deploiement termine. Redemarrez Tomcat si necessaire.
popd
endlocal