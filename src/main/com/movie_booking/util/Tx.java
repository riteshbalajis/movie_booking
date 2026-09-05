package com.movie_booking.util;

import com.movie_booking.exception.AppException;
import com.movie_booking.exception.DataAccessException;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Runs a unit of work inside a single database transaction, with automatic
 * rollback and automatic retry of deadlocks.
 *
 * <p><b>The problem it solves.</b> A booking touches three tables
 * ({@code show_seats}, {@code bookings}, {@code booking_seats}). Either all three
 * change or none of them do - a booking row with no seats attached, or seats
 * marked BOOKED with no booking to show for them, are both corrupt states that
 * no amount of later cleanup can reliably fix. Doing that by hand at every call
 * site means repeating {@code setAutoCommit(false) / commit / rollback / finally}
 * seven times and getting it subtly wrong at least once.
 *
 * <p><b>Isolation level.</b> Transactions run at {@code READ_COMMITTED} rather
 * than MySQL's default {@code REPEATABLE_READ}. Repeatable-read takes gap locks
 * on ranges it scans, which on a hot show (many users selecting seats in the same
 * few rows) produces deadlocks between transactions that never actually wanted
 * the same seat. Read-committed locks only the rows genuinely touched; the
 * explicit {@code SELECT ... FOR UPDATE} in {@code ShowSeatDao} then supplies the
 * exact serialisation the booking flow needs, and nothing more.
 *
 * <p><b>Retries.</b> InnoDB resolves a deadlock by killing one transaction
 * (error 1213) and it aborts a transaction that waits too long for a lock
 * (error 1205). Neither means the request was invalid - it means it was unlucky.
 * Because the whole unit of work rolled back cleanly, replaying it is safe, so
 * it is retried a few times with a small randomised back-off. Business failures
 * ({@link AppException}) are never retried: if the seat is genuinely taken, it
 * will still be taken on the second attempt.
 */
public final class Tx {

    /** MySQL: "Deadlock found when trying to get lock; try restarting transaction". */
    private static final int ERROR_DEADLOCK = 1213;

    /** MySQL: "Lock wait timeout exceeded; try restarting transaction". */
    private static final int ERROR_LOCK_WAIT_TIMEOUT = 1205;

    /** ANSI SQLSTATE class for serialisation failure. */
    private static final String SQLSTATE_SERIALIZATION_FAILURE = "40001";

    private static final int MAX_ATTEMPTS =
            AppConfig.getInt("tx.maxAttempts", 3);
    private static final long RETRY_BASE_DELAY_MILLIS =
            AppConfig.getLong("tx.retryBaseDelayMillis", 40L);

    private Tx() {
        // Utility class.
    }

    /** A unit of work that runs against an open, non-auto-commit connection. */
    public interface Work<T> {
        T execute(Connection connection) throws SQLException;
    }

    /** A unit of work with no return value. */
    public interface VoidWork {
        void execute(Connection connection) throws SQLException;
    }

    /**
     * Executes {@code work} transactionally.
     *
     * @return whatever the work returns, after the commit succeeds
     * @throws AppException        business failures, propagated unchanged
     * @throws DataAccessException any other SQL failure, after rollback
     */
    public static <T> T execute(Work<T> work) {
        SQLException lastFailure = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try (Connection connection = DBConnection.getConnection()) {
                connection.setAutoCommit(false);
                connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

                try {
                    T result = work.execute(connection);
                    connection.commit();
                    return result;
                } catch (SQLException | RuntimeException failure) {
                    rollbackQuietly(connection);
                    throw failure;
                } finally {
                    // The pool also scrubs this, but resetting here keeps the
                    // connection sane even if the pool is swapped out later.
                    restoreAutoCommit(connection);
                }
            } catch (SQLException ex) {
                if (!isRetryable(ex) || attempt == MAX_ATTEMPTS) {
                    throw new DataAccessException(
                            "The database could not complete the request.", ex);
                }
                lastFailure = ex;
                backOff(attempt);
            }
        }

        // Unreachable: the loop either returns or throws.
        throw new DataAccessException("Transaction failed after "
                + MAX_ATTEMPTS + " attempts.", lastFailure);
    }

    /** Convenience overload for work that produces no value. */
    public static void executeVoid(final VoidWork work) {
        execute(new Work<Void>() {
            @Override
            public Void execute(Connection connection) throws SQLException {
                work.execute(connection);
                return null;
            }
        });
    }

    /**
     * @return {@code true} for failures that are pure contention and will very
     *         likely succeed if the whole transaction is replayed
     */
    private static boolean isRetryable(SQLException ex) {
        // Walk the chained-exception list; the driver sometimes reports the real
        // cause one link down rather than on the exception it throws.
        int guard = 0;
        for (SQLException current = ex;
                current != null && guard < 16;
                current = current.getNextException(), guard++) {

            int errorCode = current.getErrorCode();
            if (errorCode == ERROR_DEADLOCK || errorCode == ERROR_LOCK_WAIT_TIMEOUT) {
                return true;
            }
            if (SQLSTATE_SERIALIZATION_FAILURE.equals(current.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Sleeps for a short, randomised, growing interval.
     *
     * <p>The randomness matters: if every loser of a deadlock retried after
     * exactly the same delay they would collide again in lockstep. Jitter
     * spreads them out.
     */
    private static void backOff(int attempt) {
        long delay = RETRY_BASE_DELAY_MILLIS * attempt;
        long jitter = (long) (Math.random() * RETRY_BASE_DELAY_MILLIS);
        try {
            Thread.sleep(delay + jitter);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new DataAccessException("Interrupted while retrying a transaction.",
                    new SQLException(ex));
        }
    }

    private static void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ex) {
            System.err.println("[tx] Rollback failed: " + ex.getMessage());
        }
    }

    private static void restoreAutoCommit(Connection connection) {
        try {
            connection.setAutoCommit(true);
        } catch (SQLException ex) {
            System.err.println("[tx] Could not restore auto-commit: " + ex.getMessage());
        }
    }
}
