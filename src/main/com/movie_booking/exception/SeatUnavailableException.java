package com.movie_booking.exception;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Raised when one or more requested seats were taken between the moment the user
 * loaded the seat map and the moment they pressed "Book".
 *
 * <p>This is the expected, non-exceptional outcome of losing a race - two people
 * clicking seat H7 at the same instant - so it carries the exact seat labels that
 * failed. The UI uses them to re-draw only those seats as taken instead of
 * dumping the user back to the start.
 *
 * <p>Reported to the client as HTTP 409.
 */
public class SeatUnavailableException extends AppException {

    private static final long serialVersionUID = 1L;

    private final List<String> unavailableSeats;

    public SeatUnavailableException(List<String> unavailableSeats) {
        super(buildMessage(unavailableSeats), 409, "SEATS_UNAVAILABLE");
        this.unavailableSeats = Collections.unmodifiableList(
                new ArrayList<String>(unavailableSeats));
    }

    /** @return the human-readable seat labels, e.g. {@code ["H7", "H8"]}. */
    public List<String> getUnavailableSeats() {
        return unavailableSeats;
    }

    private static String buildMessage(List<String> seats) {
        if (seats == null || seats.isEmpty()) {
            return "Those seats are no longer available.";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < seats.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(seats.get(i));
        }
        return (seats.size() == 1 ? "Seat " : "Seats ") + builder
                + " just got booked by someone else. Please pick another.";
    }
}
