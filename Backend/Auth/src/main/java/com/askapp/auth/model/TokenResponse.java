package com.askapp.auth.model;

public record TokenResponse(String accessToken, String refreshToken, long expiresIn) {
}
