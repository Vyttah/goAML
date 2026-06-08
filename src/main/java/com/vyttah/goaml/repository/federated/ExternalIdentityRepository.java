package com.vyttah.goaml.repository.federated;

import com.vyttah.goaml.model.entity.federated.ExternalIdentity;
import com.vyttah.goaml.model.entity.federated.SourceSystem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves a sibling system's user to a goAML {@code app_user} from the shared
 * {@code public.external_identity}, during federated token-exchange.
 */
public interface ExternalIdentityRepository extends JpaRepository<ExternalIdentity, UUID> {

    Optional<ExternalIdentity> findBySourceSystemAndExternalUserId(SourceSystem sourceSystem,
                                                                   String externalUserId);
}
