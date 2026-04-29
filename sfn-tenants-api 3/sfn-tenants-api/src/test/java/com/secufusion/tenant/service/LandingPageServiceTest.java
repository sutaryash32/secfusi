package com.secufusion.tenant.service;

import com.secufusion.tenant.entity.LandingPage;
import com.secufusion.tenant.entity.Shortcut;
import com.secufusion.tenant.repository.LandingPageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * Test suite for LandingPageService
 * 
 * Coverage:
 * - CRUD operations: create, read (all), read (by ID), update, delete
 * - Shortcut management: add, remove, clear
 * - Repository interactions and error handling
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LandingPageService Tests")
class LandingPageServiceTest {

    @Mock
    private LandingPageRepository landingPageRepository;

    @InjectMocks
    private LandingPageService landingPageService;

    private LandingPage testLandingPage;
    private List<Shortcut> testShortcuts;

    @BeforeEach
    void setUp() {
        // Common test data
        testLandingPage = new LandingPage();
        testLandingPage.setPkLandingPageId("lp-123");
        testLandingPage.setName("Dashboard Landing Page");
        testLandingPage.setDescription("Main landing page for dashboard");
        testLandingPage.setFkTenantId("tenant-001");
        testLandingPage.setShortcuts(new ArrayList<>());

        testShortcuts = new ArrayList<>();
        Shortcut shortcut1 = new Shortcut();
        shortcut1.setTitle("Google");
        shortcut1.setUrl("https://google.com");
        shortcut1.setDisplayOrder(0);
        testShortcuts.add(shortcut1);

        Shortcut shortcut2 = new Shortcut();
        shortcut2.setTitle("GitHub");
        shortcut2.setUrl("https://github.com");
        shortcut2.setDisplayOrder(1);
        testShortcuts.add(shortcut2);
    }

    @Nested
    @DisplayName("Create Landing Page")
    class CreateLandingPageTests {

        @Test
        @DisplayName("Should create landing page with shortcuts successfully")
        void shouldCreateLandingPageWithShortcuts() {
            // ARRANGE
            LandingPage input = new LandingPage();
            input.setName("New Landing Page");
            input.setDescription("Test page");
            input.setFkTenantId("tenant-001");
            input.setShortcuts(testShortcuts);

            LandingPage saved = new LandingPage();
            saved.setPkLandingPageId("lp-999");
            saved.setName("New Landing Page");
            saved.setDescription("Test page");
            saved.setFkTenantId("tenant-001");
            saved.setShortcuts(testShortcuts);

            when(landingPageRepository.save(any(LandingPage.class))).thenReturn(saved);

            // ACT
            LandingPage result = landingPageService.createLandingPage(input);

            // ASSERT
            assertNotNull(result);
            assertEquals("lp-999", result.getPkLandingPageId());
            assertEquals("New Landing Page", result.getName());
            assertEquals("Test page", result.getDescription());
            assertEquals("tenant-001", result.getFkTenantId());
            assertNotNull(result.getShortcuts());
            verify(landingPageRepository, times(1)).save(any(LandingPage.class));
        }

        @Test
        @DisplayName("Should create landing page without shortcuts")
        void shouldCreateLandingPageWithoutShortcuts() {
            // ARRANGE
            LandingPage input = new LandingPage();
            input.setName("Minimal Landing Page");
            input.setFkTenantId("tenant-002");
            input.setShortcuts(null);

            LandingPage saved = new LandingPage();
            saved.setPkLandingPageId("lp-888");
            saved.setName("Minimal Landing Page");
            saved.setFkTenantId("tenant-002");
            saved.setShortcuts(new ArrayList<>());

            when(landingPageRepository.save(any(LandingPage.class))).thenReturn(saved);

            // ACT
            LandingPage result = landingPageService.createLandingPage(input);

            // ASSERT
            assertNotNull(result);
            assertEquals("lp-888", result.getPkLandingPageId());
            assertEquals("Minimal Landing Page", result.getName());
            assertNotNull(result.getShortcuts());
            verify(landingPageRepository, times(1)).save(any(LandingPage.class));
        }

