package com.secufusion.events.service;

import com.secufusion.events.entity.SmtpConfig;
import com.secufusion.events.entity.User;
import com.secufusion.events.event.IncidentNotificationEvent;
import com.secufusion.events.repository.SmtpConfigRepository;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Properties;

@Slf4j
@Service
public class NotificationEmailService {

    private final SmtpConfigRepository smtpConfigRepository;

    // Fallback to environment variables if no DB config exists
    @Value("${mail.smtp.host:}")
    private String defaultSmtpHost;

    @Value("${mail.smtp.port:587}")
    private int defaultSmtpPort;

    @Value("${mail.smtp.auth:true}")
    private String defaultSmtpAuth;

    @Value("${mail.smtp.starttls:true}")
    private String defaultSmtpStarttls;

    @Value("${mail.smtp.username:}")
    private String defaultSmtpUsername;

    @Value("${mail.smtp.password:}")
    private String defaultSmtpPassword;

    @Value("${mail.smtp.mail:}")
    private String defaultFromEmail;

    public NotificationEmailService(SmtpConfigRepository smtpConfigRepository) {
        this.smtpConfigRepository = smtpConfigRepository;
    }

    /**
     * Send notification email using tenant-specific SMTP config.
     * Fallback chain: tenant DB config → default DB config (fk_tenant_id IS NULL) → application.properties
     */
    public void sendNotificationEmail(IncidentNotificationEvent event, User user) {
        try {
            SmtpConfig config = getSmtpConfigForTenant(event.getTenantId());
            if (config == null) {
                log.warn("SMTP not configured — skipping email for type={} to={}", event.getType(), user.getEmail());
                return;
            }

            String subject = buildSubject(event);
            String htmlBody = buildEmailTemplate(event);

            Properties props = buildMailProperties(config);

            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(config.getUsername(), config.getPassword());
                }
            });

            MimeMessage message = new MimeMessage(session);
            String fromName = config.getFromName() != null ? config.getFromName() : "SecuFusion";
            message.setFrom(new InternetAddress(config.getFromEmail(), fromName));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(user.getEmail()));
            message.setSubject(subject);
            message.setContent(htmlBody, "text/html; charset=utf-8");

            Transport.send(message);
            log.info("Notification email sent: type={} to={} smtpHost={}", event.getType(), user.getEmail(), config.getHost());

        } catch (Exception e) {
            log.error("Failed to send notification email: type={} to={}", event.getType(), user.getEmail(), e);
        }
    }

    // ==================== SMTP CONFIG RESOLUTION ====================

    private SmtpConfig getSmtpConfigForTenant(String tenantId) {
        // 1. Tenant-specific DB config
        if (tenantId != null) {
            var tenantConfig = smtpConfigRepository.findByFkTenantIdAndIsActiveTrue(tenantId);
            if (tenantConfig.isPresent()) {
                log.debug("Using tenant-specific SMTP config for tenantId={}", tenantId);
                return tenantConfig.get();
            }
        }

        // 2. Default DB config (fk_tenant_id IS NULL)
        var defaultConfig = smtpConfigRepository.findByFkTenantIdIsNullAndIsActiveTrue();
        if (defaultConfig.isPresent()) {
            log.debug("Using default SMTP config from DB");
            return defaultConfig.get();
        }

        // 3. Fallback to application.properties
        if (defaultSmtpHost != null && !defaultSmtpHost.isBlank()) {
            log.debug("Using SMTP config from application.properties");
            SmtpConfig envConfig = new SmtpConfig();
            envConfig.setHost(defaultSmtpHost);
            envConfig.setPort(defaultSmtpPort);
            envConfig.setAuth(defaultSmtpAuth);
            envConfig.setStarttls(defaultSmtpStarttls);
            envConfig.setUsername(defaultSmtpUsername);
            envConfig.setPassword(defaultSmtpPassword);
            envConfig.setFromEmail(defaultFromEmail);
            envConfig.setFromName("SecuFusion");
            return envConfig;
        }

        return null;
    }

    private Properties buildMailProperties(SmtpConfig config) {
        Properties props = new Properties();
        props.put("mail.smtp.host", config.getHost());
        props.put("mail.smtp.port", String.valueOf(config.getPort()));
        props.put("mail.smtp.auth", config.getAuth() != null ? config.getAuth() : "true");
        props.put("mail.smtp.starttls.enable", config.getStarttls() != null ? config.getStarttls() : "true");
        props.put("mail.smtp.connectiontimeout", "15000");
        props.put("mail.smtp.timeout", "15000");
        props.put("mail.smtp.writetimeout", "15000");

        // SSL support (port 465)
        if ("true".equalsIgnoreCase(config.getSsl())) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        }

        return props;
    }

    // ==================== EMAIL CONTENT ====================

    private String buildSubject(IncidentNotificationEvent event) {
        if (event.getType() == null) return "[SecuFusion] " + event.getNotificationTitle();
        return switch (event.getType()) {
            case "INCIDENT_CREATED" -> "[SecuFusion] New Incident: " + event.getNotificationTitle();
            case "INCIDENT_ASSIGNED" -> "[SecuFusion] Incident Assigned to You";
            case "INCIDENT_STATUS_CHANGED" -> "[SecuFusion] Incident Status Updated";
            case "INCIDENT_PRIORITY_ESCALATED" -> "[URGENT] Incident Priority Escalated";
            case "INCIDENT_RESOLVED" -> "[SecuFusion] Incident Resolved";
            case "INCIDENT_REOPENED" -> "[SecuFusion] Incident Reopened";
            case "INCIDENT_COMMENT_ADDED" -> "[SecuFusion] New Comment on Incident";
            case "INCIDENT_MERGED" -> "[SecuFusion] Incidents Merged";
            case "INCIDENT_ESCALATED" -> "[URGENT] Incident Auto-Escalated";
            case "INCIDENT_EVENTS_LINKED" -> "[SecuFusion] New Security Events Linked to Incident";
            default -> "[SecuFusion] " + event.getNotificationTitle();
        };
    }

    private String buildEmailTemplate(IncidentNotificationEvent event) {
        String severity = event.getSeverity() != null ? event.getSeverity().toUpperCase() : "INFO";
        String severityColor = switch (severity) {
            case "CRITICAL" -> "#dc3545";
            case "HIGH" -> "#fd7e14";
            case "MEDIUM" -> "#ffc107";
            case "LOW" -> "#17a2b8";
            default -> "#6c757d";
        };

        String incidentNumber = event.getIncidentNumber() != null ? event.getIncidentNumber() : "";
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
                            Source: sfn-events-api | %s<br/>
                            This is an automated notification from SecuFusion.
                        </p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
                severityColor,
                event.getType() != null ? event.getType().replace("_", " ") : "NOTIFICATION",
                severity,
                event.getNotificationTitle(),
                incidentLine,
                event.getMessage(),
                Instant.now().toString()
        );
    }
}
