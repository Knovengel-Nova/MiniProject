package com.spit.lanchat.common.dto;

public class GroupMemberRequest {
    private String groupId;
    private String targetUsername;
    private String action; // "ADD" or "REMOVE"

    public GroupMemberRequest() {}

    public GroupMemberRequest(String groupId, String targetUsername, String action) {
        this.groupId = groupId;
        this.targetUsername = targetUsername;
        this.action = action;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getTargetUsername() {
        return targetUsername;
    }

    public void setTargetUsername(String targetUsername) {
        this.targetUsername = targetUsername;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }
}
