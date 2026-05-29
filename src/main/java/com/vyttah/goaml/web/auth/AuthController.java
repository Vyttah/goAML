package com.vyttah.goaml.web.auth;

import com.vyttah.goaml.persistence.shared.AppUserEntity;
import com.vyttah.goaml.persistence.shared.AppUserRepository;
import com.vyttah.goaml.persistence.shared.TenantEntity;
import com.vyttah.goaml.persistence.shared.TenantRepository;
import com.vyttah.goaml.security.JwtService;
import com.vyttah.goaml.service.audit.AuditService;
import com.vyttah.goaml.tenant.TenantIdentifierResolver;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final AppUserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final JwtService jwtService;
    private final AuditService auditService;

    public AuthController(AuthenticationManager authenticationManager,
                          AppUserRepository userRepository,
                          TenantRepository tenantRepository,
                          JwtService jwtService,
                          AuditService auditService) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.jwtService = jwtService;
        this.auditService = auditService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        } catch (AuthenticationException ex) {
            // Either bad credentials or unknown user — same shape, no enumeration.
            throw new BadCredentialsException("Invalid email or password");
        }

        AppUserEntity user = userRepository.findByEmail(request.email()).orElseThrow();
        String schema = user.getTenantId() == null
                ? TenantIdentifierResolver.DEFAULT_TENANT
                : tenantRepository.findById(user.getTenantId())
                    .map(TenantEntity::getSchemaName)
                    .orElse(TenantIdentifierResolver.DEFAULT_TENANT);

        JwtService.IssuedToken token = jwtService.issueAccessToken(user, schema);
        auditService.recordLogin(user.getId(), user.getEmail(), schema);
        return ResponseEntity.ok(new LoginResponse(token.token(), "Bearer", token.expiresInSeconds()));
    }
}
