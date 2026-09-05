@echo off
REM ===========================================================================
REM  Runs the concurrency test suite.
REM
REM  MySQL must be running and seeded. The server does NOT need to be up:
REM  the tests drive the service layer directly.
REM ===========================================================================

setlocal
cd /d "%~dp0\.."

if not exist "build\test-classes" (
    echo   [ERROR] Tests are not built. Run scripts\build.bat first.
    exit /b 1
)

java -cp "build\classes;build\test-classes;lib\*" com.movie_booking.ConcurrencyTest

endlocal
