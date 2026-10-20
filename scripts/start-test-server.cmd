@echo off
rem Локальный тестовый сервер Report Web App (ветка feature/allure-results-native).
rem Данные (H2, аттачи, бэкапы) — в test-server\, лог — test-server\app.log.
setlocal
cd /d "%~dp0.."

if not exist test-server mkdir test-server

set JAR=target\report-web-app-1.0-SNAPSHOT.jar
if not exist "%JAR%" (
    echo Jar не найден: %JAR%
    echo Соберите: mvn package
    exit /b 1
)

rem Java 21: JAVA_HOME, иначе известный локальный JDK
set JAVA_BIN=java
if defined JAVA_HOME set JAVA_BIN=%JAVA_HOME%\bin\java.exe
if not exist "%JAVA_BIN%" set JAVA_BIN=C:\Users\Sergej\.jdks\temurin-21.0.11\bin\java.exe

echo Стартую тестовый сервер на http://localhost:8080 (лог: test-server\app.log) ...
powershell -NoProfile -Command "Start-Process -FilePath '%JAVA_BIN%' -ArgumentList '-jar','%JAR%','--spring.config.additional-location=file:./test-server/application.properties' -WorkingDirectory '%CD%' -WindowStyle Hidden -RedirectStandardOutput 'test-server\app.log' -RedirectStandardError 'test-server\app-err.log'"
echo Останов: scripts\stop-test-server.cmd
