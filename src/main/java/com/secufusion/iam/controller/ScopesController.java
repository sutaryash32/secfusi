package com.secufusion.iam.controller;

import com.secufusion.iam.dto.CreateScopeRequest;
import com.secufusion.iam.dto.ResponseDto;
import com.secufusion.iam.dto.UpdateScopeTenantTypesRequest;
import com.secufusion.iam.entity.Scopes;
import com.secufusion.iam.service.ScopesService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
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
    public ResponseEntity<ResponseDto<Scopes>> updateScopeTenantTypes(@PathVariable String scopeId,
            @RequestBody UpdateScopeTenantTypesRequest request
    ) {
        return ResponseEntity.ok(
                new ResponseDto<>(
                        scopesService.updateScopeTenantTypes(scopeId, request.getTenantTypes()),
                        String.valueOf(HttpStatus.OK.value())
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
    public ResponseEntity<ResponseDto<Scopes>> getScopeById(@PathVariable String scopeId
    ) {
        return ResponseEntity.ok(
                new ResponseDto<>(
                        scopesService.getScopeById(scopeId),
                        String.valueOf(HttpStatus.OK.value())
                )
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
    public ResponseEntity<ResponseDto<List<Scopes>>> getScopesByMenu(@PathVariable String menuName
    ) {
        return ResponseEntity.ok(
                new ResponseDto<>(
                        scopesService.getScopesByMenu(menuName),
                        String.valueOf(HttpStatus.OK.value())
                )
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
    public ResponseEntity<ResponseDto<List<Scopes>>> getScopesByMenuAndSubMenu(@PathVariable String menuName,@PathVariable String subMenu
    ) {
        return ResponseEntity.ok(
                new ResponseDto<>(
                        scopesService.getScopesByMenuAndSubMenu(menuName, subMenu),
                        String.valueOf(HttpStatus.OK.value())
                )
        );
    }

    // ---------------------------------------------------
    // CREATE SCOPE
    // ---------------------------------------------------
    @Operation(summary = "Create a new scope", description = "Creates a new scope with the provided details.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Scope created successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Scopes.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping
    public ResponseEntity<ResponseDto<Scopes>> createScope(
            @RequestBody CreateScopeRequest request,
            HttpServletRequest httpRequest
    ) {
        logger.info("Request received: POST /scopes from remoteAddr={}", httpRequest.getRemoteAddr());
        Scopes createdScope = scopesService.createScope(
                request.getScopeName(),
                request.getDisplayName(),
                request.getDescription(),
                request.getUserType(),
                request.getMenuName(),
                request.getAction(),
                request.getSubMenu(),
                request.getTenantTypes()
        );
        logger.debug("Scope created: scopeId={}", createdScope.getPkScopeId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ResponseDto<>(createdScope, String.valueOf(HttpStatus.CREATED.value())));
    }

    // ---------------------------------------------------
    // DELETE SCOPE
    // ---------------------------------------------------
    @Operation(summary = "Delete a scope", description = "Deletes a scope by its ID. The scope will be automatically removed from all associated roles.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Scope deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Scope not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @DeleteMapping("/{scopeId}")
    public ResponseEntity<ResponseDto<String>> deleteScope(
            @Parameter(description = "ID of the scope to delete", required = true)
            @PathVariable String scopeId,
            HttpServletRequest httpRequest
    ) {
        logger.info("Request received: DELETE /scopes/{} from remoteAddr={}", scopeId, httpRequest.getRemoteAddr());
        scopesService.deleteScope(scopeId);
        logger.info("Scope deleted: scopeId={}", scopeId);
        return ResponseEntity.ok(
                new ResponseDto<>(
                        "Scope deleted successfully",
                        String.valueOf(HttpStatus.OK.value())
                )
        );
    }


}