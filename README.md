# CineBook — Movie Ticket Booking System

A complete movie ticket booking application in **plain Java 8**, with **JDBC** to
MySQL and an **HTML/CSS/JavaScript** front end. No Spring, no Hibernate, no
Tomcat, no Maven — the only thing on the classpath that is not the JDK is the
MySQL driver.

Its real subject is **concurrency**: what happens when twenty people go for the
same seat at the same instant, and how the system guarantees exactly one of them
gets it.

> **Setting this up on a fresh machine?** Follow **[SETUP.md](SETUP.md)** instead
> of the quick start below — it covers everything from installing the JDK and
> MySQL through to the first booking, with the reason for each step and a
> troubleshooting section.
>
> For the full design walkthrough — architecture, the locking protocol, schema
> notes, API reference and the reasoning behind each decision — see
> **[PROJECT.md](PROJECT.md)**.

---

## Quick start

*(Already have a JDK and MySQL? Start here. Otherwise use
[SETUP.md](SETUP.md).)*

### 1. Prerequisites

| Need          | Version | Check with       |
|---------------|---------|------------------|
| JDK (not JRE) | 8+      | `javac -version` |
| MySQL Server  | 8.0+    | `mysql --version`|

### 2. Add the MySQL driver

Download `mysql-connector-j-8.4.0.jar` from
<https://dev.mysql.com/downloads/connector/j/> (choose **Platform Independent**)
and drop it into the `lib/` folder.

```
lib/mysql-connector-j-8.4.0.jar
```

### 3. Create the database

```bat
scripts\setup-db.bat
```

Or by hand:

```bat
mysql -u root -p < db\schema.sql
mysql -u root -p movie_booking < db\seed.sql
```

This loads 3 theatres, 4 screens, 384 seats, 5 films and 11 shows. Shows are
dated relative to `CURDATE()`, so the demo data never goes stale.

### 4. Configure your password

```bat
copy config\app.properties.example config\app.properties
```

Then edit `db.password` in that file. (`config/app.properties` is git-ignored, so
your password never reaches the repository. Environment variables such as
`DB_PASSWORD` work too and take priority.)

### 5. Build and run

```bat
scripts\build.bat
scripts\run.bat
```

Open <http://localhost:8080>.

### 6. Run the concurrency tests

```bat
scripts\test.bat
```

```
[1] Same 3 seats, 20 users at once            => PASSED
[2] 20 users, one distinct seat each          => PASSED
[3] Overlapping seats, opposite orders        => PASSED
[4] Same booking confirmed by 10 threads      => PASSED
[5] Abandoned hold is reclaimed               => PASSED
```

---

## Sign-in

| Role     | E-mail             | Password    |
|----------|--------------------|-------------|
| Admin    | `admin@movie.com`  | `Admin@123` |
| Customer | sign up on the site |            |

The administrator is created automatically on first start, only if the `users`
table contains no admin yet.

---

## What you can do

**As a customer** — browse and search films, pick a date, see showtimes with live
seat counts, choose seats on a visual map, hold them for 8 minutes while you
"pay", confirm, and view or cancel your tickets.

**As an administrator** — add films, theatres and screens (the seat layout is
generated for you), schedule shows with per-tier pricing and automatic clash
detection, cancel a show (which cancels its bookings and frees the seats), and
list registered users.

---

## Project layout

```
movie_booking/
├── db/
│   ├── schema.sql              9 InnoDB tables, keys and indexes
│   └── seed.sql                demo data, dated from CURDATE()
├── config/
│   └── app.properties.example  copy to app.properties and edit
├── lib/                        put the MySQL driver jar here
├── scripts/                    build / run / test / setup-db
├── src/main/com/movie_booking/
│   ├── model/                  entities and status enums
│   ├── dao/                    JDBC data access, one per table
│   ├── dto/                    read-only join views
│   ├── service/                business rules, transactions, locking
│   ├── exception/              domain errors carrying HTTP status
│   ├── util/                   pool, transactions, JSON, passwords, config
│   ├── web/                    router, sessions, handlers
│   └── Main.java               entry point
├── src/test/                   concurrency test suite
├── webapp/                     HTML, CSS, JavaScript
├── SETUP.md                    step-by-step setup on a new machine
├── PROJECT.md                  full design document
└── README.md
```

---

## Troubleshooting

**`No suitable driver` / driver not found**
The jar is missing from `lib/`. See step 2.

**`Access denied for user`**
`db.password` in `config/app.properties` does not match your MySQL password.

**`Unknown database 'movie_booking'`**
The schema was never loaded. See step 3.

**`Address already in use`**
Something else holds port 8080. Start on another one:

```bat
java -Dserver.port=9090 -cp "build\classes;lib\*" com.movie_booking.Main
```

**`javac is not recognised`**
You have a JRE, not a JDK. Install a JDK (Temurin or Oracle) and make sure its
`bin` folder is on your `PATH`.

**Seats look stuck as held**
A hold lasts 8 minutes and the background sweeper reclaims it after that. To see
it immediately, run `scripts\test.bat`, whose fifth test forces and verifies
expiry.
