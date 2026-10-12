package com.spit.lanchat.client.ui;

import com.spit.lanchat.client.db.LocalMessage;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Custom Swing component that renders a single message bubble.
 * Features outgoing vs incoming card styling, delivery checkmarks,
 * and high-visibility PDS risk warning badges when flagged.
 */
public class MessageBubblePanel extends JPanel {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("hh:mm a")
            .withZone(ZoneId.systemDefault());

    public MessageBubblePanel(LocalMessage msg, String currentUsername) {
        setLayout(new BorderLayout());
        setOpaque(false);
        setBorder(new EmptyBorder(4, 8, 4, 8));

        boolean isOutgoing = msg.isOutgoing() || (currentUsername != null && currentUsername.equalsIgnoreCase(msg.getSenderUsername()));

        JPanel bubbleContainer = new JPanel();
        bubbleContainer.setLayout(new BoxLayout(bubbleContainer, BoxLayout.Y_AXIS));
        bubbleContainer.setBorder(new EmptyBorder(8, 12, 8, 12));

        // Colors
        Color bgColor = isOutgoing ? new Color(225, 238, 255) : new Color(245, 245, 247);
        Color borderColor = isOutgoing ? new Color(175, 205, 245) : new Color(220, 220, 225);

        // Header: Sender & Time
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        headerPanel.setOpaque(false);

        JLabel senderLabel = new JLabel(isOutgoing ? "You" : msg.getSenderUsername());
        senderLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        senderLabel.setForeground(isOutgoing ? new Color(20, 80, 160) : new Color(60, 60, 60));

        String timeStr = msg.getTimestamp() != null ? TIME_FMT.format(msg.getTimestamp()) : "";
        JLabel timeLabel = new JLabel(timeStr);
        timeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        timeLabel.setForeground(Color.GRAY);

        headerPanel.add(senderLabel);
        headerPanel.add(timeLabel);
        bubbleContainer.add(headerPanel);
        bubbleContainer.add(Box.createVerticalStrut(4));

        // Message Text Area
        JTextArea textArea = new JTextArea(msg.getMessageText());
        textArea.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setEditable(false);
        textArea.setOpaque(false);
        textArea.setBorder(null);
        bubbleContainer.add(textArea);

        // PDS Risk Warning Badge (if flagged)
        if (msg.isFlagged()) {
            bubbleContainer.add(Box.createVerticalStrut(6));

            JPanel warningBanner = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
            warningBanner.setBackground(new Color(255, 243, 205)); // Accessible amber
            warningBanner.setBorder(BorderFactory.createLineBorder(new Color(255, 193, 7), 1));

            double score = msg.getPredictedRiskScore() != null ? msg.getPredictedRiskScore() : 0.0;
            JLabel warnIcon = new JLabel("⚠");
            warnIcon.setFont(new Font("Segoe UI", Font.BOLD, 14));
            warnIcon.setForeground(new Color(133, 100, 4));

            JLabel warnText = new JLabel(String.format("Risk: %.2f — Synthetic model risk indicator — review this message.", score));
            warnText.setFont(new Font("Segoe UI", Font.BOLD, 11));
            warnText.setForeground(new Color(133, 100, 4));

            warningBanner.add(warnIcon);
            warningBanner.add(warnText);
            bubbleContainer.add(warningBanner);
        }

        // Delivery Status (for outgoing)
        if (isOutgoing) {
            bubbleContainer.add(Box.createVerticalStrut(4));
            JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
            statusPanel.setOpaque(false);

            String statusText;
            String status = msg.getDeliveryStatus() != null ? msg.getDeliveryStatus().toUpperCase() : "PENDING";
            switch (status) {
                case "ACKNOWLEDGED":
                case "STORED":
                    statusText = "✓✓ Stored & Acknowledged";
                    break;
                case "DELIVERED":
                    statusText = "✓ Delivered";
                    break;
                default:
                    statusText = "🕒 Pending";
                    break;
            }

            JLabel statusLabel = new JLabel(statusText);
            statusLabel.setFont(new Font("Segoe UI", Font.ITALIC, 10));
            statusLabel.setForeground(new Color(80, 120, 160));
            statusPanel.add(statusLabel);
            bubbleContainer.add(statusPanel);
        }

        bubbleContainer.setBackground(bgColor);
        bubbleContainer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor, 1, true),
                new EmptyBorder(6, 10, 6, 10)
        ));

        // Horizontal alignment: right for outgoing, left for incoming
        JPanel alignmentPanel = new JPanel(new FlowLayout(isOutgoing ? FlowLayout.RIGHT : FlowLayout.LEFT, 0, 0));
        alignmentPanel.setOpaque(false);
        alignmentPanel.add(bubbleContainer);

        add(alignmentPanel, BorderLayout.CENTER);
    }
}
