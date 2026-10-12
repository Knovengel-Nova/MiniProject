package com.spit.lanchat.server;

import com.spit.lanchat.common.codec.JsonFramer;
import com.spit.lanchat.common.protocol.MessageEnvelope;
import com.spit.lanchat.common.security.TlsUtil;
import com.spit.lanchat.server.db.ServerDatabaseManager;
import com.spit.lanchat.server.pds.PDSService;
import com.spit.lanchat.server.router.MessageRouter;
import com.spit.lanchat.server.session.ClientSession;
import com.spit.lanchat.server.session.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Main LAN Secure Chat Application Server.
 * Sits on a TLS-protected TCP socket, managing sessions, DB persistence, and routing.
 */
public class ChatServer {

    private static final Logger logger = LoggerFactory.getLogger(ChatServer.class);

    private final ServerConfig config;
    private final ServerDatabaseManager dbManager;
    private final SessionManager sessionManager;
    private final PDSService pdsService;
    private final MessageRouter router;

    private final ExecutorService threadPool = Executors.newCachedThreadPool();
    private volatile boolean running = false;
    private ServerSocket serverSocket;

    public ChatServer(ServerConfig config) {
        this.config = config;
        this.dbManager = new ServerDatabaseManager(config.getDbUrl(), config.getDbUser(), config.getDbPassword());
        this.sessionManager = new SessionManager();
        this.pdsService = new PDSService(config.getPdsEndpoint(), config.getPdsTimeoutMs());
        this.router = new MessageRouter(dbManager, sessionManager, pdsService);
    }

    public synchronized void start() throws Exception {
        if (running) {
            return;
        }

        // Verify DB connectivity
        try (var conn = dbManager.getConnection()) {
            logger.info("Successfully connected to PostgreSQL server database at: {}", config.getDbUrl());
        } catch (SQLException e) {
            logger.error("Failed to connect to PostgreSQL server database: {}", e.getMessage());
            throw e;
        }

        if (config.isTlsEnabled()) {
            logger.info("Initializing TLS ServerSocket with keystore: {}", config.getKeystorePath());
            SSLContext sslContext = TlsUtil.createServerSSLContext(config.getKeystorePath(), config.getKeystorePassword());
            SSLServerSocketFactory ssf = sslContext.getServerSocketFactory();
            serverSocket = ssf.createServerSocket(config.getPort());
            ((SSLServerSocket) serverSocket).setNeedClientAuth(false);
            logger.info("TLS-protected TCP Server listening on port {}", config.getPort());
        } else {
            logger.warn("STARTING IN PLAINTEXT TCP MODE (TLS Disabled) on port {}", config.getPort());
            serverSocket = new ServerSocket(config.getPort());
        }

        running = true;

        threadPool.submit(this::acceptLoop);
    }

    private void acceptLoop() {
        while (running && !serverSocket.isClosed()) {
            try {
                Socket clientSocket = serverSocket.accept();
                logger.info("Accepted connection from: {}", clientSocket.getRemoteSocketAddress());
                threadPool.submit(() -> handleClientSocket(clientSocket));
            } catch (IOException e) {
                if (running) {
                    logger.warn("Exception in accept loop: {}", e.getMessage());
                }
            }
        }
    }

    private void handleClientSocket(Socket socket) {
        ClientSession session = null;
        try {
            JsonFramer framer = new JsonFramer(socket.getInputStream(), socket.getOutputStream());
            session = new ClientSession(socket, framer);
            sessionManager.registerSession(session);

            while (running && !socket.isClosed()) {
                MessageEnvelope env = framer.readMessage();
                if (env == null) {
                    break; // EOF disconnect
                }
                router.handleMessage(session, env);
            }

        } catch (Exception e) {
            logger.debug("Client socket closed or errored: {}", e.getMessage());
        } finally {
            if (session != null) {
                if (session.isAuthenticated()) {
                    try {
                        dbManager.updateUserStatus(session.getUserId(), "offline");
                    } catch (SQLException ignored) {}
                }
                sessionManager.removeSession(session);
            }
            try {
                socket.close();
            } catch (Exception ignored) {}
        }
    }

    public synchronized void stop() {
        if (!running) return;
        running = false;
        logger.info("Shutting down ChatServer...");

        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException ignored) {}

        threadPool.shutdownNow();
        try {
            threadPool.awaitTermination(3, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {}

        logger.info("ChatServer stopped.");
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return serverSocket != null ? serverSocket.getLocalPort() : config.getPort();
    }

    public ServerDatabaseManager getDbManager() {
        return dbManager;
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    public PDSService getPdsService() {
        return pdsService;
    }

    public MessageRouter getRouter() {
        return router;
    }
}
