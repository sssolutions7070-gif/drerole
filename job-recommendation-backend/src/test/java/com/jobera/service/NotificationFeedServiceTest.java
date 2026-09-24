package com.jobera.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import com.jobera.entity.Notification;
import com.jobera.repository.NotificationRepository;

@ExtendWith(MockitoExtension.class)
class NotificationFeedServiceTest {

    @Mock NotificationRepository notificationRepository;

    NotificationFeedService service;

    @BeforeEach
    void setUp() {
        service = new NotificationFeedService(notificationRepository);
    }

    @Test
    void recordSavesANewNotificationForTheUser() {
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Notification saved = service.record(1L, "OFFER_SENT", "You've received an offer", "Details", "/dashboard");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertEquals(1L, captor.getValue().getUserId());
        assertEquals("OFFER_SENT", captor.getValue().getType());
        assertEquals("/dashboard", captor.getValue().getLink());
        assertTrue(!saved.isRead());
    }

    @Test
    void recordDoesNothingWhenThereIsNoUserId() {
        Notification result = service.record(null, "OFFER_SENT", "title", "message", null);

        assertNull(result);
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void listForUserReturnsRepositoryResults() {
        Notification n = new Notification(1L, "NEW_MESSAGE", "New message", "Hi", null);
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(org.mockito.ArgumentMatchers.eq(1L), any(Pageable.class)))
            .thenReturn(List.of(n));

        List<Notification> result = service.listForUser(1L);

        assertEquals(1, result.size());
    }

    @Test
    void unreadCountDelegatesToRepository() {
        when(notificationRepository.countByUserIdAndReadFalse(1L)).thenReturn(3L);

        assertEquals(3L, service.unreadCount(1L));
    }

    @Test
    void markReadSetsReadWhenTheCallerOwnsIt() {
        Notification n = new Notification(1L, "NEW_MESSAGE", "New message", "Hi", null);
        n.setId(500L);
        when(notificationRepository.findById(500L)).thenReturn(Optional.of(n));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Notification result = service.markRead(500L, 1L);

        assertTrue(result.isRead());
    }

    @Test
    void markReadThrowsWhenTheCallerDoesNotOwnIt() {
        Notification n = new Notification(1L, "NEW_MESSAGE", "New message", "Hi", null);
        n.setId(500L);
        when(notificationRepository.findById(500L)).thenReturn(Optional.of(n));

        assertThrows(NotificationFeedService.NotificationNotFoundException.class,
            () -> service.markRead(500L, 2L));
    }

    @Test
    void markReadThrowsWhenNotFound() {
        when(notificationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(NotificationFeedService.NotificationNotFoundException.class,
            () -> service.markRead(999L, 1L));
    }

    @Test
    void markAllReadDelegatesToRepository() {
        service.markAllRead(1L);

        verify(notificationRepository).markAllAsRead(1L);
    }
}
