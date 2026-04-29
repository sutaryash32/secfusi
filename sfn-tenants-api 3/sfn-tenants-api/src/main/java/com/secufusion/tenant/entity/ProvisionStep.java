package com.secufusion.tenant.entity;

/**
 * Bitmask constants for tenant provisioning steps.
 * Each step is a single bit — allows tracking which steps completed independently.
 *
 * <p>Usage:
 * <pre>
 *   // Mark step complete
 *   tenant.setProvisionStepsCompleted(
 *       tenant.getProvisionStepsCompleted() | ProvisionStep.REALM_CREATED
 *   );
 *
 *   // Check if step completed
 *   boolean done = (tenant.getProvisionStepsCompleted() & ProvisionStep.REALM_CREATED) != 0;
 *
 *   // Check if ALL steps done
 *   boolean allDone = ProvisionStep.allCompleted(tenant.getProvisionStepsCompleted());
 * </pre>
 */
public final class ProvisionStep {

    private ProvisionStep() {}

    // ── Keycloak provisioning ──
    public static final int REALM_CREATED        = 1;       // bit 0
    public static final int CLIENT_CREATED       = 1 << 1;  // bit 1
    public static final int ADMIN_USER_CREATED   = 1 << 2;  // bit 2
    public static final int SSO_CONFIGURED       = 1 << 3;  // bit 3

    // ── Application DB artifacts ──
    public static final int ROLES_CREATED        = 1 << 4;  // bit 4
    public static final int ADMIN_GROUP_CREATED  = 1 << 5;  // bit 5
    public static final int USER_GROUP_LINKED    = 1 << 6;  // bit 6
    public static final int AUTH_CONFIG_SAVED    = 1 << 7;  // bit 7
    public static final int POLICIES_CREATED     = 1 << 8;  // bit 8
    public static final int EVENTS_GROUP_CREATED = 1 << 9;  // bit 9
    public static final int SUBSCRIPTION_CREATED = 1 << 10; // bit 10

    // ── Post-activation ──
    public static final int REALM_SETTINGS_APPLIED = 1 << 11; // bit 11
    public static final int EMAILS_SENT            = 1 << 12; // bit 12
    public static final int TENANT_CODE_GENERATED  = 1 << 13; // bit 13

    /** All steps that must complete before tenant is fully provisioned. */
    public static final int ALL_REQUIRED =
            REALM_CREATED | CLIENT_CREATED | ADMIN_USER_CREATED |
            ROLES_CREATED | ADMIN_GROUP_CREATED | USER_GROUP_LINKED |
            AUTH_CONFIG_SAVED | POLICIES_CREATED | TENANT_CODE_GENERATED;

    /** Check if a specific step is completed. */
    public static boolean isCompleted(int steps, int step) {
        return (steps & step) != 0;
    }

    /** Check if all required steps are completed. */
    public static boolean allCompleted(int steps) {
        return (steps & ALL_REQUIRED) == ALL_REQUIRED;
    }

    /** Mark a step as completed. */
    public static int markCompleted(int steps, int step) {
        return steps | step;
    }
}
