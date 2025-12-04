//package com.secufusion.iam.controller;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.secufusion.iam.dto.CreateTenantRequest;
//import com.secufusion.iam.dto.TenantResponse;
//import com.secufusion.iam.entity.TenantType;
//import com.secufusion.iam.service.TenantService;
//import com.secufusion.iam.service.InitializerExecutor;   // <-- mocked, prevents startup error
//
//import jakarta.servlet.http.HttpServletRequest;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//
//import org.mockito.ArgumentMatchers;
//import org.mockito.Mockito;
//
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
//import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
//
//import org.springframework.http.MediaType;
//import org.springframework.test.context.bean.override.mockito.MockitoBean;
//import org.springframework.test.web.servlet.MockMvc;
//
//import java.time.Instant;
//import java.util.List;
//import java.util.Map;
//
//import static org.hamcrest.Matchers.*;
//import static org.mockito.ArgumentMatchers.*;
//import static org.mockito.Mockito.*;
//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
//
//@WebMvcTest(controllers = TenantController.class)
//@AutoConfigureMockMvc(addFilters = false)
//public class TenantControllerTest {
//
//    @Autowired
//    private MockMvc mockMvc;
//
//    @MockitoBean
//    private TenantService tenantService;
//
//    // IMPORTANT FIX: prevents Spring from loading your App class
//    @MockitoBean
//    private InitializerExecutor initializerExecutor;
//
//    @Autowired
//    private ObjectMapper objectMapper;
//
//    private TenantResponse resp;
//    private CreateTenantRequest req;
//
//    @BeforeEach
//    void setup() {
//        req = new CreateTenantRequest();
//        req.setTenantName("tenant1");
//
//        resp = new TenantResponse();
//        resp.setTenantID("T1");
//        resp.setTenantName("tenant1");
//    }
//
//    @Test
//    void testCreateTenant() throws Exception {
//        when(tenantService.createTenant(any(), any()))
//                .thenReturn(resp);
//
//        mockMvc.perform(post("/tenants")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(req)))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.tenantID").value("T1"));
//    }
//
//    @Test
//    void testGetTenant() throws Exception {
//        when(tenantService.getTenantIfParent(any(), eq("T1")))
//                .thenReturn(resp);
//
//        mockMvc.perform(get("/tenants/id?id=T1"))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.tenantName").value("tenant1"));
//    }
//
//    @Test
//    void testGetAll() throws Exception {
//        when(tenantService.getTenantHierarchy(any()))
//                .thenReturn(List.of(resp));
//
//        mockMvc.perform(get("/tenants"))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$", hasSize(1)));
//    }
//
//    @Test
//    void testUpdateTenant() throws Exception {
//        when(tenantService.updateTenant(any(), eq("T1"), any()))
//                .thenReturn(resp);
//
//        mockMvc.perform(put("/tenants/T1")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(req)))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.tenantID").value("T1"));
//    }
//
//    @Test
//    void testDeleteTenant() throws Exception {
//        Mockito.doNothing().when(tenantService).deleteTenant("T1");
//
//        mockMvc.perform(delete("/tenants/T1"))
//                .andExpect(status().isNoContent());
//    }
//
//    @Test
//    void testGetTenantTypes() throws Exception {
//        TenantType type = new TenantType();
//        type.setTenantTypeName("enterprise");
//
//        when(tenantService.getTenantTypesByTenantType(any()))
//                .thenReturn(List.of(type));
//
//        mockMvc.perform(get("/tenants/types"))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$[0].tenantTypeName").value("enterprise"));
//    }
//
//    @Test
//    void testBillingTypes() throws Exception {
//        when(tenantService.getTenantBillingTypes())
//                .thenReturn(List.of(Map.of("billingType", "Monthly")));
//
//        mockMvc.perform(get("/tenants/billing"))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$[0].billingType").value("Monthly"));
//    }
//
//    @Test
//    void testCheckTenantName() throws Exception {
//        when(tenantService.checkTenantNameAvailability("tenant1"))
//                .thenReturn("ok");
//
//        mockMvc.perform(get("/tenants/check?tenantName=tenant1"))
//                .andExpect(status().isOk())
//                .andExpect(content().string("ok"));
//    }
//
//    @Test
//    void testCheckDomain() throws Exception {
//        when(tenantService.checkExistsByDomain("a.com"))
//                .thenReturn("ok");
//
//        mockMvc.perform(get("/tenants/check?domainName=a.com"))
//                .andExpect(status().isOk())
//                .andExpect(content().string("ok"));
//    }
//
//    @Test
//    void testCheckPhone() throws Exception {
//        when(tenantService.checkPhoneNumber("999"))
//                .thenReturn("ok");
//
//        mockMvc.perform(get("/tenants/check?phoneNumber=999"))
//                .andExpect(status().isOk())
//                .andExpect(content().string("ok"));
//    }
//
//    @Test
//    void testCheckEmail() throws Exception {
//        when(tenantService.checkEmail("a@b.com"))
//                .thenReturn("ok");
//
//        mockMvc.perform(get("/tenants/check?tenantEmail=a@b.com"))
//                .andExpect(status().isOk())
//                .andExpect(content().string("ok"));
//    }
//
//    @Test
//    void testCheckMissingParams() throws Exception {
//        mockMvc.perform(get("/tenants/check"))
//                .andExpect(status().isBadRequest());
//    }
//
//    @Test
//    void testCreateTenantSuccess() throws Exception {
//
//        CreateTenantRequest req = new CreateTenantRequest();
//        req.setTenantName("SecuFusion");
//        req.setRegion("Asia");
//        req.setEmail("admin@secufusion.com");
//
//        TenantResponse resp = new TenantResponse();
//        resp.setTenantID("TEN-123");
//        resp.setTenantName("SecuFusion");
//        resp.setRealmName("secufusion");
//        resp.setDomain("secufusion.com");
//        resp.setRegion("Asia");
//        resp.setPhoneNo("9999999999");
//        resp.setTenantType("SAAS");
//        resp.setIndustry("IT");
//        resp.setStatus("ACTIVE");
//        resp.setCreatedAt(Instant.now());
//        resp.setLoginUrl("https://login.secufusion.com");
//
//        when(tenantService.createTenant(ArgumentMatchers.any(HttpServletRequest.class), ArgumentMatchers.any(CreateTenantRequest.class)))
//                .thenReturn(resp);
//
//        mockMvc.perform(post("/tenants")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(req)))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.tenantID").value("TEN-123"))
//                .andExpect(jsonPath("$.tenantName").value("SecuFusion"))
//                .andExpect(jsonPath("$.realmName").value("secufusion"))
//                .andExpect(jsonPath("$.domain").value("secufusion.com"))
//                .andExpect(jsonPath("$.region").value("Asia"))
//                .andExpect(jsonPath("$.status").value("ACTIVE"))
//                .andExpect(jsonPath("$.loginUrl").value("https://login.secufusion.com"));
//    }
//
//    @Test
//    void testCreateTenantServiceException() throws Exception {
//
//        CreateTenantRequest req = new CreateTenantRequest();
//        req.setTenantName("BugRealm");
//        req.setEmail("bug@test.com");
//
//        when(tenantService.createTenant(ArgumentMatchers.any(HttpServletRequest.class), ArgumentMatchers.any(CreateTenantRequest.class)))
//                .thenThrow(new RuntimeException("Error creating tenant"));
//
//        mockMvc.perform(post("/tenants")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(req)))
//                .andExpect(status().isInternalServerError());
//    }
//
//    @Test
//    void testCreateTenantServiceCall() throws Exception {
//
//        CreateTenantRequest req = new CreateTenantRequest();
//        req.setTenantName("Secu");
//        req.setEmail("admin@secu.com");
//
//        TenantResponse resp = new TenantResponse();
//        resp.setTenantID("ID-1");
//        resp.setTenantName("Secu");
//
//        when(tenantService.createTenant(any(), any())).thenReturn(resp);
//
//        mockMvc.perform(post("/tenants")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(req)))
//                .andExpect(status().isOk());
//
//        verify(tenantService, times(1))
//                .createTenant(ArgumentMatchers.any(HttpServletRequest.class), ArgumentMatchers.any(CreateTenantRequest.class));
//    }
//
//}
