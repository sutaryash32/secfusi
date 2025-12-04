package com.secufusion.iam.service;

import com.secufusion.iam.dto.IndustryDTO;
import com.secufusion.iam.entity.Cities;
import com.secufusion.iam.entity.Country;
import com.secufusion.iam.entity.Region;
import com.secufusion.iam.entity.States;
import com.secufusion.iam.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

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
}