        @Test
        @DisplayName("Should create landing page with empty shortcuts list")
        void shouldCreateLandingPageWithEmptyShortcuts() {
            // ARRANGE
            LandingPage input = new LandingPage();
            input.setName("Empty Shortcuts Page");
            input.setFkTenantId("tenant-003");
            input.setShortcuts(new ArrayList<>());

            LandingPage saved = new LandingPage();
            saved.setPkLandingPageId("lp-777");
            saved.setName("Empty Shortcuts Page");
            saved.setFkTenantId("tenant-003");
            saved.setShortcuts(new ArrayList<>());

            when(landingPageRepository.save(any(LandingPage.class))).thenReturn(saved);

            // ACT
            LandingPage result = landingPageService.createLandingPage(input);

            // ASSERT
            assertNotNull(result);
            assertEquals(0, result.getShortcuts().size());
            verify(landingPageRepository, times(1)).save(any(LandingPage.class));
        }
    }

    @Nested
    @DisplayName("Read All Landing Pages")
    class ReadAllLandingPagesTests {

        @Test
        @DisplayName("Should return all landing pages with shortcuts")
        void shouldReturnAllLandingPages() {
            // ARRANGE
            LandingPage lp1 = new LandingPage();
            lp1.setPkLandingPageId("lp-1");
            lp1.setName("Page 1");
            lp1.setShortcuts(testShortcuts);

            LandingPage lp2 = new LandingPage();
            lp2.setPkLandingPageId("lp-2");
            lp2.setName("Page 2");
            lp2.setShortcuts(new ArrayList<>());

            List<LandingPage> pages = List.of(lp1, lp2);
            when(landingPageRepository.findAllWithShortcuts()).thenReturn(pages);

            // ACT
            List<LandingPage> result = landingPageService.getAllLandingPages();

            // ASSERT
            assertNotNull(result);
            assertEquals(2, result.size());
            assertEquals("lp-1", result.get(0).getPkLandingPageId());
            assertEquals("lp-2", result.get(1).getPkLandingPageId());
            verify(landingPageRepository, times(1)).findAllWithShortcuts();
        }

        @Test
        @DisplayName("Should return empty list when no landing pages exist")
        void shouldReturnEmptyList() {
            // ARRANGE
            when(landingPageRepository.findAllWithShortcuts()).thenReturn(new ArrayList<>());

            // ACT
            List<LandingPage> result = landingPageService.getAllLandingPages();

            // ASSERT
            assertNotNull(result);
            assertEquals(0, result.size());
            verify(landingPageRepository, times(1)).findAllWithShortcuts();
        }
    }

    @Nested
    @DisplayName("Read Landing Page By ID")
    class ReadLandingPageByIdTests {

        @Test
        @DisplayName("Should return landing page by ID")
        void shouldReturnLandingPageById() {
            // ARRANGE
            LandingPage page = new LandingPage();
            page.setPkLandingPageId("lp-123");
            page.setName("Test Page");
            page.setShortcuts(testShortcuts);

            when(landingPageRepository.findByIdWithShortcuts("lp-123"))
                    .thenReturn(Optional.of(page));

            // ACT
            LandingPage result = landingPageService.getLandingPageById("lp-123");

            // ASSERT
            assertNotNull(result);
            assertEquals("lp-123", result.getPkLandingPageId());
            assertEquals("Test Page", result.getName());
            assertEquals(2, result.getShortcuts().size());
            verify(landingPageRepository, times(1)).findByIdWithShortcuts("lp-123");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when landing page not found")
        void shouldThrowExceptionWhenNotFound() {
            // ARRANGE
            when(landingPageRepository.findByIdWithShortcuts("lp-999"))
                    .thenReturn(Optional.empty());

            // ACT & ASSERT
            assertThrows(IllegalArgumentException.class, () ->
                    landingPageService.getLandingPageById("lp-999"));
            verify(landingPageRepository, times(1)).findByIdWithShortcuts("lp-999");
        }
    }

    @Nested
    @DisplayName("Update Landing Page")
    class UpdateLandingPageTests {

