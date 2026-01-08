package com.secufusion.iam.service;

import com.secufusion.iam.dto.GroupsDropdown;
import com.secufusion.iam.dto.IndustryDTO;
import com.secufusion.iam.dto.RoleDropdownResponse;
import com.secufusion.iam.entity.*;
import com.secufusion.iam.exception.ResourceNotFoundException;
import com.secufusion.iam.repository.*;
import com.secufusion.iam.util.JwtUtl;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service that provides dropdown data for regions, countries, states and cities.
 * Adds logging for diagnostics and comments for clarity.
 */
@Slf4j
@Service
public class DropdownService {

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private CountryRepository countryRepository;

    @Autowired
    private StatesRepository statesRepository;

    @Autowired
    private CityRepository cityRepository;

    @Autowired
    private IndustryRepository industryRepository;

    @Autowired
    private JwtUtl jwtUtl;

    @Autowired
    private TenantTypeRepository tenantTypeRepository;

    @Autowired
    private GroupsRepository groupsRepository;

    @Autowired
    private RolesRepository rolesRepository;

    /**
     * Retrieve all regions.
     *
     * @return list of all Region entities
     */
    public List<Region> getAllRegions() {
        log.debug("Entering getAllRegions");
        List<Region> regions = regionRepository.findAll();
        int size = regions == null ? 0 : regions.size();
        log.info("Retrieved {} regions", size);
        log.debug("Exiting getAllRegions");
        return regions;
    }

    /**
     * Retrieve all countries for a given region id.
     *
     * @param regionId region identifier
     * @return list of Country entities under the region
     */
    public List<Country> getCountriesByRegion(Long regionId) {
        log.debug("Entering getCountriesByRegion with regionId={}", regionId);
        List<Country> countries = countryRepository.findAllByRegionId(regionId);
        int size = countries == null ? 0 : countries.size();
        log.info("Retrieved {} countries for regionId={}", size, regionId);
        log.debug("Exiting getCountriesByRegion with regionId={}", regionId);
        return countries;
    }

    /**
     * Retrieve all states for a given country id.
     *
     * @param countryId country identifier
     * @return list of States entities under the country
     */
    public List<States> getStatesByCountry(Long countryId) {
        log.debug("Entering getStatesByCountry with countryId={}", countryId);
        List<States> states = statesRepository.findAllByCountryId(countryId);
        int size = states == null ? 0 : states.size();
        log.info("Retrieved {} states for countryId={}", size, countryId);
        log.debug("Exiting getStatesByCountry with countryId={}", countryId);
        return states;
    }

    /**
     * Retrieve all cities for a given state id.
     *
     * @param stateId state identifier
     * @return list of Cities entities under the state
     */
    public List<Cities> getCitiesByState(Long stateId) {
        log.debug("Entering getCitiesByState with stateId={}", stateId);
        List<Cities> cities = cityRepository.findAllByStateId(stateId);
        int size = cities == null ? 0 : cities.size();
        log.info("Retrieved {} cities for stateId={}", size, stateId);
        log.debug("Exiting getCitiesByState with stateId={}", stateId);
        return cities;
    }

    public List<IndustryDTO> getAllIndustries() {

        return industryRepository.findAll()
                .stream()
                .map(industry -> new IndustryDTO(
                        industry.getIndustryId(),
                        industry.getIndustryName(),
                        industry.getIndustryCode(),
                        industry.getParentIndustry() != null ? industry.getParentIndustry().getIndustryId() : null,
                        industry.getIsActive(),
                        industry.getCreatedBy(),
                        industry.getCreatedTimestamp(),
                        industry.getLastModifiedBy(),
                        industry.getLastModifiedTimestamp()
                ))
                .toList();
    }

    @Transactional
    public List<Map<String, Object>> getTenantBillingTypes() {
        log.info("Fetching static tenant billing types.");
        List<Map<String, Object>> billingTypes = List.of(
                Map.of("id", 1, "billingType", "Trial"),
                Map.of("id", 2, "billingType", "Monthly"),
                Map.of("id", 3, "billingType", "Quarterly"),
                Map.of("id", 4, "billingType", "Yearly")
        );
        log.debug("Returning {} billing types.", billingTypes.size());
        return billingTypes;
    }

