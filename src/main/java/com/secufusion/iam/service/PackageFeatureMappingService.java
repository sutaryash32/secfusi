package com.secufusion.iam.service;

import com.secufusion.iam.dto.*;
import com.secufusion.iam.entity.*;
import com.secufusion.iam.exception.ResourceConflictException;
import com.secufusion.iam.exception.ResourceNotFoundException;
import com.secufusion.iam.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional
public class PackageFeatureMappingService {

    @Autowired
    private PackageFeatureMappingRepository mappingRepository;

    @Autowired
    private PackageRepository packageRepository;

    @Autowired
    private FeatureRepository featureRepository;

    @Autowired
    private FeatureGroupRepository featureGroupRepository;

    @Autowired
    private AccessLevelRepository accessLevelRepository;

    @Autowired
    private RetentionPeriodRepository retentionPeriodRepository;

    public PackageFeatureMappingResponse create(CreatePackageFeatureMappingRequest request) {
        log.info("Creating package-feature mapping for package: {} and feature: {}",
                request.getPackageId(), request.getFeatureId());

        // Check if mapping already exists
        if (mappingRepository.existsByPackageEntityPkPackageIdAndFeaturePkFeatureID(
                request.getPackageId(), request.getFeatureId())) {
            throw new ResourceConflictException("Mapping already exists for this package and feature");
        }

        // Validate and fetch entities
        com.secufusion.iam.entity.Package pkg = packageRepository.findById(request.getPackageId())
                .orElseThrow(() -> new ResourceNotFoundException("Package not found: " + request.getPackageId()));

        FeatureGroup featureGroup = featureGroupRepository.findById(request.getFeatureGroupId())
                .orElseThrow(() -> new ResourceNotFoundException("Feature group not found: " + request.getFeatureGroupId()));

        Feature feature = featureRepository.findById(request.getFeatureId())
                .orElseThrow(() -> new ResourceNotFoundException("Feature not found: " + request.getFeatureId()));

        AccessLevel accessLevel = accessLevelRepository.findById(request.getAccessLevelId())
                .orElseThrow(() -> new ResourceNotFoundException("Access level not found: " + request.getAccessLevelId()));

        RetentionPeriod retentionPeriod = null;
        if (request.getRetentionPeriodId() != null) {
            retentionPeriod = retentionPeriodRepository.findById(request.getRetentionPeriodId())
                    .orElseThrow(() -> new ResourceNotFoundException("Retention period not found: " + request.getRetentionPeriodId()));
        }

        // Create mapping
        PackageFeatureMapping mapping = new PackageFeatureMapping();
        mapping.setPackageEntity(pkg);
        mapping.setFeatureGroup(featureGroup);
        mapping.setFeature(feature);
        mapping.setAccessLevel(accessLevel);
        mapping.setRetentionPeriod(retentionPeriod);
        mapping.setIsEnabled(request.getIsEnabled() != null ? request.getIsEnabled() : true);
        mapping.setCustomConfig(request.getCustomConfig());

        PackageFeatureMapping saved = mappingRepository.save(mapping);
        log.info("Package-feature mapping created with ID: {}", saved.getPkMappingId());

        return mapToResponse(saved);
    }

