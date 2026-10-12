package com.spit.lanchat.server.session;

import com.spit.lanchat.common.codec.JsonFramer;
import com.spit.lanchat.common.protocol.MessageEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Socket;
import java.time.Instant;
import java.util.UUID;

/**
 * Represents an authenticated server-side session strictly bound to a client TCP connection.
 */
public class ClientSession {

    private static final Logger logger = LoggerFactory.getLogger(ClientSession.class);

    private final String sessionToken;
    private final Socket socket;
    private final JsonFramer framer;
    private final Instant connectedAt;

    private volatile int userId = -1;
    private volatile String username;
    private volatile boolean authenticated = false;
    private volatile Instant lastActive;

    public ClientSession(Socket socket, JsonFramer framer) {
        this.sessionToken = UUID.randomUUID().toString();
        this.socket = socket;
        this.framer = framer;
        this.connectedAt = Instant.now();
        this.lastActive = Instant.now();
    }

    public synchronized void authenticate(int userId, String username) {
        this.userId = userId;
        this.username = username;
        this.authenticated = true;
        this.lastActive = Instant.now();
    }

    public void touch() {
        this.lastActive = Instant.now();
    }

    public void send(MessageEnvelope envelope) throws IOException {
        touch();
        framer.writeMessage(envelope);
    }

    public void close() {
        try {
            framer.close();
        } catch (Exception ignored) {}
        try {
            socket.close();
        } catch (Exception ignored) {}
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public Socket getSocket() {
        return socket;
    }

    public JsonFramer getFramer() {
        return framer;
    }

    public int getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public Instant getConnectedAt() {
        return connectedAt;
    }

    public Instant getLastActive() {
        return lastActive;
    }
}
