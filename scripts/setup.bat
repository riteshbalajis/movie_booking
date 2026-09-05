@echo off
REM ===========================================================================
REM  One-command setup.
REM
REM      scripts\setup.bat
REM
REM  Checks prerequisites, creates the database, writes your config, builds,
REM  runs the tests, and offers to start the server.
REM
REM  Optional flags:
REM      scripts\setup.bat --skip-tests      don't run the concurrency suite
REM      scripts\setup.bat --keep-db         don't drop/recreate the database
REM      scripts\setup.bat --yes             don't ask anything (unattended)
REM
REM  To avoid the password prompt entirely, set it first:
REM      set MB_DB_PASSWORD=your_password
REM      scripts\setup.bat --yes
REM ===========================================================================

setlocal EnableDelayedExpansion
cd /d "%~dp0\.."

set "SKIP_TESTS="
set "KEEP_DB="
set "ASSUME_YES="
for %%a in (%*) do (
    if /i "%%a"=="--skip-tests" set "SKIP_TESTS=1"
    if /i "%%a"=="--keep-db"    set "KEEP_DB=1"
    if /i "%%a"=="--yes"        set "ASSUME_YES=1"
)

echo.
echo   ================================================================
echo     Movie Booking System - setup
echo   ================================================================
echo.

REM ---------------------------------------------------------------------------
REM  1/6  Java
REM
REM  We need javac, not just java. A JRE can run Java but cannot compile it,
REM  and that is the single most common thing people get wrong here.
REM ---------------------------------------------------------------------------
echo   [1/6] Checking Java...

where javac >nul 2>&1
if errorlevel 1 (
    echo         javac not found on PATH.
    echo.
    where winget >nul 2>&1
    if errorlevel 1 (
        echo         Install a JDK ^(not a JRE^) from:
        echo           https://adoptium.net/temurin/releases/
        echo         Choose Package Type = JDK, Version = 17, and tick
        echo         "Set JAVA_HOME variable" during installation.
        goto :fail
    )
    if defined ASSUME_YES (
        echo         Running unattended, so nothing will be installed for you.
        echo         Install a JDK first:  winget install EclipseAdoptium.Temurin.17.JDK
        goto :fail
    )

    echo         winget is available. Install Temurin JDK 17 now? [Y/N]
    set /p "INSTALLJDK=        > "
    if /i not "!INSTALLJDK!"=="Y" goto :fail

    echo         Installing ^(this takes a few minutes^)...
    winget install --id EclipseAdoptium.Temurin.17.JDK --silent --accept-source-agreements --accept-package-agreements
    echo.
    echo         JDK installed. Close this window, open a NEW terminal and
    echo         run scripts\setup.bat again.
    echo         ^(PATH is only read when a terminal starts.^)
    goto :end
)

for /f "tokens=*" %%v in ('javac -version 2^>^&1') do set "JAVAC_VER=%%v"
echo         OK  !JAVAC_VER!

REM ---------------------------------------------------------------------------
REM  2/6  MySQL client
REM
REM  Look on PATH first, then in the two default install locations, so this
REM  works even if MySQL was never added to PATH.
REM ---------------------------------------------------------------------------
echo   [2/6] Locating MySQL...

set "MYSQL="
where mysql >nul 2>&1
if not errorlevel 1 set "MYSQL=mysql"

if not defined MYSQL (
    for %%d in (
        "C:\Program Files\MySQL\MySQL Server 8.0\bin"
        "C:\Program Files\MySQL\MySQL Server 8.4\bin"
        "C:\Program Files (x86)\MySQL\MySQL Server 8.0\bin"
    ) do (
        if exist "%%~d\mysql.exe" set "MYSQL=%%~d\mysql.exe"
    )
)

if not defined MYSQL (
    echo         mysql.exe not found.
    echo.
    echo         Install MySQL Server 8 from:
    echo           https://dev.mysql.com/downloads/installer/
    echo         Choose "Server only" and set a root password you will remember.
    echo.
    echo         MySQL Server has to be installed interactively because it asks
    echo         you to choose a root password - that cannot be scripted safely.
    goto :fail
)
echo         OK  found mysql client

