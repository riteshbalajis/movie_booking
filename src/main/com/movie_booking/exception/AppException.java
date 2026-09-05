package com.movie_booking.exception;

/**
 * Base class for every error the service layer raises deliberately.
 *
 * <p>Each subclass carries the HTTP status and a short machine-readable code, so
 * the web layer can turn any of them into a proper response with one generic
 * handler instead of a chain of {@code instanceof} checks. The service layer
 * itself stays free of HTTP concepts - it just throws the domain error, and the
 * mapping lives here in one place.
 *
 * <p>These are unchecked on purpose: a controller cannot meaningfully recover
 * from "seat already taken" in the middle of a request, it can only report it.
 */
public class AppException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int httpStatus;
    private final String code;

    public AppException(String message, int httpStatus, String code) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code;
    }

    public AppException(String message, int httpStatus, String code, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.code = code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getCode() {
        return code;
    }
}
