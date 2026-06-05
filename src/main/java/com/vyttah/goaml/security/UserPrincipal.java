package com.vyttah.goaml.security;

import com.vyttah.goaml.model.entity.appuser.AppUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Spring Security principal carrying the authenticated user's id, tenant, and roles.
 *
 * <p>Constructed in two paths: {@link AppUserDetailsService#loadUserByUsername} (login)
 * and {@link JwtAuthFilter} (subsequent authenticated requests, no DB hit).
 */
public final class UserPrincipal implements UserDetails {

    private final UUID userId;
    private final UUID tenantId;
    private final String email;
    private final String passwordHash;
    private final boolean active;
    private final List<GrantedAuthority> authorities;

    public UserPrincipal(UUID userId, UUID tenantId, String email, String passwordHash,
                         boolean active, Collection<String> roleNames) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.active = active;
        this.authorities = roleNames.stream()
                .map(name -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + name))
                .toList();
    }

    public static UserPrincipal fromEntity(AppUser entity) {
        return new UserPrincipal(
                entity.getId(),
                entity.getTenantId(),
                entity.getEmail(),
                entity.getPasswordHash(),
                "ACTIVE".equals(entity.getStatus()),
                entity.getRoles().stream().map(r -> r.getName()).toList());
    }

    public UUID getUserId() { return userId; }
    public UUID getTenantId() { return tenantId; }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return email; }
    @Override public boolean isAccountNonExpired() { return active; }
    @Override public boolean isAccountNonLocked() { return active; }
    @Override public boolean isCredentialsNonExpired() { return active; }
    @Override public boolean isEnabled() { return active; }
}
