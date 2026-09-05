package com.movie_booking.dto;

import com.movie_booking.model.ShowStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * One row of a showtimes listing: which film, where, when, how much, and how many
 * seats are left.
 *
 * <p>The seat count is computed in SQL as part of the same query rather than by
 * looping over shows in Java and asking the database once per show. On a listing
 * page with twenty showtimes that is the difference between one query and
 * twenty-one.
 */
public class ShowSummary {

    private final int showId;
    private final ShowStatus status;
    private final LocalDate showDate;
    private final LocalTime startTime;
    private final LocalTime endTime;

    private final int movieId;
    private final String movieTitle;
    private final String language;
    private final String genre;
    private final int durationMinutes;

    private final int theatreId;
    private final String theatreName;
    private final String theatreLocation;
    private final int screenId;
    private final String screenName;

    private final int availableSeats;
    private final int totalSeats;
    private final BigDecimal minPrice;

    public ShowSummary(int showId, ShowStatus status, LocalDate showDate, LocalTime startTime,
            LocalTime endTime, int movieId, String movieTitle, String language, String genre,
            int durationMinutes, int theatreId, String theatreName, String theatreLocation,
            int screenId, String screenName, int availableSeats, int totalSeats,
            BigDecimal minPrice) {
        this.showId = showId;
        this.status = status;
        this.showDate = showDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.movieId = movieId;
        this.movieTitle = movieTitle;
        this.language = language;
        this.genre = genre;
        this.durationMinutes = durationMinutes;
        this.theatreId = theatreId;
        this.theatreName = theatreName;
        this.theatreLocation = theatreLocation;
        this.screenId = screenId;
        this.screenName = screenName;
        this.availableSeats = availableSeats;
        this.totalSeats = totalSeats;
        this.minPrice = minPrice;
    }

    /** Drives the green/amber/red "filling fast" badge on the listing. */
    public String getFillLevel() {
        if (availableSeats == 0) {
            return "SOLD_OUT";
        }
        if (totalSeats > 0 && availableSeats * 100 / totalSeats < 20) {
            return "FILLING_FAST";
        }
        return "AVAILABLE";
    }

    public int getShowId() { return showId; }
    public ShowStatus getStatus() { return status; }
    public LocalDate getShowDate() { return showDate; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public int getMovieId() { return movieId; }
    public String getMovieTitle() { return movieTitle; }
    public String getLanguage() { return language; }
    public String getGenre() { return genre; }
    public int getDurationMinutes() { return durationMinutes; }
    public int getTheatreId() { return theatreId; }
    public String getTheatreName() { return theatreName; }
    public String getTheatreLocation() { return theatreLocation; }
    public int getScreenId() { return screenId; }
    public String getScreenName() { return screenName; }
    public int getAvailableSeats() { return availableSeats; }
    public int getTotalSeats() { return totalSeats; }
    public BigDecimal getMinPrice() { return minPrice; }
}
