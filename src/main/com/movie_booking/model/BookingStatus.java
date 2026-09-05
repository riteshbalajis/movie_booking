package com.movie_booking.model;

/**
 * Lifecycle of a booking.
 *
 * <pre>
 *   PENDING --confirm--> CONFIRMED --(after the show)--> COMPLETED
 *      |                     |
 *      |                     +--cancel--> CANCELLED
 *      +--hold lapsed------------------> EXPIRED
 * </pre>
 *
 * <p>A booking is created PENDING at the same moment its seats are LOCKED, and
 * both carry the same deadline. EXPIRED and CANCELLED are kept apart on purpose:
 * one is the system reclaiming an abandoned basket, the other is a deliberate
 * act by the user, and only the second should ever trigger a refund.
 */
public enum BookingStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
    EXPIRED,
    COMPLETED
}