REM  Is the service actually running? A stopped service produces a confusing
REM  "communications link failure" later, so catch it now.
sc query MySQL80 2>nul | find "RUNNING" >nul
if errorlevel 1 (
    sc query MySQL84 2>nul | find "RUNNING" >nul
    if errorlevel 1 (
        echo         MySQL service does not appear to be running. Starting it...
        net start MySQL80 >nul 2>&1
        if errorlevel 1 echo         Could not start it automatically - carrying on anyway.
    )
)

REM ---------------------------------------------------------------------------
REM  3/6  Password, and prove it works before touching anything
REM
REM  The password is read without echoing, then written to a temporary option
REM  file rather than passed as -p on the command line. Command-line arguments
REM  are visible to any other process via the task list, so this keeps the
REM  password out of sight.
REM ---------------------------------------------------------------------------
echo   [3/6] MySQL credentials...

set "DBUSER=root"
if defined MB_DB_PASSWORD (
    set "DBPASS=%MB_DB_PASSWORD%"
    echo         Using the password from MB_DB_PASSWORD.
) else (
    REM  Read-Host -AsSecureString keeps the password off the screen, so it is
    REM  not left visible in scrollback or a screen share.
    for /f "usebackq delims=" %%p in (`powershell -NoProfile -Command "$s = Read-Host -AsSecureString 'MySQL root password'; [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($s))"`) do set "DBPASS=%%p"
)

set "MYCNF=%TEMP%\mb-setup-%RANDOM%.cnf"
> "%MYCNF%" echo [client]
>>"%MYCNF%" echo user=%DBUSER%
>>"%MYCNF%" echo password=%DBPASS%

"%MYSQL%" --defaults-extra-file="%MYCNF%" -e "SELECT 1" >nul 2>&1
if errorlevel 1 (
    del "%MYCNF%" 2>nul
    echo         Could not connect to MySQL with that password.
    echo         Check it by running:  mysql -u root -p
    goto :fail
)
echo         OK  connected

REM ---------------------------------------------------------------------------
REM  4/6  Database
REM
REM  schema.sql begins with DROP DATABASE, so confirm before destroying data
REM  that might include bookings someone made.
REM ---------------------------------------------------------------------------
echo   [4/6] Database...

if defined KEEP_DB (
    echo         --keep-db given, leaving the existing database alone.
    goto :afterdb
)

"%MYSQL%" --defaults-extra-file="%MYCNF%" -e "USE movie_booking" >nul 2>&1
if not errorlevel 1 (
    if defined ASSUME_YES (
        set "DROPOK=Y"
    ) else (
        echo         A 'movie_booking' database already exists.
        echo         Recreating it will DELETE all existing bookings. Continue? [Y/N]
        set /p "DROPOK=        > "
    )
    if /i not "!DROPOK!"=="Y" (
        echo         Keeping the existing database.
        goto :afterdb
    )
)

"%MYSQL%" --defaults-extra-file="%MYCNF%" < db\schema.sql
if errorlevel 1 (
    del "%MYCNF%" 2>nul
    echo         Schema creation failed.
    goto :fail
)

"%MYSQL%" --defaults-extra-file="%MYCNF%" movie_booking < db\seed.sql >nul
if errorlevel 1 (
    del "%MYCNF%" 2>nul
    echo         Seed data failed to load.
    goto :fail
)
echo         OK  9 tables created, demo data loaded

:afterdb

REM ---------------------------------------------------------------------------
REM  5/6  Configuration
REM
REM  config\app.properties is git-ignored, so a fresh clone has none. Generate
REM  it from the template with the password just verified, rather than making
REM  the user type it a third time.
REM ---------------------------------------------------------------------------
echo   [5/6] Writing config\app.properties...

if not exist "config" mkdir "config"

