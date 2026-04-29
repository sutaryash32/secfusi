package com.secufusion.events.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * EventsGroupDeviceUserMapping Entity
 *
 * Junction table mapping device users to events groups.
 * Used to determine which policies apply to which device users.
 *
 * Key Features:
 * - Many-to-many relationship between DeviceUser and EventsGroup
 * - Tracks assignment metadata (who assigned, when)
 * - Unique constraint prevents duplicate assignments
 * - Cascade deletes when group or device user is removed
 */
@Entity
@Table(
    name = "events_groups_device_user_map",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_device_user_events_group",
            columnNames = {"fk_device_user_id", "fk_events_group_id"}
        )
    },
    indexes = {
        @Index(name = "idx_mapping_device_user", columnList = "fk_device_user_id"),
        @Index(name = "idx_mapping_events_group", columnList = "fk_events_group_id")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventsGroupDeviceUserMapping {

    @Id
    @Column(name = "pk_mapping_id", length = 36, nullable = false)
    private String pkMappingId;

    /**
     * Foreign key to device_users table
     */
    @Column(name = "fk_device_user_id", length = 36, nullable = false)
    private String fkDeviceUserId;

    /**
     * Foreign key to events_groups table
     */
    @Column(name = "fk_events_group_id", length = 36, nullable = false)
    private String fkEventsGroupId;

    /**
     * Timestamp when the device user was assigned to the group
     */
    @Column(name = "assigned_at", nullable = false, updatable = false)
    private LocalDateTime assignedAt;

    /**
     * User who performed the assignment (email or system identifier)
     */
    @Column(name = "assigned_by", length = 100)
    private String assignedBy;

    /**
     * Lazy-loaded relationship to EventsGroup
     * Used for joining queries when needed
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_events_group_id", insertable = false, updatable = false)
    private EventsGroup eventsGroup;

    /**
     * Lazy-loaded relationship to DeviceUser
     * Used for joining queries when needed
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_device_user_id", insertable = false, updatable = false)
    private DeviceUser deviceUser;
}
