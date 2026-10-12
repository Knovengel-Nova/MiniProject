package com.spit.lanchat.common.dto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class GroupDto {
    private String groupId;
    private String groupName;
    private String createdBy;
    private List<String> members = new ArrayList<>();
    private Instant createdAt;

    public GroupDto() {}

    public GroupDto(String groupId, String groupName, String createdBy, List<String> members, Instant createdAt) {
        this.groupId = groupId;
        this.groupName = groupName;
        this.createdBy = createdBy;
        this.members = members != null ? members : new ArrayList<>();
        this.createdAt = createdAt != null ? createdAt : Instant.now();
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

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public List<String> getMembers() {
        return members;
    }

    public void setMembers(List<String> members) {
        this.members = members;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
