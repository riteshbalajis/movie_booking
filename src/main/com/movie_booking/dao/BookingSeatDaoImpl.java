package com.movie_booking.dao;

import com.movie_booking.model.BookingSeat;
import com.movie_booking.util.DBConnection;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class BookingSeatDaoImpl implements BookingSeatDao {
    private static final String BASE_SELECT = "SELECT booking_seat_id, booking_id, "
            + "show_seat_id, price FROM booking_seats";

    @Override
    public int createBookingSeat(BookingSeat bookingSeat) throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            return createBookingSeat(connection, bookingSeat);
        }
    }

    @Override
    public int createBookingSeat(Connection connection, BookingSeat bookingSeat)
            throws SQLException {
        String sql = "INSERT INTO booking_seats (booking_id, show_seat_id, price) "
                + "VALUES (?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql,
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, bookingSeat.getBookingId());
            statement.setInt(2, bookingSeat.getShowSeatId());
            statement.setBigDecimal(3, bookingSeat.getPrice());
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }

        throw new SQLException("Creating booking seat failed: no ID was generated.");
    }

    @Override
    public int createBookingSeats(Connection connection, int bookingId,
            List<BookingSeat> bookingSeats) throws SQLException {
        String sql = "INSERT INTO booking_seats (booking_id, show_seat_id, price) "
                + "VALUES (?, ?, ?)";
        int insertedCount = 0;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (BookingSeat bookingSeat : bookingSeats) {
                statement.setInt(1, bookingId);
                statement.setInt(2, bookingSeat.getShowSeatId());
                statement.setBigDecimal(3, bookingSeat.getPrice());
                statement.addBatch();
            }

            int[] results = statement.executeBatch();
            for (int result : results) {
                if (result == Statement.SUCCESS_NO_INFO) {
                    insertedCount++;
                } else if (result > 0) {
                    insertedCount += result;
                }
            }
        }

        return insertedCount;
    }

    @Override
    public BookingSeat findById(int bookingSeatId) throws SQLException {
        String sql = BASE_SELECT + " WHERE booking_seat_id = ?";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, bookingSeatId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? mapBookingSeat(resultSet) : null;
            }
        }
    }

    @Override
    public List<BookingSeat> findByBookingId(int bookingId) throws SQLException {
        String sql = BASE_SELECT + " WHERE booking_id = ? ORDER BY booking_seat_id";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, bookingId);
            return readBookingSeats(statement);
        }
    }

    @Override
    public BookingSeat findByShowSeatId(int showSeatId) throws SQLException {
        String sql = BASE_SELECT + " WHERE show_seat_id = ?";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, showSeatId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? mapBookingSeat(resultSet) : null;
            }
        }
    }

    @Override
    public boolean existsById(int bookingSeatId) throws SQLException {
        String sql = "SELECT 1 FROM booking_seats WHERE booking_seat_id = ? LIMIT 1";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, bookingSeatId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    @Override
    public boolean existsByBookingAndShowSeat(int bookingId, int showSeatId)
            throws SQLException {
        String sql = "SELECT 1 FROM booking_seats WHERE booking_id = ? AND show_seat_id = ? LIMIT 1";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, bookingId);
            statement.setInt(2, showSeatId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    @Override
    public boolean deleteByBookingId(int bookingId) throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            return deleteByBookingId(connection, bookingId);
        }
    }

    @Override
    public boolean deleteByBookingId(Connection connection, int bookingId) throws SQLException {
        String sql = "DELETE FROM booking_seats WHERE booking_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, bookingId);
            return statement.executeUpdate() > 0;
        }
    }

    @Override
    public int countByBookingId(int bookingId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM booking_seats WHERE booking_id = ?";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, bookingId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    @Override
    public BigDecimal calculateTotalByBookingId(int bookingId) throws SQLException {
        String sql = "SELECT COALESCE(SUM(price), 0) FROM booking_seats WHERE booking_id = ?";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, bookingId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getBigDecimal(1);
            }
        }
    }

    private List<BookingSeat> readBookingSeats(PreparedStatement statement) throws SQLException {
        List<BookingSeat> bookingSeats = new ArrayList<>();
        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                bookingSeats.add(mapBookingSeat(resultSet));
            }
        }
        return bookingSeats;
    }

    private BookingSeat mapBookingSeat(ResultSet resultSet) throws SQLException {
        BookingSeat bookingSeat = new BookingSeat();
        bookingSeat.setBookingSeatId(resultSet.getInt("booking_seat_id"));
        bookingSeat.setBookingId(resultSet.getInt("booking_id"));
        bookingSeat.setShowSeatId(resultSet.getInt("show_seat_id"));
        bookingSeat.setPrice(resultSet.getBigDecimal("price"));
        return bookingSeat;
    }
}
