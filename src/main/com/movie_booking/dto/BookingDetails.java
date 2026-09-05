package com.movie_booking.dto;

import com.movie_booking.model.BookingStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

/**
 * Everything a ticket or a "My bookings" row needs, in one object.
 *
 * <p>The raw {@link com.movie_booking.model.Booking} entity holds only ids, so
 * rendering it would mean fetching the show, then the movie, then the screen,
 * then the theatre, then the seats - five round trips per booking, and twenty-five
 * for a list of five. This view is filled by one join instead.
 *
 * <p>It is immutable: it is a snapshot for display, never something to write back.
 */
public class BookingDetails {

    private final int bookingId;
    private final String bookingRef;
    private final BookingStatus status;
    private final BigDecimal totalAmount;
    private final LocalDateTime bookedAt;
    private final LocalDateTime expiresAt;

    private final int showId;
    private final String movieTitle;
    private final String language;
    private final int durationMinutes;
    private final String theatreName;
    private final String theatreLocation;
    private final String screenName;
    private final LocalDate showDate;
    private final LocalTime startTime;

    private final List<String> seatLabels;

    public BookingDetails(int bookingId, String bookingRef, BookingStatus status,
            BigDecimal totalAmount, LocalDateTime bookedAt, LocalDateTime expiresAt,
            int showId, String movieTitle, String language, int durationMinutes,
            String theatreName, String theatreLocation, String screenName,
            LocalDate showDate, LocalTime startTime, List<String> seatLabels) {
        this.bookingId = bookingId;
        this.bookingRef = bookingRef;
        this.status = status;
        this.totalAmount = totalAmount;
        this.bookedAt = bookedAt;
        this.expiresAt = expiresAt;
        this.showId = showId;
        this.movieTitle = movieTitle;
        this.language = language;
        this.durationMinutes = durationMinutes;
        this.theatreName = theatreName;
        this.theatreLocation = theatreLocation;
        this.screenName = screenName;
        this.showDate = showDate;
        this.startTime = startTime;
        this.seatLabels = Collections.unmodifiableList(seatLabels);
    }

    public int getSeatCount() {
        return seatLabels.size();
    }

    public int getBookingId() { return bookingId; }
    public String getBookingRef() { return bookingRef; }
    public BookingStatus getStatus() { return status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public LocalDateTime getBookedAt() { return bookedAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public int getShowId() { return showId; }
    public String getMovieTitle() { return movieTitle; }
    public String getLanguage() { return language; }
    public int getDurationMinutes() { return durationMinutes; }
    public String getTheatreName() { return theatreName; }
    public String getTheatreLocation() { return theatreLocation; }
    public String getScreenName() { return screenName; }
    public LocalDate getShowDate() { return showDate; }
    public LocalTime getStartTime() { return startTime; }
    public List<String> getSeatLabels() { return seatLabels; }
}
