package com.secufusion.iam.service;

import com.secufusion.iam.dto.CreateFeatureRequest;
import com.secufusion.iam.dto.FeatureResponse;
import com.secufusion.iam.entity.Feature;
import com.secufusion.iam.entity.FeatureType;
import com.secufusion.iam.entity.TenantType;
import com.secufusion.iam.repository.FeatureRepository;
import com.secufusion.iam.repository.FeatureTypeRepository;
import com.secufusion.iam.repository.TenantTypeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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

    // CREATE
    public FeatureResponse createFeature(CreateFeatureRequest request) {

        log.info("Creating feature: {}", request.getFeatureName());

        // Unique Feature Name Check
        if (featureRepository.existsByFeatureNameIgnoreCase(request.getFeatureName())) {
            throw new RuntimeException("Feature already exists: " + request.getFeatureName());
        }

        // Validate Feature Scope
        TenantType scope = tenantTypeRepository.findById(request.getFeatureScopeId())
                .orElseThrow(() -> new RuntimeException("Invalid featureScopeId: " + request.getFeatureScopeId()));

        // Validate Feature Type
        FeatureType type = featureTypeRepository.findById(request.getFeatureTypeId())
                .orElseThrow(() -> new RuntimeException("Invalid featureTypeId: " + request.getFeatureTypeId()));

        Feature feature = new Feature();
        feature.setFeatureName(request.getFeatureName());
        feature.setDescription(request.getDescription());
        feature.setFeatureScope(scope.getTenantTypeName());
        feature.setFeatureType(type.getFeatureTypeName());
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
                .orElseThrow(() -> new RuntimeException("Feature not found"));

        return mapToResponse(feature);
    }


    // UPDATE
    public FeatureResponse updateFeature(Long id, CreateFeatureRequest request) {

        log.info("Updating feature with ID: {}", id);

        Feature existing = featureRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Feature not found"));

        // Unique name check but allow same name
        if (!existing.getFeatureName().equalsIgnoreCase(request.getFeatureName()) &&
                featureRepository.existsByFeatureNameIgnoreCase(request.getFeatureName())) {
            throw new RuntimeException("Feature name already exists: " + request.getFeatureName());
        }

        // Validate Scope
        TenantType scope = tenantTypeRepository.findById(request.getFeatureScopeId())
                .orElseThrow(() -> new RuntimeException("Invalid featureScopeId"));

        // Validate Type
        FeatureType type = featureTypeRepository.findById(request.getFeatureTypeId())
                .orElseThrow(() -> new RuntimeException("Invalid featureTypeId"));

        existing.setFeatureName(request.getFeatureName());
        existing.setDescription(request.getDescription());
        existing.setFeatureScope(scope.getTenantTypeName());
        existing.setFeatureType(type.getFeatureTypeName());
        existing.setLastModifiedBy(request.getCreatedBy());

        Feature updated = featureRepository.save(existing);

        log.info("Feature updated successfully: {}", id);
        return mapToResponse(updated);
    }


    // DELETE
    public void deleteFeature(Long id) {
        log.warn("Deleting feature with ID: {}", id);

        if (!featureRepository.existsById(id)) {
            throw new RuntimeException("Feature not found");
        }

        featureRepository.deleteById(id);

        log.info("Deleted feature with ID: {}", id);
    }


    // Convert entity to response
    private FeatureResponse mapToResponse(Feature feature) {
        return FeatureResponse.builder()
                .featureId(feature.getPkFeatureID())
                .featureName(feature.getFeatureName())
                .description(feature.getDescription())
                .featureScope(feature.getFeatureScope())
                .featureType(feature.getFeatureType())
                .isActive(feature.getIsActive())
                .lastModifiedTimestamp(feature.getLastModifiedTimestamp())
                .build();
    }
}
