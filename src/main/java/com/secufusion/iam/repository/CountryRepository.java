package com.secufusion.iam.repository;

import com.secufusion.iam.entity.Country;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CountryRepository extends JpaRepository<Country, Long> {
    List<Country> findAllByRegionId(Long regionId);

    Optional<Country> findByCountryName(String countryName);
}
