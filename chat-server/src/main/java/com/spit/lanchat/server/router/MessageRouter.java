package com.spit.lanchat.server.router;

import com.spit.lanchat.common.dto.*;
import com.spit.lanchat.common.protocol.MessageEnvelope;
import com.spit.lanchat.common.protocol.MessageType;
import com.spit.lanchat.server.db.ServerDatabaseManager;
import com.spit.lanchat.server.db.ServerDatabaseManager.UserRecord;
import com.spit.lanchat.server.pds.PDSService;
import com.spit.lanchat.server.session.ClientSession;
import com.spit.lanchat.server.session.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Routes and handles client protocol messages.
 * Derives sender identity strictly from the authenticated server session.
 * Coordinates PDS risk evaluation, temporary message queueing, and delivery acknowledgements.
 */
public class MessageRouter {

    private static final Logger logger = LoggerFactory.getLogger(MessageRouter.class);

    private final ServerDatabaseManager dbManager;
    private final SessionManager sessionManager;
    private final PDSService pdsService;

    public MessageRouter(ServerDatabaseManager dbManager, SessionManager sessionManager, PDSService pdsService) {
        this.dbManager = dbManager;
        this.sessionManager = sessionManager;
        this.pdsService = pdsService;
    }

    public void handleMessage(ClientSession session, MessageEnvelope env) {
        if (env == null || env.getType() == null) {
            return;
        }

        try {
            switch (env.getType()) {
                case REGISTER_REQ:
                    handleRegister(session, env);
                    break;
                case LOGIN_REQ:
                    handleLogin(session, env);
                    break;
                case LOGOUT_REQ:
                    handleLogout(session, env);
                    break;
                case USER_LIST_REQ:
                    handleUserList(session, env);
                    break;
                case DIRECT_MSG_REQ:
                    handleDirectMessage(session, env);
                    break;
                case GROUP_CREATE_REQ:
                    handleGroupCreate(session, env);
                    break;
                case GROUP_MEMBER_REQ:
                    handleGroupMember(session, env);
                    break;
                case GROUP_LIST_REQ:
                    handleGroupList(session, env);
                    break;
                case GROUP_MSG_REQ:
                    handleGroupMessage(session, env);
                    break;
                case DELIVERY_ACK_REQ:
                    handleDeliveryAck(session, env);
                    break;
                case PENDING_SYNC_REQ:
                    handlePendingSync(session, env);
                    break;
                default:
                    session.send(MessageEnvelope.createError(MessageType.ERROR_RESP, env.getRequestId(),
                            "Unsupported message type: " + env.getType()));
                    break;
            }
        } catch (Exception e) {
            logger.error("Error processing message type {}: {}", env.getType(), e.getMessage(), e);
            try {
                session.send(MessageEnvelope.createError(MessageType.ERROR_RESP, env.getRequestId(),
                        "Internal server error: " + e.getMessage()));
            } catch (IOException ignored) {}
        }
    }

    private void handleRegister(ClientSession session, MessageEnvelope env) throws IOException {
        RegisterRequest req = env.getPayloadAs(RegisterRequest.class);
        if (req == null || req.getUsername() == null || req.getPassword() == null) {
            session.send(MessageEnvelope.createError(MessageType.REGISTER_RESP, env.getRequestId(), "Missing username or password"));
            return;
        }

        try {
            int userId = dbManager.registerUser(req.getUsername(), req.getPassword());
            RegisterResponse resp = new RegisterResponse(userId, req.getUsername().trim().toLowerCase(), "Registration successful");
            session.send(MessageEnvelope.createResponse(MessageType.REGISTER_RESP, env.getRequestId(), null, "OK", resp));
        } catch (IllegalArgumentException e) {
            session.send(MessageEnvelope.createError(MessageType.REGISTER_RESP, env.getRequestId(), e.getMessage()));
        } catch (SQLException e) {
            session.send(MessageEnvelope.createError(MessageType.REGISTER_RESP, env.getRequestId(), "Database error during registration"));
        }
    }

