-- ============================================================================
--  Movie Booking System - Schema
--  MySQL 8.0 / InnoDB
--
--  Run with:  mysql -u root -p < db/schema.sql
-- ============================================================================

DROP DATABASE IF EXISTS movie_booking;
CREATE DATABASE movie_booking
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;
USE movie_booking;

-- ---------------------------------------------------------------------------
-- users
-- ---------------------------------------------------------------------------
CREATE TABLE users (
    user_id       INT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(100)  NOT NULL,
    email         VARCHAR(150)  NOT NULL,
    password_hash VARCHAR(255)  NOT NULL,
    phone         VARCHAR(20),
    role          ENUM('CUSTOMER','ADMIN')  NOT NULL DEFAULT 'CUSTOMER',
    status        ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_users_email UNIQUE (email)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------------
-- theatres
-- ---------------------------------------------------------------------------
CREATE TABLE theatres (
    theatre_id INT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(120) NOT NULL,
    location   VARCHAR(150) NOT NULL,
    status     ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_theatres_name UNIQUE (name),
    INDEX idx_theatres_location (location)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------------
-- screens  (an auditorium inside a theatre)
-- ---------------------------------------------------------------------------
CREATE TABLE screens (
    screen_id  INT AUTO_INCREMENT PRIMARY KEY,
    theatre_id INT         NOT NULL,
    name       VARCHAR(60) NOT NULL,
    capacity   INT         NOT NULL DEFAULT 0,
    status     ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT fk_screens_theatre FOREIGN KEY (theatre_id)
        REFERENCES theatres (theatre_id) ON DELETE CASCADE,
    CONSTRAINT uq_screens_theatre_name UNIQUE (theatre_id, name)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------------
-- seats  (the PHYSICAL chair - defined once per screen, never per show)
-- ---------------------------------------------------------------------------
CREATE TABLE seats (
    seat_id     INT AUTO_INCREMENT PRIMARY KEY,
    screen_id   INT         NOT NULL,
    row_label   VARCHAR(4)  NOT NULL,
    seat_number INT         NOT NULL,
    seat_type   ENUM('REGULAR','PREMIUM','RECLINER') NOT NULL DEFAULT 'REGULAR',
    status      ENUM('ACTIVE','INACTIVE')            NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT fk_seats_screen FOREIGN KEY (screen_id)
        REFERENCES screens (screen_id) ON DELETE CASCADE,
    CONSTRAINT uq_seats_position UNIQUE (screen_id, row_label, seat_number)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------------
-- movies
-- ---------------------------------------------------------------------------
CREATE TABLE movies (
    movie_id         INT AUTO_INCREMENT PRIMARY KEY,
    title            VARCHAR(200) NOT NULL,
    description      TEXT,
    duration_minutes INT          NOT NULL,
    language         VARCHAR(50),
    genre            VARCHAR(80),
    release_date     DATE,
    status           ENUM('UPCOMING','ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_movies_title UNIQUE (title),
    INDEX idx_movies_status (status),
    INDEX idx_movies_language (language),
    INDEX idx_movies_genre (genre)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------------
-- shows  (one screening of one movie on one screen)
-- ---------------------------------------------------------------------------
CREATE TABLE shows (
    show_id    INT AUTO_INCREMENT PRIMARY KEY,
    movie_id   INT  NOT NULL,
    screen_id  INT  NOT NULL,
    show_date  DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time   TIME NOT NULL,
    status     ENUM('SCHEDULED','ONGOING','COMPLETED','CANCELLED')
               NOT NULL DEFAULT 'SCHEDULED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_shows_movie  FOREIGN KEY (movie_id)  REFERENCES movies  (movie_id),
    CONSTRAINT fk_shows_screen FOREIGN KEY (screen_id) REFERENCES screens (screen_id),
    -- two shows can never start at the same instant on the same screen
    CONSTRAINT uq_shows_screen_slot UNIQUE (screen_id, show_date, start_time),
    INDEX idx_shows_date (show_date),
    INDEX idx_shows_movie_date (movie_id, show_date)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------------
-- show_seats  (the physical seat AS SOLD FOR ONE SHOW - carries price + state)
--
--  This is the row every concurrent booking competes for. Its status column is
--  the single source of truth for availability and is protected by
--  SELECT ... FOR UPDATE row locks (see BookingService).
--
--  AVAILABLE --> LOCKED (temporary hold, expires) --> BOOKED (paid, permanent)
--                   |
--                   +--> AVAILABLE  (hold expired, or user cancelled)
-- ---------------------------------------------------------------------------
CREATE TABLE show_seats (
    show_seat_id      INT AUTO_INCREMENT PRIMARY KEY,
    show_id           INT            NOT NULL,
    seat_id           INT            NOT NULL,
    status            ENUM('AVAILABLE','LOCKED','BOOKED') NOT NULL DEFAULT 'AVAILABLE',
    price             DECIMAL(10,2)  NOT NULL,
    locked_by_user_id INT            NULL,
    locked_until      DATETIME       NULL,
    CONSTRAINT fk_show_seats_show FOREIGN KEY (show_id)
        REFERENCES shows (show_id) ON DELETE CASCADE,
    CONSTRAINT fk_show_seats_seat FOREIGN KEY (seat_id)
        REFERENCES seats (seat_id),
    CONSTRAINT fk_show_seats_locker FOREIGN KEY (locked_by_user_id)
        REFERENCES users (user_id),
    -- A physical seat may appear at most ONCE per show. This unique key is the
    -- last line of defence against double-selling if application logic fails.
    CONSTRAINT uq_show_seats_show_seat UNIQUE (show_id, seat_id),
    INDEX idx_show_seats_show_status (show_id, status),
    INDEX idx_show_seats_expiry (status, locked_until)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------------
-- bookings
-- ---------------------------------------------------------------------------
CREATE TABLE bookings (
    booking_id   INT AUTO_INCREMENT PRIMARY KEY,
    booking_ref  VARCHAR(20)   NOT NULL,
    user_id      INT           NOT NULL,
    show_id      INT           NOT NULL,
    total_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    status       ENUM('PENDING','CONFIRMED','CANCELLED','EXPIRED','COMPLETED')
                 NOT NULL DEFAULT 'PENDING',
    booked_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- while PENDING, the moment the seat hold lapses
    expires_at   DATETIME      NULL,
    CONSTRAINT fk_bookings_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_bookings_show FOREIGN KEY (show_id) REFERENCES shows (show_id),
    CONSTRAINT uq_bookings_ref UNIQUE (booking_ref),
    INDEX idx_bookings_user (user_id, booked_at),
    INDEX idx_bookings_show (show_id),
    INDEX idx_bookings_pending (status, expires_at)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------------
-- booking_seats  (line items: which show_seats this booking holds)
-- ---------------------------------------------------------------------------
CREATE TABLE booking_seats (
    booking_seat_id INT AUTO_INCREMENT PRIMARY KEY,
    booking_id      INT           NOT NULL,
    show_seat_id    INT           NOT NULL,
    price           DECIMAL(10,2) NOT NULL,
    CONSTRAINT fk_booking_seats_booking FOREIGN KEY (booking_id)
        REFERENCES bookings (booking_id) ON DELETE CASCADE,
    CONSTRAINT fk_booking_seats_show_seat FOREIGN KEY (show_seat_id)
        REFERENCES show_seats (show_seat_id),
    CONSTRAINT uq_booking_seats UNIQUE (booking_id, show_seat_id),
    INDEX idx_booking_seats_show_seat (show_seat_id)
) ENGINE=InnoDB;
