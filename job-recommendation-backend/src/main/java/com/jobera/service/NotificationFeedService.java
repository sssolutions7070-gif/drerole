package com.jobera.service;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jobera.entity.Notification;
import com.jobera.repository.NotificationRepository;

/** Persists the events {@link RealtimeNotifier} pushes live, so a user who wasn't online when an
 *  event fired still sees it in their notification bell afterwards. This is a separate concern
 *  from {@link NotificationService}, which composes outbound email copy. */
@Service
public class NotificationFeedService {

    private static final int MAX_FEED_SIZE = 50;

    private final NotificationRepository notificationRepository;

    public NotificationFeedService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public Notification record(Long userId, String type, String title, String message, String link) {
        if (userId == null) {
            return null;
        }
        return notificationRepository.save(new Notification(userId, type, title, message, link));
    }

    public List<Notification> listForUser(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, MAX_FEED_SIZE));
    }

    public long unreadCount(Long userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    public Notification markRead(Long notificationId, Long callerId) {
        Notification notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new NotificationNotFoundException(notificationId));
        if (!notification.getUserId().equals(callerId)) {
            throw new NotificationNotFoundException(notificationId);
        }
        notification.setRead(true);
        return notificationRepository.save(notification);
    }

    @Transactional
    public void markAllRead(Long userId) {
        notificationRepository.markAllAsRead(userId);
    }

    public static class NotificationNotFoundException extends RuntimeException {
        public NotificationNotFoundException(Long notificationId) {
            super("Notification not found: " + notificationId);
        }
    }
}
