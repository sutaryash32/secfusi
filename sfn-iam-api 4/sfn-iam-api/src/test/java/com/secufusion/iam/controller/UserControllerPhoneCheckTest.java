package com.secufusion.iam.controller;

import com.secufusion.iam.dto.UserPhoneCheckDto;
import com.secufusion.iam.entity.Tenant;
import com.secufusion.iam.service.UserService;
import com.secufusion.iam.util.JwtUtl;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerPhoneCheckTest {

    @Mock
    private UserService userService;

    @Mock
    private JwtUtl jwtUtl;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private UserController userController;

    @Test
    void uniqueValidations_phoneNumber_returnsMinimalUsersAndCount() {
        String phone = "1234567890";

        Tenant tenant = new Tenant();
        tenant.setTenantID("tenant-1");

        UserPhoneCheckDto u1 = new UserPhoneCheckDto(
                "id-1", "u1", "u1@example.com", phone,
                "First1", "Last1", "ACTIVE", LocalDateTime.now()
        );
        UserPhoneCheckDto u2 = new UserPhoneCheckDto(
                "id-2", "u2", "u2@example.com", phone,
                "First2", "Last2", "ACTIVE", LocalDateTime.now()
        );

        when(jwtUtl.getTenantFromRequest(request)).thenReturn(tenant);
        when(userService.getUsersByPhoneNumber(phone, "tenant-1")).thenReturn(List.of(u1, u2));

        ResponseEntity<?> response = userController.uniqueValidations(request, null, phone, null);

        assertEquals(200, response.getStatusCode().value());
        Object body = response.getBody();
        assertInstanceOf(Map.class, body);

        Map<?, ?> map = (Map<?, ?>) body;
        assertEquals(2, map.get("count"));
        assertEquals("2 user(s) found with phone number 1234567890", map.get("message"));
        assertEquals(2, ((List<?>) map.get("users")).size());
    }

    @Test
    void uniqueValidations_phoneNumber_notFound_returnsEmptyListAndMessage() {
        String phone = "0000000000";

        Tenant tenant = new Tenant();
        tenant.setTenantID("tenant-1");

        when(jwtUtl.getTenantFromRequest(request)).thenReturn(tenant);
        when(userService.getUsersByPhoneNumber(phone, "tenant-1")).thenReturn(List.of());

        ResponseEntity<?> response = userController.uniqueValidations(request, null, phone, null);

        assertEquals(200, response.getStatusCode().value());
        Object body = response.getBody();
        assertInstanceOf(Map.class, body);

        Map<?, ?> map = (Map<?, ?>) body;
        assertEquals(0, map.get("count"));
        assertEquals("No users found for this phone number", map.get("message"));
        assertEquals(0, ((List<?>) map.get("users")).size());
    }
}
