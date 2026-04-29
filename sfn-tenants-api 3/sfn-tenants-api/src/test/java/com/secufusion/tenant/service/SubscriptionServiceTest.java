package com.secufusion.tenant.service;

import com.secufusion.tenant.dto.SubscriptionSummary;
import com.secufusion.tenant.entity.*;
import com.secufusion.tenant.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * Test suite for SubscriptionService
 * 
 * Coverage:
 * - Delete subscriptions by tenant
 * - Get active subscription
 * - Get subscription history
 * - Check if tenant has active subscription
 * - Create subscription (paid and trial)
 * - Create default (Freemium) subscription
 * - Upgrade subscription
 * - Convert trial to paid
 * - Cancel subscription
 * - Get packages, pricing, billing cycles
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionService Tests")
class SubscriptionServiceTest {

    @Mock
    private TenantSubscriptionRepository subscriptionRepository;

    @Mock
    private SubscriptionPackageRepository packageRepository;

    @Mock
    private BillingCycleRepository billingCycleRepository;

    @Mock
    private PackagePricingRepository pricingRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private SubscriptionHistoryRepository historyRepository;

    @InjectMocks
    private SubscriptionService subscriptionService;

    private Tenant testTenant;
    private SubscriptionPackage testPackage;
    private BillingCycle testBillingCycle;
    private PackagePricing testPricing;
    private TenantSubscription testSubscription;
    private String tenantId = "tenant-001";

    @BeforeEach
    void setUp() {
        testTenant = new Tenant();
        testTenant.setTenantID(tenantId);
        testTenant.setTenantName("Test Tenant");

        testPackage = new SubscriptionPackage();
        testPackage.setPkPackageId(1L);
        testPackage.setPackageName("Professional");
        testPackage.setIsTrialAvailable(true);
        testPackage.setTrialDays(14);

        testBillingCycle = new BillingCycle();
        testBillingCycle.setPkBillingCycleId(1L);
        testBillingCycle.setCycleCode("MONTHLY");
        testBillingCycle.setDurationMonths(1);

        testPricing = new PackagePricing();
        testPricing.setBasePrice(new BigDecimal("99.99"));
        testPricing.setFinalPrice(new BigDecimal("99.99"));
        testPricing.setCurrency("USD");

        testSubscription = new TenantSubscription();
        testSubscription.setPkSubscriptionId(100L);
        testSubscription.setTenant(testTenant);
        testSubscription.setPkg(testPackage);
        testSubscription.setBillingCycle(testBillingCycle);
        testSubscription.setPricing(testPricing);
        testSubscription.setStatus(TenantSubscription.Status.ACTIVE);
        testSubscription.setStartDate(LocalDate.now());
        testSubscription.setIsTrial(false);
        testSubscription.setAutoRenew(true);
        testSubscription.setBillingAmount(new BigDecimal("99.99"));
        testSubscription.setCurrency("USD");
    }

    @Nested
    @DisplayName("Delete Subscriptions")
    class DeleteSubscriptionsTests {

        @Test
        @DisplayName("Should delete subscriptions by tenant ID")
        void shouldDeleteByTenantId() {
            // ACT
            subscriptionService.deleteSubscriptionsByTenantId(tenantId);

            // ASSERT
            verify(historyRepository, times(1)).deleteByTenantId(tenantId);
            verify(subscriptionRepository, times(1)).deleteByTenantId(tenantId);
        }
    }

    @Nested
    @DisplayName("Query Subscriptions")
    class QuerySubscriptionsTests {

        @Test
        @DisplayName("Should get active subscription for tenant")
        void shouldGetActiveSubscription() {
            // ARRANGE
            when(subscriptionRepository.findActiveByTenantId(tenantId))
                    .thenReturn(Optional.of(testSubscription));

            // ACT
            Optional<SubscriptionSummary> result = subscriptionService.getActiveSubscription(tenantId);

            // ASSERT
            assertTrue(result.isPresent());
            verify(subscriptionRepository, times(1)).findActiveByTenantId(tenantId);
        }

        @Test
        @DisplayName("Should return empty optional when no active subscription")
        void shouldReturnEmptyWhenNoActiveSubscription() {
            // ARRANGE
            when(subscriptionRepository.findActiveByTenantId(tenantId))
                    .thenReturn(Optional.empty());

            // ACT
            Optional<SubscriptionSummary> result = subscriptionService.getActiveSubscription(tenantId);

            // ASSERT
            assertFalse(result.isPresent());
        }

