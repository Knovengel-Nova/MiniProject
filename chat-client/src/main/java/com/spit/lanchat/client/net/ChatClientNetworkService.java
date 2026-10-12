package com.spit.lanchat.client.net;

import com.spit.lanchat.client.db.ClientDatabaseManager;
import com.spit.lanchat.client.db.LocalMessage;
import com.spit.lanchat.common.codec.JsonFramer;
import com.spit.lanchat.common.dto.*;
import com.spit.lanchat.common.protocol.MessageEnvelope;
import com.spit.lanchat.common.protocol.MessageType;
import com.spit.lanchat.common.security.TlsUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.Socket;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Client networking coordinator.
 * Manages TLS-protected TCP socket, newline-delimited JSON framing,
 * synchronous request-response correlation, and durable delivery acknowledgements.
 */
public class ChatClientNetworkService {

    private static final Logger logger = LoggerFactory.getLogger(ChatClientNetworkService.class);

    private Socket socket;
    private JsonFramer framer;
    private final ClientDatabaseManager dbManager;

    private volatile boolean connected = false;
    private volatile String authenticatedUsername;
    private volatile int authenticatedUserId = -1;

    private final Map<String, CompletableFuture<MessageEnvelope>> pendingResponses = new ConcurrentHashMap<>();
    private final ExecutorService readerExecutor = Executors.newSingleThreadExecutor();

    // Listeners for UI events
    private Consumer<DirectMessagePush> onDirectMessageReceived;
    private Consumer<GroupMessagePush> onGroupMessageReceived;
    private Consumer<PresenceNotification> onPresenceChanged;
    private Consumer<StatusNotification> onStatusUpdate;
    private Consumer<Boolean> onConnectionStateChanged;

