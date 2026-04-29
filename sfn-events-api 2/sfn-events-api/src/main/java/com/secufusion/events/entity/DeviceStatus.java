package com.secufusion.events.entity;

/**
 * Status of a registered device.
 */
public enum DeviceStatus {

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