set "SRCPROPS=config\app.properties.example"
if exist "config\app.properties" (
    echo         Already exists - updating db.password only.
    set "SRCPROPS=config\app.properties"
)

REM  Rewritten line by line rather than with -replace, because the password is
REM  substituted as a literal. A regex replacement would treat characters like
REM  $ and \ in the password as capture-group and escape syntax and corrupt it.
REM  A properties file also treats \ as an escape, so it is doubled on the way in.
powershell -NoProfile -Command ^
  "$src = $env:SRCPROPS;" ^
  "$pw  = $env:DBPASS -replace '\\','\\\\';" ^
  "$out = Get-Content $src | ForEach-Object { if ($_ -match '^\s*db\.password\s*=') { 'db.password=' + $pw } else { $_ } };" ^
  "Set-Content -Path 'config\app.properties' -Value $out -Encoding ASCII"

if errorlevel 1 (
    echo         Could not write config\app.properties
    goto :fail
)
echo         OK

del "%MYCNF%" 2>nul

REM ---------------------------------------------------------------------------
REM  6/6  Build
REM ---------------------------------------------------------------------------
echo   [6/6] Building...

REM  Target Java 8 bytecode so the result runs on any Java 8 or newer runtime.
REM  Without this a modern JDK emits its own class file version, the build
REM  succeeds, and the app then fails at startup with UnsupportedClassVersionError
REM  if the `java` on PATH is older than the `javac` that built it.
REM  --release exists only on JDK 9+; a JDK 8 already emits Java 8 bytecode.
set "RELEASE=--release 8"
for /f "tokens=2" %%v in ('javac -version 2^>^&1') do (
    echo %%v | findstr /b "1." >nul && set "RELEASE="
)

if exist "build\classes" rmdir /s /q "build\classes"
mkdir "build\classes"
dir /s /b src\main\*.java > build\sources.txt
javac %RELEASE% -encoding UTF-8 -nowarn -d build\classes @build\sources.txt
if errorlevel 1 (
    echo         Compilation failed.
    goto :fail
)

if exist "src\test" (
    if exist "build\test-classes" rmdir /s /q "build\test-classes"
    mkdir "build\test-classes"
    dir /s /b src\test\*.java > build\test-sources.txt
    javac %RELEASE% -encoding UTF-8 -nowarn -cp "build\classes;lib\*" -d build\test-classes @build\test-sources.txt
    if errorlevel 1 (
        echo         Test compilation failed.
        goto :fail
    )
)
echo         OK  compiled

REM ---------------------------------------------------------------------------
REM  Verify it actually works, rather than just claiming success
REM ---------------------------------------------------------------------------
if not defined SKIP_TESTS (
    echo.
    echo   Running the concurrency suite to verify the setup...
    echo.
    java -cp "build\classes;build\test-classes;lib\*" com.movie_booking.ConcurrencyTest
    echo.
    echo   Resetting the demo data the tests just used...
    > "%MYCNF%" echo [client]
    >>"%MYCNF%" echo user=%DBUSER%
    >>"%MYCNF%" echo password=%DBPASS%
    "%MYSQL%" --defaults-extra-file="%MYCNF%" < db\schema.sql >nul 2>&1
    "%MYSQL%" --defaults-extra-file="%MYCNF%" movie_booking < db\seed.sql >nul 2>&1
    del "%MYCNF%" 2>nul
)

echo.
echo   ================================================================
echo     Setup complete.
echo   ================================================================
echo.
echo     Start the server:   scripts\run.bat
echo     Then open:          http://localhost:8080
echo     Admin sign-in:      admin@movie.com / Admin@123
echo.

if defined ASSUME_YES goto :end

set /p "STARTNOW=  Start the server now? [Y/N] > "
if /i "%STARTNOW%"=="Y" (
    echo.
    call scripts\run.bat
)
goto :end

:fail
echo.
echo   Setup did not complete. Fix the item above and run this again.
echo   Full instructions and troubleshooting: SETUP.md
echo.
endlocal
exit /b 1

:end
endlocal
exit /b 0
