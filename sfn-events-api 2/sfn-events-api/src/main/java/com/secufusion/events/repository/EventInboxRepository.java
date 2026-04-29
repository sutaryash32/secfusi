package com.secufusion.events.repository;

import com.secufusion.events.entity.EventInboxEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventInboxRepository
        extends JpaRepository<EventInboxEntity, String> {

    List<EventInboxEntity> findTop500ByTenantIdAndProcessedFalse(String tenantId);

    @Query("""
        select distinct e.tenantId
        from EventInboxEntity e
        where e.processed = false
    """)
    List<String> findTenantsWithPendingEvents();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
    update EventInboxEntity e
    set e.processed = true
    where e.eventId in :ids
""")
    void markProcessed(@Param("ids") List<String> ids);


}
