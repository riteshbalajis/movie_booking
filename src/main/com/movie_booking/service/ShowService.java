package com.movie_booking.service;

import com.movie_booking.dao.BookingDao;
import com.movie_booking.dao.BookingDaoImpl;
import com.movie_booking.dao.BookingSeatDao;
import com.movie_booking.dao.BookingSeatDaoImpl;
import com.movie_booking.dao.MovieDao;
import com.movie_booking.dao.MovieDaoImpl;
import com.movie_booking.dao.ScreenDao;
import com.movie_booking.dao.ScreenDaoImpl;
import com.movie_booking.dao.ShowDao;
import com.movie_booking.dao.ShowDaoImpl;
import com.movie_booking.dao.ShowSeatDao;
import com.movie_booking.dao.ShowSeatDaoImpl;
import com.movie_booking.dto.SeatMapEntry;
import com.movie_booking.dto.ShowSummary;
import com.movie_booking.exception.ConflictException;
import com.movie_booking.exception.DataAccessException;
import com.movie_booking.exception.NotFoundException;
import com.movie_booking.exception.ValidationException;
import com.movie_booking.model.Booking;
import com.movie_booking.model.BookingStatus;
import com.movie_booking.model.Movie;
import com.movie_booking.model.Screen;
import com.movie_booking.model.Show;
import com.movie_booking.model.ShowStatus;
import com.movie_booking.util.Tx;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Scheduling shows, and serving the seat map that customers book from.
 */
public class ShowService {

    /** Minimum gap between one show ending and the next starting on that screen. */
    private static final int TURNAROUND_MINUTES = 15;

    private final ShowDao showDao;
    private final ShowSeatDao showSeatDao;
    private final MovieDao movieDao;
    private final ScreenDao screenDao;
    private final BookingDao bookingDao;
    private final BookingSeatDao bookingSeatDao;

    public ShowService() {
        this(new ShowDaoImpl(), new ShowSeatDaoImpl(), new MovieDaoImpl(),
                new ScreenDaoImpl(), new BookingDaoImpl(), new BookingSeatDaoImpl());
    }

    public ShowService(ShowDao showDao, ShowSeatDao showSeatDao, MovieDao movieDao,
            ScreenDao screenDao, BookingDao bookingDao, BookingSeatDao bookingSeatDao) {
        this.showDao = showDao;
        this.showSeatDao = showSeatDao;
        this.movieDao = movieDao;
        this.screenDao = screenDao;
        this.bookingDao = bookingDao;
        this.bookingSeatDao = bookingSeatDao;
    }

    // =======================================================================
    // Customer-facing reads
    // =======================================================================

    /** Showtimes for one film on one date. */
    public List<ShowSummary> listShows(int movieId, LocalDate date) {
        try {
            return showDao.findSummariesByMovieAndDate(movieId, date == null
                    ? LocalDate.now() : date);
        } catch (SQLException ex) {
            throw new DataAccessException("Could not load showtimes.", ex);
        }
    }

    public List<ShowSummary> listShowsByDate(LocalDate date) {
        try {
            return showDao.findSummariesByDate(date == null ? LocalDate.now() : date);
        } catch (SQLException ex) {
            throw new DataAccessException("Could not load showtimes.", ex);
        }
    }

    public ShowSummary getShow(int showId) {
        try {
            ShowSummary summary = showDao.findSummaryById(showId);
            if (summary == null) {
                throw new NotFoundException("Show not found.");
            }
            return summary;
        } catch (SQLException ex) {
            throw new DataAccessException("Could not load the show.", ex);
        }
    }

    /**
     * The seat grid a customer picks from.
     *
     * @param viewerUserId the signed-in user, or {@code 0} when anonymous, so
     *                     seats they are already holding can be highlighted
     */
    public List<SeatMapEntry> getSeatMap(int showId, int viewerUserId) {
        try {
            if (!showDao.existsById(showId)) {
                throw new NotFoundException("Show not found.");
            }
            return showSeatDao.findSeatMap(showId, viewerUserId);
        } catch (SQLException ex) {
            throw new DataAccessException("Could not load the seat map.", ex);
        }
    }

    // =======================================================================
    // Admin - scheduling
    // =======================================================================

