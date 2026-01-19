package com.secufusion.iam.service;

import com.secufusion.iam.dto.CreateFeatureGroupRequest;
import com.secufusion.iam.dto.FeatureGroupResponse;
import com.secufusion.iam.dto.FeatureResponse;
import com.secufusion.iam.entity.FeatureGroup;
import com.secufusion.iam.exception.ResourceConflictException;
import com.secufusion.iam.exception.ResourceNotFoundException;
import com.secufusion.iam.repository.FeatureGroupRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional
public class FeatureGroupService {

    @Autowired
    private FeatureGroupRepository featureGroupRepository;

    public FeatureGroupResponse create(CreateFeatureGroupRequest request) {
        log.info("Creating feature group: {}", request.getGroupCode());

        if (featureGroupRepository.existsByGroupCodeIgnoreCase(request.getGroupCode())) {
            throw new ResourceConflictException("Feature group with code '" + request.getGroupCode() + "' already exists");
        }

        if (featureGroupRepository.existsByGroupNameIgnoreCase(request.getGroupName())) {
            throw new ResourceConflictException("Feature group with name '" + request.getGroupName() + "' already exists");
        }

        FeatureGroup featureGroup = new FeatureGroup();
        featureGroup.setGroupName(request.getGroupName());
        featureGroup.setGroupCode(request.getGroupCode().toUpperCase());
        featureGroup.setDescription(request.getDescription());
        featureGroup.setDisplayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0);
        featureGroup.setIsActive(true);

        FeatureGroup saved = featureGroupRepository.save(featureGroup);
        log.info("Feature group created with ID: {}", saved.getPkFeatureGroupId());

        return mapToResponse(saved, false);
    }

    public FeatureGroupResponse update(Long id, CreateFeatureGroupRequest request) {
        log.info("Updating feature group: {}", id);

        FeatureGroup existing = featureGroupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Feature group not found with ID: " + id));

        // Check for duplicate code (if changed)
        if (!existing.getGroupCode().equalsIgnoreCase(request.getGroupCode()) &&
                featureGroupRepository.existsByGroupCodeIgnoreCase(request.getGroupCode())) {
            throw new ResourceConflictException("Feature group with code '" + request.getGroupCode() + "' already exists");
        }

        // Check for duplicate name (if changed)
        if (!existing.getGroupName().equalsIgnoreCase(request.getGroupName()) &&
                featureGroupRepository.existsByGroupNameIgnoreCase(request.getGroupName())) {
            throw new ResourceConflictException("Feature group with name '" + request.getGroupName() + "' already exists");
        }

        existing.setGroupName(request.getGroupName());
        existing.setGroupCode(request.getGroupCode().toUpperCase());
        existing.setDescription(request.getDescription());
        if (request.getDisplayOrder() != null) {
            existing.setDisplayOrder(request.getDisplayOrder());
        }

        FeatureGroup updated = featureGroupRepository.save(existing);
        log.info("Feature group updated: {}", id);

        return mapToResponse(updated, false);
    }

    @Transactional(readOnly = true)
    public FeatureGroupResponse getById(Long id) {
        log.info("Fetching feature group: {}", id);

        FeatureGroup featureGroup = featureGroupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Feature group not found with ID: " + id));

        return mapToResponse(featureGroup, true);
    }

    @Transactional(readOnly = true)
    public FeatureGroupResponse getByCode(String code) {
        log.info("Fetching feature group by code: {}", code);

        FeatureGroup featureGroup = featureGroupRepository.findByGroupCode(code.toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("Feature group not found with code: " + code));

        return mapToResponse(featureGroup, true);
    }

    @Transactional(readOnly = true)
    public List<FeatureGroupResponse> getAll() {
        log.info("Fetching all feature groups");

        return featureGroupRepository.findAllByOrderByDisplayOrderAsc().stream()
                .map(fg -> mapToResponse(fg, false))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<FeatureGroupResponse> getActiveGroups() {
        log.info("Fetching active feature groups");

        return featureGroupRepository.findByIsActiveTrueOrderByDisplayOrderAsc().stream()
                .map(fg -> mapToResponse(fg, false))
                .collect(Collectors.toList());
    }

    public void delete(Long id) {
        log.info("Deleting feature group: {}", id);

        if (!featureGroupRepository.existsById(id)) {
            throw new ResourceNotFoundException("Feature group not found with ID: " + id);
        }

        featureGroupRepository.deleteById(id);
        log.info("Feature group deleted: {}", id);
    }

    public FeatureGroupResponse toggleActive(Long id, boolean active) {
        log.info("Setting feature group {} active status to: {}", id, active);

        FeatureGroup featureGroup = featureGroupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Feature group not found with ID: " + id));

        featureGroup.setIsActive(active);
        FeatureGroup updated = featureGroupRepository.save(featureGroup);

        return mapToResponse(updated, false);
    }

    private FeatureGroupResponse mapToResponse(FeatureGroup featureGroup, boolean includeFeatures) {
        FeatureGroupResponse.FeatureGroupResponseBuilder builder = FeatureGroupResponse.builder()
                .featureGroupId(featureGroup.getPkFeatureGroupId())
                .groupName(featureGroup.getGroupName())
                .groupCode(featureGroup.getGroupCode())
                .description(featureGroup.getDescription())
                .displayOrder(featureGroup.getDisplayOrder())
                .isActive(featureGroup.getIsActive())
                .createdAt(featureGroup.getCreatedAt())
                .updatedAt(featureGroup.getUpdatedAt());

        if (includeFeatures && featureGroup.getFeatures() != null) {
            List<FeatureResponse> features = featureGroup.getFeatures().stream()
                    .map(f -> FeatureResponse.builder()
                            .featureId(f.getPkFeatureID())
                            .featureName(f.getFeatureName())
                            .description(f.getDescription())
                            .featureScope(f.getFeatureScope())
                            .featureType(f.getFeatureType())
                            .isActive(f.getIsActive())
                            .lastModifiedTimestamp(f.getLastModifiedTimestamp())
                            .build())
                    .collect(Collectors.toList());
            builder.features(features);
        }

        return builder.build();
    }
}
