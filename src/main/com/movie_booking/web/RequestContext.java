package com.movie_booking.web;

import com.movie_booking.exception.AuthenticationException;
import com.movie_booking.exception.AuthorizationException;
import com.movie_booking.exception.ValidationException;
import com.movie_booking.model.UserRole;
import com.movie_booking.util.Json;
import com.sun.net.httpserver.HttpExchange;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything one HTTP request needs, wrapped around the raw
 * {@link HttpExchange}.
 *
 * <p>The JDK's built-in server is deliberately bare - it hands you an input
 * stream and a header map and nothing else. This class supplies the missing
 * pieces once (query parsing, JSON body reading, cookies, the signed-in user,
 * response writing) so that no handler has to repeat them.
 */
public class RequestContext {

    /** Refuse oversized bodies rather than reading them into memory. */
    private static final int MAX_BODY_BYTES = 64 * 1024;

    private static final String SESSION_COOKIE = "MBSESSION";

    private final HttpExchange exchange;
    private final SessionStore sessionStore;
    private final Map<String, String> pathParams;
    private final Map<String, String> queryParams;

    private Map<String, Object> cachedBody;
    private SessionStore.Session cachedSession;
    private boolean sessionResolved;

    RequestContext(HttpExchange exchange, SessionStore sessionStore,
            Map<String, String> pathParams) {
        this.exchange = exchange;
        this.sessionStore = sessionStore;
        this.pathParams = pathParams;
        this.queryParams = parseQuery(exchange.getRequestURI().getRawQuery());
    }

    // -----------------------------------------------------------------------
    // Request inputs
    // -----------------------------------------------------------------------

    public String method() {
        return exchange.getRequestMethod();
    }

    public String path() {
        return exchange.getRequestURI().getPath();
    }

    /** A {@code {id}} segment from the route pattern. */
    public String pathParam(String name) {
        return pathParams.get(name);
    }

    public int pathParamInt(String name) {
        String raw = pathParam(name);
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            throw new ValidationException("'" + raw + "' is not a valid id.");
        }
    }

    public String queryParam(String name) {
        return queryParams.get(name);
    }

    public int queryParamInt(String name, int defaultValue) {
        String raw = queryParam(name);
        if (raw == null || raw.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            throw new ValidationException("'" + name + "' must be a number.");
        }
    }

    /** @return the {@code ?date=YYYY-MM-DD} parameter, or {@code defaultValue} */
    public LocalDate queryParamDate(String name, LocalDate defaultValue) {
        String raw = queryParam(name);
        if (raw == null || raw.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException ex) {
            throw new ValidationException("'" + name + "' must be a date in YYYY-MM-DD form.");
        }
    }

    /** The JSON request body as a map. Read once, then cached. */
    public Map<String, Object> body() {
        if (cachedBody == null) {
            cachedBody = Json.parseObject(readBodyText());
        }
        return cachedBody;
    }

    private String readBodyText() {
        try (InputStream in = exchange.getRequestBody()) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            int total = 0;

            while ((read = in.read(chunk)) != -1) {
                total += read;
                if (total > MAX_BODY_BYTES) {
                    throw new ValidationException("Request body is too large.");
                }
                buffer.write(chunk, 0, read);
            }
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);

        } catch (IOException ex) {
            throw new ValidationException("Could not read the request body.");
        }
    }

    // -----------------------------------------------------------------------
    // Authentication
    // -----------------------------------------------------------------------

    /** @return the signed-in user, or {@code null} if anonymous */
    public SessionStore.Session session() {
        if (!sessionResolved) {
            cachedSession = sessionStore.lookup(readSessionToken());
            sessionResolved = true;
        }
        return cachedSession;
    }

    /** @return the signed-in user id, or {@code 0} when anonymous */
    public int userIdOrAnonymous() {
        SessionStore.Session session = session();
        return session == null ? 0 : session.getUserId();
    }

    /** @throws AuthenticationException if nobody is signed in */
    public SessionStore.Session requireUser() {
        SessionStore.Session session = session();
        if (session == null) {
            throw new AuthenticationException("Please sign in to continue.");
        }
        return session;
    }

    /** @throws AuthorizationException if the signed-in user is not an admin */
    public SessionStore.Session requireAdmin() {
        SessionStore.Session session = requireUser();
        if (session.getRole() != UserRole.ADMIN) {
            throw new AuthorizationException("Administrator access is required.");
        }
        return session;
    }

    public String readSessionToken() {
        List<String> cookieHeaders = exchange.getRequestHeaders().get("Cookie");
        if (cookieHeaders == null) {
            return null;
        }

        for (String header : cookieHeaders) {
            for (String cookie : header.split(";")) {
                String trimmed = cookie.trim();
                if (trimmed.startsWith(SESSION_COOKIE + "=")) {
                    return trimmed.substring(SESSION_COOKIE.length() + 1);
                }
            }
        }
        return null;
    }

    /**
     * Sets the session cookie.
     *
     * <p>{@code HttpOnly} keeps it out of reach of JavaScript, so a cross-site
     * scripting bug cannot read the token and hand the session to an attacker.
     * {@code SameSite=Lax} means the browser will not attach it to requests
     * triggered by another site, which blocks cross-site request forgery for the
     * POST endpoints. {@code Secure} is omitted only because this demo runs over
     * plain HTTP on localhost; over HTTPS it must be added.
     */
    public void setSessionCookie(String token) {
        exchange.getResponseHeaders().add("Set-Cookie",
                SESSION_COOKIE + "=" + token + "; Path=/; HttpOnly; SameSite=Lax");
    }

    public void clearSessionCookie() {
        exchange.getResponseHeaders().add("Set-Cookie",
                SESSION_COOKIE + "=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0");
    }

    // -----------------------------------------------------------------------
    // Responses
    // -----------------------------------------------------------------------

    public void sendJson(int status, Object body) throws IOException {
        byte[] payload = Json.write(body).getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        // Booking data must never be served from a cache: a stale seat map would
        // show seats as free that were sold minutes ago.
        exchange.getResponseHeaders().set("Cache-Control", "no-store");

        exchange.sendResponseHeaders(status, payload.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(payload);
        }
    }

    public void sendOk(Object body) throws IOException {
        sendJson(200, body);
    }

    public void sendNoContent() throws IOException {
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static Map<String, String> parseQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> params = new HashMap<String, String>();
        for (String pair : rawQuery.split("&")) {
            int equals = pair.indexOf('=');
            if (equals < 0) {
                params.put(decode(pair), "");
            } else {
                params.put(decode(pair.substring(0, equals)), decode(pair.substring(equals + 1)));
            }
        }
        return params;
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (Exception ex) {
            return value;
        }
    }
}
