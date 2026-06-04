package com.vyttah.goaml.security;

import com.vyttah.goaml.repository.appuser.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Loads {@link UserPrincipal} from the shared {@code public.app_user} table by email.
 * Used by {@code AuthenticationManager} during login.
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
