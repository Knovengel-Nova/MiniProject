package com.spit.lanchat.client.ui;

import com.spit.lanchat.client.db.ClientDatabaseManager;
import com.spit.lanchat.client.net.ChatClientNetworkService;
import com.spit.lanchat.common.dto.LoginResponse;
import com.spit.lanchat.common.dto.RegisterResponse;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.concurrent.ExecutionException;

/**
 * Swing Login and Registration dialog.
 * Handles server connection setup, local DB connection setup,
 * and user authentication without blocking the Event Dispatch Thread.
 */
public class LoginDialog extends JDialog {

    private final JTextField serverHostField = new JTextField("127.0.0.1", 15);
    private final JTextField serverPortField = new JTextField("8443", 6);
    private final JCheckBox tlsCheckBox = new JCheckBox("Enable TLS (Recommended)", true);

    private final JTextField localDbUrlField = new JTextField("jdbc:postgresql://127.0.0.1:5432/lan_chat_client_test", 24);
    private final JTextField localDbUserField = new JTextField("postgres", 10);
    private final JPasswordField localDbPassField = new JPasswordField("postgres", 10);

    private final JTextField usernameField = new JTextField(15);
    private final JPasswordField passwordField = new JPasswordField(15);

    private final JLabel statusLabel = new JLabel("Please enter credentials to continue.");
    private final JButton loginButton = new JButton("Login");
    private final JButton registerButton = new JButton("Register New Account");

    private ChatClientNetworkService networkService;
    private ClientDatabaseManager dbManager;
    private boolean authenticated = false;

    public LoginDialog(Frame owner) {
        super(owner, "LAN Secure Chat - Authentication", true);
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout());
        setResizable(false);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(new EmptyBorder(16, 20, 16, 20));

        // Title Header
        JLabel titleLabel = new JLabel("LAN Secure Chat");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subtitleLabel = new JLabel("Self-Hosted Academic Messaging Platform");
        subtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subtitleLabel.setForeground(Color.GRAY);
        subtitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        mainPanel.add(titleLabel);
        mainPanel.add(subtitleLabel);
        mainPanel.add(Box.createVerticalStrut(15));

        // Network Settings Box
        JPanel netPanel = new JPanel(new GridLayout(2, 2, 8, 8));
        netPanel.setBorder(BorderFactory.createTitledBorder("Server Connection (LAN)"));
        netPanel.add(new JLabel("Server Host:"));
        netPanel.add(serverHostField);
        netPanel.add(new JLabel("Server Port:"));
        JPanel portBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        portBox.add(serverPortField);
        portBox.add(Box.createHorizontalStrut(8));
        portBox.add(tlsCheckBox);
        netPanel.add(portBox);
        mainPanel.add(netPanel);
        mainPanel.add(Box.createVerticalStrut(10));

        // Local DB Settings Box
        JPanel dbPanel = new JPanel(new GridLayout(3, 2, 8, 8));
        dbPanel.setBorder(BorderFactory.createTitledBorder("Local PostgreSQL Store"));
        dbPanel.add(new JLabel("Database URL:"));
        dbPanel.add(localDbUrlField);
        dbPanel.add(new JLabel("DB Username:"));
        dbPanel.add(localDbUserField);
        dbPanel.add(new JLabel("DB Password:"));
        dbPanel.add(localDbPassField);
        mainPanel.add(dbPanel);
        mainPanel.add(Box.createVerticalStrut(10));

        // Credentials Box
        JPanel credPanel = new JPanel(new GridLayout(2, 2, 8, 8));
        credPanel.setBorder(BorderFactory.createTitledBorder("User Credentials"));
        credPanel.add(new JLabel("Username:"));
        credPanel.add(usernameField);
        credPanel.add(new JLabel("Password:"));
        credPanel.add(passwordField);
        mainPanel.add(credPanel);
        mainPanel.add(Box.createVerticalStrut(12));

        // Status Feedback
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusLabel.setForeground(new Color(0, 102, 204));
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(statusLabel);
        mainPanel.add(Box.createVerticalStrut(12));

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        loginButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
        loginButton.setBackground(new Color(33, 115, 222));
        loginButton.setForeground(Color.WHITE);
        loginButton.setFocusPainted(false);

        registerButton.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        registerButton.setFocusPainted(false);

        buttonPanel.add(loginButton);
        buttonPanel.add(registerButton);
        mainPanel.add(buttonPanel);

        add(mainPanel, BorderLayout.CENTER);

        // Action Handlers
        loginButton.addActionListener(e -> executeAuth(false));
        registerButton.addActionListener(e -> executeAuth(true));

        pack();
        setLocationRelativeTo(getOwner());
    }

    private void executeAuth(boolean isRegister) {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());

        if (username.isEmpty() || password.isEmpty()) {
            statusLabel.setForeground(Color.RED);
            statusLabel.setText("Please enter both username and password.");
            return;
        }

        loginButton.setEnabled(false);
        registerButton.setEnabled(false);
        statusLabel.setForeground(new Color(0, 102, 204));
        statusLabel.setText(isRegister ? "Registering account..." : "Connecting and authenticating...");

        // Perform off the Event Dispatch Thread
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            private String errorMsg = null;
            private String successMsg = null;

            @Override
            protected Void doInBackground() {
                try {
                    String host = serverHostField.getText().trim();
                    int port = Integer.parseInt(serverPortField.getText().trim());
                    boolean tls = tlsCheckBox.isSelected();

                    String dbUrl = localDbUrlField.getText().trim();
                    String dbUser = localDbUserField.getText().trim();
                    String dbPass = new String(localDbPassField.getPassword());

                    // 1. Initialize local DB
                    dbManager = new ClientDatabaseManager(dbUrl, dbUser, dbPass);
                    dbManager.initSchema();

                    // 2. Connect to Server
                    networkService = new ChatClientNetworkService(dbManager);
                    networkService.connect(host, port, tls, "certs/client-truststore.p12", "changeit");

                    if (isRegister) {
                        RegisterResponse resp = networkService.register(username, password);
                        successMsg = "Registration successful! You can now log in.";
                    } else {
                        LoginResponse resp = networkService.login(username, password);
                        authenticated = true;
                    }

                } catch (ExecutionException e) {
                    errorMsg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                } catch (Exception e) {
                    errorMsg = e.getMessage();
                }
                return null;
            }

            @Override
            protected void done() {
                loginButton.setEnabled(true);
                registerButton.setEnabled(true);

                if (errorMsg != null) {
                    statusLabel.setForeground(Color.RED);
                    statusLabel.setText("Error: " + errorMsg);
                } else if (isRegister) {
                    statusLabel.setForeground(new Color(0, 153, 76));
                    statusLabel.setText(successMsg);
                } else if (authenticated) {
                    dispose(); // Close dialog on successful login
                }
            }
        };

        worker.execute();
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public ChatClientNetworkService getNetworkService() {
        return networkService;
    }

    public ClientDatabaseManager getDbManager() {
        return dbManager;
    }

    public String getUsername() {
        return usernameField.getText().trim();
    }
}
