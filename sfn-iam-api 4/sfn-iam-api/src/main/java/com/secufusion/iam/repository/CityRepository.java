package com.secufusion.iam.repository;

import com.secufusion.iam.entity.Cities;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.io.Serializable;
import java.util.List;

@Repository
public interface CityRepository extends JpaRepository<Cities, Serializable> {
    List<Cities> findAllByStateId(Long stateId);

}
