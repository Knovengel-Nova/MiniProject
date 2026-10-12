package com.spit.lanchat.client.db;

import java.time.Instant;

/**
 * Model class for locally stored messages in the client's local PostgreSQL database.
 */
public class LocalMessage {

    private String messageId;
    private String messageType; // "DIRECT" or "GROUP"
    private String conversationPartner; // username for direct, groupId for group
    private String groupName;
    private String senderUsername;
    private boolean outgoing;
    private String messageText;
    private Double predictedRiskScore;
    private boolean flagged;
    private String modelStatus;
    private String deliveryStatus;
    private Instant timestamp;
    private Instant storedAt;

    public LocalMessage() {}

    public LocalMessage(String messageId, String messageType, String conversationPartner,
                        String groupName, String senderUsername, boolean outgoing,
                        String messageText, Double predictedRiskScore, boolean flagged,
                        String modelStatus, String deliveryStatus, Instant timestamp, Instant storedAt) {
        this.messageId = messageId;
        this.messageType = messageType;
        this.conversationPartner = conversationPartner;
        this.groupName = groupName;
        this.senderUsername = senderUsername;
        this.outgoing = outgoing;
        this.messageText = messageText;
        this.predictedRiskScore = predictedRiskScore;
        this.flagged = flagged;
        this.modelStatus = modelStatus;
        this.deliveryStatus = deliveryStatus;
        this.timestamp = timestamp != null ? timestamp : Instant.now();
        this.storedAt = storedAt != null ? storedAt : Instant.now();
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public String getConversationPartner() {
        return conversationPartner;
    }

    public void setConversationPartner(String conversationPartner) {
        this.conversationPartner = conversationPartner;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String getSenderUsername() {
        return senderUsername;
    }

    public void setSenderUsername(String senderUsername) {
        this.senderUsername = senderUsername;
    }

    public boolean isOutgoing() {
        return outgoing;
    }

    public void setOutgoing(boolean outgoing) {
        this.outgoing = outgoing;
    }

    public String getMessageText() {
        return messageText;
    }

    public void setMessageText(String messageText) {
        this.messageText = messageText;
    }

    public Double getPredictedRiskScore() {
        return predictedRiskScore;
    }

    public void setPredictedRiskScore(Double predictedRiskScore) {
        this.predictedRiskScore = predictedRiskScore;
    }

    public boolean isFlagged() {
        return flagged;
    }

    public void setFlagged(boolean flagged) {
        this.flagged = flagged;
    }

    public String getModelStatus() {
        return modelStatus;
    }

    public void setModelStatus(String modelStatus) {
        this.modelStatus = modelStatus;
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

    public Instant getStoredAt() {
        return storedAt;
    }

    public void setStoredAt(Instant storedAt) {
        this.storedAt = storedAt;
    }
}
