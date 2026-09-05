package com.movie_booking.web;

import com.movie_booking.exception.AppException;
import com.movie_booking.exception.SeatUnavailableException;
import com.movie_booking.util.Json;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Matches a request to a handler and turns any exception into a JSON response.
 *
 * <p>Routes are registered as {@code GET /api/shows/{id}/seats}. A {@code {name}}
 * segment matches one path segment and its value is exposed through
 * {@link RequestContext#pathParam}.
 *
 * <p><b>Error handling lives here, once.</b> Because every domain exception
 * carries its own status and code ({@link AppException}), handlers can simply
 * throw and this converts the result into a proper response. Without that, each
 * of the thirty-odd endpoints would need its own try/catch, and one forgotten
 * catch would leak a Java stack trace to the browser.
 */
public class Router implements HttpHandler {

    /** What a route does. Throwing is the normal way to report a failure. */
    public interface Handler {
        void handle(RequestContext context) throws Exception;
    }

    private final List<Route> routes = new ArrayList<Route>();
    private final SessionStore sessionStore;

    public Router(SessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    public Router get(String pattern, Handler handler) {
        return register("GET", pattern, handler);
    }

    public Router post(String pattern, Handler handler) {
        return register("POST", pattern, handler);
    }

    public Router put(String pattern, Handler handler) {
        return register("PUT", pattern, handler);
    }

    public Router delete(String pattern, Handler handler) {
        return register("DELETE", pattern, handler);
    }

    private Router register(String method, String pattern, Handler handler) {
        routes.add(new Route(method, pattern, handler));
        return this;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        try {
            // A browser sends OPTIONS before some requests; answer it cheaply
            // rather than letting it fall through to a 404.
            if ("OPTIONS".equals(method)) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }

            boolean pathExists = false;

            for (Route route : routes) {
                Map<String, String> pathParams = route.match(path);
                if (pathParams == null) {
                    continue;
                }
                pathExists = true;

                if (!route.method.equals(method)) {
                    continue;
                }

                route.handler.handle(new RequestContext(exchange, sessionStore, pathParams));
                return;
            }

            // Distinguishing these two matters: 405 tells a client the resource is
            // real but the verb is wrong, which is a very different fix from 404.
            if (pathExists) {
                sendError(exchange, 405, "METHOD_NOT_ALLOWED",
                        method + " is not supported for this endpoint.", null);
            } else {
                sendError(exchange, 404, "NOT_FOUND", "No such endpoint: " + path, null);
            }

        } catch (AppException ex) {
            // An expected, well-described failure - report it as designed.
            Map<String, Object> extra = null;
            if (ex instanceof SeatUnavailableException) {
                extra = Json.of("unavailableSeats",
                        ((SeatUnavailableException) ex).getUnavailableSeats());
            }
            sendError(exchange, ex.getHttpStatus(), ex.getCode(), ex.getMessage(), extra);

        } catch (Json.JsonException ex) {
            sendError(exchange, 400, "BAD_REQUEST", ex.getMessage(), null);

        } catch (Exception ex) {
            // Anything unplanned: log the detail server-side, tell the client
            // nothing. Stack traces and SQL text leak table names and versions.
            System.err.println("[http] Unhandled failure on " + method + " " + path);
            ex.printStackTrace();
            sendError(exchange, 500, "INTERNAL_ERROR",
                    "Something went wrong on our side. Please try again.", null);
        }
    }

    private void sendError(HttpExchange exchange, int status, String code, String message,
            Map<String, Object> extra) throws IOException {

        Map<String, Object> body = Json.object();
        body.put("error", code);
        body.put("message", message);
        if (extra != null) {
            body.putAll(extra);
        }

        byte[] payload = Json.write(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, payload.length);

        try (OutputStream out = exchange.getResponseBody()) {
            out.write(payload);
        }
    }

    /** One registered route and its compiled pattern. */
    private static final class Route {

        private final String method;
        private final String[] segments;
        private final Handler handler;

        private Route(String method, String pattern, Handler handler) {
            this.method = method;
            this.segments = split(pattern);
            this.handler = handler;
        }

        /**
         * @return the captured {@code {name}} values, or {@code null} if the path
         *         does not match this route at all
         */
        private Map<String, String> match(String path) {
            String[] parts = split(path);
            if (parts.length != segments.length) {
                return null;
            }

            Map<String, String> params = new HashMap<String, String>();
            for (int i = 0; i < segments.length; i++) {
                String segment = segments[i];

                if (segment.length() > 2
                        && segment.charAt(0) == '{'
                        && segment.charAt(segment.length() - 1) == '}') {
                    params.put(segment.substring(1, segment.length() - 1), parts[i]);
                } else if (!segment.equals(parts[i])) {
                    return null;
                }
            }
            return params;
        }

        private static String[] split(String path) {
            String trimmed = path;
            while (trimmed.startsWith("/")) {
                trimmed = trimmed.substring(1);
            }
            while (trimmed.endsWith("/")) {
                trimmed = trimmed.substring(0, trimmed.length() - 1);
            }
            return trimmed.isEmpty() ? new String[0] : trimmed.split("/");
        }
    }
}
