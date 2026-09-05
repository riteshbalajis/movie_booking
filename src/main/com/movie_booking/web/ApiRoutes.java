package com.movie_booking.web;

import com.movie_booking.dto.BookingDetails;
import com.movie_booking.model.Movie;
import com.movie_booking.model.MovieStatus;
import com.movie_booking.model.Screen;
import com.movie_booking.model.Show;
import com.movie_booking.model.User;
import com.movie_booking.service.AuthService;
import com.movie_booking.service.BookingService;
import com.movie_booking.service.CatalogService;
import com.movie_booking.service.ShowService;
import com.movie_booking.util.Json;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * The REST surface: every endpoint the browser talks to, in one place.
 *
 * <p>Each route does the same four things and nothing more - read the input,
 * check who is asking, call one service method, render the result. All the
 * business rules live in the services, so this layer stays thin enough to read
 * top to bottom. Errors are not caught here: they are thrown and
 * {@link Router} turns them into responses.
 */
public final class ApiRoutes {

    private final AuthService authService;
    private final CatalogService catalogService;
    private final ShowService showService;
    private final BookingService bookingService;
    private final SessionStore sessionStore;

    public ApiRoutes(AuthService authService, CatalogService catalogService,
            ShowService showService, BookingService bookingService, SessionStore sessionStore) {
        this.authService = authService;
        this.catalogService = catalogService;
        this.showService = showService;
        this.bookingService = bookingService;
        this.sessionStore = sessionStore;
    }

    /** Registers every endpoint on the router. */
    public void registerOn(Router router) {
        registerAuthRoutes(router);
        registerCatalogRoutes(router);
        registerShowRoutes(router);
        registerBookingRoutes(router);
        registerAdminRoutes(router);

        router.get("/api/health", context -> {
            Map<String, Object> body = Json.object();
            body.put("status", "UP");
            body.put("activeSessions", sessionStore.activeSessionCount());
            body.put("holdMinutes", bookingService.getHoldMinutes());
            context.sendOk(body);
        });
    }

    // =======================================================================
    // Authentication
    // =======================================================================

    private void registerAuthRoutes(Router router) {

        router.post("/api/auth/register", context -> {
            Map<String, Object> body = context.body();
            User user = authService.register(
                    Json.requireString(body, "name"),
                    Json.requireString(body, "email"),
                    Json.requireString(body, "password"),
                    Json.string(body, "phone"));

            // Sign them straight in - making someone type the password again
            // immediately after choosing it adds nothing.
            issueSession(context, user);
            context.sendJson(201, JsonView.user(user));
        });

        router.post("/api/auth/login", context -> {
            Map<String, Object> body = context.body();
            User user = authService.login(
                    Json.requireString(body, "email"),
                    Json.requireString(body, "password"));

            issueSession(context, user);
            context.sendOk(JsonView.user(user));
        });

        router.post("/api/auth/logout", context -> {
            sessionStore.invalidate(context.readSessionToken());
            context.clearSessionCookie();
            context.sendOk(Json.of("message", "Signed out."));
        });

        /** Lets the page find out who, if anyone, is signed in. */
        router.get("/api/auth/me", context -> {
            SessionStore.Session session = context.session();
            if (session == null) {
                context.sendOk(Json.of("user", null));
            } else {
                context.sendOk(Json.of("user", JsonView.session(session)));
            }
        });

        router.post("/api/auth/password", context -> {
            SessionStore.Session session = context.requireUser();
            Map<String, Object> body = context.body();

            authService.changePassword(session.getUserId(),
                    Json.requireString(body, "currentPassword"),
                    Json.requireString(body, "newPassword"));

            context.sendOk(Json.of("message", "Password updated."));
        });
    }

    private void issueSession(RequestContext context, User user) {
        String token = sessionStore.create(user.getUserId(), user.getName(),
                user.getEmail(), user.getRole());
        context.setSessionCookie(token);
    }

