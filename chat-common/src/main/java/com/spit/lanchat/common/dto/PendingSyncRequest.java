package com.spit.lanchat.common.dto;

public class PendingSyncRequest {
    private String username;

    public PendingSyncRequest() {}

    public PendingSyncRequest(String username) {
        this.username = username;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
