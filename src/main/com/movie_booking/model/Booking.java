package com.movie_booking.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A customer's purchase of one or more seats for a single show.
 *
 * <p>The seats themselves hang off this row as {@link BookingSeat} line items,
 * each remembering the price at the time of sale so that a later price change
 * cannot rewrite history on an old ticket.
 *
 * <p>{@link #expiresAt} is set only while the booking is
 * {@link BookingStatus#PENDING}; it mirrors the {@code locked_until} deadline on
 * the held seats so both sides of the hold agree on when it lapses.
 */
public class Booking {

    private int bookingId;
    private String bookingRef;
    private int userId;
    private int showId;
    private BigDecimal totalAmount;
    private BookingStatus status;
    private LocalDateTime bookedAt;
    private LocalDateTime expiresAt;

    public Booking() {
    }

    public Booking(int bookingId, String bookingRef, int userId, int showId,
            BigDecimal totalAmount, BookingStatus status, LocalDateTime bookedAt,
            LocalDateTime expiresAt) {
        this.bookingId = bookingId;
        this.bookingRef = bookingRef;
        this.userId = userId;
        this.showId = showId;
        this.totalAmount = totalAmount;
        this.status = status;
        this.bookedAt = bookedAt;
        this.expiresAt = expiresAt;
    }

    /** @return {@code true} if this is a hold whose deadline has already passed. */
    public boolean isExpired(LocalDateTime now) {
        return status == BookingStatus.PENDING
                && expiresAt != null
                && expiresAt.isBefore(now);
    }

    public int getBookingId() { return bookingId; }
    public void setBookingId(int bookingId) { this.bookingId = bookingId; }
    public String getBookingRef() { return bookingRef; }
    public void setBookingRef(String bookingRef) { this.bookingRef = bookingRef; }
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    public int getShowId() { return showId; }
    public void setShowId(int showId) { this.showId = showId; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public BookingStatus getStatus() { return status; }
    public void setStatus(BookingStatus status) { this.status = status; }
    public LocalDateTime getBookedAt() { return bookedAt; }
    public void setBookedAt(LocalDateTime bookedAt) { this.bookedAt = bookedAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
}
