package com.secufusion.iam.repository;

import com.secufusion.iam.entity.PackageFeatureMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PackageFeatureMappingRepository extends JpaRepository<PackageFeatureMapping, Long> {

    List<PackageFeatureMapping> findByPackageEntityPkPackageId(Long packageId);

    @Query("SELECT pfm FROM PackageFeatureMapping pfm " +
            "JOIN FETCH pfm.featureGroup " +
            "JOIN FETCH pfm.feature " +
            "JOIN FETCH pfm.accessLevel " +
            "LEFT JOIN FETCH pfm.retentionPeriod " +
            "WHERE pfm.packageEntity.pkPackageId = :packageId " +
            "ORDER BY pfm.featureGroup.displayOrder, pfm.feature.featureName")
    List<PackageFeatureMapping> findByPackageIdWithDetails(@Param("packageId") Long packageId);

    Optional<PackageFeatureMapping> findByPackageEntityPkPackageIdAndFeaturePkFeatureID(
            Long packageId, Long featureId);

    @Query("SELECT pfm FROM PackageFeatureMapping pfm " +
            "JOIN FETCH pfm.featureGroup " +
            "JOIN FETCH pfm.feature " +
            "JOIN FETCH pfm.accessLevel " +
            "LEFT JOIN FETCH pfm.retentionPeriod " +
            "WHERE pfm.packageEntity.pkPackageId = :packageId " +
            "AND pfm.feature.featureCode = :featureCode")
    Optional<PackageFeatureMapping> findByPackageIdAndFeatureCode(
            @Param("packageId") Long packageId,
            @Param("featureCode") String featureCode);

    @Query("SELECT pfm FROM PackageFeatureMapping pfm " +
            "JOIN FETCH pfm.featureGroup " +
            "JOIN FETCH pfm.feature " +
            "JOIN FETCH pfm.accessLevel " +
            "LEFT JOIN FETCH pfm.retentionPeriod " +
            "WHERE pfm.feature.featureCode = :featureCode")
    List<PackageFeatureMapping> findByFeatureCode(@Param("featureCode") String featureCode);

    List<PackageFeatureMapping> findByPackageEntityPkPackageIdAndIsEnabledTrue(Long packageId);

    boolean existsByPackageEntityPkPackageIdAndFeaturePkFeatureID(Long packageId, Long featureId);

    void deleteByPackageEntityPkPackageId(Long packageId);

    List<PackageFeatureMapping> findByFeatureGroupPkFeatureGroupId(Long featureGroupId);

    @Query("SELECT pfm FROM PackageFeatureMapping pfm " +
            "JOIN FETCH pfm.packageEntity " +
            "JOIN FETCH pfm.featureGroup " +
            "JOIN FETCH pfm.feature " +
            "JOIN FETCH pfm.accessLevel " +
            "LEFT JOIN FETCH pfm.retentionPeriod " +
            "ORDER BY pfm.packageEntity.packageName, pfm.featureGroup.displayOrder, pfm.feature.featureName")
    List<PackageFeatureMapping> findAllWithDetails();

    @Query("SELECT DISTINCT pfm.packageEntity.pkPackageId FROM PackageFeatureMapping pfm")
    List<Long> findDistinctPackageIds();
}
