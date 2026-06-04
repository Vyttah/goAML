package com.vyttah.goaml.service.auth;

import com.vyttah.goaml.model.dto.auth.LoginRequest;
import com.vyttah.goaml.model.dto.auth.LoginResponse;

/**
 * Authenticates a user and issues a goAML JWT. Implemented by {@link DefaultAuthService}.
 */
public interface AuthService {

    LoginResponse login(LoginRequest request);
}
