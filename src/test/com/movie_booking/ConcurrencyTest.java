package com.movie_booking;

import com.movie_booking.dto.BookingDetails;
import com.movie_booking.exception.AppException;
import com.movie_booking.exception.SeatUnavailableException;
import com.movie_booking.service.BookingService;
import com.movie_booking.util.DBConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Proves the seat-locking design actually holds under concurrent load.
 *
 * <p>Correctness here cannot be shown by calling the methods one at a time - a
 * race only appears when several threads are inside the critical section at once.
 * Each test below therefore lines up a group of threads on a
 * {@link CountDownLatch} and releases them together, so they collide as hard as
 * the machine allows rather than politely queueing.
 *
 * <p>Run it with {@code scripts/test.bat} while MySQL is up and seeded.
 *
 * <pre>
 *   1. Same seats, many users   -> exactly one wins, everyone else is told why
 *   2. Different seats          -> nobody is blocked by anybody else
 *   3. Overlapping, reverse order -> no deadlock (ordered lock acquisition)
 *   4. Double confirm           -> idempotent, never sold twice
 *   5. Expired hold             -> seats come back automatically
 * </pre>
 */
public final class ConcurrencyTest {

    private static final int THREADS = 20;

    private static final BookingService BOOKING_SERVICE = new BookingService();

    private static int testsRun;
    private static int testsPassed;

    private ConcurrencyTest() {
        // Entry point only.
    }

    public static void main(String[] args) throws Exception {
        System.out.println();
        System.out.println("  Concurrency test suite");
        System.out.println("  ==================================================");

        int showId = findFutureShow();
        if (showId == 0) {
            System.err.println("  No future show found. Load db/seed.sql first.");
            System.exit(1);
        }

        List<Integer> userIds = ensureTestUsers(THREADS);
        System.out.println("  Show under test : " + showId);
        System.out.println("  Test users      : " + userIds.size());
        System.out.println();

        sameSeatsRace(showId, userIds);
        differentSeatsRunFreely(showId, userIds);
        reverseOrderDoesNotDeadlock(showId, userIds);
        doubleConfirmIsIdempotent(showId, userIds);
        expiredHoldIsReclaimed(showId, userIds);

        System.out.println();
        System.out.println("  ==================================================");
        System.out.println("  " + testsPassed + " of " + testsRun + " tests passed.");

        DBConnection.shutdown();
        System.exit(testsPassed == testsRun ? 0 : 1);
    }

    // =======================================================================
    // Test 1 - the headline case
    // =======================================================================

    /**
     * Twenty users go for the same three seats at the same moment.
     *
     * <p>This is the scenario the whole locking design exists for. Exactly one
     * booking must be created; the other nineteen must be refused with a clear
     * reason. Anything else - two winners, or twenty failures - is a broken
     * system.
     */
    private static void sameSeatsRace(int showId, List<Integer> userIds) throws Exception {
        startTest("Same 3 seats, " + THREADS + " users at once");

        List<Integer> seats = reserveFreshSeats(showId, 3);
        final AtomicInteger wins = new AtomicInteger();
        final AtomicInteger lostRace = new AtomicInteger();
        final ConcurrentLinkedQueue<String> unexpected = new ConcurrentLinkedQueue<String>();

        runTogether(userIds, userId -> {
            try {
                BOOKING_SERVICE.holdSeats(userId, showId, seats);
                wins.incrementAndGet();
            } catch (SeatUnavailableException expected) {
                lostRace.incrementAndGet();
            } catch (AppException ex) {
                unexpected.add(ex.getCode() + ": " + ex.getMessage());
            }
        });

        int lockedInDb = countSeatsWithStatus(seats, "LOCKED");

        report("winners", wins.get(), 1);
        report("losers told 'seat taken'", lostRace.get(), THREADS - 1);
        report("seats LOCKED in database", lockedInDb, 3);
        reportNoUnexpectedErrors(unexpected);

        finishTest(wins.get() == 1
                && lostRace.get() == THREADS - 1
                && lockedInDb == 3
                && unexpected.isEmpty());
    }

    // =======================================================================
    // Test 2 - locking must not be too coarse
    // =======================================================================

