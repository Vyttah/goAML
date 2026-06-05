package com.vyttah.goaml.model.dto.auth;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds) {
}