    private void handleLogin(ClientSession session, MessageEnvelope env) throws IOException {
        LoginRequest req = env.getPayloadAs(LoginRequest.class);
        if (req == null || req.getUsername() == null || req.getPassword() == null) {
            session.send(MessageEnvelope.createError(MessageType.LOGIN_RESP, env.getRequestId(), "Invalid username or password"));
            return;
        }

        try {
            UserRecord user = dbManager.authenticateUser(req.getUsername(), req.getPassword());
            if (user == null) {
                // Generic authentication failure message (never reveal whether username exists)
                session.send(MessageEnvelope.createError(MessageType.LOGIN_RESP, env.getRequestId(), "Invalid username or password"));
                return;
            }

            sessionManager.bindUser(session, user.userId, user.username);
            dbManager.updateUserStatus(user.userId, "online");

            LoginResponse resp = new LoginResponse(user.userId, user.username, session.getSessionToken(), "Login successful");
            session.send(MessageEnvelope.createResponse(MessageType.LOGIN_RESP, env.getRequestId(), null, "OK", resp));

        } catch (SQLException e) {
            session.send(MessageEnvelope.createError(MessageType.LOGIN_RESP, env.getRequestId(), "Database error during login"));
        }
    }

    private void handleLogout(ClientSession session, MessageEnvelope env) throws IOException {
        if (session.isAuthenticated()) {
            try {
                dbManager.updateUserStatus(session.getUserId(), "offline");
            } catch (SQLException ignored) {}
            sessionManager.unbindUser(session);
        }
        session.send(MessageEnvelope.createResponse(MessageType.LOGOUT_RESP, env.getRequestId(), null, "OK", null));
    }

    private void handleUserList(ClientSession session, MessageEnvelope env) throws IOException {
        if (!session.isAuthenticated()) {
            session.send(MessageEnvelope.createError(MessageType.ERROR_RESP, env.getRequestId(), "Unauthorized: login required"));
            return;
        }

        try {
            List<UserDto> users = dbManager.getAllUsers();
            for (UserDto u : users) {
                if (sessionManager.isUserOnline(u.getUserId())) {
                    u.setStatus("online");
                } else {
                    u.setStatus("offline");
                }
            }
            session.send(MessageEnvelope.createResponse(MessageType.USER_LIST_RESP, env.getRequestId(), null, "OK", new UserListResponse(users)));
        } catch (SQLException e) {
            session.send(MessageEnvelope.createError(MessageType.USER_LIST_RESP, env.getRequestId(), "Failed to retrieve user list"));
        }
    }

