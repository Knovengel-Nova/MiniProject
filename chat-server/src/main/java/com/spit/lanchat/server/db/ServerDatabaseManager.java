package com.spit.lanchat.server.db;

import com.spit.lanchat.common.dto.*;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * Server PostgreSQL Database Manager.
 * Handles users, groups, conversations, pending temporary messages, and delivery tracking.
 * Message contents are deleted from the server once all recipients acknowledge storage.
 */
public class ServerDatabaseManager {

    private static final Logger logger = LoggerFactory.getLogger(ServerDatabaseManager.class);

    private final String dbUrl;
    private final String dbUser;
    private final String dbPassword;

    public ServerDatabaseManager(String dbUrl, String dbUser, String dbPassword) {
        this.dbUrl = dbUrl;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl, dbUser, dbPassword);
    }

    // ========================================================================
    // User Accounts & Authentication
    // ========================================================================

    public static class UserRecord {
        public int userId;
        public String username;
        public String passwordHash;
        public String status;
        public Instant lastSeen;

        public UserRecord(int userId, String username, String passwordHash, String status, Instant lastSeen) {
            this.userId = userId;
            this.username = username;
            this.passwordHash = passwordHash;
            this.status = status;
            this.lastSeen = lastSeen;
        }
    }

    /**
     * Registers a new user with BCrypt hashed password.
     * Throws IllegalArgumentException on duplicate username or invalid input.
     */
    public int registerUser(String username, String plaintextPassword) throws SQLException {
        if (username == null || username.trim().isEmpty() || plaintextPassword == null || plaintextPassword.isEmpty()) {
            throw new IllegalArgumentException("Username and password cannot be empty");
        }
        username = username.trim().toLowerCase();

        String hash = BCrypt.hashpw(plaintextPassword, BCrypt.gensalt(10));

        String sql = "INSERT INTO users (username, password_hash, status, created_at, last_seen) " +
                     "VALUES (?, ?, 'offline', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) RETURNING user_id";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, hash);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    logger.info("Registered new user '{}' (ID: {})", username, id);
                    return id;
                }
            }
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) { // Unique violation
                throw new IllegalArgumentException("Username '" + username + "' is already registered");
            }
            throw e;
        }
        throw new SQLException("Failed to retrieve generated user ID");
    }

    /**
     * Authenticates a user using BCrypt.
     * Returns UserRecord on success, null on invalid credentials.
     */
    public UserRecord authenticateUser(String username, String plaintextPassword) throws SQLException {
        if (username == null || plaintextPassword == null) {
            return null;
        }
        username = username.trim().toLowerCase();

        String sql = "SELECT user_id, username, password_hash, status, last_seen FROM users WHERE username = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt("user_id");
                    String uName = rs.getString("username");
                    String hash = rs.getString("password_hash");
                    String status = rs.getString("status");
                    Timestamp ts = rs.getTimestamp("last_seen");
                    Instant lastSeen = ts != null ? ts.toInstant() : Instant.now();

                    if (BCrypt.checkpw(plaintextPassword, hash)) {
                        return new UserRecord(id, uName, hash, status, lastSeen);
                    }
                }
            }
        }
        return null;
    }

    public UserRecord findUserByUsername(String username) throws SQLException {
        if (username == null) return null;
        String sql = "SELECT user_id, username, password_hash, status, last_seen FROM users WHERE username = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username.trim().toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Timestamp ts = rs.getTimestamp("last_seen");
                    return new UserRecord(
                            rs.getInt("user_id"),
                            rs.getString("username"),
                            rs.getString("password_hash"),
                            rs.getString("status"),
                            ts != null ? ts.toInstant() : Instant.now()
                    );
                }
            }
        }
        return null;
    }

    public UserRecord findUserById(int userId) throws SQLException {
        String sql = "SELECT user_id, username, password_hash, status, last_seen FROM users WHERE user_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Timestamp ts = rs.getTimestamp("last_seen");
                    return new UserRecord(
                            rs.getInt("user_id"),
                            rs.getString("username"),
                            rs.getString("password_hash"),
                            rs.getString("status"),
                            ts != null ? ts.toInstant() : Instant.now()
                    );
                }
            }
        }
        return null;
    }

    public void updateUserStatus(int userId, String status) throws SQLException {
        String sql = "UPDATE users SET status = ?, last_seen = CURRENT_TIMESTAMP WHERE user_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    public List<UserDto> getAllUsers() throws SQLException {
        List<UserDto> list = new ArrayList<>();
        String sql = "SELECT user_id, username, status, last_seen FROM users ORDER BY username ASC";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Timestamp ts = rs.getTimestamp("last_seen");
                list.add(new UserDto(
                        rs.getInt("user_id"),
                        rs.getString("username"),
                        rs.getString("status"),
                        ts != null ? ts.toInstant() : Instant.now()
                ));
            }
        }
        return list;
    }

    // ========================================================================
    // Conversations (Direct 1-on-1 pairing)
    // ========================================================================

    public int getOrCreateConversation(int userA, int userB) throws SQLException {
        int u1 = Math.min(userA, userB);
        int u2 = Math.max(userA, userB);

        String findSql = "SELECT conversation_id FROM conversations WHERE user1_id = ? AND user2_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(findSql)) {
            ps.setInt(1, u1);
            ps.setInt(2, u2);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("conversation_id");
                }
            }
        }

        String insertSql = "INSERT INTO conversations (user1_id, user2_id, created_at) " +
                           "VALUES (?, ?, CURRENT_TIMESTAMP) ON CONFLICT (user1_id, user2_id) " +
                           "DO UPDATE SET user1_id = EXCLUDED.user1_id RETURNING conversation_id";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setInt(1, u1);
            ps.setInt(2, u2);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("conversation_id");
                }
            }
        }
        throw new SQLException("Could not establish conversation between users " + u1 + " and " + u2);
    }

    // ========================================================================
    // Groups & Memberships
    // ========================================================================

    public void createGroup(String groupId, String groupName, int creatorUserId, List<String> memberUsernames) throws SQLException {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                String insertGroupSql = "INSERT INTO groups (group_id, group_name, created_by, created_at) " +
                                        "VALUES (?, ?, ?, CURRENT_TIMESTAMP)";
                try (PreparedStatement ps = conn.prepareStatement(insertGroupSql)) {
                    ps.setString(1, groupId);
                    ps.setString(2, groupName);
                    ps.setInt(3, creatorUserId);
                    ps.executeUpdate();
                }

                // Add creator as member
                String insertMemberSql = "INSERT INTO group_members (group_id, user_id, joined_at) " +
                                         "VALUES (?, ?, CURRENT_TIMESTAMP) ON CONFLICT DO NOTHING";
                try (PreparedStatement ps = conn.prepareStatement(insertMemberSql)) {
                    ps.setString(1, groupId);
                    ps.setInt(2, creatorUserId);
                    ps.executeUpdate();
                }

                // Add initial member usernames
                if (memberUsernames != null) {
                    for (String uname : memberUsernames) {
                        UserRecord u = findUserByUsername(uname);
                        if (u != null) {
                            try (PreparedStatement ps = conn.prepareStatement(insertMemberSql)) {
                                ps.setString(1, groupId);
                                ps.setInt(2, u.userId);
                                ps.executeUpdate();
                            }
                        }
                    }
                }

                conn.commit();
                logger.info("Created group '{}' (ID: {}) with creator ID {}", groupName, groupId, creatorUserId);
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public GroupDto getGroup(String groupId) throws SQLException {
        String sql = "SELECT g.group_id, g.group_name, u.username AS creator_name, g.created_at " +
                     "FROM groups g JOIN users u ON g.created_by = u.user_id WHERE g.group_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, groupId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String name = rs.getString("group_name");
                    String creator = rs.getString("creator_name");
                    Timestamp ts = rs.getTimestamp("created_at");
                    List<String> members = getGroupMemberUsernames(groupId);
                    return new GroupDto(groupId, name, creator, members, ts != null ? ts.toInstant() : Instant.now());
                }
            }
        }
        return null;
    }

    public List<GroupDto> getGroupsForUser(int userId) throws SQLException {
        List<GroupDto> list = new ArrayList<>();
        String sql = "SELECT g.group_id, g.group_name, u.username AS creator_name, g.created_at " +
                     "FROM groups g " +
                     "JOIN users u ON g.created_by = u.user_id " +
                     "JOIN group_members gm ON g.group_id = gm.group_id " +
                     "WHERE gm.user_id = ? ORDER BY g.created_at ASC";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String gId = rs.getString("group_id");
                    String gName = rs.getString("group_name");
                    String creator = rs.getString("creator_name");
                    Timestamp ts = rs.getTimestamp("created_at");
                    List<String> members = getGroupMemberUsernames(gId);
                    list.add(new GroupDto(gId, gName, creator, members, ts != null ? ts.toInstant() : Instant.now()));
                }
            }
        }
        return list;
    }

    public List<String> getGroupMemberUsernames(String groupId) throws SQLException {
        List<String> list = new ArrayList<>();
        String sql = "SELECT u.username FROM group_members gm " +
                     "JOIN users u ON gm.user_id = u.user_id " +
                     "WHERE gm.group_id = ? ORDER BY u.username ASC";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, groupId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(rs.getString("username"));
                }
            }
        }
        return list;
    }

    public List<Integer> getGroupMemberIds(String groupId) throws SQLException {
        List<Integer> list = new ArrayList<>();
        String sql = "SELECT user_id FROM group_members WHERE group_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, groupId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(rs.getInt("user_id"));
                }
            }
        }
        return list;
    }

    public boolean isGroupMember(String groupId, int userId) throws SQLException {
        String sql = "SELECT 1 FROM group_members WHERE group_id = ? AND user_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, groupId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public void addGroupMember(String groupId, int userId) throws SQLException {
        String sql = "INSERT INTO group_members (group_id, user_id, joined_at) VALUES (?, ?, CURRENT_TIMESTAMP) " +
                     "ON CONFLICT DO NOTHING";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, groupId);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    public void removeGroupMember(String groupId, int userId) throws SQLException {
        String sql = "DELETE FROM group_members WHERE group_id = ? AND user_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, groupId);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    // ========================================================================
    // Temporary Pending Queue & Delivery Tracking (Privacy & Content Deletion)
    // ========================================================================

    public void storePendingDirectMessage(String messageId, int senderId, String senderUsername,
                                          int recipientId, String recipientUsername, String text,
                                          PdsPredictionResult pds) throws SQLException {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                int convId = getOrCreateConversation(senderId, recipientId);

                String msgSql = "INSERT INTO pending_messages (message_id, message_type, conversation_id, " +
                                "sender_id, sender_username, message_text, predicted_risk_score, flagged, " +
                                "model_version, model_status, content_deleted, created_at) " +
                                "VALUES (?, 'DIRECT', ?, ?, ?, ?, ?, ?, ?, ?, FALSE, CURRENT_TIMESTAMP)";
                try (PreparedStatement ps = conn.prepareStatement(msgSql)) {
                    ps.setString(1, messageId);
                    ps.setInt(2, convId);
                    ps.setInt(3, senderId);
                    ps.setString(4, senderUsername);
                    ps.setString(5, text);
                    if (pds != null && pds.getPredictedRiskScore() != null) {
                        ps.setDouble(6, pds.getPredictedRiskScore());
                    } else {
                        ps.setNull(6, Types.DOUBLE);
                    }
                    ps.setBoolean(7, pds != null && pds.isFlagged());
                    ps.setString(8, pds != null ? pds.getModelVersion() : "none");
                    ps.setString(9, pds != null ? pds.getStatus() : "unavailable");
                    ps.executeUpdate();
                }

                String delSql = "INSERT INTO message_deliveries (message_id, recipient_id, recipient_username, status) " +
                                "VALUES (?, ?, ?, 'PENDING') ON CONFLICT DO NOTHING";
                try (PreparedStatement ps = conn.prepareStatement(delSql)) {
                    ps.setString(1, messageId);
                    ps.setInt(2, recipientId);
                    ps.setString(3, recipientUsername);
                    ps.executeUpdate();
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public void storePendingGroupMessage(String messageId, int senderId, String senderUsername,
                                         String groupId, String text, PdsPredictionResult pds) throws SQLException {
        List<Integer> memberIds = getGroupMemberIds(groupId);

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                String msgSql = "INSERT INTO pending_messages (message_id, message_type, group_id, " +
                                "sender_id, sender_username, message_text, predicted_risk_score, flagged, " +
                                "model_version, model_status, content_deleted, created_at) " +
                                "VALUES (?, 'GROUP', ?, ?, ?, ?, ?, ?, ?, ?, FALSE, CURRENT_TIMESTAMP)";
                try (PreparedStatement ps = conn.prepareStatement(msgSql)) {
                    ps.setString(1, messageId);
                    ps.setString(2, groupId);
                    ps.setInt(3, senderId);
                    ps.setString(4, senderUsername);
                    ps.setString(5, text);
                    if (pds != null && pds.getPredictedRiskScore() != null) {
                        ps.setDouble(6, pds.getPredictedRiskScore());
                    } else {
                        ps.setNull(6, Types.DOUBLE);
                    }
                    ps.setBoolean(7, pds != null && pds.isFlagged());
                    ps.setString(8, pds != null ? pds.getModelVersion() : "none");
                    ps.setString(9, pds != null ? pds.getStatus() : "unavailable");
                    ps.executeUpdate();
                }

                String delSql = "INSERT INTO message_deliveries (message_id, recipient_id, recipient_username, status) " +
                                "VALUES (?, ?, ?, 'PENDING') ON CONFLICT DO NOTHING";
                for (int memberId : memberIds) {
                    if (memberId == senderId) {
                        continue; // Do not create pending delivery for sender
                    }
                    UserRecord rec = findUserById(memberId);
                    if (rec != null) {
                        try (PreparedStatement ps = conn.prepareStatement(delSql)) {
                            ps.setString(1, messageId);
                            ps.setInt(2, memberId);
                            ps.setString(3, rec.username);
                            ps.executeUpdate();
                        }
                    }
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public void recordDeliveryPushed(String messageId, int recipientId) throws SQLException {
        String sql = "UPDATE message_deliveries SET status = 'DELIVERED', delivered_at = CURRENT_TIMESTAMP " +
                     "WHERE message_id = ? AND recipient_id = ? AND status = 'PENDING'";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, messageId);
            ps.setInt(2, recipientId);
            ps.executeUpdate();
        }
    }

    /**
     * Marks recipient delivery as ACKNOWLEDGED.
     * If ALL required deliveries for this message have reached ACKNOWLEDGED:
     * Wipes server-side message content (sets message_text = NULL, content_deleted = TRUE).
     * Returns true if server content was deleted as a result of this acknowledgement.
     */
    public boolean recordDeliveryAck(String messageId, int recipientId) throws SQLException {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                String updateDeliverySql = "UPDATE message_deliveries SET status = 'ACKNOWLEDGED', " +
                                           "acknowledged_at = CURRENT_TIMESTAMP " +
                                           "WHERE message_id = ? AND recipient_id = ?";
                try (PreparedStatement ps = conn.prepareStatement(updateDeliverySql)) {
                    ps.setString(1, messageId);
                    ps.setInt(2, recipientId);
                    ps.executeUpdate();
                }

                // Check remaining unacknowledged recipients
                String checkSql = "SELECT COUNT(*) FROM message_deliveries " +
                                  "WHERE message_id = ? AND status != 'ACKNOWLEDGED'";
                int remaining = 0;
                try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                    ps.setString(1, messageId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            remaining = rs.getInt(1);
                        }
                    }
                }

                boolean deleted = false;
                if (remaining == 0) {
                    // Safe Deletion Condition Met: All intended recipients have acknowledged local storage!
                    String deleteContentSql = "UPDATE pending_messages " +
                                              "SET message_text = NULL, content_deleted = TRUE, deleted_at = CURRENT_TIMESTAMP " +
                                              "WHERE message_id = ?";
                    try (PreparedStatement ps = conn.prepareStatement(deleteContentSql)) {
                        ps.setString(1, messageId);
                        int affected = ps.executeUpdate();
                        if (affected > 0) {
                            deleted = true;
                            logger.info("PRIVACY POLICY: Server message content deleted for messageId '{}' (all recipients ACKed)", messageId);
                        }
                    }
                }

                conn.commit();
                return deleted;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public PendingSyncResponse getPendingDeliveriesForUser(int recipientId, String recipientUsername) throws SQLException {
        List<DirectMessagePush> directMessages = new ArrayList<>();
        List<GroupMessagePush> groupMessages = new ArrayList<>();

        String sql = "SELECT m.message_id, m.message_type, m.group_id, g.group_name, " +
                     "m.sender_username, m.message_text, m.predicted_risk_score, m.flagged, " +
                     "m.model_status, m.created_at " +
                     "FROM message_deliveries d " +
                     "JOIN pending_messages m ON d.message_id = m.message_id " +
                     "LEFT JOIN groups g ON m.group_id = g.group_id " +
                     "WHERE d.recipient_id = ? AND d.status != 'ACKNOWLEDGED' AND m.content_deleted = FALSE " +
                     "ORDER BY m.created_at ASC";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, recipientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String msgId = rs.getString("message_id");
                    String type = rs.getString("message_type");
                    String sender = rs.getString("sender_username");
                    String text = rs.getString("message_text");
                    Double score = (Double) rs.getObject("predicted_risk_score");
                    boolean flagged = rs.getBoolean("flagged");
                    String modelStatus = rs.getString("model_status");
                    Timestamp ts = rs.getTimestamp("created_at");
                    Instant instant = ts != null ? ts.toInstant() : Instant.now();

                    if ("DIRECT".equalsIgnoreCase(type)) {
                        directMessages.add(new DirectMessagePush(
                                msgId, sender, recipientUsername, text, instant, score, flagged, modelStatus
                        ));
                    } else {
                        String gId = rs.getString("group_id");
                        String gName = rs.getString("group_name");
                        groupMessages.add(new GroupMessagePush(
                                msgId, gId, gName != null ? gName : gId, sender, text, instant, score, flagged, modelStatus
                        ));
                    }
                }
            }
        }

        return new PendingSyncResponse(directMessages, groupMessages);
    }

    public boolean isMessageContentDeleted(String messageId) throws SQLException {
        String sql = "SELECT content_deleted, message_text FROM pending_messages WHERE message_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, messageId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBoolean("content_deleted") && rs.getString("message_text") == null;
                }
            }
        }
        return false;
    }

    public void recordMetric(String metricName, double value, String details) {
        String sql = "INSERT INTO operational_metrics (metric_name, metric_value, details, recorded_at) " +
                     "VALUES (?, ?, ?, CURRENT_TIMESTAMP)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, metricName);
            ps.setDouble(2, value);
            ps.setString(3, details);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warn("Failed to record metric '{}': {}", metricName, e.getMessage());
        }
    }
}
