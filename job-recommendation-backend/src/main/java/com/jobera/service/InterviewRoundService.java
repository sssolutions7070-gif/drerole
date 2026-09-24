package com.jobera.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.jobera.entity.Application;
import com.jobera.entity.InterviewRound;
import com.jobera.repository.ApplicationRepository;
import com.jobera.repository.InterviewRoundRepository;

/** Schedules and manages the interview pipeline for an application - an ordered sequence of
 *  rounds (phone screen, technical, culture fit, ...), each with its own video/code-editor room.
 *  WebRTC signaling itself happens over a separate WebSocket keyed only by roomId
 *  (VideoSignalWebSocketHandler), reused unchanged from the single-room design this replaced. */
@Service
public class InterviewRoundService {

    public static final Set<String> ROUND_TYPES = Set.of(
        "PHONE_SCREEN", "TECHNICAL", "CULTURE_FIT", "FINAL", "OTHER");
    private static final Set<String> VALID_STATUSES = Set.of("SCHEDULED", "IN_PROGRESS", "COMPLETED", "CANCELLED");

    public static class ApplicationNotFoundException extends RuntimeException {
        public ApplicationNotFoundException(Long applicationId) {
            super("Application not found: " + applicationId);
        }
    }

    public static class RoundNotFoundException extends RuntimeException {
        public RoundNotFoundException(Long roundId) {
            super("Interview round not found: " + roundId);
        }
    }

    public static class InvalidRoundTypeException extends RuntimeException {
        public InvalidRoundTypeException(String roundType) {
            super("Unknown interview round type: " + roundType);
        }
    }

    public static class InvalidStatusException extends RuntimeException {
        public InvalidStatusException(String status) {
            super("Unknown interview status: " + status);
        }
    }

    private final InterviewRoundRepository interviewRoundRepository;
    private final ApplicationRepository applicationRepository;
    private final RealtimeNotifier realtimeNotifier;
    private final List<String> stunUrls;
    private final String turnUrl;
    private final String turnUsername;
    private final String turnCredential;

    public InterviewRoundService(InterviewRoundRepository interviewRoundRepository,
                                  ApplicationRepository applicationRepository,
                                  RealtimeNotifier realtimeNotifier,
                                  @Value("${app.webrtc.stun-urls:stun:stun.l.google.com:19302,stun:stun1.l.google.com:19302}") String stunUrls,
                                  @Value("${app.webrtc.turn-url:}") String turnUrl,
                                  @Value("${app.webrtc.turn-username:}") String turnUsername,
                                  @Value("${app.webrtc.turn-credential:}") String turnCredential) {
        this.interviewRoundRepository = interviewRoundRepository;
        this.applicationRepository = applicationRepository;
        this.realtimeNotifier = realtimeNotifier;
        this.stunUrls = List.of(stunUrls.split("\\s*,\\s*"));
        this.turnUrl = turnUrl;
        this.turnUsername = turnUsername;
        this.turnCredential = turnCredential;
    }

    private Long candidateUserId(Application application) {
        return application.getUser() != null ? application.getUser().getId() : null;
    }

    private Long recruiterUserId(Application application) {
        if (application.getJobPosting() == null || application.getJobPosting().getRecruiter() == null) {
            return null;
        }
        return Optional.ofNullable(application.getJobPosting().getRecruiter().getUser())
            .map(u -> u.getId())
            .orElse(null);
    }

    public InterviewRound scheduleRound(Long applicationId, String title, String roundType, LocalDateTime scheduledAt) {
        Application application = applicationRepository.findById(applicationId)
            .orElseThrow(() -> new ApplicationNotFoundException(applicationId));
        if (!ROUND_TYPES.contains(roundType)) {
            throw new InvalidRoundTypeException(roundType);
        }

        int nextRoundNumber = (int) interviewRoundRepository.countByApplicationId(applicationId) + 1;
        InterviewRound round = new InterviewRound(applicationId, nextRoundNumber, title, roundType,
            UUID.randomUUID().toString(), scheduledAt);
        InterviewRound saved = interviewRoundRepository.save(round);

        realtimeNotifier.notifyUser(candidateUserId(application), "INTERVIEW_ROUND_SCHEDULED", Map.of(
            "applicationId", applicationId,
            "roundId", saved.getId(),
            "title", title,
            "scheduledAt", String.valueOf(scheduledAt)
        ));

        return saved;
    }

