package com.movie_booking.dao;

import com.movie_booking.dto.BookingDetails;
import com.movie_booking.model.Booking;
import com.movie_booking.model.BookingStatus;
import com.movie_booking.util.DBConnection;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** JDBC implementation of {@link BookingDao}. */
public class BookingDaoImpl implements BookingDao {

    private static final String BASE_SELECT =
            "SELECT booking_id, booking_ref, user_id, show_id, total_amount, "
            + "status, booked_at, expires_at FROM bookings";

    private static final String ORDER_NEWEST_FIRST = " ORDER BY booked_at DESC, booking_id DESC";

    /**
     * The joined view behind {@link BookingDetails}.
     *
     * <p>{@code GROUP_CONCAT} collapses the booking's seat rows into one ordered
     * string ({@code "D5,D6,D7"}) so the whole thing stays a single result row per
     * booking. The alternative - a second query per booking - is the classic N+1
     * that makes a list page slow the moment it has real data in it.
     */
    private static final String DETAILS_SELECT =
            "SELECT b.booking_id, b.booking_ref, b.status, b.total_amount, "
            + "       b.booked_at, b.expires_at, b.show_id, "
            + "       m.title, m.language, m.duration_minutes, "
            + "       t.name AS theatre_name, t.location AS theatre_location, "
            + "       sc.name AS screen_name, sh.show_date, sh.start_time, "
            + "       GROUP_CONCAT(CONCAT(se.row_label, se.seat_number) "
            + "                    ORDER BY se.row_label, se.seat_number SEPARATOR ',') "
            + "                    AS seat_labels "
            + "FROM bookings b "
            + "JOIN shows sh        ON sh.show_id = b.show_id "
            + "JOIN movies m        ON m.movie_id = sh.movie_id "
            + "JOIN screens sc      ON sc.screen_id = sh.screen_id "
            + "JOIN theatres t      ON t.theatre_id = sc.theatre_id "
            + "LEFT JOIN booking_seats bs ON bs.booking_id = b.booking_id "
            + "LEFT JOIN show_seats ss    ON ss.show_seat_id = bs.show_seat_id "
            + "LEFT JOIN seats se         ON se.seat_id = ss.seat_id ";

    // -----------------------------------------------------------------------
    // Creation
    // -----------------------------------------------------------------------

    @Override
    public int createBooking(Connection connection, Booking booking) throws SQLException {
        String sql = "INSERT INTO bookings "
                + "(booking_ref, user_id, show_id, total_amount, status, expires_at) "
                + "VALUES (?, ?, ?, ?, ?, ?)";

        try (PreparedStatement statement =
                connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            statement.setString(1, booking.getBookingRef());
            statement.setInt(2, booking.getUserId());
            statement.setInt(3, booking.getShowId());
            statement.setBigDecimal(4, booking.getTotalAmount());
            statement.setString(5, booking.getStatus() == null
                    ? BookingStatus.PENDING.name() : booking.getStatus().name());
            setNullableTimestamp(statement, 6, booking.getExpiresAt());

            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }
        throw new SQLException("Creating booking failed: no ID was generated.");
    }

    // -----------------------------------------------------------------------
    // Reads
    // -----------------------------------------------------------------------

