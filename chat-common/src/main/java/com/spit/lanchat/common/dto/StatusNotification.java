package com.spit.lanchat.common.dto;

import java.time.Instant;

public class StatusNotification {
    private String messageId;
    private String recipientUsername;
    private String deliveryStatus; // PENDING, DELIVERED, ACKNOWLEDGED
    private Instant timestamp;

    public StatusNotification() {}

    public StatusNotification(String messageId, String recipientUsername, String deliveryStatus, Instant timestamp) {
        this.messageId = messageId;
        this.recipientUsername = recipientUsername;
        this.deliveryStatus = deliveryStatus;
        this.timestamp = timestamp != null ? timestamp : Instant.now();
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getRecipientUsername() {
        return recipientUsername;
    }

    public void setRecipientUsername(String recipientUsername) {
        this.recipientUsername = recipientUsername;
    }

    public String getDeliveryStatus() {
        return deliveryStatus;
    }

    public void setDeliveryStatus(String deliveryStatus) {
        this.deliveryStatus = deliveryStatus;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
