package com.spit.lanchat.common.dto;

public class DirectMessageRequest {
    private String messageId;
    private String recipientUsername;
    private String messageText;

    public DirectMessageRequest() {}

    public DirectMessageRequest(String messageId, String recipientUsername, String messageText) {
        this.messageId = messageId;
        this.recipientUsername = recipientUsername;
        this.messageText = messageText;
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

    public String getMessageText() {
        return messageText;
    }

    public void setMessageText(String messageText) {
        this.messageText = messageText;
    }
}
