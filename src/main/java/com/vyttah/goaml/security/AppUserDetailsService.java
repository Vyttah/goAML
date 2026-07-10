package com.vyttah.goaml.security;

import com.vyttah.goaml.repository.appuser.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Loads {@link UserPrincipal} from the shared {@code public.app_user} table by email.
 *
 * <p>NOT on the native-login path anymore: since email is unique only <em>per tenant</em>, login is done
 * explicitly in {@link com.vyttah.goaml.service.auth.DefaultAuthService} (company id → tenant → user),
 * not via {@code AuthenticationManager}. This bean remains for any {@code UserDetailsService} consumer;
 * a bare-email lookup here can be ambiguous across tenants and is intentionally unused for authentication.
 */
@RequiredArgsConstructor
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
                .map(UserPrincipal::fromEntity)
                .orElseThrow(() -> new UsernameNotFoundException("No user: " + email));
    }
}