    private void handleDirectMessage(ClientSession session, MessageEnvelope env) throws IOException {
        if (!session.isAuthenticated()) {
            session.send(MessageEnvelope.createError(MessageType.ERROR_RESP, env.getRequestId(), "Unauthorized: login required"));
            return;
        }

        DirectMessageRequest req = env.getPayloadAs(DirectMessageRequest.class);
        if (req == null || req.getRecipientUsername() == null || req.getMessageText() == null || req.getMessageText().trim().isEmpty()) {
            session.send(MessageEnvelope.createError(MessageType.DIRECT_MSG_RESP, env.getRequestId(), "Invalid message payload"));
            return;
        }

        String messageId = req.getMessageId() != null ? req.getMessageId() : UUID.randomUUID().toString();
        String senderUsername = session.getUsername(); // Strict server session identity
        int senderId = session.getUserId();

        try {
            UserRecord recipient = dbManager.findUserByUsername(req.getRecipientUsername());
            if (recipient == null) {
                session.send(MessageEnvelope.createError(MessageType.DIRECT_MSG_RESP, env.getRequestId(), "Recipient user not found"));
                return;
            }

            // Call Python PDS via Java PDSService
            PdsPredictionResult pds = pdsService.classifyMessage(messageId, req.getMessageText());

            // Save to temporary pending queue in server DB
            dbManager.storePendingDirectMessage(messageId, senderId, senderUsername, recipient.userId,
                    recipient.username, req.getMessageText(), pds);

            String deliveryStatus = "PENDING";
            ClientSession recipientSession = sessionManager.getSessionByUserId(recipient.userId);

            if (recipientSession != null && recipientSession.isAuthenticated()) {
                DirectMessagePush push = new DirectMessagePush(
                        messageId,
                        senderUsername,
                        recipient.username,
                        req.getMessageText(),
                        Instant.now(),
                        pds.getPredictedRiskScore(),
                        pds.isFlagged(),
                        pds.getStatus()
                );
                MessageEnvelope pushEnv = MessageEnvelope.createRequest(MessageType.DIRECT_MSG_PUSH, messageId, push);
                recipientSession.send(pushEnv);
                dbManager.recordDeliveryPushed(messageId, recipient.userId);
                deliveryStatus = "DELIVERED";
            }

            DirectMessageResponse resp = new DirectMessageResponse(
                    messageId,
                    recipient.username,
                    deliveryStatus,
                    pds.getPredictedRiskScore(),
                    pds.isFlagged(),
                    pds.getStatus(),
                    Instant.now()
            );
            session.send(MessageEnvelope.createResponse(MessageType.DIRECT_MSG_RESP, env.getRequestId(), messageId, "OK", resp));

        } catch (SQLException e) {
            session.send(MessageEnvelope.createError(MessageType.DIRECT_MSG_RESP, env.getRequestId(), "Database error saving message"));
        }
    }

    private void handleGroupCreate(ClientSession session, MessageEnvelope env) throws IOException {
        if (!session.isAuthenticated()) {
            session.send(MessageEnvelope.createError(MessageType.ERROR_RESP, env.getRequestId(), "Unauthorized: login required"));
            return;
        }

        GroupCreateRequest req = env.getPayloadAs(GroupCreateRequest.class);
        if (req == null || req.getGroupName() == null || req.getGroupName().trim().isEmpty()) {
            session.send(MessageEnvelope.createError(MessageType.GROUP_CREATE_RESP, env.getRequestId(), "Group name cannot be empty"));
            return;
        }

        String groupId = "grp-" + UUID.randomUUID().toString().substring(0, 8);
        try {
            dbManager.createGroup(groupId, req.getGroupName().trim(), session.getUserId(), req.getMemberUsernames());
            GroupDto groupDto = dbManager.getGroup(groupId);
            session.send(MessageEnvelope.createResponse(MessageType.GROUP_CREATE_RESP, env.getRequestId(), groupId, "OK", groupDto));
        } catch (SQLException e) {
            session.send(MessageEnvelope.createError(MessageType.GROUP_CREATE_RESP, env.getRequestId(), "Failed to create group"));
        }
    }

