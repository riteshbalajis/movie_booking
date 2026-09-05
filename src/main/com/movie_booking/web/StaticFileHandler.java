package com.movie_booking.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Serves the HTML, CSS and JavaScript in {@code webapp/}.
 *
 * <p><b>Path traversal.</b> The request path comes from the network and cannot be
 * trusted. A request for {@code /../../config/app.properties} would, if joined
 * naively, resolve to a file well outside the web root and hand over the database
 * password. The defence is in {@link #resolveSafely}: the joined path is
 * normalised to an absolute real path and then checked to be inside the web root.
 * Blacklisting {@code ".."} is not enough - URL-encoded and doubled-up variants
 * slip past string checks, whereas normalising and comparing the final location
 * cannot be tricked.
 */
public class StaticFileHandler implements HttpHandler {

    private final Path webRoot;

    public StaticFileHandler(String directory) {
        this.webRoot = Paths.get(directory).toAbsolutePath().normalize();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String requestPath = exchange.getRequestURI().getPath();

        try {
            if (!"GET".equals(exchange.getRequestMethod())
                    && !"HEAD".equals(exchange.getRequestMethod())) {
                sendPlain(exchange, 405, "Method not allowed");
                return;
            }

            // "/" means the home page.
            if (requestPath.isEmpty() || "/".equals(requestPath)) {
                requestPath = "/index.html";
            }
            // Extension-less paths are treated as pages: /bookings -> bookings.html
            if (!requestPath.contains(".")) {
                requestPath = requestPath + ".html";
            }

            Path file = resolveSafely(requestPath);
            if (file == null || !Files.isRegularFile(file)) {
                sendPlain(exchange, 404, "Not found: " + requestPath);
                return;
            }

            byte[] content = Files.readAllBytes(file);
            exchange.getResponseHeaders().set("Content-Type", contentTypeOf(file));
            // The demo is edited while it runs, so caching would only serve
            // yesterday's page. A real deployment would cache fingerprinted assets.
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");

            exchange.sendResponseHeaders(200, content.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(content);
            }

        } catch (IOException ex) {
            System.err.println("[static] Failed to serve " + requestPath + ": " + ex.getMessage());
            sendPlain(exchange, 500, "Could not read the requested file.");
        }
    }

    /**
     * @return the file inside the web root, or {@code null} if the path escapes it
     */
    private Path resolveSafely(String requestPath) {
        String relative = requestPath.startsWith("/") ? requestPath.substring(1) : requestPath;

        Path resolved = webRoot.resolve(relative).normalize().toAbsolutePath();
        return resolved.startsWith(webRoot) ? resolved : null;
    }

    private String contentTypeOf(Path file) {
        String name = file.getFileName().toString().toLowerCase();

        if (name.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }
        if (name.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (name.endsWith(".js")) {
            return "application/javascript; charset=utf-8";
        }
        if (name.endsWith(".json")) {
            return "application/json; charset=utf-8";
        }
        if (name.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (name.endsWith(".png")) {
            return "image/png";
        }
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (name.endsWith(".ico")) {
            return "image/x-icon";
        }
        return "application/octet-stream";
    }

    private void sendPlain(HttpExchange exchange, int status, String message) throws IOException {
        byte[] payload = message.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, payload.length);

        try (OutputStream out = exchange.getResponseBody()) {
            out.write(payload);
        }
    }
}
