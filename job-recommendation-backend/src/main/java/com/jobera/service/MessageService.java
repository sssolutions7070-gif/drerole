package com.jobera.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.jobera.entity.Application;
import com.jobera.entity.Message;
import com.jobera.repository.ApplicationRepository;
import com.jobera.repository.MessageRepository;

/** Chat between a candidate and a recruiter, scoped to one application. */
@Service
public class MessageService {

    public static class ApplicationNotFoundException extends RuntimeException {
        public ApplicationNotFoundException(Long applicationId) {
            super("Application not found: " + applicationId);
        }
    }

    /** Raised when the sender isn't actually one of the two people who belong on this thread. */
    public static class NotAParticipantException extends RuntimeException {
        public NotAParticipantException(Long senderId, Long applicationId) {
            super("User " + senderId + " is not a participant in application " + applicationId);
        }
    }

    private final MessageRepository messageRepository;
    private final ApplicationRepository applicationRepository;
    private final RealtimeNotifier realtimeNotifier;
    private final NotificationFeedService notificationFeedService;

    public MessageService(MessageRepository messageRepository,
                           ApplicationRepository applicationRepository,
                           RealtimeNotifier realtimeNotifier,
                           NotificationFeedService notificationFeedService) {
        this.messageRepository = messageRepository;
        this.applicationRepository = applicationRepository;
        this.realtimeNotifier = realtimeNotifier;
        this.notificationFeedService = notificationFeedService;
    }

    private Long candidateUserId(Application application) {
        return application.getUser() != null ? application.getUser().getId() : null;
    }

    private Long recruiterUserId(Application application) {
        if (application.getJobPosting() == null || application.getJobPosting().getRecruiter() == null) {
            return null;
        }
        return Optional.ofNullable(application.getJobPosting().getRecruiter().getUser())
            .map(user -> user.getId())
            .orElse(null);
    }

    public Message sendMessage(Long applicationId, Long senderId, String body) {
        Application application = applicationRepository.findById(applicationId)
            .orElseThrow(() -> new ApplicationNotFoundException(applicationId));

        Long candidateId = candidateUserId(application);
        Long recruiterId = recruiterUserId(application);

        Long recipientId;
        if (senderId.equals(candidateId)) {
            recipientId = recruiterId;
        } else if (senderId.equals(recruiterId)) {
            recipientId = candidateId;
        } else {
            throw new NotAParticipantException(senderId, applicationId);
        }

        Message saved = messageRepository.save(new Message(applicationId, senderId, recipientId, body));

        if (recipientId != null) {
            String preview = body.length() > 120 ? body.substring(0, 120) + "..." : body;
            realtimeNotifier.notifyUser(recipientId, "NEW_MESSAGE", Map.of(
                "applicationId", applicationId,
                "senderId", senderId,
                "preview", preview
            ));
            notificationFeedService.record(recipientId, "NEW_MESSAGE", "New message", preview,
                recipientId.equals(recruiterId) ? "/recruiter/dashboard" : "/dashboard");
        }

        return saved;
    }

    /** Sends the same message to every one of these applications' candidates in one action - a
     *  recruiter reviewing a job's applicant list previously had to open each candidate's thread
     *  separately to send the same nudge/update to all of them. Each recipient still gets an
     *  ordinary 1:1 thread message (not a new "broadcast" concept), so replies work exactly like
     *  any other thread. One recipient failing (e.g. a stale application id) doesn't abort the
     *  rest of the batch. */
    public Map<String, Object> sendBulkMessage(List<Long> applicationIds, Long senderId, String body) {
        List<Long> sentTo = new ArrayList<>();
        List<Map<String, Object>> failures = new ArrayList<>();

        for (Long applicationId : applicationIds) {
            try {
                sendMessage(applicationId, senderId, body);
                sentTo.add(applicationId);
            } catch (Exception e) {
                Map<String, Object> failure = new LinkedHashMap<>();
                failure.put("applicationId", applicationId);
                failure.put("error", e.getMessage());
                failures.add(failure);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sentCount", sentTo.size());
        result.put("failedCount", failures.size());
        result.put("failures", failures);
        return result;
    }

    public List<Message> getThread(Long applicationId) {
        return messageRepository.findByApplicationIdOrderBySentAtAsc(applicationId);
    }

    /** Marks every message in this thread addressed to {@code readerId} as read. */
    public void markThreadRead(Long applicationId, Long readerId) {
        messageRepository.markThreadRead(applicationId, readerId, LocalDateTime.now());
    }

    public long getUnreadCount(Long userId) {
        return messageRepository.countByRecipientIdAndReadAtIsNull(userId);
    }
}
