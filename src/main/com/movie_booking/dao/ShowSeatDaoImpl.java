package com.movie_booking.dao;

import com.movie_booking.dto.SeatMapEntry;
import com.movie_booking.model.SeatType;
import com.movie_booking.model.ShowSeat;
import com.movie_booking.model.ShowSeatStatus;
import com.movie_booking.util.DBConnection;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JDBC implementation of {@link ShowSeatDao}.
 *
 * <p>Every statement here is a {@link PreparedStatement}. Seat ids arrive from
 * the browser, and building {@code IN (...)} by pasting them into a string would
 * be a textbook SQL-injection hole; instead {@link #placeholders(int)} generates
 * the right number of {@code ?} markers and the values are bound.
 */
public class ShowSeatDaoImpl implements ShowSeatDao {

    private static final String BASE_SELECT =
            "SELECT show_seat_id, show_id, seat_id, status, price, "
            + "locked_by_user_id, locked_until FROM show_seats";

    /**
     * A seat is sellable when it is AVAILABLE, or LOCKED by a hold that has
     * already lapsed. Reused by every availability check so the definition of
     * "free" cannot drift between queries.
     */
    private static final String SELLABLE_PREDICATE =
            "(status = 'AVAILABLE' OR (status = 'LOCKED' AND locked_until < ?))";

    // -----------------------------------------------------------------------
    // Creation
    // -----------------------------------------------------------------------

    @Override
    public int createShowSeatsForShow(Connection connection, int showId, int screenId,
            BigDecimal regularPrice, BigDecimal premiumPrice, BigDecimal reclinerPrice)
            throws SQLException {

        String sql = "INSERT INTO show_seats (show_id, seat_id, status, price) "
                + "SELECT ?, seat_id, 'AVAILABLE', "
                + "CASE seat_type WHEN 'REGULAR' THEN ? WHEN 'PREMIUM' THEN ? "
                + "WHEN 'RECLINER' THEN ? END "
                + "FROM seats WHERE screen_id = ? AND status = 'ACTIVE'";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, showId);
            statement.setBigDecimal(2, regularPrice);
            statement.setBigDecimal(3, premiumPrice);
            statement.setBigDecimal(4, reclinerPrice);
            statement.setInt(5, screenId);
            return statement.executeUpdate();
        }
    }

    // -----------------------------------------------------------------------
    // Reads
    // -----------------------------------------------------------------------

    @Override
    public ShowSeat findById(int showSeatId) throws SQLException {
        String sql = BASE_SELECT + " WHERE show_seat_id = ?";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, showSeatId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? mapShowSeat(resultSet) : null;
            }
        }
    }

    @Override
    public List<ShowSeat> findByShowId(int showId) throws SQLException {
        String sql = BASE_SELECT + " WHERE show_id = ? ORDER BY show_seat_id";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, showId);
            return readShowSeats(statement);
        }
    }

    @Override
    public List<SeatMapEntry> findSeatMap(int showId, int viewerUserId) throws SQLException {
        // One join gives the whole grid. Ordering by row then number means the UI
        // can render straight down the list without sorting client-side.
        String sql = "SELECT ss.show_seat_id, ss.seat_id, ss.status, ss.price, "
                + "ss.locked_by_user_id, ss.locked_until, "
                + "s.row_label, s.seat_number, s.seat_type "
                + "FROM show_seats ss "
                + "JOIN seats s ON s.seat_id = ss.seat_id "
                + "WHERE ss.show_id = ? "
                + "ORDER BY s.row_label, s.seat_number";

        LocalDateTime now = LocalDateTime.now();
        List<SeatMapEntry> entries = new ArrayList<SeatMapEntry>();

        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, showId);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    entries.add(new SeatMapEntry(
                            resultSet.getInt("show_seat_id"),
                            resultSet.getInt("seat_id"),
                            resultSet.getString("row_label"),
                            resultSet.getInt("seat_number"),
                            SeatType.valueOf(resultSet.getString("seat_type")),
                            resultSet.getBigDecimal("price"),
                            resolveDisplayStatus(resultSet, viewerUserId, now)));
                }
            }
        }
        return entries;
    }

    /**
     * Translates the raw row state into what this particular viewer should see.
     * Doing it on the server keeps the rule in one place - the browser is never
     * trusted to decide whether a hold has expired.
     */
    private String resolveDisplayStatus(ResultSet resultSet, int viewerUserId, LocalDateTime now)
            throws SQLException {

        ShowSeatStatus status = ShowSeatStatus.valueOf(resultSet.getString("status"));
        if (status == ShowSeatStatus.BOOKED) {
            return SeatMapEntry.BOOKED;
        }
        if (status == ShowSeatStatus.AVAILABLE) {
            return SeatMapEntry.AVAILABLE;
        }

        Timestamp lockedUntil = resultSet.getTimestamp("locked_until");
        if (lockedUntil == null || lockedUntil.toLocalDateTime().isBefore(now)) {
            return SeatMapEntry.AVAILABLE;
        }

        int lockedBy = resultSet.getInt("locked_by_user_id");
        boolean lockedByViewer = !resultSet.wasNull()
                && viewerUserId != 0
                && lockedBy == viewerUserId;

        return lockedByViewer ? SeatMapEntry.MINE : SeatMapEntry.HELD_BY_OTHER;
    }

    @Override
    public int countSellableByShowId(int showId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM show_seats "
                + "WHERE show_id = ? AND " + SELLABLE_PREDICATE;

        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, showId);
            statement.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        }
    }

    @Override
    public BigDecimal findMinPriceByShowId(int showId) throws SQLException {
        String sql = "SELECT MIN(price) FROM show_seats WHERE show_id = ?";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, showId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getBigDecimal(1) : null;
            }
        }
    }

    @Override
    public boolean existsByShowAndSeat(int showId, int seatId) throws SQLException {
        String sql = "SELECT 1 FROM show_seats WHERE show_id = ? AND seat_id = ? LIMIT 1";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, showId);
            statement.setInt(2, seatId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    @Override
    public Map<Integer, String> findSeatLabels(Connection connection, List<Integer> showSeatIds)
            throws SQLException {

        if (showSeatIds.isEmpty()) {
            return Collections.emptyMap();
        }

        String sql = "SELECT ss.show_seat_id, s.row_label, s.seat_number "
                + "FROM show_seats ss "
                + "JOIN seats s ON s.seat_id = ss.seat_id "
                + "WHERE ss.show_seat_id IN (" + placeholders(showSeatIds.size()) + ")";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindInts(statement, 1, showSeatIds);

            try (ResultSet resultSet = statement.executeQuery()) {
                Map<Integer, String> labels = new LinkedHashMap<Integer, String>();
                while (resultSet.next()) {
                    labels.put(Integer.valueOf(resultSet.getInt("show_seat_id")),
                            resultSet.getString("row_label") + resultSet.getInt("seat_number"));
                }
                return labels;
            }
        }
    }

    // -----------------------------------------------------------------------
    // Mutations
    // -----------------------------------------------------------------------

    @Override
    public List<ShowSeat> lockForUpdate(Connection connection, List<Integer> showSeatIds)
            throws SQLException {

        if (showSeatIds.isEmpty()) {
            return Collections.emptyList();
        }

        // Sorting is the deadlock guard. Every transaction in the system acquires
        // seat locks in ascending id order, so two overlapping requests can never
        // hold the locks the other is waiting for.
        List<Integer> ordered = new ArrayList<Integer>(showSeatIds);
        Collections.sort(ordered);

        String sql = BASE_SELECT
                + " WHERE show_seat_id IN (" + placeholders(ordered.size()) + ")"
                + " ORDER BY show_seat_id"
                + " FOR UPDATE";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindInts(statement, 1, ordered);
            return readShowSeats(statement);
        }
    }

    @Override
    public int hold(Connection connection, List<Integer> showSeatIds, int userId,
            LocalDateTime lockedUntil) throws SQLException {

        if (showSeatIds.isEmpty()) {
            return 0;
        }

        String sql = "UPDATE show_seats "
                + "SET status = 'LOCKED', locked_by_user_id = ?, locked_until = ? "
                + "WHERE show_seat_id IN (" + placeholders(showSeatIds.size()) + ") "
                + "AND " + SELLABLE_PREDICATE;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            statement.setInt(index++, userId);
            statement.setTimestamp(index++, Timestamp.valueOf(lockedUntil));
            index = bindInts(statement, index, showSeatIds);
            statement.setTimestamp(index, Timestamp.valueOf(LocalDateTime.now()));
            return statement.executeUpdate();
        }
    }

    @Override
    public int markBooked(Connection connection, List<Integer> showSeatIds, int userId)
            throws SQLException {

        if (showSeatIds.isEmpty()) {
            return 0;
        }

        // The status/owner/deadline conditions together are a compare-and-set:
        // the seat is only sold if it is still THIS user's live hold. If the hold
        // lapsed and someone else took the seat, zero rows change and the caller
        // rolls the whole confirmation back.
        String sql = "UPDATE show_seats "
                + "SET status = 'BOOKED', locked_by_user_id = NULL, locked_until = NULL "
                + "WHERE show_seat_id IN (" + placeholders(showSeatIds.size()) + ") "
                + "AND status = 'LOCKED' AND locked_by_user_id = ? AND locked_until >= ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = bindInts(statement, 1, showSeatIds);
            statement.setInt(index++, userId);
            statement.setTimestamp(index, Timestamp.valueOf(LocalDateTime.now()));
            return statement.executeUpdate();
        }
    }

    @Override
    public int release(Connection connection, List<Integer> showSeatIds) throws SQLException {
        if (showSeatIds.isEmpty()) {
            return 0;
        }

        String sql = "UPDATE show_seats "
                + "SET status = 'AVAILABLE', locked_by_user_id = NULL, locked_until = NULL "
                + "WHERE show_seat_id IN (" + placeholders(showSeatIds.size()) + ")";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindInts(statement, 1, showSeatIds);
            return statement.executeUpdate();
        }
    }

    @Override
    public int releaseExpiredHolds(Connection connection) throws SQLException {
        String sql = "UPDATE show_seats "
                + "SET status = 'AVAILABLE', locked_by_user_id = NULL, locked_until = NULL "
                + "WHERE status = 'LOCKED' AND locked_until < ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            return statement.executeUpdate();
        }
    }

    @Override
    public boolean updatePrice(int showSeatId, BigDecimal price) throws SQLException {
        String sql = "UPDATE show_seats SET price = ? WHERE show_seat_id = ?";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBigDecimal(1, price);
            statement.setInt(2, showSeatId);
            return statement.executeUpdate() > 0;
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** @return {@code "?, ?, ?"} for a count of 3. */
    private static String placeholders(int count) {
        StringBuilder builder = new StringBuilder(count * 3);
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append('?');
        }
        return builder.toString();
    }

    /** Binds a list of ints from {@code startIndex}; @return the next free index. */
    private static int bindInts(PreparedStatement statement, int startIndex, List<Integer> values)
            throws SQLException {
        int index = startIndex;
        for (Integer value : values) {
            statement.setInt(index++, value.intValue());
        }
        return index;
    }

    private static List<ShowSeat> readShowSeats(PreparedStatement statement) throws SQLException {
        List<ShowSeat> showSeats = new ArrayList<ShowSeat>();
        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                showSeats.add(mapShowSeat(resultSet));
            }
        }
        return showSeats;
    }

    private static ShowSeat mapShowSeat(ResultSet resultSet) throws SQLException {
        ShowSeat showSeat = new ShowSeat();
        showSeat.setShowSeatId(resultSet.getInt("show_seat_id"));
        showSeat.setShowId(resultSet.getInt("show_id"));
        showSeat.setSeatId(resultSet.getInt("seat_id"));
        showSeat.setStatus(ShowSeatStatus.valueOf(resultSet.getString("status")));
        showSeat.setPrice(resultSet.getBigDecimal("price"));

        int lockedBy = resultSet.getInt("locked_by_user_id");
        showSeat.setLockedByUserId(resultSet.wasNull() ? null : Integer.valueOf(lockedBy));

        Timestamp lockedUntil = resultSet.getTimestamp("locked_until");
        showSeat.setLockedUntil(lockedUntil == null ? null : lockedUntil.toLocalDateTime());

        return showSeat;
    }
}
