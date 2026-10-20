@echo off
rem Остановка тестового сервера Report Web App (порт из test-server\application.properties).
setlocal
for /f "tokens=5" %%p in ('netstat -ano ^| findstr /r /c:":8080 .*LISTENING"') do taskkill /F /PID %%p
