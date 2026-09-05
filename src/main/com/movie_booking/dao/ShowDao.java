package com.movie_booking.dao;

import com.movie_booking.model.Show;
import com.movie_booking.model.ShowStatus;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public interface ShowDao {
    int createShow(Show show) throws SQLException;

    Show findById(int showId) throws SQLException;

    List<Show> findAll() throws SQLException;

    List<Show> findShowsByMovieId(int movieId) throws SQLException;

    List<Show> findShowsByScreenId(int screenId) throws SQLException;

    List<Show> findShowsByDate(LocalDate showDate) throws SQLException;

    List<Show> findShowsByMovieAndDate(int movieId, LocalDate showDate) throws SQLException;

    List<Show> findShowsByScreenAndDate(int screenId, LocalDate showDate) throws SQLException;

    List<Show> findShowsByTheatreAndDate(int theatreId, LocalDate showDate) throws SQLException;

    List<Show> findUpcomingShows() throws SQLException;

    List<Show> findActiveShowsByMovieId(int movieId) throws SQLException;

    boolean updateShow(Show show) throws SQLException;

    boolean updateShowStatus(int showId, ShowStatus status) throws SQLException;

    boolean cancelShow(int showId) throws SQLException;

    boolean completeShow(int showId) throws SQLException;

    boolean existsById(int showId) throws SQLException;
}