    // =======================================================================
    // Catalogue
    // =======================================================================

    private void registerCatalogRoutes(Router router) {

        router.get("/api/movies", context -> {
            String search = context.queryParam("q");
            List<Movie> movies = (search == null || search.trim().isEmpty())
                    ? catalogService.listActiveMovies()
                    : catalogService.searchMovies(search);
            context.sendOk(Json.of("movies", JsonView.movies(movies)));
        });

        router.get("/api/movies/{id}", context ->
                context.sendOk(JsonView.movie(
                        catalogService.getMovie(context.pathParamInt("id")))));

        router.get("/api/theatres", context ->
                context.sendOk(Json.of("theatres",
                        JsonView.theatres(catalogService.listActiveTheatres()))));

        router.get("/api/theatres/{id}/screens", context ->
                context.sendOk(Json.of("screens",
                        JsonView.screens(catalogService.listScreens(
                                context.pathParamInt("id"))))));
    }

    // =======================================================================
    // Shows and seat maps
    // =======================================================================

    private void registerShowRoutes(Router router) {

        /** Showtimes for one film, defaulting to today. */
        router.get("/api/movies/{id}/shows", context -> {
            LocalDate date = context.queryParamDate("date", LocalDate.now());
            context.sendOk(Json.of("shows", JsonView.shows(
                    showService.listShows(context.pathParamInt("id"), date))));
        });

        router.get("/api/shows/{id}", context ->
                context.sendOk(JsonView.show(
                        showService.getShow(context.pathParamInt("id")))));

        /**
         * The seat grid. Anonymous visitors may browse it; the viewer id is
         * passed through only so that seats they already hold come back marked
         * MINE rather than as somebody else's hold.
         */
        router.get("/api/shows/{id}/seats", context -> {
            int showId = context.pathParamInt("id");

            Map<String, Object> body = Json.object();
            body.put("show", JsonView.show(showService.getShow(showId)));
            body.put("seats", JsonView.seats(
                    showService.getSeatMap(showId, context.userIdOrAnonymous())));
            body.put("holdMinutes", bookingService.getHoldMinutes());
            body.put("maxSeatsPerBooking", bookingService.getMaxSeatsPerBooking());
            context.sendOk(body);
        });
    }

    // =======================================================================
    // Booking
    // =======================================================================

    private void registerBookingRoutes(Router router) {

        /** Step 1: claim the seats for a few minutes. */
        router.post("/api/bookings/hold", context -> {
            SessionStore.Session session = context.requireUser();
            Map<String, Object> body = context.body();

            BookingDetails booking = bookingService.holdSeats(
                    session.getUserId(),
                    Json.requireInt(body, "showId"),
                    Json.requireIntList(body, "showSeatIds"));

            context.sendJson(201, JsonView.booking(booking));
        });

        /** Step 2: turn the hold into a sale. */
        router.post("/api/bookings/{id}/confirm", context -> {
            SessionStore.Session session = context.requireUser();
            context.sendOk(JsonView.booking(bookingService.confirmBooking(
                    session.getUserId(), context.pathParamInt("id"))));
        });

        router.post("/api/bookings/{id}/cancel", context -> {
            SessionStore.Session session = context.requireUser();
            context.sendOk(JsonView.booking(bookingService.cancelBooking(
                    session.getUserId(), context.pathParamInt("id"), session.getRole())));
        });

        router.get("/api/bookings", context -> {
            SessionStore.Session session = context.requireUser();
            context.sendOk(Json.of("bookings",
                    JsonView.bookings(bookingService.getMyBookings(session.getUserId()))));
        });

        router.get("/api/bookings/{id}", context -> {
            SessionStore.Session session = context.requireUser();
            context.sendOk(JsonView.booking(bookingService.getBookingForUser(
                    session.getUserId(), context.pathParamInt("id"), session.getRole())));
        });
    }

    // =======================================================================
    // Admin
    // =======================================================================

