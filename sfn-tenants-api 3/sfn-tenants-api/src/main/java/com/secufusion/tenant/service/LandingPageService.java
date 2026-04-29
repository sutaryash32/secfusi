package com.secufusion.tenant.service;

import com.secufusion.tenant.entity.LandingPage;
import com.secufusion.tenant.entity.Shortcut;
import com.secufusion.tenant.repository.LandingPageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@Transactional
public class LandingPageService {

    private final LandingPageRepository landingPageRepository;

    @Autowired
    public LandingPageService(LandingPageRepository landingPageRepository) {
        this.landingPageRepository = landingPageRepository;
    }

    /* ==========================================================
       CREATE
       ========================================================== */
    public LandingPage createLandingPage(LandingPage landingPage) {
        log.debug("Creating LandingPage name={}", landingPage.getName());

        // Create new shortcuts and link to landing page
        if (landingPage.getShortcuts() != null && !landingPage.getShortcuts().isEmpty()) {
            List<Shortcut> inputShortcuts = landingPage.getShortcuts();
            landingPage.setShortcuts(new java.util.ArrayList<>());

            for (int i = 0; i < inputShortcuts.size(); i++) {
                Shortcut input = inputShortcuts.get(i);
                Shortcut shortcut = new Shortcut();
                shortcut.setTitle(input.getTitle());
                shortcut.setUrl(input.getUrl());
                shortcut.setDisplayOrder(input.getDisplayOrder() != null ? input.getDisplayOrder() : i);
                landingPage.addShortcut(shortcut);
            }
        }

        LandingPage saved = landingPageRepository.save(landingPage);
        log.info("LandingPage created successfully. id={}, name={}",
                saved.getPkLandingPageId(), saved.getName());
        return saved;
    }

    /* ==========================================================
       READ - Get All
       ========================================================== */
    @Transactional(readOnly = true)
    public List<LandingPage> getAllLandingPages() {
        log.debug("Fetching all LandingPages");
        return landingPageRepository.findAllWithShortcuts();
    }

    /* ==========================================================
       READ - Get By ID
       ========================================================== */
    @Transactional(readOnly = true)
    public LandingPage getLandingPageById(String id) {
        log.debug("Fetching LandingPage id={}", id);
        return landingPageRepository.findByIdWithShortcuts(id)
                .orElseThrow(() -> new IllegalArgumentException("LandingPage not found: " + id));
    }

    /* ==========================================================
       UPDATE
       ========================================================== */
    public LandingPage updateLandingPage(String id, LandingPage updatedLandingPage) {
        log.debug("Updating LandingPage id={}", id);

        LandingPage existing = getLandingPageById(id);

        // Update basic fields
        existing.setName(updatedLandingPage.getName());
        existing.setDescription(updatedLandingPage.getDescription());

        // Clear existing shortcuts and add new ones
        existing.clearShortcuts();

        if (updatedLandingPage.getShortcuts() != null) {
            for (int i = 0; i < updatedLandingPage.getShortcuts().size(); i++) {
                Shortcut newShortcut = updatedLandingPage.getShortcuts().get(i);
                Shortcut shortcut = new Shortcut();
                shortcut.setTitle(newShortcut.getTitle());
                shortcut.setUrl(newShortcut.getUrl());
                shortcut.setDisplayOrder(newShortcut.getDisplayOrder() != null ? newShortcut.getDisplayOrder() : i);
                existing.addShortcut(shortcut);
            }
        }

        LandingPage saved = landingPageRepository.save(existing);
        log.info("LandingPage updated successfully. id={}", saved.getPkLandingPageId());
        return saved;
    }

    /* ==========================================================
       DELETE
       ========================================================== */
    public void deleteLandingPage(String id) {
        log.debug("Deleting LandingPage id={}", id);

        LandingPage existing = getLandingPageById(id);
        landingPageRepository.delete(existing);

        log.info("LandingPage deleted successfully. id={}", id);
    }
}
