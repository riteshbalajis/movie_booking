package com.movie_booking.service;

import com.movie_booking.dao.MovieDao;
import com.movie_booking.dao.MovieDaoImpl;
import com.movie_booking.dao.ScreenDao;
import com.movie_booking.dao.ScreenDaoImpl;
import com.movie_booking.dao.SeatDao;
import com.movie_booking.dao.SeatDaoImpl;
import com.movie_booking.dao.TheatreDao;
import com.movie_booking.dao.TheatreDaoImpl;
import com.movie_booking.exception.ConflictException;
import com.movie_booking.exception.DataAccessException;
import com.movie_booking.exception.NotFoundException;
import com.movie_booking.exception.ValidationException;
import com.movie_booking.model.Movie;
import com.movie_booking.model.MovieStatus;
import com.movie_booking.model.Screen;
import com.movie_booking.model.ScreenStatus;
import com.movie_booking.model.Seat;
import com.movie_booking.model.SeatType;
import com.movie_booking.model.Theatre;
import com.movie_booking.model.TheatreStatus;
import com.movie_booking.util.Tx;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The catalogue: films, and the venues that show them.
 *
 * <p>Everything here is reference data - read on every page, changed only by an
 * administrator. None of it takes part in the booking transaction, so these
 * methods are plain single-statement units of work. The one exception is
 * {@link #createScreenWithLayout}, where the screen and its seats have to appear
 * together or not at all.
 */
public class CatalogService {

    private static final int MAX_ROWS = 26;          // A..Z
    private static final int MAX_SEATS_PER_ROW = 40;
    private static final int MAX_DURATION_MINUTES = 600;

    private final MovieDao movieDao;
    private final TheatreDao theatreDao;
    private final ScreenDao screenDao;
    private final SeatDao seatDao;

    public CatalogService() {
        this(new MovieDaoImpl(), new TheatreDaoImpl(), new ScreenDaoImpl(), new SeatDaoImpl());
    }

    public CatalogService(MovieDao movieDao, TheatreDao theatreDao, ScreenDao screenDao,
            SeatDao seatDao) {
        this.movieDao = movieDao;
        this.theatreDao = theatreDao;
        this.screenDao = screenDao;
        this.seatDao = seatDao;
    }

    // =======================================================================
    // Movies
    // =======================================================================

    /** The home-page listing: films currently on. */
    public List<Movie> listActiveMovies() {
        try {
            return movieDao.findAllActive();
        } catch (SQLException ex) {
            throw new DataAccessException("Could not load the film list.", ex);
        }
    }

    /** Admin listing: everything, including retired and upcoming films. */
    public List<Movie> listAllMovies() {
        try {
            return movieDao.findAll();
        } catch (SQLException ex) {
            throw new DataAccessException("Could not load the film list.", ex);
        }
    }

    public List<Movie> searchMovies(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return listActiveMovies();
        }
        try {
            return movieDao.searchMovies(keyword.trim());
        } catch (SQLException ex) {
            throw new DataAccessException("Search failed.", ex);
        }
    }

    public Movie getMovie(int movieId) {
        try {
            Movie movie = movieDao.findById(movieId);
            if (movie == null) {
                throw new NotFoundException("Film not found.");
            }
            return movie;
        } catch (SQLException ex) {
            throw new DataAccessException("Could not load the film.", ex);
        }
    }

    public Movie createMovie(Movie movie) {
        validateMovie(movie);
        try {
            if (movieDao.findByTitle(movie.getTitle()) != null) {
                throw new ConflictException(
                        "A film called \"" + movie.getTitle() + "\" already exists.");
            }
            movie.setMovieId(movieDao.createMovie(movie));
            return movie;
        } catch (SQLException ex) {
            throw new DataAccessException("Could not save the film.", ex);
        }
    }

    public Movie updateMovie(Movie movie) {
        validateMovie(movie);
        try {
            if (!movieDao.existsById(movie.getMovieId())) {
                throw new NotFoundException("Film not found.");
            }
            movieDao.updateMovie(movie);
            return movie;
        } catch (SQLException ex) {
            throw new DataAccessException("Could not update the film.", ex);
        }
    }

    /**
     * Retires a film from the listings.
     *
     * <p>A soft delete, not a real one: past bookings reference this row, and
     * deleting it would leave those tickets pointing at nothing.
     */
    public void deactivateMovie(int movieId) {
        try {
            if (!movieDao.deactivateMovie(movieId)) {
                throw new NotFoundException("Film not found.");
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Could not retire the film.", ex);
        }
    }

    public void activateMovie(int movieId) {
        try {
            if (!movieDao.activateMovie(movieId)) {
                throw new NotFoundException("Film not found.");
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Could not restore the film.", ex);
        }
    }

    // =======================================================================
    // Theatres
    // =======================================================================

    public List<Theatre> listActiveTheatres() {
        try {
            return theatreDao.findAllActive();
        } catch (SQLException ex) {
            throw new DataAccessException("Could not load the theatre list.", ex);
        }
    }

    public Theatre getTheatre(int theatreId) {
        try {
            Theatre theatre = theatreDao.findById(theatreId);
            if (theatre == null) {
                throw new NotFoundException("Theatre not found.");
            }
            return theatre;
        } catch (SQLException ex) {
            throw new DataAccessException("Could not load the theatre.", ex);
        }
    }

    public Theatre createTheatre(String name, String location) {
        String cleanName = require(name, "Theatre name");
        String cleanLocation = require(location, "Location");

        Theatre theatre = new Theatre();
        theatre.setName(cleanName);
        theatre.setLocation(cleanLocation);
        theatre.setStatus(TheatreStatus.ACTIVE);

        try {
            if (theatreDao.findByName(cleanName) != null) {
                throw new ConflictException("A theatre with that name already exists.");
            }
            theatre.setTheatreId(theatreDao.createTheatre(theatre));
            return theatre;
        } catch (SQLException ex) {
            throw new DataAccessException("Could not save the theatre.", ex);
        }
    }

    // =======================================================================
    // Screens and seat layouts
    // =======================================================================

    public List<Screen> listScreens(int theatreId) {
        try {
            return screenDao.findActiveScreensByTheatreId(theatreId);
        } catch (SQLException ex) {
            throw new DataAccessException("Could not load the screens.", ex);
        }
    }

    public Screen getScreen(int screenId) {
        try {
            Screen screen = screenDao.findById(screenId);
            if (screen == null) {
                throw new NotFoundException("Screen not found.");
            }
            return screen;
        } catch (SQLException ex) {
            throw new DataAccessException("Could not load the screen.", ex);
        }
    }

    /**
     * Creates a screen together with a full rectangular seat layout.
     *
     * <p>Rows are labelled A, B, C...; the first {@code regularRows} are REGULAR,
     * the next {@code premiumRows} PREMIUM, and anything left over RECLINER -
     * cheap seats at the front, the good ones at the back, as in a real cinema.
     *
     * <p>The screen row and its seats are written in one transaction. A screen
     * that exists with no seats would show up in the admin list and then produce a
     * show nobody can book.
     */
    public Screen createScreenWithLayout(final int theatreId, String screenName,
            final int rowCount, final int seatsPerRow, final int regularRows,
            final int premiumRows) {

        final String cleanName = require(screenName, "Screen name");
        validateLayout(rowCount, seatsPerRow, regularRows, premiumRows);

        final Screen screen = new Screen();
        screen.setTheatreId(theatreId);
        screen.setName(cleanName);
        screen.setCapacity(rowCount * seatsPerRow);
        screen.setStatus(ScreenStatus.ACTIVE);

        return Tx.execute(new Tx.Work<Screen>() {
            @Override
            public Screen execute(Connection connection) throws SQLException {
                if (theatreDao.findById(theatreId) == null) {
                    throw new NotFoundException("Theatre not found.");
                }
                if (screenDao.existsByName(theatreId, cleanName)) {
                    throw new ConflictException(
                            "This theatre already has a screen called " + cleanName + ".");
                }

                int screenId = screenDao.createScreen(screen);
                screen.setScreenId(screenId);

                seatDao.createSeats(connection,
                        buildLayout(screenId, rowCount, seatsPerRow, regularRows, premiumRows));
                return screen;
            }
        });
    }

    public List<Seat> listSeats(int screenId) {
        try {
            return seatDao.findActiveSeatsByScreenId(screenId);
        } catch (SQLException ex) {
            throw new DataAccessException("Could not load the seat layout.", ex);
        }
    }

    /** Generates the seat rows for a rectangular auditorium. */
    private List<Seat> buildLayout(int screenId, int rowCount, int seatsPerRow,
            int regularRows, int premiumRows) {

        List<Seat> seats = new ArrayList<Seat>(rowCount * seatsPerRow);

        for (int row = 0; row < rowCount; row++) {
            String rowLabel = String.valueOf((char) ('A' + row));
            SeatType seatType;
            if (row < regularRows) {
                seatType = SeatType.REGULAR;
            } else if (row < regularRows + premiumRows) {
                seatType = SeatType.PREMIUM;
            } else {
                seatType = SeatType.RECLINER;
            }

            for (int number = 1; number <= seatsPerRow; number++) {
                Seat seat = new Seat();
                seat.setScreenId(screenId);
                seat.setRowLabel(rowLabel);
                seat.setSeatNumber(number);
                seat.setSeatType(seatType);
                seats.add(seat);
            }
        }
        return seats;
    }

    // =======================================================================
    // Validation
    // =======================================================================

    private void validateMovie(Movie movie) {
        if (movie == null) {
            throw new ValidationException("No film details were supplied.");
        }
        movie.setTitle(require(movie.getTitle(), "Title"));

        if (movie.getDurationMinutes() <= 0) {
            throw new ValidationException("Duration must be a positive number of minutes.");
        }
        if (movie.getDurationMinutes() > MAX_DURATION_MINUTES) {
            throw new ValidationException(
                    "Duration must be under " + MAX_DURATION_MINUTES + " minutes.");
        }
        if (movie.getStatus() == null) {
            movie.setStatus(MovieStatus.ACTIVE);
        }
        LocalDate release = movie.getReleaseDate();
        if (release != null && release.getYear() < 1888) {
            // 1888 is Roundhay Garden Scene - there is no earlier film to catalogue.
            throw new ValidationException("Release date looks wrong.");
        }
    }

    private void validateLayout(int rowCount, int seatsPerRow, int regularRows, int premiumRows) {
        if (rowCount <= 0 || rowCount > MAX_ROWS) {
            throw new ValidationException("Rows must be between 1 and " + MAX_ROWS + ".");
        }
        if (seatsPerRow <= 0 || seatsPerRow > MAX_SEATS_PER_ROW) {
            throw new ValidationException(
                    "Seats per row must be between 1 and " + MAX_SEATS_PER_ROW + ".");
        }
        if (regularRows < 0 || premiumRows < 0) {
            throw new ValidationException("Row counts cannot be negative.");
        }
        if (regularRows + premiumRows > rowCount) {
            throw new ValidationException(
                    "Regular and premium rows together exceed the number of rows.");
        }
    }

    private String require(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new ValidationException(fieldName + " is required.");
        }
        return value.trim();
    }
}
