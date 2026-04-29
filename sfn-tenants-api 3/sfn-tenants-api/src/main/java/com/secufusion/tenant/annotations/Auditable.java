package com.secufusion.tenant.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark entities for audit logging.
 *
 * When applied to an entity class, the AdvancedAuditListener will capture
 * INSERT, UPDATE, and DELETE operations and log them to the audit_log table.
 *
 * By default, all entities are audited. Use this annotation with enabled=false
 * to exclude specific entities from auditing.
 *
 * Example usage:
 * <pre>
 * {@code
 * @Entity
 * @Auditable  // Enable auditing (default)
 * public class BrowserPolicy { ... }
 *
 * @Entity
 * @Auditable(enabled = false)  // Disable auditing for this entity
 * public class TempData { ... }
 * }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {

    /**
     * Whether auditing is enabled for this entity.
     * Default is true.
     */
    boolean enabled() default true;

    /**
     * Optional description or reason for audit configuration.
     */
    String description() default "";
}
