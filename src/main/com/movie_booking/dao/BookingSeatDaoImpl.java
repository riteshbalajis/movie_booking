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
import java.util.Collections;
import java.util.List;

/** JDBC implementation of {@link BookingSeatDao}. */
public class BookingSeatDaoImpl implements BookingSeatDao {

    private static final String BASE_SELECT =
            "SELECT booking_seat_id, booking_id, show_seat_id, price FROM booking_seats";

    @Override
    public int createBookingSeats(Connection connection, int bookingId,
            List<BookingSeat> bookingSeats) throws SQLException {

        if (bookingSeats.isEmpty()) {
            return 0;
        }

        String sql = "INSERT INTO booking_seats (booking_id, show_seat_id, price) "
                + "VALUES (?, ?, ?)";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (BookingSeat bookingSeat : bookingSeats) {
                statement.setInt(1, bookingId);
                statement.setInt(2, bookingSeat.getShowSeatId());
                statement.setBigDecimal(3, bookingSeat.getPrice());
                statement.addBatch();
            }
            return countUpdated(statement.executeBatch());
        }
    }

    @Override
    public List<BookingSeat> findByBookingId(int bookingId) throws SQLException {
        String sql = BASE_SELECT + " WHERE booking_id = ? ORDER BY show_seat_id";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, bookingId);

            try (ResultSet resultSet = statement.executeQuery()) {
                List<BookingSeat> seats = new ArrayList<BookingSeat>();
                while (resultSet.next()) {
                    seats.add(mapBookingSeat(resultSet));
                }
                return seats;
            }
        }
    }

    @Override
    public List<Integer> findShowSeatIds(Connection connection, int bookingId)
            throws SQLException {
        String sql = "SELECT show_seat_id FROM booking_seats "
                + "WHERE booking_id = ? ORDER BY show_seat_id";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, bookingId);
            return readIds(statement);
        }
    }

    @Override
    public List<Integer> findShowSeatIdsForBookings(Connection connection, List<Integer> bookingIds)
            throws SQLException {
        if (bookingIds.isEmpty()) {
            return Collections.emptyList();
        }

        StringBuilder sql = new StringBuilder(
                "SELECT show_seat_id FROM booking_seats WHERE booking_id IN (");
        for (int i = 0; i < bookingIds.size(); i++) {
            sql.append(i == 0 ? "?" : ", ?");
        }
        sql.append(") ORDER BY show_seat_id");

        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            int index = 1;
            for (Integer id : bookingIds) {
                statement.setInt(index++, id.intValue());
            }
            return readIds(statement);
        }
    }

    @Override
    public int countByBookingId(int bookingId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM booking_seats WHERE booking_id = ?";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, bookingId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
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
                return resultSet.next() ? resultSet.getBigDecimal(1) : BigDecimal.ZERO;
            }
        }
    }

    @Override
    public int deleteByBookingId(Connection connection, int bookingId) throws SQLException {
        String sql = "DELETE FROM booking_seats WHERE booking_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, bookingId);
            return statement.executeUpdate();
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static List<Integer> readIds(PreparedStatement statement) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery()) {
            List<Integer> ids = new ArrayList<Integer>();
            while (resultSet.next()) {
                ids.add(Integer.valueOf(resultSet.getInt(1)));
            }
            return ids;
        }
    }

    /**
     * Sums a batch result. A driver is allowed to report
     * {@link Statement#SUCCESS_NO_INFO} instead of a row count, so a plain
     * {@code results.length} would be wrong and summing blindly would be too.
     */
    private static int countUpdated(int[] results) {
        int total = 0;
        for (int result : results) {
            if (result == Statement.SUCCESS_NO_INFO) {
                total++;
            } else if (result > 0) {
                total += result;
            }
        }
        return total;
    }

    private static BookingSeat mapBookingSeat(ResultSet resultSet) throws SQLException {
        BookingSeat bookingSeat = new BookingSeat();
        bookingSeat.setBookingSeatId(resultSet.getInt("booking_seat_id"));
        bookingSeat.setBookingId(resultSet.getInt("booking_id"));
        bookingSeat.setShowSeatId(resultSet.getInt("show_seat_id"));
        bookingSeat.setPrice(resultSet.getBigDecimal("price"));
        return bookingSeat;
    }
}
