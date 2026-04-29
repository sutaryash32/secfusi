package com.secufusion.tenant.controller;

import com.secufusion.tenant.dto.LoggedInUserDetailsBean;
import com.secufusion.tenant.entity.LandingPage;
import com.secufusion.tenant.service.LandingPageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/tenants/landing-pages")
@Tag(name = "Landing Page", description = "APIs for managing Landing Pages")
public class LandingPageController {

    @Autowired
    private LandingPageService landingPageService;

    /* ==========================================================
                           CREATE
       ========================================================== */

    @Operation(summary = "Create Landing Page")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Landing Page created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PostMapping
    public ResponseEntity<LandingPage> createLandingPage(
            HttpServletRequest request,
            @RequestBody LandingPage landingPage
    ) {
        log.info("ENTER createLandingPage - name={}", landingPage.getName());

        LoggedInUserDetailsBean loggedInUserDetailsBean =
                (LoggedInUserDetailsBean) request.getAttribute("loggedInUser");
        String tenantId = loggedInUserDetailsBean.getTenantId();
        landingPage.setFkTenantId(tenantId);

        LandingPage created = landingPageService.createLandingPage(landingPage);
        log.info("EXIT createLandingPage - id={}", created.getPkLandingPageId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /* ==========================================================
                           GET ALL
       ========================================================== */

    @Operation(summary = "Get all Landing Pages")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Landing Pages fetched successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping
    public ResponseEntity<List<LandingPage>> getAllLandingPages() {
        log.info("ENTER getAllLandingPages");
        List<LandingPage> landingPages = landingPageService.getAllLandingPages();
        log.info("EXIT getAllLandingPages - count={}", landingPages.size());
        return ResponseEntity.ok(landingPages);
    }

    /* ==========================================================
                           GET BY ID
       ========================================================== */

    @Operation(summary = "Get Landing Page by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Landing Page fetched successfully"),
            @ApiResponse(responseCode = "404", description = "Landing Page not found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/{id}")
    public ResponseEntity<LandingPage> getLandingPageById(@PathVariable String id) {
        log.info("ENTER getLandingPageById - id={}", id);
        LandingPage landingPage = landingPageService.getLandingPageById(id);
        log.info("EXIT getLandingPageById - id={}", id);
        return ResponseEntity.ok(landingPage);
    }

    /* ==========================================================
                           UPDATE
       ========================================================== */

    @Operation(summary = "Update Landing Page")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Landing Page updated successfully"),
            @ApiResponse(responseCode = "404", description = "Landing Page not found"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PutMapping("/{id}")
    public ResponseEntity<LandingPage> updateLandingPage(
            @PathVariable String id,
            @RequestBody LandingPage landingPage
    ) {
        log.info("ENTER updateLandingPage - id={}", id);
        LandingPage updated = landingPageService.updateLandingPage(id, landingPage);
        log.info("EXIT updateLandingPage - id={}", updated.getPkLandingPageId());
        return ResponseEntity.ok(updated);
    }

    /* ==========================================================
                           DELETE
       ========================================================== */

    @Operation(summary = "Delete Landing Page")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Landing Page deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Landing Page not found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLandingPage(@PathVariable String id) {
        log.info("ENTER deleteLandingPage - id={}", id);
        landingPageService.deleteLandingPage(id);
        log.info("EXIT deleteLandingPage - id={}", id);
        return ResponseEntity.noContent().build();
    }
}
