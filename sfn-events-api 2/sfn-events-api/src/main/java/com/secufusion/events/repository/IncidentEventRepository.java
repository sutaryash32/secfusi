package com.secufusion.events.repository;

import com.secufusion.events.entity.IncidentEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IncidentEventRepository extends JpaRepository<IncidentEvent, String> {

    List<IncidentEvent> findByIncident_PkIncidentIdAndTenantIdOrderByLinkedAtDesc(
            String incidentId, String tenantId);

    Optional<IncidentEvent> findByIncident_PkIncidentIdAndEvent_PkEventId(
            String incidentId, String eventId);

    long countByIncident_PkIncidentId(String incidentId);

    boolean existsByEvent_PkEventIdAndTenantId(String eventId, String tenantId);

    List<IncidentEvent> findByIncident_PkIncidentId(String incidentId);

    void deleteByIncident_PkIncidentIdAndEvent_PkEventId(String incidentId, String eventId);

    /**
     * Batch fetch incident event links for a list of event IDs.
     * Returns IncidentEvent with incident eagerly loaded for efficient mapping.
     */
    @Query("SELECT ie FROM IncidentEvent ie JOIN FETCH ie.incident WHERE ie.event.pkEventId IN :eventIds AND ie.tenantId = :tenantId")
    List<IncidentEvent> findByEventIdsAndTenantId(@Param("eventIds") List<String> eventIds,
                                                   @Param("tenantId") String tenantId);

    /**
     * Batch duplicate-check: find all existing links for a given incident and a list of event IDs.
     * Used to replace the per-event SELECT in linkEventsInternal, eliminating the N+1 query problem.
     */
    @Query("SELECT ie FROM IncidentEvent ie WHERE ie.incident.pkIncidentId = :incidentId AND ie.event.pkEventId IN :eventIds")
    List<IncidentEvent> findByIncident_PkIncidentIdAndEvent_PkEventIdIn(
            @Param("incidentId") String incidentId,
            @Param("eventIds") List<String> eventIds);

    /**
     * Find the incident event link for a single event (first/latest one).
     */
    Optional<IncidentEvent> findFirstByEvent_PkEventIdAndTenantIdOrderByLinkedAtDesc(
            String eventId, String tenantId);
}
