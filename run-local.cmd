@echo off
REM ============================================================
REM  Run the app locally (profile=local, middleware on 127.0.0.1)
REM  Loads secrets from .env, so passwords are not hardcoded here.
REM  Usage: double-click, or from WSL:
REM    cmd.exe /c "D:\projects\ticket-system\run-local.cmd"
REM ============================================================
set "JAVA_HOME=C:\dev\jdk-21.0.2"
set "PATH=%JAVA_HOME%\bin;%PATH%"
cd /d "%~dp0"

if not exist ".env" (
  echo [run-local.cmd] ERROR: .env not found
  exit /b 2
)

for /f "usebackq eol=# tokens=1,* delims==" %%a in (".env") do set "%%a=%%b"

if not exist "backend\target\ticket-system.jar" (
  echo [run-local.cmd] ERROR: jar not found, run build.cmd package first
  exit /b 2
)

echo [run-local.cmd] starting on http://localhost:8080/doc.html
"%JAVA_HOME%\bin\java.exe" -Xms256m -Xmx512m -XX:+UseG1GC ^
  -jar "backend\target\ticket-system.jar" --spring.profiles.active=local