        @Test
        @DisplayName("Should get subscription history for tenant")
        void shouldGetSubscriptionHistory() {
            // ARRANGE
            List<TenantSubscription> history = List.of(testSubscription);
            when(subscriptionRepository.findAllByTenantId(tenantId))
                    .thenReturn(history);

            // ACT
            List<SubscriptionSummary> result = subscriptionService.getSubscriptionHistory(tenantId);

            // ASSERT
            assertNotNull(result);
            assertFalse(result.isEmpty());
            verify(subscriptionRepository, times(1)).findAllByTenantId(tenantId);
        }

        @Test
        @DisplayName("Should check if tenant has active subscription")
        void shouldCheckActiveSubscription() {
            // ARRANGE
            when(subscriptionRepository.findActiveByTenantId(tenantId))
                    .thenReturn(Optional.of(testSubscription));

            // ACT
            boolean result = subscriptionService.hasActiveSubscription(tenantId);

            // ASSERT
            assertTrue(result);
        }

        @Test
        @DisplayName("Should return false when no active subscription")
        void shouldReturnFalseWhenNoActiveSubscription() {
            // ARRANGE
            when(subscriptionRepository.findActiveByTenantId(tenantId))
                    .thenReturn(Optional.empty());

            // ACT
            boolean result = subscriptionService.hasActiveSubscription(tenantId);

            // ASSERT
            assertFalse(result);
        }

        @Test
        @DisplayName("Should get subscription by ID")
        void shouldGetSubscriptionById() {
            // ARRANGE
            when(subscriptionRepository.findById(100L))
                    .thenReturn(Optional.of(testSubscription));

            // ACT
            Optional<SubscriptionSummary> result = subscriptionService.getSubscriptionById(100L);

            // ASSERT
            assertTrue(result.isPresent());
        }
    }

    @Nested
    @DisplayName("Create Subscription")
    class CreateSubscriptionTests {

        @Test
        @DisplayName("Should create paid subscription successfully")
        void shouldCreatePaidSubscription() {
            // ARRANGE
            when(tenantRepository.findByTenantID(tenantId))
                    .thenReturn(Optional.of(testTenant));
            when(packageRepository.findById(1L))
                    .thenReturn(Optional.of(testPackage));
            when(billingCycleRepository.findById(1L))
                    .thenReturn(Optional.of(testBillingCycle));
            when(pricingRepository.findByPackageIdAndBillingCycleId(1L, 1L))
                    .thenReturn(Optional.of(testPricing));

            when(subscriptionRepository.save(any(TenantSubscription.class)))
                    .thenReturn(testSubscription);

            when(historyRepository.save(any(SubscriptionHistory.class)))
                    .thenReturn(null);

            // ACT
            SubscriptionSummary result = subscriptionService.createSubscription(
                    tenantId, 1L, 1L, false, "admin-001");

            // ASSERT
            assertNotNull(result);
            verify(subscriptionRepository, times(1)).save(argThat(sub ->
                    sub.getTenant().getTenantID().equals(tenantId) &&
                    sub.getStatus() == TenantSubscription.Status.ACTIVE &&
                    !sub.getIsTrial()
            ));
            verify(historyRepository, times(1)).save(any(SubscriptionHistory.class));
        }

        @Test
        @DisplayName("Should create trial subscription when trial available")
        void shouldCreateTrialSubscription() {
            // ARRANGE
            testPackage.setIsTrialAvailable(true);

            when(tenantRepository.findByTenantID(tenantId))
                    .thenReturn(Optional.of(testTenant));
            when(packageRepository.findById(1L))
                    .thenReturn(Optional.of(testPackage));
            when(billingCycleRepository.findById(1L))
                    .thenReturn(Optional.of(testBillingCycle));
            when(pricingRepository.findByPackageIdAndBillingCycleId(1L, 1L))
                    .thenReturn(Optional.of(testPricing));

            TenantSubscription trialSub = new TenantSubscription();
            trialSub.setPkSubscriptionId(1L);
            trialSub.setTenant(testTenant);
            trialSub.setPkg(testPackage);
            trialSub.setBillingCycle(testBillingCycle);
            trialSub.setPricing(testPricing);
            trialSub.setStatus(TenantSubscription.Status.TRIAL);
            trialSub.setIsTrial(true);
            trialSub.setTrialDays(14);

            when(subscriptionRepository.save(any(TenantSubscription.class)))
                    .thenReturn(trialSub);

            // ACT
            SubscriptionSummary result = subscriptionService.createSubscription(
                    tenantId, 1L, 1L, true, "admin-001");

            // ASSERT
            assertNotNull(result);
            verify(subscriptionRepository, times(1)).save(any(TenantSubscription.class));
        }

        @Test
        @DisplayName("Should throw exception when tenant not found")
        void shouldThrowExceptionWhenTenantNotFound() {
            // ARRANGE
            when(tenantRepository.findByTenantID(tenantId))
                    .thenReturn(Optional.empty());

            // ACT & ASSERT
            assertThrows(Exception.class, () ->
                    subscriptionService.createSubscription(tenantId, 1L, 1L, false, "admin"));
        }
    }