    public List<PackageFeatureMappingResponse> bulkCreate(BulkPackageFeatureMappingRequest request) {
        log.info("Bulk creating package-feature mappings for package: {}", request.getPackageId());

        com.secufusion.iam.entity.Package pkg = packageRepository.findById(request.getPackageId())
                .orElseThrow(() -> new ResourceNotFoundException("Package not found: " + request.getPackageId()));

        List<PackageFeatureMappingResponse> results = new ArrayList<>();

        for (BulkPackageFeatureMappingRequest.FeatureMappingItem item : request.getMappings()) {
            // Skip if mapping already exists
            if (mappingRepository.existsByPackageEntityPkPackageIdAndFeaturePkFeatureID(
                    request.getPackageId(), item.getFeatureId())) {
                log.warn("Skipping duplicate mapping for package {} and feature {}",
                        request.getPackageId(), item.getFeatureId());
                continue;
            }

            FeatureGroup featureGroup = featureGroupRepository.findById(item.getFeatureGroupId())
                    .orElseThrow(() -> new ResourceNotFoundException("Feature group not found: " + item.getFeatureGroupId()));

            Feature feature = featureRepository.findById(item.getFeatureId())
                    .orElseThrow(() -> new ResourceNotFoundException("Feature not found: " + item.getFeatureId()));

            AccessLevel accessLevel = accessLevelRepository.findById(item.getAccessLevelId())
                    .orElseThrow(() -> new ResourceNotFoundException("Access level not found: " + item.getAccessLevelId()));

            RetentionPeriod retentionPeriod = null;
            if (item.getRetentionPeriodId() != null) {
                retentionPeriod = retentionPeriodRepository.findById(item.getRetentionPeriodId())
                        .orElseThrow(() -> new ResourceNotFoundException("Retention period not found: " + item.getRetentionPeriodId()));
            }

            PackageFeatureMapping mapping = new PackageFeatureMapping();
            mapping.setPackageEntity(pkg);
            mapping.setFeatureGroup(featureGroup);
            mapping.setFeature(feature);
            mapping.setAccessLevel(accessLevel);
            mapping.setRetentionPeriod(retentionPeriod);
            mapping.setIsEnabled(item.getIsEnabled() != null ? item.getIsEnabled() : true);
            mapping.setCustomConfig(item.getCustomConfig());

            PackageFeatureMapping saved = mappingRepository.save(mapping);
            results.add(mapToResponse(saved));
        }

        log.info("Bulk created {} package-feature mappings", results.size());
        return results;
    }

    public PackageFeatureMappingResponse update(Long id, CreatePackageFeatureMappingRequest request) {
        log.info("Updating package-feature mapping: {}", id);

        PackageFeatureMapping existing = mappingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Mapping not found: " + id));

        // If changing package or feature, check for duplicates
        if (!existing.getPackageEntity().getPkPackageId().equals(request.getPackageId()) ||
                !existing.getFeature().getPkFeatureID().equals(request.getFeatureId())) {

            if (mappingRepository.existsByPackageEntityPkPackageIdAndFeaturePkFeatureID(
                    request.getPackageId(), request.getFeatureId())) {
                throw new ResourceConflictException("Mapping already exists for this package and feature");
            }

            com.secufusion.iam.entity.Package pkgEntity = packageRepository.findById(request.getPackageId())
                    .orElseThrow(() -> new ResourceNotFoundException("Package not found: " + request.getPackageId()));
            existing.setPackageEntity(pkgEntity);

            Feature feature = featureRepository.findById(request.getFeatureId())
                    .orElseThrow(() -> new ResourceNotFoundException("Feature not found: " + request.getFeatureId()));
            existing.setFeature(feature);
        }

        FeatureGroup featureGroup = featureGroupRepository.findById(request.getFeatureGroupId())
                .orElseThrow(() -> new ResourceNotFoundException("Feature group not found: " + request.getFeatureGroupId()));
        existing.setFeatureGroup(featureGroup);

        AccessLevel accessLevel = accessLevelRepository.findById(request.getAccessLevelId())
                .orElseThrow(() -> new ResourceNotFoundException("Access level not found: " + request.getAccessLevelId()));
        existing.setAccessLevel(accessLevel);

        if (request.getRetentionPeriodId() != null) {
            RetentionPeriod retentionPeriod = retentionPeriodRepository.findById(request.getRetentionPeriodId())
                    .orElseThrow(() -> new ResourceNotFoundException("Retention period not found: " + request.getRetentionPeriodId()));
            existing.setRetentionPeriod(retentionPeriod);
        } else {
            existing.setRetentionPeriod(null);
        }

        existing.setIsEnabled(request.getIsEnabled() != null ? request.getIsEnabled() : true);
        existing.setCustomConfig(request.getCustomConfig());

        PackageFeatureMapping updated = mappingRepository.save(existing);
        log.info("Package-feature mapping updated: {}", id);

        return mapToResponse(updated);
    }

