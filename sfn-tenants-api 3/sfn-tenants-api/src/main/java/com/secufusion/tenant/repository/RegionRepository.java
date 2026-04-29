package com.secufusion.tenant.repository;


import com.secufusion.tenant.entity.Region;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface RegionRepository extends JpaRepository<Region, Long> {
    @Query(value = """
            SELECT CASE WHEN COUNT(ci.pk_city_id) > 0 THEN true ELSE false END
                FROM region r
                JOIN country c ON c.region_id = r.pk_region_id
                JOIN states s  ON s.country_id = c.pk_country_id
                JOIN cities ci   ON ci.state_id = s.pk_state_id
                WHERE r.region_name = :region
                  AND c.country_name = :country
                  AND s.state_name   = :state
                  AND ci.city_name   = :city""", nativeQuery = true)
    boolean existsRegionCountryStateCity(String region,String country,String state,String city);

}


