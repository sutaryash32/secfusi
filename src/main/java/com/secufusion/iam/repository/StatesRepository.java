package com.secufusion.iam.repository;

import com.secufusion.iam.entity.States;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.io.Serializable;
import java.util.List;

@Repository
public interface StatesRepository extends JpaRepository<States, Serializable> {
//    boolean existsByStateNameIgnoreCase(String stateName);

    List<States> findAllByCountryId(Long countryId);
}
