package com.spit.lanchat.client.db;

import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ClientDatabaseTest {

    private static ClientDatabaseManager db;
    private static final String DB_URL = "jdbc:postgresql://127.0.0.1:5432/lan_chat_client_test";
    private static final String DB_USER = "postgres";
    private static final String DB_PASS = "postgres";

    @BeforeAll
    public static void setup() throws Exception {
        db = new ClientDatabaseManager(DB_URL, DB_USER, DB_PASS);
        db.initSchema();

        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE TABLE local_messages, local_conversations, local_sync_state;");
        }
    }

    @Test
    @Order(1)
    public void testSaveMessageAndDeduplication() throws Exception {
        String msgId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        LocalMessage msg = new LocalMessage(
                msgId,
                "DIRECT",
                "bob",
                null,
                "alice",
                false,
                "Hello from Alice!",
                0.12,
                false,
                "ok",
                "STORED",
                now,
                now
        );

        // First save must succeed
        boolean inserted1 = db.saveMessage(msg);
        assertTrue(inserted1, "First save of unique message UUID must succeed");
        assertTrue(db.hasMessage(msgId));

        // Second save of exact same UUID (simulated retry or crash recovery)
        boolean inserted2 = db.saveMessage(msg);
        assertFalse(inserted2, "Second save of identical message UUID must be deduplicated");

        // History count for bob must remain 1
        List<LocalMessage> history = db.getConversationHistory("bob");
        assertEquals(1, history.size());
        assertEquals("Hello from Alice!", history.get(0).getMessageText());
    }

    @Test
    @Order(2)
    public void testConversationHistoryOrdering() throws Exception {
        Instant t1 = Instant.now().minusSeconds(60);
        Instant t2 = Instant.now().minusSeconds(30);
        Instant t3 = Instant.now();

        String id1 = UUID.randomUUID().toString();
        String id2 = UUID.randomUUID().toString();
        String id3 = UUID.randomUUID().toString();

        db.saveMessage(new LocalMessage(id1, "DIRECT", "charlie", null, "charlie", false, "First msg", 0.05, false, "ok", "STORED", t1, t1));
        db.saveMessage(new LocalMessage(id2, "DIRECT", "charlie", null, "me", true, "Second msg", 0.08, false, "ok", "STORED", t2, t2));
        db.saveMessage(new LocalMessage(id3, "DIRECT", "charlie", null, "charlie", false, "Third msg", 0.10, false, "ok", "STORED", t3, t3));

        List<LocalMessage> history = db.getConversationHistory("charlie");
        assertEquals(3, history.size());
        assertEquals("First msg", history.get(0).getMessageText());
        assertEquals("Second msg", history.get(1).getMessageText());
        assertEquals("Third msg", history.get(2).getMessageText());
    }

    @Test
    @Order(3)
    public void testUpdateDeliveryStatus() throws Exception {
        String id = UUID.randomUUID().toString();
        LocalMessage msg = new LocalMessage(id, "DIRECT", "david", null, "me", true, "Pending check", 0.0, false, "ok", "PENDING", Instant.now(), Instant.now());
        db.saveMessage(msg);

        db.updateDeliveryStatus(id, "ACKNOWLEDGED");

        List<LocalMessage> history = db.getConversationHistory("david");
        assertEquals(1, history.size());
        assertEquals("ACKNOWLEDGED", history.get(0).getDeliveryStatus());
    }
}
