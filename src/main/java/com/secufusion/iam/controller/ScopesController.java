package com.secufusion.iam.controller;

import com.secufusion.iam.entity.Scopes;
import com.secufusion.iam.service.ScopesService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import java.util.List;

/**
 * REST controller exposing endpoints to work with Scopes.
 * Provides an endpoint to retrieve all configured scopes.
 */
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RestController
@RequestMapping("/scopes")
@Tag(name = "Scopes", description = "Operations related to OAuth scopes")
public class ScopesController {

    private static final Logger logger = LoggerFactory.getLogger(ScopesController.class);

    @Autowired
    private ScopesService scopesService;

    /**
     * Retrieve all scopes.
     *
     * @param request HTTP servlet request (used for logging / audit)
     * @return list of scopes wrapped in a ResponseEntity with status 200
     */
    @Operation(summary = "Get all scopes", description = "Returns a list of all available scopes.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of scopes returned"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping
    public ResponseEntity<List<Scopes>> getAllScopes(HttpServletRequest request) {
        logger.info("Request received: GET /scopes from remoteAddr={}", request.getRemoteAddr());
        List<Scopes> scopesList = scopesService.getAllScopes(request);
        logger.debug("Scopes retrieved: count={}", scopesList != null ? scopesList.size() : 0);
        return ResponseEntity.ok(scopesList);
    }
}