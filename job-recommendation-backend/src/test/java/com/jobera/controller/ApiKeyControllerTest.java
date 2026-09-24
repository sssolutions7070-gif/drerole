package com.jobera.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.jobera.config.AuthInterceptor;
import com.jobera.entity.User;
import com.jobera.service.ApiKeyService;

import jakarta.servlet.http.HttpServletRequest;

@ExtendWith(MockitoExtension.class)
class ApiKeyControllerTest {

    @Mock ApiKeyService apiKeyService;

    ApiKeyController controller;

    @BeforeEach
    void setUp() {
        controller = new ApiKeyController(apiKeyService);
    }

    private HttpServletRequest requestFor(long userId) {
        User user = new User();
        user.setId(userId);
        HttpServletRequest request = mock(HttpServletRequest.class);
        lenient().when(request.getAttribute(AuthInterceptor.AUTHENTICATED_USER)).thenReturn(user);
        return request;
    }

    @Test
    void listReturnsKeys() {
        when(apiKeyService.listKeys(100L)).thenReturn(List.of(Map.of("id", 1)));

        ResponseEntity<?> response = controller.list(100L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void generateReturnsTheNewKey() {
        when(apiKeyService.generateKey(100L, 1L, "label")).thenReturn(Map.of("key", "dr_live_x"));

        ResponseEntity<?> response = controller.generate(100L, Map.of("label", "label"), requestFor(1L));

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void generateReturns403ForNonOwner() {
        when(apiKeyService.generateKey(100L, 2L, "label"))
            .thenThrow(new ApiKeyService.NotTheAccountOwnerException());

        ResponseEntity<?> response = controller.generate(100L, Map.of("label", "label"), requestFor(2L));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void revokeReturnsOk() {
        ResponseEntity<?> response = controller.revoke(100L, 5L, requestFor(1L));

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void revokeReturns404WhenKeyMissing() {
        org.mockito.Mockito.doThrow(new ApiKeyService.ApiKeyNotFoundException(5L))
            .when(apiKeyService).revokeKey(100L, 5L, 1L);

        ResponseEntity<?> response = controller.revoke(100L, 5L, requestFor(1L));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}
