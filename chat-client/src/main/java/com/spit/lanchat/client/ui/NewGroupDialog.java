package com.spit.lanchat.client.ui;

import com.spit.lanchat.client.net.ChatClientNetworkService;
import com.spit.lanchat.common.dto.GroupDto;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Dialog to create a new group conversation.
 */
public class NewGroupDialog extends JDialog {

    private final JTextField groupNameField = new JTextField(20);
    private final JTextField membersField = new JTextField(20);
    private final JLabel statusLabel = new JLabel(" ");
    private final ChatClientNetworkService networkService;
    private final Runnable onGroupCreated;

    public NewGroupDialog(Frame parent, ChatClientNetworkService networkService, Runnable onGroupCreated) {
        super(parent, "Create New Group", true);
        this.networkService = networkService;
        this.onGroupCreated = onGroupCreated;
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout());
        setResizable(false);

        JPanel panel = new JPanel(new GridLayout(3, 2, 8, 8));
        panel.setBorder(new EmptyBorder(16, 16, 10, 16));

        panel.add(new JLabel("Group Name:"));
        panel.add(groupNameField);

        panel.add(new JLabel("Members (comma-separated):"));
        panel.add(membersField);

        statusLabel.setForeground(Color.RED);
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        panel.add(statusLabel);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        JButton createButton = new JButton("Create Group");
        createButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
        JButton cancelButton = new JButton("Cancel");

        buttonPanel.add(cancelButton);
        buttonPanel.add(createButton);

        add(panel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        cancelButton.addActionListener(e -> dispose());
        createButton.addActionListener(e -> {
            String gName = groupNameField.getText().trim();
            if (gName.isEmpty()) {
                statusLabel.setText("Please enter a group name");
                return;
            }

            List<String> members = Arrays.stream(membersField.getText().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());

            createButton.setEnabled(false);
            new SwingWorker<GroupDto, Void>() {
                @Override
                protected GroupDto doInBackground() throws Exception {
                    return networkService.createGroup(gName, members);
                }

                @Override
                protected void done() {
                    createButton.setEnabled(true);
                    try {
                        get();
                        if (onGroupCreated != null) {
                            onGroupCreated.run();
                        }
                        dispose();
                    } catch (Exception ex) {
                        statusLabel.setText("Error: " + ex.getMessage());
                    }
                }
            }.execute();
        });

        pack();
        setLocationRelativeTo(getOwner());
    }
}
