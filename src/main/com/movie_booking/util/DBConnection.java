package com.movie_booking.util;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * The single entry point to the database for the whole application.
 *
 * <p>Every DAO calls {@link #getConnection()} and closes what it gets back;
 * because the returned object is pooled, "closing" it just hands it back.
 *
 * <p>Connection details come from {@link AppConfig}, so nothing secret is
 * compiled into the class file. Set them with either environment variables:
 *
 * <pre>
 *   set DB_URL=jdbc:mysql://localhost:3306/movie_booking
 *   set DB_USER=root
 *   set DB_PASSWORD=your_password
 * </pre>
 *
 * <p>or a {@code config/app.properties} file (see {@code config/app.properties.example}).
 */
public final class DBConnection {

    /**
     * Note the time-zone settings, which are load-bearing rather than decoration.
     *
     * <p>By default Connector/J treats a {@code DATETIME} as an instant and shifts
     * it between the JVM's zone and the server's. Every value in this schema is a
     * <em>wall-clock</em> time in the cinema's own locale - a 10:00 show starts at
     * 10:00 whoever is looking - so that shifting is pure corruption. It caused a
     * real bug during development: a hold written for "now + 8 minutes" was stored
     * five and a half hours in the past, so the seats read back as already expired
     * and the hold did nothing at all.
     *
     * <p>{@code connectionTimeZone=SERVER} with {@code preserveInstants=false}
     * turns the conversion off, so a {@code LocalDateTime} round-trips exactly as
     * written.
     */
    private static final String DEFAULT_URL =
            "jdbc:mysql://localhost:3306/movie_booking"
            + "?useSSL=false"
            + "&allowPublicKeyRetrieval=true"
            + "&connectionTimeZone=SERVER"
            + "&preserveInstants=false"
            + "&rewriteBatchedStatements=true";

    private static final ConnectionPool POOL = createPool();

    private DBConnection() {
        // Utility class.
    }

    /**
     * Hands out a connection to work on.
     *
     * <p>If this thread is inside a {@link Tx} transaction, the transaction's own
     * connection is returned instead of a new one from the pool. That is what
     * lets an ordinary DAO read - {@code showDao.findById(id)} - be called from
     * inside a booking transaction and automatically take part in it: it sees the
     * transaction's uncommitted writes and sits inside its locks.
     *
     * <p>It also prevents a self-inflicted deadlock. Borrowing a second
     * connection while already holding one means that, with as many concurrent
     * transactions as the pool has connections, every thread holds one and waits
     * for another that can never arrive. That failure is not theoretical: it
     * showed up as twelve of twenty threads timing out during load testing.
     *
     * <p>The returned wrapper ignores {@code close()}, so a DAO's
     * try-with-resources cannot end the transaction early. The transaction is
     * closed by whoever opened it.
     *
     * @return a connection to use; always close it in a try-with-resources block
     */
    public static Connection getConnection() throws SQLException {
        Connection active = Tx.activeConnection();
        if (active != null) {
            return nonClosing(active);
        }
        return POOL.borrow();
    }

    /**
     * Wraps a connection so that {@code close()} does nothing.
     *
     * <p>Used only for the transaction-scoped connection above: the DAO believes
     * it owns what it was given and dutifully closes it, which must not commit,
     * roll back, or return anything to the pool.
     */
    private static Connection nonClosing(final Connection connection) {
        return (Connection) java.lang.reflect.Proxy.newProxyInstance(
                DBConnection.class.getClassLoader(),
                new Class<?>[] { Connection.class },
                new java.lang.reflect.InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, java.lang.reflect.Method method,
                            Object[] args) throws Throwable {
                        String name = method.getName();

                        if ("close".equals(name)) {
                            return null; // The transaction owner closes it.
                        }
                        if ("isClosed".equals(name)) {
                            return Boolean.FALSE;
                        }
                        // Guard the transaction against a DAO that decides to
                        // commit or change the commit mode underneath it.
                        if ("commit".equals(name) || "rollback".equals(name)
                                || "setAutoCommit".equals(name)) {
                            throw new SQLException("A DAO must not call " + name
                                    + "() - the transaction is owned by Tx.");
                        }

                        try {
                            return method.invoke(connection, args);
                        } catch (java.lang.reflect.InvocationTargetException ex) {
                            throw ex.getCause() == null ? ex : ex.getCause();
                        }
                    }
                });
    }

    /** Closes the pool. Called from the shutdown hook in {@code Main}. */
    public static void shutdown() {
        POOL.shutdown();
    }

    public static String describePool() {
        return "pool[live=" + POOL.liveConnectionCount()
                + ", idle=" + POOL.idleConnectionCount() + "]";
    }

    private static ConnectionPool createPool() {
        loadDriver();
        return new ConnectionPool(
                AppConfig.get("db.url", DEFAULT_URL),
                AppConfig.get("db.user", "root"),
                AppConfig.get("db.password", ""),
                AppConfig.getInt("db.pool.size", 20),
                AppConfig.getLong("db.pool.borrowTimeoutMillis", 10_000L),
                AppConfig.getInt("db.pool.validationTimeoutSeconds", 2));
    }

    /**
     * JDBC 4 auto-discovers drivers on the classpath, but loading it explicitly
     * turns "no suitable driver" (which tells you nothing) into a message that
     * says exactly which jar is missing.
     */
    private static void loadDriver() {
        String driverClass = AppConfig.get("db.driver", "com.mysql.cj.jdbc.Driver");
        try {
            Class.forName(driverClass);
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException(
                    "MySQL JDBC driver (" + driverClass + ") is not on the classpath.\n"
                    + "Download mysql-connector-j-<version>.jar into the lib/ folder "
                    + "and rebuild with scripts/build.bat", ex);
        }
    }
}
