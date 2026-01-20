package com.secufusion.iam.repository;

import com.secufusion.iam.entity.Feature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FeatureRepository extends JpaRepository<Feature, Long> {
    boolean existsByFeatureNameIgnoreCase(String featureName);

    java.util.Optional<Feature> findByFeatureCode(String featureCode);

    /**
     * Find all active addon features.
     */
    java.util.List<Feature> findByIsAddonTrueAndIsActiveTrue();

    /**
     * Find addon features by feature group.
     */
    java.util.List<Feature> findByIsAddonTrueAndIsActiveTrueAndFeatureGroupGroupCode(String groupCode);
}
