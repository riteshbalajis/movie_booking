package com.movie_booking.dao;

import com.movie_booking.model.User;
import com.movie_booking.model.UserRole;
import com.movie_booking.model.UserStatus;

import java.sql.SQLException;
import java.util.List;

public interface UserDao {
    int createUser(User user) throws SQLException;

    User findById(int userId) throws SQLException;

    User findByEmail(String email) throws SQLException;

    boolean existsByEmail(String email) throws SQLException;

    boolean updateUser(User user) throws SQLException;

    boolean updatePassword(int userId, String passwordHash) throws SQLException;

    boolean deactivateUser(int userId) throws SQLException;

    boolean activateUser(int userId) throws SQLException;

    List<User> findAll() throws SQLException;

    List<User> findAllByRole(UserRole role) throws SQLException;

    List<User> findAllByStatus(UserStatus status) throws SQLException;

    List<User> findByRoleAndStatus(UserRole role, UserStatus status) throws SQLException;
}
