package com.movie_booking.dao;

import com.movie_booking.model.Show;
import com.movie_booking.model.ShowStatus;
import com.movie_booking.util.DBConnection;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ShowDaoImpl implements ShowDao {
    private static final String BASE_SELECT = "SELECT show_id, movie_id, screen_id, show_date, "
            + "start_time, end_time, status, created_at FROM shows";
    private static final String ORDER_BY_TIME = " ORDER BY show_date, start_time";

    @Override
    public int createShow(Show show) throws SQLException {
        String sql = "INSERT INTO shows (movie_id, screen_id, show_date, start_time, end_time, status) "
                + "VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql,
                        Statement.RETURN_GENERATED_KEYS)) {
            setShowParameters(statement, show);
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }

        throw new SQLException("Creating show failed: no ID was generated.");
    }

    @Override
    public Show findById(int showId) throws SQLException {
        String sql = BASE_SELECT + " WHERE show_id = ?";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, showId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? mapShow(resultSet) : null;
            }
        }
    }

    @Override
    public List<Show> findAll() throws SQLException {
        return findShows(BASE_SELECT + ORDER_BY_TIME);
    }

    @Override
    public List<Show> findShowsByMovieId(int movieId) throws SQLException {
        return findShowsByInt(BASE_SELECT + " WHERE movie_id = ?" + ORDER_BY_TIME, movieId);
    }

    @Override
    public List<Show> findShowsByScreenId(int screenId) throws SQLException {
        return findShowsByInt(BASE_SELECT + " WHERE screen_id = ?" + ORDER_BY_TIME, screenId);
    }

    @Override
    public List<Show> findShowsByDate(LocalDate showDate) throws SQLException {
        return findShowsByDateValue(BASE_SELECT + " WHERE show_date = ?" + ORDER_BY_TIME, showDate);
    }

    @Override
    public List<Show> findShowsByMovieAndDate(int movieId, LocalDate showDate)
            throws SQLException {
        String sql = BASE_SELECT + " WHERE movie_id = ? AND show_date = ?" + ORDER_BY_TIME;
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, movieId);
            statement.setDate(2, Date.valueOf(showDate));
            return readShows(statement);
        }
    }

    @Override
    public List<Show> findShowsByScreenAndDate(int screenId, LocalDate showDate)
            throws SQLException {
        String sql = BASE_SELECT + " WHERE screen_id = ? AND show_date = ?" + ORDER_BY_TIME;
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, screenId);
            statement.setDate(2, Date.valueOf(showDate));
            return readShows(statement);
        }
    }

    @Override
    public List<Show> findShowsByTheatreAndDate(int theatreId, LocalDate showDate)
            throws SQLException {
        String sql = "SELECT s.show_id, s.movie_id, s.screen_id, s.show_date, s.start_time, "
                + "s.end_time, s.status, s.created_at FROM shows s "
                + "JOIN screens sc ON s.screen_id = sc.screen_id "
                + "WHERE sc.theatre_id = ? AND s.show_date = ?"
                + " ORDER BY s.show_date, s.start_time";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, theatreId);
            statement.setDate(2, Date.valueOf(showDate));
            return readShows(statement);
        }
    }

    @Override
    public List<Show> findUpcomingShows() throws SQLException {
        String sql = BASE_SELECT + " WHERE status = ? AND (show_date > CURRENT_DATE "
                + "OR (show_date = CURRENT_DATE AND start_time > CURRENT_TIME))"
                + ORDER_BY_TIME;
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, ShowStatus.SCHEDULED.name());
            return readShows(statement);
        }
    }

    @Override
    public List<Show> findActiveShowsByMovieId(int movieId) throws SQLException {
        String sql = BASE_SELECT + " WHERE movie_id = ? AND status IN (?, ?)" + ORDER_BY_TIME;
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, movieId);
            statement.setString(2, ShowStatus.SCHEDULED.name());
            statement.setString(3, ShowStatus.ONGOING.name());
            return readShows(statement);
        }
    }

    @Override
    public boolean updateShow(Show show) throws SQLException {
        String sql = "UPDATE shows SET movie_id = ?, screen_id = ?, show_date = ?, "
                + "start_time = ?, end_time = ? WHERE show_id = ?";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, show.getMovieId());
            statement.setInt(2, show.getScreenId());
            statement.setDate(3, Date.valueOf(show.getShowDate()));
            statement.setTime(4, Time.valueOf(show.getStartTime()));
            statement.setTime(5, Time.valueOf(show.getEndTime()));
            statement.setInt(6, show.getShowId());
            return statement.executeUpdate() > 0;
        }
    }

    @Override
    public boolean updateShowStatus(int showId, ShowStatus status) throws SQLException {
        String sql = "UPDATE shows SET status = ? WHERE show_id = ?";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name());
            statement.setInt(2, showId);
            return statement.executeUpdate() > 0;
        }
    }

    @Override
    public boolean cancelShow(int showId) throws SQLException {
        return updateShowStatus(showId, ShowStatus.CANCELLED);
    }

    @Override
    public boolean completeShow(int showId) throws SQLException {
        return updateShowStatus(showId, ShowStatus.COMPLETED);
    }

    @Override
    public boolean existsById(int showId) throws SQLException {
        String sql = "SELECT 1 FROM shows WHERE show_id = ? LIMIT 1";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, showId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private void setShowParameters(PreparedStatement statement, Show show) throws SQLException {
        statement.setInt(1, show.getMovieId());
        statement.setInt(2, show.getScreenId());
        statement.setDate(3, Date.valueOf(show.getShowDate()));
        statement.setTime(4, Time.valueOf(show.getStartTime()));
        statement.setTime(5, Time.valueOf(show.getEndTime()));
        statement.setString(6, show.getStatus() == null
                ? ShowStatus.SCHEDULED.name() : show.getStatus().name());
    }

    private List<Show> findShows(String sql) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            return readShows(statement);
        }
    }

    private List<Show> findShowsByInt(String sql, int value) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, value);
            return readShows(statement);
        }
    }

    private List<Show> findShowsByDateValue(String sql, LocalDate showDate) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setDate(1, Date.valueOf(showDate));
            return readShows(statement);
        }
    }

    private List<Show> readShows(PreparedStatement statement) throws SQLException {
        List<Show> shows = new ArrayList<>();
        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                shows.add(mapShow(resultSet));
            }
        }
        return shows;
    }

    private Show mapShow(ResultSet resultSet) throws SQLException {
        Date showDate = resultSet.getDate("show_date");
        Time startTime = resultSet.getTime("start_time");
        Time endTime = resultSet.getTime("end_time");
        Timestamp createdAt = resultSet.getTimestamp("created_at");
        Show show = new Show();
        show.setShowId(resultSet.getInt("show_id"));
        show.setMovieId(resultSet.getInt("movie_id"));
        show.setScreenId(resultSet.getInt("screen_id"));
        show.setShowDate(showDate == null ? null : showDate.toLocalDate());
        show.setStartTime(startTime == null ? null : startTime.toLocalTime());
        show.setEndTime(endTime == null ? null : endTime.toLocalTime());
        show.setStatus(ShowStatus.valueOf(resultSet.getString("status")));
        show.setCreatedAt(createdAt == null ? null : createdAt.toLocalDateTime());
        return show;
    }
}