    @Nested
    @DisplayName("Create Default Subscription")
    class CreateDefaultSubscriptionTests {

        @Test
        @DisplayName("Should create Freemium subscription for new tenant")
        void shouldCreateFreemiumSubscription() {
            // ARRANGE
            SubscriptionPackage freemium = new SubscriptionPackage();
            freemium.setPkPackageId(0L);
            freemium.setPackageName("Freemium");

            when(tenantRepository.findByTenantID(tenantId))
                    .thenReturn(Optional.of(testTenant));
            when(packageRepository.findByPackageName("Freemium"))
                    .thenReturn(Optional.of(freemium));
            when(billingCycleRepository.findByCycleCode("MONTHLY"))
                    .thenReturn(Optional.of(testBillingCycle));

            TenantSubscription freemiumSub = new TenantSubscription();
            freemiumSub.setPkSubscriptionId(200L);
            freemiumSub.setTenant(testTenant);
            freemiumSub.setPkg(freemium);
            freemiumSub.setBillingCycle(testBillingCycle);
            freemiumSub.setPricing(testPricing);
            freemiumSub.setStatus(TenantSubscription.Status.ACTIVE);
            freemiumSub.setIsTrial(false);
            freemiumSub.setBillingAmount(BigDecimal.ZERO);

            when(subscriptionRepository.save(any(TenantSubscription.class)))
                    .thenReturn(freemiumSub);

            when(historyRepository.save(any(SubscriptionHistory.class)))
                    .thenReturn(null);

            // ACT
            SubscriptionSummary result = subscriptionService.createDefaultSubscription(tenantId, "system");

            // ASSERT
            assertNotNull(result);
            verify(subscriptionRepository, times(1)).save(argThat(sub ->
                    sub.getStatus() == TenantSubscription.Status.ACTIVE &&
                    sub.getBillingAmount().compareTo(BigDecimal.ZERO) == 0
            ));
        }

        @Test
        @DisplayName("Should throw exception when tenant not found")
        void shouldThrowExceptionWhenTenantNotFound() {
            // ARRANGE
            when(tenantRepository.findByTenantID(tenantId))
                    .thenReturn(Optional.empty());

            // ACT & ASSERT
            assertThrows(Exception.class, () ->
                    subscriptionService.createDefaultSubscription(tenantId, "system"));
        }
    }

    @Nested
    @DisplayName("Upgrade Subscription")
    class UpgradeSubscriptionTests {

        @Test
        @DisplayName("Should upgrade subscription to new package")
        void shouldUpgradeSubscription() {
            // ARRANGE
            SubscriptionPackage premiumPackage = new SubscriptionPackage();
            premiumPackage.setPkPackageId(2L);
            premiumPackage.setPackageName("Premium");

            when(subscriptionRepository.findActiveByTenantId(tenantId))
                    .thenReturn(Optional.of(testSubscription));
            when(packageRepository.findById(2L))
                    .thenReturn(Optional.of(premiumPackage));
            when(billingCycleRepository.findById(1L))
                    .thenReturn(Optional.of(testBillingCycle));
            when(pricingRepository.findByPackageIdAndBillingCycleId(2L, 1L))
                    .thenReturn(Optional.of(testPricing));

            TenantSubscription upgraded = new TenantSubscription();
            upgraded.setPkSubscriptionId(100L);
            upgraded.setTenant(testTenant);
            upgraded.setPkg(premiumPackage);
            upgraded.setBillingCycle(testBillingCycle);
            upgraded.setPricing(testPricing);
            upgraded.setStatus(TenantSubscription.Status.ACTIVE);

            when(subscriptionRepository.save(any(TenantSubscription.class)))
                    .thenReturn(upgraded);

            when(historyRepository.save(any(SubscriptionHistory.class)))
                    .thenReturn(null);

            // ACT
            SubscriptionSummary result = subscriptionService.upgradeSubscription(
                    tenantId, 2L, 1L, "admin-001");

            // ASSERT
            assertNotNull(result);
            verify(subscriptionRepository, times(1)).findActiveByTenantId(tenantId);
            verify(subscriptionRepository, times(1)).save(any(TenantSubscription.class));
        }

        @Test
        @DisplayName("Should throw exception when no active subscription")
        void shouldThrowExceptionWhenNoActiveSubscription() {
            // ARRANGE
            when(subscriptionRepository.findActiveByTenantId(tenantId))
                    .thenReturn(Optional.empty());

            // ACT & ASSERT
            assertThrows(Exception.class, () ->
                    subscriptionService.upgradeSubscription(tenantId, 2L, 1L, "admin"));
        }
    }

