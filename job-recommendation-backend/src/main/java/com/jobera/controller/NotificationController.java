package com.jobera.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jobera.entity.Notification;
import com.jobera.service.NotificationFeedService;

/** A user's notification feed, entirely scoped under their own user id - covered by AccessPolicy's
 *  existing self(userId) wildcard rule for /api/users/{userId}/**, so no new access-control rule
 *  is needed here. */
@RestController
@RequestMapping("/api/users/{userId}/notifications")
public class NotificationController {

    private final NotificationFeedService notificationFeedService;

    public NotificationController(NotificationFeedService notificationFeedService) {
        this.notificationFeedService = notificationFeedService;
    }

    @GetMapping
    public ResponseEntity<List<Notification>> list(@PathVariable Long userId) {
        return ResponseEntity.ok(notificationFeedService.listForUser(userId));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount(@PathVariable Long userId) {
        return ResponseEntity.ok(Map.of("count", notificationFeedService.unreadCount(userId)));
    }

    @PostMapping("/{notificationId}/read")
    public ResponseEntity<?> markRead(@PathVariable Long userId, @PathVariable Long notificationId) {
        try {
            return ResponseEntity.ok(notificationFeedService.markRead(notificationId, userId));
        } catch (NotificationFeedService.NotificationNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead(@PathVariable Long userId) {
        notificationFeedService.markAllRead(userId);
        return ResponseEntity.ok().build();
    }
}
