package com.secufusion.tenant.repository;

import com.secufusion.tenant.entity.BillingCycle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BillingCycleRepository extends JpaRepository<BillingCycle, Long> {

    Optional<BillingCycle> findByCycleCode(String cycleCode);

    Optional<BillingCycle> findByCycleName(String cycleName);

    List<BillingCycle> findByIsActiveTrue();

    boolean existsByCycleCode(String cycleCode);
}
