package com.spit.lanchat.server.session;

import com.spit.lanchat.common.dto.PresenceNotification;
import com.spit.lanchat.common.protocol.MessageEnvelope;
import com.spit.lanchat.common.protocol.MessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages active connected and authenticated client sessions.
 * Coordinates real-time routing and presence notification broadcasts.
 */
public class SessionManager {

    private static final Logger logger = LoggerFactory.getLogger(SessionManager.class);

    private final Set<ClientSession> activeSessions = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<Integer, ClientSession> sessionsByUserId = new ConcurrentHashMap<>();
    private final Map<String, ClientSession> sessionsByUsername = new ConcurrentHashMap<>();

    public void registerSession(ClientSession session) {
        activeSessions.add(session);
    }

    public synchronized void bindUser(ClientSession session, int userId, String username) {
        session.authenticate(userId, username);
        sessionsByUserId.put(userId, session);
        sessionsByUsername.put(username.toLowerCase(), session);
        logger.info("Bound session {} to user '{}' (ID: {})", session.getSessionToken(), username, userId);

        broadcastPresence(userId, username, "online");
    }

    public synchronized void unbindUser(ClientSession session) {
        if (session.isAuthenticated()) {
            int uId = session.getUserId();
            String uName = session.getUsername();
            sessionsByUserId.remove(uId, session);
            if (uName != null) {
                sessionsByUsername.remove(uName.toLowerCase(), session);
                broadcastPresence(uId, uName, "offline");
            }
            logger.info("Unbound session for user '{}' (ID: {})", uName, uId);
        }
    }

    public void removeSession(ClientSession session) {
        unbindUser(session);
        activeSessions.remove(session);
        session.close();
    }

    public ClientSession getSessionByUserId(int userId) {
        return sessionsByUserId.get(userId);
    }

    public ClientSession getSessionByUsername(String username) {
        if (username == null) return null;
        return sessionsByUsername.get(username.trim().toLowerCase());
    }

    public boolean isUserOnline(String username) {
        return getSessionByUsername(username) != null;
    }

    public boolean isUserOnline(int userId) {
        return getSessionByUserId(userId) != null;
    }

    public void broadcastToAllAuthenticated(MessageEnvelope envelope) {
        for (ClientSession session : sessionsByUserId.values()) {
            try {
                session.send(envelope);
            } catch (IOException e) {
                logger.warn("Failed to broadcast message to user '{}': {}", session.getUsername(), e.getMessage());
            }
        }
    }

    public void broadcastPresence(int userId, String username, String status) {
        PresenceNotification notif = new PresenceNotification(userId, username, status, Instant.now());
        MessageEnvelope env = MessageEnvelope.createRequest(MessageType.PRESENCE_NOTIF, notif);
        broadcastToAllAuthenticated(env);
    }

    public int getActiveCount() {
        return activeSessions.size();
    }

    public int getAuthenticatedCount() {
        return sessionsByUserId.size();
    }
}
