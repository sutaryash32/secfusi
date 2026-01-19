package com.secufusion.iam.service;

import com.secufusion.iam.dto.AccessLevelResponse;
import com.secufusion.iam.dto.RetentionPeriodResponse;
import com.secufusion.iam.dto.TenantFeatureAccessResponse;
import com.secufusion.iam.entity.PackageFeatureMapping;
import com.secufusion.iam.entity.Tenant;
import com.secufusion.iam.exception.ResourceNotFoundException;
import com.secufusion.iam.repository.PackageFeatureMappingRepository;
import com.secufusion.iam.repository.TenantRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@Transactional(readOnly = true)
public class TenantFeatureService {

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private PackageFeatureMappingRepository mappingRepository;

    /**
     * Check if a tenant has access to a specific feature based on their package.
     */
    public boolean hasFeatureAccess(String tenantId, String featureCode) {
        log.debug("Checking feature access for tenant: {}, feature: {}", tenantId, featureCode);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        if (tenant.getSubscriptionPackage() == null) {
            log.warn("Tenant {} has no subscription package assigned", tenantId);
            return false;
        }

        Long packageId = tenant.getSubscriptionPackage().getPkPackageId();
        Optional<PackageFeatureMapping> mapping = mappingRepository
                .findByPackageIdAndFeatureCode(packageId, featureCode);

        if (mapping.isEmpty()) {
            log.debug("No mapping found for package {} and feature {}", packageId, featureCode);
            return false;
        }

        PackageFeatureMapping pfm = mapping.get();
        if (!pfm.getIsEnabled()) {
            log.debug("Feature {} is disabled for package {}", featureCode, packageId);
            return false;
        }

        // Access granted if level value > 0 (anything except "No")
        boolean hasAccess = pfm.getAccessLevel().getLevelValue() > 0;
        log.debug("Feature access result: tenant={}, feature={}, hasAccess={}", tenantId, featureCode, hasAccess);

        return hasAccess;
    }

