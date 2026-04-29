package com.secufusion.tenant.config;

import com.secufusion.tenant.listener.AdvancedAuditListener;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class that registers Hibernate event listeners for advanced audit logging.
 * Registers listeners for POST_INSERT, POST_UPDATE, and POST_DELETE events.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class AuditConfig {

    private final EntityManagerFactory entityManagerFactory;
    private final AdvancedAuditListener advancedAuditListener;

    @PostConstruct
    public void registerListeners() {
        try {
            SessionFactoryImplementor sessionFactory = entityManagerFactory.unwrap(SessionFactoryImplementor.class);
            EventListenerRegistry registry = sessionFactory.getServiceRegistry()
                    .getService(EventListenerRegistry.class);

            if (registry != null) {
                registry.getEventListenerGroup(EventType.POST_INSERT).appendListener(advancedAuditListener);
                registry.getEventListenerGroup(EventType.POST_UPDATE).appendListener(advancedAuditListener);
                registry.getEventListenerGroup(EventType.POST_DELETE).appendListener(advancedAuditListener);
                log.info("Successfully registered AdvancedAuditListener for POST_INSERT, POST_UPDATE, and POST_DELETE events");
            } else {
                log.warn("EventListenerRegistry is null - audit listeners not registered");
            }
        } catch (Exception e) {
            log.error("Failed to register audit listeners: {}", e.getMessage(), e);
        }
    }
}
