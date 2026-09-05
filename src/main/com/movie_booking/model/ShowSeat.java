package com.movie_booking.model;

import java.math.BigDecimal;

public class ShowSeat {
    private int showSeatId;
    private int showId;
    private int seatId;
    private ShowSeatStatus status;
    private BigDecimal price;

    public ShowSeat() {
    }

    public ShowSeat(int showSeatId, int showId, int seatId, ShowSeatStatus status,
            BigDecimal price) {
        this.showSeatId = showSeatId;
        this.showId = showId;
        this.seatId = seatId;
        this.status = status;
        this.price = price;
    }

    public int getShowSeatId() { return showSeatId; }
    public void setShowSeatId(int showSeatId) { this.showSeatId = showSeatId; }
    public int getShowId() { return showId; }
    public void setShowId(int showId) { this.showId = showId; }
    public int getSeatId() { return seatId; }
    public void setSeatId(int seatId) { this.seatId = seatId; }
    public ShowSeatStatus getStatus() { return status; }
    public void setStatus(ShowSeatStatus status) { this.status = status; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
}
