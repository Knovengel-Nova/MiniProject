package com.spit.lanchat.common.dto;

public class LoginResponse {
    private int userId;
    private String username;
    private String sessionToken;
    private String message;

    public LoginResponse() {}

    public LoginResponse(int userId, String username, String sessionToken, String message) {
        this.userId = userId;
        this.username = username;
        this.sessionToken = sessionToken;
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

    public String getSessionToken() {
        return sessionToken;
    }

    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
