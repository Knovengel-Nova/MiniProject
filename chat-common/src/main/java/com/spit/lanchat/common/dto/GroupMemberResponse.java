package com.spit.lanchat.common.dto;

import java.util.ArrayList;
import java.util.List;

public class GroupMemberResponse {
    private String groupId;
    private String groupName;
    private List<String> currentMembers = new ArrayList<>();
    private String message;

    public GroupMemberResponse() {}

    public GroupMemberResponse(String groupId, String groupName, List<String> currentMembers, String message) {
        this.groupId = groupId;
        this.groupName = groupName;
        this.currentMembers = currentMembers != null ? currentMembers : new ArrayList<>();
        this.message = message;
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

    public List<String> getCurrentMembers() {
        return currentMembers;
    }

    public void setCurrentMembers(List<String> currentMembers) {
        this.currentMembers = currentMembers;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