    /**
     * Get full feature access details for a tenant.
     */
    public TenantFeatureAccessResponse getTenantFeatureAccess(String tenantId) {
        log.info("Getting feature access for tenant: {}", tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        TenantFeatureAccessResponse.TenantFeatureAccessResponseBuilder builder = TenantFeatureAccessResponse.builder()
                .tenantId(tenant.getTenantID())
                .tenantName(tenant.getTenantName());

        if (tenant.getSubscriptionPackage() == null) {
            log.warn("Tenant {} has no subscription package assigned", tenantId);
            builder.features(new ArrayList<>());
            return builder.build();
        }

        Long packageId = tenant.getSubscriptionPackage().getPkPackageId();
        builder.packageId(packageId)
                .packageName(tenant.getSubscriptionPackage().getPackageName());

        List<PackageFeatureMapping> mappings = mappingRepository.findByPackageIdWithDetails(packageId);

        List<TenantFeatureAccessResponse.FeatureAccess> features = mappings.stream()
                .map(m -> TenantFeatureAccessResponse.FeatureAccess.builder()
                        .featureCode(m.getFeature().getFeatureCode())
                        .featureName(m.getFeature().getFeatureName())
                        .featureGroup(m.getFeatureGroup().getGroupName())
                        .featureGroupCode(m.getFeatureGroup().getGroupCode())
                        .accessLevel(m.getAccessLevel().getLevelName())
                        .accessLevelCode(m.getAccessLevel().getLevelCode())
                        .accessLevelValue(m.getAccessLevel().getLevelValue())
                        .hasAccess(m.getIsEnabled() && m.getAccessLevel().getLevelValue() > 0)
                        .retentionPeriod(m.getRetentionPeriod() != null ? m.getRetentionPeriod().getPeriodName() : null)
                        .retentionDays(m.getRetentionPeriod() != null ? m.getRetentionPeriod().getPeriodDays() : null)
                        .build())
                .toList();

        builder.features(features);
        return builder.build();
    }

    /**
     * Get retention period in days for a feature.
     */
    public Integer getFeatureRetentionDays(String tenantId, String featureCode) {
        log.debug("Getting retention days for tenant: {}, feature: {}", tenantId, featureCode);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        if (tenant.getSubscriptionPackage() == null) {
            log.warn("Tenant {} has no subscription package assigned", tenantId);
            return null;
        }

        Long packageId = tenant.getSubscriptionPackage().getPkPackageId();
        Optional<PackageFeatureMapping> mapping = mappingRepository
                .findByPackageIdAndFeatureCode(packageId, featureCode);

        if (mapping.isEmpty() || mapping.get().getRetentionPeriod() == null) {
            return null;
        }

        return mapping.get().getRetentionPeriod().getPeriodDays();
    }

    /**
     * Get access level code for a feature (YES, NO, LIMITED, BASIC, ADVANCED, COMING_SOON).
     */
    public String getFeatureAccessLevelCode(String tenantId, String featureCode) {
        log.debug("Getting access level for tenant: {}, feature: {}", tenantId, featureCode);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        if (tenant.getSubscriptionPackage() == null) {
            log.warn("Tenant {} has no subscription package assigned", tenantId);
            return "NO";
        }

        Long packageId = tenant.getSubscriptionPackage().getPkPackageId();
        Optional<PackageFeatureMapping> mapping = mappingRepository
                .findByPackageIdAndFeatureCode(packageId, featureCode);

        if (mapping.isEmpty()) {
            return "NO";
        }

        PackageFeatureMapping pfm = mapping.get();
        if (!pfm.getIsEnabled()) {
            return "NO";
        }

        return pfm.getAccessLevel().getLevelCode();
    }

    /**
     * Update tenant's subscription package.
     */
    @Transactional
    public void updateTenantPackage(String tenantId, Long packageId) {
        log.info("Updating tenant {} package to {}", tenantId, packageId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        com.secufusion.iam.entity.Package pkg = new com.secufusion.iam.entity.Package();
        pkg.setPkPackageId(packageId);
        tenant.setSubscriptionPackage(pkg);

        tenantRepository.save(tenant);
        log.info("Tenant {} package updated to {}", tenantId, packageId);
    }

    /**
     * Get tenant's current package name.
     */
    public String getTenantPackageName(String tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        if (tenant.getSubscriptionPackage() == null) {
            return "No Package";
        }

        return tenant.getSubscriptionPackage().getPackageName();
    }

    /**
     * Get full access level details for a feature.
     */
    public AccessLevelResponse getFeatureAccessLevel(String tenantId, String featureCode) {
        log.debug("Getting access level details for tenant: {}, feature: {}", tenantId, featureCode);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        if (tenant.getSubscriptionPackage() == null) {
            return null;
        }

        Long packageId = tenant.getSubscriptionPackage().getPkPackageId();
        Optional<PackageFeatureMapping> mapping = mappingRepository
                .findByPackageIdAndFeatureCode(packageId, featureCode);

        if (mapping.isEmpty()) {
            return null;
        }

        PackageFeatureMapping pfm = mapping.get();
        return AccessLevelResponse.builder()
                .accessLevelId(pfm.getAccessLevel().getPkAccessLevelId())
                .levelName(pfm.getAccessLevel().getLevelName())
                .levelCode(pfm.getAccessLevel().getLevelCode())
                .levelValue(pfm.getAccessLevel().getLevelValue())
                .description(pfm.getAccessLevel().getDescription())
                .isActive(pfm.getAccessLevel().getIsActive())
                .build();
    }

    /**
     * Get full retention period details for a feature.
     */
    public RetentionPeriodResponse getFeatureRetention(String tenantId, String featureCode) {
        log.debug("Getting retention details for tenant: {}, feature: {}", tenantId, featureCode);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        if (tenant.getSubscriptionPackage() == null) {
            return null;
        }

        Long packageId = tenant.getSubscriptionPackage().getPkPackageId();
        Optional<PackageFeatureMapping> mapping = mappingRepository
                .findByPackageIdAndFeatureCode(packageId, featureCode);

        if (mapping.isEmpty() || mapping.get().getRetentionPeriod() == null) {
            return null;
        }

        var rp = mapping.get().getRetentionPeriod();
        return RetentionPeriodResponse.builder()
                .retentionPeriodId(rp.getPkRetentionPeriodId())
                .periodName(rp.getPeriodName())
                .periodCode(rp.getPeriodCode())
                .periodDays(rp.getPeriodDays())
                .description(rp.getDescription())
                .isActive(rp.getIsActive())
                .build();
    }

    /**
     * Get all feature access details as a list.
     */
    public List<TenantFeatureAccessResponse.FeatureAccess> getAllFeatureAccess(String tenantId) {
        TenantFeatureAccessResponse response = getTenantFeatureAccess(tenantId);
        return response.getFeatures();
    }
}
