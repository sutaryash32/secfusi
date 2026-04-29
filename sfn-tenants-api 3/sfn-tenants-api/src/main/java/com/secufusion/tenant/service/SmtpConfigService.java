package com.secufusion.tenant.service;

import com.secufusion.tenant.entity.SmtpConfig;
import com.secufusion.tenant.entity.Tenant;
import com.secufusion.tenant.exception.ResourceConflictException;
import com.secufusion.tenant.repository.SmtpConfigRepository;
import com.secufusion.tenant.repository.TenantRepository;
import com.secufusion.tenant.util.KeycloakAdminUtil;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

@Slf4j
@Service
public class SmtpConfigService {

    private final SmtpConfigRepository smtpConfigRepository;
    private final TenantRepository tenantRepository;
    private final KeycloakAdminUtil kcUtil;

    // Fallback to environment variables if no DB config exists
    @Value("${mail.smtp.host:}")
    private String defaultSmtpHost;

    @Value("${mail.smtp.port:587}")
    private String defaultSmtpPort;

    @Value("${mail.smtp.auth:true}")
    private String defaultSmtpAuth;

    @Value("${mail.smtp.starttls:true}")
    private String defaultSmtpStarttls;

    @Value("${mail.smtp.username:}")
    private String defaultSmtpUsername;

    @Value("${mail.smtp.password:}")
    private String defaultSmtpPassword;

    @Value("${mail.smtp.mail:}")
    private String defaultSmtpMail;

    @Autowired
    public SmtpConfigService(SmtpConfigRepository smtpConfigRepository,
                             TenantRepository tenantRepository,
                             KeycloakAdminUtil kcUtil) {
        this.smtpConfigRepository = smtpConfigRepository;
        this.tenantRepository = tenantRepository;
        this.kcUtil = kcUtil;
    }

    /* ==========================================================
       CREATE
       ========================================================== */
    @Transactional
    public SmtpConfig createSmtpConfig(String tenantId, SmtpConfig smtpConfig) {
        log.debug("Creating SMTP config for tenantId={}", tenantId);

        // Check if config already exists for tenant
        if (smtpConfigRepository.existsByFkTenantId(tenantId)) {
            throw new ResourceConflictException("SMTP Mail configuration already exists.");
        }

        smtpConfig.setFkTenantId(tenantId);
        smtpConfig.setPkSmtpConfigId(null); // Ensure new ID is generated

        SmtpConfig saved = smtpConfigRepository.save(smtpConfig);
        log.info("SMTP config created successfully. id={}, tenantId={}",
                saved.getPkSmtpConfigId(), tenantId);
        return saved;
    }

    /* ==========================================================
       READ - Get by Tenant (with fallback to default)
       ========================================================== */
    @Transactional(readOnly = true)
    public SmtpConfig getSmtpConfigForTenant(String tenantId) {
        log.debug("Fetching SMTP config for tenantId={}", tenantId);

        // First try tenant-specific config
        return smtpConfigRepository.findByFkTenantIdAndIsActiveTrue(tenantId)
                .orElseGet(() -> {
                    log.debug("No tenant-specific SMTP config found, using default");
                    return getDefaultSmtpConfig();
                });
    }

    /* ==========================================================
       READ - Get Default Config
       ========================================================== */
    @Transactional(readOnly = true)
    public SmtpConfig getDefaultSmtpConfig() {
        log.debug("Fetching default SMTP config");

        return smtpConfigRepository.findByFkTenantIdIsNullAndIsActiveTrue()
                .orElseGet(() -> {
                    log.debug("No default SMTP config in DB, building from environment");
                    return buildFromEnvironment();
                });
    }

    /* ==========================================================
       READ - Get Tenant's Own Config (without fallback)
       ========================================================== */
    @Transactional(readOnly = true)
    public SmtpConfig getTenantSmtpConfig(String tenantId) {
        log.debug("Fetching tenant's own SMTP config for tenantId={}", tenantId);
        return smtpConfigRepository.findByFkTenantId(tenantId)
                .orElse(null);
    }

    /* ==========================================================
       UPDATE
       ========================================================== */
    @Transactional
    public SmtpConfig updateSmtpConfig(String tenantId, SmtpConfig updatedConfig) {
        log.debug("Updating SMTP config for tenantId={}", tenantId);

        SmtpConfig existing = smtpConfigRepository.findByFkTenantId(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("SMTP config not found for tenant: " + tenantId));

        existing.setHost(updatedConfig.getHost());
        existing.setPort(updatedConfig.getPort());
        existing.setAuth(updatedConfig.getAuth());
        existing.setStarttls(updatedConfig.getStarttls());
        existing.setSsl(updatedConfig.getSsl());
        existing.setUsername(updatedConfig.getUsername());

        // Only update password if provided (not null or empty)
        if (updatedConfig.getPassword() != null && !updatedConfig.getPassword().isEmpty()) {
            existing.setPassword(updatedConfig.getPassword());
        }

        existing.setFromEmail(updatedConfig.getFromEmail());
        existing.setFromName(updatedConfig.getFromName());
        existing.setActive(updatedConfig.isActive());

        SmtpConfig saved = smtpConfigRepository.save(existing);
        log.info("SMTP config updated successfully. id={}, tenantId={}",
                saved.getPkSmtpConfigId(), tenantId);
        return saved;
    }

