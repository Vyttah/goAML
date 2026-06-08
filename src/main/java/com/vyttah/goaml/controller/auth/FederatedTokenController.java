package com.vyttah.goaml.controller.auth;

import com.vyttah.goaml.model.dto.auth.FederatedTokenRequest;
import com.vyttah.goaml.model.dto.auth.LoginResponse;
import com.vyttah.goaml.service.auth.FederatedAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Federated token-exchange endpoint (Phase 1.5). Called <strong>server-to-server</strong> by a sibling
 * Vyttah service (accounting / screening) after it has authenticated its own user; returns a standard goAML
 * JWT. Reachable unauthenticated ({@code /api/v1/auth/**} is permitted) — the request's signed service
 * assertion is the credential, verified inside {@link FederatedAuthService}. Disabled (→ 404) unless
 * {@code goaml.auth.mode} is {@code federated} or {@code both}.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/auth/federated")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class FederatedTokenController {

    private final FederatedAuthService federatedAuthService;

    @PostMapping("/token")
    public ResponseEntity<LoginResponse> token(@Valid @RequestBody FederatedTokenRequest request) {
        return ResponseEntity.ok(federatedAuthService.exchange(request));
    }
}
