package com.secufusion.iam.controller;

import com.secufusion.iam.dto.ResponseDto;
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
    public ResponseEntity<ResponseDto<Package>> createPackage(@RequestBody Package pkg) {
        return ResponseEntity.ok(new ResponseDto<>(
                packageService.createPackage(pkg),
                "200"
        ));
    }


    @PutMapping("/{id}")
    public ResponseEntity<ResponseDto<Package>> updatePackage(
            @PathVariable Long id,
            @RequestBody Package pkg) {
        return ResponseEntity.ok(new ResponseDto<>(packageService.updatePackage(id, pkg),
                "200"
        ));
    }


    @GetMapping("/{id}")
    public ResponseEntity<ResponseDto<Package>> getPackage(@PathVariable Long id) {
        return ResponseEntity.ok(new ResponseDto<>(packageService.getPackage(id),
                "200"
        ));
    }


    @GetMapping
    public ResponseEntity<ResponseDto<List<Package>>> getAllPackages() {
        return ResponseEntity.ok(new ResponseDto<>(packageService.getAllPackages(),
                "200"));
    }


    @DeleteMapping("/{id}")
    public ResponseEntity<ResponseDto<String>> deletePackage(@PathVariable Long id) {
        packageService.deletePackage(id);
        return ResponseEntity.ok(new ResponseDto<>(
                "Package deleted successfully",
                "200"
        ));
    }
}

