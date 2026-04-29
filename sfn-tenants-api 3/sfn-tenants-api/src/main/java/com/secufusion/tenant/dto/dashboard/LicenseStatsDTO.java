package com.secufusion.tenant.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * License statistics - visible to MASTER_MSSP and MSSP only.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LicenseStatsDTO {

    // License capacity
    private long totalLicenses;
    private long usedLicenses;
    private long availableLicenses;
    private double utilizationPercentage;

    // License allocation (for MSSP)
    private long licensesAllocatedToEnterprises;
    private long unallocatedLicenses;

    // License status
    private LocalDate licenseExpiryDate;
    private long daysUntilExpiry;
    private boolean isExpiringSoon; // Within 30 days
    private boolean isExpired;

    // License type
    private String licenseType;
    private String licenseTier;

    // For hierarchy views (MASTER_MSSP)
    private long totalLicensesAcrossAllMssps;
    private long usedLicensesAcrossAllMssps;

    public static LicenseStatsDTO forMssp(long totalLicenses, long usedLicenses,
            long licensesAllocatedToEnterprises, LocalDate expiryDate, String licenseType, String licenseTier) {
        long available = totalLicenses - usedLicenses;
        long daysUntil = expiryDate != null ?
                java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), expiryDate) : 0;

        return LicenseStatsDTO.builder()
                .totalLicenses(totalLicenses)
                .usedLicenses(usedLicenses)
                .availableLicenses(available)
                .utilizationPercentage(totalLicenses > 0 ? (double) usedLicenses / totalLicenses * 100 : 0)
                .licensesAllocatedToEnterprises(licensesAllocatedToEnterprises)
                .unallocatedLicenses(totalLicenses - licensesAllocatedToEnterprises)
                .licenseExpiryDate(expiryDate)
                .daysUntilExpiry(daysUntil)
                .isExpiringSoon(daysUntil > 0 && daysUntil <= 30)
                .isExpired(daysUntil < 0)
                .licenseType(licenseType)
                .licenseTier(licenseTier)
                .build();
    }

    public static LicenseStatsDTO forMasterMssp(long totalLicenses, long usedLicenses,
            long totalAcrossAllMssps, long usedAcrossAllMssps, LocalDate expiryDate,
            String licenseType, String licenseTier) {
        long available = totalLicenses - usedLicenses;
        long daysUntil = expiryDate != null ?
                java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), expiryDate) : 0;

        return LicenseStatsDTO.builder()
                .totalLicenses(totalLicenses)
                .usedLicenses(usedLicenses)
                .availableLicenses(available)
                .utilizationPercentage(totalLicenses > 0 ? (double) usedLicenses / totalLicenses * 100 : 0)
                .licenseExpiryDate(expiryDate)
                .daysUntilExpiry(daysUntil)
                .isExpiringSoon(daysUntil > 0 && daysUntil <= 30)
                .isExpired(daysUntil < 0)
                .licenseType(licenseType)
                .licenseTier(licenseTier)
                .totalLicensesAcrossAllMssps(totalAcrossAllMssps)
                .usedLicensesAcrossAllMssps(usedAcrossAllMssps)
                .build();
    }

    public static LicenseStatsDTO forPlatformAdmin(long totalLicenses, long usedLicenses,
            long totalAcrossAllMssps, long usedAcrossAllMssps, LocalDate expiryDate,
            String licenseType, String licenseTier) {
        long available = totalLicenses - usedLicenses;
        long daysUntil = expiryDate != null ?
                java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), expiryDate) : 0;

        return LicenseStatsDTO.builder()
                .totalLicenses(totalLicenses)
                .usedLicenses(usedLicenses)
                .availableLicenses(available)
                .utilizationPercentage(totalLicenses > 0 ? (double) usedLicenses / totalLicenses * 100 : 0)
                .licenseExpiryDate(expiryDate)
                .daysUntilExpiry(daysUntil)
                .isExpiringSoon(daysUntil > 0 && daysUntil <= 30)
                .isExpired(daysUntil < 0)
                .licenseType(licenseType)
                .licenseTier(licenseTier)
                .totalLicensesAcrossAllMssps(totalAcrossAllMssps)
                .usedLicensesAcrossAllMssps(usedAcrossAllMssps)
                .build();
    }

    public static LicenseStatsDTO empty() {
        return LicenseStatsDTO.builder()
                .totalLicenses(0)
                .usedLicenses(0)
                .availableLicenses(0)
                .utilizationPercentage(0)
                .build();
    }
}