    @Nested
    @DisplayName("Convert Trial")
    class ConvertTrialTests {

        @Test
        @DisplayName("Should convert trial subscription to paid")
        void shouldConvertTrialToPaid() {
            // ARRANGE
            TenantSubscription trialSub = new TenantSubscription();
            trialSub.setPkSubscriptionId(100L);
            trialSub.setTenant(testTenant);
            trialSub.setPkg(testPackage);
            trialSub.setBillingCycle(testBillingCycle);
            trialSub.setStatus(TenantSubscription.Status.TRIAL);
            trialSub.setIsTrial(true);
            trialSub.setTrialDays(14);

            when(subscriptionRepository.findByTenantIdAndStatus(tenantId, TenantSubscription.Status.TRIAL))
                    .thenReturn(Optional.of(trialSub));
            when(billingCycleRepository.findById(1L))
                    .thenReturn(Optional.of(testBillingCycle));
            when(pricingRepository.findByPackageIdAndBillingCycleId(any(), eq(1L)))
                    .thenReturn(Optional.of(testPricing));

            TenantSubscription converted = new TenantSubscription();
            converted.setPkSubscriptionId(100L);
            converted.setTenant(testTenant);
            converted.setPkg(testPackage);
            converted.setBillingCycle(testBillingCycle);
            converted.setPricing(testPricing);
            converted.setStatus(TenantSubscription.Status.ACTIVE);
            converted.setIsTrial(false);
            converted.setTrialConverted(true);

            when(subscriptionRepository.save(any(TenantSubscription.class)))
                    .thenReturn(converted);

            when(historyRepository.save(any(SubscriptionHistory.class)))
                    .thenReturn(null);

            // ACT
            SubscriptionSummary result = subscriptionService.convertTrial(tenantId, 1L, "admin");

            // ASSERT
            assertNotNull(result);
            verify(subscriptionRepository, times(1)).findByTenantIdAndStatus(tenantId, TenantSubscription.Status.TRIAL);
        }
    }

    @Nested
    @DisplayName("Cancel Subscription")
    class CancelSubscriptionTests {

        @Test
        @DisplayName("Should cancel subscription immediately")
        void shouldCancelSubscriptionImmediately() {
            // ARRANGE
            when(subscriptionRepository.findActiveByTenantId(tenantId))
                    .thenReturn(Optional.of(testSubscription));

            TenantSubscription cancelled = new TenantSubscription();
            cancelled.setPkSubscriptionId(100L);
            cancelled.setTenant(testTenant);
            cancelled.setPkg(testPackage);
            cancelled.setBillingCycle(testBillingCycle);
            cancelled.setPricing(testPricing);
            cancelled.setStatus(TenantSubscription.Status.CANCELLED);
            cancelled.setAutoRenew(false);

            when(subscriptionRepository.save(any(TenantSubscription.class)))
                    .thenReturn(cancelled);

            when(historyRepository.save(any(SubscriptionHistory.class)))
                    .thenReturn(null);

            // ACT
            SubscriptionSummary result = subscriptionService.cancelSubscription(
                    tenantId, "No longer needed", true, "admin");

            // ASSERT
            assertNotNull(result);
            verify(subscriptionRepository, times(1)).save(argThat(sub ->
                    sub.getStatus() == TenantSubscription.Status.CANCELLED
            ));
        }
    }

    @Nested
    @DisplayName("Get Packages and Pricing")
    class GetPackagesAndPricingTests {

        @Test
        @DisplayName("Should get all available packages")
        void shouldGetAllPackages() {
            // ARRANGE
            List<SubscriptionPackage> packages = List.of(testPackage);
            when(packageRepository.findAll()).thenReturn(packages);

            // ACT
            List<SubscriptionPackage> result = subscriptionService.getAllPackages();

            // ASSERT
            assertNotNull(result);
            assertFalse(result.isEmpty());
            verify(packageRepository, times(1)).findAll();
        }

        @Test
        @DisplayName("Should get package by ID")
        void shouldGetPackageById() {
            // ARRANGE
            when(packageRepository.findById(1L)).thenReturn(Optional.of(testPackage));

            // ACT
            Optional<SubscriptionPackage> result = subscriptionService.getPackageById(1L);

            // ASSERT
            assertTrue(result.isPresent());
        }

        @Test
        @DisplayName("Should get active billing cycles")
        void shouldGetActiveBillingCycles() {
            // ARRANGE
            List<BillingCycle> cycles = List.of(testBillingCycle);
            when(billingCycleRepository.findByIsActiveTrue()).thenReturn(cycles);

            // ACT
            List<BillingCycle> result = subscriptionService.getActiveBillingCycles();

            // ASSERT
            assertNotNull(result);
            assertFalse(result.isEmpty());
        }
    }
}
