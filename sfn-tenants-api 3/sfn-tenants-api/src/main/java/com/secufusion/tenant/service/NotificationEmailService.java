package com.secufusion.tenant.service;

import com.secufusion.tenant.dto.NotificationEvent;
import com.secufusion.tenant.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationEmailService {

    private final EmailService emailService;

    @Async("notificationEmailExecutor")
    public void sendNotificationEmail(NotificationEvent event, User user) {
        try {
            String subject = buildSubject(event);
            String htmlBody = buildEmailTemplate(event);
            boolean sent = emailService.sendEmail(event.getTenantId(), user.getEmail(), subject, htmlBody);
            if (sent) {
                log.info("Notification email sent: type={} to={}", event.getType(), user.getEmail());
            }
        } catch (Exception e) {
            log.error("Failed to send notification email: type={} to={}",
                    event.getType(), user.getEmail(), e);
        }
    }

    private String buildSubject(NotificationEvent event) {
        if (event.getType() == null) return "[SecuFusion] " + event.getTitle();
        return switch (event.getType()) {
            case "INCIDENT_CREATED" -> "[SecuFusion] New Incident: " + event.getTitle();
            case "INCIDENT_ASSIGNED" -> "[SecuFusion] Incident Assigned to You";
            case "INCIDENT_STATUS_CHANGED" -> "[SecuFusion] Incident Status Updated";
            case "INCIDENT_PRIORITY_ESCALATED" -> "[URGENT] Incident Priority Escalated";
            case "INCIDENT_RESOLVED" -> "[SecuFusion] Incident Resolved";
            case "INCIDENT_REOPENED" -> "[SecuFusion] Incident Reopened";
            case "INCIDENT_COMMENT_ADDED" -> "[SecuFusion] New Comment on Incident";
            default -> "[SecuFusion] " + event.getTitle();
        };
    }

    private String buildEmailTemplate(NotificationEvent event) {
        String severityColor = switch (event.getSeverity() != null ? event.getSeverity().toUpperCase() : "INFO") {
            case "CRITICAL" -> "#dc3545";
            case "HIGH" -> "#fd7e14";
            case "MEDIUM" -> "#ffc107";
            case "LOW" -> "#17a2b8";
            default -> "#6c757d";
        };

        String incidentNumber = event.getMetadata() != null
                ? event.getMetadata().getOrDefault("incidentNumber", "")
                : "";

        String incidentLine = incidentNumber.isEmpty() ? ""
                : "<p><strong>Incident:</strong> " + incidentNumber + "</p>";

        return """
            <html>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
                <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                    <div style="background-color: %s; color: white; padding: 12px 20px; border-radius: 4px 4px 0 0;">
                        <strong>%s</strong> | Severity: %s
                    </div>
                    <div style="border: 1px solid #dee2e6; border-top: none; padding: 20px; border-radius: 0 0 4px 4px;">
                        <h2 style="color: #2c3e50; margin-top: 0;">%s</h2>
                        %s
                        <p>%s</p>
                        <hr style="border: 0; border-top: 1px solid #eee; margin: 20px 0;">
                        <p style="font-size: 12px; color: #999;">
                            Source: %s | %s<br/>
                            This is an automated notification from SecuFusion.
                        </p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
                severityColor,
                event.getType() != null ? event.getType().replace("_", " ") : "NOTIFICATION",
                event.getSeverity() != null ? event.getSeverity() : "INFO",
                event.getTitle(),
                incidentLine,
                event.getMessage(),
                event.getSourceService() != null ? event.getSourceService() : "",
                event.getTimestamp() != null ? event.getTimestamp().toString() : ""
        );
    }
}
