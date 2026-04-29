package com.secufusion.iam.controller;


import com.secufusion.iam.dto.GroupsDropdown;
import com.secufusion.iam.dto.ResponseDto;
import com.secufusion.iam.entity.Groups;
import com.secufusion.iam.service.GroupService;
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
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import java.nio.file.AccessDeniedException;
import java.util.List;

/**
 * REST controller for managing Groups.
 * Provides endpoints to create groups, fetch a group by id, list all groups and obtain dropdown data.
 */
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RestController
@RequestMapping("/groups")
@Tag(name = "Groups", description = "Endpoints for group management")
public class GroupController {

    private static final Logger logger = LoggerFactory.getLogger(GroupController.class);

    @Autowired
    private GroupService groupService;

    /**
     * Create a new group and associate roles.
     *
     * @param request servlet request (for auth/context)
     * @param groups  group payload
     * @return saved group
     */
    @Operation(summary = "Create group", description = "Create a new group and map roles to it")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Group created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    @PostMapping
    public ResponseEntity<ResponseDto<Groups>> createGroup(HttpServletRequest request, @RequestBody Groups groups) {
        logger.info("Request to create group: name={}", groups != null ? groups.getName() : "null");
        Groups savedGroup = groupService.createGroup(request, groups);
        logger.info("Group created with id={}", savedGroup != null ? savedGroup.getPkGroupId() : "null");
        return ResponseEntity.ok(new ResponseDto<>(savedGroup, String.valueOf(HttpStatus.CREATED.value())));
    }

/**
         * Update an existing group and associated roles.
         *
         * @param request servlet request (for auth/context)
         * @param id      group id
         * @param groups  group payload with updates
         * @return updated group
         */
        @Operation(summary = "Update group", description = "Update an existing group and map roles to it")
        @ApiResponses({
                @ApiResponse(responseCode = "200", description = "Group updated successfully"),
                @ApiResponse(responseCode = "400", description = "Invalid request"),
                @ApiResponse(responseCode = "404", description = "Group not found")
        })
        @PutMapping("/{id}")
        public ResponseEntity<ResponseDto<Groups>> updateGroup(HttpServletRequest request,
                                                  @Parameter(description = "Group id", required = true) @PathVariable String id,
                                                  @RequestBody Groups groups) throws AccessDeniedException {
            logger.info("Request to update group: id={}, name={}", id, groups != null ? groups.getName() : "null");
            Groups updatedGroup = groupService.updateGroup(request, id, groups);
            logger.info("Group updated with id={}", updatedGroup != null ? updatedGroup.getPkGroupId() : "null");
            return ResponseEntity.ok(new ResponseDto<>(updatedGroup, String.valueOf(HttpStatus.ACCEPTED.value())));
        }

    /**
     * Get a group by its id.
     *
     * @param request servlet request (for auth/context)
     * @param id      group id
     * @return group entity
     */
    @Operation(summary = "Get group by id", description = "Retrieve a group by its identifier")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Group retrieved"),
            @ApiResponse(responseCode = "404", description = "Group not found")
    })
    @GetMapping("/id")
    public ResponseEntity<ResponseDto<Groups>> getGroupById(
            HttpServletRequest request,
            @Parameter(description = "Group id", required = true) @RequestParam String id) {
        logger.debug("Fetching group by id={}", id);
        Groups group = groupService.getGroupById(request, id);
        logger.debug("Fetched group: {}", group != null ? group.getPkGroupId() : "null");
        return ResponseEntity.ok(new ResponseDto<>(group, String.valueOf(HttpStatus.OK.value())));
    }

    /**
     * Get all groups.
     *
     * @param request servlet request (for auth/context)
     * @return list of groups
     */
    @Operation(summary = "List all groups", description = "Retrieve all groups")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of groups returned")
    })
    @GetMapping
    public ResponseEntity<ResponseDto<List<Groups>>> getAllGroups(HttpServletRequest request) {
        logger.debug("Fetching all groups");
        List<Groups> groups = groupService.getAllGroups(request);
        logger.debug("Number of groups fetched={}", groups != null ? groups.size() : 0);
        return ResponseEntity.ok(new ResponseDto<>(groups, String.valueOf(HttpStatus.OK.value())));
    }

    @GetMapping("/tenant")
    public ResponseEntity<ResponseDto<List<Groups>>> getGroupsByTenant(HttpServletRequest request,
                                                          @Parameter(description = "Tenant id", required = true)
                                                            @RequestParam String tenantId) {
        logger.debug("Fetching groups for tenantId={}", tenantId);
        List<Groups> groups = groupService.getGroupsByTenant(request, tenantId);
        logger.debug("Number of groups fetched for tenantId {}: {}", tenantId, groups != null ? groups.size() : 0);
        return ResponseEntity.ok(new ResponseDto<>(groups, String.valueOf(HttpStatus.OK.value())));
    }
}