    public List<InterviewRound> listRounds(Long applicationId) {
        if (applicationRepository.findById(applicationId).isEmpty()) {
            throw new ApplicationNotFoundException(applicationId);
        }
        return interviewRoundRepository.findByApplicationIdOrderByRoundNumberAsc(applicationId);
    }

    private InterviewRound requireRound(Long roundId) {
        return interviewRoundRepository.findById(roundId)
            .orElseThrow(() -> new RoundNotFoundException(roundId));
    }

    public Map<String, Object> getJoinInfo(Long roundId, Long callerId) {
        InterviewRound round = requireRound(roundId);
        Application application = applicationRepository.findById(round.getApplicationId())
            .orElseThrow(() -> new ApplicationNotFoundException(round.getApplicationId()));

        Long candidateId = candidateUserId(application);
        boolean callerIsCandidate = callerId.equals(candidateId);
        String counterpartName = callerIsCandidate
            ? recruiterDisplayName(application)
            : (application.getUser() != null ? application.getUser().getFullName() : "Candidate");

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("roundId", round.getId());
        info.put("applicationId", round.getApplicationId());
        info.put("roomId", round.getRoomId());
        info.put("title", round.getTitle());
        info.put("roundType", round.getRoundType());
        info.put("status", round.getStatus());
        info.put("scheduledAt", round.getScheduledAt());
        info.put("role", callerIsCandidate ? "candidate" : "recruiter");
        info.put("counterpartName", counterpartName);
        info.put("iceServers", iceServers());
        return info;
    }

    private String recruiterDisplayName(Application application) {
        if (application.getJobPosting() == null || application.getJobPosting().getRecruiter() == null) {
            return "Recruiter";
        }
        String companyName = application.getJobPosting().getRecruiter().getCompanyName();
        return (companyName == null || companyName.isBlank()) ? "Recruiter" : companyName;
    }

    private List<Map<String, Object>> iceServers() {
        List<Map<String, Object>> servers = new ArrayList<>();
        servers.add(Map.of("urls", stunUrls));
        if (!turnUrl.isBlank()) {
            Map<String, Object> turn = new LinkedHashMap<>();
            turn.put("urls", turnUrl);
            turn.put("username", turnUsername);
            turn.put("credential", turnCredential);
            servers.add(turn);
        }
        return servers;
    }

    public InterviewRound updateStatus(Long roundId, String status, Long callerId) {
        if (!VALID_STATUSES.contains(status)) {
            throw new InvalidStatusException(status);
        }
        InterviewRound round = requireRound(roundId);
        Application application = applicationRepository.findById(round.getApplicationId())
            .orElseThrow(() -> new ApplicationNotFoundException(round.getApplicationId()));

        round.setStatus(status);
        round.setUpdatedAt(LocalDateTime.now());
        InterviewRound saved = interviewRoundRepository.save(round);

        Long candidateId = candidateUserId(application);
        Long recruiterId = recruiterUserId(application);
        Long counterpartId = callerId.equals(candidateId) ? recruiterId : candidateId;
        realtimeNotifier.notifyUser(counterpartId, "INTERVIEW_ROUND_STATUS_CHANGED", Map.of(
            "applicationId", round.getApplicationId(),
            "roundId", roundId,
            "status", status
        ));

        return saved;
    }

    public InterviewRound rescheduleRound(Long roundId, LocalDateTime scheduledAt) {
        InterviewRound round = requireRound(roundId);
        round.setScheduledAt(scheduledAt);
        round.setStatus("SCHEDULED");
        round.setUpdatedAt(LocalDateTime.now());
        InterviewRound saved = interviewRoundRepository.save(round);

        applicationRepository.findById(round.getApplicationId()).ifPresent(application ->
            realtimeNotifier.notifyUser(candidateUserId(application), "INTERVIEW_ROUND_RESCHEDULED", Map.of(
                "applicationId", round.getApplicationId(),
                "roundId", roundId,
                "scheduledAt", String.valueOf(scheduledAt)
            )));

        return saved;
    }

    /** Used by the WebSocket handshake to check whether a given user may join a given room's
     *  video/code-editor session - the same participant rule AccessPolicy applies to every other
     *  application-scoped route, re-checked here because a WebSocket handshake never goes
     *  through AuthInterceptor. */
    public boolean isParticipant(String roomId, Long userId) {
        return interviewRoundRepository.findByRoomId(roomId)
            .flatMap(round -> applicationRepository.findById(round.getApplicationId()))
            .map(application -> userId.equals(candidateUserId(application)) || userId.equals(recruiterUserId(application)))
            .orElse(false);
    }
}
