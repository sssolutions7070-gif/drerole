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
import com.jobera.entity.Webhook;
import com.jobera.service.WebhookService;

import jakarta.servlet.http.HttpServletRequest;

@ExtendWith(MockitoExtension.class)
class WebhookControllerTest {

    @Mock WebhookService webhookService;

    WebhookController controller;

    @BeforeEach
    void setUp() {
        controller = new WebhookController(webhookService);
    }

    private HttpServletRequest requestFor(long userId) {
        User user = new User();
        user.setId(userId);
        HttpServletRequest request = mock(HttpServletRequest.class);
        lenient().when(request.getAttribute(AuthInterceptor.AUTHENTICATED_USER)).thenReturn(user);
        return request;
    }

    @Test
    void listReturnsWebhooks() {
        when(webhookService.list(100L)).thenReturn(List.of(Map.of("id", 1)));

        ResponseEntity<?> response = controller.list(100L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void registerReturnsTheNewWebhook() {
        when(webhookService.register(100L, 1L, "https://x.com", Webhook.EVENT_APPLICATION_SUBMITTED))
            .thenReturn(Map.of("id", 1));

        ResponseEntity<?> response = controller.register(100L,
            Map.of("url", "https://x.com", "eventType", Webhook.EVENT_APPLICATION_SUBMITTED), requestFor(1L));

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void registerReturns400ForAnInvalidEventType() {
        when(webhookService.register(100L, 1L, "https://x.com", "BOGUS"))
            .thenThrow(new WebhookService.InvalidEventTypeException("BOGUS"));

        ResponseEntity<?> response = controller.register(100L, Map.of("url", "https://x.com", "eventType", "BOGUS"), requestFor(1L));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void deleteReturnsOk() {
        ResponseEntity<?> response = controller.delete(100L, 5L, requestFor(1L));

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void deleteReturns403ForNonOwner() {
        org.mockito.Mockito.doThrow(new WebhookService.NotTheAccountOwnerException())
            .when(webhookService).delete(100L, 5L, 2L);

        ResponseEntity<?> response = controller.delete(100L, 5L, requestFor(2L));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void testWebhookReturnsSuccessStatus() {
        when(webhookService.testDispatch(100L, 5L, 1L))
            .thenReturn(Map.of("status", "SUCCESS", "message", "Ping delivered"));

        ResponseEntity<?> response = controller.testWebhook(100L, 5L, requestFor(1L));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("SUCCESS", ((Map<?, ?>) response.getBody()).get("status"));
    }
}
