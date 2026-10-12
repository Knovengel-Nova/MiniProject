package com.spit.lanchat.common.dto;

import java.time.Instant;

public class DeliveryAckRequest {
    private String messageId;
    private String recipientUsername;
    private Instant storedTimestamp;

    public DeliveryAckRequest() {}

    public DeliveryAckRequest(String messageId, String recipientUsername, Instant storedTimestamp) {
        this.messageId = messageId;
        this.recipientUsername = recipientUsername;
        this.storedTimestamp = storedTimestamp != null ? storedTimestamp : Instant.now();
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

    public Instant getStoredTimestamp() {
        return storedTimestamp;
    }

    public void setStoredTimestamp(Instant storedTimestamp) {
        this.storedTimestamp = storedTimestamp;
    }
}
