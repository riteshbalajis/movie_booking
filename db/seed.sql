-- ============================================================================
--  Movie Booking System - Demo data
--
--  Run AFTER schema.sql:   mysql -u root -p movie_booking < db/seed.sql
--
--  Users are NOT seeded here on purpose: passwords must be hashed with PBKDF2
--  by the application. The app creates a default admin on first start
--  (see AppBootstrap) -- admin@movie.com / Admin@123
--
--  All shows are scheduled relative to CURDATE(), so the demo data never
--  goes stale.
-- ============================================================================

USE movie_booking;

-- ---------------------------------------------------------------------------
-- Theatres
-- ---------------------------------------------------------------------------
INSERT INTO theatres (name, location) VALUES
    ('PVR Grand Mall',      'Chennai'),
    ('INOX City Centre',    'Chennai'),
    ('Cinepolis Riverside', 'Bengaluru');

-- ---------------------------------------------------------------------------
-- Screens
-- ---------------------------------------------------------------------------
INSERT INTO screens (theatre_id, name, capacity) VALUES
    (1, 'Screen 1', 96),
    (1, 'Screen 2', 96),
    (2, 'Audi A',   96),
    (3, 'IMAX',     96);

-- ---------------------------------------------------------------------------
-- Seats  (8 rows A..H x 12 seats = 96 per screen)
--
--   Rows A-C -> REGULAR   (front)
--   Rows D-F -> PREMIUM   (middle)
--   Rows G-H -> RECLINER  (back)
--
-- Generated with a recursive CTE cross-joined against the screen list, so the
-- layout stays identical for every screen without 400 hand-written INSERTs.
-- ---------------------------------------------------------------------------
INSERT INTO seats (screen_id, row_label, seat_number, seat_type)
WITH RECURSIVE numbers (n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM numbers WHERE n < 12
),
rows_def (row_label, seat_type) AS (
    SELECT 'A', 'REGULAR'  UNION ALL
    SELECT 'B', 'REGULAR'  UNION ALL
    SELECT 'C', 'REGULAR'  UNION ALL
    SELECT 'D', 'PREMIUM'  UNION ALL
    SELECT 'E', 'PREMIUM'  UNION ALL
    SELECT 'F', 'PREMIUM'  UNION ALL
    SELECT 'G', 'RECLINER' UNION ALL
    SELECT 'H', 'RECLINER'
)
SELECT s.screen_id, r.row_label, n.n, r.seat_type
FROM screens s
CROSS JOIN rows_def r
CROSS JOIN numbers n
ORDER BY s.screen_id, r.row_label, n.n;

-- ---------------------------------------------------------------------------
-- Movies
-- ---------------------------------------------------------------------------
INSERT INTO movies (title, description, duration_minutes, language, genre, release_date, status) VALUES
    ('Interstellar',
     'A team of explorers travel through a wormhole in space in an attempt to ensure humanity''s survival.',
     169, 'English', 'Sci-Fi', '2014-11-07', 'ACTIVE'),
    ('Vikram',
     'A special agent investigates a murder case that unravels into a war against a drug syndicate.',
     174, 'Tamil', 'Action', '2022-06-03', 'ACTIVE'),
    ('Jawan',
     'A man is driven by a personal vendetta to rectify the wrongs in society.',
     169, 'Hindi', 'Action', '2023-09-07', 'ACTIVE'),
    ('Inception',
     'A thief who steals corporate secrets through dream-sharing technology is given the inverse task.',
     148, 'English', 'Thriller', '2010-07-16', 'ACTIVE'),
    ('Dune: Part Three',
     'The saga continues on Arrakis.',
     165, 'English', 'Sci-Fi', '2026-12-18', 'UPCOMING');

-- ---------------------------------------------------------------------------
-- Shows  (today + next 2 days, 3 slots a day)
-- ---------------------------------------------------------------------------
INSERT INTO shows (movie_id, screen_id, show_date, start_time, end_time) VALUES
    -- today
    (1, 1, CURDATE(),                  '10:00:00', '12:49:00'),
    (2, 1, CURDATE(),                  '14:00:00', '16:54:00'),
    (3, 2, CURDATE(),                  '18:30:00', '21:19:00'),
    (4, 3, CURDATE(),                  '19:00:00', '21:28:00'),
    -- tomorrow
    (1, 1, CURDATE() + INTERVAL 1 DAY, '10:00:00', '12:49:00'),
    (2, 2, CURDATE() + INTERVAL 1 DAY, '13:15:00', '16:09:00'),
    (3, 3, CURDATE() + INTERVAL 1 DAY, '17:45:00', '20:34:00'),
    (4, 4, CURDATE() + INTERVAL 1 DAY, '21:00:00', '23:28:00'),
    -- day after
    (1, 4, CURDATE() + INTERVAL 2 DAY, '11:00:00', '13:49:00'),
    (2, 3, CURDATE() + INTERVAL 2 DAY, '15:30:00', '18:24:00'),
    (3, 1, CURDATE() + INTERVAL 2 DAY, '20:00:00', '22:49:00');

-- ---------------------------------------------------------------------------
-- show_seats  (materialise every ACTIVE seat of the screen for every show,
--              pricing it by seat type)
--
-- This mirrors exactly what ShowSeatDao.createShowSeatsForShow() does at
-- runtime when an admin creates a show.
-- ---------------------------------------------------------------------------
INSERT INTO show_seats (show_id, seat_id, status, price)
SELECT sh.show_id,
       se.seat_id,
       'AVAILABLE',
       CASE se.seat_type
           WHEN 'REGULAR'  THEN 150.00
           WHEN 'PREMIUM'  THEN 250.00
           WHEN 'RECLINER' THEN 400.00
       END
FROM shows sh
JOIN seats se ON se.screen_id = sh.screen_id AND se.status = 'ACTIVE'
ORDER BY sh.show_id, se.seat_id;

-- ---------------------------------------------------------------------------
-- Sanity report
-- ---------------------------------------------------------------------------
SELECT 'theatres'   AS table_name, COUNT(*) AS rows_inserted FROM theatres
UNION ALL SELECT 'screens',    COUNT(*) FROM screens
UNION ALL SELECT 'seats',      COUNT(*) FROM seats
UNION ALL SELECT 'movies',     COUNT(*) FROM movies
UNION ALL SELECT 'shows',      COUNT(*) FROM shows
UNION ALL SELECT 'show_seats', COUNT(*) FROM show_seats;
