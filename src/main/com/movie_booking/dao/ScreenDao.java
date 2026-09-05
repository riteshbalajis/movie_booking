package com.movie_booking.dao;

import com.movie_booking.model.Screen;
import java.sql.SQLException;
import java.util.List;

public interface ScreenDao {
    int createScreen(Screen screen) throws SQLException;

    Screen findById(int screenId) throws SQLException;

    Screen findByName(int theatreId, String name) throws SQLException;

    List<Screen> findAll() throws SQLException;

    List<Screen> findScreensByTheatreId(int theatreId) throws SQLException;

    List<Screen> findActiveScreensByTheatreId(int theatreId) throws SQLException;

    boolean updateScreen(Screen screen) throws SQLException;

    boolean activateScreen(int screenId) throws SQLException;

    boolean deactivateScreen(int screenId) throws SQLException;

    boolean existsById(int screenId) throws SQLException;

    boolean existsByName(int theatreId, String name) throws SQLException;

    boolean deleteScreen(int screenId) throws SQLException;
}
