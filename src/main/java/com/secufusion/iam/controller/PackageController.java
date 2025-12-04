package com.secufusion.iam.controller;

import com.secufusion.iam.entity.Package;
import com.secufusion.iam.service.PackageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "*", allowedHeaders = "*")
@RestController
@RequestMapping("/packages")
public class PackageController {

    @Autowired
    private PackageService packageService;


    @PostMapping
    public ResponseEntity<Package> createPackage(@RequestBody Package pkg) {
        return ResponseEntity.ok(packageService.createPackage(pkg));
    }


    @PutMapping("/{id}")
    public ResponseEntity<Package> updatePackage(
            @PathVariable Long id,
            @RequestBody Package pkg) {
        return ResponseEntity.ok(packageService.updatePackage(id, pkg));
    }


    @GetMapping("/{id}")
    public ResponseEntity<Package> getPackage(@PathVariable Long id) {
        return ResponseEntity.ok(packageService.getPackage(id));
    }


    @GetMapping
    public ResponseEntity<List<Package>> getAllPackages() {
        return ResponseEntity.ok(packageService.getAllPackages());
    }


    @DeleteMapping("/{id}")
    public ResponseEntity<String> deletePackage(@PathVariable Long id) {
        packageService.deletePackage(id);
        return ResponseEntity.ok("Package deleted");
    }
}