    /**
     * Twenty users each take a seat of their own, all at the same moment.
     *
     * <p>The mirror image of test 1, and just as important. A design that locked
     * the whole show, or synchronised the booking method, would pass test 1 and
     * fail here by serialising users who were never in conflict. All twenty must
     * succeed.
     */
    private static void differentSeatsRunFreely(int showId, List<Integer> userIds)
            throws Exception {
        startTest(THREADS + " users, one distinct seat each");

        List<Integer> pool = reserveFreshSeats(showId, THREADS);
        final AtomicInteger wins = new AtomicInteger();
        final ConcurrentLinkedQueue<String> failures = new ConcurrentLinkedQueue<String>();

        final List<Integer> users = userIds;
        long startedAt = System.currentTimeMillis();

        runTogether(users, userId -> {
            int index = users.indexOf(Integer.valueOf(userId));
            List<Integer> mySeat = Collections.singletonList(pool.get(index));
            try {
                BOOKING_SERVICE.holdSeats(userId, showId, mySeat);
                wins.incrementAndGet();
            } catch (AppException ex) {
                failures.add(ex.getCode() + ": " + ex.getMessage());
            }
        });

        long elapsed = System.currentTimeMillis() - startedAt;

        report("successful bookings", wins.get(), THREADS);
        reportNoUnexpectedErrors(failures);
        System.out.println("      elapsed: " + elapsed + " ms for " + THREADS
                + " concurrent bookings");

        finishTest(wins.get() == THREADS && failures.isEmpty());
    }

    // =======================================================================
    // Test 3 - deadlock avoidance
    // =======================================================================

    /**
     * Half the threads ask for {@code [x, y]} and half for {@code [y, x]}.
     *
     * <p>This is the classic deadlock recipe: two transactions each holding the
     * lock the other needs. It does not happen here because
     * {@code lockForUpdate} sorts the ids before locking, so every transaction
     * walks the rows in the same direction. If that sort were removed, this test
     * would start reporting InnoDB deadlocks.
     */
    private static void reverseOrderDoesNotDeadlock(int showId, List<Integer> userIds)
            throws Exception {
        startTest("Overlapping seats requested in opposite orders");

        List<Integer> pair = reserveFreshSeats(showId, 2);
        final List<Integer> ascending = Arrays.asList(pair.get(0), pair.get(1));
        final List<Integer> descending = Arrays.asList(pair.get(1), pair.get(0));

        final AtomicInteger wins = new AtomicInteger();
        final AtomicInteger lostRace = new AtomicInteger();
        final ConcurrentLinkedQueue<String> deadlocks = new ConcurrentLinkedQueue<String>();

        final List<Integer> users = userIds;

        runTogether(users, userId -> {
            List<Integer> order = users.indexOf(Integer.valueOf(userId)) % 2 == 0
                    ? ascending : descending;
            try {
                BOOKING_SERVICE.holdSeats(userId, showId, order);
                wins.incrementAndGet();
            } catch (SeatUnavailableException expected) {
                lostRace.incrementAndGet();
            } catch (AppException ex) {
                deadlocks.add(ex.getCode() + ": " + ex.getMessage());
            }
        });

        report("winners", wins.get(), 1);
        report("losers told 'seat taken'", lostRace.get(), THREADS - 1);
        reportNoUnexpectedErrors(deadlocks);

        finishTest(wins.get() == 1 && deadlocks.isEmpty());
    }

    // =======================================================================
    // Test 4 - idempotent confirmation
    // =======================================================================

    /**
     * The same booking is confirmed by ten threads at once - a user
     * double-clicking, or a browser retrying a request that already succeeded.
     *
     * <p>Every call must report success and the booking must end up CONFIRMED
     * exactly once, with its seats sold exactly once.
     */
    private static void doubleConfirmIsIdempotent(int showId, List<Integer> userIds)
            throws Exception {
        startTest("Same booking confirmed by 10 threads at once");

        final int userId = userIds.get(0).intValue();
        List<Integer> seats = reserveFreshSeats(showId, 2);

        BookingDetails held = BOOKING_SERVICE.holdSeats(userId, showId, seats);
        final int bookingId = held.getBookingId();

        final AtomicInteger confirmed = new AtomicInteger();
        final ConcurrentLinkedQueue<String> failures = new ConcurrentLinkedQueue<String>();

        List<Integer> tenThreads = new ArrayList<Integer>();
        for (int i = 0; i < 10; i++) {
            tenThreads.add(Integer.valueOf(userId));
        }

        runTogether(tenThreads, ignored -> {
            try {
                BOOKING_SERVICE.confirmBooking(userId, bookingId);
                confirmed.incrementAndGet();
            } catch (AppException ex) {
                failures.add(ex.getCode() + ": " + ex.getMessage());
            }
        });

        int bookedSeats = countSeatsWithStatus(seats, "BOOKED");

        report("calls reporting success", confirmed.get(), 10);
        report("seats BOOKED", bookedSeats, 2);
        reportNoUnexpectedErrors(failures);

        finishTest(confirmed.get() == 10 && bookedSeats == 2 && failures.isEmpty());
    }

