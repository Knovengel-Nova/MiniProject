package com.spit.lanchat.client.ui;

import com.spit.lanchat.client.db.ClientDatabaseManager;
import com.spit.lanchat.client.db.LocalMessage;
import com.spit.lanchat.client.net.ChatClientNetworkService;
import com.spit.lanchat.common.dto.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main Java Swing Chat Window for LAN Secure Chat.
 * Provides accessible, clean, dynamic messaging with local PostgreSQL persistence,
 * real-time contact presence, group broadcast, and PDS risk warning badges.
 */
public class MainChatFrame extends JFrame {

    private final ChatClientNetworkService networkService;
    private final ClientDatabaseManager dbManager;
    private final String currentUsername;

    // Active Conversation State
    private String activePartner = null; // username or groupId
    private String activeType = "DIRECT"; // "DIRECT" or "GROUP"
    private String activeDisplayName = "";

    // UI Components - Top Bar
    private final JLabel userBadgeLabel = new JLabel();
    private final JLabel connectionStatusLabel = new JLabel("🟢 Connected (TLS)");
    private final JButton reconnectButton = new JButton("Reconnect");
    private final JButton logoutButton = new JButton("Logout");

    // UI Components - Sidebar
    private final DefaultListModel<UserDto> userListModel = new DefaultListModel<>();
    private final JList<UserDto> userList = new JList<>(userListModel);

    private final DefaultListModel<GroupDto> groupListModel = new DefaultListModel<>();
    private final JList<GroupDto> groupList = new JList<>(groupListModel);

    private final JButton newGroupButton = new JButton("+ New Group");
    private final JButton manageGroupButton = new JButton("Manage Group");
    private final JButton refreshButton = new JButton("🔄 Refresh");

    // UI Components - Chat View
    private final JLabel chatHeaderTitle = new JLabel("Select a contact or group to start chatting");
    private final JLabel chatHeaderSubtitle = new JLabel("");
    private final JPanel messagesContainer = new JPanel();
    private final JScrollPane messageScrollPane = new JScrollPane(messagesContainer);
    private final JTextField messageInputField = new JTextField();
    private final JButton sendButton = new JButton("Send");

    // Cache of active message bubble components for live status updates
    private final Map<String, MessageBubblePanel> messageBubbles = new ConcurrentHashMap<>();

    public MainChatFrame(ChatClientNetworkService networkService, ClientDatabaseManager dbManager, String currentUsername) {
        super("LAN Secure Chat - " + currentUsername);
        this.networkService = networkService;
        this.dbManager = dbManager;
        this.currentUsername = currentUsername;

        initUI();
        setupNetworkListeners();
        loadInitialData();
    }

