package com.spit.lanchat.server.db;

import com.spit.lanchat.common.dto.GroupDto;
import com.spit.lanchat.common.dto.PdsPredictionResult;
import com.spit.lanchat.common.dto.PendingSyncResponse;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ServerDatabaseTest {

    private static ServerDatabaseManager db;
    private static final String DB_URL = "jdbc:postgresql://127.0.0.1:5432/lan_chat_server";
    private static final String DB_USER = "postgres";
    private static final String DB_PASS = "postgres";

    @BeforeAll
    public static void setup() throws Exception {
        db = new ServerDatabaseManager(DB_URL, DB_USER, DB_PASS);

        // Reset tables cleanly before testing
        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE TABLE message_deliveries, pending_messages, group_members, groups, conversations, users RESTART IDENTITY CASCADE;");
        }
    }

    @Test
    @Order(1)
    public void testUserRegistrationAndDuplicateRejection() throws SQLException {
        int id1 = db.registerUser("alice", "Password123!");
        assertTrue(id1 > 0);

        int id2 = db.registerUser("bob", "Secret456!");
        assertTrue(id2 > 0);

        int id3 = db.registerUser("charlie", "MyPass789!");
        assertTrue(id3 > 0);

        // Duplicate username must throw IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> {
            db.registerUser("alice", "AnotherPassword");
        });
    }

    @Test
    @Order(2)
    public void testAuthenticationAndStatusUpdate() throws SQLException {
        // Correct credentials
        var user = db.authenticateUser("alice", "Password123!");
        assertNotNull(user);
        assertEquals("alice", user.username);

        // Incorrect password
        var failed = db.authenticateUser("alice", "WrongPassword");
        assertNull(failed);

        // Non-existent user
        var nonExistent = db.authenticateUser("nobody", "Password");
        assertNull(nonExistent);

        // Status update
        db.updateUserStatus(user.userId, "online");
        var updated = db.findUserByUsername("alice");
        assertEquals("online", updated.status);
    }

    @Test
    @Order(3)
    public void testGroupCreationAndMembership() throws SQLException {
        var alice = db.findUserByUsername("alice");
        var bob = db.findUserByUsername("bob");

        String groupId = "grp-" + UUID.randomUUID().toString().substring(0, 8);
        db.createGroup(groupId, "Project Alpha", alice.userId, List.of("bob"));

        GroupDto group = db.getGroup(groupId);
        assertNotNull(group);
        assertEquals("Project Alpha", group.getGroupName());
        assertEquals("alice", group.getCreatedBy());
        assertTrue(group.getMembers().contains("alice"));
        assertTrue(group.getMembers().contains("bob"));

        // Add Charlie
        var charlie = db.findUserByUsername("charlie");
        db.addGroupMember(groupId, charlie.userId);
        assertTrue(db.isGroupMember(groupId, charlie.userId));

        // Remove Charlie
        db.removeGroupMember(groupId, charlie.userId);
        assertFalse(db.isGroupMember(groupId, charlie.userId));
    }

    @Test
    @Order(4)
    public void testDirectMessagePendingQueueAndContentDeletion() throws SQLException {
        var alice = db.findUserByUsername("alice");
        var bob = db.findUserByUsername("bob");

        String msgId = UUID.randomUUID().toString();
        PdsPredictionResult pds = new PdsPredictionResult("req-1", msgId, 0.15, false, 0.70, "v1", "ok", null);

        // Alice sends direct message to Bob
        db.storePendingDirectMessage(msgId, alice.userId, alice.username, bob.userId, bob.username, "Hi Bob, are you free?", pds);

        // Message should be pending on server with content intact
        assertFalse(db.isMessageContentDeleted(msgId));

        // Mark pushed
        db.recordDeliveryPushed(msgId, bob.userId);

        // Bob durably stores locally and sends ACK
        boolean deleted = db.recordDeliveryAck(msgId, bob.userId);
        assertTrue(deleted, "Server message content must be deleted after recipient ACKs storage");

        // Verify message content in server database is now wiped (nullified)
        assertTrue(db.isMessageContentDeleted(msgId));
    }

    @Test
    @Order(5)
    public void testGroupMessageMultiRecipientAckAndDelayedContentDeletion() throws SQLException {
        var alice = db.findUserByUsername("alice");
        var bob = db.findUserByUsername("bob");
        var charlie = db.findUserByUsername("charlie");

        String groupId = "grp-" + UUID.randomUUID().toString().substring(0, 8);
        db.createGroup(groupId, "Trio Team", alice.userId, List.of("bob", "charlie"));

        String msgId = UUID.randomUUID().toString();
        PdsPredictionResult pds = new PdsPredictionResult("req-2", msgId, 0.85, true, 0.70, "v1", "ok", null);

        // Alice broadcasts to group (Bob & Charlie are recipients)
        db.storePendingGroupMessage(msgId, alice.userId, alice.username, groupId, "URGENT meeting now!", pds);
        assertFalse(db.isMessageContentDeleted(msgId));

        // Bob ACKs storage
        boolean deletedAfterBob = db.recordDeliveryAck(msgId, bob.userId);
        assertFalse(deletedAfterBob, "Group message content must NOT be deleted while Charlie hasn't ACKed yet");
        assertFalse(db.isMessageContentDeleted(msgId));

        // Charlie finally comes online and ACKs
        boolean deletedAfterCharlie = db.recordDeliveryAck(msgId, charlie.userId);
        assertTrue(deletedAfterCharlie, "Group message content must be deleted once ALL recipients have ACKed");
        assertTrue(db.isMessageContentDeleted(msgId));
    }

    @Test
    @Order(6)
    public void testOfflineMessageQueueingAndSync() throws SQLException {
        var alice = db.findUserByUsername("alice");
        var bob = db.findUserByUsername("bob");

        String offlineMsgId = UUID.randomUUID().toString();
        PdsPredictionResult pds = new PdsPredictionResult("req-3", offlineMsgId, 0.05, false, 0.70, "v1", "ok", null);

        // Alice sends message while Bob is offline
        db.storePendingDirectMessage(offlineMsgId, alice.userId, alice.username, bob.userId, bob.username, "Message while you were away", pds);

        // Bob syncs pending messages
        PendingSyncResponse pending = db.getPendingDeliveriesForUser(bob.userId, bob.username);
        assertNotNull(pending);
        boolean found = pending.getDirectMessages().stream().anyMatch(m -> m.getMessageId().equals(offlineMsgId));
        assertTrue(found, "Pending sync must return messages awaiting delivery for offline user");

        // Bob ACKs
        db.recordDeliveryAck(offlineMsgId, bob.userId);
        assertTrue(db.isMessageContentDeleted(offlineMsgId));
    }
}
