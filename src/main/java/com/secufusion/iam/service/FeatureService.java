package com.secufusion.iam.service;

import com.secufusion.iam.dto.CreateFeatureRequest;
import com.secufusion.iam.dto.FeatureResponse;
import com.secufusion.iam.entity.Feature;
import com.secufusion.iam.entity.FeatureGroup;
import com.secufusion.iam.entity.FeatureType;
import com.secufusion.iam.entity.TenantType;
import com.secufusion.iam.exception.ResourceConflictException;
import com.secufusion.iam.exception.ResourceNotFoundException;
import com.secufusion.iam.repository.FeatureGroupRepository;
import com.secufusion.iam.repository.FeatureRepository;
import com.secufusion.iam.repository.FeatureTypeRepository;
import com.secufusion.iam.repository.TenantTypeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.rmi.AlreadyBoundException;
import java.util.List;

@Service
@Slf4j
public class FeatureService {

    @Autowired
    private FeatureRepository featureRepository;

    @Autowired
    private FeatureTypeRepository featureTypeRepository;

    @Autowired
    private TenantTypeRepository tenantTypeRepository;

    @Autowired
    private FeatureGroupRepository featureGroupRepository;

    // CREATE
    public FeatureResponse createFeature(CreateFeatureRequest request) {

        log.info("Creating feature: {}", request.getFeatureName());

        // Unique Feature Name Check
        if (featureRepository.existsByFeatureNameIgnoreCase(request.getFeatureName())) {
            throw new ResourceConflictException("Feature name already exists: " + request.getFeatureName());
        }

        // Unique Feature Code Check
        if (request.getFeatureCode() != null && featureRepository.existsByFeatureCodeIgnoreCase(request.getFeatureCode())) {
            throw new ResourceConflictException("Feature code already exists: " + request.getFeatureCode());
        }

        // Validate Feature Scope (accept either ID or name, optional)
        String featureScopeName = null;
        if (request.getFeatureScopeId() != null) {
            TenantType scope = tenantTypeRepository.findById(request.getFeatureScopeId())
                    .orElseThrow(() -> new ResourceNotFoundException("Invalid featureScopeId: " + request.getFeatureScopeId()));
            featureScopeName = scope.getTenantTypeName();
        } else if (request.getFeatureScope() != null) {
            TenantType scope = tenantTypeRepository.findByTenantTypeNameIgnoreCase(request.getFeatureScope())
                    .orElseThrow(() -> new ResourceNotFoundException("Invalid featureScope: " + request.getFeatureScope()));
            featureScopeName = scope.getTenantTypeName();
        }
        // If no scope provided, leave it null (optional field)

        // Validate Feature Type (accept either ID or name, optional)
        String featureTypeName = null;
        if (request.getFeatureTypeId() != null) {
            FeatureType type = featureTypeRepository.findById(request.getFeatureTypeId())
                    .orElseThrow(() -> new ResourceNotFoundException("Invalid featureTypeId: " + request.getFeatureTypeId()));
            featureTypeName = type.getFeatureTypeName();
        } else if (request.getFeatureType() != null) {
            FeatureType type = featureTypeRepository.findByFeatureTypeNameIgnoreCase(request.getFeatureType())
                    .orElseThrow(() -> new ResourceNotFoundException("Invalid featureType: " + request.getFeatureType()));
            featureTypeName = type.getFeatureTypeName();
        }
        // If no type provided, leave it null (optional field)

        // Validate Feature Group (optional)
        FeatureGroup featureGroup = null;
        if (request.getFeatureGroupId() != null) {
            featureGroup = featureGroupRepository.findById(request.getFeatureGroupId())
                    .orElseThrow(() -> new ResourceNotFoundException("Invalid featureGroupId: " + request.getFeatureGroupId()));
        }

        Feature feature = new Feature();
        feature.setFeatureName(request.getFeatureName());
        feature.setFeatureCode(request.getFeatureCode());
        feature.setDescription(request.getDescription());
        feature.setFeatureScope(featureScopeName);
        feature.setFeatureType(featureTypeName);
        feature.setFeatureGroup(featureGroup);
        feature.setIsAddon(request.getIsAddon() != null ? request.getIsAddon() : false);
        feature.setAddonMonthlyPrice(request.getAddonMonthlyPrice());
        feature.setAddonTrialDays(request.getAddonTrialDays());
        feature.setCreatedBy(request.getCreatedBy());
        feature.setLastModifiedBy(request.getCreatedBy());
        feature.setIsActive(true);

        Feature saved = featureRepository.save(feature);

        log.info("Feature created successfully with ID: {}", saved.getPkFeatureID());
        return mapToResponse(saved);
    }


    // GET ALL
    public List<FeatureResponse> getAllFeatures() {
        log.info("Fetching all features");
        return featureRepository.findAll().stream()
                .map(this::mapToResponse)
                .toList();
    }


    // GET BY ID
    public FeatureResponse getFeature(Long id) {
        log.info("Fetching feature with ID: {}", id);

        Feature feature = featureRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Feature not found"));

        return mapToResponse(feature);
    }