    private void handleGroupMember(ClientSession session, MessageEnvelope env) throws IOException {
        if (!session.isAuthenticated()) {
            session.send(MessageEnvelope.createError(MessageType.ERROR_RESP, env.getRequestId(), "Unauthorized: login required"));
            return;
        }

        GroupMemberRequest req = env.getPayloadAs(GroupMemberRequest.class);
        if (req == null || req.getGroupId() == null || req.getTargetUsername() == null) {
            session.send(MessageEnvelope.createError(MessageType.GROUP_MEMBER_RESP, env.getRequestId(), "Invalid group member request"));
            return;
        }

        try {
            if (!dbManager.isGroupMember(req.getGroupId(), session.getUserId())) {
                session.send(MessageEnvelope.createError(MessageType.GROUP_MEMBER_RESP, env.getRequestId(), "Unauthorized: not a member of this group"));
                return;
            }

            UserRecord target = dbManager.findUserByUsername(req.getTargetUsername());
            if (target == null) {
                session.send(MessageEnvelope.createError(MessageType.GROUP_MEMBER_RESP, env.getRequestId(), "User not found"));
                return;
            }

            if ("ADD".equalsIgnoreCase(req.getAction())) {
                dbManager.addGroupMember(req.getGroupId(), target.userId);
            } else if ("REMOVE".equalsIgnoreCase(req.getAction())) {
                dbManager.removeGroupMember(req.getGroupId(), target.userId);
            }

            GroupDto grp = dbManager.getGroup(req.getGroupId());
            GroupMemberResponse resp = new GroupMemberResponse(
                    req.getGroupId(),
                    grp != null ? grp.getGroupName() : "",
                    grp != null ? grp.getMembers() : List.of(),
                    "Member updated"
            );
            session.send(MessageEnvelope.createResponse(MessageType.GROUP_MEMBER_RESP, env.getRequestId(), req.getGroupId(), "OK", resp));

        } catch (SQLException e) {
            session.send(MessageEnvelope.createError(MessageType.GROUP_MEMBER_RESP, env.getRequestId(), "Failed to update group members"));
        }
    }

    private void handleGroupList(ClientSession session, MessageEnvelope env) throws IOException {
        if (!session.isAuthenticated()) {
            session.send(MessageEnvelope.createError(MessageType.ERROR_RESP, env.getRequestId(), "Unauthorized: login required"));
            return;
        }

        try {
            List<GroupDto> groups = dbManager.getGroupsForUser(session.getUserId());
            session.send(MessageEnvelope.createResponse(MessageType.GROUP_LIST_RESP, env.getRequestId(), null, "OK", new GroupListResponse(groups)));
        } catch (SQLException e) {
            session.send(MessageEnvelope.createError(MessageType.GROUP_LIST_RESP, env.getRequestId(), "Failed to retrieve groups"));
        }
    }

    private void handleGroupMessage(ClientSession session, MessageEnvelope env) throws IOException {
        if (!session.isAuthenticated()) {
            session.send(MessageEnvelope.createError(MessageType.ERROR_RESP, env.getRequestId(), "Unauthorized: login required"));
            return;
        }

        GroupMessageRequest req = env.getPayloadAs(GroupMessageRequest.class);
        if (req == null || req.getGroupId() == null || req.getMessageText() == null || req.getMessageText().trim().isEmpty()) {
            session.send(MessageEnvelope.createError(MessageType.GROUP_MSG_RESP, env.getRequestId(), "Invalid group message"));
            return;
        }

        try {
            if (!dbManager.isGroupMember(req.getGroupId(), session.getUserId())) {
                session.send(MessageEnvelope.createError(MessageType.GROUP_MSG_RESP, env.getRequestId(), "Unauthorized: not a member of this group"));
                return;
            }

            GroupDto group = dbManager.getGroup(req.getGroupId());
            if (group == null) {
                session.send(MessageEnvelope.createError(MessageType.GROUP_MSG_RESP, env.getRequestId(), "Group not found"));
                return;
            }

            String messageId = req.getMessageId() != null ? req.getMessageId() : UUID.randomUUID().toString();
            String senderUsername = session.getUsername();
            int senderId = session.getUserId();

            PdsPredictionResult pds = pdsService.classifyMessage(messageId, req.getMessageText());
            dbManager.storePendingGroupMessage(messageId, senderId, senderUsername, req.getGroupId(), req.getMessageText(), pds);

            List<Integer> memberIds = dbManager.getGroupMemberIds(req.getGroupId());
            int intendedCount = Math.max(0, memberIds.size() - 1);

            GroupMessagePush push = new GroupMessagePush(
                    messageId,
                    req.getGroupId(),
                    group.getGroupName(),
                    senderUsername,
                    req.getMessageText(),
                    Instant.now(),
                    pds.getPredictedRiskScore(),
                    pds.isFlagged(),
                    pds.getStatus()
            );
            MessageEnvelope pushEnv = MessageEnvelope.createRequest(MessageType.GROUP_MSG_PUSH, messageId, push);

            for (int mId : memberIds) {
                if (mId == senderId) continue;
                ClientSession memberSession = sessionManager.getSessionByUserId(mId);
                if (memberSession != null && memberSession.isAuthenticated()) {
                    memberSession.send(pushEnv);
                    dbManager.recordDeliveryPushed(messageId, mId);
                }
            }

            GroupMessageResponse resp = new GroupMessageResponse(
                    messageId,
                    req.getGroupId(),
                    "PENDING",
                    intendedCount,
                    pds.getPredictedRiskScore(),
                    pds.isFlagged(),
                    pds.getStatus(),
                    Instant.now()
            );
            session.send(MessageEnvelope.createResponse(MessageType.GROUP_MSG_RESP, env.getRequestId(), messageId, "OK", resp));

        } catch (SQLException e) {
            session.send(MessageEnvelope.createError(MessageType.GROUP_MSG_RESP, env.getRequestId(), "Database error saving group message"));
        }
    }

