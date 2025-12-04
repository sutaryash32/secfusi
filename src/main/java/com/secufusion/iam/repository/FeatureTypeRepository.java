package com.secufusion.iam.repository;

import com.secufusion.iam.entity.FeatureType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FeatureTypeRepository extends JpaRepository<FeatureType, Long> {
}