    // UPDATE
    public FeatureResponse updateFeature(Long id, CreateFeatureRequest request) {

        log.info("Updating feature with ID: {}", id);

        Feature existing = featureRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Feature not found"));

        // Unique name check but allow same name
        if (!existing.getFeatureName().equalsIgnoreCase(request.getFeatureName()) &&
                featureRepository.existsByFeatureNameIgnoreCase(request.getFeatureName())) {
            throw new ResourceConflictException("Feature name already exists: " + request.getFeatureName());
        }

        // Unique feature code check but allow same code
        if (request.getFeatureCode() != null &&
                (existing.getFeatureCode() == null || !existing.getFeatureCode().equalsIgnoreCase(request.getFeatureCode())) &&
                featureRepository.existsByFeatureCodeIgnoreCase(request.getFeatureCode())) {
            throw new ResourceConflictException("Feature code already exists: " + request.getFeatureCode());
        }

        // Validate Scope (accept either ID or name, optional)
        String featureScopeName = null;
        if (request.getFeatureScopeId() != null) {
            TenantType scope = tenantTypeRepository.findById(request.getFeatureScopeId())
                    .orElseThrow(() -> new ResourceNotFoundException("Invalid featureScopeId: " + request.getFeatureScopeId()));
            featureScopeName = scope.getTenantTypeName();
        } else if (request.getFeatureScope() != null) {
            TenantType scope = tenantTypeRepository.findByTenantTypeNameIgnoreCase(request.getFeatureScope())
                    .orElseThrow(() -> new ResourceNotFoundException("Invalid featureScope: " + request.getFeatureScope()));
            featureScopeName = scope.getTenantTypeName();
        }
        // If no scope provided, keep existing value
        if (featureScopeName == null) {
            featureScopeName = existing.getFeatureScope();
        }

        // Validate Type (accept either ID or name, optional)
        String featureTypeName = null;
        if (request.getFeatureTypeId() != null) {
            FeatureType type = featureTypeRepository.findById(request.getFeatureTypeId())
                    .orElseThrow(() -> new ResourceNotFoundException("Invalid featureTypeId: " + request.getFeatureTypeId()));
            featureTypeName = type.getFeatureTypeName();
        } else if (request.getFeatureType() != null) {
            FeatureType type = featureTypeRepository.findByFeatureTypeNameIgnoreCase(request.getFeatureType())
                    .orElseThrow(() -> new ResourceNotFoundException("Invalid featureType: " + request.getFeatureType()));
            featureTypeName = type.getFeatureTypeName();
        }
        // If no type provided, keep existing value
        if (featureTypeName == null) {
            featureTypeName = existing.getFeatureType();
        }

        // Validate Feature Group (optional)
        FeatureGroup featureGroup = null;
        if (request.getFeatureGroupId() != null) {
            featureGroup = featureGroupRepository.findById(request.getFeatureGroupId())
                    .orElseThrow(() -> new ResourceNotFoundException("Invalid featureGroupId: " + request.getFeatureGroupId()));
        }

        existing.setFeatureName(request.getFeatureName());
        existing.setFeatureCode(request.getFeatureCode());
        existing.setDescription(request.getDescription());
        existing.setFeatureScope(featureScopeName);
        existing.setFeatureType(featureTypeName);
        existing.setFeatureGroup(featureGroup);
        existing.setIsAddon(request.getIsAddon() != null ? request.getIsAddon() : false);
        existing.setAddonMonthlyPrice(request.getAddonMonthlyPrice());
        existing.setAddonTrialDays(request.getAddonTrialDays());
        existing.setLastModifiedBy(request.getCreatedBy());

        Feature updated = featureRepository.save(existing);

        log.info("Feature updated successfully: {}", id);
        return mapToResponse(updated);
    }


    // DELETE
    public void deleteFeature(Long id) {
        log.warn("Deleting feature with ID: {}", id);

        if (!featureRepository.existsById(id)) {
            throw new ResourceNotFoundException("Feature not found");
        }

        featureRepository.deleteById(id);

        log.info("Deleted feature with ID: {}", id);
    }


    // Convert entity to response
    private FeatureResponse mapToResponse(Feature feature) {
        return FeatureResponse.builder()
                .featureId(feature.getPkFeatureID())
                .featureName(feature.getFeatureName())
                .featureCode(feature.getFeatureCode())
                .description(feature.getDescription())
                .featureScope(feature.getFeatureScope())
                .featureType(feature.getFeatureType())
                .featureGroupId(feature.getFeatureGroup() != null ? feature.getFeatureGroup().getPkFeatureGroupId() : null)
                .featureGroupName(feature.getFeatureGroup() != null ? feature.getFeatureGroup().getGroupName() : null)
                .isActive(feature.getIsActive())
                .isAddon(feature.getIsAddon())
                .addonMonthlyPrice(feature.getAddonMonthlyPrice())
                .addonTrialDays(feature.getAddonTrialDays())
                .lastModifiedTimestamp(feature.getLastModifiedTimestamp())
                .build();
    }
}