    // =======================================================================
    // Test 5 - holds expire
    // =======================================================================

    /**
     * A hold whose deadline has passed must free its seats.
     *
     * <p>Rather than waiting eight real minutes, the deadline is pushed into the
     * past directly in the database - which is exactly the state the system would
     * reach on its own - and the sweeper is then run once.
     */
    private static void expiredHoldIsReclaimed(int showId, List<Integer> userIds)
            throws Exception {
        startTest("Abandoned hold is reclaimed by the sweeper");

        int userId = userIds.get(1).intValue();
        List<Integer> seats = reserveFreshSeats(showId, 2);

        BOOKING_SERVICE.holdSeats(userId, showId, seats);
        int lockedBefore = countSeatsWithStatus(seats, "LOCKED");

        forceExpiry(seats);
        int reclaimed = BOOKING_SERVICE.sweepExpiredHolds();

        int availableAfter = countSeatsWithStatus(seats, "AVAILABLE");

        report("seats LOCKED before expiry", lockedBefore, 2);
        report("bookings expired by sweeper", reclaimed, 1);
        report("seats back to AVAILABLE", availableAfter, 2);

        finishTest(lockedBefore == 2 && reclaimed >= 1 && availableAfter == 2);
    }

    // =======================================================================
    // Harness
    // =======================================================================

    /** What one thread of a race does. */
    private interface Attempt {
        void run(int userId);
    }

