package com.spit.lanchat.common.dto;

import java.util.ArrayList;
import java.util.List;

public class GroupListResponse {
    private List<GroupDto> groups = new ArrayList<>();

    public GroupListResponse() {}

    public GroupListResponse(List<GroupDto> groups) {
        this.groups = groups != null ? groups : new ArrayList<>();
    }

    public List<GroupDto> getGroups() {
        return groups;
    }

    public void setGroups(List<GroupDto> groups) {
        this.groups = groups;
    }
}
