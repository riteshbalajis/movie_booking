package com.movie_booking.dao;

import com.movie_booking.model.ShowSeat;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public interface ShowSeatDao {
    int createShowSeat(ShowSeat showSeat) throws SQLException;

    int createShowSeat(Connection connection, ShowSeat showSeat) throws SQLException;

    int createShowSeatsForShow(int showId, int screenId, BigDecimal regularPrice,
            BigDecimal premiumPrice, BigDecimal reclinerPrice) throws SQLException;

        int createShowSeatsForShow(Connection connection, int showId, int screenId,
            BigDecimal regularPrice, BigDecimal premiumPrice, BigDecimal reclinerPrice)
            throws SQLException;

    ShowSeat findById(int showSeatId) throws SQLException;

    List<ShowSeat> findByShowId(int showId) throws SQLException;

    List<ShowSeat> findAvailableByShowId(int showId) throws SQLException;

    List<ShowSeat> findBookedByShowId(int showId) throws SQLException;

    ShowSeat findByShowAndSeat(int showId, int seatId) throws SQLException;

    int countAvailableByShowId(int showId) throws SQLException;

    boolean updatePrice(int showSeatId, BigDecimal price) throws SQLException;

    boolean bookSeat(int showSeatId) throws SQLException;

    boolean bookSeat(Connection connection, int showSeatId) throws SQLException;

    boolean releaseSeat(int showSeatId) throws SQLException;

    boolean releaseSeat(Connection connection, int showSeatId) throws SQLException;

    boolean isAvailable(int showSeatId) throws SQLException;

    boolean existsById(int showSeatId) throws SQLException;

    boolean existsByShowAndSeat(int showId, int seatId) throws SQLException;
}
