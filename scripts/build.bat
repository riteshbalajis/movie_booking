@echo off
REM ===========================================================================
REM  Compiles the application into build\classes.
REM
REM  Needs a JDK (javac), not just a JRE. Check with:  javac -version
REM ===========================================================================

setlocal
cd /d "%~dp0\.."

echo.
echo   Building Movie Booking System
echo   ------------------------------------------------

REM --- The MySQL driver must be present before we start ---------------------
if not exist "lib\mysql-connector-j-*.jar" (
    echo   [ERROR] No MySQL JDBC driver found in lib\
    echo.
    echo   Download mysql-connector-j-8.4.0.jar from
    echo     https://dev.mysql.com/downloads/connector/j/
    echo   choosing "Platform Independent", and put the .jar in the lib\ folder.
    exit /b 1
)

REM --- Fresh output directory so deleted classes cannot linger --------------
if exist "build\classes" rmdir /s /q "build\classes"
mkdir "build\classes"

REM --- Collect sources ------------------------------------------------------
REM javac takes an @argfile, which avoids the command-line length limit that a
REM project of this size would otherwise hit on Windows.
dir /s /b src\main\*.java > build\sources.txt

echo   Compiling main sources...
javac -encoding UTF-8 -d build\classes @build\sources.txt
if errorlevel 1 (
    echo   [ERROR] Compilation failed.
    exit /b 1
)

REM --- Tests ----------------------------------------------------------------
if exist "src\test" (
    if exist "build\test-classes" rmdir /s /q "build\test-classes"
    mkdir "build\test-classes"
    dir /s /b src\test\*.java > build\test-sources.txt

    echo   Compiling tests...
    javac -encoding UTF-8 -cp "build\classes;lib\*" -d build\test-classes @build\test-sources.txt
    if errorlevel 1 (
        echo   [ERROR] Test compilation failed.
        exit /b 1
    )
)

echo.
echo   Build succeeded.
echo   Next:  scripts\run.bat
echo.
endlocal
