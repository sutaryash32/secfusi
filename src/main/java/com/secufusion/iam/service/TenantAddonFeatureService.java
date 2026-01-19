package com.secufusion.iam.service;

import com.secufusion.iam.dto.BulkTenantAddonFeatureRequest;
import com.secufusion.iam.dto.CreateTenantAddonFeatureRequest;
import com.secufusion.iam.dto.TenantAddonFeatureResponse;
import com.secufusion.iam.entity.*;
import com.secufusion.iam.exception.ResourceNotFoundException;
import com.secufusion.iam.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantAddonFeatureService {

    private final TenantAddonFeatureRepository tenantAddonFeatureRepository;
    private final TenantRepository tenantRepository;
    private final FeatureRepository featureRepository;
    private final AccessLevelRepository accessLevelRepository;
    private final RetentionPeriodRepository retentionPeriodRepository;

    @Transactional
    public TenantAddonFeatureResponse createAddonFeature(CreateTenantAddonFeatureRequest request, String createdBy) {
        log.info("Creating addon feature for tenant: {}", request.getTenantId());

        Tenant tenant = tenantRepository.findById(request.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + request.getTenantId()));

        Feature feature = resolveFeature(request.getFeatureId(), request.getFeatureCode());

        if (tenantAddonFeatureRepository.existsByTenantTenantIDAndFeaturePkFeatureID(
                request.getTenantId(), feature.getPkFeatureID())) {
            throw new IllegalArgumentException("Addon feature already exists for this tenant and feature");
        }

        AccessLevel accessLevel = accessLevelRepository.findById(request.getAccessLevelId())
                .orElseThrow(() -> new ResourceNotFoundException("Access level not found: " + request.getAccessLevelId()));

        RetentionPeriod retentionPeriod = null;
        if (request.getRetentionPeriodId() != null) {
            retentionPeriod = retentionPeriodRepository.findById(request.getRetentionPeriodId())
                    .orElseThrow(() -> new ResourceNotFoundException("Retention period not found: " + request.getRetentionPeriodId()));
        }

        TenantAddonFeature addon = new TenantAddonFeature();
        addon.setTenant(tenant);
        addon.setFeature(feature);
        addon.setAccessLevel(accessLevel);
        addon.setRetentionPeriod(retentionPeriod);
        addon.setIsEnabled(true);
        addon.setStartDate(request.getStartDate() != null ? request.getStartDate() : LocalDate.now());
        addon.setEndDate(request.getEndDate());
        addon.setIsTrial(request.getIsTrial() != null ? request.getIsTrial() : false);
        addon.setCustomConfig(request.getCustomConfig());
        addon.setCreatedBy(createdBy);
        addon.setUpdatedBy(createdBy);

        TenantAddonFeature saved = tenantAddonFeatureRepository.save(addon);
        log.info("Created addon feature with ID: {}", saved.getPkAddonId());

        return mapToResponse(saved);
    }

    @Transactional
    public List<TenantAddonFeatureResponse> createBulkAddonFeatures(BulkTenantAddonFeatureRequest request, String createdBy) {
        log.info("Creating bulk addon features for tenant: {}", request.getTenantId());

        Tenant tenant = tenantRepository.findById(request.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + request.getTenantId()));

        List<TenantAddonFeatureResponse> responses = new ArrayList<>();

        for (BulkTenantAddonFeatureRequest.AddonFeatureItem item : request.getAddons()) {
            Feature feature = resolveFeature(item.getFeatureId(), item.getFeatureCode());

            if (tenantAddonFeatureRepository.existsByTenantTenantIDAndFeaturePkFeatureID(
                    request.getTenantId(), feature.getPkFeatureID())) {
                log.warn("Skipping duplicate addon for feature: {}", feature.getFeatureCode());
                continue;
            }

            AccessLevel accessLevel = accessLevelRepository.findById(item.getAccessLevelId())
                    .orElseThrow(() -> new ResourceNotFoundException("Access level not found: " + item.getAccessLevelId()));

            RetentionPeriod retentionPeriod = null;
            if (item.getRetentionPeriodId() != null) {
                retentionPeriod = retentionPeriodRepository.findById(item.getRetentionPeriodId())
                        .orElseThrow(() -> new ResourceNotFoundException("Retention period not found: " + item.getRetentionPeriodId()));
            }

            TenantAddonFeature addon = new TenantAddonFeature();
            addon.setTenant(tenant);
            addon.setFeature(feature);
            addon.setAccessLevel(accessLevel);
            addon.setRetentionPeriod(retentionPeriod);
            addon.setIsEnabled(true);
            addon.setStartDate(LocalDate.now());
            addon.setIsTrial(false);
            addon.setCreatedBy(createdBy);
            addon.setUpdatedBy(createdBy);

            TenantAddonFeature saved = tenantAddonFeatureRepository.save(addon);
            responses.add(mapToResponse(saved));
        }

        log.info("Created {} addon features for tenant: {}", responses.size(), request.getTenantId());
        return responses;
    }

    public List<TenantAddonFeatureResponse> getAddonsByTenant(String tenantId) {
        log.debug("Fetching addons for tenant: {}", tenantId);
        List<TenantAddonFeature> addons = tenantAddonFeatureRepository.findByTenantTenantID(tenantId);
        return addons.stream().map(this::mapToResponse).toList();
    }

    public List<TenantAddonFeatureResponse> getActiveAddonsByTenant(String tenantId) {
        log.debug("Fetching active addons for tenant: {}", tenantId);
        List<TenantAddonFeature> addons = tenantAddonFeatureRepository.findAllActiveAddonsByTenant(
                tenantId, LocalDate.now());
        return addons.stream().map(this::mapToResponse).toList();
    }

    public TenantAddonFeatureResponse getAddonById(Long addonId) {
        TenantAddonFeature addon = tenantAddonFeatureRepository.findById(addonId)
                .orElseThrow(() -> new ResourceNotFoundException("Addon feature not found: " + addonId));
        return mapToResponse(addon);
    }

    public Optional<TenantAddonFeatureResponse> getAddonByTenantAndFeatureCode(String tenantId, String featureCode) {
        return tenantAddonFeatureRepository.findActiveAddonByTenantAndFeatureCode(
                tenantId, featureCode, LocalDate.now())
                .map(this::mapToResponse);
    }

    @Transactional
    public TenantAddonFeatureResponse updateAddonFeature(Long addonId, CreateTenantAddonFeatureRequest request, String updatedBy) {
        log.info("Updating addon feature: {}", addonId);

        TenantAddonFeature addon = tenantAddonFeatureRepository.findById(addonId)
                .orElseThrow(() -> new ResourceNotFoundException("Addon feature not found: " + addonId));

        if (request.getAccessLevelId() != null) {
            AccessLevel accessLevel = accessLevelRepository.findById(request.getAccessLevelId())
                    .orElseThrow(() -> new ResourceNotFoundException("Access level not found: " + request.getAccessLevelId()));
            addon.setAccessLevel(accessLevel);
        }

        if (request.getRetentionPeriodId() != null) {
            RetentionPeriod retentionPeriod = retentionPeriodRepository.findById(request.getRetentionPeriodId())
                    .orElseThrow(() -> new ResourceNotFoundException("Retention period not found: " + request.getRetentionPeriodId()));
            addon.setRetentionPeriod(retentionPeriod);
        }

        if (request.getStartDate() != null) {
            addon.setStartDate(request.getStartDate());
        }

        if (request.getEndDate() != null) {
            addon.setEndDate(request.getEndDate());
        }

        if (request.getIsTrial() != null) {
            addon.setIsTrial(request.getIsTrial());
        }

        if (request.getCustomConfig() != null) {
            addon.setCustomConfig(request.getCustomConfig());
        }

        addon.setUpdatedBy(updatedBy);

        TenantAddonFeature saved = tenantAddonFeatureRepository.save(addon);
        log.info("Updated addon feature: {}", addonId);

        return mapToResponse(saved);
    }

    @Transactional
    public TenantAddonFeatureResponse toggleAddonFeature(Long addonId, boolean enabled, String updatedBy) {
        log.info("Toggling addon feature {} to enabled={}", addonId, enabled);

        TenantAddonFeature addon = tenantAddonFeatureRepository.findById(addonId)
                .orElseThrow(() -> new ResourceNotFoundException("Addon feature not found: " + addonId));

        addon.setIsEnabled(enabled);
        addon.setUpdatedBy(updatedBy);

        TenantAddonFeature saved = tenantAddonFeatureRepository.save(addon);
        return mapToResponse(saved);
    }

    @Transactional
    public void deleteAddonFeature(Long addonId) {
        log.info("Deleting addon feature: {}", addonId);
        if (!tenantAddonFeatureRepository.existsById(addonId)) {
            throw new ResourceNotFoundException("Addon feature not found: " + addonId);
        }
        tenantAddonFeatureRepository.deleteById(addonId);
    }

    @Transactional
    public void deleteAddonByTenantAndFeature(String tenantId, Long featureId) {
        log.info("Deleting addon for tenant: {} and feature: {}", tenantId, featureId);
        tenantAddonFeatureRepository.deleteByTenantTenantIDAndFeaturePkFeatureID(tenantId, featureId);
    }

    public boolean hasActiveAddon(String tenantId, String featureCode) {
        return tenantAddonFeatureRepository.findActiveAddonByTenantAndFeatureCode(
                tenantId, featureCode, LocalDate.now()).isPresent();
    }

    private Feature resolveFeature(Long featureId, String featureCode) {
        if (featureId != null) {
            return featureRepository.findById(featureId)
                    .orElseThrow(() -> new ResourceNotFoundException("Feature not found: " + featureId));
        } else if (featureCode != null && !featureCode.isEmpty()) {
            return featureRepository.findByFeatureCode(featureCode)
                    .orElseThrow(() -> new ResourceNotFoundException("Feature not found with code: " + featureCode));
        } else {
            throw new IllegalArgumentException("Either featureId or featureCode must be provided");
        }
    }

    private TenantAddonFeatureResponse mapToResponse(TenantAddonFeature addon) {
        LocalDate today = LocalDate.now();
        boolean isExpired = addon.getEndDate() != null && addon.getEndDate().isBefore(today);

        return TenantAddonFeatureResponse.builder()
                .pkAddonId(addon.getPkAddonId())
                .tenantId(addon.getTenant().getTenantID())
                .tenantName(addon.getTenant().getTenantName())
                .featureId(addon.getFeature().getPkFeatureID())
                .featureName(addon.getFeature().getFeatureName())
                .featureCode(addon.getFeature().getFeatureCode())
                .featureGroupName(addon.getFeature().getFeatureGroup() != null
                        ? addon.getFeature().getFeatureGroup().getGroupName() : null)
                .accessLevelId(addon.getAccessLevel().getPkAccessLevelId())
                .accessLevelName(addon.getAccessLevel().getLevelName())
                .accessLevelValue(addon.getAccessLevel().getLevelValue())
                .retentionPeriodId(addon.getRetentionPeriod() != null
                        ? addon.getRetentionPeriod().getPkRetentionPeriodId() : null)
                .retentionPeriodName(addon.getRetentionPeriod() != null
                        ? addon.getRetentionPeriod().getPeriodName() : null)
                .retentionDays(addon.getRetentionPeriod() != null
                        ? addon.getRetentionPeriod().getPeriodDays() : null)
                .isEnabled(addon.getIsEnabled())
                .startDate(addon.getStartDate())
                .endDate(addon.getEndDate())
                .isTrial(addon.getIsTrial())
                .isExpired(isExpired)
                .customConfig(addon.getCustomConfig())
                .createdAt(addon.getCreatedAt())
                .updatedAt(addon.getUpdatedAt())
                .createdBy(addon.getCreatedBy())
                .updatedBy(addon.getUpdatedBy())
                .build();
    }
}
