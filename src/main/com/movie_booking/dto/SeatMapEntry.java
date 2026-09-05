package com.movie_booking.dto;

import com.movie_booking.model.SeatType;
import java.math.BigDecimal;

/**
 * One cell of the seat-selection grid.
 *
 * <p>Drawing that grid needs columns from three tables ({@code show_seats} for
 * price and state, {@code seats} for the label and type, and the show for
 * context). Returning three separate lists and joining them in Java would mean
 * N+1 queries per render; this flat view is filled by a single join instead.
 *
 * <p>{@link #displayStatus} is the <em>effective</em> state as the viewer should
 * see it, not the raw column value. A hold that has lapsed is reported as
 * {@code AVAILABLE} even though the row still says {@code LOCKED}, and a seat the
 * viewer is holding right now is reported as {@code MINE} so the UI can colour it
 * differently from someone else's hold.
 */
public class SeatMapEntry {

    /** Values {@link #displayStatus} can take. */
    public static final String AVAILABLE = "AVAILABLE";
    public static final String HELD_BY_OTHER = "HELD";
    public static final String MINE = "MINE";
    public static final String BOOKED = "BOOKED";

    private final int showSeatId;
    private final int seatId;
    private final String rowLabel;
    private final int seatNumber;
    private final SeatType seatType;
    private final BigDecimal price;
    private final String displayStatus;

    public SeatMapEntry(int showSeatId, int seatId, String rowLabel, int seatNumber,
            SeatType seatType, BigDecimal price, String displayStatus) {
        this.showSeatId = showSeatId;
        this.seatId = seatId;
        this.rowLabel = rowLabel;
        this.seatNumber = seatNumber;
        this.seatType = seatType;
        this.price = price;
        this.displayStatus = displayStatus;
    }

    /** @return the label a human reads on the ticket, e.g. {@code "H7"}. */
    public String getSeatLabel() {
        return rowLabel + seatNumber;
    }

    public boolean isSelectable() {
        return AVAILABLE.equals(displayStatus) || MINE.equals(displayStatus);
    }

    public int getShowSeatId() { return showSeatId; }
    public int getSeatId() { return seatId; }
    public String getRowLabel() { return rowLabel; }
    public int getSeatNumber() { return seatNumber; }
    public SeatType getSeatType() { return seatType; }
    public BigDecimal getPrice() { return price; }
    public String getDisplayStatus() { return displayStatus; }
}