    public ChatClientNetworkService(ClientDatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public synchronized void connect(String host, int port, boolean tlsEnabled, String truststorePath, String truststorePass) throws Exception {
        if (connected) {
            disconnect();
        }

        if (tlsEnabled) {
            SSLContext sslContext;
            if (truststorePath != null && !truststorePath.isEmpty() && new java.io.File(truststorePath).exists()) {
                sslContext = TlsUtil.createClientSSLContext(truststorePath, truststorePass != null ? truststorePass : "changeit");
            } else {
                // Fallback to testing SSLContext when running tests or self-signed certs
                sslContext = TlsUtil.createTestingSSLContext();
            }
            SSLSocketFactory sf = sslContext.getSocketFactory();
            SSLSocket sslSocket = (SSLSocket) sf.createSocket(host, port);
            sslSocket.startHandshake();
            this.socket = sslSocket;
            logger.info("Connected via TLSv1.3 to {}:{}", host, port);
        } else {
            this.socket = new Socket(host, port);
            logger.info("Connected via plaintext TCP to {}:{}", host, port);
        }

        this.framer = new JsonFramer(socket.getInputStream(), socket.getOutputStream());
        this.connected = true;

        if (onConnectionStateChanged != null) {
            onConnectionStateChanged.accept(true);
        }

        readerExecutor.submit(this::listenLoop);
    }

    private void listenLoop() {
        while (connected && socket != null && !socket.isClosed()) {
            try {
                MessageEnvelope env = framer.readMessage();
                if (env == null) {
                    break; // Server disconnected
                }
                handleIncomingEnvelope(env);
            } catch (Exception e) {
                if (connected) {
                    logger.warn("Connection lost: {}", e.getMessage());
                }
                break;
            }
        }
        handleDisconnect();
    }

    private void handleIncomingEnvelope(MessageEnvelope env) {
        // If this envelope has a matching requestId in pending responses, complete the future
        if (env.getRequestId() != null) {
            CompletableFuture<MessageEnvelope> future = pendingResponses.remove(env.getRequestId());
            if (future != null) {
                future.complete(env);
                return;
            }
        }

        // Handle server push notifications
        try {
            switch (env.getType()) {
                case DIRECT_MSG_PUSH:
                    handleDirectMessagePush(env);
                    break;
                case GROUP_MSG_PUSH:
                    handleGroupMessagePush(env);
                    break;
                case PRESENCE_NOTIF:
                    if (onPresenceChanged != null) {
                        PresenceNotification notif = env.getPayloadAs(PresenceNotification.class);
                        if (notif != null) onPresenceChanged.accept(notif);
                    }
                    break;
                case STATUS_UPDATE_PUSH:
                    if (onStatusUpdate != null) {
                        StatusNotification notif = env.getPayloadAs(StatusNotification.class);
                        if (notif != null) {
                            if (dbManager != null) {
                                dbManager.updateDeliveryStatus(notif.getMessageId(), notif.getDeliveryStatus());
                            }
                            onStatusUpdate.accept(notif);
                        }
                    }
                    break;
                default:
                    logger.debug("Unhandled incoming envelope type: {}", env.getType());
                    break;
            }
        } catch (Exception e) {
            logger.error("Error handling incoming push envelope: {}", e.getMessage(), e);
        }
    }

    private void handleDirectMessagePush(MessageEnvelope env) throws Exception {
        DirectMessagePush push = env.getPayloadAs(DirectMessagePush.class);
        if (push == null) return;

        // Requirement 4 & 5: Save message to local PostgreSQL database using atomic transaction
        if (dbManager != null) {
            LocalMessage localMsg = new LocalMessage(
                    push.getMessageId(),
                    "DIRECT",
                    push.getSenderUsername(), // partner is sender
                    null,
                    push.getSenderUsername(),
                    false, // incoming
                    push.getMessageText(),
                    push.getPredictedRiskScore(),
                    push.isFlagged(),
                    push.getModelStatus(),
                    "STORED",
                    push.getTimestamp(),
                    Instant.now()
            );

            // Commit to local DB transaction
            dbManager.saveMessage(localMsg);

            // ONLY AFTER THAT TRANSACTION COMMITS: send durable-storage ACK to the server!
            sendDeliveryAck(push.getMessageId());
        }

        if (onDirectMessageReceived != null) {
            onDirectMessageReceived.accept(push);
        }
    }

    private void handleGroupMessagePush(MessageEnvelope env) throws Exception {
        GroupMessagePush push = env.getPayloadAs(GroupMessagePush.class);
        if (push == null) return;

        // Durably persist locally in transaction
        if (dbManager != null) {
            LocalMessage localMsg = new LocalMessage(
                    push.getMessageId(),
                    "GROUP",
                    push.getGroupId(), // partner is groupId
                    push.getGroupName(),
                    push.getSenderUsername(),
                    push.getSenderUsername().equalsIgnoreCase(authenticatedUsername),
                    push.getMessageText(),
                    push.getPredictedRiskScore(),
                    push.isFlagged(),
                    push.getModelStatus(),
                    "STORED",
                    push.getTimestamp(),
                    Instant.now()
            );

            dbManager.saveMessage(localMsg);

            // ONLY AFTER COMMIT: send ACK to server
            if (!push.getSenderUsername().equalsIgnoreCase(authenticatedUsername)) {
                sendDeliveryAck(push.getMessageId());
            }
        }

        if (onGroupMessageReceived != null) {
            onGroupMessageReceived.accept(push);
        }
    }

    private void sendDeliveryAck(String messageId) {
        if (!connected || authenticatedUsername == null) return;
        try {
            DeliveryAckRequest ack = new DeliveryAckRequest(messageId, authenticatedUsername, Instant.now());
            MessageEnvelope env = MessageEnvelope.createRequest(MessageType.DELIVERY_ACK_REQ, messageId, ack);
            framer.writeMessage(env);
            logger.debug("Emitted durable storage ACK for messageId '{}'", messageId);
        } catch (IOException e) {
            logger.warn("Failed to send delivery ACK for messageId '{}': {}", messageId, e.getMessage());
        }
    }

    private synchronized void handleDisconnect() {
        if (!connected) return;
        connected = false;
        logger.info("Disconnected from server.");
        for (var f : pendingResponses.values()) {
            f.completeExceptionally(new IOException("Connection lost"));
        }
        pendingResponses.clear();

        if (onConnectionStateChanged != null) {
            onConnectionStateChanged.accept(false);
        }
    }

    public synchronized void disconnect() {
        connected = false;
        if (socket != null) {
            try { socket.close(); } catch (Exception ignored) {}
        }
        if (framer != null) {
            framer.close();
        }
        handleDisconnect();
    }

    public CompletableFuture<MessageEnvelope> sendRequest(MessageEnvelope request) {
        if (!connected || framer == null) {
            CompletableFuture<MessageEnvelope> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IOException("Not connected to server"));
            return failed;
        }

        CompletableFuture<MessageEnvelope> future = new CompletableFuture<>();
        pendingResponses.put(request.getRequestId(), future);

        try {
            framer.writeMessage(request);
        } catch (IOException e) {
            pendingResponses.remove(request.getRequestId());
            future.completeExceptionally(e);
        }

        return future;
    }

