#!/usr/bin/env bash
# ============================================================================
#  One-command setup (macOS / Linux).
#
#      ./scripts/setup.sh
#
#  Checks prerequisites, creates the database, writes your config, builds,
#  runs the tests, and tells you how to start the server.
#
#  Optional flags:
#      --skip-tests    don't run the concurrency suite
#      --keep-db       don't drop/recreate the database
#      --yes           don't ask anything (unattended)
#
#  To avoid the password prompt entirely:
#      MB_DB_PASSWORD=your_password ./scripts/setup.sh --yes
# ============================================================================

# -e  stop at the first failure instead of ploughing on with a broken state
# -u  treat an unset variable as an error rather than an empty string
# -o pipefail  a failure anywhere in a pipeline fails the pipeline
set -euo pipefail

cd "$(dirname "$0")/.."

SKIP_TESTS=""
KEEP_DB=""
ASSUME_YES=""
for arg in "$@"; do
    case "$arg" in
        --skip-tests) SKIP_TESTS=1 ;;
        --keep-db)    KEEP_DB=1 ;;
        --yes)        ASSUME_YES=1 ;;
        *) echo "Unknown option: $arg"; exit 1 ;;
    esac
done

echo
echo "  ================================================================"
echo "    Movie Booking System - setup"
echo "  ================================================================"
echo

fail() {
    echo
    echo "  Setup did not complete: $1"
    echo "  Full instructions and troubleshooting: SETUP.md"
    echo
    exit 1
}

# ---------------------------------------------------------------------------
#  1/6  Java
#
#  javac, not just java: a JRE can run Java but cannot compile it.
# ---------------------------------------------------------------------------
echo "  [1/6] Checking Java..."

if ! command -v javac >/dev/null 2>&1; then
    echo "        javac not found."
    echo
    if [[ "$OSTYPE" == "darwin"* ]]; then
        echo "        Install it with:  brew install openjdk@17"
    else
        echo "        Install it with:  sudo apt install openjdk-17-jdk"
    fi
    fail "no JDK"
fi
echo "        OK  $(javac -version 2>&1)"

# ---------------------------------------------------------------------------
#  2/6  MySQL client
# ---------------------------------------------------------------------------
echo "  [2/6] Locating MySQL..."

if ! command -v mysql >/dev/null 2>&1; then
    echo "        mysql client not found."
    if [[ "$OSTYPE" == "darwin"* ]]; then
        echo "        Install it with:  brew install mysql && brew services start mysql"
    else
        echo "        Install it with:  sudo apt install mysql-server"
    fi
    fail "no MySQL client"
fi
echo "        OK  found mysql client"

# ---------------------------------------------------------------------------
#  3/6  Password, verified before anything destructive happens
#
#  The password goes into a temporary option file rather than onto the command
#  line, because command-line arguments are visible to any user on the machine
#  through `ps`. The file is created with 0600 and removed on exit.
# ---------------------------------------------------------------------------
echo "  [3/6] MySQL credentials..."

DBUSER="root"
if [[ -n "${MB_DB_PASSWORD:-}" ]]; then
    DBPASS="$MB_DB_PASSWORD"
    echo "        Using the password from MB_DB_PASSWORD."
else
    read -r -s -p "        MySQL root password: " DBPASS
    echo
fi

MYCNF="$(mktemp)"
chmod 600 "$MYCNF"
trap 'rm -f "$MYCNF"' EXIT
printf '[client]\nuser=%s\npassword=%s\n' "$DBUSER" "$DBPASS" > "$MYCNF"

if ! mysql --defaults-extra-file="$MYCNF" -e "SELECT 1" >/dev/null 2>&1; then
    fail "could not connect to MySQL with that password"
fi
echo "        OK  connected"

# ---------------------------------------------------------------------------
#  4/6  Database
# ---------------------------------------------------------------------------
echo "  [4/6] Database..."

if [[ -n "$KEEP_DB" ]]; then
    echo "        --keep-db given, leaving the existing database alone."
