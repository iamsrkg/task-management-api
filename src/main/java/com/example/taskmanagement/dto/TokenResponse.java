package com.example.taskmanagement.dto;

public class TokenResponse {

    /** The short-lived access token: send it as "Authorization: Bearer ...". */
    private String token;

    /** Exchange it at /api/auth/refresh for a new pair when the access token expires. Single use. */
    private String refreshToken;

    public TokenResponse() {}

    public TokenResponse(String token, String refreshToken) {
        this.token = token;
        this.refreshToken = refreshToken;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
}
