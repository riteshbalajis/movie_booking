package com.movie_booking.dao;

import com.movie_booking.model.BookingSeat;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Access to {@code booking_seats} - the line items that tie a booking to the
 * exact {@code show_seats} rows it holds, at the price paid on the day.
 *
 * <p>Storing the price here rather than reading it back from {@code show_seats}
 * is deliberate: if an admin re-prices a show next week, an already-issued ticket
 * must still show what the customer actually paid.
 */
public interface BookingSeatDao {

    /**
     * Inserts every line item of a booking in one JDBC batch.
     *
     * <p>A batch sends all rows in a single round trip instead of one per seat,
     * which matters because this runs inside the booking transaction - the longer
     * that transaction stays open, the longer it holds seat locks and the more it
     * blocks other users.
     *
     * @return how many rows were inserted
     */
    int createBookingSeats(Connection connection, int bookingId, List<BookingSeat> bookingSeats)
            throws SQLException;

    List<BookingSeat> findByBookingId(int bookingId) throws SQLException;

    /**
     * The {@code show_seat_id}s attached to a booking, read inside the caller's
     * transaction. Used by confirm and cancel to work out which seats to move.
     */
    List<Integer> findShowSeatIds(Connection connection, int bookingId) throws SQLException;

    /** The same, for several bookings at once - used by the expiry sweeper. */
    List<Integer> findShowSeatIdsForBookings(Connection connection, List<Integer> bookingIds)
            throws SQLException;

    int countByBookingId(int bookingId) throws SQLException;

    BigDecimal calculateTotalByBookingId(int bookingId) throws SQLException;

    int deleteByBookingId(Connection connection, int bookingId) throws SQLException;
}
