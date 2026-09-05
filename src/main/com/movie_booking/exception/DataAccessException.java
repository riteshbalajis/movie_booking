package com.movie_booking.exception;

import java.sql.SQLException;

/**
 * Wraps a low-level {@link SQLException} that the application cannot do anything
 * about - the server is down, a query is malformed, a constraint blew up
 * unexpectedly.
 *
 * <p>The DAO layer throws checked {@code SQLException} because that is the JDBC
 * contract. The service layer converts it here so that the web layer never has
 * to import {@code java.sql} - the boundary between "database" and "HTTP" stays
 * clean, and a driver change would not ripple upwards.
 *
 * <p>The original exception is always kept as the cause, so the stack trace in
 * the server log still points at the exact statement that failed. The message
 * sent to the browser stays generic, because SQL error text can leak table and
 * column names to an attacker.
 *
 * <p>Reported to the client as HTTP 500.
 */
public class DataAccessException extends AppException {

    private static final long serialVersionUID = 1L;

    public DataAccessException(String message, SQLException cause) {
        super(message, 500, "DATABASE_ERROR", cause);
    }
}
