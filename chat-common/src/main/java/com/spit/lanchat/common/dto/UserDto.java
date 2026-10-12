package com.spit.lanchat.common.dto;

import java.time.Instant;

public class UserDto {
    private int userId;
    private String username;
    private String status;
    private Instant lastSeen;

    public UserDto() {}

    public UserDto(int userId, String username, String status, Instant lastSeen) {
        this.userId = userId;
        this.username = username;
        this.status = status;
        this.lastSeen = lastSeen;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(Instant lastSeen) {
        this.lastSeen = lastSeen;
    }
}