    @Transactional
    public List<TenantType> getTenantTypesByTenantType(HttpServletRequest request) {

        Tenant tenantFromEmail = jwtUtl.getTenantFromRequest(request);

        log.info(
                "Fetching tenant types for tenantId={}, tenantType={}, parentTenantId={}",
                tenantFromEmail.getTenantID(),
                tenantFromEmail.getTenantType(),
                tenantFromEmail.getParentTenantId()
        );

        List<TenantType> types = tenantTypeRepository.findAll();
        log.debug("Fetched {} tenant types from DB.", types.size());

        String tenantType = tenantFromEmail.getTenantType();
        if (tenantType == null) {
            return Collections.emptyList();
        }

        boolean isRootMasterMssp =
                tenantFromEmail.getParentTenantId() == null;

        switch (tenantType.trim().toLowerCase(Locale.ROOT)) {

            case "master mssp":
            case "master_mssp":
            case "mastermssp":

                if (isRootMasterMssp) {
                    // Root Master MSSP → can see all tenant types
                    return types;
                }

                // Child Master MSSP → exclude master
                return types.stream()
                        .filter(t ->
                                !"master mssp".equalsIgnoreCase(t.getTenantTypeName()))
                        .collect(Collectors.toList());

            case "mssp":
                // MSSP → enterprise only
                return types.stream()
                        .filter(t ->
                                "enterprise".equalsIgnoreCase(t.getTenantTypeName()))
                        .collect(Collectors.toList());

            case "enterprise":
                // Enterprise → none
                return Collections.emptyList();

            default:
                return types;
        }
    }


    /**
     * Get groups formatted for dropdown (includes mapped roles).
     */
    public List<GroupsDropdown> getGroupsForDropdown(HttpServletRequest request) {
        Tenant tenantFromRequest = jwtUtl.getTenantFromRequest(request);
        if (tenantFromRequest == null) {
            log.error("getGroupsForDropdown: tenant not present in request");
            throw new ResourceNotFoundException("Tenant not found in request");
        }
        String tenantId = tenantFromRequest.getTenantID();
        log.info("getGroupsForDropdown: Fetching all groups for tenantId={}", tenantId);
        List<Groups> groupsList = groupsRepository.findByTenantId(tenantId);
        List<GroupsDropdown> dropdown = groupsList.stream()
                .map(group -> {
                    // Convert mapped roles to dropdown DTOs safely
                    Set<RoleDropdownResponse> roles = Optional.ofNullable(group.getMappedRoles())
                            .orElse(Collections.emptySet())
                            .stream()
                            .map(r -> new RoleDropdownResponse(r.getPkRoleId(), r.getName()))
                            .collect(java.util.stream.Collectors.toSet());

                    log.debug("getGroupsForDropdown: group id={} name={} rolesCount={}",
                            group.getPkGroupId(), group.getName(), roles.size());

                    return new GroupsDropdown(group.getPkGroupId(), group.getName(), roles);
                })
                .toList();

        log.debug("getGroupsForDropdown: returning {} dropdown entries for tenantId={}", dropdown.size(), tenantId);
        return dropdown;
    }

    /**
     * Get roles for dropdown usage. Currently returns all roles (filters commented out).
     *
     * @return list of RoleDropdownResponse
     */
    public List<RoleDropdownResponse> getRolesForDropdown(String action) {
        log.info("Fetching roles for dropdown mode={}", action);
        List<Roles> rolesList = rolesRepository.findAll();
        return rolesList.stream()
                .filter(role -> Boolean.TRUE.equals(role.getActive()))
                .filter(role -> {
                    Character isSuper = role.getIsSuperRole();
                    Character isDefault = role.getIsDefault();
                    if ("admin".equalsIgnoreCase(action)) {
                        return isSuper != null && isSuper == 'Y';
                    } else if ("all".equalsIgnoreCase(action)) {
                        return true;
                    }
                    return (isSuper == null || isSuper != 'Y') && (isDefault == null || isDefault != 'Y');
                })
                .map(role -> new RoleDropdownResponse(role.getPkRoleId(), role.getName()))
                .toList();
    }
}