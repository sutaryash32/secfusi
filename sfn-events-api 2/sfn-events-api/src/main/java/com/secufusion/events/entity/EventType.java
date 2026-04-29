package com.secufusion.events.entity;

/**
 * Classification of browser extension events by type.
 */
public enum EventType {

    /**
     * URL navigation/page load events.
     */
    WEBSITE_VISIT,

    /**
     * Analytics/tracking detection events.
     */
    TRACKING_ACTIVITY,

    /**
     * File operations (generic — legacy, kept for backward compatibility).
     */
    FILE_OPERATION,

    /**
     * File download operation.
     */
    FILE_DOWNLOAD,

    /**
     * File upload operation.
     */
    FILE_UPLOAD,

    /**
     * Print operation.
     */
    FILE_PRINT,

    /**
     * Copy to clipboard operation.
     */
    FILE_CLIPBOARD_COPY,

    /**
     * Paste from clipboard operation.
     */
    FILE_CLIPBOARD_PASTE,

    /**
     * User behavior: copy/paste, screenshot, idle detection.
     */
    USER_BEHAVIOR,

    /**
     * Extension settings changes, policy sync events.
     */
    SYSTEM_CONFIGURATION,

    /**
     * Blocked actions, DLP triggers, policy violations.
     */
    POLICY_VIOLATION,

    /**
     * Security threats: CSP violations, malware detection, phishing attempts.
     */
    SECURITY_THREAT,

    /**
     * Extension policy violations: blocked extensions, compliance blocks.
     */
    EXTENSION_VIOLATION,

    /**
     * DLP events: file upload/download monitoring, PII detection.
     */
    DLP_EVENT,

    /**
     * Network violations: blocked URLs, blacklist/whitelist enforcement.
     */
    NETWORK_VIOLATION
}
