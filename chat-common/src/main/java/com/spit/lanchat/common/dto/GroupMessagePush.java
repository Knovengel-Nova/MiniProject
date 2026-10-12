package com.spit.lanchat.common.dto;

import java.time.Instant;

public class GroupMessagePush {
    private String messageId;
    private String groupId;
    private String groupName;
    private String senderUsername;
    private String messageText;
    private Instant timestamp;
    private Double predictedRiskScore;
    private boolean flagged;
    private String modelStatus;

    public GroupMessagePush() {}

    public GroupMessagePush(String messageId, String groupId, String groupName, String senderUsername,
                            String messageText, Instant timestamp, Double predictedRiskScore,
                            boolean flagged, String modelStatus) {
        this.messageId = messageId;
        this.groupId = groupId;
        this.groupName = groupName;
        this.senderUsername = senderUsername;
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

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
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
