package com.secufusion.events.repository;

import com.secufusion.events.entity.IncidentActivity;
import com.secufusion.events.entity.IncidentActivityAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IncidentActivityRepository extends JpaRepository<IncidentActivity, String> {

    List<IncidentActivity> findByIncident_PkIncidentIdAndTenantIdOrderByPerformedAtDesc(
            String incidentId, String tenantId);

    Page<IncidentActivity> findByIncident_PkIncidentIdAndTenantIdOrderByPerformedAtDesc(
            String incidentId, String tenantId, Pageable pageable);

    List<IncidentActivity> findByIncident_PkIncidentIdAndActionOrderByPerformedAtDesc(
            String incidentId, IncidentActivityAction action);

    long countByIncident_PkIncidentIdAndTenantId(String incidentId, String tenantId);
}
