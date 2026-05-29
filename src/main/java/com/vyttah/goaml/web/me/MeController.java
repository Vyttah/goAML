package com.vyttah.goaml.web.me;

import com.vyttah.goaml.security.UserPrincipal;
import com.vyttah.goaml.tenant.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Returns the currently authenticated user — handy for the React UI and for
 * verifying the JWT filter + tenant routing work end-to-end.
 */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    @GetMapping
    public ResponseEntity<MeResponse> me(@AuthenticationPrincipal UserPrincipal principal) {
        List<String> roles = AuthorityUtils.authorityListToSet(principal.getAuthorities())
                .stream()
                .map(a -> a.startsWith("ROLE_") ? a.substring(5) : a)
                .toList();
        return ResponseEntity.ok(new MeResponse(
                principal.getUserId(),
                principal.getTenantId(),
                TenantContext.get(),
                principal.getUsername(),
                roles));
    }

    public record MeResponse(UUID userId, UUID tenantId, String tenantSchema,
                             String email, List<String> roles) {}
}
