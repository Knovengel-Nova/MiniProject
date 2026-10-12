package com.spit.lanchat.client.ui;

import com.spit.lanchat.client.net.ChatClientNetworkService;
import com.spit.lanchat.common.dto.GroupDto;
import com.spit.lanchat.common.dto.GroupMemberResponse;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Dialog to view and manage members of an existing group.
 */
public class ManageGroupDialog extends JDialog {

    private final GroupDto group;
    private final ChatClientNetworkService networkService;
    private final Runnable onGroupUpdated;

    private final DefaultListModel<String> membersListModel = new DefaultListModel<>();
    private final JList<String> membersList = new JList<>(membersListModel);
    private final JTextField addMemberField = new JTextField(12);
    private final JLabel statusLabel = new JLabel(" ");

    public ManageGroupDialog(Frame parent, GroupDto group, ChatClientNetworkService networkService, Runnable onGroupUpdated) {
        super(parent, "Manage Group: " + group.getGroupName(), true);
        this.group = group;
        this.networkService = networkService;
        this.onGroupUpdated = onGroupUpdated;
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout());
        setResizable(false);

        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(new EmptyBorder(12, 16, 12, 16));

        // Group Header Info
        JPanel header = new JPanel(new GridLayout(2, 1));
        JLabel nameLabel = new JLabel(group.getGroupName());
        nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        JLabel creatorLabel = new JLabel("Created by: " + group.getCreatedBy());
        creatorLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        creatorLabel.setForeground(Color.GRAY);
        header.add(nameLabel);
        header.add(creatorLabel);
        panel.add(header, BorderLayout.NORTH);

        // Members List
        if (group.getMembers() != null) {
            for (String m : group.getMembers()) {
                membersListModel.addElement(m);
            }
        }
        membersList.setVisibleRowCount(6);
        JScrollPane scrollPane = new JScrollPane(membersList);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Current Members"));
        panel.add(scrollPane, BorderLayout.CENTER);

        // Member Controls
        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));

        JPanel addPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        addPanel.add(new JLabel("Add User:"));
        addPanel.add(addMemberField);
        JButton addButton = new JButton("Add");
        addPanel.add(addButton);
        controls.add(addPanel);

        JPanel removePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        JButton removeButton = new JButton("Remove Selected Member");
        removePanel.add(removeButton);
        controls.add(removePanel);

        statusLabel.setForeground(Color.RED);
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        controls.add(statusLabel);

        panel.add(controls, BorderLayout.SOUTH);
        add(panel, BorderLayout.CENTER);

        // Handlers
        addButton.addActionListener(e -> {
            String uName = addMemberField.getText().trim();
            if (uName.isEmpty()) return;

            addButton.setEnabled(false);
            new SwingWorker<GroupMemberResponse, Void>() {
                @Override
                protected GroupMemberResponse doInBackground() throws Exception {
                    return networkService.modifyGroupMember(group.getGroupId(), uName, "ADD");
                }

                @Override
                protected void done() {
                    addButton.setEnabled(true);
                    try {
                        GroupMemberResponse resp = get();
                        membersListModel.clear();
                        for (String m : resp.getCurrentMembers()) {
                            membersListModel.addElement(m);
                        }
                        group.setMembers(resp.getCurrentMembers());
                        addMemberField.setText("");
                        statusLabel.setText("Added " + uName);
                        if (onGroupUpdated != null) onGroupUpdated.run();
                    } catch (Exception ex) {
                        statusLabel.setText("Error: " + ex.getMessage());
                    }
                }
            }.execute();
        });

        removeButton.addActionListener(e -> {
            String selected = membersList.getSelectedValue();
            if (selected == null) {
                statusLabel.setText("Select a member to remove");
                return;
            }

            removeButton.setEnabled(false);
            new SwingWorker<GroupMemberResponse, Void>() {
                @Override
                protected GroupMemberResponse doInBackground() throws Exception {
                    return networkService.modifyGroupMember(group.getGroupId(), selected, "REMOVE");
                }

                @Override
                protected void done() {
                    removeButton.setEnabled(true);
                    try {
                        GroupMemberResponse resp = get();
                        membersListModel.clear();
                        for (String m : resp.getCurrentMembers()) {
                            membersListModel.addElement(m);
                        }
                        group.setMembers(resp.getCurrentMembers());
                        statusLabel.setText("Removed " + selected);
                        if (onGroupUpdated != null) onGroupUpdated.run();
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
