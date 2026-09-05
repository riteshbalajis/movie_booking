# Setup Guide — Running CineBook on a New Laptop

Every step from a blank machine to a working application, with the reason for
each one. Follow it in order and it works.

**Time needed:** about 25 minutes, most of it waiting for installers.

---

## Contents

- [What you are installing, and why](#what-you-are-installing-and-why)
- [Step 1 — Install the JDK](#step-1--install-the-jdk)
- [Step 2 — Install MySQL](#step-2--install-mysql)
- [Step 3 — Get the code](#step-3--get-the-code)
- [Step 4 — Create the database](#step-4--create-the-database)
- [Step 5 — Tell the app your password](#step-5--tell-the-app-your-password)
- [Step 6 — Build](#step-6--build)
- [Step 7 — Run](#step-7--run)
- [Step 8 — Use it](#step-8--use-it)
- [Step 9 — Run the concurrency tests](#step-9--run-the-concurrency-tests)
- [Stopping and restarting](#stopping-and-restarting)
- [Troubleshooting](#troubleshooting)
- [All commands on one page](#all-commands-on-one-page)
- [Appendix A — Without the scripts](#appendix-a--without-the-scripts)
- [Appendix B — macOS and Linux](#appendix-b--macos-and-linux)

---

## What you are installing, and why

Only two things. Everything else is already in the repository.

| You install | Why |
|---|---|
| **JDK 17** | To compile and run Java. It must be a **JDK**, not a JRE — a JRE can only *run* Java, it has no `javac` to compile with. |
| **MySQL 8** | The database. All the seat locking that stops two people buying one seat is enforced by MySQL, so the app cannot run without it. |

**You do *not* need** Maven, Gradle, Tomcat, Spring, an IDE, or the MySQL
Connector/J driver. The driver jar is already committed in `lib/`, so there is
nothing else to download.

> **Why no Maven or Tomcat?** The project brief was plain Java with JDBC. The web
> server is `com.sun.net.httpserver`, which ships inside the JDK itself. That
> means the *only* thing on the classpath that is not part of Java is the MySQL
> driver — and that is already in the repo.

---

## Step 1 — Install the JDK

### Download

Go to **<https://adoptium.net/temurin/releases/>** and choose:

| Field | Choose |
|---|---|
| Operating System | Windows |
| Architecture | x64 |
| Package Type | **JDK** ← not JRE |
| Version | **17 (LTS)** |

Download the **`.msi`** installer and run it.

### During installation

On the "Custom Setup" screen, click the dropdown next to **"Set JAVA_HOME
variable"** and choose **"Will be installed on local hard drive"**.

> **Why this matters.** By default the installer skips this. `JAVA_HOME` and the
> `PATH` entry are what let you type `java` and `javac` in any terminal. Without
> them you would have to type the full
> `"C:\Program Files\Eclipse Adoptium\jdk-17\bin\javac.exe"` every time.

### Verify

Open a **new** Command Prompt (`Win + R` → `cmd` → Enter) and run:

```bat
java -version
javac -version
```

**Expected:**

```
openjdk version "17.0.x" ...
javac 17.0.x
```

> ⚠️ **`javac` must work.** If `java` works but `javac` says *"is not recognized"*,
> you installed a JRE instead of a JDK. Go back and download the JDK package.

> **Why a new terminal?** `PATH` is read once when a terminal starts. A window
> that was already open will not see the change.

### Which Java versions work

The code is compiled to target **Java 8**, so it runs on Java 8 or anything
newer. JDK 17 is recommended simply because it is a current LTS release with a
clean installer.

---

## Step 2 — Install MySQL

### Download

Go to **<https://dev.mysql.com/downloads/installer/>** and download
**"Windows (x86, 32-bit), MSI Installer"** — the larger *(mysql-installer-community)*
file. Ignore the 32-bit label; it installs the 64-bit server.

On the download page, click **"No thanks, just start my download"** — an Oracle
account is not required.

### During installation

| Screen | Choose | Why |
|---|---|---|
| Setup Type | **Server only** | You do not need the other tools. Add Workbench later if you want a GUI. |
| Authentication Method | **Use Strong Password Encryption** | The default. The app's driver handles it. |
| Root Password | **Pick one and write it down** | You need it in Step 4 *and* Step 5. Forgetting it means reinstalling. |
| Windows Service | Leave **"Start at System Startup"** ticked | MySQL then runs in the background automatically, so you do not have to start it by hand every time. |

> 📝 **Write your root password somewhere now.** This is the single most common
> place people get stuck.

### Verify the service is running

```bat
sc query MySQL80
```

**Expected:** a line reading `STATE : 4 RUNNING`.

If it says `STOPPED`, start it:

```bat
net start MySQL80
```

### Add MySQL to your PATH

This lets you type `mysql` instead of the full path. Run this **once**, in
Command Prompt:

```bat
setx PATH "%PATH%;C:\Program Files\MySQL\MySQL Server 8.0\bin"
```

Then **close and reopen** the terminal, and check:

```bat
mysql --version
```

**Expected:** `mysql  Ver 8.0.xx for Win64 on x86_64`

> **If you would rather not touch PATH:** skip this and use the **MySQL 8.0
> Command Line Client** from the Start Menu in Step 4 instead. Both work.

---

## Step 3 — Get the code

### Install Git (skip if you already have it)

Download from **<https://git-scm.com/download/win>** and install with all
defaults.

### Clone

```bat
cd %USERPROFILE%\Documents
git clone -b feature/complete-booking-system https://github.com/adithya11sci/movie_booking.git
cd movie_booking
```

> ⚠️ **Read this carefully — it is the easiest thing to get wrong.**
>
> The completed application lives on the **`feature/complete-booking-system`**
> branch of the **`adithya11sci`** fork. That is what `-b` selects above.
>
> Cloning `riteshbalajis/movie_booking` without a branch gives you only the
> original starter code — the DAO and model classes, with no service layer, no
> web server and no front end. It will not run.
>
> **Once pull request #1 is merged**, the code will be on the original repo's
> `main` and you can clone the simple way instead:
> ```bat
> git clone https://github.com/riteshbalajis/movie_booking.git
> ```

### Verify you got the right thing

```bat
dir
```

**Expected:** you should see `PROJECT.md`, `README.md`, `SETUP.md`, and the
folders `db`, `lib`, `scripts`, `src`, `webapp`, `config`.

If you only see `src`, you cloned the wrong branch — delete the folder and redo
the clone command above.

Also confirm the driver came with it:

```bat
dir lib
```

**Expected:** `mysql-connector-j-8.4.0.jar` (about 2.5 MB).

---

## Step 4 — Create the database

This creates the `movie_booking` database, its nine tables, and demo data
(3 theatres, 4 screens, 384 seats, 5 films, 11 shows).

```bat
scripts\setup-db.bat
```

It asks you to type `YES` to confirm, then prompts for your MySQL root password
**twice** — once for the schema, once for the data.

> **Why it asks for confirmation.** The script begins with
> `DROP DATABASE IF EXISTS movie_booking`, so running it again wipes any bookings
> you have made. That is exactly what you want for a clean demo, but not
> something that should happen by accident.

**Expected output at the end:**

```
table_name      rows_inserted
theatres        3
screens         4
seats           384
movies          5
shows           11
show_seats      1056
```

> **What are those 1,056 rows?** One per seat per show — 96 seats × 11 shows.
> This is the heart of the data model: `seats` is the physical chair, and
> `show_seats` is that chair *for one screening*, carrying its own price and
> availability. It is why the same seat can be free at 6pm and sold at 9pm.

### If `setup-db.bat` fails because `mysql` is not found

Use the **MySQL 8.0 Command Line Client** from the Start Menu, enter your
password, then paste these two lines (adjust the path if you cloned elsewhere):

```sql
SOURCE C:/Users/YOUR_NAME/Documents/movie_booking/db/schema.sql;
SOURCE C:/Users/YOUR_NAME/Documents/movie_booking/db/seed.sql;
```

> Use **forward slashes** in `SOURCE`, even on Windows — MySQL treats a backslash
> as an escape character.

---

## Step 5 — Tell the app your password

```bat
copy config\app.properties.example config\app.properties
notepad config\app.properties
```

Find this line:

```properties
db.password=CHANGE_ME
```

Change it to your MySQL root password from Step 2, then **save and close**:

```properties
db.password=your_actual_password
```

> **Why a separate file instead of editing the Java source?**
>
> 1. **The password never enters the repository.** `config/app.properties` is
>    listed in `.gitignore`, so it cannot be committed by accident. Only the
>    `.example` template is tracked, and it contains `CHANGE_ME`.
> 2. **No rebuild to change it.** Configuration is read at startup, not compiled
>    in. The same build runs on your laptop and on a server with different
>    credentials.
>
> The original code had `PASSWORD = "0307"` hard-coded in `DBConnection.java`,
> which meant the real password was visible to anyone who cloned the repo. This
> is the fix for that.

### Alternative: an environment variable

If you would rather not create the file:

```bat
set DB_PASSWORD=your_actual_password
```

This works for the current terminal only. Environment variables take priority
over the file, so it also overrides it if both exist.

---

## Step 6 — Build

```bat
scripts\build.bat
```

**Expected:**

```
  Building Movie Booking System
  ------------------------------------------------
  Compiling main sources...
  Compiling tests...

  Build succeeded.
  Next:  scripts\run.bat
```

> **What just happened.** `javac` compiled 66 `.java` files into `build\classes`,
> then compiled the test into `build\test-classes`.
>
> The script writes the file list to `build\sources.txt` and passes it as
> `@build\sources.txt` rather than listing every file on the command line.
> Windows has a command-line length limit that a project this size would exceed,
> and an *argfile* is the standard way around it.
>
> It also deletes `build\classes` first, so a class you removed from the source
> cannot linger as a stale `.class` file and quietly keep working.

---

## Step 7 — Run

```bat
scripts\run.bat
```

**Expected:**

```
  Movie Booking System
  Java 17.0.x  |  JDBC + MySQL  |  no frameworks
  ------------------------------------------------
[db] Connected to MySQL 8.0.xx
[bootstrap] No administrator existed, so one was created:
            admin@movie.com / Admin@123
            Change this password before deploying anywhere real.
[sweeper] Reclaiming expired holds every 60s.
[server] Listening on http://localhost:8080
[server] Serving pages from ./webapp
[server] Press Ctrl+C to stop.
```

> **Why it checks the database before opening the port.** If your password is
> wrong, you find out here — with a message telling you exactly what to fix —
> instead of getting a blank 500 error after clicking around in the browser.
>
> **What the bootstrap line means.** The sign-up form only ever creates
> customers, so a brand-new database would have nobody who could add a film. The
> app creates one administrator on first start, and only if no admin exists yet.
> On later runs this line does not appear.
>
> **The sweeper** reclaims seats that people held and abandoned.

**Leave this window open.** The server runs until you press `Ctrl + C`.

---

## Step 8 — Use it

Open your browser at **<http://localhost:8080>**

### As a customer

1. Click **Sign up** and create an account (password must be 8+ characters).
2. Pick a film from the grid.
3. Choose a date — **shows before the current time are not bookable**, so if
   today's are all in the past, click tomorrow.
4. Click a showtime to open the seat map.
5. Select seats and click **Hold**. A countdown starts.
6. Click **Pay & confirm**. You land on **My Bookings** with a reference like
   `BK-7QK4T2XM`.

### As an administrator

Sign in with **`admin@movie.com` / `Admin@123`** and open the **Admin** tab to
add films, theatres and screens, or schedule and cancel shows.

### Seeing the concurrency for yourself

This is the part worth demonstrating.

1. Hold three seats as your customer account. Leave the tab open.
2. Open a **private/incognito window** and sign in as a *different* account.
3. Open the same show.

Those three seats now show as **held** in a different colour and cannot be
clicked. Try to take them anyway through the API and you get told exactly which
ones went:

```json
{
  "error": "SEATS_UNAVAILABLE",
  "message": "Seats A2, A3 just got booked by someone else. Please pick another.",
  "unavailableSeats": ["A2", "A3"]
}
```

If you leave the hold and wait 8 minutes without confirming, the seats return to
sale on their own.

---

## Step 9 — Run the concurrency tests

Open a **second** Command Prompt (leave the server running in the first):

```bat
cd %USERPROFILE%\Documents\movie_booking
scripts\test.bat
```

**Expected:**

```
  [1] Same 3 seats, 20 users at once             => PASSED
  [2] 20 users, one distinct seat each           => PASSED
  [3] Overlapping seats, opposite orders         => PASSED
  [4] Same booking confirmed by 10 threads       => PASSED
  [5] Abandoned hold is reclaimed by the sweeper => PASSED

  5 of 5 tests passed.
```

> **The server does not need to be running** — the tests drive the service layer
> directly, which is possible because the business logic has no dependency on
> HTTP.

### The extra proof worth showing

This runs 20 simultaneous bookings against a deliberately starved 4-connection
pool. Before the connection-pool fix in the final commit, it was guaranteed to
fail:

```bat
java -Ddb.pool.size=4 -cp "build\classes;build\test-classes;lib\*" com.movie_booking.ConcurrencyTest
```

Still `5 of 5`.

> **Note:** the tests leave test bookings in the database. To get a clean demo
> afterwards, re-run `scripts\setup-db.bat`.

---

## Stopping and restarting

| To do this | Do this |
|---|---|
| Stop the server | `Ctrl + C` in its window |
| Start it again | `scripts\run.bat` |
| Rebuild after editing code | `scripts\build.bat` then `scripts\run.bat` |
| Reset all data to a clean demo | `scripts\setup-db.bat` |

You only run Steps 1–5 **once**. Day to day it is just `run.bat`.

---

## Troubleshooting

### `'javac' is not recognized as an internal or external command`

You have a JRE, not a JDK, or the terminal predates the install.
**Fix:** open a new terminal. If it still fails, reinstall choosing **JDK** in
Step 1.

### `[db] Cannot reach the database: Access denied for user 'root'@'localhost'`

The password in `config/app.properties` does not match MySQL's.
**Fix:** confirm it by logging in manually:

```bat
mysql -u root -p
```

If that works with a password, put that exact password in the file. Watch for a
trailing space after `db.password=`.

### `[db] Cannot reach the database: Unknown database 'movie_booking'`

Step 4 was skipped or failed. **Fix:** run `scripts\setup-db.bat`.

### `[db] Cannot reach the database: Communications link failure`

MySQL is not running. **Fix:**

```bat
net start MySQL80
```

### `[db] MySQL JDBC driver ... is not on the classpath`

`lib\mysql-connector-j-8.4.0.jar` is missing — usually from downloading the
repo as a ZIP rather than cloning, or from cloning the wrong branch.
**Fix:** `dir lib` to check, and re-clone using the Step 3 command if empty.

### `[server] Could not bind to port 8080: Address already in use`

Something else has the port. **Fix:** run on a different one:

```bat
java -Dserver.port=9090 -cp "build\classes;lib\*" com.movie_booking.Main
```

Then browse to <http://localhost:9090>.

### The page loads but says "Could not reach the server"

The server window has stopped or crashed. Check it for an error and restart with
`scripts\run.bat`.

### There are no showtimes for today

Shows that have already started are correctly hidden. **Fix:** click tomorrow's
date, or re-run `scripts\setup-db.bat` — the demo data is generated relative to
today's date.

### Seats appear stuck as "held"

Someone (possibly you, in another tab) is holding them. They release
automatically after 8 minutes. To clear immediately, re-run
`scripts\setup-db.bat`.

### `Access denied` when running `setx` or `net start`

**Fix:** right-click Command Prompt → **Run as administrator**.

---

## All commands on one page

**One-time setup**

```bat
:: after installing JDK 17 and MySQL 8
cd %USERPROFILE%\Documents
git clone -b feature/complete-booking-system https://github.com/adithya11sci/movie_booking.git
cd movie_booking

scripts\setup-db.bat
copy config\app.properties.example config\app.properties
notepad config\app.properties
:: set db.password, save, close

scripts\build.bat
```

**Every time after that**

```bat
cd %USERPROFILE%\Documents\movie_booking
scripts\run.bat
:: browse to http://localhost:8080
```

**Tests**

```bat
scripts\test.bat
```

---

## Appendix A — Without the scripts

If a `.bat` file is blocked by policy, these are the exact commands it runs.

**Build**

```bat
rmdir /s /q build\classes
mkdir build\classes
dir /s /b src\main\*.java > build\sources.txt
javac -encoding UTF-8 -d build\classes @build\sources.txt

mkdir build\test-classes
dir /s /b src\test\*.java > build\test-sources.txt
javac -encoding UTF-8 -cp "build\classes;lib\*" -d build\test-classes @build\test-sources.txt
```

**Run**

```bat
java -cp "build\classes;lib\*" com.movie_booking.Main
```

**Test**

```bat
java -cp "build\classes;build\test-classes;lib\*" com.movie_booking.ConcurrencyTest
```

**Database**

```bat
mysql -u root -p < db\schema.sql
mysql -u root -p movie_booking < db\seed.sql
```

> **What `-cp` means.** The classpath — where Java looks for compiled code.
> `build\classes` is the application; `lib\*` picks up the MySQL driver jar.
> Windows separates entries with a **semicolon**; macOS and Linux use a colon.

---

## Appendix B — macOS and Linux

The Java and SQL are identical; only the shell syntax differs.

**Install (macOS with Homebrew)**

```bash
brew install openjdk@17 mysql
brew services start mysql
```

**Install (Ubuntu/Debian)**

```bash
sudo apt update
sudo apt install openjdk-17-jdk mysql-server
sudo systemctl start mysql
```

**Everything else**

```bash
git clone -b feature/complete-booking-system https://github.com/adithya11sci/movie_booking.git
cd movie_booking

mysql -u root -p < db/schema.sql
mysql -u root -p movie_booking < db/seed.sql

cp config/app.properties.example config/app.properties
nano config/app.properties          # set db.password

mkdir -p build/classes build/test-classes
find src/main -name "*.java" > build/sources.txt
javac -encoding UTF-8 -d build/classes @build/sources.txt
find src/test -name "*.java" > build/test-sources.txt
javac -encoding UTF-8 -cp "build/classes:lib/*" -d build/test-classes @build/test-sources.txt

java -cp "build/classes:lib/*" com.movie_booking.Main
```

> Note the **colon** in `-cp "build/classes:lib/*"` rather than the semicolon
> Windows uses. This is the single most common mistake when moving between the
> two.

---

## Where to go next

- **[README.md](README.md)** — what the app does and how the project is laid out.
- **[PROJECT.md](PROJECT.md)** — the full design document: architecture, how
  double-booking is prevented, the schema reasoning, security, the two bugs found
  during development, and a question-and-answer section.
