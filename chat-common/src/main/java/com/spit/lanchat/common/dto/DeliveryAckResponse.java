package com.spit.lanchat.common.dto;

public class DeliveryAckResponse {
    private String messageId;
    private String status;
    private boolean serverContentDeleted;

    public DeliveryAckResponse() {}

    public DeliveryAckResponse(String messageId, String status, boolean serverContentDeleted) {
        this.messageId = messageId;
        this.status = status;
        this.serverContentDeleted = serverContentDeleted;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isServerContentDeleted() {
        return serverContentDeleted;
    }

    public void setServerContentDeleted(boolean serverContentDeleted) {
        this.serverContentDeleted = serverContentDeleted;
    }
}
