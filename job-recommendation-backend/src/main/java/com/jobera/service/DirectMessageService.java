package com.jobera.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.jobera.entity.DirectMessage;
import com.jobera.entity.Recruiter;
import com.jobera.entity.User;
import com.jobera.repository.DirectMessageRepository;
import com.jobera.repository.RecruiterRepository;
import com.jobera.repository.UserRepository;

/**
 * Chat between a recruiter and a candidate who found each other outside of any application - a
 * recruiter reaching out to someone from CandidateDiscoveryService's "open to work" pool. Access
 * is checked by AccessPolicy before this is ever called: only the recruiter (owner or a team
 * member) or the candidate themselves can reach these methods, so recipient resolution here just
 * asks "is the sender the candidate, or the recruiter side?"
 */
@Service
public class DirectMessageService {

    private final DirectMessageRepository directMessageRepository;
    private final RecruiterRepository recruiterRepository;
    private final UserRepository userRepository;
    private final RealtimeNotifier realtimeNotifier;
    private final NotificationFeedService notificationFeedService;

    public DirectMessageService(DirectMessageRepository directMessageRepository,
                                 RecruiterRepository recruiterRepository,
                                 UserRepository userRepository,
                                 RealtimeNotifier realtimeNotifier,
                                 NotificationFeedService notificationFeedService) {
        this.directMessageRepository = directMessageRepository;
        this.recruiterRepository = recruiterRepository;
        this.userRepository = userRepository;
        this.realtimeNotifier = realtimeNotifier;
        this.notificationFeedService = notificationFeedService;
    }

    public static class RecruiterNotFoundException extends RuntimeException {
        public RecruiterNotFoundException(Long recruiterId) {
            super("Recruiter not found: " + recruiterId);
        }
    }

    public static class CandidateNotFoundException extends RuntimeException {
        public CandidateNotFoundException(Long candidateUserId) {
            super("Candidate not found: " + candidateUserId);
        }
    }

    private Recruiter requireRecruiter(Long recruiterId) {
        return recruiterRepository.findById(recruiterId)
            .orElseThrow(() -> new RecruiterNotFoundException(recruiterId));
    }

    public DirectMessage sendMessage(Long recruiterId, Long candidateUserId, Long senderId, String body) {
        Recruiter recruiter = requireRecruiter(recruiterId);
        User candidate = userRepository.findById(candidateUserId)
            .orElseThrow(() -> new CandidateNotFoundException(candidateUserId));

        boolean senderIsCandidate = senderId.equals(candidateUserId);
        Long recipientId = senderIsCandidate
            ? (recruiter.getUser() != null ? recruiter.getUser().getId() : null)
            : candidateUserId;

        DirectMessage saved = directMessageRepository.save(
            new DirectMessage(recruiterId, candidateUserId, senderId, recipientId, body));

        if (recipientId != null) {
            String preview = body.length() > 120 ? body.substring(0, 120) + "..." : body;
            realtimeNotifier.notifyUser(recipientId, "NEW_MESSAGE", Map.of(
                "recruiterId", recruiterId, "candidateUserId", candidateUserId, "preview", preview));
            notificationFeedService.record(recipientId, "NEW_MESSAGE", "New message",
                preview, senderIsCandidate ? "/recruiter/dashboard" : "/dashboard");
        }

        return saved;
    }

    public List<DirectMessage> getThread(Long recruiterId, Long candidateUserId) {
        requireRecruiter(recruiterId);
        return directMessageRepository.findByRecruiterIdAndCandidateUserIdOrderBySentAtAsc(recruiterId, candidateUserId);
    }

    /** One row per candidate this recruiter has an open thread with, most recently active first. */
    public List<Map<String, Object>> listThreadsForRecruiter(Long recruiterId) {
        List<DirectMessage> messages = directMessageRepository.findByRecruiterIdOrderBySentAtDesc(recruiterId);
        Map<Long, DirectMessage> latestByCandidateId = new LinkedHashMap<>();
        for (DirectMessage message : messages) {
            latestByCandidateId.putIfAbsent(message.getCandidateUserId(), message);
        }
        return latestByCandidateId.values().stream().map(message -> {
            Map<String, Object> view = new LinkedHashMap<>();
            User candidate = userRepository.findById(message.getCandidateUserId()).orElse(null);
            view.put("candidateUserId", message.getCandidateUserId());
            view.put("candidateName", candidate != null ? candidate.getFullName() : "Unknown");
            view.put("lastMessage", message.getBody());
            view.put("lastMessageAt", message.getSentAt());
            return view;
        }).toList();
    }

    /** One row per recruiter this candidate has an open thread with, most recently active first. */
    public List<Map<String, Object>> listThreadsForCandidate(Long candidateUserId) {
        List<DirectMessage> messages = directMessageRepository.findByCandidateUserIdOrderBySentAtDesc(candidateUserId);
        Map<Long, DirectMessage> latestByRecruiterId = new LinkedHashMap<>();
        for (DirectMessage message : messages) {
            latestByRecruiterId.putIfAbsent(message.getRecruiterId(), message);
        }
        return latestByRecruiterId.values().stream().map(message -> {
            Map<String, Object> view = new LinkedHashMap<>();
            Recruiter recruiter = recruiterRepository.findById(message.getRecruiterId()).orElse(null);
            view.put("recruiterId", message.getRecruiterId());
            view.put("companyName", recruiter != null ? recruiter.getCompanyName() : "Unknown company");
            view.put("lastMessage", message.getBody());
            view.put("lastMessageAt", message.getSentAt());
            return view;
        }).toList();
    }
}
