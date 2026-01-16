package com.secufusion.iam.controller;

import com.secufusion.iam.dto.ResponseDto;
import com.secufusion.iam.dto.RetentionPeriodResponse;
import com.secufusion.iam.entity.RetentionPeriod;
import com.secufusion.iam.repository.RetentionPeriodRepository;
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
@RequestMapping("/retention-periods")
@Tag(name = "Retention Periods", description = "Retrieve retention period definitions for the package-feature matrix")
public class RetentionPeriodController {

    @Autowired
    private RetentionPeriodRepository retentionPeriodRepository;

    @GetMapping
    @Operation(summary = "Get all retention periods", description = "Retrieve all retention periods ordered by days (shortest to longest)")
    @ApiResponse(responseCode = "200", description = "Retention periods retrieved successfully")
    public ResponseEntity<ResponseDto<List<RetentionPeriodResponse>>> getAll() {
        List<RetentionPeriodResponse> response = retentionPeriodRepository.findAllByOrderByPeriodDaysAsc().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(new ResponseDto<>(response, "200"));
    }

    @GetMapping("/active")
    @Operation(summary = "Get active retention periods", description = "Retrieve only active retention periods")
    @ApiResponse(responseCode = "200", description = "Active retention periods retrieved successfully")
    public ResponseEntity<ResponseDto<List<RetentionPeriodResponse>>> getActive() {
        List<RetentionPeriodResponse> response = retentionPeriodRepository.findByIsActiveTrueOrderByPeriodDaysAsc().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(new ResponseDto<>(response, "200"));
    }

    private RetentionPeriodResponse mapToResponse(RetentionPeriod rp) {
        return RetentionPeriodResponse.builder()
                .retentionPeriodId(rp.getPkRetentionPeriodId())
                .periodName(rp.getPeriodName())
                .periodCode(rp.getPeriodCode())
                .periodDays(rp.getPeriodDays())
                .description(rp.getDescription())
                .isActive(rp.getIsActive())
                .build();
    }
}
