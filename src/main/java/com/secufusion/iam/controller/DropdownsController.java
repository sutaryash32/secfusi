package com.secufusion.iam.controller;

import com.secufusion.iam.dto.GroupsDropdown;
import com.secufusion.iam.dto.IndustryDTO;
import com.secufusion.iam.dto.RoleDropdownResponse;
import com.secufusion.iam.entity.*;
import com.secufusion.iam.service.DropdownService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Controller exposing dropdown-related endpoints for regions, countries, states and cities.
 * Each endpoint returns the data required to populate frontend drop-down lists.
 */
@Slf4j
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RestController
@RequestMapping
@Tag(name = "Dropdowns", description = "Endpoints to fetch regions, countries, states and cities for dropdowns")
public class DropdownsController {

    @Autowired
    private DropdownService dropdownService;

    /**
     * Retrieve all regions.
     *
     * @return a list of {@link Region} containing available regions
     */
    @Operation(summary = "Get all regions", description = "Retrieve all regions to populate a dropdown")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of regions",
            content = @Content(mediaType = "application/json",
                array = @ArraySchema(schema = @Schema(implementation = Region.class))))
    })
    @GetMapping("/regions")
    public List<Region> getRegions() {
        return dropdownService.getAllRegions();
    }

    /**
     * Retrieve all countries belonging to the specified region.
     *
     * @param regionId the id of the region for which countries should be returned
     * @return HTTP 200 with a list of {@link Country} for the given regionId
     */
    @Operation(summary = "Get countries by region", description = "Retrieve all countries for the specified region id")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of countries",
            content = @Content(mediaType = "application/json",
                array = @ArraySchema(schema = @Schema(implementation = Country.class)))),
        @ApiResponse(responseCode = "400", description = "Bad request")
    })
    @GetMapping("/countries")
    public ResponseEntity<List<Country>> getCountriesByRegion(
            @Parameter(description = "Region id", required = true) @RequestParam Long regionId) {
        return ResponseEntity.ok(dropdownService.getCountriesByRegion(regionId));
    }

    /**
     * Retrieve all states belonging to the specified country.
     *
     * @param countryId the id of the country for which states should be returned
     * @return HTTP 200 with a list of {@link States} for the given countryId
     */
    @Operation(summary = "Get states by country", description = "Retrieve all states for the specified country id")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of states",
            content = @Content(mediaType = "application/json",
                array = @ArraySchema(schema = @Schema(implementation = States.class)))),
        @ApiResponse(responseCode = "400", description = "Bad request")
    })
    @GetMapping("/states")
    public ResponseEntity<List<States>> getStatesByCountry(
            @Parameter(description = "Country id", required = true) @RequestParam Long countryId) {
        return ResponseEntity.ok(dropdownService.getStatesByCountry(countryId));
    }

    /**
     * Retrieve all cities belonging to the specified state.
     *
     * @param stateId the id of the state for which cities should be returned
     * @return HTTP 200 with a list of {@link Cities} for the given stateId
     */
    @Operation(summary = "Get cities by state", description = "Retrieve all cities for the specified state id")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of cities",
            content = @Content(mediaType = "application/json",
                array = @ArraySchema(schema = @Schema(implementation = Cities.class)))),
        @ApiResponse(responseCode = "400", description = "Bad request")
    })
    @GetMapping("/cities")
    public ResponseEntity<List<Cities>> getCitiesByStateId(
            @Parameter(description = "State id", required = true) @RequestParam Long stateId) {
        return ResponseEntity.ok(dropdownService.getCitiesByState(stateId));
    }

    @GetMapping("/industries")
    @Operation(summary = "Get all industries", description = "Retrieve all industries to populate a dropdown")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of industries",
            content = @Content(mediaType = "application/json",
                array = @ArraySchema(schema = @Schema(implementation = IndustryDTO.class))))
    })
    public List<IndustryDTO> getAllIndustries() {
        return dropdownService.getAllIndustries();
    }

    /**
     * Return billing types metadata for tenants.
     *
     * @return list of maps describing billing types
     */
    @GetMapping("/tenants/billing")
    @Operation(
            summary = "Get billing types",
            description = "Return billing types metadata for tenants.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Billing types returned",
                            content = @Content(mediaType = "application/json"))
            }
    )
    public ResponseEntity<List<Map<String, Object>>> getBillingTypes() {
        log.debug("getBillingTypes - fetching billing types");
        List<Map<String, Object>> billing = dropdownService.getTenantBillingTypes();
        log.debug("getBillingTypes - completed: count={}", billing != null ? billing.size() : 0);
        return ResponseEntity.ok(billing);
    }

    /**
     * Return tenant types available to the caller.
     *
     * @param request HTTP servlet request (for auth/context)
     * @return list of TenantType enums
     */
    @GetMapping("/tenants/types")
    @Operation(
            summary = "Get tenant types",
            description = "Return tenant types available to the caller.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Tenant types returned",
                            content = @Content(mediaType = "application/json",
                                    array = @ArraySchema(schema = @Schema(implementation = TenantType.class))))
            }
    )
    public ResponseEntity<List<TenantType>> getAllTenantTypes(@Parameter(hidden = true) HttpServletRequest request) {
        log.debug("getAllTenantTypes - start");
        List<TenantType> types = dropdownService.getTenantTypesByTenantType(request);
        log.debug("getAllTenantTypes - completed: count={}", types != null ? types.size() : 0);
        return ResponseEntity.ok(types);
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
    @GetMapping("/groups/dropdown")
    public ResponseEntity<List<GroupsDropdown>> getGroupsForDropdown(HttpServletRequest request) {
        log.debug("Fetching groups for dropdown");
        List<GroupsDropdown> dropdownList = dropdownService.getGroupsForDropdown(request);
        log.debug("Dropdown items returned={}", dropdownList != null ? dropdownList.size() : 0);
        return ResponseEntity.ok(dropdownList);
    }

    /**
     * Get roles as key/value pairs for dropdowns.
     *
     * @return list of RoleDropdownResponse (id/name)
     */
    @Operation(summary = "Get roles for dropdown", description = "Return roles as key/value pairs (id/name) for dropdowns.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Dropdown roles returned",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = RoleDropdownResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/roles/dropdown")
    public ResponseEntity<List<RoleDropdownResponse>> getRolesForDropdown(@RequestParam(required = false) String action) {
        log.info("GET /roles/dropdown - getRolesForDropdown called");
        List<RoleDropdownResponse> dropdownList = dropdownService.getRolesForDropdown(action);
        log.info("GET /roles/dropdown - returning {} items", dropdownList != null ? dropdownList.size() : 0);
        return ResponseEntity.ok(dropdownList);
    }
}