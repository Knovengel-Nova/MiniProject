package com.spit.lanchat.common.dto;

import java.util.ArrayList;
import java.util.List;

public class UserListResponse {
    private List<UserDto> users = new ArrayList<>();

    public UserListResponse() {}

    public UserListResponse(List<UserDto> users) {
        this.users = users != null ? users : new ArrayList<>();
    }

    public List<UserDto> getUsers() {
        return users;
    }

    public void setUsers(List<UserDto> users) {
        this.users = users;
    }
}
