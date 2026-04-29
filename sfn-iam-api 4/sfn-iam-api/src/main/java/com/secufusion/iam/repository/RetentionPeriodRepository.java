package com.secufusion.iam.repository;

import com.secufusion.iam.entity.RetentionPeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RetentionPeriodRepository extends JpaRepository<RetentionPeriod, Long> {

    Optional<RetentionPeriod> findByPeriodCode(String periodCode);

    Optional<RetentionPeriod> findByPeriodName(String periodName);

    List<RetentionPeriod> findByIsActiveTrueOrderByPeriodDaysAsc();

    List<RetentionPeriod> findAllByOrderByPeriodDaysAsc();
}
