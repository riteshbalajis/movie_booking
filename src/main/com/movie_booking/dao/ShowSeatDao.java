package com.movie_booking.dao;

import com.movie_booking.dto.SeatMapEntry;
import com.movie_booking.model.ShowSeat;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Access to {@code show_seats} - the table every concurrent booking fights over.
 *
 * <p>The methods split cleanly in two:
 *
 * <ul>
 *   <li><b>Read methods</b> take no {@link Connection}: they open one from the
 *       pool, run, and close. They are safe to call from anywhere.</li>
 *   <li><b>Mutating methods</b> all require a caller-supplied {@link Connection}.
 *       That is not an accident - changing a seat's state is only ever correct
 *       inside the booking transaction that also writes {@code bookings} and
 *       {@code booking_seats}. Forcing the connection into the signature makes it
 *       impossible to flip a seat to BOOKED outside a transaction by mistake.</li>
 * </ul>
 */
public interface ShowSeatDao {

    // -----------------------------------------------------------------------
    // Creation - called when an admin schedules a show
    // -----------------------------------------------------------------------

    /**
     * Materialises one {@code show_seats} row for every ACTIVE seat of the screen,
     * priced by seat type, in a single {@code INSERT ... SELECT}.
     *
     * @return how many seats were created
     */
    int createShowSeatsForShow(Connection connection, int showId, int screenId,
            BigDecimal regularPrice, BigDecimal premiumPrice, BigDecimal reclinerPrice)
            throws SQLException;

    // -----------------------------------------------------------------------
    // Reads
    // -----------------------------------------------------------------------

    ShowSeat findById(int showSeatId) throws SQLException;

    List<ShowSeat> findByShowId(int showId) throws SQLException;

    /**
     * The full seat grid for a show, already joined with {@code seats} for labels
     * and types, and with each seat's state resolved from the viewer's point of
     * view.
     *
     * @param viewerUserId the signed-in user, or {@code 0} for an anonymous
     *                     visitor; seats this user holds come back as
     *                     {@code MINE}
     */
    List<SeatMapEntry> findSeatMap(int showId, int viewerUserId) throws SQLException;

    /** @return seats that can still be sold, counting lapsed holds as free. */
    int countSellableByShowId(int showId) throws SQLException;

    /** @return the cheapest seat price for a show, or {@code null} if it has none. */
    BigDecimal findMinPriceByShowId(int showId) throws SQLException;

    boolean existsByShowAndSeat(int showId, int seatId) throws SQLException;

    /**
     * Maps {@code show_seat_id} to the human seat label ({@code "H7"}).
     *
     * <p>Used to turn a lost race into a message a person can act on - "Seat H7
     * just got booked" rather than "show_seat_id 8412 unavailable". Takes the
     * caller's connection so the lookup joins the transaction that already holds
     * the row locks.
     */
    Map<Integer, String> findSeatLabels(Connection connection, List<Integer> showSeatIds)
            throws SQLException;

    // -----------------------------------------------------------------------
    // Mutations - transaction-scoped by design
    // -----------------------------------------------------------------------

    /**
     * Takes an exclusive row lock on each of the given seats and returns their
     * current state.
     *
     * <p>This is the pessimistic half of the concurrency strategy. From the
     * moment this returns until the transaction ends, no other transaction can
     * read-for-update or modify these rows, so the availability check that
     * follows cannot go stale between the check and the write.
     *
     * <p>Implementations must lock the ids in a <b>deterministic order</b>
     * (ascending {@code show_seat_id}). Two users picking {H7, H8} and {H8, H7}
     * would otherwise grab the two rows in opposite orders and deadlock.
     *
     * @return the locked rows, ascending by id; a missing id simply does not
     *         appear, which the caller must treat as an error
     */
    List<ShowSeat> lockForUpdate(Connection connection, List<Integer> showSeatIds)
            throws SQLException;

    /**
     * Moves seats to {@code LOCKED} on behalf of a user until a deadline.
     *
     * <p>The {@code WHERE} clause re-asserts that each seat is still sellable, so
     * even if this were somehow called without the row lock above, it could not
     * steal a seat from a live hold. Belt and braces.
     *
     * @return how many rows actually changed; anything less than the number of
     *         ids requested means the caller lost a race and must roll back
     */
    int hold(Connection connection, List<Integer> showSeatIds, int userId,
            LocalDateTime lockedUntil) throws SQLException;

    /**
     * Converts this user's live holds into permanent {@code BOOKED} seats and
     * clears the hold columns.
     *
     * @return how many rows changed; less than requested means a hold expired
     *         mid-flight and the confirmation must be rejected
     */
    int markBooked(Connection connection, List<Integer> showSeatIds, int userId)
            throws SQLException;

    /**
     * Returns seats to {@code AVAILABLE} - used when a user cancels, and when a
     * booking is abandoned.
     *
     * @return how many rows changed
     */
    int release(Connection connection, List<Integer> showSeatIds) throws SQLException;

    /**
     * Sweeps up every hold whose deadline has passed, across all shows.
     *
     * @return how many seats were reclaimed
     */
    int releaseExpiredHolds(Connection connection) throws SQLException;

    boolean updatePrice(int showSeatId, BigDecimal price) throws SQLException;
}
