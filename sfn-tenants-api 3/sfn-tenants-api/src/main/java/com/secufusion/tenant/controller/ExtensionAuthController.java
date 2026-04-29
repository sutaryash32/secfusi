//package com.secufusion.tenant.controller;
//
//import com.secufusion.tenant.dto.ExtensionAuthRequest;
//import com.secufusion.tenant.dto.ExtensionAuthResponse;
//import com.secufusion.tenant.service.ExtensionApiKeyService;
//import com.secufusion.tenant.util.JwtUtl;
//import jakarta.servlet.http.HttpServletRequest;
//import lombok.RequiredArgsConstructor;
//import org.springframework.security.access.prepost.PreAuthorize;
//import org.springframework.web.bind.annotation.*;
//
//@RestController
//@RequestMapping("/extension")
//@RequiredArgsConstructor
//public class ExtensionAuthController {
//
//    private final ExtensionApiKeyService apiKeyService;
//    private final JwtUtl jwtUtl;
//
//    // ============================================
//    //  Generate API Key (Admin Only)
//    // ============================================
//
//    @PostMapping("/generate")
//    public String generateApiKey(HttpServletRequest request) {
//
//        String tenantId = jwtUtl.getTenantFromRequest(request).getTenantID();
//        String createdBy = jwtUtl.getUserFromRequest(request).getPkUserId();
//
//        return apiKeyService.generateApiKey(tenantId, createdBy);
//    }
//
//    // ============================================
//    //  Authenticate Extension (Public Endpoint)
//    // ============================================
//
//    @PostMapping("/auth")
//    public ExtensionAuthResponse authenticate(@RequestBody ExtensionAuthRequest request) {
//
//        return apiKeyService.authenticateExtension(request.getApiKey());
//    }
//}