    private void handleDeliveryAck(ClientSession session, MessageEnvelope env) throws IOException {
        if (!session.isAuthenticated()) {
            session.send(MessageEnvelope.createError(MessageType.ERROR_RESP, env.getRequestId(), "Unauthorized: login required"));
            return;
        }

        DeliveryAckRequest req = env.getPayloadAs(DeliveryAckRequest.class);
        if (req == null || req.getMessageId() == null) {
            session.send(MessageEnvelope.createError(MessageType.DELIVERY_ACK_RESP, env.getRequestId(), "Missing messageId in ACK"));
            return;
        }

        try {
            // Recipient sends ACK ONLY AFTER commit in local DB
            boolean contentDeleted = dbManager.recordDeliveryAck(req.getMessageId(), session.getUserId());

            DeliveryAckResponse resp = new DeliveryAckResponse(req.getMessageId(), "ACKNOWLEDGED", contentDeleted);
            session.send(MessageEnvelope.createResponse(MessageType.DELIVERY_ACK_RESP, env.getRequestId(), req.getMessageId(), "OK", resp));

        } catch (SQLException e) {
            session.send(MessageEnvelope.createError(MessageType.DELIVERY_ACK_RESP, env.getRequestId(), "Database error recording ACK"));
        }
    }

    private void handlePendingSync(ClientSession session, MessageEnvelope env) throws IOException {
        if (!session.isAuthenticated()) {
            session.send(MessageEnvelope.createError(MessageType.ERROR_RESP, env.getRequestId(), "Unauthorized: login required"));
            return;
        }

        try {
            PendingSyncResponse pending = dbManager.getPendingDeliveriesForUser(session.getUserId(), session.getUsername());
            session.send(MessageEnvelope.createResponse(MessageType.PENDING_SYNC_RESP, env.getRequestId(), null, "OK", pending));

            // Also push individual messages
            for (DirectMessagePush dm : pending.getDirectMessages()) {
                session.send(MessageEnvelope.createRequest(MessageType.DIRECT_MSG_PUSH, dm.getMessageId(), dm));
                dbManager.recordDeliveryPushed(dm.getMessageId(), session.getUserId());
            }
            for (GroupMessagePush gm : pending.getGroupMessages()) {
                session.send(MessageEnvelope.createRequest(MessageType.GROUP_MSG_PUSH, gm.getMessageId(), gm));
                dbManager.recordDeliveryPushed(gm.getMessageId(), session.getUserId());
            }

        } catch (SQLException e) {
            session.send(MessageEnvelope.createError(MessageType.PENDING_SYNC_RESP, env.getRequestId(), "Failed to retrieve pending deliveries"));
        }
    }
}
