package com.movie_booking.dao;

import com.movie_booking.dto.ShowSummary;
import com.movie_booking.model.Show;
import com.movie_booking.model.ShowStatus;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Access to {@code shows}.
 *
 * <p>{@link #createShow(Connection, Show)} takes a connection because scheduling
 * a show and materialising its {@code show_seats} must be one atomic step: a show
 * that exists with no seats would appear in the listings and then fail every
 * booking attempt.
 */
public interface ShowDao {

    int createShow(Connection connection, Show show) throws SQLException;

    // -----------------------------------------------------------------------
    // Reads
    // -----------------------------------------------------------------------

    Show findById(int showId) throws SQLException;

    List<Show> findAll() throws SQLException;

    List<Show> findShowsByMovieId(int movieId) throws SQLException;

    List<Show> findShowsByScreenId(int screenId) throws SQLException;

    List<Show> findShowsByDate(LocalDate showDate) throws SQLException;

    List<Show> findShowsByMovieAndDate(int movieId, LocalDate showDate) throws SQLException;

    List<Show> findShowsByScreenAndDate(int screenId, LocalDate showDate) throws SQLException;

    List<Show> findShowsByTheatreAndDate(int theatreId, LocalDate showDate) throws SQLException;

    List<Show> findUpcomingShows() throws SQLException;

    // -- joined listing views ------------------------------------------------

    /**
     * Showtimes for one film on one date, with theatre, screen, price and live
     * seat availability resolved in the same query.
     */
    List<ShowSummary> findSummariesByMovieAndDate(int movieId, LocalDate showDate)
            throws SQLException;

    /** Every scheduled show on a date - the admin dashboard view. */
    List<ShowSummary> findSummariesByDate(LocalDate showDate) throws SQLException;

    ShowSummary findSummaryById(int showId) throws SQLException;

    /**
     * Whether the given time slot collides with a show already scheduled on that
     * screen.
     *
     * <p>The unique key on {@code (screen_id, show_date, start_time)} only stops
     * two shows starting at the same minute; it cannot stop a 3-hour film being
     * booked to start thirty minutes into another one. This does.
     *
     * @param excludeShowId the show being edited, or {@code 0} when creating,
     *                      so a show is not reported as clashing with itself
     */
    boolean hasScheduleConflict(Connection connection, int screenId, LocalDate showDate,
            LocalTime startTime, LocalTime endTime, int excludeShowId) throws SQLException;

    boolean existsById(int showId) throws SQLException;

    // -----------------------------------------------------------------------
    // Mutations
    // -----------------------------------------------------------------------

    boolean updateShow(Show show) throws SQLException;

    boolean updateShowStatus(int showId, ShowStatus status) throws SQLException;

    boolean cancelShow(Connection connection, int showId) throws SQLException;

    boolean completeShow(int showId) throws SQLException;
}
