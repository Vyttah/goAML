package com.vyttah.goaml.web.auth;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds) {
}
