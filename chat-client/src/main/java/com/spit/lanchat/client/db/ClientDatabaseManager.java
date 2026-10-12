package com.spit.lanchat.client.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Client-local PostgreSQL database manager.
 * Stores durable conversation history and received messages locally.
 * Enforces transaction commitment before durable acknowledgements are emitted.
 */
public class ClientDatabaseManager {

    private static final Logger logger = LoggerFactory.getLogger(ClientDatabaseManager.class);

    private final String dbUrl;
    private final String dbUser;
    private final String dbPassword;

    public ClientDatabaseManager(String dbUrl, String dbUser, String dbPassword) {
        this.dbUrl = dbUrl;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl, dbUser, dbPassword);
    }

    /**
     * Initializes client-local tables if they do not exist.
     */
    public void initSchema() throws SQLException {
        String createMessagesTable =
                "CREATE TABLE IF NOT EXISTS local_messages (" +
                "    message_id VARCHAR(64) PRIMARY KEY," +
                "    message_type VARCHAR(20) NOT NULL," +
                "    conversation_partner VARCHAR(64) NOT NULL," +
                "    group_name VARCHAR(128)," +
                "    sender_username VARCHAR(64) NOT NULL," +
                "    is_outgoing BOOLEAN NOT NULL DEFAULT FALSE," +
                "    message_text TEXT NOT NULL," +
                "    predicted_risk_score DOUBLE PRECISION," +
                "    flagged BOOLEAN DEFAULT FALSE," +
                "    model_status VARCHAR(32) DEFAULT 'ok'," +
                "    delivery_status VARCHAR(20) DEFAULT 'STORED'," +
                "    timestamp TIMESTAMP WITH TIME ZONE NOT NULL," +
                "    stored_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP" +
                ");" +
                "CREATE INDEX IF NOT EXISTS idx_local_messages_partner ON local_messages(conversation_partner, timestamp);";

        String createConversationsTable =
                "CREATE TABLE IF NOT EXISTS local_conversations (" +
                "    conversation_key VARCHAR(128) PRIMARY KEY," +
                "    conversation_type VARCHAR(20) NOT NULL," +
                "    target_id VARCHAR(64) NOT NULL," +
                "    target_name VARCHAR(128) NOT NULL," +
                "    last_message_preview TEXT," +
                "    last_message_at TIMESTAMP WITH TIME ZONE," +
                "    unread_count INT DEFAULT 0" +
                ");";

        String createSyncTable =
                "CREATE TABLE IF NOT EXISTS local_sync_state (" +
                "    state_key VARCHAR(64) PRIMARY KEY," +
                "    state_value TEXT," +
                "    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP" +
                ");";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createMessagesTable);
            stmt.execute(createConversationsTable);
            stmt.execute(createSyncTable);
            logger.info("Client local PostgreSQL database schema verified at {}", dbUrl);
        }
    }

    /**
     * Durably stores a message into the local PostgreSQL database using an atomic transaction.
     * Uses message UUID primary key for deduplication.
     * Returns true if newly inserted, false if already existed (duplicate).
     */
    public boolean saveMessage(LocalMessage msg) throws SQLException {
        if (msg == null || msg.getMessageId() == null) {
            return false;
        }

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                String insertMsgSql =
                        "INSERT INTO local_messages (" +
                        "    message_id, message_type, conversation_partner, group_name, " +
                        "    sender_username, is_outgoing, message_text, predicted_risk_score, " +
                        "    flagged, model_status, delivery_status, timestamp, stored_at" +
                        ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP) " +
                        "ON CONFLICT (message_id) DO NOTHING";

                int affected;
                try (PreparedStatement ps = conn.prepareStatement(insertMsgSql)) {
                    ps.setString(1, msg.getMessageId());
                    ps.setString(2, msg.getMessageType());
                    ps.setString(3, msg.getConversationPartner());
                    ps.setString(4, msg.getGroupName());
                    ps.setString(5, msg.getSenderUsername());
                    ps.setBoolean(6, msg.isOutgoing());
                    ps.setString(7, msg.getMessageText());
                    if (msg.getPredictedRiskScore() != null) {
                        ps.setDouble(8, msg.getPredictedRiskScore());
                    } else {
                        ps.setNull(8, Types.DOUBLE);
                    }
                    ps.setBoolean(9, msg.isFlagged());
                    ps.setString(10, msg.getModelStatus());
                    ps.setString(11, msg.getDeliveryStatus());
                    ps.setTimestamp(12, Timestamp.from(msg.getTimestamp()));
                    affected = ps.executeUpdate();
                }

                // If freshly inserted, update conversation container
                if (affected > 0) {
                    String convKey = msg.getMessageType() + ":" + msg.getConversationPartner();
                    String preview = msg.getMessageText().length() > 60
                            ? msg.getMessageText().substring(0, 57) + "..."
                            : msg.getMessageText();

                    String updateConvSql =
                            "INSERT INTO local_conversations (" +
                            "    conversation_key, conversation_type, target_id, target_name, " +
                            "    last_message_preview, last_message_at, unread_count" +
                            ") VALUES (?, ?, ?, ?, ?, ?, ?) " +
                            "ON CONFLICT (conversation_key) DO UPDATE SET " +
                            "    last_message_preview = EXCLUDED.last_message_preview, " +
                            "    last_message_at = EXCLUDED.last_message_at, " +
                            "    unread_count = local_conversations.unread_count + EXCLUDED.unread_count";

                    try (PreparedStatement ps = conn.prepareStatement(updateConvSql)) {
                        ps.setString(1, convKey);
                        ps.setString(2, msg.getMessageType());
                        ps.setString(3, msg.getConversationPartner());
                        String targetName = msg.getGroupName() != null && !msg.getGroupName().isEmpty()
                                ? msg.getGroupName()
                                : msg.getConversationPartner();
                        ps.setString(4, targetName);
                        ps.setString(5, preview);
                        ps.setTimestamp(6, Timestamp.from(msg.getTimestamp()));
                        ps.setInt(7, msg.isOutgoing() ? 0 : 1);
                        ps.executeUpdate();
                    }
                }

                conn.commit(); // Transaction commits durably
                return affected > 0;

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /**
     * Retrieves all messages for a given conversation partner (user or group) in chronological order.
     */
    public List<LocalMessage> getConversationHistory(String conversationPartner) throws SQLException {
        List<LocalMessage> messages = new ArrayList<>();
        if (conversationPartner == null) return messages;

        String sql = "SELECT message_id, message_type, conversation_partner, group_name, " +
                     "       sender_username, is_outgoing, message_text, predicted_risk_score, " +
                     "       flagged, model_status, delivery_status, timestamp, stored_at " +
                     "FROM local_messages WHERE conversation_partner = ? ORDER BY timestamp ASC";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, conversationPartner);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp ts = rs.getTimestamp("timestamp");
                    Timestamp storedTs = rs.getTimestamp("stored_at");
                    Double score = (Double) rs.getObject("predicted_risk_score");

                    messages.add(new LocalMessage(
                            rs.getString("message_id"),
                            rs.getString("message_type"),
                            rs.getString("conversation_partner"),
                            rs.getString("group_name"),
                            rs.getString("sender_username"),
                            rs.getBoolean("is_outgoing"),
                            rs.getString("message_text"),
                            score,
                            rs.getBoolean("flagged"),
                            rs.getString("model_status"),
                            rs.getString("delivery_status"),
                            ts != null ? ts.toInstant() : Instant.now(),
                            storedTs != null ? storedTs.toInstant() : Instant.now()
                    ));
                }
            }
        }
        return messages;
    }

    public void updateDeliveryStatus(String messageId, String deliveryStatus) throws SQLException {
        String sql = "UPDATE local_messages SET delivery_status = ? WHERE message_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, deliveryStatus);
            ps.setString(2, messageId);
            ps.executeUpdate();
        }
    }

    public boolean hasMessage(String messageId) throws SQLException {
        String sql = "SELECT 1 FROM local_messages WHERE message_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, messageId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
