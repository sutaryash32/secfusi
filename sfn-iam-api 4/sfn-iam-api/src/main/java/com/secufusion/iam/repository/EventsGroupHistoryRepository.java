package com.secufusion.iam.repository;

import com.secufusion.iam.entity.EventsGroupHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventsGroupHistoryRepository extends JpaRepository<EventsGroupHistory, String> {

    List<EventsGroupHistory> findByEventsGroupIdAndFkTenantIdOrderByPerformedAtDesc(
            String eventsGroupId, String fkTenantId);

    List<EventsGroupHistory> findByFkTenantIdOrderByPerformedAtDesc(String fkTenantId);
}
