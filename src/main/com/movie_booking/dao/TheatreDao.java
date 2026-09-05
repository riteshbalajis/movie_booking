package com.movie_booking.dao;

import com.movie_booking.model.Theatre;
import java.sql.SQLException;
import java.util.List;

public interface TheatreDao {
    int createTheatre(Theatre theatre) throws SQLException;

    Theatre findById(int theatreId) throws SQLException;

    Theatre findByName(String name) throws SQLException;

    List<Theatre> findAll() throws SQLException;

    List<Theatre> findAllActive() throws SQLException;

    List<Theatre> searchByName(String keyword) throws SQLException;

    List<Theatre> findByLocation(String location) throws SQLException;

    boolean updateTheatre(Theatre theatre) throws SQLException;

    boolean activateTheatre(int theatreId) throws SQLException;

    boolean deactivateTheatre(int theatreId) throws SQLException;

    boolean existsById(int theatreId) throws SQLException;
}
