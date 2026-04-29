package com.secufusion.events.scheduler;

import com.secufusion.events.service.EscalationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled task to check for incidents that need escalation
 * based on configured escalation rules.
 * Runs every 5 minutes.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EscalationScheduler {

    private final EscalationService escalationService;

    @Scheduled(fixedRate = 300000) // 5 minutes
    public void checkEscalations() {
        log.debug("[ESCALATION-SCHEDULER] Triggering escalation check");

        try {
            escalationService.processEscalations();
        } catch (Exception e) {
            log.error("[ESCALATION-SCHEDULER] Error during escalation check", e);
        }
    }
}
