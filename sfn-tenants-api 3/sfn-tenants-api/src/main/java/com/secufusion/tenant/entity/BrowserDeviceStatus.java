package com.secufusion.tenant.entity;

/**
 * Status of a registered browser device.
 */
public enum BrowserDeviceStatus {

    /**
     * Device is active and can send events.
     */
    ACTIVE,

    /**
     * Device has not been seen recently.
     */
    INACTIVE,

    /**
     * Device is blocked from sending events.
     */
    BLOCKED
}
