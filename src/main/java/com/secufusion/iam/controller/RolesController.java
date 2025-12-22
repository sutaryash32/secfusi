package com.secufusion.iam.controller;

import com.secufusion.iam.dto.ResponseDto;
import com.secufusion.iam.dto.RoleDropdownResponse;
import com.secufusion.iam.dto.RolesDto;
import com.secufusion.iam.entity.Roles;
import com.secufusion.iam.service.RoleService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * REST controller for managing roles.
 * Provides CRUD endpoints and a dropdown-friendly list of roles.
 */
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RestController
@RequestMapping("/roles")
@Tag(name = "Roles", description = "APIs for role management")
public class RolesController {

    private static final Logger logger = LoggerFactory.getLogger(RolesController.class);

    @Autowired
    private RoleService roleService;

    /**
     * Create a new role.
     *
     * @param request servlet request (for auth/context)
     * @param roles   role payload
     * @return created Roles entity
     */
    @Operation(summary = "Create role", description = "Create a new role with the provided details.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Role created",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Roles.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping
    public ResponseEntity<ResponseDto<Roles>> createRole(HttpServletRequest request, @RequestBody RolesDto roles) {
        logger.info("POST /roles - createRole called with name={}", roles != null ? roles.getName() : "null");
        Roles savedRole = roleService.createRoles(request, roles);
        logger.info("POST /roles - role created with id={}", savedRole != null ? savedRole.getPkRoleId() : "null");
        return ResponseEntity.ok(new ResponseDto<>(savedRole, String.valueOf(HttpStatus.CREATED.value())));
    }

    /**
     * Update an existing role.
     *
     * @param request servlet request (for auth/context)
     * @param id      role identifier
     * @param roles   updated role payload
     * @return updated Roles entity
     */
    @Operation(summary = "Update role", description = "Update an existing role by id.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Role updated",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Roles.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Role not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PutMapping("/{id}")
    public ResponseEntity<ResponseDto<Roles>> updateRole(
            HttpServletRequest request,
            @Parameter(description = "ID of the role to update") @PathVariable String id,
            @RequestBody RolesDto roles) {
        logger.info("PUT /roles/{} - updateRole called", id);
        Roles updatedRole = roleService.updateRole(request, id, roles);
        logger.info("PUT /roles/{} - update completed", id);
        return ResponseEntity.ok(new ResponseDto<>(updatedRole, String.valueOf(HttpStatus.OK.value())));
    }

    /**
     * Retrieve all roles.
     *
     * @param request servlet request (for auth/context)
     * @return list of Roles
     */
    @Operation(summary = "Get all roles", description = "Retrieve a list of all roles.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of roles",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Roles.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping
    public ResponseEntity<ResponseDto<List<Roles>>> getAllRoles(HttpServletRequest request) {
        logger.info("GET /roles - getAllRoles called");
        List<Roles> rolesList = roleService.getAllRoles(request);
        logger.info("GET /roles - returning {} roles", rolesList != null ? rolesList.size() : 0);
        return ResponseEntity.ok(new ResponseDto<>(rolesList, String.valueOf(HttpStatus.OK.value())));
    }

    /**
     * Retrieve a role by id.
     *
     * @param request servlet request (for auth/context)
     * @param id      role identifier
     * @return Roles entity
     */
    @Operation(summary = "Get role by id", description = "Retrieve a role by its id.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Role found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Roles.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Role not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ResponseDto<Roles>> getRoleById(
            HttpServletRequest request,
            @Parameter(description = "ID of the role to retrieve") @PathVariable String id) {
        logger.info("GET /roles/{} - getRoleById called", id);
        Roles role = roleService.getRoleById(request, id);
        logger.info("GET /roles/{} - retrieval completed", id);
        return ResponseEntity.ok(new ResponseDto<>(role, String.valueOf(HttpStatus.OK.value())));
    }

    @PostMapping("/activate")
    public ResponseEntity<ResponseDto<Roles>> activateRole(
            HttpServletRequest request,
            @RequestParam String roleId) {
        logger.info("POST /roles/activate - activateRole called for roleId={}", roleId);
        Roles activatedRole = roleService.updateRoleActive(request, roleId);
        logger.info("POST /roles/activate - role activated for roleId={}", roleId);
        return ResponseEntity.ok(new ResponseDto<>(activatedRole, String.valueOf(HttpStatus.OK.value())));
    }
}