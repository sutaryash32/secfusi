package com.secufusion.iam.controller;

import com.secufusion.iam.dto.AccessLevelResponse;
import com.secufusion.iam.dto.ResponseDto;
import com.secufusion.iam.entity.AccessLevel;
import com.secufusion.iam.repository.AccessLevelRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*", allowedHeaders = "*")
@RestController
@RequestMapping("/access-levels")
@Tag(name = "Access Levels", description = "Retrieve access level definitions for the package-feature matrix")
public class AccessLevelController {

    @Autowired
    private AccessLevelRepository accessLevelRepository;

    @GetMapping
    @Operation(summary = "Get all access levels", description = "Retrieve all access levels ordered by value (lowest to highest)")
    @ApiResponse(responseCode = "200", description = "Access levels retrieved successfully")
    public ResponseEntity<ResponseDto<List<AccessLevelResponse>>> getAll() {
        List<AccessLevelResponse> response = accessLevelRepository.findAllByOrderByLevelValueAsc().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(new ResponseDto<>(response, "200"));
    }

    @GetMapping("/active")
    @Operation(summary = "Get active access levels", description = "Retrieve only active access levels")
    @ApiResponse(responseCode = "200", description = "Active access levels retrieved successfully")
    public ResponseEntity<ResponseDto<List<AccessLevelResponse>>> getActive() {
        List<AccessLevelResponse> response = accessLevelRepository.findByIsActiveTrueOrderByLevelValueAsc().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(new ResponseDto<>(response, "200"));
    }

    private AccessLevelResponse mapToResponse(AccessLevel al) {
        return AccessLevelResponse.builder()
                .accessLevelId(al.getPkAccessLevelId())
                .levelName(al.getLevelName())
                .levelCode(al.getLevelCode())
                .levelValue(al.getLevelValue())
                .description(al.getDescription())
                .isActive(al.getIsActive())
                .build();
    }
}