    /**
     * Schedules a show and materialises every seat for it.
     *
     * <p>Both steps run in one transaction. If seat creation failed after the show
     * row was committed, the show would appear in the listings with nothing to
     * sell, and every booking attempt against it would fail.
     *
     * @param regularPrice  price for REGULAR seats on this show
     * @param premiumPrice  price for PREMIUM seats
     * @param reclinerPrice price for RECLINER seats
     * @return the created show, with its generated id
     * @throws ConflictException the screen is already busy in that slot
     */
    public Show scheduleShow(final int movieId, final int screenId, final LocalDate showDate,
            final LocalTime startTime, final BigDecimal regularPrice,
            final BigDecimal premiumPrice, final BigDecimal reclinerPrice) {

        validatePrices(regularPrice, premiumPrice, reclinerPrice);

        if (showDate == null || startTime == null) {
            throw new ValidationException("Show date and start time are required.");
        }
        if (LocalDateTime.of(showDate, startTime).isBefore(LocalDateTime.now())) {
            throw new ValidationException("A show cannot be scheduled in the past.");
        }

        return Tx.execute(new Tx.Work<Show>() {
            @Override
            public Show execute(Connection connection) throws SQLException {
                Movie movie = movieDao.findById(movieId);
                if (movie == null) {
                    throw new NotFoundException("Film not found.");
                }
                Screen screen = screenDao.findById(screenId);
                if (screen == null) {
                    throw new NotFoundException("Screen not found.");
                }

                // End time is derived, never supplied: the runtime plus a
                // turnaround for cleaning and letting the next audience in.
                LocalTime endTime = startTime
                        .plusMinutes(movie.getDurationMinutes())
                        .plusMinutes(TURNAROUND_MINUTES);

                if (endTime.isBefore(startTime)) {
                    throw new ValidationException(
                            "This show would run past midnight. Schedule it earlier, or "
                            + "split it across the date boundary.");
                }
                if (showDao.hasScheduleConflict(connection, screenId, showDate,
                        startTime, endTime, 0)) {
                    throw new ConflictException(
                            "That screen is already showing something in this time slot.");
                }

                Show show = new Show();
                show.setMovieId(movieId);
                show.setScreenId(screenId);
                show.setShowDate(showDate);
                show.setStartTime(startTime);
                show.setEndTime(endTime);
                show.setStatus(ShowStatus.SCHEDULED);

                int showId = showDao.createShow(connection, show);
                show.setShowId(showId);

                int seatsCreated = showSeatDao.createShowSeatsForShow(connection, showId,
                        screenId, regularPrice, premiumPrice, reclinerPrice);

                if (seatsCreated == 0) {
                    throw new ConflictException(
                            "That screen has no active seats, so no tickets could be created.");
                }
                return show;
            }
        });
    }

    /**
     * Cancels a show and cancels every live booking on it, freeing the seats.
     *
     * <p>One transaction, because a cancelled show whose bookings still say
     * CONFIRMED would leave customers holding tickets for a screening that is not
     * happening.
     *
     * @return how many bookings were cancelled as a result
     */
    public int cancelShow(final int showId) {
        return Tx.execute(new Tx.Work<Integer>() {
            @Override
            public Integer execute(Connection connection) throws SQLException {
                if (!showDao.cancelShow(connection, showId)) {
                    throw new ConflictException(
                            "Only a scheduled show can be cancelled - this one has already "
                            + "run or was cancelled earlier.");
                }

                int cancelled = 0;
                for (Booking booking : bookingDao.findByShowId(showId)) {
                    if (booking.getStatus() != BookingStatus.PENDING
                            && booking.getStatus() != BookingStatus.CONFIRMED) {
                        continue;
                    }

                    List<Integer> seatIds =
                            bookingSeatDao.findShowSeatIds(connection, booking.getBookingId());
                    showSeatDao.lockForUpdate(connection, seatIds);
                    showSeatDao.release(connection, seatIds);
                    bookingDao.cancel(connection, booking.getBookingId());
                    cancelled++;
                }
                return Integer.valueOf(cancelled);
            }
        }).intValue();
    }

    public void completeShow(int showId) {
        try {
            if (!showDao.completeShow(showId)) {
                throw new NotFoundException("Show not found.");
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Could not close the show.", ex);
        }
    }

    private void validatePrices(BigDecimal... prices) {
        for (BigDecimal price : prices) {
            if (price == null) {
                throw new ValidationException("All three seat prices are required.");
            }
            if (price.signum() <= 0) {
                throw new ValidationException("Seat prices must be greater than zero.");
            }
        }
    }
}
