package com.spit.lanchat.common.dto;

public class RegisterResponse {
    private int userId;
    private String username;
    private String message;

    public RegisterResponse() {}

    public RegisterResponse(int userId, String username, String message) {
        this.userId = userId;
        this.username = username;
        this.message = message;
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

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
