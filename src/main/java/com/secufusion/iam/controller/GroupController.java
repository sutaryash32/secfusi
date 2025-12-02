package com.secufusion.iam.controller;


import com.secufusion.iam.dto.GroupsDropdown;
import com.secufusion.iam.entity.Groups;
import com.secufusion.iam.service.GroupService;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

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
    public ResponseEntity<Groups> createGroup(HttpServletRequest request, @RequestBody Groups groups) {
        logger.info("Request to create group: name={}", groups != null ? groups.getName() : "null");
        Groups savedGroup = groupService.createGroup(request, groups);
        logger.info("Group created with id={}", savedGroup != null ? savedGroup.getPkGroupId() : "null");
        return ResponseEntity.ok(savedGroup);
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
        public ResponseEntity<Groups> updateGroup(HttpServletRequest request,
                                                  @Parameter(description = "Group id", required = true) @PathVariable String id,
                                                  @RequestBody Groups groups) {
            logger.info("Request to update group: id={}, name={}", id, groups != null ? groups.getName() : "null");
            Groups updatedGroup = groupService.updateGroup(request, id, groups);
            logger.info("Group updated with id={}", updatedGroup != null ? updatedGroup.getPkGroupId() : "null");
            return ResponseEntity.ok(updatedGroup);
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
    public ResponseEntity<Groups> getGroupById(
            HttpServletRequest request,
            @Parameter(description = "Group id", required = true) @RequestParam String id) {
        logger.debug("Fetching group by id={}", id);
        Groups group = groupService.getGroupById(request, id);
        logger.debug("Fetched group: {}", group != null ? group.getPkGroupId() : "null");
        return ResponseEntity.ok(group);
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
    public ResponseEntity<List<Groups>> getAllGroups(HttpServletRequest request) {
        logger.debug("Fetching all groups");
        List<Groups> groups = groupService.getAllGroups(request);
        logger.debug("Number of groups fetched={}", groups != null ? groups.size() : 0);
        return ResponseEntity.ok(groups);
    }

    /**
     * Get groups formatted for dropdowns (id + display name).
     *
     * @param request servlet request (for auth/context)
     * @return list of groups for dropdown
     */
    @Operation(summary = "Groups dropdown", description = "Get groups formatted for dropdown selection")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dropdown list returned")
    })
    @GetMapping("/dropdown")
    public ResponseEntity<List<GroupsDropdown>> getGroupsForDropdown(HttpServletRequest request) {
        logger.debug("Fetching groups for dropdown");
        List<GroupsDropdown> dropdownList = groupService.getGroupsForDropdown(request);
        logger.debug("Dropdown items returned={}", dropdownList != null ? dropdownList.size() : 0);
        return ResponseEntity.ok(dropdownList);
    }
}
