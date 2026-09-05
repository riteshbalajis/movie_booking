package com.movie_booking;

import com.movie_booking.service.AuthService;
import com.movie_booking.service.BookingService;
import com.movie_booking.service.CatalogService;
import com.movie_booking.service.ExpirySweeper;
import com.movie_booking.service.ShowService;
import com.movie_booking.util.AppConfig;
import com.movie_booking.util.DBConnection;
import com.movie_booking.web.ApiRoutes;
import com.movie_booking.web.Router;
import com.movie_booking.web.SessionStore;
import com.movie_booking.web.StaticFileHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Starts the application.
 *
 * <p>The HTTP server is {@code com.sun.net.httpserver.HttpServer}, which ships
 * inside the JDK. The brief was plain Java with JDBC and no frameworks, so there
 * is no Tomcat and no servlet container - just this, the MySQL driver, and the
 * standard library.
 *
 * <p>Start-up order matters and is deliberate:
 * <ol>
 *   <li>prove the database is reachable, and fail loudly now rather than on the
 *       first click;</li>
 *   <li>make sure an administrator account exists, or nobody could ever sign in
 *       to a fresh database;</li>
 *   <li>begin reclaiming abandoned seat holds;</li>
 *   <li>only then open the port and start accepting traffic.</li>
 * </ol>
 */
public final class Main {

    private static final int DEFAULT_PORT = 8080;

    private Main() {
        // Entry point only.
    }

    public static void main(String[] args) {
        int port = AppConfig.getInt("server.port", DEFAULT_PORT);
        String webRoot = AppConfig.get("server.webRoot", "webapp");

        banner();

        if (!verifyDatabase()) {
            System.exit(1);
        }

        AuthService authService = new AuthService();
        CatalogService catalogService = new CatalogService();
        ShowService showService = new ShowService();
        BookingService bookingService = new BookingService();
        SessionStore sessionStore = new SessionStore();

        bootstrapAdmin(authService);

        ExpirySweeper sweeper = new ExpirySweeper(bookingService);
        sweeper.start();

        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

            Router router = new Router(sessionStore);
            new ApiRoutes(authService, catalogService, showService, bookingService, sessionStore)
                    .registerOn(router);

            // Longest prefix wins in this server, so /api never reaches the
            // static handler registered at /.
            server.createContext("/api", router);
            server.createContext("/", new StaticFileHandler(webRoot));

            // A thread pool, not the default single-threaded executor. Without
            // this every request would be served one at a time, which would hide
            // exactly the concurrency this project is about.
            ExecutorService pool = Executors.newFixedThreadPool(
                    AppConfig.getInt("server.threads", 32));
            server.setExecutor(pool);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n[server] Shutting down...");
                server.stop(1);
                pool.shutdown();
                sweeper.stop();
                DBConnection.shutdown();
                System.out.println("[server] Stopped cleanly.");
            }, "shutdown-hook"));

            server.start();

            System.out.println("[server] Listening on http://localhost:" + port);
            System.out.println("[server] Serving pages from ./" + webRoot);
            System.out.println("[server] Press Ctrl+C to stop.\n");

        } catch (IOException ex) {
            System.err.println("[server] Could not bind to port " + port + ": " + ex.getMessage());
            System.err.println("         Another process may be using it. Try:");
            System.err.println("         java -Dserver.port=9090 -cp ... com.movie_booking.Main");
            System.exit(1);
        }
    }

    /**
     * Opens one connection so a misconfiguration is reported here, with a message
     * that says what to fix, instead of surfacing as a 500 on the first request.
     */
    private static boolean verifyDatabase() {
        try (Connection connection = DBConnection.getConnection()) {
            System.out.println("[db] Connected to "
                    + connection.getMetaData().getDatabaseProductName() + " "
                    + connection.getMetaData().getDatabaseProductVersion());
            return true;

        } catch (SQLException ex) {
            System.err.println("[db] Cannot reach the database: " + ex.getMessage());
            System.err.println();
            System.err.println("  Check that:");
            System.err.println("   1. MySQL is running.");
            System.err.println("   2. The schema is loaded:");
            System.err.println("        mysql -u root -p < db/schema.sql");
            System.err.println("        mysql -u root -p movie_booking < db/seed.sql");
            System.err.println("   3. Credentials are set, either as environment variables");
            System.err.println("        set DB_USER=root");
            System.err.println("        set DB_PASSWORD=your_password");
            System.err.println("      or in config/app.properties");
            return false;

        } catch (IllegalStateException ex) {
            // Thrown by DBConnection when the JDBC driver jar is missing.
            System.err.println("[db] " + ex.getMessage());
            return false;
        }
    }

    private static void bootstrapAdmin(AuthService authService) {
        String email = AppConfig.get("admin.email", "admin@movie.com");
        String password = AppConfig.get("admin.password", "Admin@123");

        if (authService.ensureAdminExists("Administrator", email, password)) {
            System.out.println("[bootstrap] No administrator existed, so one was created:");
            System.out.println("            " + email + " / " + password);
            System.out.println("            Change this password before deploying anywhere real.");
        }
    }

    private static void banner() {
        System.out.println();
        System.out.println("  Movie Booking System");
        System.out.println("  Java " + System.getProperty("java.version")
                + "  |  JDBC + MySQL  |  no frameworks");
        System.out.println("  ------------------------------------------------");
    }
}
