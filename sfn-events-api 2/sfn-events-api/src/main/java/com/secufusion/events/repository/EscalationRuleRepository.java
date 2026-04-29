package com.secufusion.events.repository;

import com.secufusion.events.entity.EscalationRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EscalationRuleRepository extends JpaRepository<EscalationRule, String> {

    List<EscalationRule> findByTenant_TenantIDAndIsActiveTrueOrderByNameAsc(String tenantId);

    List<EscalationRule> findByIsActiveTrue();

    Optional<EscalationRule> findByPkEscalationRuleIdAndTenant_TenantID(
            String ruleId, String tenantId);

    boolean existsByTenant_TenantIDAndName(String tenantId, String name);
}
