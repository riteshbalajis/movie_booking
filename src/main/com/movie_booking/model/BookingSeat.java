package com.movie_booking.model;

import java.math.BigDecimal;

public class BookingSeat {
    private int bookingSeatId;
    private int bookingId;
    private int showSeatId;
    private BigDecimal price;

    public BookingSeat() {
    }

    public BookingSeat(int bookingSeatId, int bookingId, int showSeatId, BigDecimal price) {
        this.bookingSeatId = bookingSeatId;
        this.bookingId = bookingId;
        this.showSeatId = showSeatId;
        this.price = price;
    }

    public int getBookingSeatId() { return bookingSeatId; }
    public void setBookingSeatId(int bookingSeatId) { this.bookingSeatId = bookingSeatId; }
    public int getBookingId() { return bookingId; }
    public void setBookingId(int bookingId) { this.bookingId = bookingId; }
    public int getShowSeatId() { return showSeatId; }
    public void setShowSeatId(int showSeatId) { this.showSeatId = showSeatId; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
}
