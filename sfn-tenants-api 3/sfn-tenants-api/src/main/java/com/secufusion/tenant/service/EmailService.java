package com.secufusion.tenant.service;

import com.secufusion.tenant.entity.SmtpConfig;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Properties;

@Slf4j
@Service
public class EmailService {

    private final SmtpConfigService smtpConfigService;

    @Autowired
    public EmailService(SmtpConfigService smtpConfigService) {
        this.smtpConfigService = smtpConfigService;
    }

    /**
     * Send email using tenant-specific SMTP config (with fallback to default).
     */
    public boolean sendEmail(String tenantId, String toEmail, String subject, String htmlBody) {
        log.info("Sending email to={} for tenantId={}", toEmail, tenantId);

        SmtpConfig config = smtpConfigService.getSmtpConfigForTenant(tenantId);
        return sendEmailWithConfig(config, toEmail, subject, htmlBody);
    }

    /**
     * Send email using default/global SMTP config.
     */
    public boolean sendEmailWithDefaultConfig(String toEmail, String subject, String htmlBody) {
        log.info("Sending email to={} using default SMTP config", toEmail);

        SmtpConfig config = smtpConfigService.getDefaultSmtpConfig();
        return sendEmailWithConfig(config, toEmail, subject, htmlBody);
    }

    /**
     * Send email with provided SMTP config.
     */
    public boolean sendEmailWithConfig(SmtpConfig config, String toEmail, String subject, String htmlBody) {
        log.debug("Sending email via host={}, port={}", config.getHost(), config.getPort());

        Properties props = buildMailProperties(config);

        try {
            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(config.getUsername(), config.getPassword());
                }
            });

            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(config.getFromEmail(), config.getFromName()));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
            message.setSubject(subject);
            message.setContent(htmlBody, "text/html; charset=utf-8");

            Transport.send(message);
            log.info("Email sent successfully to={}", toEmail);
            return true;

        } catch (Exception e) {
            log.error("Failed to send email to={}: {}", toEmail, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Send email to multiple recipients.
     */
    public boolean sendEmailToMultiple(String tenantId, String[] toEmails, String subject, String htmlBody) {
        log.info("Sending email to {} recipients for tenantId={}", toEmails.length, tenantId);

        SmtpConfig config = smtpConfigService.getSmtpConfigForTenant(tenantId);
        Properties props = buildMailProperties(config);

        try {
            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(config.getUsername(), config.getPassword());
                }
            });

            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(config.getFromEmail(), config.getFromName()));

            // Add all recipients
            for (String email : toEmails) {
                message.addRecipient(Message.RecipientType.TO, new InternetAddress(email));
            }

            message.setSubject(subject);
            message.setContent(htmlBody, "text/html; charset=utf-8");

            Transport.send(message);
            log.info("Email sent successfully to {} recipients", toEmails.length);
            return true;

        } catch (Exception e) {
            log.error("Failed to send email to multiple recipients: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Send welcome email to new user.
     */
    public boolean sendWelcomeEmail(String tenantId, String toEmail, String userName, String tenantName) {
        String subject = "Welcome to " + tenantName + " - SecuFusion";
        String htmlBody = buildWelcomeEmailTemplate(userName, tenantName);
        return sendEmail(tenantId, toEmail, subject, htmlBody);
    }

    /**
     * Send password reset email.
     */
    public boolean sendPasswordResetEmail(String tenantId, String toEmail, String userName, String resetLink) {
        String subject = "Password Reset Request - SecuFusion";
        String htmlBody = buildPasswordResetTemplate(userName, resetLink);
        return sendEmail(tenantId, toEmail, subject, htmlBody);
    }

    /* ==========================================================
       HELPER: Build Mail Properties
       ========================================================== */
    private Properties buildMailProperties(SmtpConfig config) {
        Properties props = new Properties();
        props.put("mail.smtp.host", config.getHost());
        props.put("mail.smtp.port", String.valueOf(config.getPort()));
        props.put("mail.smtp.auth", config.getAuth());
        props.put("mail.smtp.ssl.trust", config.getHost());
        props.put("mail.smtp.ssl.protocols", "TLSv1.2");
        props.put("mail.smtp.connectiontimeout", "15000");
        props.put("mail.smtp.timeout", "15000");
        props.put("mail.smtp.writetimeout", "15000");

        // SSL mode (port 465) vs STARTTLS mode (port 587)
        if ("true".equalsIgnoreCase(config.getSsl())) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.starttls.enable", "false");
        } else {
            props.put("mail.smtp.ssl.enable", "false");
            props.put("mail.smtp.starttls.enable", config.getStarttls());
            props.put("mail.smtp.starttls.required", "true".equalsIgnoreCase(config.getStarttls()) ? "true" : "false");
        }

        return props;
    }

    /* ==========================================================
       EMAIL TEMPLATES
       ========================================================== */
    private String buildWelcomeEmailTemplate(String userName, String tenantName) {
        return """
            <html>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
                <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                    <h2 style="color: #2c3e50;">Welcome to %s!</h2>
                    <p>Hello %s,</p>
                    <p>Your account has been successfully created. You can now access the SecuFusion platform.</p>
                    <p>If you have any questions, please contact your administrator.</p>
                    <br/>
                    <p>Best regards,</p>
                    <p><strong>SecuFusion Team</strong></p>
                </div>
            </body>
            </html>
            """.formatted(tenantName, userName);
    }

    private String buildPasswordResetTemplate(String userName, String resetLink) {
        return """
            <html>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
                <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                    <h2 style="color: #2c3e50;">Password Reset Request</h2>
                    <p>Hello %s,</p>
                    <p>We received a request to reset your password. Click the button below to proceed:</p>
                    <p style="text-align: center; margin: 30px 0;">
                        <a href="%s" style="background-color: #3498db; color: white; padding: 12px 24px; text-decoration: none; border-radius: 4px;">Reset Password</a>
                    </p>
                    <p>If you didn't request this, please ignore this email.</p>
                    <p>This link will expire in 24 hours.</p>
                    <br/>
                    <p>Best regards,</p>
                    <p><strong>SecuFusion Team</strong></p>
                </div>
            </body>
            </html>
            """.formatted(userName, resetLink);
    }
}
