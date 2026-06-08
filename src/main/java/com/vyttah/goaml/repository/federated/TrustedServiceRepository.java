package com.vyttah.goaml.repository.federated;

import com.vyttah.goaml.model.entity.federated.SourceSystem;
import com.vyttah.goaml.model.entity.federated.TrustedService;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Reads registered sibling services from the shared {@code public.trusted_service}. The
 * {@code ServiceCredentialValidator} resolves the assertion's issuer → the registered public key here.
 */
public interface TrustedServiceRepository extends JpaRepository<TrustedService, UUID> {

    Optional<TrustedService> findBySourceSystem(SourceSystem sourceSystem);
}
