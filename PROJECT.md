# CineBook — Design Document

A movie ticket booking system in plain Java 8 with JDBC, MySQL, and an
HTML/CSS/JavaScript front end.

This document explains **what the system does, how it is built, and why each
significant decision was made**. Read it top to bottom and you will be able to
defend any part of the project.

---

## Table of contents

1. [What the system does](#1-what-the-system-does)
2. [The central problem: two people, one seat](#2-the-central-problem-two-people-one-seat)
3. [Architecture](#3-architecture)
4. [The database](#4-the-database)
5. [The concurrency design in detail](#5-the-concurrency-design-in-detail)
6. [Walking through a booking](#6-walking-through-a-booking)
7. [Layer by layer](#7-layer-by-layer)
8. [Security](#8-security)
9. [Performance](#9-performance)
10. [Testing](#10-testing)
11. [API reference](#11-api-reference)
12. [Bugs found while building it](#12-bugs-found-while-building-it)
13. [Design decisions and trade-offs](#13-design-decisions-and-trade-offs)
14. [Limitations and what would come next](#14-limitations-and-what-would-come-next)
15. [How to demonstrate it](#15-how-to-demonstrate-it)
16. [Likely questions, with answers](#16-likely-questions-with-answers)

---

## 1. What the system does

A customer can browse films, pick a date, see showtimes with live seat
availability, choose seats on a visual map, hold them briefly while completing
the booking, confirm, and later view or cancel their tickets.

An administrator can add films, theatres and screens, schedule shows with
per-tier pricing, and cancel shows.

### Scale of the build

| Part                     | Files | Lines |
|--------------------------|------:|------:|
| Java (main)              |    66 | 8,040 |
| Java (concurrency tests) |     1 |   527 |
| SQL (schema + seed)      |     2 |   311 |
| HTML / CSS / JavaScript  |    10 | 2,179 |
| **Total**                |**79** |**11,057** |

### The technology constraint

The brief was plain Java with JDBC and a basic HTML/CSS front end. That rules out
Spring, Hibernate, Jackson and a servlet container, so several things normally
taken off the shelf are written here instead:

| Normally you would use | Here it is                 | Where                    |
|------------------------|----------------------------|--------------------------|
| Tomcat / Spring Boot   | `com.sun.net.httpserver`   | `web/Router.java`        |
| HikariCP               | A ~250-line pool           | `util/ConnectionPool.java` |
| Jackson / Gson         | A small JSON reader/writer | `util/Json.java`         |
| `@Transactional`       | A transaction template     | `util/Tx.java`           |
| Spring Security        | PBKDF2 + token sessions    | `util/PasswordUtil.java`, `web/SessionStore.java` |

`com.sun.net.httpserver` is part of the JDK, so this is still "plain Java" — the
**only** non-JDK item on the classpath is the MySQL JDBC driver.

---

## 2. The central problem: two people, one seat

Everything interesting about a booking system comes from one fact: **a seat can be
sold once.** Ordinary CRUD does not have this property. If two people edit their
profile at once, the last write wins and nobody is harmed. If two people buy seat
H7 at once and both succeed, two customers arrive at the cinema with a ticket for
the same chair.

The naive implementation looks correct and is not:

```java
// WRONG - do not do this
ShowSeat seat = showSeatDao.findById(seatId);
if (seat.getStatus() == AVAILABLE) {      // (1) check
    showSeatDao.book(seatId);             // (2) act
}
```

Two threads can both execute (1) before either reaches (2). Both see `AVAILABLE`,
both proceed, and the seat is sold twice. This is a **check-then-act race**, and
no amount of re-reading or retrying fixes it — the gap between the two statements
is the bug.

There is a second, subtler problem. Real booking involves payment, which takes
time. That forces a choice:

- Sell the seat before payment → the seat is gone if the payment fails.
- Leave the seat open during payment → someone else buys it mid-transaction.

Neither is acceptable, so this system does neither. It introduces a third state.

---

## 3. Architecture

```
┌────────────────────────────────────────────────────────────┐
│  Browser — HTML, CSS, vanilla JavaScript                    │
│  index · movie · seats · bookings · login · admin           │
└──────────────────────────┬─────────────────────────────────┘
                           │  JSON over HTTP, session cookie
┌──────────────────────────▼─────────────────────────────────┐
│  Web layer            com.movie_booking.web                 │
│  Router · RequestContext · SessionStore · JsonView          │
│  Routing, authentication, serialisation, error → HTTP       │
└──────────────────────────┬─────────────────────────────────┘
                           │  plain Java calls, no HTTP types
┌──────────────────────────▼─────────────────────────────────┐
│  Service layer        com.movie_booking.service             │
│  BookingService · ShowService · CatalogService · AuthService │
│  Business rules, TRANSACTION BOUNDARIES, LOCKING PROTOCOL   │
└──────────────────────────┬─────────────────────────────────┘
                           │  SQL, one DAO per table
┌──────────────────────────▼─────────────────────────────────┐
│  DAO layer            com.movie_booking.dao                 │
│  PreparedStatements, row mapping, SELECT ... FOR UPDATE     │
└──────────────────────────┬─────────────────────────────────┘
                           │  pooled JDBC connections
┌──────────────────────────▼─────────────────────────────────┐
│  MySQL 8 / InnoDB — row locks, constraints, transactions    │
└────────────────────────────────────────────────────────────┘
```

### The rule each boundary enforces

- **The web layer knows about HTTP but not SQL.** It never imports `java.sql`.
- **The service layer knows about business rules but not HTTP.** It never imports
  `HttpExchange`, and it returns objects, not responses. That is what makes the
  same `BookingService` usable from the concurrency tests with no web server
  running at all — which is exactly how those tests work.
- **The DAO layer knows about SQL but not business rules.** It has no opinion
  about whether a booking is allowed, only how to read and write rows.

### Why this matters practically

The tests in `src/test` call `BookingService` directly. If transaction and
locking logic lived in the HTTP handlers — as it does in many student projects —
testing twenty simultaneous bookings would mean running a load generator against
a live server, and a failure would be far harder to diagnose.

---

## 4. The database

Nine tables. The shape that matters is the split between a **physical seat** and
**that seat for one show**.

```
theatres ──< screens ──< seats
                            │
movies ──< shows ───────────┴──< show_seats >── booking_seats >── bookings >── users
```

### The key modelling decision: `seats` vs `show_seats`

`seats` is the physical chair. Row H, number 7, of Screen 2 — bolted to the
floor, defined once.

`show_seats` is **that chair for one particular screening**. It carries the price
and the availability state.

Why they must be separate:

- **Price varies by screening.** H7 is ₹150 for a Tuesday matinee and ₹400 for a
  Saturday premiere. Price cannot live on the physical seat.
- **Availability is per show.** H7 being sold for the 6pm show says nothing about
  the 9pm show.
- **Contention is isolated.** Two users booking H7 for different shows touch
  different rows and never block each other. Had availability been stored on
  `seats`, every show in the building would contend for the same 96 rows.

Creating a show materialises its seats in one statement:

```sql
INSERT INTO show_seats (show_id, seat_id, status, price)
SELECT ?, seat_id, 'AVAILABLE',
       CASE seat_type WHEN 'REGULAR'  THEN ?
                      WHEN 'PREMIUM'  THEN ?
                      WHEN 'RECLINER' THEN ? END
FROM seats WHERE screen_id = ? AND status = 'ACTIVE';
```

One round trip creates 96 rows. A Java loop would be 96.

### The three-state seat

```
AVAILABLE ──hold──> LOCKED ──confirm──> BOOKED
    ▲                  │                   │
    └──── expiry ──────┴──── cancel ────────┘
```

`LOCKED` is the answer to the payment dilemma from section 2. It is a
**time-bounded** reservation, backed by two extra columns:

```sql
status            ENUM('AVAILABLE','LOCKED','BOOKED') NOT NULL DEFAULT 'AVAILABLE',
locked_by_user_id INT      NULL,
locked_until      DATETIME NULL,
```

The user gets an exclusive option on the seat for eight minutes. If they walk
away, it lapses and the seat returns to sale automatically. Nothing is oversold
and nothing gets stuck.

### Constraints that carry weight

```sql
-- A physical seat may appear at most ONCE per show.
CONSTRAINT uq_show_seats_show_seat UNIQUE (show_id, seat_id)
```

This is the **last line of defence**. Even if every piece of application logic
were wrong, the database itself cannot represent a double-sold seat.

```sql
-- Two shows cannot start at the same minute on the same screen.
CONSTRAINT uq_shows_screen_slot UNIQUE (screen_id, show_date, start_time)
```

Note what this does *not* catch: a 3-hour film starting thirty minutes into
another. That needs a real interval-overlap test, which is
`ShowDao.hasScheduleConflict`:

```sql
-- [a1,a2) and [b1,b2) overlap exactly when a1 < b2 AND b1 < a2
WHERE screen_id = ? AND show_date = ? AND status <> 'CANCELLED'
  AND start_time < ? AND ? < end_time
```

### Indexes, and the reason for each

| Index                                    | Serves                                   |
|------------------------------------------|------------------------------------------|
| `idx_show_seats_show_status (show_id, status)` | seat map and availability counts   |
| `idx_show_seats_expiry (status, locked_until)` | the expiry sweeper                 |
| `idx_bookings_user (user_id, booked_at)`       | "My bookings", already sorted      |
| `idx_bookings_pending (status, expires_at)`    | finding lapsed holds               |
| `idx_shows_movie_date (movie_id, show_date)`   | the showtimes listing              |

Each is a composite in the order the query filters, so MySQL can seek rather than
scan.

### Soft delete, not hard delete

There is no `DELETE` for films or shows. Retiring a film sets its status to
`INACTIVE`. A film that has been booked is referenced by historical tickets, and
deleting the row would leave those tickets pointing at nothing. It vanishes from
the listings while every past booking still resolves.

---

## 5. The concurrency design in detail

Three independent mechanisms, layered so that no single mistake can sell one seat
twice.

### Layer 1 — Pessimistic row locks (the primary defence)

Every booking transaction begins by locking the rows it intends to change:

```sql
SELECT ... FROM show_seats
WHERE show_seat_id IN (?, ?, ?)
ORDER BY show_seat_id
FOR UPDATE;
```

`FOR UPDATE` takes an **exclusive lock** on each matched row. A second transaction
running the same statement **blocks at this line** until the first commits or
rolls back.

That closes the check-then-act gap. The check and the write are now inside a
region no other transaction can enter:

```
Thread A: FOR UPDATE ──> sees AVAILABLE ──> writes LOCKED ──> COMMIT
Thread B: FOR UPDATE ─────────(blocked)──────────────────────> sees LOCKED ──> refused
```

Thread B does not read stale data and then fail; it does not read at all until A
is finished.

#### Why the `ORDER BY` is not cosmetic

Consider two users, no ordering:

| Time | User A          | User B          |
|------|-----------------|-----------------|
| t1   | locks seat H7   | locks seat H8   |
| t2   | wants H8, waits | wants H7, waits |
| t3   | **deadlock**    | **deadlock**    |

Each holds what the other needs. InnoDB detects it and kills one transaction with
error 1213.

`lockForUpdate` sorts the ids ascending before locking, so **every transaction in
the system walks the rows in the same direction**. A cycle becomes impossible:
whoever gets the lowest id first will get all of them, and everyone else simply
queues.

```java
List<Integer> ordered = new ArrayList<Integer>(showSeatIds);
Collections.sort(ordered);   // this line is the deadlock prevention
```

Test 3 in the suite requests overlapping seats in opposite orders specifically to
prove this. Remove the sort and that test starts reporting deadlocks.

### Layer 2 — Compare-and-set updates

Every state change re-states the expected current state in its `WHERE` clause and
the code checks the affected-row count:

```sql
UPDATE show_seats
   SET status = 'BOOKED', locked_by_user_id = NULL, locked_until = NULL
 WHERE show_seat_id IN (?, ?, ?)
   AND status = 'LOCKED'
   AND locked_by_user_id = ?
   AND locked_until >= ?;
```

```java
int sold = showSeatDao.markBooked(connection, seatIds, userId);
if (sold != seatIds.size()) {
    throw new ConflictException("The hold lapsed while confirming.");
}
```

An `UPDATE` in InnoDB is itself atomic and takes its own row locks, so this is
safe **even without layer 1**. Two mechanisms, each sufficient alone — which is
the point of defence in depth.

The same pattern makes confirmation and cancellation safe:

```sql
UPDATE bookings SET status = 'CONFIRMED' WHERE booking_id = ? AND status = 'PENDING';
```

A second concurrent confirmation changes zero rows and is rejected, so a
double-click cannot pay twice.

### Layer 3 — The unique constraint

`uq_show_seats_show_seat (show_id, seat_id)` means the *data* cannot represent a
double-sold seat, whatever the code does. Enforced by the database, not by
anybody remembering.

### Why there are no Java locks anywhere

A natural first instinct is:

```java
public synchronized void bookSeats(...) { ... }   // NOT used here
```

This is wrong for three reasons:

1. **It does not scale to more than one JVM.** Run a second copy of the
   application behind a load balancer and `synchronized` protects nothing, while
   still looking like it does. Two JVMs share only the database, so the mutual
   exclusion has to live there.
2. **It is far too coarse.** One `synchronized` method serialises *every* booking
   in the building, including users in different cinemas booking different films.
   Test 2 exists to catch exactly this: 20 users taking 20 distinct seats must all
   succeed, and they complete in about 76 ms because they never block each other.
3. **The database already does it, correctly.** InnoDB row locking is mature,
   deadlock-detecting, and crash-safe. Reimplementing it in application memory
   adds risk and removes durability.

The one place `java.util.concurrent` *is* used is where the state genuinely lives
in this process: `ConcurrentHashMap` for HTTP sessions, and a `ReentrantLock` for
the connection pool's own free list.

### Transaction isolation

Transactions run at `READ_COMMITTED`, not MySQL's default `REPEATABLE_READ`.

Repeatable-read takes **gap locks** on the ranges it scans. On a busy show, where
many users select seats in the same few rows, that produces deadlocks between
transactions that never wanted the same seat at all. Read-committed locks only
the rows actually touched; the explicit `FOR UPDATE` then supplies exactly the
serialisation the booking flow needs, and nothing more.

### Deadlock retry

Even with ordered locking, InnoDB can abort a transaction for contention it
alone can see (error 1213), or because a lock wait ran too long (error 1205).
Neither means the request was invalid — it means it was unlucky. Since the whole
unit of work rolled back cleanly, replaying it is safe:

```java
if (!isRetryable(ex) || attempt == MAX_ATTEMPTS) {
    throw new DataAccessException("The database could not complete the request.", ex);
}
backOff(attempt);   // base delay × attempt, plus random jitter
```

The **jitter** matters: without it, every loser of a deadlock would retry after
the identical delay and collide again in lockstep. Business failures
(`AppException`) are deliberately **never** retried — if a seat is genuinely
taken, it will still be taken on the second attempt.

### Expiry: enforced twice, on purpose

**Lazily, at read time.** Every availability query treats a lapsed hold as free:

```sql
(status = 'AVAILABLE' OR (status = 'LOCKED' AND locked_until < NOW()))
```

This is the path correctness depends on. It is immediate and race-free — the
moment a hold lapses, the seat is sellable, with no background job in the loop.

**Eagerly, by a sweeper.** `ExpirySweeper` runs every 60 seconds on a daemon
thread and resets lapsed rows to `AVAILABLE`.

The system would be *correct* without the sweeper, so why have it? Because
without it, `show_seats` slowly fills with rows claiming to be `LOCKED` by users
who left hours ago, bookings sit at `PENDING` for ever, any report counting "seats
currently held" is nonsense, and the expiry index fills with dead entries. The
lazy path is correctness; the sweeper is hygiene.

One detail worth noting:

```java
} catch (Throwable failure) {
    System.err.println("[sweeper] Pass failed, will retry: " + failure.getMessage());
}
```

Catching `Throwable` is usually a smell. Here it is required:
`scheduleWithFixedDelay` **silently cancels the entire schedule** if a run throws.
Without this catch, one transient database blip would stop expiry for the rest of
the process lifetime, with no error anywhere.

---

## 6. Walking through a booking

### Step 1 — Hold

`POST /api/bookings/hold` with `{ "showId": 5, "showSeatIds": [385, 386, 387] }`

```
BookingService.holdSeats()
│
├─ validate: not empty, no duplicates, ≤ 10 seats          ← before any lock
│
└─ Tx.execute — one transaction, READ_COMMITTED
   │
   ├─ 1. show exists, is SCHEDULED, has not started
   │
   ├─ 2. showSeatDao.lockForUpdate(seatIds)                ← SELECT ... FOR UPDATE
   │        all competing transactions queue here
   │
   ├─ 3. with rows pinned, verify each is sellable
   │        └─ any taken?  →  SeatUnavailableException(["H7","H8"])  →  ROLLBACK
   │
   ├─ 4. total = Σ price   (read from the locked rows, never from the client)
   │
   ├─ 5. UPDATE ... SET status='LOCKED', locked_until = now + 8min
   │        rows changed ≠ requested?  →  rollback
   │
   ├─ 6. INSERT INTO bookings (PENDING, expires_at, booking_ref)
   │
   ├─ 7. INSERT INTO booking_seats  (one JDBC batch)
   │
   └─ COMMIT — the locks release here
```

Two details worth pointing out:

- **De-duplication is not tidiness.** A repeated seat id would make the requested
  count disagree with the row count the database reports, and the code would read
  that as a lost race.
- **The price is read from the locked row**, never taken from the request. A
  client sending `"price": 1` gets ignored.

### Step 2 — Confirm

`POST /api/bookings/{id}/confirm`

```
├─ bookingDao.findByIdForUpdate(id)      ← locks the BOOKING row too
│     this is what serialises a double-click on the same booking
├─ already CONFIRMED?  →  return it unchanged (idempotent)
├─ not PENDING?        →  409
├─ lock the seats
├─ hold expired?       →  release seats, mark EXPIRED, 409 with a clear message
├─ markBooked(...)     ← compare-and-set; must affect every row
└─ bookings: PENDING → CONFIRMED, expires_at = NULL
```

Confirmation is **idempotent**: calling it twice returns success both times and
sells the seats once. This is not a nicety — users double-click, and a flaky
connection makes the browser retry a request that already succeeded.

### Step 3 — Cancel

Locks the booking, checks ownership (an admin may cancel anyone's), refuses once
the show has started, frees the seats, and sets the booking to `CANCELLED` — all
in one transaction.

---

## 7. Layer by layer

### `model/` — entities

Plain objects mapping to tables, plus the status enums. A little behaviour lives
here where it belongs to the data itself:

```java
public boolean isSellable(LocalDateTime now) {
    if (status == ShowSeatStatus.AVAILABLE) return true;
    return status == ShowSeatStatus.LOCKED
        && lockedUntil != null && lockedUntil.isBefore(now);
}
```

Putting that in one place is what stops the definition of "free" drifting between
the seat map, the availability count and the booking check.

### `dao/` — data access

One interface plus one implementation per table. Two conventions:

**Reads take no `Connection`; writes require one.**

```java
List<ShowSeat> findByShowId(int showId);                            // opens its own
int markBooked(Connection connection, List<Integer> ids, int user); // caller's transaction
```

That is a compile-time guarantee. Changing a seat's state is only ever correct
inside the booking transaction, and putting the connection in the signature makes
it *impossible* to flip a seat to `BOOKED` outside one by accident.

**Every statement is a `PreparedStatement`.** Seat ids arrive from the browser;
building `IN (...)` by pasting them into a string would be a textbook SQL
injection hole. Instead the placeholder list is generated and the values bound:

```java
private static String placeholders(int count) { /* "?, ?, ?" */ }
```

### `dto/` — read-only join views

`SeatMapEntry`, `ShowSummary`, `BookingDetails` exist to avoid the **N+1 query
problem**.

A booking row holds only ids. Rendering "My bookings" from entities means: fetch
bookings, then per booking fetch the show, the movie, the screen, the theatre and
the seats — five queries each, twenty-five for five bookings. One join does it in
one:

```sql
GROUP_CONCAT(CONCAT(se.row_label, se.seat_number)
             ORDER BY se.row_label, se.seat_number SEPARATOR ',') AS seat_labels
```

`ShowSummary` does the same for listings, resolving seat counts in correlated
sub-queries so a 20-show page is one query rather than 61.

`SeatMapEntry.displayStatus` is the *effective* status for a given viewer: a
lapsed hold reads as `AVAILABLE`, and a seat the viewer holds reads as `MINE` so
the UI can colour it differently. Resolved on the server — the browser is never
trusted to decide whether a hold has expired.

### `service/` — business rules

Where transactions begin and end, and the only layer that knows the locking
protocol. Covered in sections 5 and 6.

Constructor injection throughout:

```java
public BookingService() {                                    // production
    this(new BookingDaoImpl(), new BookingSeatDaoImpl(), ...);
}
public BookingService(BookingDao bookingDao, ...) { ... }    // tests
```

### `exception/` — domain errors

Each carries its own HTTP status and machine-readable code:

| Exception                  | Status | Meaning                        |
|----------------------------|--------|--------------------------------|
| `ValidationException`      | 400    | input broke a rule             |
| `AuthenticationException`  | 401    | not signed in / bad password   |
| `AuthorizationException`   | 403    | signed in, not permitted       |
| `NotFoundException`        | 404    | no such row                    |
| `ConflictException`        | 409    | clashes with current state     |
| `SeatUnavailableException` | 409    | lost the race — carries labels |
| `DataAccessException`      | 500    | wrapped `SQLException`         |

Because the exception knows its own status, `Router` converts *any* of them with
one generic handler. Without that, thirty-odd endpoints would each need a
try/catch, and one forgotten catch leaks a stack trace to the browser.

`SeatUnavailableException` carrying the seat *labels* is what lets the UI say
"Seats A2, A3 just got booked by someone else" instead of "error", and re-draw
only those two seats.

### `util/` — the reusable machinery

**`ConnectionPool`** — the original code opened a fresh TCP connection, MySQL
handshake and authentication for *every* DAO call. Under load that dominates
response time and exhausts `max_connections`. The pool hands out a
`java.lang.reflect.Proxy` implementing `Connection`, forwarding everything except
`close()`, which returns it to the pool. Every existing try-with-resources block
therefore became pooled with no code change. Connections are scrubbed on return —
any open transaction is rolled back — so no caller can inherit another's
half-finished work.

**`Tx`** — the transaction template: begin, set isolation, commit, roll back on
failure, retry deadlocks with jittered back-off, always restore auto-commit.
Written once instead of seven hand-rolled copies, one of which would inevitably
be subtly wrong.

It also **publishes the running transaction on a `ThreadLocal`**, which is what
lets a plain DAO read called from inside a transaction join it rather than borrow
a second connection. That is the fix described in section 12, and it is what makes
the rule "reads take no `Connection`" safe everywhere rather than only outside
transactions. `Tx.execute` is re-entrant for the same reason: a nested call joins
the transaction in progress instead of starting a second one, so an inner unit of
work can never commit half of an outer one.

**`Json`** — a recursive-descent parser and a writer, because Jackson is off the
table.

**`PasswordUtil`** — PBKDF2-HMAC-SHA256, 120,000 iterations, 16-byte random salt
per password, constant-time comparison.

**`AppConfig`** — resolves in order: system property → environment variable →
`config/app.properties` → default. That is how the same build runs on a laptop and
a server without editing source, and how credentials stay out of the repository.

### `web/` — HTTP

**`Router`** — pattern routes with `{id}` capture, plus the single error-to-JSON
conversion point. Distinguishes 404 (no such path) from 405 (wrong verb), which
is a very different fix for a client.

**`SessionStore`** — the cookie carries an opaque `SecureRandom` token and
nothing else. Everything meaningful is looked up server-side. Had it carried
`userId=7&role=ADMIN`, anyone could edit it and become an administrator. Backed by
`ConcurrentHashMap` because every request runs on a pool thread; a synchronised
store would make the whole server queue on one monitor.

**`JsonView`** — serialises field by field, so `passwordHash` cannot reach the
browser **by construction** rather than by someone remembering to null it out.
Reflection would happily publish every field a future developer adds.

**`StaticFileHandler`** — path traversal is blocked by normalising the resolved
path and checking it is still inside the web root. Blacklisting `".."` is not
enough: URL-encoded and doubled-up variants slip past string checks, whereas
comparing the final resolved location cannot be tricked.

### `webapp/` — the front end

Six pages, one stylesheet, two scripts. No framework and no build step.

- `app.js` `escapeHtml()` routes every server value through `textContent` before
  it reaches `innerHTML`. A film title of `<img src=x onerror=...>` would
  otherwise execute — stored XSS.
- `api.js` uses `credentials: 'same-origin'` and never touches the token itself.
  The cookie is `HttpOnly`, so script on the page cannot read it: an XSS bug still
  cannot steal a session.
- `seats.html` renders the grid with a gangway down the middle, runs the hold
  countdown, and recovers from a lost race in place.
- The countdown is **presentation only**. The server re-checks the deadline, so a
  sleeping tab or a doctored clock buys nobody extra time.

---

## 8. Security

| Threat | Defence |
|---|---|
| SQL injection | `PreparedStatement` everywhere, generated placeholders for `IN` lists |
| Password theft | PBKDF2-HMAC-SHA256, 120k iterations, per-password salt |
| Timing attack on login | constant-time hash comparison |
| Account enumeration | wrong e-mail and wrong password give the identical error |
| Session hijacking | 256-bit `SecureRandom` token, `HttpOnly`, server-side lookup |
| XSS | every rendered value escaped through `textContent` |
| CSRF | `SameSite=Lax` on the session cookie |
| Open redirect | `?next=` accepted only if it is a same-site path |
| Path traversal | normalise-and-verify against the web root |
| Privilege escalation | role checked server-side on every admin route |
| IDOR | ownership checked on every booking read and write |
| Information leak | SQL errors logged server-side, generic message returned |
| Denial of service | request bodies capped at 64 KB; ≤ 10 seats per booking |

### Password storage, specifically

```java
private static final int ITERATIONS = 120_000;
```

A plain SHA-256 is designed to be *fast*, which is precisely wrong for passwords:
a GPU tries billions of candidates per second against a stolen table. PBKDF2 is
deliberately slow, and the per-password salt means identical passwords produce
different hashes, so one precomputed rainbow table cannot attack two accounts at
once.

The stored format is self-describing:

```
pbkdf2$120000$<base64 salt>$<base64 hash>
```

The iteration count travels with the hash, so the cost factor can be raised later
without invalidating existing rows.

---

## 9. Performance

| Technique | Effect |
|---|---|
| Connection pooling | removes TCP + handshake + auth from every DAO call |
| Join views (DTOs) | listings are 1 query, not N+1 |
| Correlated sub-queries | seat counts resolved in SQL, not by looping in Java |
| JDBC batching | 96-seat layouts and multi-seat bookings in one round trip |
| `INSERT ... SELECT` | show seats created server-side, no data round trip |
| Composite indexes | ordered to match query filters, so MySQL seeks |
| Short transactions | validation happens before locks are taken |
| Row-level locking | independent seats never block each other |
| Transaction-scoped connections | one connection per transaction, never two |
| 32-thread HTTP pool | requests are genuinely concurrent |

**Measured:** 20 concurrent bookings of distinct seats complete in ~76 ms on this
machine — they do not serialise.

The general principle throughout: **hold locks for as short a time as possible.**
Every validation that can happen before `FOR UPDATE` does happen before it,
because the lock's duration is time other users spend blocked.

---

## 10. Testing

`scripts\test.bat` runs five tests against real MySQL. Each lines its threads up
on a `CountDownLatch` and releases them together — without that, the first thread
would usually finish before the last was even scheduled, and the tests would pass
on a system with no locking at all.

```
[1] Same 3 seats, 20 users at once
      ok    winners: 1 (expected 1)
      ok    losers told 'seat taken': 19 (expected 19)
      ok    seats LOCKED in database: 3 (expected 3)
      => PASSED

[2] 20 users, one distinct seat each
      ok    successful bookings: 20 (expected 20)
      elapsed: 76 ms for 20 concurrent bookings
      => PASSED

[3] Overlapping seats requested in opposite orders
      ok    winners: 1 (expected 1)
      ok    no unexpected errors                      ← no deadlocks
      => PASSED

[4] Same booking confirmed by 10 threads at once
      ok    calls reporting success: 10 (expected 10)
      ok    seats BOOKED: 2 (expected 2)
      => PASSED

[5] Abandoned hold is reclaimed by the sweeper
      ok    bookings expired by sweeper: 1 (expected 1)
      ok    seats back to AVAILABLE: 2 (expected 2)
      => PASSED

  5 of 5 tests passed.
```

**Why test 2 matters as much as test 1.** Test 1 proves the locking is strong
enough. Test 2 proves it is not too strong. A `synchronized` method would pass
test 1 and fail test 2 by serialising users who were never in conflict. Together
they pin the design from both sides.

**Test 3 justifies one line of code** — the `Collections.sort` in
`lockForUpdate`. Delete it and this test starts reporting InnoDB deadlocks.

**Running the suite under a starved pool** is the regression test for the bug in
section 12. Twenty concurrent transactions against four connections would have
been fatal before the fix:

```bat
java -Ddb.pool.size=4 -cp "build\classes;build\test-classes;lib\*" ^
     com.movie_booking.ConcurrencyTest
```

```
  5 of 5 tests passed.
```

Beyond the suite, the following were verified by hand against the running server:
registration, login, hold, confirm and cancel; a customer receiving 403 on an
admin endpoint; overlapping showtimes rejected; a cancelled show cascading to its
bookings and freeing all 40 seats; and path traversal (plain, URL-encoded and
nested) returning 404.

---

## 11. API reference

All responses are JSON. Errors are `{ "error": CODE, "message": "..." }`.

### Public

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/health` | liveness and configuration |
| `GET` | `/api/movies?q=` | list or search films |
| `GET` | `/api/movies/{id}` | one film |
| `GET` | `/api/movies/{id}/shows?date=` | showtimes with availability |
| `GET` | `/api/shows/{id}` | one show |
| `GET` | `/api/shows/{id}/seats` | the seat map |
| `GET` | `/api/theatres` | theatres |
| `GET` | `/api/theatres/{id}/screens` | screens of a theatre |

### Authentication

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/auth/register` | create account, signs in |
| `POST` | `/api/auth/login` | sign in |
| `POST` | `/api/auth/logout` | sign out |
| `GET` | `/api/auth/me` | current user, or `null` |
| `POST` | `/api/auth/password` | change password |

### Booking — sign-in required

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/bookings/hold` | reserve seats, open a PENDING booking |
| `POST` | `/api/bookings/{id}/confirm` | complete it |
| `POST` | `/api/bookings/{id}/cancel` | cancel, seats return to sale |
| `GET` | `/api/bookings` | my bookings |
| `GET` | `/api/bookings/{id}` | one booking |

### Admin — admin role required

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/admin/movies` | all films, any status |
| `POST` | `/api/admin/movies` | add a film |
| `DELETE` | `/api/admin/movies/{id}` | retire a film (soft delete) |
| `POST` | `/api/admin/theatres` | add a theatre |
| `POST` | `/api/admin/screens` | add a screen and generate its seats |
| `GET` | `/api/admin/shows?date=` | shows on a date |
| `POST` | `/api/admin/shows` | schedule a show |
| `POST` | `/api/admin/shows/{id}/cancel` | cancel a show and its bookings |
| `GET` | `/api/admin/users` | registered users |

### Example — losing a race

```http
POST /api/bookings/hold
{ "showId": 5, "showSeatIds": [386, 387, 388] }
```

```json
HTTP/1.1 409
{
  "error": "SEATS_UNAVAILABLE",
  "message": "Seats A2, A3 just got booked by someone else. Please pick another.",
  "unavailableSeats": ["A2", "A3"]
}
```

---

## 12. Bugs found while building it

Two real bugs surfaced only when the system was actually run under load. Neither
was visible by reading the code, and both are worth knowing because they are
exactly the kind of thing a reviewer will ask about.

---

### Bug 1 — The connection pool deadlocking against itself

#### The symptom

The concurrency suite passed, then failed on a freshly loaded database:

```
[1] Same 3 seats, 20 users at once
      ok    winners: 1 (expected 1)
      FAIL  losers told 'seat taken': 7 (expected 19)
      FAIL  12 unexpected error(s):
              DATABASE_ERROR: The database could not complete the request.
```

Exactly one thread still won, so no seat was ever double-sold — but twelve of the
twenty threads failed with a database error instead of being told the seats were
taken. Intermittent, and timing-dependent.

#### The cause

A booking transaction holds a connection for its whole duration. Inside it,
`requireBookableShow` called an ordinary DAO read:

```java
Show show = showDao.findById(showId);   // borrows a SECOND connection
```

That method takes no `Connection`, so it borrowed its own from the pool. With
twenty concurrent bookings against a twenty-connection pool, **every thread held
one connection and waited for a second that could never arrive.** The pool was
deadlocked against itself, and each thread eventually hit the 10-second borrow
timeout.

It was intermittent because it only bites when the number of simultaneous
transactions reaches the pool size — which is precisely when the system is busiest.

The same pattern existed in **seven places** across three services.

#### The fix

Rather than patch seven call sites, the class of bug was removed. `Tx` now
publishes its connection on a `ThreadLocal`, and `DBConnection.getConnection()`
returns it when the calling thread is already in a transaction:

```java
public static Connection getConnection() throws SQLException {
    Connection active = Tx.activeConnection();
    if (active != null) {
        return nonClosing(active);     // join the transaction
    }
    return POOL.borrow();
}
```

The wrapper ignores `close()`, so a DAO's try-with-resources cannot end the
transaction early, and it rejects `commit()`, `rollback()` and `setAutoCommit()`
outright, since those belong to whoever opened the transaction.

`Tx.execute` also became re-entrant: a nested call joins the running transaction
instead of starting a second one, so an inner unit of work can never commit half
of an outer one.

#### Why this was the right fix

It solves every occurrence at once and prevents future ones — a DAO read added
inside a transaction next year is correct by default. It also fixes a **second,
quieter bug** nobody had noticed: a read on a separate connection is outside the
transaction, so it could not see the transaction's own uncommitted writes and sat
outside its locks. Those reads were returning subtly wrong data even when the
pool was not exhausted.

#### The proof

The suite now runs 20 threads against a deliberately crippled **4-connection**
pool and still passes:

```
java -Ddb.pool.size=4 ... com.movie_booking.ConcurrencyTest
  5 of 5 tests passed.
```

Before the fix that was guaranteed to fail. Test 2 also got faster — 59 ms
instead of 86 ms — because bookings no longer contend for extra connections.

---

### Bug 2 — Holds that silently did nothing

#### The symptom

After holding three seats, the response looked wrong:

```json
"bookedAt":  "2026-09-06T02:17:44",
"expiresAt": "2026-09-05T20:55:45"     ← the hold "expired" before it began
```

and the show still reported **96 of 96 seats available** even though three were
held.

#### The cause

Connector/J was treating `DATETIME` values as *instants* and shifting them
between the JVM's time zone and the server's. A hold written for "now + 8
minutes" was stored **five and a half hours in the past**. Every availability
query compares `locked_until < NOW()`, so the freshly created hold read as
already expired — meaning **holds silently did nothing at all**.

#### The fix

```java
+ "&connectionTimeZone=SERVER"
+ "&preserveInstants=false"
```

plus moving the audit columns from `TIMESTAMP` to `DATETIME`.

Every time in this schema is a **wall-clock** time in the cinema's own locale — a
10:00 show starts at 10:00 whoever is looking — so converting between zones is
pure corruption, not a feature.

#### Why it is worth mentioning

The code compiled, the unit logic was right, and a purely visual review would have
passed it. Only running the thing exposed it. It is also a genuinely subtle
failure: the seat *appeared* to be held in the database, but every query that
mattered disagreed.

---

## 13. Design decisions and trade-offs

| Decision | Alternative | Why this one |
|---|---|---|
| Pessimistic locking | Optimistic version column | Seat contention is common and hot on a popular release; optimistic locking would make most users retry. It is used anyway as layer 2. |
| Hold-then-confirm | Book in one call | Removes the payment dilemma: the seat is neither sold before payment nor left open during it. |
| `READ_COMMITTED` | `REPEATABLE_READ` (MySQL default) | Avoids gap locks that deadlock transactions with no seat in common. |
| Expiry lazy **and** eager | One or the other | Lazy is immediate and race-free; eager keeps stored state honest. Neither alone is enough. |
| Soft delete | Hard delete | Historical tickets reference films and shows. |
| Server-side sessions | JWT | No key management, and instant revocation. Cost: sessions do not survive a restart. |
| In-memory sessions | A sessions table | No extra table or cleanup job; a restart is not a normal event. Would need a shared store to run more than one instance. |
| `Connection` in write signatures | Same method for both | Makes it a compile error to change seat state outside a transaction. |
| Custom pool and JSON | Third-party libraries | The brief said plain Java; it also makes the mechanisms visible rather than magic. |
| `com.sun.net.httpserver` | Tomcat | Ships in the JDK, so "no frameworks" is honest. |

---

## 14. Limitations and what would come next

Known and deliberate, given the scope:

1. **No real payment.** Confirmation stands in for a payment gateway. The
   hold-then-confirm split is exactly the shape a real integration needs — the
   gateway callback would drive `confirmBooking`.
2. **Sessions are per-instance.** Running two copies behind a load balancer needs
   a shared session store. The *booking* logic already works across instances,
   because its locking is in the database, not in memory.
3. **No e-mail or SMS.** A confirmed booking would normally send a ticket.
4. **No seat-level pricing rules** beyond the three tiers — no weekend surcharge,
   no discount codes.
5. **HTTP, not HTTPS.** For a real deployment the session cookie also needs the
   `Secure` flag, which is why it is called out in the code comment rather than
   quietly omitted.
6. **No rate limiting.** Login is not throttled, so it is open to brute force
   given enough time. PBKDF2's cost makes that expensive but not impossible.
7. **The `shows` table has no timezone.** Fine for one region, wrong for a chain
   spanning several.
8. **A show that runs past midnight is rejected** rather than modelled across the
   date boundary.

---

## 15. How to demonstrate it

A ten-minute run-through that shows the interesting parts rather than just the
CRUD.

**1. Start it (30s)** — `scripts\run.bat`. Point out the start-up order: the
database is verified before the port opens, so a misconfiguration fails loudly
with instructions rather than as a 500 on the first click.

**2. Browse and book (2 min)** — Sign up, pick a film and a date, open the seat
map. Point out the price tiers by row and the live seat count. Select three
seats, hold them, and let the countdown appear.

**3. Show the race (2 min)** — the centrepiece. In a **second browser or a private
window**, sign in as a different user and open the same show. The three seats
show as *held* in a different colour, and cannot be clicked. Try to book them via
the API and the response names the exact seats:

```bat
curl -X POST localhost:8080/api/bookings/hold -d "{\"showId\":5,\"showSeatIds\":[385]}"
```

**4. Confirm (1 min)** — Back in the first window, confirm. The booking reference
appears on My Bookings.

**5. Run the concurrency suite (2 min)** — `scripts\test.bat`. Walk through the
five results, and make the point about tests 1 and 2 pinning the design from both
sides.

**6. Admin (2 min)** — Schedule a show, then try to schedule another that overlaps
it on the same screen and show the conflict being refused. Then cancel a show and
show that its bookings are cancelled and its seats freed.

**7. Close on the design (1 min)** — Bring up the three layers of defence and the
reason there are no Java locks anywhere in the booking path.

---

## 16. Likely questions, with answers

**How do you stop two people booking the same seat?**
Three layers. First, `SELECT ... FOR UPDATE` takes an exclusive row lock at the
start of the booking transaction, so competing transactions queue at that
statement rather than reading stale data — that closes the check-then-act gap.
Second, every update is a compare-and-set that re-states the expected status in
its `WHERE` clause and verifies the affected-row count, which would be safe on its
own. Third, a unique key on `(show_id, seat_id)` means the data cannot even
represent a double-sold seat.

**Why not just `synchronized`?**
It only works inside one JVM. Run a second instance behind a load balancer and it
protects nothing while still looking like it does. It is also far too coarse — it
would serialise every booking in the building, including users in different
cinemas. The database is the only thing all instances share, so that is where the
mutual exclusion has to be.

**What is a deadlock and how did you prevent it?**
Two transactions each holding a lock the other needs, so neither can proceed. It
would happen here if one user asked for seats `[H7, H8]` and another for
`[H8, H7]`. The fix is to sort the seat ids before locking, so every transaction
acquires locks in the same order and a cycle is impossible. Test 3 exists
specifically to prove that line of code earns its place.

**Why `READ_COMMITTED` rather than the MySQL default?**
Repeatable-read takes gap locks on scanned ranges, which causes deadlocks between
transactions that never wanted the same seat. Read-committed locks only the rows
actually touched, and the explicit `FOR UPDATE` supplies exactly the
serialisation the booking flow needs.

**What happens if the user closes the tab mid-booking?**
Nothing is stuck. The hold carries a deadline. Every availability query already
treats a lapsed hold as free, so the seat is sellable the instant it expires, and
a background sweeper tidies the stored state within a minute.

**Why hold seats instead of just booking them?**
Because payment takes time. Selling before payment loses the seat if payment
fails; leaving it open during payment lets someone else take it. A time-bounded
hold gives the user an exclusive option without either risk.

**What if the server crashes halfway through a booking?**
The transaction was never committed, so InnoDB rolls it back on recovery. There
is no state where a booking exists without its seats, or seats are marked sold
with no booking.

**How are passwords stored?**
PBKDF2-HMAC-SHA256 with 120,000 iterations and a random 16-byte salt per
password, compared in constant time. A plain digest is designed to be fast, which
is the wrong property for passwords; the salt stops one rainbow table attacking
two accounts at once.

**Why write your own connection pool and JSON parser?**
The brief was plain Java with JDBC. It also means the mechanisms are visible: the
pool is a dynamic proxy that intercepts `close()`, so every existing
try-with-resources block became pooled without a single call site changing.

**How would you scale this to a real cinema chain?**
The booking logic already scales horizontally, because its locking lives in the
database rather than in application memory. Three things would need changing:
sessions into a shared store, the database given read replicas for the listing
queries, and a queue in front of the booking endpoint for very high demand
releases. The seat-locking protocol itself would not change.

**What was the hardest bug?**
The connection pool deadlocking against itself. A DAO read called from inside a
booking transaction borrowed a *second* connection, so with twenty concurrent
bookings against a twenty-connection pool every thread held one and waited for one
that could never arrive. It was intermittent, because it only bites when
concurrency reaches the pool size — exactly when the system is busiest. I fixed
the class of bug rather than the seven call sites: `Tx` publishes its connection
on a `ThreadLocal` and `DBConnection` hands it back to anything already inside a
transaction. That also fixed a quieter bug, since those reads had been running
outside the transaction and could not see its own uncommitted writes. The suite
now passes 20 threads against a deliberately crippled 4-connection pool.

**Was there another one worth mentioning?**
A JDBC time-zone conversion that stored every hold five and a half hours in the
past, so holds silently did nothing while appearing to work. Fixed by disabling
instant conversion on the connection and using `DATETIME` for wall-clock values.
Both bugs share a lesson: the code compiled and read correctly in review, and only
running it under real concurrency exposed them.

**Can two instances of the app run against one database?**
The booking logic, yes — all its mutual exclusion is in the database, not in
application memory, which is the main reason no `synchronized` was used. Sessions
would need moving to a shared store first, since they are currently in-process.

**What would you do differently with more time?**
Add a real payment integration, move sessions to a shared store, add rate
limiting on login, and write unit tests for the service layer with mocked DAOs —
the constructor injection is already there for it. I would also model shows that
cross midnight, which are currently rejected.
