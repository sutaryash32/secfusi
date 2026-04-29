package com.secufusion.events.entity;

/**
 * Classification of file operation types for browser events.
 */
public enum FileOperationType {

    /**
     * File download operation.
     */
    DOWNLOAD,

    /**
     * File upload operation.
     */
    UPLOAD,

    /**
     * Print operation.
     */
    PRINT,

    /**
     * Copy to clipboard operation.
     */
    CLIPBOARD_COPY,

    /**
     * Paste from clipboard operation.
     */
    CLIPBOARD_PASTE,

    /**
     * Prohibited extension was installed and blocked.
     */
    BLOCKED_EXTENSION_INSTALLED,

    /**
     * PII detected in a file during upload/download.
     */
    PII_IN_FILE,

    /**
     * URL blocked by blacklist/whitelist policy.
     */
    URL_BLOCKED,

    /**
     * Extension compliance violation triggered browser block.
     */
    EXTENSION_COMPLIANCE_BLOCK,

    /**
     * File download operation (alias sent by browser extension).
     */
    FILE_DOWNLOAD,

    /**
     * File upload operation (alias sent by browser extension).
     */
    FILE_UPLOAD,

    /**
     * Phishing site detected (warned or blocked).
     */
    PHISHING_DETECTION,

    /**
     * Credential submission to untrusted domain blocked.
     */
    CREDENTIAL_THEFT_BLOCKED,

    /**
     * User reported phishing warning as false positive.
     */
    PHISHING_FALSE_POSITIVE,

    /**
     * Suspicious or unknown script injection detected.
     */
    SCRIPT_INJECTION,

    /**
     * Form action hijacked to cross-origin domain.
     */
    FORM_HIJACKING
}
