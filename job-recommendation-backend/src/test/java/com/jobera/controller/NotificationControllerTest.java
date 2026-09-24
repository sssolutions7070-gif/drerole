package com.jobera.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.jobera.entity.Notification;
import com.jobera.service.NotificationFeedService;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock NotificationFeedService notificationFeedService;

    NotificationController controller;

    @BeforeEach
    void setUp() {
        controller = new NotificationController(notificationFeedService);
    }

    @Test
    void listReturnsTheUsersFeed() {
        Notification n = new Notification(1L, "NEW_MESSAGE", "New message", "Hi", null);
        when(notificationFeedService.listForUser(1L)).thenReturn(List.of(n));

        ResponseEntity<List<Notification>> response = controller.list(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
    }

    @Test
    void unreadCountReturnsTheCount() {
        when(notificationFeedService.unreadCount(1L)).thenReturn(4L);

        ResponseEntity<java.util.Map<String, Long>> response = controller.unreadCount(1L);

        assertEquals(4L, response.getBody().get("count"));
    }

    @Test
    void markReadReturnsOkWhenFound() {
        Notification n = new Notification(1L, "NEW_MESSAGE", "New message", "Hi", null);
        n.setRead(true);
        when(notificationFeedService.markRead(500L, 1L)).thenReturn(n);

        ResponseEntity<?> response = controller.markRead(1L, 500L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void markReadReturnsNotFoundWhenMissing() {
        when(notificationFeedService.markRead(999L, 1L))
            .thenThrow(new NotificationFeedService.NotificationNotFoundException(999L));

        ResponseEntity<?> response = controller.markRead(1L, 999L);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void markAllReadDelegatesAndReturnsOk() {
        ResponseEntity<Void> response = controller.markAllRead(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(notificationFeedService).markAllRead(1L);
    }
}
