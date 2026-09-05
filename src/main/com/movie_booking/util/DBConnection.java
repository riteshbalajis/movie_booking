package com.movie_booking.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class DBConnection {
    private static final String URL = "jdbc:mysql://localhost:3306/movie_booking"
            + "?useSSL=false&serverTimezone=UTC";
    private static final String USER = getEnvironmentValue("MOVIE_DB_USER", "root");
    private static final String PASSWORD = getEnvironmentValue("MOVIE_DB_PASSWORD", "0307");

    private DBConnection() {
        // Utility class.
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    private static String getEnvironmentValue(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null ? defaultValue : value;
    }
}