else
    RECREATE=1
    if mysql --defaults-extra-file="$MYCNF" -e "USE movie_booking" >/dev/null 2>&1; then
        if [[ -z "$ASSUME_YES" ]]; then
            echo "        A 'movie_booking' database already exists."
            echo "        Recreating it will DELETE all existing bookings."
            read -r -p "        Continue? [y/N] " REPLY
            [[ "$REPLY" =~ ^[Yy]$ ]] || RECREATE=""
        fi
    fi

    if [[ -n "$RECREATE" ]]; then
        mysql --defaults-extra-file="$MYCNF" < db/schema.sql       || fail "schema creation failed"
        mysql --defaults-extra-file="$MYCNF" movie_booking < db/seed.sql >/dev/null \
                                                                    || fail "seed data failed"
        echo "        OK  9 tables created, demo data loaded"
    else
        echo "        Keeping the existing database."
    fi
fi

# ---------------------------------------------------------------------------
#  5/6  Configuration
#
#  config/app.properties is git-ignored, so a fresh clone has none. Generate it
#  from the template using the password just verified.
# ---------------------------------------------------------------------------
echo "  [5/6] Writing config/app.properties..."

mkdir -p config
SRC="config/app.properties.example"
[[ -f config/app.properties ]] && SRC="config/app.properties"

# A properties file treats a backslash as an escape, so double them on the way in.
#
# The value is handed to awk through the environment rather than with -v. Both
# avoid interpolating the password into the program text (where & and / would be
# misread as substitution syntax), but -v additionally expands escape sequences
# in the value it is given - so a password containing \y would silently lose its
# backslash. ENVIRON passes the string through untouched.
PW_ESCAPED="${DBPASS//\\/\\\\}" awk \
    '/^[[:space:]]*db\.password[[:space:]]*=/ { print "db.password=" ENVIRON["PW_ESCAPED"]; next } { print }' \
    "$SRC" > config/app.properties.tmp
mv config/app.properties.tmp config/app.properties
chmod 600 config/app.properties
echo "        OK"

# ---------------------------------------------------------------------------
#  6/6  Build
#
#  Target Java 8 bytecode so the result runs on any Java 8 or newer runtime.
#  Without this a modern JDK emits its own class file version, the build
#  succeeds, and the app then fails at startup with UnsupportedClassVersionError
#  if `java` is older than the `javac` that built it.
#  --release exists only on JDK 9+; a JDK 8 already emits Java 8 bytecode.
# ---------------------------------------------------------------------------
echo "  [6/6] Building..."

RELEASE=(--release 8)
javac -version 2>&1 | grep -q "javac 1\." && RELEASE=()

rm -rf build/classes build/test-classes
mkdir -p build/classes build/test-classes

find src/main -name "*.java" > build/sources.txt
javac "${RELEASE[@]}" -encoding UTF-8 -nowarn -d build/classes @build/sources.txt \
    || fail "compilation failed"

if [[ -d src/test ]]; then
    find src/test -name "*.java" > build/test-sources.txt
    javac "${RELEASE[@]}" -encoding UTF-8 -nowarn -cp "build/classes:lib/*" \
        -d build/test-classes @build/test-sources.txt || fail "test compilation failed"
fi
echo "        OK  compiled"

# ---------------------------------------------------------------------------
#  Verify it actually works rather than just claiming success
# ---------------------------------------------------------------------------
if [[ -z "$SKIP_TESTS" ]]; then
    echo
    echo "  Running the concurrency suite to verify the setup..."
    echo
    java -cp "build/classes:build/test-classes:lib/*" com.movie_booking.ConcurrencyTest

    echo
    echo "  Resetting the demo data the tests just used..."
    mysql --defaults-extra-file="$MYCNF" < db/schema.sql >/dev/null 2>&1
    mysql --defaults-extra-file="$MYCNF" movie_booking < db/seed.sql >/dev/null 2>&1
fi

echo
echo "  ================================================================"
echo "    Setup complete."
echo "  ================================================================"
echo
echo "    Start the server:   java -cp \"build/classes:lib/*\" com.movie_booking.Main"
echo "    Then open:          http://localhost:8080"
echo "    Admin sign-in:      admin@movie.com / Admin@123"
echo
