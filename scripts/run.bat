@echo off
REM ===========================================================================
REM  Starts the server on http://localhost:8080
REM ===========================================================================

setlocal
cd /d "%~dp0\.."

if not exist "build\classes\com\movie_booking\Main.class" (
    echo   [ERROR] Not built yet. Run scripts\build.bat first.
    exit /b 1
)

if not exist "config\app.properties" (
    echo   [WARN] config\app.properties is missing.
    echo          Copy config\app.properties.example to config\app.properties
    echo          and set your MySQL password, or export DB_PASSWORD.
    echo.
)

java -cp "build\classes;lib\*" com.movie_booking.Main

endlocal
