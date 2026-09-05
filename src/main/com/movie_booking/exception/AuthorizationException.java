package com.movie_booking.exception;

/**
 * The caller is signed in but is not allowed to do this - a customer reaching for
 * an admin endpoint, or trying to open someone else's booking.
 *
 * <p>Reported to the client as HTTP 403.
 */
public class AuthorizationException extends AppException {

    private static final long serialVersionUID = 1L;

    public AuthorizationException(String message) {
        super(message, 403, "FORBIDDEN");
    }

    public AuthorizationException(String message, Throwable cause) {
        super(message, 403, "FORBIDDEN", cause);
    }
}