        @Test
        @DisplayName("Should update landing page with new shortcuts")
        void shouldUpdateLandingPageWithNewShortcuts() {
            // ARRANGE
            LandingPage existing = new LandingPage();
            existing.setPkLandingPageId("lp-123");
            existing.setName("Old Name");
            existing.setDescription("Old Desc");
            existing.setShortcuts(new ArrayList<>(testShortcuts));

            LandingPage updated = new LandingPage();
            updated.setName("Updated Name");
            updated.setDescription("Updated Desc");
            Shortcut newShortcut = new Shortcut();
            newShortcut.setTitle("New Site");
            newShortcut.setUrl("https://newsite.com");
            newShortcut.setDisplayOrder(0);
            updated.setShortcuts(List.of(newShortcut));

            LandingPage saved = new LandingPage();
            saved.setPkLandingPageId("lp-123");
            saved.setName("Updated Name");
            saved.setDescription("Updated Desc");
            saved.setShortcuts(List.of(newShortcut));

            when(landingPageRepository.findByIdWithShortcuts("lp-123"))
                    .thenReturn(Optional.of(existing));
            when(landingPageRepository.save(any(LandingPage.class)))
                    .thenReturn(saved);

            // ACT
            LandingPage result = landingPageService.updateLandingPage("lp-123", updated);

            // ASSERT
            assertNotNull(result);
            assertEquals("Updated Name", result.getName());
            assertEquals("Updated Desc", result.getDescription());
            assertEquals(1, result.getShortcuts().size());
            verify(landingPageRepository, times(1)).findByIdWithShortcuts("lp-123");
            verify(landingPageRepository, times(1)).save(any(LandingPage.class));
        }

        @Test
        @DisplayName("Should clear existing shortcuts when updating")
        void shouldClearExistingShortcutsOnUpdate() {
            // ARRANGE
            LandingPage existing = new LandingPage();
            existing.setPkLandingPageId("lp-123");
            existing.setName("Old");
            existing.setShortcuts(new ArrayList<>(testShortcuts));

            LandingPage updated = new LandingPage();
            updated.setName("New");
            updated.setShortcuts(null);

            LandingPage saved = new LandingPage();
            saved.setPkLandingPageId("lp-123");
            saved.setName("New");
            saved.setShortcuts(new ArrayList<>());

            when(landingPageRepository.findByIdWithShortcuts("lp-123"))
                    .thenReturn(Optional.of(existing));
            when(landingPageRepository.save(any(LandingPage.class)))
                    .thenReturn(saved);

            // ACT
            LandingPage result = landingPageService.updateLandingPage("lp-123", updated);

            // ASSERT
            assertNotNull(result);
            assertEquals(0, result.getShortcuts().size());
            verify(landingPageRepository, times(1)).findByIdWithShortcuts("lp-123");
            verify(landingPageRepository, times(1)).save(any(LandingPage.class));
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when updating non-existent landing page")
        void shouldThrowExceptionWhenUpdateNonExistent() {
            // ARRANGE
            LandingPage updated = new LandingPage();
            updated.setName("Updated");

            when(landingPageRepository.findByIdWithShortcuts("lp-999"))
                    .thenReturn(Optional.empty());

            // ACT & ASSERT
            assertThrows(IllegalArgumentException.class, () ->
                    landingPageService.updateLandingPage("lp-999", updated));
            verify(landingPageRepository, times(1)).findByIdWithShortcuts("lp-999");
            verify(landingPageRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Delete Landing Page")
    class DeleteLandingPageTests {

        @Test
        @DisplayName("Should delete landing page successfully")
        void shouldDeleteLandingPageSuccessfully() {
            // ARRANGE
            LandingPage page = new LandingPage();
            page.setPkLandingPageId("lp-123");
            page.setName("To Delete");

            when(landingPageRepository.findByIdWithShortcuts("lp-123"))
                    .thenReturn(Optional.of(page));

            // ACT
            landingPageService.deleteLandingPage("lp-123");

            // ASSERT
            verify(landingPageRepository, times(1)).findByIdWithShortcuts("lp-123");
            verify(landingPageRepository, times(1)).delete(page);
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when deleting non-existent landing page")
        void shouldThrowExceptionWhenDeleteNonExistent() {
            // ARRANGE
            when(landingPageRepository.findByIdWithShortcuts("lp-999"))
                    .thenReturn(Optional.empty());

            // ACT & ASSERT
            assertThrows(IllegalArgumentException.class, () ->
                    landingPageService.deleteLandingPage("lp-999"));
            verify(landingPageRepository, times(1)).findByIdWithShortcuts("lp-999");
            verify(landingPageRepository, never()).delete(any());
        }
    }
}
