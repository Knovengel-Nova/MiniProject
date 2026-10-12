package com.spit.lanchat.common.dto;

import java.time.Instant;

public class DirectMessageResponse {
    private String messageId;
    private String recipientUsername;
    private String deliveryStatus; // PENDING, DELIVERED, FAILED
    private Double predictedRiskScore;
    private boolean flagged;
    private String modelStatus;
    private Instant timestamp;

    public DirectMessageResponse() {}

    public DirectMessageResponse(String messageId, String recipientUsername, String deliveryStatus,
                                 Double predictedRiskScore, boolean flagged, String modelStatus, Instant timestamp) {
        this.messageId = messageId;
        this.recipientUsername = recipientUsername;
        this.deliveryStatus = deliveryStatus;
        this.predictedRiskScore = predictedRiskScore;
        this.flagged = flagged;
        this.modelStatus = modelStatus;
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

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
