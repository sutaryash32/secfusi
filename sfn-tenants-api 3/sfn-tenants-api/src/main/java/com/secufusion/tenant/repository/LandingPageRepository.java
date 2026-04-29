package com.secufusion.tenant.repository;

import com.secufusion.tenant.entity.LandingPage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LandingPageRepository extends JpaRepository<LandingPage, String> {

    @Query("SELECT lp FROM LandingPage lp LEFT JOIN FETCH lp.shortcuts ORDER BY lp.name")
    List<LandingPage> findAllWithShortcuts();

    @Query("SELECT lp FROM LandingPage lp LEFT JOIN FETCH lp.shortcuts WHERE lp.pkLandingPageId = :id")
    Optional<LandingPage> findByIdWithShortcuts(String id);
}