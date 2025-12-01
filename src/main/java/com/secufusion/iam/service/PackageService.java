package com.secufusion.iam.service;

import com.secufusion.iam.entity.Feature;
import com.secufusion.iam.entity.Package;
import com.secufusion.iam.entity.PackageType;
import com.secufusion.iam.exception.ResourceNotFoundException;
import com.secufusion.iam.repository.FeatureRepository;
import com.secufusion.iam.repository.PackageRepository;
import com.secufusion.iam.repository.PackageTypeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PackageService {

    @Autowired
    private PackageRepository packageRepository;

    @Autowired
    private PackageTypeRepository packageTypeRepository;

    @Autowired
    private FeatureRepository featureRepository;

    // CREATE
    public Package createPackage(Package pkg) {

        // Validate package name unique
        if (packageRepository.existsByPackageNameIgnoreCase(pkg.getPackageName())) {
            throw new ResourceNotFoundException("Package name already exists: " + pkg.getPackageName());
        }

        // Validate package type
        if (pkg.getPackageType() != null && pkg.getPackageType().getPkPackageTypeId() != null) {
            PackageType type = packageTypeRepository.findById(pkg.getPackageType().getPkPackageTypeId())
                    .orElseThrow(() -> new ResourceNotFoundException("Invalid Package Type"));
            pkg.setPackageType(type);
        }

        // Validate features
        if (pkg.getFeatures() != null && !pkg.getFeatures().isEmpty()) {
            Set<Long> ids = pkg.getFeatures().stream()
                    .map(Feature::getPkFeatureID)
                    .collect(Collectors.toSet());

            Set<Feature> features = new HashSet<>(featureRepository.findAllById(ids));
            pkg.setFeatures(features);
        }

        pkg.setCreatedAt(LocalDateTime.now());
        pkg.setUpdatedAt(LocalDateTime.now());

        return packageRepository.save(pkg);
    }


    // UPDATE
    public Package updatePackage(Long id, Package update) {

        Package existing = packageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Package not found"));

        // Validate unique name (allow same name if unchanged)
        if (!existing.getPackageName().equalsIgnoreCase(update.getPackageName()) &&
                packageRepository.existsByPackageNameIgnoreCase(update.getPackageName())) {
            throw new ResourceNotFoundException("Package name already exists: " + update.getPackageName());
        }

        existing.setPackageName(update.getPackageName());
        existing.setDescription(update.getDescription());
        existing.setUpdatedAt(LocalDateTime.now());
        existing.setUpdatedBy(update.getUpdatedBy());

        // Update type
        if (update.getPackageType() != null) {
            PackageType type = packageTypeRepository.findById(update.getPackageType().getPkPackageTypeId())
                    .orElseThrow(() -> new ResourceNotFoundException("Invalid packageTypeId"));
            existing.setPackageType(type);
        }

        // Update features
        if (update.getFeatures() != null) {
            Set<Long> ids = update.getFeatures().stream()
                    .map(Feature::getPkFeatureID)
                    .collect(Collectors.toSet());
            Set<Feature> features = new HashSet<>(featureRepository.findAllById(ids));
            existing.setFeatures(features);
        }

        return packageRepository.save(existing);
    }


    // READ BY ID
    public Package getPackage(Long id) {
        return packageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Package not found"));
    }

    // READ ALL
    public List<Package> getAllPackages() {
        return packageRepository.findAll();
    }

    // DELETE
    public void deletePackage(Long id) {
        if (!packageRepository.existsById(id)) {
            throw new ResourceNotFoundException("Package not found");
        }
        packageRepository.deleteById(id);
    }
}
