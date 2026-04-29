package com.secufusion.events.repository;

import com.secufusion.events.entity.IncidentPlaybook;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IncidentPlaybookRepository extends JpaRepository<IncidentPlaybook, String> {

    List<IncidentPlaybook> findByIncident_PkIncidentIdAndTenantIdOrderByCreatedAtDesc(
            String incidentId, String tenantId);

    Optional<IncidentPlaybook> findByPkIncidentPlaybookIdAndTenantId(
            String playbookId, String tenantId);

    boolean existsByIncident_PkIncidentIdAndPlaybookTemplate_PkPlaybookTemplateId(
            String incidentId, String templateId);
}