    // ========================================================================
    // High-Level Protocol Operations
    // ========================================================================

    public RegisterResponse register(String username, String password) throws Exception {
        RegisterRequest req = new RegisterRequest(username, password);
        MessageEnvelope env = MessageEnvelope.createRequest(MessageType.REGISTER_REQ, req);

        MessageEnvelope respEnv = sendRequest(env).get(5, TimeUnit.SECONDS);
        if ("ERROR".equalsIgnoreCase(respEnv.getStatus())) {
            throw new IllegalArgumentException(respEnv.getError());
        }
        return respEnv.getPayloadAs(RegisterResponse.class);
    }

    public LoginResponse login(String username, String password) throws Exception {
        LoginRequest req = new LoginRequest(username, password);
        MessageEnvelope env = MessageEnvelope.createRequest(MessageType.LOGIN_REQ, req);

        MessageEnvelope respEnv = sendRequest(env).get(5, TimeUnit.SECONDS);
        if ("ERROR".equalsIgnoreCase(respEnv.getStatus())) {
            throw new IllegalArgumentException(respEnv.getError());
        }

        LoginResponse resp = respEnv.getPayloadAs(LoginResponse.class);
        this.authenticatedUsername = resp.getUsername();
        this.authenticatedUserId = resp.getUserId();
        return resp;
    }

    public void logout() {
        if (connected) {
            MessageEnvelope env = MessageEnvelope.createRequest(MessageType.LOGOUT_REQ, null);
            try {
                sendRequest(env).get(2, TimeUnit.SECONDS);
            } catch (Exception ignored) {}
        }
        this.authenticatedUsername = null;
        this.authenticatedUserId = -1;
    }

    public List<UserDto> getUserList() throws Exception {
        MessageEnvelope env = MessageEnvelope.createRequest(MessageType.USER_LIST_REQ, null);
        MessageEnvelope respEnv = sendRequest(env).get(5, TimeUnit.SECONDS);
        if ("ERROR".equalsIgnoreCase(respEnv.getStatus())) {
            throw new IOException(respEnv.getError());
        }
        UserListResponse resp = respEnv.getPayloadAs(UserListResponse.class);
        return resp != null ? resp.getUsers() : List.of();
    }

    public DirectMessageResponse sendDirectMessage(String recipientUsername, String messageText) throws Exception {
        String msgId = UUID.randomUUID().toString();
        DirectMessageRequest req = new DirectMessageRequest(msgId, recipientUsername, messageText);
        MessageEnvelope env = MessageEnvelope.createRequest(MessageType.DIRECT_MSG_REQ, msgId, req);

        MessageEnvelope respEnv = sendRequest(env).get(5, TimeUnit.SECONDS);
        if ("ERROR".equalsIgnoreCase(respEnv.getStatus())) {
            throw new IOException(respEnv.getError());
        }
        DirectMessageResponse resp = respEnv.getPayloadAs(DirectMessageResponse.class);

        // Store outgoing message into client's local DB
        if (dbManager != null) {
            LocalMessage localMsg = new LocalMessage(
                    msgId,
                    "DIRECT",
                    recipientUsername,
                    null,
                    authenticatedUsername,
                    true, // outgoing
                    messageText,
                    resp != null ? resp.getPredictedRiskScore() : null,
                    resp != null && resp.isFlagged(),
                    resp != null ? resp.getModelStatus() : "ok",
                    resp != null ? resp.getDeliveryStatus() : "PENDING",
                    Instant.now(),
                    Instant.now()
            );
            dbManager.saveMessage(localMsg);
        }

        return resp;
    }