    /* ==========================================================
       DELETE
       ========================================================== */
    @Transactional
    public void deleteSmtpConfig(String tenantId) {
        log.debug("Deleting SMTP config for tenantId={}", tenantId);

        SmtpConfig existing = smtpConfigRepository.findByFkTenantId(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("SMTP config not found for tenant: " + tenantId));

        smtpConfigRepository.delete(existing);
        log.info("SMTP config deleted successfully. tenantId={}", tenantId);
    }

    /* ==========================================================
       TEST SMTP Connection
       ========================================================== */
    public String testSmtpConnection(SmtpConfig config, String tenantId) {
        log.info("Testing SMTP connection to host={}, port={}", config.getHost(), config.getPort());

        // If password is empty, fall back to saved password from DB
        if (config.getPassword() == null || config.getPassword().isEmpty()) {
            log.info("Password is empty in test request, fetching saved password from DB");
            SmtpConfig savedConfig = null;
            if (tenantId != null && !tenantId.isEmpty()) {
                savedConfig = smtpConfigRepository.findByFkTenantId(tenantId).orElse(null);
            }
            if (savedConfig == null) {
                savedConfig = smtpConfigRepository.findByFkTenantIdIsNullAndIsActiveTrue().orElse(null);
            }
            if (savedConfig != null && savedConfig.getPassword() != null && !savedConfig.getPassword().isEmpty()) {
                config.setPassword(savedConfig.getPassword());
                log.info("Using saved password from DB for connection test");
            } else {
                return "Password is required but was not provided and no saved configuration was found";
            }
        }

        Properties props = buildMailProperties(config);

        try {
            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(config.getUsername(), config.getPassword());
                }
            });

            Transport transport = session.getTransport("smtp");
            transport.connect(config.getHost(), config.getPort(), config.getUsername(), config.getPassword());
            transport.close();

            log.info("SMTP connection test successful for host={}", config.getHost());
            return null; // null means success

        } catch (MessagingException e) {
            log.error("SMTP connection test failed for host={}: {}", config.getHost(), e.getMessage(), e);
            return e.getMessage();
        }
    }

    /* ==========================================================
       TEST SMTP by Sending Test Email
       ========================================================== */
    public String sendTestEmail(SmtpConfig config, String toEmail, String tenantId) {
        log.info("Sending test email to={} via host={}", toEmail, config.getHost());

        // If password is empty, fall back to saved password from DB
        if (config.getPassword() == null || config.getPassword().isEmpty()) {
            log.info("Password is empty in test request, fetching saved password from DB");
            SmtpConfig savedConfig = null;
            if (tenantId != null && !tenantId.isEmpty()) {
                savedConfig = smtpConfigRepository.findByFkTenantId(tenantId).orElse(null);
            }
            if (savedConfig == null) {
                savedConfig = smtpConfigRepository.findByFkTenantIdIsNullAndIsActiveTrue().orElse(null);
            }
            if (savedConfig != null && savedConfig.getPassword() != null && !savedConfig.getPassword().isEmpty()) {
                config.setPassword(savedConfig.getPassword());
                log.info("Using saved password from DB for test email");
            } else {
                return "Password is required but was not provided and no saved configuration was found";
            }
        }

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
            message.setSubject("SMTP Configuration Test - SecuFusion");
            message.setContent(
                    "<html><body>" +
                    "<h2>SMTP Configuration Test</h2>" +
                    "<p>This is a test email to verify your SMTP configuration.</p>" +
                    "<p>If you received this email, your SMTP settings are configured correctly.</p>" +
                    "<br/><p>- SecuFusion Team</p>" +
                    "</body></html>",
                    "text/html; charset=utf-8"
            );

            Transport.send(message);
            log.info("Test email sent successfully to={}", toEmail);
            return null; // null means success

        } catch (Exception e) {
            log.error("Failed to send test email to={}: {}", toEmail, e.getMessage(), e);
            return e.getMessage();
        }
    }

    /* ==========================================================
       Convert to Keycloak SMTP Map format
       ========================================================== */
    public Map<String, String> toKeycloakSmtpMap(SmtpConfig config) {
        Map<String, String> smtp = new HashMap<>();
        smtp.put("host", config.getHost());
        smtp.put("port", String.valueOf(config.getPort()));
        smtp.put("from", config.getFromEmail());
        smtp.put("user", config.getUsername());
        smtp.put("password", config.getPassword());
        smtp.put("auth", config.getAuth());
        smtp.put("starttls", config.getStarttls());
        smtp.put("ssl", config.getSsl());
        return smtp;
    }

    /* ==========================================================
       HELPER: Build SmtpConfig from environment variables
       ========================================================== */
    private SmtpConfig buildFromEnvironment() {
        SmtpConfig config = new SmtpConfig();
        config.setHost(defaultSmtpHost);
        config.setPort(Integer.parseInt(defaultSmtpPort));
        config.setAuth(defaultSmtpAuth);
        config.setStarttls(defaultSmtpStarttls);
        config.setSsl("false");
        config.setUsername(defaultSmtpUsername);
        config.setPassword(defaultSmtpPassword);
        config.setFromEmail(defaultSmtpMail);
        config.setFromName("SecuFusion");
        config.setActive(true);
        return config;
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
       CREATE Default Config (for initial setup)
       ========================================================== */
    @Transactional
    public SmtpConfig createDefaultSmtpConfig(SmtpConfig smtpConfig) {
        log.debug("Creating default SMTP config");

        Optional<SmtpConfig> defaultSmtpConfiguration = smtpConfigRepository.findByFkTenantIdIsNull();
        // Check if default config already exists
        if (defaultSmtpConfiguration.isPresent()) {
            return defaultSmtpConfiguration.get();
        }

        smtpConfig.setSsl("false");
        smtpConfig.setCreatedAt(LocalDateTime.now());
        smtpConfig.setActive(true);
        smtpConfig.setFkTenantId(null); // NULL = default
        smtpConfig.setPkSmtpConfigId(null);

        SmtpConfig saved = smtpConfigRepository.save(smtpConfig);
        log.info("Default SMTP config created successfully. id={}", saved.getPkSmtpConfigId());
        return saved;
    }

    /* ==========================================================
       UPDATE Default Config
       ========================================================== */
    @Transactional
    public SmtpConfig updateDefaultSmtpConfig(SmtpConfig updatedConfig) {
        log.debug("Updating default SMTP config");

        SmtpConfig existing = smtpConfigRepository.findByFkTenantIdIsNull()
                .orElseThrow(() -> new IllegalArgumentException("Default SMTP config not found"));

        existing.setHost(updatedConfig.getHost());
        existing.setPort(updatedConfig.getPort());
        existing.setAuth(updatedConfig.getAuth());
        existing.setStarttls(updatedConfig.getStarttls());
        existing.setSsl(updatedConfig.getSsl());
        existing.setUsername(updatedConfig.getUsername());

        if (updatedConfig.getPassword() != null && !updatedConfig.getPassword().isEmpty()) {
            existing.setPassword(updatedConfig.getPassword());
        }

        existing.setFromEmail(updatedConfig.getFromEmail());
        existing.setFromName(updatedConfig.getFromName());
        existing.setActive(updatedConfig.isActive());

        SmtpConfig saved = smtpConfigRepository.save(existing);
        log.info("Default SMTP config updated successfully. id={}", saved.getPkSmtpConfigId());
        return saved;
    }

    /* ==========================================================
       SYNC SMTP to Keycloak Realm
       ========================================================== */

    /**
     * Sync SMTP configuration to the tenant's Keycloak realm.
     * This updates the realm's SMTP server settings so Keycloak can send emails.
     *
     * @param tenantId the tenant ID
     * @return true if sync was successful
     */
    public boolean syncSmtpToKeycloak(String tenantId) {
        log.info("Syncing SMTP config to Keycloak for tenantId={}", tenantId);

        // Get tenant to find realm name
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));

        // Get SMTP config (tenant-specific or default)
        SmtpConfig config = getSmtpConfigForTenant(tenantId);
        Map<String, String> smtpMap = toKeycloakSmtpMap(config);

        try {
            kcUtil.updateRealmSmtpSettings(tenant.getRealmName(), smtpMap);
            log.info("SMTP config synced to Keycloak realm={} successfully", tenant.getRealmName());
            return true;
        } catch (Exception e) {
            log.error("Failed to sync SMTP config to Keycloak for tenantId={}: {}", tenantId, e.getMessage());
            return false;
        }
    }

    /**
     * Sync default SMTP configuration to all tenant Keycloak realms.
     * Useful when updating the global default config.
     *
     * @return number of realms updated successfully
     */
    public int syncDefaultSmtpToAllRealms() {
        log.info("Syncing default SMTP config to all tenant realms");

        SmtpConfig defaultConfig = getDefaultSmtpConfig();
        Map<String, String> smtpMap = toKeycloakSmtpMap(defaultConfig);

        int successCount = 0;
        for (Tenant tenant : tenantRepository.findAll()) {
            // Skip tenants that have their own SMTP config
            if (smtpConfigRepository.findByFkTenantIdAndIsActiveTrue(tenant.getTenantID()).isPresent()) {
                log.debug("Skipping tenant {} - has custom SMTP config", tenant.getTenantName());
                continue;
            }

            try {
                kcUtil.updateRealmSmtpSettings(tenant.getRealmName(), smtpMap);
                successCount++;
            } catch (Exception e) {
                log.warn("Failed to sync SMTP to realm={}: {}", tenant.getRealmName(), e.getMessage());
            }
        }

        log.info("Synced default SMTP config to {} realms", successCount);
        return successCount;
    }
}
