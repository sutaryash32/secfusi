package com.secufusion.iam.util;

public class ResponseCodes {

    private ResponseCodes() {}

    // ===== Common =====
    public static final String BAD_REQUEST = "BAD REQUEST";
    public static final String INTERNAL_SERVER_ERROR = "INTERNAL SERVER ERROR";
    public static final String VALIDATION_ERROR = "VALIDATION ERROR";

    // ===== Resource =====
    public static final String RESOURCE_NOT_FOUND = "RESOURCE NOT FOUND";
    public static final String RESOURCE_CONFLICT = "RESOURCE CONFLICT";

    // ===== Security / Auth =====
    public static final String ACCESS_DENIED = "ACCESS DENIED";
    public static final String AUTHENTICATION_FAILED = "AUTHENTICATION FAILED";
    public static final String FEATURE_NOT_AVAILABLE = "FEATURE_NOT_AVAILABLE";
    public static final String INVALID_TOKEN = "INVALID TOKEN";
    public static final String TOKEN_EXPIRED = "TOKEN EXPIRED";
    public static final String TOKEN_MISMATCH = "TOKEN MISMATCH";
    public static final String TOKEN_VALIDATION_FAILED = "TOKEN VALIDATION FAILED";
    public static final String MISSING_AUTHORIZATION = "MISSING AUTHORIZATION";

    // =====================================================
    // TENANT – VALIDATION
    // =====================================================
    public static final String ORGANIZATION_NAME_REQUIRED = "ORGANIZATION_NAME_REQUIRED";
    public static final String ORGANIZATION_NAME_ALREADY_EXISTS = "ORGANIZATION_NAME_ALREADY_EXISTS";

    public static final String INVALID_DOMAIN = "INVALID_DOMAIN";
    public static final String DOMAIN_ALREADY_EXISTS = "DOMAIN_ALREADY_EXISTS";

    public static final String ORGANIZATION_PHONE_REQUIRED = "ORGANIZATION_PHONE_REQUIRED";
    public static final String ORGANIZATION_PHONE_ALREADY_EXISTS = "ORGANIZATION_PHONE_ALREADY_EXISTS";

    public static final String ORGANIZATION_EMAIL_REQUIRED = "ORGANIZATION_EMAIL_REQUIRED";
    public static final String ORGANIZATION_EMAIL_ALREADY_EXISTS = "ORGANIZATION_EMAIL_ALREADY_EXISTS";

    public static final String TENANT_NOT_FOUND = "TENANT_NOT_FOUND";
    public static final String TENANT_ALREADY_ACTIVE = "TENANT_ALREADY_ACTIVE";
    // =====================================================
    // ADMIN USER VALIDATION
    // =====================================================
    public static final String ADMIN_EMAIL_REQUIRED = "ADMIN_EMAIL_REQUIRED";
    public static final String ADMIN_EMAIL_ALREADY_EXISTS = "ADMIN_EMAIL_ALREADY_EXISTS";

    public static final String ADMIN_PHONE_REQUIRED = "ADMIN_PHONE_REQUIRED";
    public static final String ADMIN_PHONE_ALREADY_EXISTS = "ADMIN_PHONE_ALREADY_EXISTS";

    public static final String DEFAULT_ADMIN_MISSING = "DEFAULT ADMIN MISSING";
    public static final String USERNAME_GENERATION_FAILED = "USERNAME_GENERATION_FAILED";

    // =====================================================
    // KEYCLOAK / IAM
    // =====================================================
    public static final String KEYCLOAK_OPERATION_FAILED = "KEYCLOAK_OPERATION_FAILED";

    public static final String REALM_CREATION_FAILED = "REALM_CREATION_FAILED";
    public static final String ORGANIZATION_REALM_ALREADY_EXISTS = "ORGANIZATION_REALM_ALREADY_EXISTS";

    public static final String CLIENT_CREATION_FAILED = "CLIENT_CREATION_FAILED";
    public static final String EXTENSION_CLIENT_CREATION_FAILED = "EXTENSION_CLIENT_CREATION_FAILED";

    public static final String USER_CREATION_FAILED = "USER_CREATION_FAILED";
    public static final String USER_CONFIGURATION_FAILED = "USER_CONFIGURATION_FAILED";

    public static final String UNKNOWN_PROVISIONING_STATE = "UNKNOWN_PROVISIONING_STATE";

    // =====================================================
    // EMAIL / NOTIFICATIONS
    // =====================================================
    public static final String EMAIL_SEND_FAILED = "UNABLE_TO_SEND_EMAIL";

    // =====================================================
    // CONFIG / SSO
    // =====================================================
    public static final String AUTH_PROVIDER_CONFIG_MISSING = "AUTH_PROVIDER_CONFIG_MISSING";
}
