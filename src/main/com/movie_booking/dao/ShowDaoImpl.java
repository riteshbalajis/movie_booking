package com.movie_booking.dao;

import com.movie_booking.dto.ShowSummary;
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
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class ShowDaoImpl implements ShowDao {
    private static final String BASE_SELECT = "SELECT show_id, movie_id, screen_id, show_date, "
            + "start_time, end_time, status, created_at FROM shows";
    private static final String ORDER_BY_TIME = " ORDER BY show_date, start_time";

    @Override
    public int createShow(Connection connection, Show show) throws SQLException {
        String sql = "INSERT INTO shows "
                + "(movie_id, screen_id, show_date, start_time, end_time, status) "
                + "VALUES (?, ?, ?, ?, ?, ?)";

        try (PreparedStatement statement = connection.prepareStatement(sql,
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
    public boolean cancelShow(Connection connection, int showId) throws SQLException {
        // Only a show that has not run yet can be cancelled, and the caller must
        // be inside a transaction because the seats have to be freed with it.
        String sql = "UPDATE shows SET status = 'CANCELLED' "
                + "WHERE show_id = ? AND status = 'SCHEDULED'";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, showId);
            return statement.executeUpdate() > 0;
        }
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

    // -----------------------------------------------------------------------
    // Joined listing views
    // -----------------------------------------------------------------------

    /**
     * Listing query behind {@link ShowSummary}.
     *
     * <p>The three correlated sub-queries resolve seat counts and the lowest price
     * inside SQL. That keeps a whole listing page to one round trip; fetching the
     * shows and then counting seats per show in Java is the N+1 pattern that turns
     * a 20-show page into 61 queries.
     *
     * <p>The availability count repeats the "sellable" rule used everywhere else:
     * AVAILABLE, or LOCKED by a hold that has already lapsed.
     */
    private static final String SUMMARY_SELECT =
            "SELECT sh.show_id, sh.status, sh.show_date, sh.start_time, sh.end_time, "
            + "       m.movie_id, m.title, m.language, m.genre, m.duration_minutes, "
            + "       t.theatre_id, t.name AS theatre_name, t.location AS theatre_location, "
            + "       sc.screen_id, sc.name AS screen_name, "
            + "       (SELECT COUNT(*) FROM show_seats ss "
            + "         WHERE ss.show_id = sh.show_id "
            + "           AND (ss.status = 'AVAILABLE' "
            + "                OR (ss.status = 'LOCKED' AND ss.locked_until < NOW()))"
            + "       ) AS available_seats, "
            + "       (SELECT COUNT(*) FROM show_seats ss2 "
            + "         WHERE ss2.show_id = sh.show_id) AS total_seats, "
            + "       (SELECT MIN(ss3.price) FROM show_seats ss3 "
            + "         WHERE ss3.show_id = sh.show_id) AS min_price "
            + "FROM shows sh "
            + "JOIN movies m   ON m.movie_id = sh.movie_id "
            + "JOIN screens sc ON sc.screen_id = sh.screen_id "
            + "JOIN theatres t ON t.theatre_id = sc.theatre_id ";

    @Override
    public List<ShowSummary> findSummariesByMovieAndDate(int movieId, LocalDate showDate)
            throws SQLException {
        String sql = SUMMARY_SELECT
                + "WHERE sh.movie_id = ? AND sh.show_date = ? AND sh.status = 'SCHEDULED' "
                + "ORDER BY t.name, sc.name, sh.start_time";

        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, movieId);
            statement.setDate(2, Date.valueOf(showDate));
            return readSummaries(statement);
        }
    }

    @Override
    public List<ShowSummary> findSummariesByDate(LocalDate showDate) throws SQLException {
        String sql = SUMMARY_SELECT
                + "WHERE sh.show_date = ? ORDER BY t.name, sc.name, sh.start_time";

        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setDate(1, Date.valueOf(showDate));
            return readSummaries(statement);
        }
    }

    @Override
    public ShowSummary findSummaryById(int showId) throws SQLException {
        String sql = SUMMARY_SELECT + "WHERE sh.show_id = ?";

        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, showId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? mapSummary(resultSet) : null;
            }
        }
    }

    @Override
    public boolean hasScheduleConflict(Connection connection, int screenId, LocalDate showDate,
            LocalTime startTime, LocalTime endTime, int excludeShowId) throws SQLException {

        // Two half-open intervals [a1,a2) and [b1,b2) overlap exactly when
        // a1 < b2 AND b1 < a2. A cancelled show does not occupy the screen.
        String sql = "SELECT 1 FROM shows "
                + "WHERE screen_id = ? AND show_date = ? AND status <> 'CANCELLED' "
                + "AND show_id <> ? "
                + "AND start_time < ? AND ? < end_time "
                + "LIMIT 1";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, screenId);
            statement.setDate(2, Date.valueOf(showDate));
            statement.setInt(3, excludeShowId);
            statement.setTime(4, Time.valueOf(endTime));
            statement.setTime(5, Time.valueOf(startTime));
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private List<ShowSummary> readSummaries(PreparedStatement statement) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery()) {
            List<ShowSummary> summaries = new ArrayList<>();
            while (resultSet.next()) {
                summaries.add(mapSummary(resultSet));
            }
            return summaries;
        }
    }

    private ShowSummary mapSummary(ResultSet resultSet) throws SQLException {
        Date showDate = resultSet.getDate("show_date");
        Time startTime = resultSet.getTime("start_time");
        Time endTime = resultSet.getTime("end_time");

        return new ShowSummary(
                resultSet.getInt("show_id"),
                ShowStatus.valueOf(resultSet.getString("status")),
                showDate == null ? null : showDate.toLocalDate(),
                startTime == null ? null : startTime.toLocalTime(),
                endTime == null ? null : endTime.toLocalTime(),
                resultSet.getInt("movie_id"),
                resultSet.getString("title"),
                resultSet.getString("language"),
                resultSet.getString("genre"),
                resultSet.getInt("duration_minutes"),
                resultSet.getInt("theatre_id"),
                resultSet.getString("theatre_name"),
                resultSet.getString("theatre_location"),
                resultSet.getInt("screen_id"),
                resultSet.getString("screen_name"),
                resultSet.getInt("available_seats"),
                resultSet.getInt("total_seats"),
                resultSet.getBigDecimal("min_price"));
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
