package com.movie_booking.exception;

/**
 * The request collides with the current state of the data: an e-mail that is
 * already registered, a screen already showing a film in that time slot, or a
 * booking being confirmed twice.
 *
 * <p>Reported to the client as HTTP 409.
 */
public class ConflictException extends AppException {

    private static final long serialVersionUID = 1L;

    public ConflictException(String message) {
        super(message, 409, "CONFLICT");
    }

    public ConflictException(String message, Throwable cause) {
        super(message, 409, "CONFLICT", cause);
    }
}
