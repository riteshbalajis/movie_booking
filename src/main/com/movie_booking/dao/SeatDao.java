package com.movie_booking.dao;

import com.movie_booking.model.Seat;
import java.sql.SQLException;
import java.util.List;

public interface SeatDao {
    int createSeat(Seat seat) throws SQLException;

    Seat findById(int seatId) throws SQLException;

    List<Seat> findByScreenId(int screenId) throws SQLException;

    List<Seat> findActiveSeatsByScreenId(int screenId) throws SQLException;

    List<Seat> findByRow(int screenId, String rowLabel) throws SQLException;

    Seat findByPosition(int screenId, String rowLabel, int seatNumber) throws SQLException;

    List<Seat> findAll() throws SQLException;

    boolean updateSeat(Seat seat) throws SQLException;

    boolean activateSeat(int seatId) throws SQLException;

    boolean deactivateSeat(int seatId) throws SQLException;

    boolean existsById(int seatId) throws SQLException;

    boolean existsByPosition(int screenId, String rowLabel, int seatNumber) throws SQLException;

}
