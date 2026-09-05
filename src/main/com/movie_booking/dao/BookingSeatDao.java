package com.movie_booking.dao;

import com.movie_booking.model.BookingSeat;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public interface BookingSeatDao {
    int createBookingSeat(BookingSeat bookingSeat) throws SQLException;

    int createBookingSeat(Connection connection, BookingSeat bookingSeat) throws SQLException;

    int createBookingSeats(Connection connection, int bookingId,
            List<BookingSeat> bookingSeats) throws SQLException;

    BookingSeat findById(int bookingSeatId) throws SQLException;

    List<BookingSeat> findByBookingId(int bookingId) throws SQLException;

    BookingSeat findByShowSeatId(int showSeatId) throws SQLException;

    boolean existsById(int bookingSeatId) throws SQLException;

    boolean existsByBookingAndShowSeat(int bookingId, int showSeatId) throws SQLException;

    boolean deleteByBookingId(int bookingId) throws SQLException;

    boolean deleteByBookingId(Connection connection, int bookingId) throws SQLException;

    int countByBookingId(int bookingId) throws SQLException;

    BigDecimal calculateTotalByBookingId(int bookingId) throws SQLException;
}
