package com.movie_booking.dao;

import com.movie_booking.model.Theatre;
import com.movie_booking.model.TheatreStatus;
import com.movie_booking.util.DBConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class TheatreDaoImpl implements TheatreDao {
    private static final String BASE_SELECT = "SELECT theatre_id, name, location, status, "
            + "created_at FROM theatres";

    @Override
    public int createTheatre(Theatre theatre) throws SQLException {
        String sql = "INSERT INTO theatres (name, location, status) VALUES (?, ?, ?)";

        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql,
                        Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, theatre.getName());
            statement.setString(2, theatre.getLocation());
            statement.setString(3, theatre.getStatus() == null
                    ? TheatreStatus.ACTIVE.name() : theatre.getStatus().name());
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }

        throw new SQLException("Creating theatre failed: no ID was generated.");
    }

    @Override
    public Theatre findById(int theatreId) throws SQLException {
        String sql = BASE_SELECT + " WHERE theatre_id = ?";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, theatreId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? mapTheatre(resultSet) : null;
            }
        }
    }

    @Override
    public Theatre findByName(String name) throws SQLException {
        String sql = BASE_SELECT + " WHERE name = ?";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? mapTheatre(resultSet) : null;
            }
        }
    }

    @Override
    public List<Theatre> findAll() throws SQLException {
        return findTheatres(BASE_SELECT + " ORDER BY theatre_id");
    }

    @Override
    public List<Theatre> findAllActive() throws SQLException {
        return findTheatresByValue(BASE_SELECT + " WHERE status = ? ORDER BY name",
                TheatreStatus.ACTIVE.name());
    }

    @Override
    public List<Theatre> searchByName(String keyword) throws SQLException {
        return findTheatresByValue(BASE_SELECT + " WHERE name LIKE ? ORDER BY name",
                "%" + keyword + "%");
    }

    @Override
    public List<Theatre> findByLocation(String location) throws SQLException {
        return findTheatresByValue(BASE_SELECT + " WHERE location = ? ORDER BY name", location);
    }

    @Override
    public boolean updateTheatre(Theatre theatre) throws SQLException {
        String sql = "UPDATE theatres SET name = ?, location = ? WHERE theatre_id = ?";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, theatre.getName());
            statement.setString(2, theatre.getLocation());
            statement.setInt(3, theatre.getTheatreId());
            return statement.executeUpdate() > 0;
        }
    }

    @Override
    public boolean activateTheatre(int theatreId) throws SQLException {
        return updateStatus(theatreId, TheatreStatus.ACTIVE);
    }

    @Override
    public boolean deactivateTheatre(int theatreId) throws SQLException {
        return updateStatus(theatreId, TheatreStatus.INACTIVE);
    }

    @Override
    public boolean existsById(int theatreId) throws SQLException {
        String sql = "SELECT 1 FROM theatres WHERE theatre_id = ? LIMIT 1";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, theatreId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private boolean updateStatus(int theatreId, TheatreStatus status) throws SQLException {
        String sql = "UPDATE theatres SET status = ? WHERE theatre_id = ?";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name());
            statement.setInt(2, theatreId);
            return statement.executeUpdate() > 0;
        }
    }

    private List<Theatre> findTheatres(String sql) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            return readTheatres(statement);
        }
    }

    private List<Theatre> findTheatresByValue(String sql, String value) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            return readTheatres(statement);
        }
    }

    private List<Theatre> readTheatres(PreparedStatement statement) throws SQLException {
        List<Theatre> theatres = new ArrayList<>();
        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                theatres.add(mapTheatre(resultSet));
            }
        }
        return theatres;
    }

    private Theatre mapTheatre(ResultSet resultSet) throws SQLException {
        Timestamp createdAt = resultSet.getTimestamp("created_at");
        Theatre theatre = new Theatre();
        theatre.setTheatreId(resultSet.getInt("theatre_id"));
        theatre.setName(resultSet.getString("name"));
        theatre.setLocation(resultSet.getString("location"));
        theatre.setStatus(TheatreStatus.valueOf(resultSet.getString("status")));
        theatre.setCreatedAt(createdAt == null ? null : createdAt.toLocalDateTime());
        return theatre;
    }
}
