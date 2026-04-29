package com.secufusion.tenant.repository;

import com.secufusion.tenant.entity.Cities;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.io.Serializable;

@Repository
public interface CityRepository extends JpaRepository<Cities, Serializable> {
}
