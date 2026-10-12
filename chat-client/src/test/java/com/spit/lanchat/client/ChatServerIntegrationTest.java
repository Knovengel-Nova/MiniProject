package com.spit.lanchat.client;

import com.spit.lanchat.client.db.ClientDatabaseManager;
import com.spit.lanchat.client.net.ChatClientNetworkService;
import com.spit.lanchat.common.dto.*;
import com.spit.lanchat.server.ChatServer;
import com.spit.lanchat.server.ServerConfig;
import org.junit.jupiter.api.*;

import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ChatServerIntegrationTest {

    private static ChatServer server;
    private static int serverPort;
    private static final String DB_URL = "jdbc:postgresql://127.0.0.1:5432/lan_chat_server";
    private static final String CLIENT_DB_URL = "jdbc:postgresql://127.0.0.1:5432/lan_chat_client_test";
    private static final String DB_USER = "postgres";
    private static final String DB_PASS = "postgres";

    private static ClientDatabaseManager clientDb;

    @BeforeAll
    public static void setup() throws Exception {
        // Clean both databases
        try (Connection conn = java.sql.DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             Statement stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE TABLE message_deliveries, pending_messages, group_members, groups, conversations, users RESTART IDENTITY CASCADE;");
        }

        clientDb = new ClientDatabaseManager(CLIENT_DB_URL, DB_USER, DB_PASS);
        clientDb.initSchema();
        try (Connection conn = clientDb.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE TABLE local_messages, local_conversations, local_sync_state;");
        }

        ServerConfig config = new ServerConfig();
        config.setPort(8445);
        config.setTlsEnabled(true);
        config.setKeystorePath("certs/server-keystore.p12");
        config.setKeystorePassword("changeit");
        config.setDbUrl(DB_URL);
        config.setDbUser(DB_USER);
        config.setDbPassword(DB_PASS);

        server = new ChatServer(config);
        server.start();
        serverPort = server.getPort();
    }

    @AfterAll
    public static void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    @Order(1)
    public void testRegistrationLoginAndDuplicateRejection() throws Exception {
        ChatClientNetworkService client = new ChatClientNetworkService(clientDb);
        client.connect("127.0.0.1", serverPort, true, "certs/client-truststore.p12", "changeit");

        // 1. Register Alice
        RegisterResponse regAlice = client.register("alice_int", "Secret123!");
        assertNotNull(regAlice);
        assertTrue(regAlice.getUserId() > 0);

        // 2. Duplicate registration rejection
        assertThrows(IllegalArgumentException.class, () -> {
            client.register("alice_int", "AnotherPassword");
        });

        // 3. Login Alice
        LoginResponse loginResp = client.login("alice_int", "Secret123!");
        assertNotNull(loginResp);
        assertEquals("alice_int", loginResp.getUsername());
        assertNotNull(loginResp.getSessionToken());

        client.disconnect();
    }

    @Test
    @Order(2)
    public void testUnauthorizedRequestRejection() throws Exception {
        ChatClientNetworkService client = new ChatClientNetworkService(clientDb);
        client.connect("127.0.0.1", serverPort, true, "certs/client-truststore.p12", "changeit");

        // Attempting to list users or send message without login must fail with unauthorized error
        assertThrows(Exception.class, client::getUserList);

        client.disconnect();
    }

    @Test
    @Order(3)
    public void testDirectMessageExchangeAndServerContentDeletion() throws Exception {
        // Register Bob
        ChatClientNetworkService bobClient = new ChatClientNetworkService(clientDb);
        bobClient.connect("127.0.0.1", serverPort, true, "certs/client-truststore.p12", "changeit");
        bobClient.register("bob_int", "BobPass123!");
        bobClient.login("bob_int", "BobPass123!");

        // Connect Alice
        ChatClientNetworkService aliceClient = new ChatClientNetworkService(clientDb);
        aliceClient.connect("127.0.0.1", serverPort, true, "certs/client-truststore.p12", "changeit");
        aliceClient.login("alice_int", "Secret123!");

        CountDownLatch bobReceivedLatch = new CountDownLatch(1);
        AtomicReference<DirectMessagePush> receivedPush = new AtomicReference<>();

        bobClient.setOnDirectMessageReceived(push -> {
            receivedPush.set(push);
            bobReceivedLatch.countDown();
        });

        // Alice sends direct message to Bob
        DirectMessageResponse sendResp = aliceClient.sendDirectMessage("bob_int", "Hello Bob, live test!");
        assertNotNull(sendResp);
        String msgId = sendResp.getMessageId();

        // Wait for Bob to receive
        assertTrue(bobReceivedLatch.await(3, TimeUnit.SECONDS), "Bob should receive live pushed message");
        assertNotNull(receivedPush.get());
        assertEquals("Hello Bob, live test!", receivedPush.get().getMessageText());
        assertEquals("alice_int", receivedPush.get().getSenderUsername());

        // Wait brief moment for Bob's ACK to reach server and process
        Thread.sleep(300);

        // Verify that server message content is deleted after Bob's durable storage ACK!
        assertTrue(server.getDbManager().isMessageContentDeleted(msgId),
                "Server message content must be deleted after recipient ACK");

        bobClient.disconnect();
        aliceClient.disconnect();
    }

    @Test
    @Order(4)
    public void testOfflineMessageDeliveryAndSync() throws Exception {
        // Bob is offline
        ChatClientNetworkService aliceClient = new ChatClientNetworkService(clientDb);
        aliceClient.connect("127.0.0.1", serverPort, true, "certs/client-truststore.p12", "changeit");
        aliceClient.login("alice_int", "Secret123!");

        // Alice sends message to Bob while Bob is offline
        DirectMessageResponse sendResp = aliceClient.sendDirectMessage("bob_int", "Offline message for Bob");
        assertEquals("PENDING", sendResp.getDeliveryStatus());
        String msgId = sendResp.getMessageId();

        // Verify message content is currently retained on server for the offline user
        assertFalse(server.getDbManager().isMessageContentDeleted(msgId));

        // Now Bob connects, logs in, and syncs
        ChatClientNetworkService bobClient = new ChatClientNetworkService(clientDb);
        bobClient.connect("127.0.0.1", serverPort, true, "certs/client-truststore.p12", "changeit");
        bobClient.login("bob_int", "BobPass123!");

        CountDownLatch syncLatch = new CountDownLatch(1);
        bobClient.setOnDirectMessageReceived(push -> {
            if (push.getMessageId().equals(msgId)) {
                syncLatch.countDown();
            }
        });

        bobClient.syncPendingMessages();
        assertTrue(syncLatch.await(3, TimeUnit.SECONDS), "Bob should receive offline pending message upon sync");

        // Wait brief moment for Bob's durable ACK to process
        Thread.sleep(300);

        // Server message content must now be deleted!
        assertTrue(server.getDbManager().isMessageContentDeleted(msgId),
                "Server message content must be deleted after offline recipient reconnects and ACKs");

        aliceClient.disconnect();
        bobClient.disconnect();
    }

    @Test
    @Order(5)
    public void testGroupBroadcastAndDelayedContentDeletion() throws Exception {
        // Register Charlie
        ChatClientNetworkService charlieClient = new ChatClientNetworkService(clientDb);
        charlieClient.connect("127.0.0.1", serverPort, true, "certs/client-truststore.p12", "changeit");
        charlieClient.register("charlie_int", "CharliePass123!");
        charlieClient.disconnect(); // Charlie goes offline immediately

        // Alice and Bob are online
        ChatClientNetworkService aliceClient = new ChatClientNetworkService(clientDb);
        aliceClient.connect("127.0.0.1", serverPort, true, "certs/client-truststore.p12", "changeit");
        aliceClient.login("alice_int", "Secret123!");

        ChatClientNetworkService bobClient = new ChatClientNetworkService(clientDb);
        bobClient.connect("127.0.0.1", serverPort, true, "certs/client-truststore.p12", "changeit");
        bobClient.login("bob_int", "BobPass123!");

        // Alice creates group with Bob and Charlie
        GroupDto group = aliceClient.createGroup("Engineering Team", List.of("bob_int", "charlie_int"));
        assertNotNull(group);

        CountDownLatch bobGroupLatch = new CountDownLatch(1);
        bobClient.setOnGroupMessageReceived(push -> {
            bobGroupLatch.countDown();
        });

        // Alice broadcasts to group
        GroupMessageResponse sendResp = aliceClient.sendGroupMessage(group.getGroupId(), group.getGroupName(), "Team standup at 10 AM");
        String msgId = sendResp.getMessageId();

        // Bob receives and ACKs
        assertTrue(bobGroupLatch.await(3, TimeUnit.SECONDS));
        Thread.sleep(300);

        // Charlie hasn't ACKed yet (offline) -> content must NOT be deleted yet!
        assertFalse(server.getDbManager().isMessageContentDeleted(msgId),
                "Server group message content must NOT be deleted while Charlie is still unacknowledged");

        // Charlie connects, logs in, syncs
        ChatClientNetworkService charlieReconnect = new ChatClientNetworkService(clientDb);
        charlieReconnect.connect("127.0.0.1", serverPort, true, "certs/client-truststore.p12", "changeit");
        charlieReconnect.login("charlie_int", "CharliePass123!");

        CountDownLatch charlieLatch = new CountDownLatch(1);
        charlieReconnect.setOnGroupMessageReceived(push -> {
            if (push.getMessageId().equals(msgId)) {
                charlieLatch.countDown();
            }
        });
        charlieReconnect.syncPendingMessages();

        assertTrue(charlieLatch.await(3, TimeUnit.SECONDS));
        Thread.sleep(300);

        // Now that BOTH Bob and Charlie have ACKed -> server content IS deleted!
        assertTrue(server.getDbManager().isMessageContentDeleted(msgId),
                "Server group message content must be deleted once ALL recipients have acknowledged");

        aliceClient.disconnect();
        bobClient.disconnect();
        charlieReconnect.disconnect();
    }

    @Test
    @Order(6)
    public void testMalformedJsonHandlingDoesNotCrashServer() throws Exception {
        // Connect raw TLS socket and send malformed JSON string
        javax.net.ssl.SSLContext sslContext = com.spit.lanchat.common.security.TlsUtil.createTestingSSLContext();
        Socket socket = sslContext.getSocketFactory().createSocket("127.0.0.1", serverPort);

        OutputStream out = socket.getOutputStream();
        out.write("This is a malformed non-json line\n".getBytes(StandardCharsets.UTF_8));
        out.flush();

        Thread.sleep(200);
        socket.close();

        // Verify server is still alive and responsive to valid clients
        ChatClientNetworkService healthyClient = new ChatClientNetworkService(clientDb);
        healthyClient.connect("127.0.0.1", serverPort, true, "certs/client-truststore.p12", "changeit");
        LoginResponse resp = healthyClient.login("alice_int", "Secret123!");
        assertNotNull(resp);
        healthyClient.disconnect();
    }
}
