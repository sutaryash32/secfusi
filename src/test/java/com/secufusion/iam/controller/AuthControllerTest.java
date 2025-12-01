//package com.secufusion.iam.controller;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//// <-- mocked, prevents startup error
//
//import com.secufusion.iam.dto.AuthDetailsDto;
//import com.secufusion.iam.service.AuthConfigService;
//import com.secufusion.iam.service.InitializerExecutor;
//import org.junit.jupiter.api.Test;
//
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
//import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
//
//import org.springframework.http.MediaType;
//import org.springframework.test.context.bean.override.mockito.MockitoBean;
//import org.springframework.test.web.servlet.MockMvc;
//
//import static org.mockito.Mockito.*;
//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
//
//@WebMvcTest(controllers = AuthController.class)
//@AutoConfigureMockMvc(addFilters = false)
//public class AuthControllerTest {
//
//    @Autowired
//    private MockMvc mockMvc;
//
//    @MockitoBean
//    private AuthConfigService authConfigService;
//
//    @MockitoBean
//    private InitializerExecutor initializerExecutor;
//
//
//    @Autowired
//    private ObjectMapper objectMapper;
//
//    // -------------------------------------------------------------------------
//    // SIMPLE /tenant-config
//    // -------------------------------------------------------------------------
//
//    @Test
//    void testGetTenantConfigSimple() throws Exception {
//        AuthDetailsDto dto = new AuthDetailsDto();
//        dto.setClientId("abc123");
//
//        when(authConfigService.getTenantConfig("example.com")).thenReturn(dto);
//
//        mockMvc.perform(get("/tenant-config")
//                        .param("host", "example.com"))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.clientId").value("abc123"));
//    }
//
//    // -------------------------------------------------------------------------
//    // /tenant-config/v1 — SUCCESS SCENARIO
//    // -------------------------------------------------------------------------
//
//    @Test
//    void testGetTenantConfigV1_Success() throws Exception {
//
//        AuthDetailsDto dto = new AuthDetailsDto();
//        dto.setClientId("client-ok");
//
//        when(authConfigService.getTenantConfig("example.com"))
//                .thenReturn(dto);
//
//        mockMvc.perform(
//                        get("/tenant-config/v1")
//                                .param("host", "example.com")
//                                .header("Referer", "https://example.com/login")
//                                .header("Host", "example.com")
//                                .secure(true)
//                                .contentType(MediaType.APPLICATION_JSON)
//                )
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.clientId").value("client-ok"));
//    }
//
//    // -------------------------------------------------------------------------
//    // NO REFERER → FORBIDDEN
//    // -------------------------------------------------------------------------
//
//    @Test
//    void testGetTenantConfigV1_NoReferer() throws Exception {
//
//        mockMvc.perform(get("/tenant-config/v1")
//                        .param("host", "example.com"))
//                .andExpect(status().isForbidden());
//    }
//
//    // -------------------------------------------------------------------------
//    // REFERER DOMAIN != HOST PARAM → FORBIDDEN
//    // -------------------------------------------------------------------------
//
//    @Test
//    void testGetTenantConfigV1_DomainMismatch() throws Exception {
//
//        mockMvc.perform(
//                        get("/tenant-config/v1")
//                                .param("host", "example.com")
//                                .header("Referer", "https://evil.com/x")
//                )
//                .andExpect(status().isForbidden());
//    }
//
//    // -------------------------------------------------------------------------
//    // REFERER OK but REQUEST URL domain mismatch → FORBIDDEN
//    // -------------------------------------------------------------------------
//
//    @Test
//    void testGetTenantConfigV1_RequestDomainMismatch() throws Exception {
//
//        mockMvc.perform(
//                        get("/tenant-config/v1")
//                                .param("host", "example.com")
//                                .header("Referer", "https://example.com/login")
//                                .header("Host", "different.com") // triggers mismatch
//                )
//                .andExpect(status().isForbidden());
//    }
//
//    // -------------------------------------------------------------------------
//    // EXCEPTION inside controller → FORBIDDEN
//    // -------------------------------------------------------------------------
//
//    @Test
//    void testGetTenantConfigV1_ExceptionThrown() throws Exception {
//
//        when(authConfigService.getTenantConfig("example.com"))
//                .thenThrow(new RuntimeException("boom"));
//
//        mockMvc.perform(
//                        get("/tenant-config/v1")
//                                .param("host", "example.com")
//                                .header("Referer", "https://example.com/login")
//                                .header("Host", "example.com")
//                )
//                .andExpect(status().isForbidden());
//    }
//}
