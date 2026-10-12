package com.spit.lanchat.client;

import com.spit.lanchat.client.ui.LoginDialog;
import com.spit.lanchat.client.ui.MainChatFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;

/**
 * Entry point for the Java Swing Desktop Client.
 */
public class ClientMain {

    private static final Logger logger = LoggerFactory.getLogger(ClientMain.class);

    public static void main(String[] args) {
        // Set System Look and Feel for modern native desktop appearance
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            logger.debug("System LookAndFeel not available, using default");
        }

        SwingUtilities.invokeLater(() -> {
            LoginDialog dialog = new LoginDialog(null);
            dialog.setVisible(true);

            if (dialog.isAuthenticated()) {
                logger.info("User '{}' authenticated successfully. Opening MainChatFrame.", dialog.getUsername());
                MainChatFrame mainFrame = new MainChatFrame(
                        dialog.getNetworkService(),
                        dialog.getDbManager(),
                        dialog.getUsername()
                );
                mainFrame.setVisible(true);
            } else {
                logger.info("Authentication canceled or unsuccessful. Exiting client.");
                System.exit(0);
            }
        });
    }
}
