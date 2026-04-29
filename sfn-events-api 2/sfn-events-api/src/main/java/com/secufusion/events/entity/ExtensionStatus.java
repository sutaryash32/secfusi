package com.secufusion.events.entity;

/**
 * Status of an installed extension on a device.
 */
public enum ExtensionStatus {

    /**
     * Extension is active and running.
     */
    ACTIVE,

    /**
     * Extension is installed but disabled by user.
     */
    DISABLED,

    /**
     * Extension is blocked by policy.
     */
    BLOCKED,

    /**
     * Extension has a warning but user can continue using it.
     */
    WARNING,

    /**
     * Extension was uninstalled (soft delete for history tracking).
     */
    UNINSTALLED,

    /**
     * Extension status is unknown.
     */
    UNKNOWN
}
