package com.movie_booking.service;

import com.movie_booking.dao.UserDao;
import com.movie_booking.dao.UserDaoImpl;
import com.movie_booking.exception.AuthenticationException;
import com.movie_booking.exception.ConflictException;
import com.movie_booking.exception.DataAccessException;
import com.movie_booking.exception.NotFoundException;
import com.movie_booking.exception.ValidationException;
import com.movie_booking.model.User;
import com.movie_booking.model.UserRole;
import com.movie_booking.model.UserStatus;
import com.movie_booking.util.PasswordUtil;
import java.sql.SQLException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Registration and sign-in.
 *
 * <p>Passwords never exist in the database in readable form: {@link PasswordUtil}
 * turns them into a salted PBKDF2 hash on the way in, and login re-derives the
 * hash to compare rather than ever decrypting anything. Nothing in this class can
 * tell you what a user's password is, which is the point.
 */
public class AuthService {

    /**
     * Pragmatic e-mail check. Full RFC 5322 validation by regular expression is a
     * famous mistake - the real grammar allows comments and quoted strings, and
     * every "complete" pattern rejects addresses that genuinely work. This only
     * catches typing errors; the real proof an address exists is that mail sent to
     * it arrives.
     */
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[A-Za-z]{2,}$");

    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_NAME_LENGTH = 100;

    private final UserDao userDao;

    public AuthService() {
        this(new UserDaoImpl());
    }

    public AuthService(UserDao userDao) {
        this.userDao = userDao;
    }

    // -----------------------------------------------------------------------
    // Registration
    // -----------------------------------------------------------------------

    /**
     * Creates a customer account.
     *
     * @return the stored user, without its password hash populated
     * @throws ValidationException bad name, e-mail or password
     * @throws ConflictException   the e-mail is already registered
     */
    public User register(String name, String email, String rawPassword, String phone) {
        String cleanName = requireText(name, "Name");
        String cleanEmail = normaliseEmail(email);
        validatePassword(rawPassword);

        if (cleanName.length() > MAX_NAME_LENGTH) {
            throw new ValidationException("Name must be at most "
                    + MAX_NAME_LENGTH + " characters.");
        }
        if (!EMAIL_PATTERN.matcher(cleanEmail).matches()) {
            throw new ValidationException("That does not look like a valid e-mail address.");
        }

        User user = new User();
        user.setName(cleanName);
        user.setEmail(cleanEmail);
        user.setPasswordHash(PasswordUtil.hash(rawPassword));
        user.setPhone(phone == null || phone.trim().isEmpty() ? null : phone.trim());
        user.setRole(UserRole.CUSTOMER);
        user.setStatus(UserStatus.ACTIVE);

        try {
            if (userDao.existsByEmail(cleanEmail)) {
                throw new ConflictException("An account with that e-mail already exists.");
            }

            int userId = userDao.createUser(user);
            user.setUserId(userId);
            user.setPasswordHash(null);
            return user;

        } catch (SQLException ex) {
            // Two people registering the same address at once both pass the check
            // above and one hits uq_users_email. The unique key is the real
            // guarantee; the check above only produces a nicer message when there
            // is no race.
            if (isDuplicateKey(ex)) {
                throw new ConflictException("An account with that e-mail already exists.");
            }
            throw new DataAccessException("Could not create the account.", ex);
        }
    }

    // -----------------------------------------------------------------------
    // Sign-in
    // -----------------------------------------------------------------------

    /**
     * Verifies credentials.
     *
     * <p>A wrong e-mail and a wrong password produce the identical error. Saying
     * "no such user" would let anyone probe which addresses hold accounts.
     *
     * @return the authenticated user, hash cleared
     * @throws AuthenticationException wrong credentials, or the account is disabled
     */
    public User login(String email, String rawPassword) {
        if (email == null || rawPassword == null) {
            throw new AuthenticationException("Invalid e-mail or password.");
        }

        User user;
        try {
            user = userDao.findByEmail(normaliseEmail(email));
        } catch (SQLException ex) {
            throw new DataAccessException("Could not sign you in.", ex);
        }

        if (user == null || !PasswordUtil.matches(rawPassword, user.getPasswordHash())) {
            throw new AuthenticationException("Invalid e-mail or password.");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AuthenticationException("This account has been deactivated.");
        }

        user.setPasswordHash(null);
        return user;
    }

    // -----------------------------------------------------------------------
    // Account management
    // -----------------------------------------------------------------------

    public User findById(int userId) {
        try {
            User user = userDao.findById(userId);
            if (user == null) {
                throw new NotFoundException("User not found.");
            }
            user.setPasswordHash(null);
            return user;
        } catch (SQLException ex) {
            throw new DataAccessException("Could not load the user.", ex);
        }
    }

    public void changePassword(int userId, String currentPassword, String newPassword) {
        validatePassword(newPassword);

        try {
            User user = userDao.findById(userId);
            if (user == null) {
                throw new NotFoundException("User not found.");
            }
            // Proving knowledge of the current password stops someone who walked
            // up to an unlocked browser from locking the real owner out.
            if (!PasswordUtil.matches(currentPassword, user.getPasswordHash())) {
                throw new AuthenticationException("Your current password is incorrect.");
            }
            userDao.updatePassword(userId, PasswordUtil.hash(newPassword));
        } catch (SQLException ex) {
            throw new DataAccessException("Could not change the password.", ex);
        }
    }

    /** Admin listing. */
    public List<User> findAll() {
        try {
            List<User> users = userDao.findAll();
            for (User user : users) {
                user.setPasswordHash(null);
            }
            return users;
        } catch (SQLException ex) {
            throw new DataAccessException("Could not load the user list.", ex);
        }
    }

    /**
     * Creates the first administrator if the system has none.
     *
     * <p>Called once at start-up. Without it there is no way in: the sign-up form
     * only ever creates customers, so a brand-new database would have nobody able
     * to add a film.
     *
     * @return {@code true} if an admin was created by this call
     */
    public boolean ensureAdminExists(String name, String email, String rawPassword) {
        try {
            if (!userDao.findAllByRole(UserRole.ADMIN).isEmpty()) {
                return false;
            }

            User admin = new User();
            admin.setName(name);
            admin.setEmail(normaliseEmail(email));
            admin.setPasswordHash(PasswordUtil.hash(rawPassword));
            admin.setRole(UserRole.ADMIN);
            admin.setStatus(UserStatus.ACTIVE);

            userDao.createUser(admin);
            return true;

        } catch (SQLException ex) {
            throw new DataAccessException("Could not create the default administrator.", ex);
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Lower-cased and trimmed, so {@code Bob@X.com} and {@code bob@x.com} are one account. */
    private String normaliseEmail(String email) {
        return requireText(email, "E-mail").toLowerCase();
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new ValidationException(fieldName + " is required.");
        }
        return value.trim();
    }

    private void validatePassword(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new ValidationException(
                    "Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        if (rawPassword.trim().isEmpty()) {
            throw new ValidationException("Password cannot be only whitespace.");
        }
    }

    /** MySQL error 1062 / SQLSTATE 23000 - a unique constraint was violated. */
    private boolean isDuplicateKey(SQLException ex) {
        return ex.getErrorCode() == 1062 || "23000".equals(ex.getSQLState());
    }
}
