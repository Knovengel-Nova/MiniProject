package com.spit.lanchat.common.dto;

public class GroupMessageRequest {
    private String messageId;
    private String groupId;
    private String messageText;

    public GroupMessageRequest() {}

    public GroupMessageRequest(String messageId, String groupId, String messageText) {
        this.messageId = messageId;
        this.groupId = groupId;
        this.messageText = messageText;
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

    public String getMessageText() {
        return messageText;
    }

    public void setMessageText(String messageText) {
        this.messageText = messageText;
    }
}
