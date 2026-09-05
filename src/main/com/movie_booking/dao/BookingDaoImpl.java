package com.movie_booking.dao;

import com.movie_booking.model.Booking;
import com.movie_booking.model.BookingStatus;
import com.movie_booking.util.DBConnection;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class BookingDaoImpl implements BookingDao {
    private static final String BASE_SELECT = "SELECT booking_id, user_id, show_id, "
            + "total_amount, status, booked_at FROM bookings";
    private static final String ORDER_BY_BOOKED_AT = " ORDER BY booked_at DESC";

    @Override
    public int createBooking(Booking booking) throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            return createBooking(connection, booking);
        }
        }

        @Override
        public int createBooking(Connection connection, Booking booking) throws SQLException {
        String sql = "INSERT INTO bookings (user_id, show_id, total_amount, status) "
                + "VALUES (?, ?, ?, ?)";

        try (PreparedStatement statement = connection.prepareStatement(sql,
            Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, booking.getUserId());
            statement.setInt(2, booking.getShowId());
            statement.setBigDecimal(3, booking.getTotalAmount());
            statement.setString(4, booking.getStatus() == null
                    ? BookingStatus.PENDING.name() : booking.getStatus().name());
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }

        throw new SQLException("Creating booking failed: no ID was generated.");
    }

    @Override
    public Booking findById(int bookingId) throws SQLException {
        String sql = BASE_SELECT + " WHERE booking_id = ?";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, bookingId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? mapBooking(resultSet) : null;
            }
        }
    }

    @Override
    public List<Booking> findByUserId(int userId) throws SQLException {
        return findBookingsByInt(BASE_SELECT + " WHERE user_id = ?" + ORDER_BY_BOOKED_AT, userId);
    }

    @Override
    public List<Booking> findByShowId(int showId) throws SQLException {
        return findBookingsByInt(BASE_SELECT + " WHERE show_id = ?" + ORDER_BY_BOOKED_AT, showId);
    }

    @Override
    public List<Booking> findByUserAndShow(int userId, int showId) throws SQLException {
        String sql = BASE_SELECT + " WHERE user_id = ? AND show_id = ?"
                + ORDER_BY_BOOKED_AT;
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            statement.setInt(2, showId);
            return readBookings(statement);
        }
    }

    @Override
    public List<Booking> findAll() throws SQLException {
        return findBookings(BASE_SELECT + ORDER_BY_BOOKED_AT);
    }

    @Override
    public List<Booking> findByStatus(BookingStatus status) throws SQLException {
        return findBookingsByStatus(BASE_SELECT + " WHERE status = ?" + ORDER_BY_BOOKED_AT, status);
    }

    @Override
    public List<Booking> findUserBookingsByStatus(int userId, BookingStatus status)
            throws SQLException {
        String sql = BASE_SELECT + " WHERE user_id = ? AND status = ?" + ORDER_BY_BOOKED_AT;
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            statement.setString(2, status.name());
            return readBookings(statement);
        }
    }

    @Override
    public boolean updateStatus(int bookingId, BookingStatus status) throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            return updateStatus(connection, bookingId, status);
        }
    }

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
    public boolean confirmBooking(int bookingId) throws SQLException {
        return updateStatus(bookingId, BookingStatus.CONFIRMED);
    }

    @Override
    public boolean cancelBooking(int bookingId) throws SQLException {
        return updateStatus(bookingId, BookingStatus.CANCELLED);
    }

    @Override
    public boolean completeBooking(int bookingId) throws SQLException {
        return updateStatus(bookingId, BookingStatus.COMPLETED);
    }

    @Override
    public boolean updateTotalAmount(int bookingId, BigDecimal totalAmount) throws SQLException {
        String sql = "UPDATE bookings SET total_amount = ? WHERE booking_id = ?";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBigDecimal(1, totalAmount);
            statement.setInt(2, bookingId);
            return statement.executeUpdate() > 0;
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

    @Override
    public int countByShowId(int showId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM bookings WHERE show_id = ?";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, showId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    private List<Booking> findBookings(String sql) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            return readBookings(statement);
        }
    }

    private List<Booking> findBookingsByInt(String sql, int value) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, value);
            return readBookings(statement);
        }
    }

    private List<Booking> findBookingsByStatus(String sql, BookingStatus status) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name());
            return readBookings(statement);
        }
    }

    private List<Booking> readBookings(PreparedStatement statement) throws SQLException {
        List<Booking> bookings = new ArrayList<>();
        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                bookings.add(mapBooking(resultSet));
            }
        }
        return bookings;
    }

    private Booking mapBooking(ResultSet resultSet) throws SQLException {
        Timestamp bookedAt = resultSet.getTimestamp("booked_at");
        Booking booking = new Booking();
        booking.setBookingId(resultSet.getInt("booking_id"));
        booking.setUserId(resultSet.getInt("user_id"));
        booking.setShowId(resultSet.getInt("show_id"));
        booking.setTotalAmount(resultSet.getBigDecimal("total_amount"));
        booking.setStatus(BookingStatus.valueOf(resultSet.getString("status")));
        booking.setBookedAt(bookedAt == null ? null : bookedAt.toLocalDateTime());
        return booking;
    }
}
