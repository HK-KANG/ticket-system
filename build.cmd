@echo off
REM ============================================================
REM  Ticket System - Windows-side Maven wrapper
REM  Why: Hermes runs in WSL, but JDK/Maven only exist on Windows.
REM       Calling Windows mvn from WSL needs a long path each time.
REM  Usage (from WSL):
REM    cmd.exe /c "D:\projects\ticket-system\build.cmd -q -DskipTests compile"
REM ============================================================
set "JAVA_HOME=C:\dev\jdk-21.0.2"
set "MAVEN_HOME=C:\dev\apache-maven-3.9.16"
set "PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%"
set "POM=D:\projects\ticket-system\backend\pom.xml"

if not exist "%POM%" (
  echo [build.cmd] ERROR: %POM% not found
  echo [build.cmd] Phase 0 has not generated the backend project yet.
  exit /b 2
)

call "%MAVEN_HOME%\bin\mvn.cmd" -f "%POM%" %*
exit /b %ERRORLEVEL%
