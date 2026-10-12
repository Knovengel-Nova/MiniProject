package com.spit.lanchat.common.dto;

import java.time.Instant;

public class GroupMessageResponse {
    private String messageId;
    private String groupId;
    private String deliveryStatus;
    private int intendedRecipientsCount;
    private Double predictedRiskScore;
    private boolean flagged;
    private String modelStatus;
    private Instant timestamp;

    public GroupMessageResponse() {}

    public GroupMessageResponse(String messageId, String groupId, String deliveryStatus,
                                int intendedRecipientsCount, Double predictedRiskScore,
                                boolean flagged, String modelStatus, Instant timestamp) {
        this.messageId = messageId;
        this.groupId = groupId;
        this.deliveryStatus = deliveryStatus;
        this.intendedRecipientsCount = intendedRecipientsCount;
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

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getDeliveryStatus() {
        return deliveryStatus;
    }

    public void setDeliveryStatus(String deliveryStatus) {
        this.deliveryStatus = deliveryStatus;
    }

    public int getIntendedRecipientsCount() {
        return intendedRecipientsCount;
    }

    public void setIntendedRecipientsCount(int intendedRecipientsCount) {
        this.intendedRecipientsCount = intendedRecipientsCount;
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
