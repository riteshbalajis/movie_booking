package com.movie_booking.dao;

import com.movie_booking.dto.BookingDetails;
import com.movie_booking.model.Booking;
import com.movie_booking.model.BookingStatus;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Access to {@code bookings}.
 *
 * <p>As with {@link ShowSeatDao}, anything that writes takes a caller-supplied
 * {@link Connection}: a booking row is never created or advanced on its own, only
 * as one step of a transaction that is also moving seats.
 */
public interface BookingDao {

    /**
     * Inserts a booking and returns its generated id.
     *
     * @return the new {@code booking_id}
     */
    int createBooking(Connection connection, Booking booking) throws SQLException;

    // -----------------------------------------------------------------------
    // Reads
    // -----------------------------------------------------------------------

    Booking findById(int bookingId) throws SQLException;

    Booking findByRef(String bookingRef) throws SQLException;

    /**
     * Reads a booking and holds an exclusive lock on it for the rest of the
     * transaction.
     *
     * <p>This serialises two clicks on the same booking. Without it, a user
     * double-tapping "Confirm" could run two confirmations concurrently: both
     * would read status PENDING, both would proceed, and the second would either
     * double-charge or corrupt the seat state.
     */
    Booking findByIdForUpdate(Connection connection, int bookingId) throws SQLException;

    List<Booking> findByUserId(int userId) throws SQLException;

    List<Booking> findByShowId(int showId) throws SQLException;

    List<Booking> findAll() throws SQLException;

    List<Booking> findByStatus(BookingStatus status) throws SQLException;

    List<Booking> findUserBookingsByStatus(int userId, BookingStatus status) throws SQLException;

    /**
     * The rich, join-backed view used by "My bookings" and the ticket page:
     * booking, movie title, theatre, screen, showtime and seat labels in one go.
     */
    List<BookingDetails> findDetailsByUserId(int userId) throws SQLException;

    BookingDetails findDetailsById(int bookingId) throws SQLException;

    /**
     * Ids of PENDING bookings whose hold has lapsed.
     *
     * <p>Read inside the sweeper's transaction so the rows it is about to expire
     * cannot be confirmed underneath it.
     */
    List<Integer> findExpiredPendingIds(Connection connection, int limit) throws SQLException;

    int countByShowId(int showId) throws SQLException;

    boolean existsById(int bookingId) throws SQLException;

    // -----------------------------------------------------------------------
    // Mutations
    // -----------------------------------------------------------------------

    boolean updateStatus(Connection connection, int bookingId, BookingStatus status)
            throws SQLException;

    /**
     * Moves a booking from PENDING to CONFIRMED and clears its deadline.
     *
     * <p>The {@code WHERE status = 'PENDING'} clause makes this a compare-and-set:
     * a second concurrent confirmation changes zero rows and is rejected.
     *
     * @return {@code true} if this call was the one that confirmed it
     */
    boolean confirm(Connection connection, int bookingId) throws SQLException;

    /** @return {@code true} if this call was the one that cancelled it. */
    boolean cancel(Connection connection, int bookingId) throws SQLException;

    /** Bulk-marks abandoned holds as EXPIRED. @return rows changed. */
    int markExpired(Connection connection, List<Integer> bookingIds) throws SQLException;

    boolean updateTotalAmount(Connection connection, int bookingId,
            java.math.BigDecimal totalAmount) throws SQLException;
}
