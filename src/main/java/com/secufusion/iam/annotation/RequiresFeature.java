package com.secufusion.iam.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to enforce feature access based on tenant's subscription package.
 *
 * Usage:
 * <pre>
 * {@code
 * @RequiresFeature("DLP_BLOCK_UPLOADS")
 * public void blockUpload(String tenantId, UploadRequest request) {
 *     // This method will only execute if tenant has access to DLP_BLOCK_UPLOADS
 * }
 *
 * @RequiresFeature(value = "SECOPS_MITRE_MAPPING", minimumLevel = "ADVANCED")
 * public void getMitreMapping(String tenantId) {
 *     // This method requires ADVANCED or higher access level
 * }
 *
 * @RequiresFeature(value = "DLP_CLIPBOARD_CONTROLS", requiredPackage = "Standard")
 * public void clipboardControl(String tenantId) {
 *     // Shows "Upgrade to Standard" message if access denied
 * }
 * }
 * </pre>
 *
 * Note: The first parameter of the annotated method must be tenantId (String).
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresFeature {

    /**
     * The feature code to check (e.g., "DLP_BLOCK_UPLOADS", "SECOPS_MITRE_MAPPING")
     */
    String value();

    /**
     * Optional: Minimum access level required (e.g., "ADVANCED", "BASIC", "YES")
     * If not specified, any access level > 0 is accepted.
     */
    String minimumLevel() default "";

    /**
     * Optional: The package required for upgrade message (e.g., "Basic", "Standard", "Premium")
     */
    String requiredPackage() default "";

    /**
     * Optional: Custom error message if feature is not available.
     */
    String message() default "";
}
