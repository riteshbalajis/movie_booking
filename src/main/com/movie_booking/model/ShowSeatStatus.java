package com.movie_booking.model;

/**
 * Lifecycle of one seat for one show.
 *
 * <pre>
 *   AVAILABLE --hold--> LOCKED --confirm--> BOOKED
 *       ^                  |                   |
 *       |                  |                   |
 *       +---- expiry ------+---- cancel -------+
 * </pre>
 *
 * <p>LOCKED is a <em>time-bounded</em> reservation: the row also carries
 * {@code locked_by_user_id} and {@code locked_until}. It exists so a user can be
 * shown a payment screen without another user stealing the seat mid-payment,
 * while guaranteeing the seat returns to sale if that user simply walks away.
 * Without it we would either sell the seat twice or lose it forever.
 */
public enum ShowSeatStatus {
    AVAILABLE,
    LOCKED,
    BOOKED
}
