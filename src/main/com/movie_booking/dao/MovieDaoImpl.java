package com.movie_booking.dao;

import com.movie_booking.model.Movie;
import com.movie_booking.model.MovieStatus;
import com.movie_booking.util.DBConnection;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class MovieDaoImpl implements MovieDao {
    private static final String BASE_SELECT = "SELECT movie_id, title, description, "
            + "duration_minutes, language, genre, release_date, status, created_at FROM movies";

    @Override
    public int createMovie(Movie movie) throws SQLException {
        String sql = "INSERT INTO movies (title, description, duration_minutes, language, genre, "
                + "release_date, status) VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql,
                        Statement.RETURN_GENERATED_KEYS)) {
            setCreateMovieParameters(statement, movie);
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }

        throw new SQLException("Creating movie failed: no ID was generated.");
    }

    @Override
    public Movie findById(int movieId) throws SQLException {
        String sql = BASE_SELECT + " WHERE movie_id = ?";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, movieId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? mapMovie(resultSet) : null;
            }
        }
    }

    @Override
    public Movie findByTitle(String title) throws SQLException {
        String sql = BASE_SELECT + " WHERE title = ?";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, title);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? mapMovie(resultSet) : null;
            }
        }
    }

    @Override
    public List<Movie> findAll() throws SQLException {
        return findMovies(BASE_SELECT + " ORDER BY movie_id");
    }

    @Override
    public List<Movie> findActiveMovies() throws SQLException {
        String sql = BASE_SELECT + " WHERE status = ? ORDER BY release_date, title";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, MovieStatus.ACTIVE.name());
            return readMovies(statement);
        }
    }

    @Override
    public List<Movie> findAllActive() throws SQLException {
        return findActiveMovies();
    }

    @Override
    public List<Movie> findAllByStatus(MovieStatus status) throws SQLException {
        return findMoviesByValue(BASE_SELECT + " WHERE status = ? ORDER BY title", status.name());
    }

    @Override
    public List<Movie> findAllByLanguage(String language) throws SQLException {
        return findMoviesByValue(BASE_SELECT + " WHERE language = ? ORDER BY title", language);
    }

    @Override
    public List<Movie> findAllByGenre(String genre) throws SQLException {
        return findMoviesByValue(BASE_SELECT + " WHERE genre = ? ORDER BY title", genre);
    }

    @Override
    public List<Movie> searchMovies(String keyword) throws SQLException {
        String sql = BASE_SELECT + " WHERE title LIKE ? OR genre LIKE ? OR language LIKE ? "
                + "ORDER BY title";
        String searchPattern = "%" + keyword + "%";

        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, searchPattern);
            statement.setString(2, searchPattern);
            statement.setString(3, searchPattern);
            return readMovies(statement);
        }
    }

    @Override
    public List<Movie> searchByTitle(String keyword) throws SQLException {
        String sql = BASE_SELECT + " WHERE title LIKE ? ORDER BY title";
        return findMoviesByValue(sql, "%" + keyword + "%");
    }

    @Override
    public boolean existsById(int movieId) throws SQLException {
        String sql = "SELECT 1 FROM movies WHERE movie_id = ? LIMIT 1";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, movieId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    @Override
    public boolean updateMovie(Movie movie) throws SQLException {
        String sql = "UPDATE movies SET title = ?, description = ?, duration_minutes = ?, "
                + "language = ?, genre = ?, release_date = ? WHERE movie_id = ?";

        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, movie.getTitle());
            statement.setString(2, movie.getDescription());
            statement.setInt(3, movie.getDurationMinutes());
            statement.setString(4, movie.getLanguage());
            statement.setString(5, movie.getGenre());
            setNullableDate(statement, 6, movie);
            statement.setInt(7, movie.getMovieId());
            return statement.executeUpdate() > 0;
        }
    }

    @Override
    public boolean deactivateMovie(int movieId) throws SQLException {
        return updateStatus(movieId, MovieStatus.INACTIVE);
    }

    @Override
    public boolean activateMovie(int movieId) throws SQLException {
        return updateStatus(movieId, MovieStatus.ACTIVE);
    }

    private boolean updateStatus(int movieId, MovieStatus status) throws SQLException {
        String sql = "UPDATE movies SET status = ? WHERE movie_id = ?";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name());
            statement.setInt(2, movieId);
            return statement.executeUpdate() > 0;
        }
    }

    private void setCreateMovieParameters(PreparedStatement statement, Movie movie) throws SQLException {
        statement.setString(1, movie.getTitle());
        statement.setString(2, movie.getDescription());
        statement.setInt(3, movie.getDurationMinutes());
        statement.setString(4, movie.getLanguage());
        statement.setString(5, movie.getGenre());
        setNullableDate(statement, 6, movie);
        statement.setString(7, movie.getStatus() == null
                ? MovieStatus.UPCOMING.name() : movie.getStatus().name());
    }

    private void setNullableDate(PreparedStatement statement, int parameterIndex, Movie movie)
            throws SQLException {
        if (movie.getReleaseDate() == null) {
            statement.setNull(parameterIndex, java.sql.Types.DATE);
        } else {
            statement.setDate(parameterIndex, Date.valueOf(movie.getReleaseDate()));
        }
    }

    private List<Movie> findMovies(String sql) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            return readMovies(statement);
        }
    }

    private List<Movie> findMoviesByValue(String sql, String value) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            return readMovies(statement);
        }
    }

    private List<Movie> readMovies(PreparedStatement statement) throws SQLException {
        List<Movie> movies = new ArrayList<>();
        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                movies.add(mapMovie(resultSet));
            }
        }
        return movies;
    }

    private Movie mapMovie(ResultSet resultSet) throws SQLException {
        Date releaseDate = resultSet.getDate("release_date");
        Timestamp createdAt = resultSet.getTimestamp("created_at");
        Movie movie = new Movie();
        movie.setMovieId(resultSet.getInt("movie_id"));
        movie.setTitle(resultSet.getString("title"));
        movie.setDescription(resultSet.getString("description"));
        movie.setDurationMinutes(resultSet.getInt("duration_minutes"));
        movie.setLanguage(resultSet.getString("language"));
        movie.setGenre(resultSet.getString("genre"));
        movie.setReleaseDate(releaseDate == null ? null : releaseDate.toLocalDate());
        movie.setStatus(MovieStatus.valueOf(resultSet.getString("status")));
        movie.setCreatedAt(createdAt == null ? null : createdAt.toLocalDateTime());
        return movie;
    }
}
