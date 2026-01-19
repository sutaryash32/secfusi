package com.secufusion.iam.controller;

import com.secufusion.iam.dto.GroupsDropdown;
import com.secufusion.iam.dto.IndustryDTO;
import com.secufusion.iam.dto.ResponseDto;
import com.secufusion.iam.dto.RoleDropdownResponse;
import com.secufusion.iam.entity.*;
import com.secufusion.iam.service.DropdownService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
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
    public ResponseEntity<ResponseDto<List<Region>>> getRegions() {
        return ResponseEntity.ok(new ResponseDto<>(dropdownService.getAllRegions(), String.valueOf(HttpStatus.OK.value())));
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
    public ResponseEntity<ResponseDto<List<Country>>> getCountriesByRegion(
            @Parameter(description = "Region id", required = true) @RequestParam Long regionId) {
        return ResponseEntity.ok(new ResponseDto<>(dropdownService.getCountriesByRegion(regionId), String.valueOf(HttpStatus.OK.value())));
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
    public ResponseEntity<ResponseDto<List<States>>> getStatesByCountry(
            @Parameter(description = "Country id", required = true) @RequestParam Long countryId) {
        return ResponseEntity.ok(new ResponseDto<>(dropdownService.getStatesByCountry(countryId), String.valueOf(HttpStatus.OK.value())));
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
    public ResponseEntity<ResponseDto<List<Cities>>> getCitiesByStateId(
            @Parameter(description = "State id", required = true) @RequestParam Long stateId) {
        return ResponseEntity.ok(new ResponseDto<>(dropdownService.getCitiesByState(stateId), String.valueOf(HttpStatus.OK.value())));
    }

    @GetMapping("/industries")
    @Operation(summary = "Get all industries", description = "Retrieve all industries to populate a dropdown")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of industries",
            content = @Content(mediaType = "application/json",
                array = @ArraySchema(schema = @Schema(implementation = IndustryDTO.class))))
    })
    public ResponseEntity<ResponseDto<List<IndustryDTO>>> getAllIndustries() {
        return ResponseEntity.ok(new ResponseDto<>(dropdownService.getAllIndustries(), String.valueOf(HttpStatus.OK.value())));
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
    public ResponseEntity<ResponseDto<List<Map<String, Object>>>> getBillingTypes() {
        log.debug("getBillingTypes - fetching billing types");
        List<Map<String, Object>> billing = dropdownService.getTenantBillingTypes();
        log.debug("getBillingTypes - completed: count={}", billing != null ? billing.size() : 0);
        return ResponseEntity.ok(new ResponseDto<>(billing, String.valueOf(HttpStatus.OK.value())));
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
    public ResponseEntity<ResponseDto<List<TenantType>>> getAllTenantTypes(@Parameter(hidden = true) HttpServletRequest request) {
        log.debug("getAllTenantTypes - start");
        List<TenantType> types = dropdownService.getTenantTypesByTenantType(request);
        log.debug("getAllTenantTypes - completed: count={}", types != null ? types.size() : 0);
        return ResponseEntity.ok(new ResponseDto<>(types, String.valueOf(HttpStatus.OK.value())));
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
    public ResponseEntity<ResponseDto<List<GroupsDropdown>>> getGroupsForDropdown(HttpServletRequest request) {
        log.debug("Fetching groups for dropdown");
        List<GroupsDropdown> dropdownList = dropdownService.getGroupsForDropdown(request);
        log.debug("Dropdown items returned={}", dropdownList != null ? dropdownList.size() : 0);
        return ResponseEntity.ok(new ResponseDto<>(dropdownList, String.valueOf(HttpStatus.OK.value())));
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
    public ResponseEntity<ResponseDto<List<RoleDropdownResponse>>> getRolesForDropdown(@RequestParam(required = false) String action) {
        log.info("GET /roles/dropdown - getRolesForDropdown called");
        List<RoleDropdownResponse> dropdownList = dropdownService.getRolesForDropdown(action);
        log.info("GET /roles/dropdown - returning {} items", dropdownList != null ? dropdownList.size() : 0);
        return ResponseEntity.ok(new ResponseDto<>(dropdownList, String.valueOf(HttpStatus.OK.value())));
    }

    /**
     * Retrieve all package types for dropdown.
     *
     * @return list of PackageType entities
     */
    @Operation(summary = "Get all package types", description = "Retrieve all package types to populate a dropdown")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of package types",
            content = @Content(mediaType = "application/json",
                array = @ArraySchema(schema = @Schema(implementation = PackageType.class))))
    })
    @GetMapping("/package-types")
    public ResponseEntity<ResponseDto<List<PackageType>>> getPackageTypes() {
        log.debug("GET /package-types - fetching package types");
        List<PackageType> packageTypes = dropdownService.getAllPackageTypes();
        log.debug("GET /package-types - returning {} items", packageTypes != null ? packageTypes.size() : 0);
        return ResponseEntity.ok(new ResponseDto<>(packageTypes, String.valueOf(HttpStatus.OK.value())));
    }

    /**
     * Retrieve all active billing cycles for dropdown.
     *
     * @return list of active BillingCycle entities
     */
    @Operation(summary = "Get all billing cycles", description = "Retrieve all active billing cycles to populate a dropdown")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of billing cycles",
            content = @Content(mediaType = "application/json",
                array = @ArraySchema(schema = @Schema(implementation = BillingCycle.class))))
    })
    @GetMapping("/billing-cycles")
    public ResponseEntity<ResponseDto<List<BillingCycle>>> getBillingCycles() {
        log.debug("GET /billing-cycles - fetching billing cycles");
        List<BillingCycle> billingCycles = dropdownService.getAllBillingCycles();
        log.debug("GET /billing-cycles - returning {} items", billingCycles != null ? billingCycles.size() : 0);
        return ResponseEntity.ok(new ResponseDto<>(billingCycles, String.valueOf(HttpStatus.OK.value())));
    }
}