    public GroupDto createGroup(String groupName, List<String> memberUsernames) throws Exception {
        GroupCreateRequest req = new GroupCreateRequest(groupName, memberUsernames);
        MessageEnvelope env = MessageEnvelope.createRequest(MessageType.GROUP_CREATE_REQ, req);

        MessageEnvelope respEnv = sendRequest(env).get(5, TimeUnit.SECONDS);
        if ("ERROR".equalsIgnoreCase(respEnv.getStatus())) {
            throw new IOException(respEnv.getError());
        }
        return respEnv.getPayloadAs(GroupDto.class);
    }

    public List<GroupDto> getGroupList() throws Exception {
        MessageEnvelope env = MessageEnvelope.createRequest(MessageType.GROUP_LIST_REQ, null);
        MessageEnvelope respEnv = sendRequest(env).get(5, TimeUnit.SECONDS);
        if ("ERROR".equalsIgnoreCase(respEnv.getStatus())) {
            throw new IOException(respEnv.getError());
        }
        GroupListResponse resp = respEnv.getPayloadAs(GroupListResponse.class);
        return resp != null ? resp.getGroups() : List.of();
    }

    public GroupMemberResponse modifyGroupMember(String groupId, String targetUsername, String action) throws Exception {
        GroupMemberRequest req = new GroupMemberRequest(groupId, targetUsername, action);
        MessageEnvelope env = MessageEnvelope.createRequest(MessageType.GROUP_MEMBER_REQ, req);

        MessageEnvelope respEnv = sendRequest(env).get(5, TimeUnit.SECONDS);
        if ("ERROR".equalsIgnoreCase(respEnv.getStatus())) {
            throw new IOException(respEnv.getError());
        }
        return respEnv.getPayloadAs(GroupMemberResponse.class);
    }

    public GroupMessageResponse sendGroupMessage(String groupId, String groupName, String messageText) throws Exception {
        String msgId = UUID.randomUUID().toString();
        GroupMessageRequest req = new GroupMessageRequest(msgId, groupId, messageText);
        MessageEnvelope env = MessageEnvelope.createRequest(MessageType.GROUP_MSG_REQ, msgId, req);

        MessageEnvelope respEnv = sendRequest(env).get(5, TimeUnit.SECONDS);
        if ("ERROR".equalsIgnoreCase(respEnv.getStatus())) {
            throw new IOException(respEnv.getError());
        }
        GroupMessageResponse resp = respEnv.getPayloadAs(GroupMessageResponse.class);

        if (dbManager != null) {
            LocalMessage localMsg = new LocalMessage(
                    msgId,
                    "GROUP",
                    groupId,
                    groupName,
                    authenticatedUsername,
                    true, // outgoing
                    messageText,
                    resp != null ? resp.getPredictedRiskScore() : null,
                    resp != null && resp.isFlagged(),
                    resp != null ? resp.getModelStatus() : "ok",
                    "PENDING",
                    Instant.now(),
                    Instant.now()
            );
            dbManager.saveMessage(localMsg);
        }

        return resp;
    }

    public PendingSyncResponse syncPendingMessages() throws Exception {
        PendingSyncRequest req = new PendingSyncRequest(authenticatedUsername);
        MessageEnvelope env = MessageEnvelope.createRequest(MessageType.PENDING_SYNC_REQ, req);

        MessageEnvelope respEnv = sendRequest(env).get(5, TimeUnit.SECONDS);
        if ("ERROR".equalsIgnoreCase(respEnv.getStatus())) {
            throw new IOException(respEnv.getError());
        }
        return respEnv.getPayloadAs(PendingSyncResponse.class);
    }

    // Setters for UI event listeners
    public void setOnDirectMessageReceived(Consumer<DirectMessagePush> listener) {
        this.onDirectMessageReceived = listener;
    }

    public void setOnGroupMessageReceived(Consumer<GroupMessagePush> listener) {
        this.onGroupMessageReceived = listener;
    }

    public void setOnPresenceChanged(Consumer<PresenceNotification> listener) {
        this.onPresenceChanged = listener;
    }

    public void setOnStatusUpdate(Consumer<StatusNotification> listener) {
        this.onStatusUpdate = listener;
    }

    public void setOnConnectionStateChanged(Consumer<Boolean> listener) {
        this.onConnectionStateChanged = listener;
    }

    public boolean isConnected() {
        return connected;
    }

    public String getAuthenticatedUsername() {
        return authenticatedUsername;
    }

    public int getAuthenticatedUserId() {
        return authenticatedUserId;
    }
}
