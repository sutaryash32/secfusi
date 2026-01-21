package com.secufusion.iam.aspect;

import com.secufusion.iam.annotation.RequiresFeature;
import com.secufusion.iam.exception.FeatureNotAvailableException;
import com.secufusion.iam.service.FeatureAccessChecker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

/**
 * Aspect that enforces feature access checks using @RequiresFeature annotation.
 *
 * This aspect intercepts methods annotated with @RequiresFeature and verifies
 * that the tenant has access to the specified feature before allowing execution.
 *
 * The annotated method's first parameter must be the tenantId (String).
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class FeatureAccessAspect {

    private final FeatureAccessChecker featureAccessChecker;

    @Before("@annotation(requiresFeature)")
    public void checkFeatureAccess(JoinPoint joinPoint, RequiresFeature requiresFeature) {
        String featureCode = requiresFeature.value();
        String minimumLevel = requiresFeature.minimumLevel();
        String requiredPackage = requiresFeature.requiredPackage();
        String customMessage = requiresFeature.message();

        // Extract tenantId from method arguments
        String tenantId = extractTenantId(joinPoint);

        if (tenantId == null) {
            log.error("Cannot extract tenantId from method arguments for feature check: {}",
                    joinPoint.getSignature().toShortString());
            throw new IllegalArgumentException(
                    "Method annotated with @RequiresFeature must have tenantId as first parameter");
        }

        log.debug("Checking feature access: tenant={}, feature={}, minimumLevel={}",
                tenantId, featureCode, minimumLevel);

        // Check basic access
        if (!featureAccessChecker.hasAccess(tenantId, featureCode)) {
            handleAccessDenied(tenantId, featureCode, requiredPackage, customMessage);
        }

        // Check minimum level if specified
        if (!minimumLevel.isEmpty()) {
            if (!featureAccessChecker.hasMinimumAccessLevel(tenantId, featureCode, minimumLevel)) {
                String currentLevel = featureAccessChecker.getAccessLevel(tenantId, featureCode);
                log.warn("Feature access level insufficient: tenant={}, feature={}, current={}, required={}",
                        tenantId, featureCode, currentLevel, minimumLevel);

                if (!customMessage.isEmpty()) {
                    throw new FeatureNotAvailableException(customMessage);
                }

                throw new FeatureNotAvailableException(
                        String.format("Feature '%s' requires %s access level. Current level: %s",
                                featureCode, minimumLevel, currentLevel));
            }
        }

        log.debug("Feature access granted: tenant={}, feature={}", tenantId, featureCode);
    }

    /**
     * Extract tenantId from method arguments.
     * Looks for String parameter named 'tenantId' or first String parameter.
     */
    private String extractTenantId(JoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] parameterNames = signature.getParameterNames();

        if (args == null || args.length == 0) {
            return null;
        }

        // First, try to find parameter named "tenantId"
        if (parameterNames != null) {
            for (int i = 0; i < parameterNames.length; i++) {
                if ("tenantId".equals(parameterNames[i]) && args[i] instanceof String) {
                    return (String) args[i];
                }
            }
        }

        // Fallback: use first String argument
        if (args[0] instanceof String) {
            return (String) args[0];
        }

        return null;
    }

    /**
     * Handle access denied scenario.
     */
    private void handleAccessDenied(String tenantId, String featureCode,
                                    String requiredPackage, String customMessage) {
        log.warn("Feature access denied: tenant={}, feature={}", tenantId, featureCode);

        if (!customMessage.isEmpty()) {
            throw new FeatureNotAvailableException(customMessage);
        }

        if (!requiredPackage.isEmpty()) {
            featureAccessChecker.requireFeature(tenantId, featureCode, requiredPackage);
        } else {
            featureAccessChecker.requireFeature(tenantId, featureCode);
        }
    }
}
