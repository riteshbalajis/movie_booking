@echo off
REM ===========================================================================
REM  Creates the schema and loads the demo data.
REM
REM  WARNING: this DROPS and recreates the movie_booking database.
REM  Usage:  scripts\setup-db.bat [mysql-user]      (defaults to root)
REM ===========================================================================

setlocal
cd /d "%~dp0\.."

set DBUSER=%1
if "%DBUSER%"=="" set DBUSER=root

echo.
echo   This will DROP and recreate the 'movie_booking' database.
set /p CONFIRM="  Type YES to continue: "
if /i not "%CONFIRM%"=="YES" (
    echo   Cancelled.
    exit /b 0
)

echo   Creating schema...
mysql -u %DBUSER% -p < db\schema.sql
if errorlevel 1 (
    echo   [ERROR] Schema load failed. Is mysql.exe on your PATH?
    exit /b 1
)

echo   Loading demo data...
mysql -u %DBUSER% -p movie_booking < db\seed.sql
if errorlevel 1 (
    echo   [ERROR] Seed load failed.
    exit /b 1
)

echo.
echo   Database ready.
echo.
endlocal
