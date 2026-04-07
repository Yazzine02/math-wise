package com.mathwise.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AuthResponseDto {
    private String token;
    private String email;
    @JsonProperty("display_name")
    private String displayName;

    public AuthResponseDto(String token, String email, String displayName) {
        this.token = token;
        this.email = email;
        this.displayName = displayName;
    }

    // Getters
    public String getToken() { return token; }
    public String getEmail() { return email; }
    public String getDisplayName() { return displayName; }
}
