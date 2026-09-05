package com.movie_booking.dao;

import com.movie_booking.model.ShowSeat;
import com.movie_booking.model.ShowSeatStatus;
import com.movie_booking.util.DBConnection;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class ShowSeatDaoImpl implements ShowSeatDao {
    private static final String BASE_SELECT = "SELECT show_seat_id, show_id, seat_id, "
            + "status, price FROM show_seats";

    @Override
    public int createShowSeat(ShowSeat showSeat) throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            return createShowSeat(connection, showSeat);
        }
        }

        @Override
        public int createShowSeat(Connection connection, ShowSeat showSeat) throws SQLException {
        String sql = "INSERT INTO show_seats (show_id, seat_id, status, price) "
            + "VALUES (?, ?, ?, ?)";

        try (PreparedStatement statement = connection.prepareStatement(sql,
            Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, showSeat.getShowId());
            statement.setInt(2, showSeat.getSeatId());
            statement.setString(3, showSeat.getStatus() == null
                    ? ShowSeatStatus.AVAILABLE.name() : showSeat.getStatus().name());
            statement.setBigDecimal(4, showSeat.getPrice());
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }

        throw new SQLException("Creating show seat failed: no ID was generated.");
    }

    @Override
    public int createShowSeatsForShow(int showId, int screenId, BigDecimal regularPrice,
            BigDecimal premiumPrice, BigDecimal reclinerPrice) throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            return createShowSeatsForShow(connection, showId, screenId, regularPrice,
                premiumPrice, reclinerPrice);
        }
        }

        @Override
        public int createShowSeatsForShow(Connection connection, int showId, int screenId,
            BigDecimal regularPrice, BigDecimal premiumPrice, BigDecimal reclinerPrice)
            throws SQLException {
        String sql = "INSERT INTO show_seats (show_id, seat_id, status, price) "
                + "SELECT ?, seat_id, ?, CASE seat_type "
                + "WHEN 'REGULAR' THEN ? WHEN 'PREMIUM' THEN ? WHEN 'RECLINER' THEN ? END "
                + "FROM seats WHERE screen_id = ? AND status = 'ACTIVE'";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, showId);
            statement.setString(2, ShowSeatStatus.AVAILABLE.name());
            statement.setBigDecimal(3, regularPrice);
            statement.setBigDecimal(4, premiumPrice);
            statement.setBigDecimal(5, reclinerPrice);
            statement.setInt(6, screenId);
            return statement.executeUpdate();
        }
    }

    @Override
    public ShowSeat findById(int showSeatId) throws SQLException {
        String sql = BASE_SELECT + " WHERE show_seat_id = ?";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, showSeatId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? mapShowSeat(resultSet) : null;
            }
        }
    }

    @Override
    public List<ShowSeat> findByShowId(int showId) throws SQLException {
        return findShowSeatsByInt(BASE_SELECT + " WHERE show_id = ? ORDER BY seat_id", showId);
    }

    @Override
    public List<ShowSeat> findAvailableByShowId(int showId) throws SQLException {
        return findShowSeatsByStatus(showId, ShowSeatStatus.AVAILABLE);
    }

    @Override
    public List<ShowSeat> findBookedByShowId(int showId) throws SQLException {
        return findShowSeatsByStatus(showId, ShowSeatStatus.BOOKED);
    }

    @Override
    public ShowSeat findByShowAndSeat(int showId, int seatId) throws SQLException {
        String sql = BASE_SELECT + " WHERE show_id = ? AND seat_id = ?";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, showId);
            statement.setInt(2, seatId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? mapShowSeat(resultSet) : null;
            }
        }
    }

    @Override
    public int countAvailableByShowId(int showId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM show_seats WHERE show_id = ? AND status = ?";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, showId);
            statement.setString(2, ShowSeatStatus.AVAILABLE.name());
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    @Override
    public boolean updatePrice(int showSeatId, BigDecimal price) throws SQLException {
        String sql = "UPDATE show_seats SET price = ? WHERE show_seat_id = ?";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBigDecimal(1, price);
            statement.setInt(2, showSeatId);
            return statement.executeUpdate() > 0;
        }
    }

    @Override
    public boolean bookSeat(int showSeatId) throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            return bookSeat(connection, showSeatId);
        }
    }

    @Override
    public boolean bookSeat(Connection connection, int showSeatId) throws SQLException {
        return updateStatusIfCurrent(connection, showSeatId, ShowSeatStatus.BOOKED,
                ShowSeatStatus.AVAILABLE);
    }

    @Override
    public boolean releaseSeat(int showSeatId) throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            return releaseSeat(connection, showSeatId);
        }
    }

    @Override
    public boolean releaseSeat(Connection connection, int showSeatId) throws SQLException {
        return updateStatusIfCurrent(connection, showSeatId, ShowSeatStatus.AVAILABLE,
                ShowSeatStatus.BOOKED);
    }

    @Override
    public boolean isAvailable(int showSeatId) throws SQLException {
        String sql = "SELECT 1 FROM show_seats WHERE show_seat_id = ? AND status = ? LIMIT 1";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, showSeatId);
            statement.setString(2, ShowSeatStatus.AVAILABLE.name());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    @Override
    public boolean existsById(int showSeatId) throws SQLException {
        String sql = "SELECT 1 FROM show_seats WHERE show_seat_id = ? LIMIT 1";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, showSeatId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    @Override
    public boolean existsByShowAndSeat(int showId, int seatId) throws SQLException {
        String sql = "SELECT 1 FROM show_seats WHERE show_id = ? AND seat_id = ? LIMIT 1";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, showId);
            statement.setInt(2, seatId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private boolean updateStatusIfCurrent(Connection connection, int showSeatId,
            ShowSeatStatus newStatus,
            ShowSeatStatus currentStatus) throws SQLException {
        String sql = "UPDATE show_seats SET status = ? WHERE show_seat_id = ? AND status = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, newStatus.name());
            statement.setInt(2, showSeatId);
            statement.setString(3, currentStatus.name());
            return statement.executeUpdate() > 0;
        }
    }

    private List<ShowSeat> findShowSeatsByInt(String sql, int showId) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, showId);
            return readShowSeats(statement);
        }
    }

    private List<ShowSeat> findShowSeatsByStatus(int showId, ShowSeatStatus status)
            throws SQLException {
        String sql = BASE_SELECT + " WHERE show_id = ? AND status = ? ORDER BY seat_id";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, showId);
            statement.setString(2, status.name());
            return readShowSeats(statement);
        }
    }

    private List<ShowSeat> readShowSeats(PreparedStatement statement) throws SQLException {
        List<ShowSeat> showSeats = new ArrayList<>();
        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                showSeats.add(mapShowSeat(resultSet));
            }
        }
        return showSeats;
    }

    private ShowSeat mapShowSeat(ResultSet resultSet) throws SQLException {
        ShowSeat showSeat = new ShowSeat();
        showSeat.setShowSeatId(resultSet.getInt("show_seat_id"));
        showSeat.setShowId(resultSet.getInt("show_id"));
        showSeat.setSeatId(resultSet.getInt("seat_id"));
        showSeat.setStatus(ShowSeatStatus.valueOf(resultSet.getString("status")));
        showSeat.setPrice(resultSet.getBigDecimal("price"));
        return showSeat;
    }
}
