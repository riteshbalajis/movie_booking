/* ===========================================================================
   app.js - helpers shared by every page: the header, formatting, messages.
   =========================================================================== */

const App = (() => {

    /* --- Escaping --------------------------------------------------------
       Every value that comes from the server is written into the page through
       this. A film title or a user's name is data someone typed, and pasting it
       into innerHTML unescaped is how a stored cross-site-scripting bug gets in:
       a title of `<img src=x onerror=...>` would otherwise execute. Assigning
       through textContent lets the browser do the escaping correctly. */
    function escapeHtml(value) {
        if (value === null || value === undefined) {
            return '';
        }
        const holder = document.createElement('div');
        holder.textContent = String(value);
        return holder.innerHTML;
    }

    /* --- Formatting ------------------------------------------------------ */

    function money(amount) {
        const number = Number(amount || 0);
        return '₹' + number.toLocaleString('en-IN', {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        });
    }

    /** "18:30" -> "6:30 PM" */
    function time(value) {
        if (!value) {
            return '';
        }
        const [hourText, minute] = value.split(':');
        let hour = parseInt(hourText, 10);
        const suffix = hour >= 12 ? 'PM' : 'AM';
        hour = hour % 12 || 12;
        return hour + ':' + minute + ' ' + suffix;
    }

    /** "2026-09-06" -> "Sun, 6 Sep" */
    function dateLabel(isoDate) {
        const parsed = new Date(isoDate + 'T00:00:00');
        return parsed.toLocaleDateString('en-GB', {
            weekday: 'short', day: 'numeric', month: 'short'
        });
    }

    function isoToday(offsetDays) {
        const date = new Date();
        date.setDate(date.getDate() + (offsetDays || 0));
        // Built from local parts, not toISOString(), which would convert to UTC
        // and hand back yesterday for anyone east of Greenwich in the evening.
        const month = String(date.getMonth() + 1).padStart(2, '0');
        const day = String(date.getDate()).padStart(2, '0');
        return date.getFullYear() + '-' + month + '-' + day;
    }

    function minutesToRuntime(minutes) {
        const hours = Math.floor(minutes / 60);
        const rest = minutes % 60;
        return hours > 0 ? hours + 'h ' + rest + 'm' : rest + 'm';
    }

    /* --- Messages -------------------------------------------------------- */

    function showMessage(elementId, text, kind) {
        const element = document.getElementById(elementId);
        if (!element) {
            return;
        }
        element.className = 'message ' + (kind || 'info');
        element.textContent = text;
    }

    function clearMessage(elementId) {
        const element = document.getElementById(elementId);
        if (element) {
            element.textContent = '';
            element.className = 'message';
        }
    }

    /* --- Query string ---------------------------------------------------- */

    function param(name) {
        return new URLSearchParams(window.location.search).get(name);
    }

    /* --- Header ---------------------------------------------------------- */

    /**
     * Draws the navigation for whoever is signed in.
     *
     * <p>This is convenience only, never security. Hiding the admin link does
     * not protect anything - the server checks the role on every admin endpoint,
     * because anyone can type the URL by hand.
     */
    async function renderHeader(activePage) {
        const header = document.getElementById('site-header');
        if (!header) {
            return null;
        }

        let user = null;
        try {
            const response = await Api.me();
            user = response.user;
        } catch (ignored) {
            // Signed out, or the server is down; either way, show public links.
        }

        const link = (href, label) =>
            '<a href="' + href + '"' +
            (activePage === label.toLowerCase() ? ' class="active"' : '') +
            '>' + label + '</a>';

        let nav = link('/index.html', 'Movies');

        if (user) {
            nav += link('/bookings.html', 'My Bookings');
            if (user.isAdmin) {
                nav += link('/admin.html', 'Admin');
            }
            nav += '<span class="muted small">' + escapeHtml(user.name) + '</span>';
            nav += '<a href="#" id="logout-link">Sign out</a>';
        } else {
            nav += link('/login.html', 'Sign in');
            nav += '<a class="btn btn-sm" href="/register.html">Sign up</a>';
        }

        header.innerHTML =
            '<a class="brand" href="/index.html">Cine<span>Book</span></a>' +
            '<nav class="site-nav">' + nav + '</nav>';

        const logout = document.getElementById('logout-link');
        if (logout) {
            logout.addEventListener('click', async (event) => {
                event.preventDefault();
                await Api.logout();
                window.location.href = '/index.html';
            });
        }

        return user;
    }

    /** Sends anonymous visitors to the sign-in page, remembering where they were. */
    function requireSignIn(user) {
        if (!user) {
            const target = window.location.pathname + window.location.search;
            window.location.href = '/login.html?next=' + encodeURIComponent(target);
            return false;
        }
        return true;
    }

    /* --- Deterministic poster colour ------------------------------------- */

    /**
     * Turns a title into a stable hue, so each film keeps the same colour on
     * every page and across reloads without storing anything.
     */
    function posterStyle(title) {
        let hash = 0;
        for (let i = 0; i < title.length; i++) {
            hash = (hash * 31 + title.charCodeAt(i)) & 0xffffff;
        }
        const hue = hash % 360;
        return 'background: linear-gradient(150deg, hsl(' + hue + ',26%,26%), hsl(' +
               ((hue + 40) % 360) + ',24%,14%));';
    }

    return {
        escapeHtml, money, time, dateLabel, isoToday, minutesToRuntime,
        showMessage, clearMessage, param, renderHeader, requireSignIn, posterStyle
    };
})();