    public void delete(Long id) {
        log.info("Deleting package-feature mapping: {}", id);

        if (!mappingRepository.existsById(id)) {
            throw new ResourceNotFoundException("Mapping not found: " + id);
        }

        mappingRepository.deleteById(id);
        log.info("Package-feature mapping deleted: {}", id);
    }

    public void deleteByPackage(Long packageId) {
        log.info("Deleting all mappings for package: {}", packageId);

        if (!packageRepository.existsById(packageId)) {
            throw new ResourceNotFoundException("Package not found: " + packageId);
        }

        mappingRepository.deleteByPackageEntityPkPackageId(packageId);
        log.info("All mappings deleted for package: {}", packageId);
    }

    @Transactional(readOnly = true)
    public PackageFeatureMappingResponse getById(Long id) {
        log.info("Fetching mapping: {}", id);

        PackageFeatureMapping mapping = mappingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Mapping not found: " + id));

        return mapToResponse(mapping);
    }

    @Transactional(readOnly = true)
    public List<PackageFeatureMappingResponse> getMappingsByPackage(Long packageId) {
        log.info("Fetching mappings for package: {}", packageId);

        return mappingRepository.findByPackageIdWithDetails(packageId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PackageFeatureMatrixResponse getPackageMatrix(Long packageId) {
        log.info("Fetching feature matrix for package: {}", packageId);

        com.secufusion.iam.entity.Package pkg = packageRepository.findById(packageId)
                .orElseThrow(() -> new ResourceNotFoundException("Package not found: " + packageId));

        List<PackageFeatureMapping> mappings = mappingRepository.findByPackageIdWithDetails(packageId);

        // Group by feature group
        Map<Long, List<PackageFeatureMapping>> groupedByFeatureGroup = mappings.stream()
                .collect(Collectors.groupingBy(m -> m.getFeatureGroup().getPkFeatureGroupId()));

        List<PackageFeatureMatrixResponse.FeatureGroupMatrix> featureGroups = new ArrayList<>();

        groupedByFeatureGroup.forEach((groupId, groupMappings) -> {
            if (!groupMappings.isEmpty()) {
                FeatureGroup group = groupMappings.get(0).getFeatureGroup();

                List<PackageFeatureMatrixResponse.FeatureAccessInfo> features = groupMappings.stream()
                        .map(m -> PackageFeatureMatrixResponse.FeatureAccessInfo.builder()
                                .featureId(m.getFeature().getPkFeatureID())
                                .featureName(m.getFeature().getFeatureName())
                                .featureCode(m.getFeature().getFeatureCode())
                                .accessLevel(m.getAccessLevel().getLevelName())
                                .accessLevelCode(m.getAccessLevel().getLevelCode())
                                .accessLevelValue(m.getAccessLevel().getLevelValue())
                                .retentionPeriod(m.getRetentionPeriod() != null ? m.getRetentionPeriod().getPeriodName() : null)
                                .retentionPeriodCode(m.getRetentionPeriod() != null ? m.getRetentionPeriod().getPeriodCode() : null)
                                .retentionDays(m.getRetentionPeriod() != null ? m.getRetentionPeriod().getPeriodDays() : null)
                                .isEnabled(m.getIsEnabled())
                                .build())
                        .collect(Collectors.toList());

                featureGroups.add(PackageFeatureMatrixResponse.FeatureGroupMatrix.builder()
                        .featureGroupId(group.getPkFeatureGroupId())
                        .featureGroupName(group.getGroupName())
                        .featureGroupCode(group.getGroupCode())
                        .displayOrder(group.getDisplayOrder())
                        .features(features)
                        .build());
            }
        });

        // Sort by display order
        featureGroups.sort(Comparator.comparing(PackageFeatureMatrixResponse.FeatureGroupMatrix::getDisplayOrder));

        return PackageFeatureMatrixResponse.builder()
                .packageId(pkg.getPkPackageId())
                .packageName(pkg.getPackageName())
                .packageTypeName(pkg.getPackageType() != null ? pkg.getPackageType().getPackageTypeName() : null)
                .featureGroups(featureGroups)
                .build();
    }

    @Transactional(readOnly = true)
    public List<PackageFeatureMatrixResponse> getAllPackageMatrices() {
        log.info("Fetching all package feature matrices");

        List<Long> packageIds = mappingRepository.findDistinctPackageIds();
        return packageIds.stream()
                .map(this::getPackageMatrix)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public boolean hasFeatureAccess(Long packageId, String featureCode) {
        Optional<PackageFeatureMapping> mapping = mappingRepository.findByPackageIdAndFeatureCode(packageId, featureCode);

        if (mapping.isEmpty()) {
            log.debug("No mapping found for package {} and feature {}", packageId, featureCode);
            return false;
        }

        PackageFeatureMapping m = mapping.get();
        // Access granted if enabled and access level value > 0 (anything except "No")
        return m.getIsEnabled() && m.getAccessLevel().getLevelValue() > 0;
    }

    @Transactional(readOnly = true)
    public AccessLevelResponse getFeatureAccessLevel(Long packageId, String featureCode) {
        Optional<PackageFeatureMapping> mapping = mappingRepository.findByPackageIdAndFeatureCode(packageId, featureCode);

        if (mapping.isEmpty()) {
            return null;
        }

        AccessLevel al = mapping.get().getAccessLevel();
        return AccessLevelResponse.builder()
                .accessLevelId(al.getPkAccessLevelId())
                .levelName(al.getLevelName())
                .levelCode(al.getLevelCode())
                .levelValue(al.getLevelValue())
                .description(al.getDescription())
                .isActive(al.getIsActive())
                .build();
    }

    @Transactional(readOnly = true)
    public RetentionPeriodResponse getFeatureRetention(Long packageId, String featureCode) {
        Optional<PackageFeatureMapping> mapping = mappingRepository.findByPackageIdAndFeatureCode(packageId, featureCode);

        if (mapping.isEmpty() || mapping.get().getRetentionPeriod() == null) {
            return null;
        }

        RetentionPeriod rp = mapping.get().getRetentionPeriod();
        return RetentionPeriodResponse.builder()
                .retentionPeriodId(rp.getPkRetentionPeriodId())
                .periodName(rp.getPeriodName())
                .periodCode(rp.getPeriodCode())
                .periodDays(rp.getPeriodDays())
                .description(rp.getDescription())
                .isActive(rp.getIsActive())
                .build();
    }

    private PackageFeatureMappingResponse mapToResponse(PackageFeatureMapping mapping) {
        return PackageFeatureMappingResponse.builder()
                .mappingId(mapping.getPkMappingId())
                .packageId(mapping.getPackageEntity().getPkPackageId())
                .packageName(mapping.getPackageEntity().getPackageName())
                .featureGroupId(mapping.getFeatureGroup().getPkFeatureGroupId())
                .featureGroupName(mapping.getFeatureGroup().getGroupName())
                .featureGroupCode(mapping.getFeatureGroup().getGroupCode())
                .featureId(mapping.getFeature().getPkFeatureID())
                .featureName(mapping.getFeature().getFeatureName())
                .featureCode(mapping.getFeature().getFeatureCode())
                .accessLevelId(mapping.getAccessLevel().getPkAccessLevelId())
                .accessLevelName(mapping.getAccessLevel().getLevelName())
                .accessLevelCode(mapping.getAccessLevel().getLevelCode())
                .accessLevelValue(mapping.getAccessLevel().getLevelValue())
                .retentionPeriodId(mapping.getRetentionPeriod() != null ? mapping.getRetentionPeriod().getPkRetentionPeriodId() : null)
                .retentionPeriodName(mapping.getRetentionPeriod() != null ? mapping.getRetentionPeriod().getPeriodName() : null)
                .retentionPeriodCode(mapping.getRetentionPeriod() != null ? mapping.getRetentionPeriod().getPeriodCode() : null)
                .retentionDays(mapping.getRetentionPeriod() != null ? mapping.getRetentionPeriod().getPeriodDays() : null)
                .isEnabled(mapping.getIsEnabled())
                .customConfig(mapping.getCustomConfig())
                .createdAt(mapping.getCreatedAt())
                .updatedAt(mapping.getUpdatedAt())
                .build();
    }
}
