package com.movie_booking.dao;

import com.movie_booking.model.Seat;
import com.movie_booking.model.SeatStatus;
import com.movie_booking.model.SeatType;
import com.movie_booking.util.DBConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class SeatDaoImpl implements SeatDao {
    private static final String BASE_SELECT = "SELECT seat_id, screen_id, row_label, seat_number, "
            + "seat_type, status FROM seats";

    @Override
    public int createSeat(Seat seat) throws SQLException {
        String sql = "INSERT INTO seats (screen_id, row_label, seat_number, seat_type, status) "
                + "VALUES (?, ?, ?, ?, ?)";

        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql,
                        Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, seat.getScreenId());
            statement.setString(2, seat.getRowLabel());
            statement.setInt(3, seat.getSeatNumber());
            statement.setString(4, seat.getSeatType() == null
                    ? SeatType.REGULAR.name() : seat.getSeatType().name());
            statement.setString(5, seat.getStatus() == null
                    ? SeatStatus.ACTIVE.name() : seat.getStatus().name());
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }

        throw new SQLException("Creating seat failed: no ID was generated.");
    }

    @Override
    public Seat findById(int seatId) throws SQLException {
        String sql = BASE_SELECT + " WHERE seat_id = ?";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, seatId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? mapSeat(resultSet) : null;
            }
        }
    }

    @Override
    public List<Seat> findByScreenId(int screenId) throws SQLException {
        return findSeatsByScreen(BASE_SELECT + " WHERE screen_id = ? ORDER BY row_label, seat_number",
                screenId);
    }

    @Override
    public List<Seat> findActiveSeatsByScreenId(int screenId) throws SQLException {
        String sql = BASE_SELECT + " WHERE screen_id = ? AND status = ? "
                + "ORDER BY row_label, seat_number";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, screenId);
            statement.setString(2, SeatStatus.ACTIVE.name());
            return readSeats(statement);
        }
    }

    @Override
    public List<Seat> findByRow(int screenId, String rowLabel) throws SQLException {
        String sql = BASE_SELECT + " WHERE screen_id = ? AND row_label = ? "
                + "ORDER BY seat_number";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, screenId);
            statement.setString(2, rowLabel);
            return readSeats(statement);
        }
    }

    @Override
    public Seat findByPosition(int screenId, String rowLabel, int seatNumber) throws SQLException {
        String sql = BASE_SELECT + " WHERE screen_id = ? AND row_label = ? AND seat_number = ?";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, screenId);
            statement.setString(2, rowLabel);
            statement.setInt(3, seatNumber);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? mapSeat(resultSet) : null;
            }
        }
    }

    @Override
    public List<Seat> findAll() throws SQLException {
        return findSeats(BASE_SELECT + " ORDER BY screen_id, row_label, seat_number");
    }

    @Override
    public boolean updateSeat(Seat seat) throws SQLException {
        String sql = "UPDATE seats SET row_label = ?, seat_number = ?, seat_type = ? "
                + "WHERE seat_id = ?";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, seat.getRowLabel());
            statement.setInt(2, seat.getSeatNumber());
            statement.setString(3, seat.getSeatType().name());
            statement.setInt(4, seat.getSeatId());
            return statement.executeUpdate() > 0;
        }
    }

    @Override
    public boolean activateSeat(int seatId) throws SQLException {
        return updateStatus(seatId, SeatStatus.ACTIVE);
    }

    @Override
    public boolean deactivateSeat(int seatId) throws SQLException {
        return updateStatus(seatId, SeatStatus.INACTIVE);
    }

    @Override
    public boolean existsById(int seatId) throws SQLException {
        String sql = "SELECT 1 FROM seats WHERE seat_id = ? LIMIT 1";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, seatId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    @Override
    public boolean existsByPosition(int screenId, String rowLabel, int seatNumber)
            throws SQLException {
        String sql = "SELECT 1 FROM seats WHERE screen_id = ? AND row_label = ? "
                + "AND seat_number = ? LIMIT 1";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, screenId);
            statement.setString(2, rowLabel);
            statement.setInt(3, seatNumber);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private boolean updateStatus(int seatId, SeatStatus status) throws SQLException {
        String sql = "UPDATE seats SET status = ? WHERE seat_id = ?";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name());
            statement.setInt(2, seatId);
            return statement.executeUpdate() > 0;
        }
    }

    private List<Seat> findSeats(String sql) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            return readSeats(statement);
        }
    }

    private List<Seat> findSeatsByScreen(String sql, int screenId) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, screenId);
            return readSeats(statement);
        }
    }

    private List<Seat> readSeats(PreparedStatement statement) throws SQLException {
        List<Seat> seats = new ArrayList<>();
        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                seats.add(mapSeat(resultSet));
            }
        }
        return seats;
    }

    private Seat mapSeat(ResultSet resultSet) throws SQLException {
        Seat seat = new Seat();
        seat.setSeatId(resultSet.getInt("seat_id"));
        seat.setScreenId(resultSet.getInt("screen_id"));
        seat.setRowLabel(resultSet.getString("row_label"));
        seat.setSeatNumber(resultSet.getInt("seat_number"));
        seat.setSeatType(SeatType.valueOf(resultSet.getString("seat_type")));
        seat.setStatus(SeatStatus.valueOf(resultSet.getString("status")));
        return seat;
    }
}