    /**
     * Runs one attempt per user, all released at the same instant.
     *
     * <p>The starting latch is what makes this a real race. Without it the first
     * thread would usually finish before the last one had even been scheduled, and
     * the test would pass on a system with no locking at all.
     */
    private static void runTogether(List<Integer> userIds, final Attempt attempt)
            throws InterruptedException {

        final CountDownLatch startSignal = new CountDownLatch(1);
        final CountDownLatch finished = new CountDownLatch(userIds.size());
        ExecutorService pool = Executors.newFixedThreadPool(userIds.size());

        for (Integer userId : userIds) {
            final int id = userId.intValue();
            pool.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        startSignal.await();
                        attempt.run(id);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    } catch (RuntimeException ex) {
                        System.err.println("      unexpected: " + ex);
                    } finally {
                        finished.countDown();
                    }
                }
            });
        }

        startSignal.countDown();
        finished.await(60, TimeUnit.SECONDS);
        pool.shutdownNow();
    }

    // =======================================================================
    // Database helpers
    // =======================================================================

    private static int findFutureShow() throws SQLException {
        String sql = "SELECT show_id FROM shows "
                + "WHERE status = 'SCHEDULED' "
                + "AND TIMESTAMP(show_date, start_time) > NOW() "
                + "ORDER BY show_date, start_time LIMIT 1";

        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        }
    }

    /**
     * Takes the next block of untouched seats and resets them to AVAILABLE.
     *
     * <p>Each test needs seats no earlier test has already sold, otherwise a
     * failure in one would cascade into the next and hide its real cause.
     */
    private static int seatCursor;

    private static synchronized List<Integer> reserveFreshSeats(int showId, int count)
            throws SQLException {
        String sql = "SELECT show_seat_id FROM show_seats WHERE show_id = ? "
                + "ORDER BY show_seat_id LIMIT ? OFFSET ?";

        List<Integer> seats = new ArrayList<Integer>(count);
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, showId);
            statement.setInt(2, count);
            statement.setInt(3, seatCursor);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    seats.add(Integer.valueOf(resultSet.getInt(1)));
                }
            }
        }
        seatCursor += count;

        resetSeats(seats);
        return seats;
    }

    private static void resetSeats(List<Integer> seatIds) throws SQLException {
        if (seatIds.isEmpty()) {
            return;
        }
        StringBuilder sql = new StringBuilder("UPDATE show_seats SET status = 'AVAILABLE', "
                + "locked_by_user_id = NULL, locked_until = NULL WHERE show_seat_id IN (");
        for (int i = 0; i < seatIds.size(); i++) {
            sql.append(i == 0 ? "?" : ", ?");
        }
        sql.append(')');

        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < seatIds.size(); i++) {
                statement.setInt(i + 1, seatIds.get(i).intValue());
            }
            statement.executeUpdate();
        }
    }

    private static int countSeatsWithStatus(List<Integer> seatIds, String status)
            throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM show_seats WHERE status = ? AND show_seat_id IN (");
        for (int i = 0; i < seatIds.size(); i++) {
            sql.append(i == 0 ? "?" : ", ?");
        }
        sql.append(')');

        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            statement.setString(1, status);
            for (int i = 0; i < seatIds.size(); i++) {
                statement.setInt(i + 2, seatIds.get(i).intValue());
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        }
    }

    /** Drags the hold deadline into the past so expiry can be tested at once. */
    private static void forceExpiry(List<Integer> seatIds) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement seatUpdate = connection.prepareStatement(
                        "UPDATE show_seats SET locked_until = NOW() - INTERVAL 1 HOUR "
                        + "WHERE status = 'LOCKED' AND show_seat_id = ?");
                PreparedStatement bookingUpdate = connection.prepareStatement(
                        "UPDATE bookings SET expires_at = NOW() - INTERVAL 1 HOUR "
                        + "WHERE status = 'PENDING' AND booking_id IN "
                        + "(SELECT booking_id FROM booking_seats WHERE show_seat_id = ?)")) {

            for (Integer seatId : seatIds) {
                seatUpdate.setInt(1, seatId.intValue());
                seatUpdate.executeUpdate();
                bookingUpdate.setInt(1, seatId.intValue());
                bookingUpdate.executeUpdate();
            }
        }
    }

    /** Creates (or reuses) the accounts the racing threads book under. */
    private static List<Integer> ensureTestUsers(int count) throws SQLException {
        List<Integer> ids = new ArrayList<Integer>(count);

        try (Connection connection = DBConnection.getConnection();
                PreparedStatement find = connection.prepareStatement(
                        "SELECT user_id FROM users WHERE email = ?");
                PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO users (name, email, password_hash, role) "
                        + "VALUES (?, ?, 'test-only-never-logs-in', 'CUSTOMER')",
                        java.sql.Statement.RETURN_GENERATED_KEYS)) {

            for (int i = 0; i < count; i++) {
                String email = "loadtest" + i + "@example.com";

                find.setString(1, email);
                try (ResultSet resultSet = find.executeQuery()) {
                    if (resultSet.next()) {
                        ids.add(Integer.valueOf(resultSet.getInt(1)));
                        continue;
                    }
                }

                insert.setString(1, "Load Test " + i);
                insert.setString(2, email);
                insert.executeUpdate();
                try (ResultSet keys = insert.getGeneratedKeys()) {
                    keys.next();
                    ids.add(Integer.valueOf(keys.getInt(1)));
                }
            }
        }
        return ids;
    }

    // =======================================================================
    // Output
    // =======================================================================

    private static void startTest(String name) {
        testsRun++;
        System.out.println("  [" + testsRun + "] " + name);
    }

    private static void report(String label, int actual, int expected) {
        String mark = actual == expected ? "ok  " : "FAIL";
        System.out.println("      " + mark + "  " + label + ": " + actual
                + " (expected " + expected + ")");
    }

    private static void reportNoUnexpectedErrors(ConcurrentLinkedQueue<String> errors) {
        if (errors.isEmpty()) {
            System.out.println("      ok    no unexpected errors");
        } else {
            System.out.println("      FAIL  " + errors.size() + " unexpected error(s):");
            for (String error : errors) {
                System.out.println("              " + error);
            }
        }
    }

    private static void finishTest(boolean passed) {
        if (passed) {
            testsPassed++;
            System.out.println("      => PASSED");
        } else {
            System.out.println("      => FAILED");
        }
        System.out.println();
    }
}
