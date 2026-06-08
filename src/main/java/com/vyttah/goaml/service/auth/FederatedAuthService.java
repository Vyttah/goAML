package com.vyttah.goaml.service.auth;

import com.vyttah.goaml.model.dto.auth.FederatedTokenRequest;
import com.vyttah.goaml.model.dto.auth.LoginResponse;

/**
 * Exchanges a sibling service's signed assertion for a standard goAML JWT (Phase 1.5). Implemented by
 * {@link DefaultFederatedAuthService}.
 */
public interface FederatedAuthService {

    LoginResponse exchange(FederatedTokenRequest request);
}
