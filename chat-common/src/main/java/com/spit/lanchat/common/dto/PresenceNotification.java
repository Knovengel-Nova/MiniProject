package com.spit.lanchat.common.dto;

import java.time.Instant;

public class PresenceNotification {
    private int userId;
    private String username;
    private String status;
    private Instant timestamp;

    public PresenceNotification() {}

    public PresenceNotification(int userId, String username, String status, Instant timestamp) {
        this.userId = userId;
        this.username = username;
        this.status = status;
        this.timestamp = timestamp != null ? timestamp : Instant.now();
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

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
