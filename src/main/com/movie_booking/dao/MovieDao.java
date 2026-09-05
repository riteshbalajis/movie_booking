package com.movie_booking.dao;

import com.movie_booking.model.Movie;
import com.movie_booking.model.MovieStatus;
import java.sql.SQLException;
import java.util.List;

/**
 * Access to {@code movies}.
 *
 * <p>Movies are reference data: read constantly, written rarely, and never part
 * of the booking transaction. That is why nothing here takes a {@link
 * java.sql.Connection} - each call is a self-contained unit of work.
 *
 * <p>Note there is no {@code delete}. A film that has already been booked cannot
 * be removed without orphaning those tickets, so retirement is done by setting
 * the status to {@code INACTIVE} instead. It disappears from the listings while
 * every past booking still resolves.
 */
public interface MovieDao {

    int createMovie(Movie movie) throws SQLException;

    Movie findById(int movieId) throws SQLException;

    Movie findByTitle(String title) throws SQLException;

    List<Movie> findAll() throws SQLException;

    /** Currently-screening films, newest release first - the home page listing. */
    List<Movie> findAllActive() throws SQLException;

    List<Movie> findAllByStatus(MovieStatus status) throws SQLException;

    List<Movie> findAllByLanguage(String language) throws SQLException;

    List<Movie> findAllByGenre(String genre) throws SQLException;

    /** Case-insensitive substring match across title, genre and language. */
    List<Movie> searchMovies(String keyword) throws SQLException;

    boolean existsById(int movieId) throws SQLException;

    boolean updateMovie(Movie movie) throws SQLException;

    boolean activateMovie(int movieId) throws SQLException;

    /** Soft delete: hides the film from listings without touching its history. */
    boolean deactivateMovie(int movieId) throws SQLException;
}
