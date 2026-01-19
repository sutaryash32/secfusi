package com.secufusion.iam.repository;

import com.secufusion.iam.entity.FeatureGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FeatureGroupRepository extends JpaRepository<FeatureGroup, Long> {

    Optional<FeatureGroup> findByGroupCode(String groupCode);

    Optional<FeatureGroup> findByGroupName(String groupName);

    boolean existsByGroupCodeIgnoreCase(String groupCode);

    boolean existsByGroupNameIgnoreCase(String groupName);

    List<FeatureGroup> findByIsActiveTrueOrderByDisplayOrderAsc();

    List<FeatureGroup> findAllByOrderByDisplayOrderAsc();
}
