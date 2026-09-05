package com.movie_booking.web;

import com.movie_booking.dto.BookingDetails;
import com.movie_booking.dto.SeatMapEntry;
import com.movie_booking.dto.ShowSummary;
import com.movie_booking.model.Movie;
import com.movie_booking.model.Screen;
import com.movie_booking.model.Theatre;
import com.movie_booking.model.User;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.movie_booking.util.Json;

/**
 * Turns domain objects into the maps that go out as JSON.
 *
 * <p>This exists so that the wire format is decided in exactly one place. Two
 * consequences matter:
 *
 * <ul>
 *   <li><b>Nothing leaks by accident.</b> A user is serialised field by field
 *       here, so {@code passwordHash} cannot reach the browser even if someone
 *       later forgets to null it out in the service. Reflecting over the object
 *       would happily publish every field a future developer adds.</li>
 *   <li><b>Renaming a Java field does not break the front end</b>, because the
 *       JSON key is written out explicitly rather than derived from the getter.</li>
 * </ul>
 */
final class JsonView {

    private JsonView() {
        // Utility class.
    }

    static Map<String, Object> user(User user) {
        Map<String, Object> json = Json.object();
        json.put("userId", user.getUserId());
        json.put("name", user.getName());
        json.put("email", user.getEmail());
        json.put("phone", user.getPhone());
        json.put("role", user.getRole());
        // Note: passwordHash is deliberately absent.
        return json;
    }

    static Map<String, Object> session(SessionStore.Session session) {
        Map<String, Object> json = Json.object();
        json.put("userId", session.getUserId());
        json.put("name", session.getName());
        json.put("email", session.getEmail());
        json.put("role", session.getRole());
        json.put("isAdmin", session.isAdmin());
        return json;
    }

    static Map<String, Object> movie(Movie movie) {
        Map<String, Object> json = Json.object();
        json.put("movieId", movie.getMovieId());
        json.put("title", movie.getTitle());
        json.put("description", movie.getDescription());
        json.put("durationMinutes", movie.getDurationMinutes());
        json.put("language", movie.getLanguage());
        json.put("genre", movie.getGenre());
        json.put("releaseDate", movie.getReleaseDate());
        json.put("status", movie.getStatus());
        return json;
    }

    static List<Object> movies(List<Movie> movies) {
        List<Object> json = new ArrayList<Object>(movies.size());
        for (Movie movie : movies) {
            json.add(movie(movie));
        }
        return json;
    }

    static Map<String, Object> theatre(Theatre theatre) {
        Map<String, Object> json = Json.object();
        json.put("theatreId", theatre.getTheatreId());
        json.put("name", theatre.getName());
        json.put("location", theatre.getLocation());
        json.put("status", theatre.getStatus());
        return json;
    }

    static List<Object> theatres(List<Theatre> theatres) {
        List<Object> json = new ArrayList<Object>(theatres.size());
        for (Theatre theatre : theatres) {
            json.add(theatre(theatre));
        }
        return json;
    }

    static Map<String, Object> screen(Screen screen) {
        Map<String, Object> json = Json.object();
        json.put("screenId", screen.getScreenId());
        json.put("theatreId", screen.getTheatreId());
        json.put("name", screen.getName());
        json.put("capacity", screen.getCapacity());
        json.put("status", screen.getStatus());
        return json;
    }

    static List<Object> screens(List<Screen> screens) {
        List<Object> json = new ArrayList<Object>(screens.size());
        for (Screen screen : screens) {
            json.add(screen(screen));
        }
        return json;
    }

    static Map<String, Object> show(ShowSummary show) {
        Map<String, Object> json = Json.object();
        json.put("showId", show.getShowId());
        json.put("status", show.getStatus());
        json.put("showDate", show.getShowDate());
        json.put("startTime", show.getStartTime());
        json.put("endTime", show.getEndTime());
        json.put("movieId", show.getMovieId());
        json.put("movieTitle", show.getMovieTitle());
        json.put("language", show.getLanguage());
        json.put("genre", show.getGenre());
        json.put("durationMinutes", show.getDurationMinutes());
        json.put("theatreId", show.getTheatreId());
        json.put("theatreName", show.getTheatreName());
        json.put("theatreLocation", show.getTheatreLocation());
        json.put("screenId", show.getScreenId());
        json.put("screenName", show.getScreenName());
        json.put("availableSeats", show.getAvailableSeats());
        json.put("totalSeats", show.getTotalSeats());
        json.put("minPrice", show.getMinPrice());
        json.put("fillLevel", show.getFillLevel());
        return json;
    }

    static List<Object> shows(List<ShowSummary> shows) {
        List<Object> json = new ArrayList<Object>(shows.size());
        for (ShowSummary show : shows) {
            json.add(show(show));
        }
        return json;
    }

    static Map<String, Object> seat(SeatMapEntry seat) {
        Map<String, Object> json = Json.object();
        json.put("showSeatId", seat.getShowSeatId());
        json.put("label", seat.getSeatLabel());
        json.put("rowLabel", seat.getRowLabel());
        json.put("seatNumber", seat.getSeatNumber());
        json.put("seatType", seat.getSeatType());
        json.put("price", seat.getPrice());
        json.put("status", seat.getDisplayStatus());
        json.put("selectable", seat.isSelectable());
        return json;
    }

    static List<Object> seats(List<SeatMapEntry> seats) {
        List<Object> json = new ArrayList<Object>(seats.size());
        for (SeatMapEntry seat : seats) {
            json.add(seat(seat));
        }
        return json;
    }

    static Map<String, Object> booking(BookingDetails booking) {
        Map<String, Object> json = Json.object();
        json.put("bookingId", booking.getBookingId());
        json.put("bookingRef", booking.getBookingRef());
        json.put("status", booking.getStatus());
        json.put("totalAmount", booking.getTotalAmount());
        json.put("bookedAt", booking.getBookedAt());
        json.put("expiresAt", booking.getExpiresAt());
        json.put("showId", booking.getShowId());
        json.put("movieTitle", booking.getMovieTitle());
        json.put("language", booking.getLanguage());
        json.put("durationMinutes", booking.getDurationMinutes());
        json.put("theatreName", booking.getTheatreName());
        json.put("theatreLocation", booking.getTheatreLocation());
        json.put("screenName", booking.getScreenName());
        json.put("showDate", booking.getShowDate());
        json.put("startTime", booking.getStartTime());
        json.put("seats", booking.getSeatLabels());
        json.put("seatCount", booking.getSeatCount());
        return json;
    }

    static List<Object> bookings(List<BookingDetails> bookings) {
        List<Object> json = new ArrayList<Object>(bookings.size());
        for (BookingDetails booking : bookings) {
            json.add(booking(booking));
        }
        return json;
    }
}
