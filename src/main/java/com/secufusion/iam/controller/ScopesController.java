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
            @ApiResponse(responseCode = "200", description = "List of scopes returned",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Scopes.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping
    public ResponseEntity<ResponseDto<List<Scopes>>> getAllScopes(HttpServletRequest request) {
        logger.info("Request received: GET /scopes from remoteAddr={}", request.getRemoteAddr());
        List<Scopes> scopesList = scopesService.getAllScopes(request);
        logger.debug("Scopes retrieved: count={}", scopesList != null ? scopesList.size() : 0);
        return ResponseEntity.ok(new ResponseDto<>(scopesList, String.valueOf(HttpStatus.OK.value())));
    }


    @Operation(summary = "Update scope tenant types",
            description = "Update the tenant types associated with the given scope.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Scope updated",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Scopes.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Scope not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PutMapping("/{scopeId}/tenant-types")
    public ResponseEntity<Scopes> updateScopeTenantTypes(@PathVariable String scopeId,
            @RequestBody UpdateScopeTenantTypesRequest request
    ) {
        return ResponseEntity.ok(
                scopesService.updateScopeTenantTypes(
                        scopeId,
                        request.getTenantTypes()
                )
        );
    }

    // ---------------------------------------------------
    // GET SCOPE BY ID
    // ---------------------------------------------------
    @Operation(summary = "Get scope by id", description = "Retrieve a single scope by its id.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Scope returned",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Scopes.class))),
            @ApiResponse(responseCode = "404", description = "Scope not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/{scopeId}")
    public ResponseEntity<Scopes> getScopeById(@PathVariable String scopeId
    ) {
        return ResponseEntity.ok(
                scopesService.getScopeById(scopeId)
        );
    }

    // ---------------------------------------------------
    // GET SCOPES BY MENU NAME
    // ---------------------------------------------------
    @Operation(summary = "Get scopes by menu", description = "Retrieve scopes belonging to a specific menu.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Scopes returned",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Scopes.class))),
            @ApiResponse(responseCode = "404", description = "No scopes found for menu"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/menu/{menuName}")
    public ResponseEntity<List<Scopes>> getScopesByMenu(@PathVariable String menuName
    ) {
        return ResponseEntity.ok(
                scopesService.getScopesByMenu(menuName)
        );
    }

    // ---------------------------------------------------
    // GET SCOPES BY MENU + SUBMENU
    // ---------------------------------------------------
    @Operation(summary = "Get scopes by menu and submenu", description = "Retrieve scopes for a specific menu and submenu.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Scopes returned",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Scopes.class))),
            @ApiResponse(responseCode = "404", description = "No scopes found for menu/submenu"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/menu/{menuName}/submenu/{subMenu}")
    public ResponseEntity<List<Scopes>> getScopesByMenuAndSubMenu(@PathVariable String menuName,@PathVariable String subMenu
    ) {
        return ResponseEntity.ok(
                scopesService.getScopesByMenuAndSubMenu(
                        menuName, subMenu
                )
        );
    }
}