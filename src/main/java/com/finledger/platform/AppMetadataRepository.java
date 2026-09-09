package com.finledger.platform;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppMetadataRepository extends JpaRepository<AppMetadata, Long> {

    Optional<AppMetadata> findByMetaKey(String metaKey);
}
