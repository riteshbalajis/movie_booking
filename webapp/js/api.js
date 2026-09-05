/* ===========================================================================
   api.js - the only place that talks to the server.

   Every page uses these helpers rather than calling fetch() directly, so that
   error handling, JSON encoding and cookie behaviour are defined once.
   =========================================================================== */

const Api = (() => {

    /**
     * One request.
     *
     * `credentials: 'same-origin'` is what carries the session cookie. The
     * cookie is HttpOnly, so this script cannot read the token - it only asks
     * the browser to send it, which is exactly the point: an XSS bug on this
     * page still cannot steal the session.
     *
     * A non-2xx response is turned into a thrown Error carrying the server's
     * message, so callers can use one try/catch instead of checking res.ok
     * everywhere.
     */
    async function request(method, path, body) {
        const options = {
            method,
            credentials: 'same-origin',
            headers: {}
        };

        if (body !== undefined) {
            options.headers['Content-Type'] = 'application/json';
            options.body = JSON.stringify(body);
        }

        let response;
        try {
            response = await fetch(path, options);
        } catch (networkFailure) {
            throw new Error('Could not reach the server. Is it still running?');
        }

        if (response.status === 204) {
            return null;
        }

        let payload = null;
        try {
            payload = await response.json();
        } catch (notJson) {
            // A crash can produce an HTML error page; do not let the JSON
            // parse failure mask the real status code.
            if (!response.ok) {
                throw new Error('Request failed (HTTP ' + response.status + ').');
            }
            return null;
        }

        if (!response.ok) {
            const error = new Error(payload.message || 'Request failed.');
            error.code = payload.error;
            error.status = response.status;
            // Carried by SEATS_UNAVAILABLE so the page can highlight them.
            error.unavailableSeats = payload.unavailableSeats;
            throw error;
        }

        return payload;
    }

    return {
        get:  (path)       => request('GET', path),
        post: (path, body) => request('POST', path, body),
        del:  (path)       => request('DELETE', path),

        /* --- Endpoints, named so pages never hard-code URLs --------------- */

        me:           ()                 => request('GET', '/api/auth/me'),
        login:        (email, password)  => request('POST', '/api/auth/login', { email, password }),
        register:     (data)             => request('POST', '/api/auth/register', data),
        logout:       ()                 => request('POST', '/api/auth/logout', {}),

        movies:       (query)            => request('GET', '/api/movies' + (query ? '?q=' + encodeURIComponent(query) : '')),
        movie:        (id)               => request('GET', '/api/movies/' + id),
        showsFor:     (movieId, date)    => request('GET', '/api/movies/' + movieId + '/shows?date=' + date),
        seatMap:      (showId)           => request('GET', '/api/shows/' + showId + '/seats'),

        holdSeats:    (showId, seatIds)  => request('POST', '/api/bookings/hold', { showId, showSeatIds: seatIds }),
        confirm:      (bookingId)        => request('POST', '/api/bookings/' + bookingId + '/confirm', {}),
        cancel:       (bookingId)        => request('POST', '/api/bookings/' + bookingId + '/cancel', {}),
        myBookings:   ()                 => request('GET', '/api/bookings'),

        adminMovies:  ()                 => request('GET', '/api/admin/movies'),
        adminShows:   (date)             => request('GET', '/api/admin/shows?date=' + date),
        theatres:     ()                 => request('GET', '/api/theatres'),
        screensOf:    (theatreId)        => request('GET', '/api/theatres/' + theatreId + '/screens'),
        createMovie:  (data)             => request('POST', '/api/admin/movies', data),
        createTheatre:(data)             => request('POST', '/api/admin/theatres', data),
        createScreen: (data)             => request('POST', '/api/admin/screens', data),
        createShow:   (data)             => request('POST', '/api/admin/shows', data),
        cancelShow:   (showId)           => request('POST', '/api/admin/shows/' + showId + '/cancel', {}),
        adminUsers:   ()                 => request('GET', '/api/admin/users')
    };
})();
