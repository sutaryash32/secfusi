package com.secufusion.tenant.repository;

import com.secufusion.tenant.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, String> {

    Page<Notification> findByFkTenantIdAndFkUserIdOrderByCreatedAtDesc(
            String tenantId, String userId, Pageable pageable);

    long countByFkTenantIdAndFkUserIdAndIsReadFalse(String tenantId, String userId);

    @Query("SELECT n.type, COUNT(n) FROM Notification n " +
            "WHERE n.fkTenantId = :tenantId AND n.fkUserId = :userId AND n.isRead = false " +
            "GROUP BY n.type")
    List<Object[]> countUnreadByType(@Param("tenantId") String tenantId,
                                      @Param("userId") String userId);

    @Query("SELECT n.severity, COUNT(n) FROM Notification n " +
            "WHERE n.fkTenantId = :tenantId AND n.fkUserId = :userId AND n.isRead = false " +
            "GROUP BY n.severity")
    List<Object[]> countUnreadBySeverity(@Param("tenantId") String tenantId,
                                          @Param("userId") String userId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = :now " +
            "WHERE n.fkTenantId = :tenantId AND n.fkUserId = :userId AND n.isRead = false")
    int markAllAsRead(@Param("tenantId") String tenantId,
                      @Param("userId") String userId,
                      @Param("now") Instant now);
}
