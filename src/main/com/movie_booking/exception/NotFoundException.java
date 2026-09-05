package com.movie_booking.exception;

/**
 * The requested row does not exist: an unknown movie, show, or booking id.
 *
 * <p>Reported to the client as HTTP 404.
 */
public class NotFoundException extends AppException {

    private static final long serialVersionUID = 1L;

    public NotFoundException(String message) {
        super(message, 404, "NOT_FOUND");
    }

    public NotFoundException(String message, Throwable cause) {
        super(message, 404, "NOT_FOUND", cause);
    }
}
