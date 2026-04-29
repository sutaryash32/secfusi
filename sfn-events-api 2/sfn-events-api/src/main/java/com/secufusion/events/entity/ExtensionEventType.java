package com.secufusion.events.entity;

/**
 * Classification of extension-specific events.
 */
public enum ExtensionEventType {

    /**
     * Extension was installed on a device.
     */
    EXTENSION_INSTALLED,

    /**
     * Extension was uninstalled/removed from a device.
     */
    EXTENSION_UNINSTALLED,

    /**
     * Extension was updated to a new version.
     */
    EXTENSION_UPDATED,

    /**
     * Extension was enabled after being disabled.
     */
    EXTENSION_ENABLED,

    /**
     * Extension was disabled.
     */
    EXTENSION_DISABLED,

    /**
     * Extension permissions were changed.
     */
    EXTENSION_PERMISSIONS_CHANGED,

    /**
     * Extension was blocked by policy.
     */
    EXTENSION_BLOCKED,

    /**
     * User received warning about extension.
     */
    EXTENSION_WARNING_SHOWN,

    /**
     * User acknowledged/dismissed extension warning.
     */
    EXTENSION_WARNING_ACKNOWLEDGED,

    /**
     * Extension sync completed (policy sync from server).
     */
    EXTENSION_SYNC
}
