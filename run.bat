@echo off
setlocal
cd /d "%~dp0"
title ThreadSeek

where javac >nul 2>nul
if errorlevel 1 (
    echo Java JDK was not found. Install JDK 17 or newer from https://adoptium.net
    echo then open a NEW window and run this file again.
    pause
    exit /b 1
)

if not exist out mkdir out
javac -encoding UTF-8 -cp "lib\mysql-connector-j-26.10.0.jar" -d out src\main\java\*.java src\main\java\model\*.java src\main\java\service\*.java
if errorlevel 1 (
    echo.
    echo Compilation failed. Please check the errors above.
    pause
    exit /b 1
)

if "%THREADSEEK_DB_PASSWORD%"=="" (
    echo.
    set /p THREADSEEK_DB_PASSWORD=Enter your MySQL root password ^(just press ENTER if it has no password^): 
)

cls
java -cp "out;lib\mysql-connector-j-26.10.0.jar" Main
pause
