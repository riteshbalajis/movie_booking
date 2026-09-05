package com.movie_booking.exception;

/**
 * Input failed a business rule: a blank name, a malformed e-mail, a seat count
 * over the per-booking limit, a show that ends before it starts.
 *
 * <p>Reported to the client as HTTP 400.
 */
public class ValidationException extends AppException {

    private static final long serialVersionUID = 1L;

    public ValidationException(String message) {
        super(message, 400, "VALIDATION_ERROR");
    }

    public ValidationException(String message, Throwable cause) {
        super(message, 400, "VALIDATION_ERROR", cause);
    }
}
