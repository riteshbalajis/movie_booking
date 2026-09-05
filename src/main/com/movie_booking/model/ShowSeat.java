package com.movie_booking.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One physical {@link Seat} as it is sold for one {@link Show}.
 *
 * <p>This is the row that concurrent bookings compete for, so it is the row the
 * booking transaction locks with {@code SELECT ... FOR UPDATE}. Price lives here
 * rather than on {@code Seat} because the same chair costs different amounts for
 * a matinee and a Saturday premiere.
 *
 * <p>When {@link #status} is {@link ShowSeatStatus#LOCKED}, {@link #lockedByUserId}
 * and {@link #lockedUntil} say who holds it and until when. Both are null in
 * every other state.
 */
public class ShowSeat {

    private int showSeatId;
    private int showId;
    private int seatId;
    private ShowSeatStatus status;
    private BigDecimal price;
    private Integer lockedByUserId;
    private LocalDateTime lockedUntil;

    public ShowSeat() {
    }

    public ShowSeat(int showSeatId, int showId, int seatId, ShowSeatStatus status,
            BigDecimal price) {
        this(showSeatId, showId, seatId, status, price, null, null);
    }

    public ShowSeat(int showSeatId, int showId, int seatId, ShowSeatStatus status,
            BigDecimal price, Integer lockedByUserId, LocalDateTime lockedUntil) {
        this.showSeatId = showSeatId;
        this.showId = showId;
        this.seatId = seatId;
        this.status = status;
        this.price = price;
        this.lockedByUserId = lockedByUserId;
        this.lockedUntil = lockedUntil;
    }

    /**
     * @return {@code true} if this seat can be picked right now, treating a hold
     *         whose deadline has passed as free. The database sweeper eventually
     *         resets such rows, but a read happening before the sweep must not
     *         show a stale hold as if it were live.
     */
    public boolean isSellable(LocalDateTime now) {
        if (status == ShowSeatStatus.AVAILABLE) {
            return true;
        }
        return status == ShowSeatStatus.LOCKED
                && lockedUntil != null
                && lockedUntil.isBefore(now);
    }

    /** @return {@code true} if this user already holds the seat and the hold is live. */
    public boolean isHeldBy(int userId, LocalDateTime now) {
        return status == ShowSeatStatus.LOCKED
                && lockedByUserId != null
                && lockedByUserId.intValue() == userId
                && lockedUntil != null
                && !lockedUntil.isBefore(now);
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
    public Integer getLockedByUserId() { return lockedByUserId; }
    public void setLockedByUserId(Integer lockedByUserId) { this.lockedByUserId = lockedByUserId; }
    public LocalDateTime getLockedUntil() { return lockedUntil; }
    public void setLockedUntil(LocalDateTime lockedUntil) { this.lockedUntil = lockedUntil; }
}
