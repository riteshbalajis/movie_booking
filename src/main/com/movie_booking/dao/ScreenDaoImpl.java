package com.movie_booking.dao;

import com.movie_booking.model.Screen;
import com.movie_booking.model.ScreenStatus;
import com.movie_booking.util.DBConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class ScreenDaoImpl implements ScreenDao {
    private static final String BASE_SELECT = "SELECT screen_id, theatre_id, name, capacity, "
            + "status FROM screens";

    @Override
    public int createScreen(Screen screen) throws SQLException {
        String sql = "INSERT INTO screens (theatre_id, name, capacity, status) "
                + "VALUES (?, ?, ?, ?)";

        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql,
                        Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, screen.getTheatreId());
            statement.setString(2, screen.getName());
            statement.setInt(3, screen.getCapacity());
            statement.setString(4, screen.getStatus() == null
                    ? ScreenStatus.ACTIVE.name() : screen.getStatus().name());
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }

        throw new SQLException("Creating screen failed: no ID was generated.");
    }

    @Override
    public Screen findById(int screenId) throws SQLException {
        String sql = BASE_SELECT + " WHERE screen_id = ?";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, screenId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? mapScreen(resultSet) : null;
            }
        }
    }

    @Override
    public Screen findByName(int theatreId, String name) throws SQLException {
        String sql = BASE_SELECT + " WHERE theatre_id = ? AND name = ?";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, theatreId);
            statement.setString(2, name);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? mapScreen(resultSet) : null;
            }
        }
    }

    @Override
    public List<Screen> findAll() throws SQLException {
        return findScreens(BASE_SELECT + " ORDER BY theatre_id, screen_id");
    }

    @Override
    public List<Screen> findScreensByTheatreId(int theatreId) throws SQLException {
        return findScreensByTheatre(BASE_SELECT + " WHERE theatre_id = ? ORDER BY name", theatreId);
    }

    @Override
    public List<Screen> findActiveScreensByTheatreId(int theatreId) throws SQLException {
        String sql = BASE_SELECT + " WHERE theatre_id = ? AND status = ? ORDER BY name";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, theatreId);
            statement.setString(2, ScreenStatus.ACTIVE.name());
            return readScreens(statement);
        }
    }

    @Override
    public boolean updateScreen(Screen screen) throws SQLException {
        String sql = "UPDATE screens SET name = ?, capacity = ? WHERE screen_id = ?";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, screen.getName());
            statement.setInt(2, screen.getCapacity());
            statement.setInt(3, screen.getScreenId());
            return statement.executeUpdate() > 0;
        }
    }

    @Override
    public boolean activateScreen(int screenId) throws SQLException {
        return updateStatus(screenId, ScreenStatus.ACTIVE);
    }

    @Override
    public boolean deactivateScreen(int screenId) throws SQLException {
        return updateStatus(screenId, ScreenStatus.INACTIVE);
    }

    @Override
    public boolean existsById(int screenId) throws SQLException {
        String sql = "SELECT 1 FROM screens WHERE screen_id = ? LIMIT 1";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, screenId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    @Override
    public boolean existsByName(int theatreId, String name) throws SQLException {
        String sql = "SELECT 1 FROM screens WHERE theatre_id = ? AND name = ? LIMIT 1";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, theatreId);
            statement.setString(2, name);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    @Override
    public boolean deleteScreen(int screenId) throws SQLException {
        String sql = "DELETE FROM screens WHERE screen_id = ?";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, screenId);
            return statement.executeUpdate() > 0;
        }
    }

    private boolean updateStatus(int screenId, ScreenStatus status) throws SQLException {
        String sql = "UPDATE screens SET status = ? WHERE screen_id = ?";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name());
            statement.setInt(2, screenId);
            return statement.executeUpdate() > 0;
        }
    }

    private List<Screen> findScreens(String sql) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            return readScreens(statement);
        }
    }

    private List<Screen> findScreensByTheatre(String sql, int theatreId) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, theatreId);
            return readScreens(statement);
        }
    }

    private List<Screen> readScreens(PreparedStatement statement) throws SQLException {
        List<Screen> screens = new ArrayList<>();
        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                screens.add(mapScreen(resultSet));
            }
        }
        return screens;
    }

    private Screen mapScreen(ResultSet resultSet) throws SQLException {
        Screen screen = new Screen();
        screen.setScreenId(resultSet.getInt("screen_id"));
        screen.setTheatreId(resultSet.getInt("theatre_id"));
        screen.setName(resultSet.getString("name"));
        screen.setCapacity(resultSet.getInt("capacity"));
        screen.setStatus(ScreenStatus.valueOf(resultSet.getString("status")));
        return screen;
    }
}
