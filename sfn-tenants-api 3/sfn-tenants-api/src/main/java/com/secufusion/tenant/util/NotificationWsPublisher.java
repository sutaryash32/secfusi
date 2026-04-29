package com.secufusion.tenant.util;

import com.secufusion.tenant.dto.NotificationEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class NotificationWsPublisher {

    private final SimpMessagingTemplate messagingTemplate;
    private final SimpUserRegistry simpUserRegistry;

    public NotificationWsPublisher(@Lazy SimpMessagingTemplate messagingTemplate,
                                    @Lazy SimpUserRegistry simpUserRegistry) {
        this.messagingTemplate = messagingTemplate;
        this.simpUserRegistry = simpUserRegistry;
    }

    /**
     * Broadcast to all users subscribed to a tenant's notification channel.
     * Frontend subscribes to: /topic/notifications/{tenantId}
     */
    public void broadcastToTenant(NotificationEvent event) {
        String destination = "/topic/notifications/" + event.getTenantId();
        log.info("Broadcasting notification to WS: dest={} type={} users={}",
                destination, event.getType(), simpUserRegistry.getUserCount());
        messagingTemplate.convertAndSend(destination, event);
    }

    /**
     * Send to a specific user's private notification queue.
     * Frontend subscribes to: /user/notifications
     * Requires StompPrincipal set on CONNECT (via JwtStompInterceptor).
     */
    public void sendToUser(NotificationEvent event, String userId) {
        log.debug("Sending user-targeted notification: userId={} type={}",
                userId, event.getType());
        messagingTemplate.convertAndSendToUser(userId, "/notifications", event);
    }
}