    private void initUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 700);
        setMinimumSize(new Dimension(800, 550));
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // 1. Top Header Bar
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(new Color(240, 243, 246));
        topBar.setBorder(new EmptyBorder(8, 16, 8, 16));

        JPanel topLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        topLeft.setOpaque(false);
        userBadgeLabel.setText("👤 " + currentUsername);
        userBadgeLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        topLeft.add(userBadgeLabel);

        connectionStatusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        connectionStatusLabel.setForeground(new Color(0, 140, 60));
        topLeft.add(connectionStatusLabel);
        topBar.add(topLeft, BorderLayout.WEST);

        JPanel topRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        topRight.setOpaque(false);
        reconnectButton.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        reconnectButton.setFocusPainted(false);
        logoutButton.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        logoutButton.setFocusPainted(false);
        topRight.add(reconnectButton);
        topRight.add(logoutButton);
        topBar.add(topRight, BorderLayout.EAST);

        add(topBar, BorderLayout.NORTH);

        // 2. Main Content Split Pane
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(280);
        splitPane.setContinuousLayout(true);

        // Left Sidebar: Contacts & Groups
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBorder(new EmptyBorder(8, 8, 8, 8));

        JPanel sidebarContent = new JPanel();
        sidebarContent.setLayout(new BoxLayout(sidebarContent, BoxLayout.Y_AXIS));

        // Direct Messages Section
        JLabel dmHeader = new JLabel("Direct Messages");
        dmHeader.setFont(new Font("Segoe UI", Font.BOLD, 12));
        dmHeader.setBorder(new EmptyBorder(4, 4, 4, 4));
        sidebarContent.add(dmHeader);

        userList.setCellRenderer(new UserListCellRenderer());
        userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane userScrollPane = new JScrollPane(userList);
        userScrollPane.setPreferredSize(new Dimension(250, 200));
        sidebarContent.add(userScrollPane);
        sidebarContent.add(Box.createVerticalStrut(10));

        // Groups Section
        JLabel groupHeader = new JLabel("Groups");
        groupHeader.setFont(new Font("Segoe UI", Font.BOLD, 12));
        groupHeader.setBorder(new EmptyBorder(4, 4, 4, 4));
        sidebarContent.add(groupHeader);

        groupList.setCellRenderer(new GroupListCellRenderer());
        groupList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane groupScrollPane = new JScrollPane(groupList);
        groupScrollPane.setPreferredSize(new Dimension(250, 180));
        sidebarContent.add(groupScrollPane);
        sidebarContent.add(Box.createVerticalStrut(8));

        // Group Action Buttons
        JPanel groupBtnPanel = new JPanel(new GridLayout(1, 2, 4, 4));
        newGroupButton.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        manageGroupButton.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        groupBtnPanel.add(newGroupButton);
        groupBtnPanel.add(manageGroupButton);
        sidebarContent.add(groupBtnPanel);
        sidebarContent.add(Box.createVerticalStrut(8));

        refreshButton.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        refreshButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        sidebarContent.add(refreshButton);

        sidebar.add(sidebarContent, BorderLayout.CENTER);
        splitPane.setLeftComponent(sidebar);

        // Right Panel: Active Chat View
        JPanel chatPanel = new JPanel(new BorderLayout());

        // Chat Header
        JPanel chatHeader = new JPanel(new BorderLayout());
        chatHeader.setBackground(Color.WHITE);
        chatHeader.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(220, 220, 225)),
                new EmptyBorder(10, 16, 10, 16)
        ));

        chatHeaderTitle.setFont(new Font("Segoe UI", Font.BOLD, 15));
        chatHeaderSubtitle.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        chatHeaderSubtitle.setForeground(Color.GRAY);

        JPanel chatTitleBox = new JPanel(new GridLayout(2, 1));
        chatTitleBox.setOpaque(false);
        chatTitleBox.add(chatHeaderTitle);
        chatTitleBox.add(chatHeaderSubtitle);
        chatHeader.add(chatTitleBox, BorderLayout.WEST);
        chatPanel.add(chatHeader, BorderLayout.NORTH);

        // Messages Scroll Area
        messagesContainer.setLayout(new BoxLayout(messagesContainer, BoxLayout.Y_AXIS));
        messagesContainer.setBackground(Color.WHITE);
        messageScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        messageScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        messageScrollPane.setBorder(null);
        chatPanel.add(messageScrollPane, BorderLayout.CENTER);

        // Composer Bottom Bar
        JPanel composerPanel = new JPanel(new BorderLayout(8, 0));
        composerPanel.setBackground(new Color(245, 247, 250));
        composerPanel.setBorder(new EmptyBorder(10, 16, 10, 16));

        messageInputField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        messageInputField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 205, 215), 1, true),
                new EmptyBorder(6, 10, 6, 10)
        ));

        sendButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
        sendButton.setBackground(new Color(33, 115, 222));
        sendButton.setForeground(Color.WHITE);
        sendButton.setFocusPainted(false);
        sendButton.setEnabled(false);

        composerPanel.add(messageInputField, BorderLayout.CENTER);
        composerPanel.add(sendButton, BorderLayout.EAST);
        chatPanel.add(composerPanel, BorderLayout.SOUTH);

        splitPane.setRightComponent(chatPanel);
        add(splitPane, BorderLayout.CENTER);

        // Event Handlers
        userList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                UserDto selected = userList.getSelectedValue();
                if (selected != null) {
                    groupList.clearSelection();
                    selectConversation(selected.getUsername(), "DIRECT", selected.getUsername(),
                            selected.getStatus() != null ? selected.getStatus() : "offline");
                }
            }
        });

        groupList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                GroupDto selected = groupList.getSelectedValue();
                if (selected != null) {
                    userList.clearSelection();
                    int memberCount = selected.getMembers() != null ? selected.getMembers().size() : 0;
                    selectConversation(selected.getGroupId(), "GROUP", selected.getGroupName(),
                            memberCount + " members: " + String.join(", ", selected.getMembers()));
                }
            }
        });

        sendButton.addActionListener(e -> sendMessage());
        messageInputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    sendMessage();
                }
            }
        });

        newGroupButton.addActionListener(e -> {
            NewGroupDialog dlg = new NewGroupDialog(this, networkService, this::loadInitialData);
            dlg.setVisible(true);
        });

        manageGroupButton.addActionListener(e -> {
            GroupDto selected = groupList.getSelectedValue();
            if (selected == null) {
                JOptionPane.showMessageDialog(this, "Please select a group first.", "Manage Group", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            ManageGroupDialog dlg = new ManageGroupDialog(this, selected, networkService, this::loadInitialData);
            dlg.setVisible(true);
        });

        refreshButton.addActionListener(e -> loadInitialData());

        logoutButton.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to log out?", "Logout", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                networkService.logout();
                networkService.disconnect();
                dispose();
                // Return to login
                SwingUtilities.invokeLater(() -> {
                    LoginDialog loginDlg = new LoginDialog(null);
                    loginDlg.setVisible(true);
                    if (loginDlg.isAuthenticated()) {
                        MainChatFrame frame = new MainChatFrame(loginDlg.getNetworkService(), loginDlg.getDbManager(), loginDlg.getUsername());
                        frame.setVisible(true);
                    }
                });
            }
        });

        reconnectButton.addActionListener(e -> {
            reconnectButton.setEnabled(false);
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    if (!networkService.isConnected()) {
                        networkService.connect("127.0.0.1", 8443, true, "certs/client-truststore.p12", "changeit");
                    }
                    networkService.syncPendingMessages();
                    return null;
                }

                @Override
                protected void done() {
                    reconnectButton.setEnabled(true);
                    loadInitialData();
                }
            }.execute();
        });
    }

    private void selectConversation(String partnerId, String type, String displayName, String subtitle) {
        this.activePartner = partnerId;
        this.activeType = type;
        this.activeDisplayName = displayName;

        chatHeaderTitle.setText(displayName);
        chatHeaderSubtitle.setText(subtitle);
        sendButton.setEnabled(true);

        // Load conversation history from local PostgreSQL message store
        loadHistoryFromLocalDb();
    }

    private void loadHistoryFromLocalDb() {
        if (activePartner == null || dbManager == null) return;

        messagesContainer.removeAll();
        messageBubbles.clear();

        new SwingWorker<List<LocalMessage>, Void>() {
            @Override
            protected List<LocalMessage> doInBackground() throws Exception {
                return dbManager.getConversationHistory(activePartner);
            }

            @Override
            protected void done() {
                try {
                    List<LocalMessage> history = get();
                    for (LocalMessage msg : history) {
                        appendMessageBubble(msg);
                    }
                    scrollToBottom();
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(MainChatFrame.this,
                            "Failed to load local chat history: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void appendMessageBubble(LocalMessage msg) {
        MessageBubblePanel bubble = new MessageBubblePanel(msg, currentUsername);
        messageBubbles.put(msg.getMessageId(), bubble);
        messagesContainer.add(bubble);
        messagesContainer.revalidate();
        messagesContainer.repaint();
    }

    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollBar vertical = messageScrollPane.getVerticalScrollBar();
            vertical.setValue(vertical.getMaximum());
        });
    }

    private void sendMessage() {
        String text = messageInputField.getText().trim();
        if (text.isEmpty() || activePartner == null) return;

        messageInputField.setText("");
        sendButton.setEnabled(false);

        String partner = activePartner;
        String type = activeType;
        String groupName = activeDisplayName;

        new SwingWorker<Void, Void>() {
            private LocalMessage sentLocalMsg;

            @Override
            protected Void doInBackground() throws Exception {
                if ("DIRECT".equals(type)) {
                    DirectMessageResponse resp = networkService.sendDirectMessage(partner, text);
                    sentLocalMsg = new LocalMessage(
                            resp.getMessageId(),
                            "DIRECT",
                            partner,
                            null,
                            currentUsername,
                            true,
                            text,
                            resp.getPredictedRiskScore(),
                            resp.isFlagged(),
                            resp.getModelStatus(),
                            resp.getDeliveryStatus(),
                            resp.getTimestamp(),
                            Instant.now()
                    );
                } else {
                    GroupMessageResponse resp = networkService.sendGroupMessage(partner, groupName, text);
                    sentLocalMsg = new LocalMessage(
                            resp.getMessageId(),
                            "GROUP",
                            partner,
                            groupName,
                            currentUsername,
                            true,
                            text,
                            resp.getPredictedRiskScore(),
                            resp.isFlagged(),
                            resp.getModelStatus(),
                            resp.getDeliveryStatus(),
                            resp.getTimestamp(),
                            Instant.now()
                    );
                }
                return null;
            }

            @Override
            protected void done() {
                sendButton.setEnabled(true);
                try {
                    get();
                    if (sentLocalMsg != null && partner.equals(activePartner)) {
                        appendMessageBubble(sentLocalMsg);
                        scrollToBottom();
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(MainChatFrame.this,
                            "Failed to send message: " + ex.getMessage(), "Send Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void setupNetworkListeners() {
        networkService.setOnDirectMessageReceived(push -> {
            SwingUtilities.invokeLater(() -> {
                if ("DIRECT".equals(activeType) && push.getSenderUsername().equalsIgnoreCase(activePartner)) {
                    LocalMessage local = new LocalMessage(
                            push.getMessageId(),
                            "DIRECT",
                            push.getSenderUsername(),
                            null,
                            push.getSenderUsername(),
                            false,
                            push.getMessageText(),
                            push.getPredictedRiskScore(),
                            push.isFlagged(),
                            push.getModelStatus(),
                            "STORED",
                            push.getTimestamp(),
                            Instant.now()
                    );
                    appendMessageBubble(local);
                    scrollToBottom();
                } else {
                    // Update user unread or notification
                    loadInitialData();
                }
            });
        });

        networkService.setOnGroupMessageReceived(push -> {
            SwingUtilities.invokeLater(() -> {
                if ("GROUP".equals(activeType) && push.getGroupId().equalsIgnoreCase(activePartner)) {
                    LocalMessage local = new LocalMessage(
                            push.getMessageId(),
                            "GROUP",
                            push.getGroupId(),
                            push.getGroupName(),
                            push.getSenderUsername(),
                            push.getSenderUsername().equalsIgnoreCase(currentUsername),
                            push.getMessageText(),
                            push.getPredictedRiskScore(),
                            push.isFlagged(),
                            push.getModelStatus(),
                            "STORED",
                            push.getTimestamp(),
                            Instant.now()
                    );
                    appendMessageBubble(local);
                    scrollToBottom();
                } else {
                    loadInitialData();
                }
            });
        });

        networkService.setOnPresenceChanged(notif -> {
            SwingUtilities.invokeLater(() -> {
                for (int i = 0; i < userListModel.size(); i++) {
                    UserDto u = userListModel.get(i);
                    if (u.getUsername().equalsIgnoreCase(notif.getUsername())) {
                        u.setStatus(notif.getStatus());
                        userListModel.set(i, u);
                        break;
                    }
                }
                if (activePartner != null && activePartner.equalsIgnoreCase(notif.getUsername())) {
                    chatHeaderSubtitle.setText(notif.getStatus());
                }
            });
        });

        networkService.setOnStatusUpdate(statusNotif -> {
            SwingUtilities.invokeLater(() -> {
                // Refresh active conversation if status matches message
                if (messageBubbles.containsKey(statusNotif.getMessageId())) {
                    loadHistoryFromLocalDb();
                }
            });
        });

        networkService.setOnConnectionStateChanged(isConnected -> {
            SwingUtilities.invokeLater(() -> {
                if (isConnected) {
                    connectionStatusLabel.setText("🟢 Connected (TLS)");
                    connectionStatusLabel.setForeground(new Color(0, 140, 60));
                } else {
                    connectionStatusLabel.setText("🔴 Disconnected");
                    connectionStatusLabel.setForeground(Color.RED);
                }
            });
        });
    }

    private void loadInitialData() {
        new SwingWorker<Void, Void>() {
            private List<UserDto> users;
            private List<GroupDto> groups;

            @Override
            protected Void doInBackground() throws Exception {
                users = networkService.getUserList();
                groups = networkService.getGroupList();
                // Synchronize any pending offline messages
                networkService.syncPendingMessages();
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    userListModel.clear();
                    if (users != null) {
                        for (UserDto u : users) {
                            if (!u.getUsername().equalsIgnoreCase(currentUsername)) {
                                userListModel.addElement(u);
                            }
                        }
                    }

                    groupListModel.clear();
                    if (groups != null) {
                        for (GroupDto g : groups) {
                            groupListModel.addElement(g);
                        }
                    }
                } catch (Exception e) {
                    connectionStatusLabel.setText("🔴 Disconnected");
                    connectionStatusLabel.setForeground(Color.RED);
                }
            }
        }.execute();
    }

    // List Cell Renderers
    private static class UserListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof UserDto) {
                UserDto u = (UserDto) value;
                boolean isOnline = "online".equalsIgnoreCase(u.getStatus());
                String statusDot = isOnline ? "🟢" : "⚪";
                setText(statusDot + "  " + u.getUsername());
                setFont(new Font("Segoe UI", isOnline ? Font.BOLD : Font.PLAIN, 12));
                setBorder(new EmptyBorder(4, 6, 4, 6));
            }
            return this;
        }
    }

    private static class GroupListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof GroupDto) {
                GroupDto g = (GroupDto) value;
                setText("👥 " + g.getGroupName());
                setFont(new Font("Segoe UI", Font.PLAIN, 12));
                setBorder(new EmptyBorder(4, 6, 4, 6));
            }
            return this;
        }
    }
}
