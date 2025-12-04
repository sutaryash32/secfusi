package com.secufusion.iam.repository;

import com.secufusion.iam.entity.Feature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FeatureRepository extends JpaRepository<Feature, Long> {
    boolean existsByFeatureNameIgnoreCase(String featureName);

}
