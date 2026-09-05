package com.movie_booking.exception;

/**
 * The caller is not signed in, or the e-mail/password pair was wrong.
 *
 * <p>The message is deliberately vague ("Invalid e-mail or password") so an
 * attacker cannot use it to discover which e-mail addresses are registered.
 *
 * <p>Reported to the client as HTTP 401.
 */
public class AuthenticationException extends AppException {

    private static final long serialVersionUID = 1L;

    public AuthenticationException(String message) {
        super(message, 401, "UNAUTHENTICATED");
    }

    public AuthenticationException(String message, Throwable cause) {
        super(message, 401, "UNAUTHENTICATED", cause);
    }
}
