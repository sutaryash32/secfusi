package com.secufusion.events.event;

import com.secufusion.events.service.NotificationDispatchService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Dispatches notifications for incident state changes.
 *
 * Using {@code @TransactionalEventListener(AFTER_COMMIT)} guarantees:
 *   - Notifications are sent only after the DB transaction has successfully committed.
 *   - If the transaction rolls back the notification is never sent.
 *
 * The dispatch is offloaded to a thread pool so the HTTP response is not
 * blocked.  We use an explicit executor instead of {@code @Async} because
 * the async AOP proxy can prevent {@code @TransactionalEventListener} from firing.
 */
@Component
@Slf4j
public class IncidentNotificationListener {

    private final NotificationDispatchService notificationDispatchService;
    private final ThreadPoolTaskExecutor notificationExecutor;

    public IncidentNotificationListener(NotificationDispatchService notificationDispatchService) {
        this.notificationDispatchService = notificationDispatchService;

        // Small dedicated pool for background notification dispatch
        this.notificationExecutor = new ThreadPoolTaskExecutor();
        this.notificationExecutor.setCorePoolSize(2);
        this.notificationExecutor.setMaxPoolSize(4);
        this.notificationExecutor.setQueueCapacity(100);
        this.notificationExecutor.setThreadNamePrefix("notif-dispatch-");
        this.notificationExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        this.notificationExecutor.setWaitForTasksToCompleteOnShutdown(true);
        this.notificationExecutor.setAwaitTerminationSeconds(30);
        this.notificationExecutor.initialize();
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down notification executor...");
        notificationExecutor.shutdown();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleIncidentNotification(IncidentNotificationEvent appEvent) {
        notificationExecutor.execute(() -> {
            try {
                notificationDispatchService.dispatch(appEvent);
            } catch (Exception e) {
                log.error("IncidentNotificationListener - failed to dispatch notification. type={} incidentId={}",
                        appEvent.getType(), appEvent.getIncidentId(), e);
            }
        });
    }
}
