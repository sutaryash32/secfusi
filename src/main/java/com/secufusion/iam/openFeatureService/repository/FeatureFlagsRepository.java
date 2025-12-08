package com.secufusion.iam.openFeatureService.repository;

import com.secufusion.iam.openFeatureService.entity.FeatureFlags;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FeatureFlagsRepository extends JpaRepository<FeatureFlags, Long> {
    Optional<FeatureFlags> findByFlagKey(String flagKey);
}

