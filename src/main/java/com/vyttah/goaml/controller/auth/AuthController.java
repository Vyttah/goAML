package com.vyttah.goaml.controller.auth;

import com.vyttah.goaml.model.dto.auth.LoginRequest;
import com.vyttah.goaml.model.dto.auth.LoginResponse;
import com.vyttah.goaml.security.LoginRateLimiter;
import com.vyttah.goaml.service.auth.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/auth")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AuthController {

    private final AuthService authService;
    private final LoginRateLimiter rateLimiter;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletRequest http) {
        // B14 — throttle per (client IP + company + email) so a host cannot brute-force credentials. 429 on exceed.
        String key = "login:" + clientIp(http) + ":"
                + (request.companyId() == null ? "" : request.companyId().toLowerCase()) + ":"
                + (request.email() == null ? "" : request.email().toLowerCase());
        if (!rateLimiter.tryAcquire(key)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many login attempts; try again shortly");
        }
        return ResponseEntity.ok(authService.login(request));
    }

    /** Honour a single proxy hop (X-Forwarded-For) when present; else the socket address. */
    static String clientIp(HttpServletRequest http) {
        String xff = http.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return http.getRemoteAddr();
    }
}
