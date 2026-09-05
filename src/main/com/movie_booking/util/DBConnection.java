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
     * @return a pooled connection in auto-commit mode. Always close it in a
     *         try-with-resources block.
     */
    public static Connection getConnection() throws SQLException {
        return POOL.borrow();
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
