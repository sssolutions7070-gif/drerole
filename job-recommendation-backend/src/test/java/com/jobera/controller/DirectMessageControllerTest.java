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

import com.jobera.entity.DirectMessage;
import com.jobera.entity.User;
import com.jobera.service.DirectMessageService;

import jakarta.servlet.http.HttpServletRequest;

@ExtendWith(MockitoExtension.class)
class DirectMessageControllerTest {

    @Mock DirectMessageService directMessageService;

    DirectMessageController controller;

    @BeforeEach
    void setUp() {
        controller = new DirectMessageController(directMessageService);
    }

    private HttpServletRequest requestFor(long userId) {
        User user = new User();
        user.setId(userId);
        HttpServletRequest request = mock(HttpServletRequest.class);
        lenient().when(request.getAttribute(com.jobera.config.AuthInterceptor.AUTHENTICATED_USER)).thenReturn(user);
        return request;
    }

    @Test
    void getThreadReturnsMessages() {
        when(directMessageService.getThread(100L, 50L)).thenReturn(List.of(new DirectMessage(100L, 50L, 1L, 50L, "hi")));

        ResponseEntity<?> response = controller.getThread(100L, 50L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void sendMessageRejectsBlankBody() {
        ResponseEntity<?> response = controller.sendMessage(100L, 50L, Map.of("body", "  "), requestFor(1L));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void sendMessageReturnsTheSavedMessage() {
        DirectMessage saved = new DirectMessage(100L, 50L, 1L, 50L, "hello");
        when(directMessageService.sendMessage(100L, 50L, 1L, "hello")).thenReturn(saved);

        ResponseEntity<?> response = controller.sendMessage(100L, 50L, Map.of("body", "hello"), requestFor(1L));

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void listForRecruiterReturnsThreads() {
        when(directMessageService.listThreadsForRecruiter(100L)).thenReturn(List.of(Map.of("candidateUserId", 50L)));

        ResponseEntity<List<Map<String, Object>>> response = controller.listForRecruiter(100L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
    }

    @Test
    void listForCandidateReturnsThreads() {
        when(directMessageService.listThreadsForCandidate(50L)).thenReturn(List.of(Map.of("recruiterId", 100L)));

        ResponseEntity<List<Map<String, Object>>> response = controller.listForCandidate(50L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
    }
}
