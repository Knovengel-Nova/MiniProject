package com.spit.lanchat.common.dto;

import java.time.Instant;

public class DirectMessagePush {
    private String messageId;
    private String senderUsername;
    private String recipientUsername;
    private String messageText;
    private Instant timestamp;
    private Double predictedRiskScore;
    private boolean flagged;
    private String modelStatus;

    public DirectMessagePush() {}

    public DirectMessagePush(String messageId, String senderUsername, String recipientUsername,
                             String messageText, Instant timestamp, Double predictedRiskScore,
                             boolean flagged, String modelStatus) {
        this.messageId = messageId;
        this.senderUsername = senderUsername;
        this.recipientUsername = recipientUsername;
        this.messageText = messageText;
        this.timestamp = timestamp != null ? timestamp : Instant.now();
        this.predictedRiskScore = predictedRiskScore;
        this.flagged = flagged;
        this.modelStatus = modelStatus;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getSenderUsername() {
        return senderUsername;
    }

    public void setSenderUsername(String senderUsername) {
        this.senderUsername = senderUsername;
    }

    public String getRecipientUsername() {
        return recipientUsername;
    }

    public void setRecipientUsername(String recipientUsername) {
        this.recipientUsername = recipientUsername;
    }

    public String getMessageText() {
        return messageText;
    }

    public void setMessageText(String messageText) {
        this.messageText = messageText;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
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
}