    private void registerAdminRoutes(Router router) {

        router.get("/api/admin/movies", context -> {
            context.requireAdmin();
            context.sendOk(Json.of("movies", JsonView.movies(catalogService.listAllMovies())));
        });

        router.post("/api/admin/movies", context -> {
            context.requireAdmin();
            Map<String, Object> body = context.body();

            Movie movie = new Movie();
            movie.setTitle(Json.requireString(body, "title"));
            movie.setDescription(Json.string(body, "description"));
            movie.setDurationMinutes(Json.requireInt(body, "durationMinutes"));
            movie.setLanguage(Json.string(body, "language"));
            movie.setGenre(Json.string(body, "genre"));

            String releaseDate = Json.string(body, "releaseDate");
            if (releaseDate != null && !releaseDate.trim().isEmpty()) {
                movie.setReleaseDate(LocalDate.parse(releaseDate.trim()));
            }
            String status = Json.string(body, "status");
            movie.setStatus(status == null || status.trim().isEmpty()
                    ? MovieStatus.ACTIVE : MovieStatus.valueOf(status.trim().toUpperCase()));

            context.sendJson(201, JsonView.movie(catalogService.createMovie(movie)));
        });

        router.delete("/api/admin/movies/{id}", context -> {
            context.requireAdmin();
            catalogService.deactivateMovie(context.pathParamInt("id"));
            context.sendOk(Json.of("message", "Film retired from listings."));
        });

        router.post("/api/admin/theatres", context -> {
            context.requireAdmin();
            Map<String, Object> body = context.body();
            context.sendJson(201, JsonView.theatre(catalogService.createTheatre(
                    Json.requireString(body, "name"),
                    Json.requireString(body, "location"))));
        });

        /** Creates a screen and generates its whole seat layout in one step. */
        router.post("/api/admin/screens", context -> {
            context.requireAdmin();
            Map<String, Object> body = context.body();

            Screen screen = catalogService.createScreenWithLayout(
                    Json.requireInt(body, "theatreId"),
                    Json.requireString(body, "name"),
                    Json.optionalInt(body, "rows", 8),
                    Json.optionalInt(body, "seatsPerRow", 12),
                    Json.optionalInt(body, "regularRows", 3),
                    Json.optionalInt(body, "premiumRows", 3));

            context.sendJson(201, JsonView.screen(screen));
        });

        router.get("/api/admin/shows", context -> {
            context.requireAdmin();
            LocalDate date = context.queryParamDate("date", LocalDate.now());
            context.sendOk(Json.of("shows", JsonView.shows(showService.listShowsByDate(date))));
        });

        router.post("/api/admin/shows", context -> {
            context.requireAdmin();
            Map<String, Object> body = context.body();

            Show show = showService.scheduleShow(
                    Json.requireInt(body, "movieId"),
                    Json.requireInt(body, "screenId"),
                    LocalDate.parse(Json.requireString(body, "showDate")),
                    LocalTime.parse(Json.requireString(body, "startTime")),
                    Json.requireDecimal(body, "regularPrice"),
                    Json.requireDecimal(body, "premiumPrice"),
                    Json.requireDecimal(body, "reclinerPrice"));

            context.sendJson(201, JsonView.show(showService.getShow(show.getShowId())));
        });

        router.post("/api/admin/shows/{id}/cancel", context -> {
            context.requireAdmin();
            int cancelled = showService.cancelShow(context.pathParamInt("id"));

            Map<String, Object> response = Json.object();
            response.put("message", "Show cancelled.");
            response.put("bookingsCancelled", cancelled);
            context.sendOk(response);
        });

        router.get("/api/admin/users", context -> {
            context.requireAdmin();
            List<User> users = authService.findAll();

            List<Object> json = new java.util.ArrayList<Object>(users.size());
            for (User user : users) {
                json.add(JsonView.user(user));
            }
            context.sendOk(Json.of("users", json));
        });
    }
}
