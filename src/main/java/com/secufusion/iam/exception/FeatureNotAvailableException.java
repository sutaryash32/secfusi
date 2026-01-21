package com.secufusion.iam.exception;

import lombok.Getter;

@Getter
public class FeatureNotAvailableException extends RuntimeException {

    private final String featureCode;
    private final String tenantId;
    private final String currentPackage;
    private final String requiredPackage;

    public FeatureNotAvailableException(String message) {
        super(message);
        this.featureCode = null;
        this.tenantId = null;
        this.currentPackage = null;
        this.requiredPackage = null;
    }

    public FeatureNotAvailableException(String featureCode, String tenantId) {
        super(String.format("Feature '%s' is not available for tenant '%s'", featureCode, tenantId));
        this.featureCode = featureCode;
        this.tenantId = tenantId;
        this.currentPackage = null;
        this.requiredPackage = null;
    }

    public FeatureNotAvailableException(String featureCode, String tenantId, String currentPackage, String requiredPackage) {
        super(String.format(
                "Feature '%s' is not available for tenant '%s'. Current package: %s. Upgrade to %s or higher.",
                featureCode, tenantId, currentPackage, requiredPackage));
        this.featureCode = featureCode;
        this.tenantId = tenantId;
        this.currentPackage = currentPackage;
        this.requiredPackage = requiredPackage;
    }
}
