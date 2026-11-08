package com.jobera.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import com.jobera.config.AuthInterceptor;
import com.jobera.entity.User;
import com.jobera.service.MessageService;

import jakarta.servlet.http.HttpServletRequest;

/** Only the new bulk-message endpoint - MessageController's existing per-thread endpoints have
 *  no prior dedicated test file to extend. */
@ExtendWith(MockitoExtension.class)
class MessageControllerBulkMessageTest {

    @Mock MessageService messageService;

    MessageController controller;
    HttpServletRequest request;
    User recruiterUser;

    @BeforeEach
    void setUp() {
        controller = new MessageController(messageService);
        recruiterUser = new User();
        recruiterUser.setId(2L);
        request = mock(HttpServletRequest.class);
        lenient().when(request.getAttribute(AuthInterceptor.AUTHENTICATED_USER)).thenReturn(recruiterUser);
    }

    @Test
    void sendsTheMessageToEveryApplicationIdInTheBody() {
        when(messageService.sendBulkMessage(List.of(1000L, 1001L), 2L, "Reminder: assessment due Friday"))
            .thenReturn(Map.of("sentCount", 2, "failedCount", 0, "failures", List.of()));

        Map<String, Object> payload = Map.of(
            "applicationIds", List.of(1000, 1001),
            "body", "Reminder: assessment due Friday"
        );

        ResponseEntity<?> response = controller.sendBulkMessage(5L, payload, request);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(2, ((Map<?, ?>) response.getBody()).get("sentCount"));
    }

    @Test
    void rejectsAnEmptyMessageBodyWithoutCallingTheService() {
        Map<String, Object> payload = Map.of("applicationIds", List.of(1000), "body", "   ");

        ResponseEntity<?> response = controller.sendBulkMessage(5L, payload, request);

        assertEquals(400, response.getStatusCode().value());
        verify(messageService, never()).sendBulkMessage(any(), anyLong(), anyString());
    }

    @Test
    void rejectsAnEmptyApplicationIdListWithoutCallingTheService() {
        Map<String, Object> payload = Map.of("applicationIds", List.of(), "body", "Hello");

        ResponseEntity<?> response = controller.sendBulkMessage(5L, payload, request);

        assertEquals(400, response.getStatusCode().value());
    }
}
