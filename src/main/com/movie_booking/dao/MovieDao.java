package com.movie_booking.dao;

import com.movie_booking.model.Movie;
import java.sql.SQLException;
import java.util.List;

public interface MovieDao {
    int createMovie(Movie movie) throws SQLException;

    Movie findById(int movieId) throws SQLException;

    Movie findByTitle(String title) throws SQLException;

    List<Movie> findAll() throws SQLException;

    List<Movie> findActiveMovies() throws SQLException;

    List<Movie> findAllActive() throws SQLException;

    List<Movie> findAllByStatus(com.movie_booking.model.MovieStatus status) throws SQLException;

    List<Movie> findAllByLanguage(String language) throws SQLException;

    List<Movie> findAllByGenre(String genre) throws SQLException;

    List<Movie> searchMovies(String keyword) throws SQLException;

    List<Movie> searchByTitle(String keyword) throws SQLException;

    boolean existsById(int movieId) throws SQLException;

    boolean updateMovie(Movie movie) throws SQLException;

    boolean deactivateMovie(int movieId) throws SQLException;

    boolean activateMovie(int movieId) throws SQLException;
}
