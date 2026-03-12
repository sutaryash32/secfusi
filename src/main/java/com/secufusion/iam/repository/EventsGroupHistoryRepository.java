package com.secufusion.iam.repository;

import com.secufusion.iam.entity.EventsGroupHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventsGroupHistoryRepository extends JpaRepository<EventsGroupHistory, String> {

    List<EventsGroupHistory> findByEventsGroupIdAndTenantIdOrderByPerformedAtDesc(
            String eventsGroupId, String tenantId);

    List<EventsGroupHistory> findByTenantIdOrderByPerformedAtDesc(String tenantId);
}
