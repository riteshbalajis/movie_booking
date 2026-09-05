package com.movie_booking.service;

import com.movie_booking.dao.BookingDao;
import com.movie_booking.dao.BookingDaoImpl;
import com.movie_booking.dao.BookingSeatDao;
import com.movie_booking.dao.BookingSeatDaoImpl;
import com.movie_booking.dao.ShowDao;
import com.movie_booking.dao.ShowDaoImpl;
import com.movie_booking.dao.ShowSeatDao;
import com.movie_booking.dao.ShowSeatDaoImpl;
import com.movie_booking.dto.BookingDetails;
import com.movie_booking.exception.AuthorizationException;
import com.movie_booking.exception.ConflictException;
import com.movie_booking.exception.DataAccessException;
import com.movie_booking.exception.NotFoundException;
import com.movie_booking.exception.SeatUnavailableException;
import com.movie_booking.exception.ValidationException;
import com.movie_booking.model.Booking;
import com.movie_booking.model.BookingSeat;
import com.movie_booking.model.BookingStatus;
import com.movie_booking.model.Show;
import com.movie_booking.model.ShowSeat;
import com.movie_booking.model.ShowStatus;
import com.movie_booking.model.UserRole;
import com.movie_booking.util.AppConfig;
import com.movie_booking.util.Tx;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The booking workflow - and the part of the system that has to survive many
 * users going for the same seat at the same moment.
 *
 * <h2>Why booking is split into hold then confirm</h2>
 *
 * A single "book it now" call would be simpler, but it forces an impossible
 * choice as soon as payment is involved: either the seat is sold before the money
 * arrives, or the seat stays open while the user is on the payment page and can
 * be sold from under them. Splitting the flow removes the dilemma:
 *
 * <ol>
 *   <li>{@link #holdSeats} claims the seats for a few minutes and creates a
 *       PENDING booking. The user now has an exclusive, expiring option on them.</li>
 *   <li>{@link #confirmBooking} turns that option into a sale.</li>
 * </ol>
 *
 * If the user abandons the page, the hold simply lapses and the seats return to
 * sale on their own. Nothing is lost and nothing stays stuck.
 *
 * <h2>How a double-booking is actually prevented</h2>
 *
 * Three independent mechanisms, deliberately layered so that no single mistake
 * can sell one seat twice:
 *
 * <ol>
 *   <li><b>Pessimistic row locks.</b> Every booking transaction begins with
 *       {@code SELECT ... FOR UPDATE} over the requested seats
 *       ({@link ShowSeatDao#lockForUpdate}). Competing transactions queue up at
 *       that statement, so the "is it free?" check and the "take it" write cannot
 *       be split apart by another transaction. The locks are always taken in
 *       ascending seat id, which is what stops two overlapping requests from
 *       deadlocking against each other.</li>
 *   <li><b>Conditional updates.</b> Every state change is a compare-and-set - the
 *       {@code UPDATE} re-states the expected current state in its {@code WHERE}
 *       clause and the code checks the affected-row count. If the count is short,
 *       the transaction rolls back. This holds even if the lock above were
 *       somehow skipped.</li>
 *   <li><b>A unique key.</b> {@code uq_show_seats_show_seat} means one physical
 *       seat can exist only once per show, so the data cannot represent a
 *       double-sold seat even if the application logic were wrong.</li>
 * </ol>
 *
 * <p><b>Why there are no Java locks here.</b> A {@code synchronized} block or a
 * {@code ReentrantLock} only coordinates threads inside one JVM. Run a second
 * copy of this application against the same database - which is exactly what
 * happens behind any load balancer - and in-process locks protect nothing while
 * looking like they do. The database is the only thing both copies share, so the
 * database is where the mutual exclusion has to live.
 */
public class BookingService {

    /** How long a hold survives before the seats go back on sale. */
    private static final int HOLD_MINUTES = AppConfig.getInt("booking.holdMinutes", 8);

    /** Guards against one account sweeping an entire screen. */
    private static final int MAX_SEATS_PER_BOOKING =
            AppConfig.getInt("booking.maxSeatsPerBooking", 10);

    private static final char[] REF_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private final BookingDao bookingDao;
    private final BookingSeatDao bookingSeatDao;
    private final ShowSeatDao showSeatDao;
    private final ShowDao showDao;

    public BookingService() {
        this(new BookingDaoImpl(), new BookingSeatDaoImpl(),
                new ShowSeatDaoImpl(), new ShowDaoImpl());
    }

    /** Constructor injection, so a test can substitute fakes for the DAOs. */
    public BookingService(BookingDao bookingDao, BookingSeatDao bookingSeatDao,
            ShowSeatDao showSeatDao, ShowDao showDao) {
        this.bookingDao = bookingDao;
        this.bookingSeatDao = bookingSeatDao;
        this.showSeatDao = showSeatDao;
        this.showDao = showDao;
    }

    // =======================================================================
    // Step 1 - hold the seats
    // =======================================================================

    /**
     * Reserves seats for this user for the next few minutes and opens a PENDING
     * booking against them.
     *
     * @param requestedSeatIds {@code show_seat_id}s chosen in the UI
     * @throws ValidationException      empty, duplicated or over-limit selection
     * @throws NotFoundException        no such show, or a seat id not on this show
     * @throws ConflictException        the show is cancelled or already started
     * @throws SeatUnavailableException someone else got there first
     */
    public BookingDetails holdSeats(final int userId, final int showId,
            final List<Integer> requestedSeatIds) {

        final List<Integer> seatIds = validateSelection(requestedSeatIds);

        int bookingId = Tx.execute(new Tx.Work<Integer>() {
            @Override
            public Integer execute(Connection connection) throws SQLException {
                Show show = requireBookableShow(showId);
                LocalDateTime now = LocalDateTime.now();

                // --- (1) Take the row locks. Everything below this line is
                //         serialised with respect to any other booking touching
                //         the same seats.
                List<ShowSeat> locked = showSeatDao.lockForUpdate(connection, seatIds);

                if (locked.size() != seatIds.size()) {
                    throw new NotFoundException(
                            "Some of the selected seats do not exist for this show.");
                }

                // --- (2) Now that the rows are pinned, the checks cannot go stale.
                List<Integer> unavailable = new ArrayList<Integer>();
                BigDecimal total = BigDecimal.ZERO;

                for (ShowSeat seat : locked) {
                    if (seat.getShowId() != show.getShowId()) {
                        throw new ValidationException(
                                "Seat selection contains a seat from a different show.");
                    }
                    // A seat this user is already holding may be re-held: it lets
                    // them go back and adjust their basket without being blocked
                    // by their own earlier hold.
                    if (!seat.isSellable(now) && !seat.isHeldBy(userId, now)) {
                        unavailable.add(Integer.valueOf(seat.getShowSeatId()));
                    }
                    total = total.add(seat.getPrice());
                }

                if (!unavailable.isEmpty()) {
                    throw new SeatUnavailableException(
                            labelsFor(connection, unavailable));
                }

                // --- (3) Claim them.
                LocalDateTime holdUntil = now.plusMinutes(HOLD_MINUTES);
                int held = showSeatDao.hold(connection, seatIds, userId, holdUntil);

                if (held != seatIds.size()) {
                    // Cannot happen while the locks above are held, but if it ever
                    // does, rolling back is the only safe answer.
                    throw new SeatUnavailableException(labelsFor(connection, seatIds));
                }

                // --- (4) Record the intent to buy.
                Booking booking = new Booking();
                booking.setBookingRef(generateBookingRef());
                booking.setUserId(userId);
                booking.setShowId(showId);
                booking.setTotalAmount(total);
                booking.setStatus(BookingStatus.PENDING);
                booking.setExpiresAt(holdUntil);

                int newBookingId = bookingDao.createBooking(connection, booking);

                List<BookingSeat> lineItems = new ArrayList<BookingSeat>(locked.size());
                for (ShowSeat seat : locked) {
                    BookingSeat lineItem = new BookingSeat();
                    lineItem.setShowSeatId(seat.getShowSeatId());
                    lineItem.setPrice(seat.getPrice());
                    lineItems.add(lineItem);
                }
                bookingSeatDao.createBookingSeats(connection, newBookingId, lineItems);

                return Integer.valueOf(newBookingId);
            }
        }).intValue();

        return getBookingForUser(userId, bookingId, UserRole.CUSTOMER);
    }

    // =======================================================================
    // Step 2 - confirm
    // =======================================================================

    /**
     * Turns a live hold into a sale.
     *
     * <p>Calling this twice is harmless: the second call sees a booking that is
     * already CONFIRMED and returns it unchanged rather than failing. That
     * matters because users double-click, and because a flaky connection makes
     * the browser retry a request that actually succeeded.
     *
     * @throws ConflictException the hold lapsed, or the booking is in a state
     *                           that cannot be confirmed
     */
    public BookingDetails confirmBooking(final int userId, final int bookingId) {
        Tx.executeVoid(new Tx.VoidWork() {
            @Override
            public void execute(Connection connection) throws SQLException {
                // Locking the booking row first serialises two concurrent clicks
                // on the same booking.
                Booking booking = bookingDao.findByIdForUpdate(connection, bookingId);
                requireOwnedBooking(booking, userId);

                if (booking.getStatus() == BookingStatus.CONFIRMED) {
                    return; // Idempotent: already done by an earlier attempt.
                }
                if (booking.getStatus() != BookingStatus.PENDING) {
                    throw new ConflictException("This booking is "
                            + booking.getStatus().name().toLowerCase()
                            + " and can no longer be confirmed.");
                }

                List<Integer> seatIds = bookingSeatDao.findShowSeatIds(connection, bookingId);
                showSeatDao.lockForUpdate(connection, seatIds);

                if (booking.isExpired(LocalDateTime.now())) {
                    // Release here and now rather than waiting for the sweeper, so
                    // the seats are back on sale the instant we know they are free.
                    showSeatDao.release(connection, seatIds);
                    bookingDao.updateStatus(connection, bookingId, BookingStatus.EXPIRED);
                    throw new ConflictException(
                            "Your " + HOLD_MINUTES + "-minute hold expired. "
                            + "The seats have been released - please select them again.");
                }

                // Compare-and-set: only seats still held by THIS user are sold.
                int sold = showSeatDao.markBooked(connection, seatIds, userId);
                if (sold != seatIds.size()) {
                    throw new ConflictException(
                            "The hold on these seats lapsed while confirming. "
                            + "Please start again.");
                }

                if (!bookingDao.confirm(connection, bookingId)) {
                    throw new ConflictException("This booking was already finalised.");
                }
            }
        });

        return getBookingForUser(userId, bookingId, UserRole.CUSTOMER);
    }

    // =======================================================================
    // Cancellation
    // =======================================================================

    /**
     * Cancels a booking and puts its seats straight back on sale.
     *
     * <p>Cancelling is refused once the show has started - by then the seat has no
     * resale value, which is why every cinema has the same rule.
     */
    public BookingDetails cancelBooking(final int userId, final int bookingId,
            final UserRole role) {

        Tx.executeVoid(new Tx.VoidWork() {
            @Override
            public void execute(Connection connection) throws SQLException {
                Booking booking = bookingDao.findByIdForUpdate(connection, bookingId);
                if (booking == null) {
                    throw new NotFoundException("Booking not found.");
                }
                if (role != UserRole.ADMIN && booking.getUserId() != userId) {
                    throw new AuthorizationException("This booking belongs to another account.");
                }

                if (booking.getStatus() == BookingStatus.CANCELLED) {
                    return; // Idempotent.
                }
                if (booking.getStatus() != BookingStatus.PENDING
                        && booking.getStatus() != BookingStatus.CONFIRMED) {
                    throw new ConflictException("A "
                            + booking.getStatus().name().toLowerCase()
                            + " booking cannot be cancelled.");
                }

                if (role != UserRole.ADMIN && hasStarted(booking.getShowId())) {
                    throw new ConflictException(
                            "The show has already started, so this booking can no longer "
                            + "be cancelled.");
                }

                List<Integer> seatIds = bookingSeatDao.findShowSeatIds(connection, bookingId);
                showSeatDao.lockForUpdate(connection, seatIds);
                showSeatDao.release(connection, seatIds);

                if (!bookingDao.cancel(connection, bookingId)) {
                    throw new ConflictException("This booking could not be cancelled.");
                }
            }
        });

        return getBookingForUser(userId, bookingId, role);
    }

    // =======================================================================
    // The expiry sweeper
    // =======================================================================

    /**
     * Reclaims abandoned holds. Called on a timer by {@link ExpirySweeper}.
     *
     * <p>Reads and writes happen in one transaction so a booking cannot be
     * confirmed in the gap between being listed as expired and being marked as
     * such. Work is capped per pass so a large backlog is cleared over several
     * short transactions instead of one long one that would block live bookings.
     *
     * @return how many bookings were expired
     */
    public int sweepExpiredHolds() {
        return Tx.execute(new Tx.Work<Integer>() {
            @Override
            public Integer execute(Connection connection) throws SQLException {
                List<Integer> expiredIds = bookingDao.findExpiredPendingIds(connection, 200);

                if (!expiredIds.isEmpty()) {
                    List<Integer> seatIds =
                            bookingSeatDao.findShowSeatIdsForBookings(connection, expiredIds);
                    showSeatDao.lockForUpdate(connection, seatIds);
                    showSeatDao.release(connection, seatIds);
                    bookingDao.markExpired(connection, expiredIds);
                }

                // Also catch seats whose hold lapsed without a booking attached -
                // belt and braces against any future code path that holds seats
                // directly.
                showSeatDao.releaseExpiredHolds(connection);

                return Integer.valueOf(expiredIds.size());
            }
        }).intValue();
    }

    // =======================================================================
    // Reads
    // =======================================================================

    /** @return this user's bookings, newest first. */
    public List<BookingDetails> getMyBookings(int userId) {
        try {
            return bookingDao.findDetailsByUserId(userId);
        } catch (SQLException ex) {
            throw new DataAccessException("Could not load your bookings.", ex);
        }
    }

    /**
     * @param role an ADMIN may open any booking; a customer only their own
     * @throws AuthorizationException when a customer asks for someone else's
     */
    public BookingDetails getBookingForUser(int userId, int bookingId, UserRole role) {
        BookingDetails details;
        Booking booking;
        try {
            details = bookingDao.findDetailsById(bookingId);
            booking = bookingDao.findById(bookingId);
        } catch (SQLException ex) {
            throw new DataAccessException("Could not load the booking.", ex);
        }

        if (details == null || booking == null) {
            throw new NotFoundException("Booking not found.");
        }
        if (role != UserRole.ADMIN && booking.getUserId() != userId) {
            throw new AuthorizationException("This booking belongs to another account.");
        }
        return details;
    }

    /** Admin view: every booking placed for a show. */
    public List<Booking> getBookingsForShow(int showId) {
        try {
            return bookingDao.findByShowId(showId);
        } catch (SQLException ex) {
            throw new DataAccessException("Could not load bookings for this show.", ex);
        }
    }

    public int getHoldMinutes() {
        return HOLD_MINUTES;
    }

    public int getMaxSeatsPerBooking() {
        return MAX_SEATS_PER_BOOKING;
    }

    // =======================================================================
    // Helpers
    // =======================================================================

    /**
     * Rejects a selection before any lock is taken.
     *
     * <p>De-duplicating matters for more than tidiness: a repeated id would make
     * the requested count disagree with the number of rows the database reports,
     * and the code would read that as a lost race.
     */
    private List<Integer> validateSelection(List<Integer> requestedSeatIds) {
        if (requestedSeatIds == null || requestedSeatIds.isEmpty()) {
            throw new ValidationException("Select at least one seat.");
        }

        LinkedHashSet<Integer> unique = new LinkedHashSet<Integer>(requestedSeatIds);
        if (unique.size() != requestedSeatIds.size()) {
            throw new ValidationException("The same seat was selected more than once.");
        }
        if (unique.size() > MAX_SEATS_PER_BOOKING) {
            throw new ValidationException(
                    "You can book at most " + MAX_SEATS_PER_BOOKING + " seats at a time.");
        }
        for (Integer id : unique) {
            if (id == null || id.intValue() <= 0) {
                throw new ValidationException("Seat selection contains an invalid id.");
            }
        }
        return new ArrayList<Integer>(unique);
    }

    /** @throws ConflictException if the show cannot be sold right now */
    private Show requireBookableShow(int showId) throws SQLException {
        Show show = showDao.findById(showId);
        if (show == null) {
            throw new NotFoundException("Show not found.");
        }
        if (show.getStatus() == ShowStatus.CANCELLED) {
            throw new ConflictException("This show has been cancelled.");
        }
        if (show.getStatus() == ShowStatus.COMPLETED) {
            throw new ConflictException("This show has already finished.");
        }
        if (startsInThePast(show)) {
            throw new ConflictException("This show has already started.");
        }
        return show;
    }

    private boolean startsInThePast(Show show) {
        return LocalDateTime.of(show.getShowDate(), show.getStartTime())
                .isBefore(LocalDateTime.now());
    }

    private boolean hasStarted(int showId) throws SQLException {
        Show show = showDao.findById(showId);
        return show != null && startsInThePast(show);
    }

    private void requireOwnedBooking(Booking booking, int userId) {
        if (booking == null) {
            throw new NotFoundException("Booking not found.");
        }
        if (booking.getUserId() != userId) {
            throw new AuthorizationException("This booking belongs to another account.");
        }
    }

    /** Turns seat ids into labels for an error message the user can act on. */
    private List<String> labelsFor(Connection connection, List<Integer> showSeatIds)
            throws SQLException {
        Map<Integer, String> labels = showSeatDao.findSeatLabels(connection, showSeatIds);
        List<String> ordered = new ArrayList<String>(showSeatIds.size());
        for (Integer id : showSeatIds) {
            String label = labels.get(id);
            ordered.add(label == null ? ("#" + id) : label);
        }
        Collections.sort(ordered);
        return ordered;
    }

    /**
     * A short, human-friendly reference such as {@code BK-7QK4T2XM}.
     *
     * <p>The alphabet omits I, O, 0 and 1 so a reference read aloud at a counter
     * cannot be mistyped. Uniqueness is still enforced by {@code uq_bookings_ref};
     * this only makes a clash vanishingly unlikely.
     */
    private String generateBookingRef() {
        StringBuilder builder = new StringBuilder(11).append("BK-");
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < 8; i++) {
            builder.append(REF_ALPHABET[random.nextInt(REF_ALPHABET.length)]);
        }
        return builder.toString();
    }
}
