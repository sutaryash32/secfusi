package com.secufusion.iam.openFeatureService.controller;

import com.secufusion.iam.openFeatureService.repository.FeatureFlagsRepository;
import com.secufusion.iam.openFeatureService.dto.CreateFlagRequest;
import com.secufusion.iam.openFeatureService.dto.UpdateFlagRequest;
import com.secufusion.iam.openFeatureService.entity.FeatureFlags;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/admin/feature-flags")
public class FeatureFlagAdminController {

    @Autowired
    private FeatureFlagsRepository featureFlagRepository;

    // GET all flags
    @GetMapping
    public ResponseEntity<List<FeatureFlags>> getAllFlags() {
        List<FeatureFlags> flags = featureFlagRepository.findAll();
        return ResponseEntity.ok(flags);
    }

    // GET single flag by key
    @GetMapping("/{flagKey}")
    public ResponseEntity<?> getFlag(@PathVariable String flagKey) {
        Optional<FeatureFlags> flag = featureFlagRepository.findByFlagKey(flagKey);
        return flag.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // PUT update flag (toggle enabled/disabled)
    @PutMapping("/{flagKey}")
    public ResponseEntity<?> updateFlag(@PathVariable String flagKey,
                                        @RequestBody UpdateFlagRequest request) {

        Optional<FeatureFlags> existingFlagOpt = featureFlagRepository.findByFlagKey(flagKey);

        if (existingFlagOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        FeatureFlags flag = existingFlagOpt.get();
        flag.setEnabled(request.isEnabled());
        flag.setDescription(request.getDescription());
        flag.setUpdatedAt(Instant.now());

        FeatureFlags updatedFlag = featureFlagRepository.save(flag);
        return ResponseEntity.ok(updatedFlag);
    }

    // POST create new flag
    @PostMapping
    public ResponseEntity<?> createFlag(@RequestBody CreateFlagRequest request) {
        FeatureFlags newFlag = new FeatureFlags();
        newFlag.setFlagKey(request.getFlagKey());
        newFlag.setEnabled(request.isEnabled());
        newFlag.setDescription(request.getDescription());
        newFlag.setUpdatedAt(Instant.now());

        FeatureFlags savedFlag = featureFlagRepository.save(newFlag);
        return ResponseEntity.ok(savedFlag);
    }
}
