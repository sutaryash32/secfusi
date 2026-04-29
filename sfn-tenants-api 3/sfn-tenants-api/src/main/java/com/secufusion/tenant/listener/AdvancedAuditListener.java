package com.secufusion.tenant.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.secufusion.tenant.annotations.Auditable;
import com.secufusion.tenant.util.CurrentUserHolder;
import jakarta.persistence.Id;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.event.spi.*;
import org.hibernate.persister.entity.EntityPersister;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Advanced Hibernate event listener that captures entity changes and logs them to the audit_log table.
 * Listens to POST_INSERT, POST_UPDATE, and POST_DELETE events.
 */
@Component
@Slf4j
public class AdvancedAuditListener implements PostInsertEventListener, PostUpdateEventListener, PostDeleteEventListener {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    private static final String INSERT_AUDIT_SQL =
            "INSERT INTO audit_log(entity_name, entity_id, operation, old_data, new_data, tenant_id, created_by) " +
                    "VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?, ?)";

    public AdvancedAuditListener(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    }

    @Override
    public void onPostInsert(PostInsertEvent event) {
        audit(event.getEntity(), null, event.getState(), event.getPersister(), "INSERT");
    }

    @Override
    public void onPostUpdate(PostUpdateEvent event) {
        audit(event.getEntity(), event.getOldState(), event.getState(), event.getPersister(), "UPDATE");
    }

    @Override
    public void onPostDelete(PostDeleteEvent event) {
        audit(event.getEntity(), event.getDeletedState(), null, event.getPersister(), "DELETE");
    }

    @Override
    public boolean requiresPostCommitHandling(EntityPersister persister) {
        return false;
    }

    private void audit(Object entity, Object[] oldState, Object[] newState, EntityPersister persister, String operation) {
        // Skip auditing the audit_log entity itself to prevent infinite loops
        String entityName = entity.getClass().getSimpleName();
        if ("AuditLog".equals(entityName)) {
            return;
        }

        // Check if entity has @Auditable annotation with enabled=false
        Auditable auditable = entity.getClass().getAnnotation(Auditable.class);
        if (auditable != null && !auditable.enabled()) {
            log.debug("Skipping audit for entity {} - auditing disabled via @Auditable annotation", entityName);
            return;
        }

        try {
            String entityId = getEntityId(entity);
            String oldDataJson = stateToJson(oldState, persister);
            String newDataJson = stateToJson(newState, persister);
            String tenantId = CurrentUserHolder.getTenantId();
            String createdBy = CurrentUserHolder.getUsername();

            // Fall back to SYSTEM if no user context is available
            if (createdBy == null || createdBy.isBlank()) {
                createdBy = "SYSTEM";
            }

            jdbcTemplate.update(INSERT_AUDIT_SQL,
                    entityName,
                    entityId,
                    operation,
                    oldDataJson,
                    newDataJson,
                    tenantId,
                    createdBy
            );

            log.debug("Audit log created for {} operation on entity {} with id {}", operation, entityName, entityId);
        } catch (Exception e) {
            log.error("Failed to create audit log for entity {}: {}", entityName, e.getMessage(), e);
        }
    }

    /**
     * Convert Hibernate state array to JSON string using property names from the persister.
     */
    private String stateToJson(Object[] state, EntityPersister persister) {
        if (state == null) {
            return null;
        }

        try {
            Map<String, Object> data = new HashMap<>();
            String[] propertyNames = persister.getPropertyNames();

            for (int i = 0; i < propertyNames.length && i < state.length; i++) {
                Object value = state[i];
                // Skip complex/lazy-loaded objects and collections to avoid serialization issues
                if (value != null && !isSimpleType(value)) {
                    // For complex objects, just store their ID or class name
                    String complexId = getEntityId(value);
                    if (complexId != null) {
                        data.put(propertyNames[i], Map.of("id", complexId, "type", value.getClass().getSimpleName()));
                    } else {
                        data.put(propertyNames[i], Map.of("type", value.getClass().getSimpleName()));
                    }
                } else {
                    data.put(propertyNames[i], value);
                }
            }

            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize state to JSON: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Check if the value is a simple type that can be serialized directly.
     */
    private boolean isSimpleType(Object value) {
        if (value == null) return true;
        Class<?> clazz = value.getClass();
        return clazz.isPrimitive() ||
                value instanceof String ||
                value instanceof Number ||
                value instanceof Boolean ||
                value instanceof Enum ||
                value instanceof java.time.temporal.Temporal ||
                value instanceof java.util.Date ||
                value instanceof java.util.UUID;
    }

    /**
     * Extract the entity ID using reflection to find the @Id annotated field.
     */
    private String getEntityId(Object entity) {
        if (entity == null) return null;

        // Check declared fields in the class hierarchy
        Class<?> clazz = entity.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(Id.class)) {
                    field.setAccessible(true);
                    try {
                        Object id = field.get(entity);
                        return id != null ? id.toString() : null;
                    } catch (IllegalAccessException e) {
                        log.warn("Failed to access @Id field: {}", e.getMessage());
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }
}
