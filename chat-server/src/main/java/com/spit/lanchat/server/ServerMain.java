package com.spit.lanchat.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for starting the LAN Secure Chat Application Server.
 */
public class ServerMain {

    private static final Logger logger = LoggerFactory.getLogger(ServerMain.class);

    public static void main(String[] args) {
        logger.info("=================================================");
        logger.info("   LAN SECURE CHATTING APPLICATION SERVER        ");
        logger.info("   TE Computer Engineering Mini Project          ");
        logger.info("=================================================");

        ServerConfig config = ServerConfig.load();
        ChatServer server = new ChatServer(config);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("JVM shutdown requested. Stopping server...");
            server.stop();
        }));

        try {
            server.start();
            logger.info("Server is running. Press Ctrl+C to terminate.");
            // Keep main thread alive
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            logger.info("Server main thread interrupted.");
        } catch (Exception e) {
            logger.error("Failed to start ChatServer: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
