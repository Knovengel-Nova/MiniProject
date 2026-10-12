package com.spit.lanchat.common.dto;

import java.util.ArrayList;
import java.util.List;

public class GroupCreateRequest {
    private String groupName;
    private List<String> memberUsernames = new ArrayList<>();

    public GroupCreateRequest() {}

    public GroupCreateRequest(String groupName, List<String> memberUsernames) {
        this.groupName = groupName;
        this.memberUsernames = memberUsernames != null ? memberUsernames : new ArrayList<>();
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public List<String> getMemberUsernames() {
        return memberUsernames;
    }

    public void setMemberUsernames(List<String> memberUsernames) {
        this.memberUsernames = memberUsernames;
    }
}
