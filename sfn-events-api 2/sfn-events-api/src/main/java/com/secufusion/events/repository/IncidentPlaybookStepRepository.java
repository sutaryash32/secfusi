package com.secufusion.events.repository;

import com.secufusion.events.entity.IncidentPlaybookStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IncidentPlaybookStepRepository extends JpaRepository<IncidentPlaybookStep, String> {

    List<IncidentPlaybookStep> findByIncidentPlaybook_PkIncidentPlaybookIdAndTenantIdOrderByStepNumberAsc(
            String playbookId, String tenantId);

    Optional<IncidentPlaybookStep> findByPkIncidentPlaybookStepIdAndTenantId(
            String stepId, String tenantId);

    long countByIncidentPlaybook_PkIncidentPlaybookIdAndIsCompletedTrue(String playbookId);
}