    @Override
    public Booking findById(int bookingId) throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            return findSingle(connection, BASE_SELECT + " WHERE booking_id = ?", bookingId);
        }
    }

    @Override
    public Booking findByIdForUpdate(Connection connection, int bookingId) throws SQLException {
        return findSingle(connection, BASE_SELECT + " WHERE booking_id = ? FOR UPDATE", bookingId);
    }

    @Override
    public Booking findByRef(String bookingRef) throws SQLException {
        String sql = BASE_SELECT + " WHERE booking_ref = ?";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, bookingRef);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? mapBooking(resultSet) : null;
            }
        }
    }

    @Override
    public List<Booking> findByUserId(int userId) throws SQLException {
        return findList(BASE_SELECT + " WHERE user_id = ?" + ORDER_NEWEST_FIRST, userId);
    }

    @Override
    public List<Booking> findByShowId(int showId) throws SQLException {
        return findList(BASE_SELECT + " WHERE show_id = ?" + ORDER_NEWEST_FIRST, showId);
    }

    @Override
    public List<Booking> findAll() throws SQLException {
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(BASE_SELECT + ORDER_NEWEST_FIRST)) {
            return readBookings(statement);
        }
    }

    @Override
    public List<Booking> findByStatus(BookingStatus status) throws SQLException {
        String sql = BASE_SELECT + " WHERE status = ?" + ORDER_NEWEST_FIRST;
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name());
            return readBookings(statement);
        }
    }

    @Override
    public List<Booking> findUserBookingsByStatus(int userId, BookingStatus status)
            throws SQLException {
        String sql = BASE_SELECT + " WHERE user_id = ? AND status = ?" + ORDER_NEWEST_FIRST;
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            statement.setString(2, status.name());
            return readBookings(statement);
        }
    }

    @Override
    public List<BookingDetails> findDetailsByUserId(int userId) throws SQLException {
        String sql = DETAILS_SELECT
                + "WHERE b.user_id = ? "
                + "GROUP BY b.booking_id "
                + "ORDER BY b.booked_at DESC, b.booking_id DESC";

        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<BookingDetails> results = new ArrayList<BookingDetails>();
                while (resultSet.next()) {
                    results.add(mapDetails(resultSet));
                }
                return results;
            }
        }
    }

    @Override
    public BookingDetails findDetailsById(int bookingId) throws SQLException {
        String sql = DETAILS_SELECT + "WHERE b.booking_id = ? GROUP BY b.booking_id";

        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, bookingId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? mapDetails(resultSet) : null;
            }
        }
    }

    @Override
    public List<Integer> findExpiredPendingIds(Connection connection, int limit)
            throws SQLException {
        String sql = "SELECT booking_id FROM bookings "
                + "WHERE status = 'PENDING' AND expires_at IS NOT NULL AND expires_at < ? "
                + "ORDER BY expires_at LIMIT ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            statement.setInt(2, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<Integer> ids = new ArrayList<Integer>();
                while (resultSet.next()) {
                    ids.add(Integer.valueOf(resultSet.getInt(1)));
                }
                return ids;
            }
        }
    }

    @Override
    public int countByShowId(int showId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM bookings WHERE show_id = ? AND status = 'CONFIRMED'";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, showId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        }
    }

    @Override
    public boolean existsById(int bookingId) throws SQLException {
        String sql = "SELECT 1 FROM bookings WHERE booking_id = ? LIMIT 1";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, bookingId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    // -----------------------------------------------------------------------
    // Mutations
    // -----------------------------------------------------------------------

    @Override
    public boolean updateStatus(Connection connection, int bookingId, BookingStatus status)
            throws SQLException {
        String sql = "UPDATE bookings SET status = ? WHERE booking_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name());
            statement.setInt(2, bookingId);
            return statement.executeUpdate() > 0;
        }
    }

    @Override
    public boolean confirm(Connection connection, int bookingId) throws SQLException {
        String sql = "UPDATE bookings SET status = 'CONFIRMED', expires_at = NULL "
                + "WHERE booking_id = ? AND status = 'PENDING'";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, bookingId);
            return statement.executeUpdate() > 0;
        }
    }

    @Override
    public boolean cancel(Connection connection, int bookingId) throws SQLException {
        // Only a live booking can be cancelled; cancelling an already-cancelled or
        // expired one must not silently "succeed".
        String sql = "UPDATE bookings SET status = 'CANCELLED', expires_at = NULL "
                + "WHERE booking_id = ? AND status IN ('PENDING', 'CONFIRMED')";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, bookingId);
            return statement.executeUpdate() > 0;
        }
    }

    @Override
    public int markExpired(Connection connection, List<Integer> bookingIds) throws SQLException {
        if (bookingIds.isEmpty()) {
            return 0;
        }

        StringBuilder sql = new StringBuilder(
                "UPDATE bookings SET status = 'EXPIRED', expires_at = NULL "
                + "WHERE status = 'PENDING' AND booking_id IN (");
        for (int i = 0; i < bookingIds.size(); i++) {
            sql.append(i == 0 ? "?" : ", ?");
        }
        sql.append(')');

        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            int index = 1;
            for (Integer id : bookingIds) {
                statement.setInt(index++, id.intValue());
            }
            return statement.executeUpdate();
        }
    }

    @Override
    public boolean updateTotalAmount(Connection connection, int bookingId, BigDecimal totalAmount)
            throws SQLException {
        String sql = "UPDATE bookings SET total_amount = ? WHERE booking_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBigDecimal(1, totalAmount);
            statement.setInt(2, bookingId);
            return statement.executeUpdate() > 0;
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Booking findSingle(Connection connection, String sql, int id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? mapBooking(resultSet) : null;
            }
        }
    }

    private List<Booking> findList(String sql, int value) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, value);
            return readBookings(statement);
        }
    }

    private static List<Booking> readBookings(PreparedStatement statement) throws SQLException {
        List<Booking> bookings = new ArrayList<Booking>();
        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                bookings.add(mapBooking(resultSet));
            }
        }
        return bookings;
    }

    private static void setNullableTimestamp(PreparedStatement statement, int index,
            LocalDateTime value) throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.TIMESTAMP);
        } else {
            statement.setTimestamp(index, Timestamp.valueOf(value));
        }
    }

    private static Booking mapBooking(ResultSet resultSet) throws SQLException {
        Booking booking = new Booking();
        booking.setBookingId(resultSet.getInt("booking_id"));
        booking.setBookingRef(resultSet.getString("booking_ref"));
        booking.setUserId(resultSet.getInt("user_id"));
        booking.setShowId(resultSet.getInt("show_id"));
        booking.setTotalAmount(resultSet.getBigDecimal("total_amount"));
        booking.setStatus(BookingStatus.valueOf(resultSet.getString("status")));
        booking.setBookedAt(toLocalDateTime(resultSet.getTimestamp("booked_at")));
        booking.setExpiresAt(toLocalDateTime(resultSet.getTimestamp("expires_at")));
        return booking;
    }

    private static BookingDetails mapDetails(ResultSet resultSet) throws SQLException {
        Date showDate = resultSet.getDate("show_date");
        Time startTime = resultSet.getTime("start_time");

        return new BookingDetails(
                resultSet.getInt("booking_id"),
                resultSet.getString("booking_ref"),
                BookingStatus.valueOf(resultSet.getString("status")),
                resultSet.getBigDecimal("total_amount"),
                toLocalDateTime(resultSet.getTimestamp("booked_at")),
                toLocalDateTime(resultSet.getTimestamp("expires_at")),
                resultSet.getInt("show_id"),
                resultSet.getString("title"),
                resultSet.getString("language"),
                resultSet.getInt("duration_minutes"),
                resultSet.getString("theatre_name"),
                resultSet.getString("theatre_location"),
                resultSet.getString("screen_name"),
                showDate == null ? null : showDate.toLocalDate(),
                startTime == null ? null : startTime.toLocalTime(),
                splitSeatLabels(resultSet.getString("seat_labels")));
    }

    /** Turns the GROUP_CONCAT output back into a list; empty for a seatless booking. */
    private static List<String> splitSeatLabels(String concatenated) {
        if (concatenated == null || concatenated.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<String>(Arrays.asList(concatenated.split(",")));
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
