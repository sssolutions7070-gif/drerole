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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private static final String STATUS_PROPOSED = "PROPOSED";
    private static final int MAX_PROPOSED_SLOTS = 5;

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

    public static class InvalidSlotCountException extends RuntimeException {
        public InvalidSlotCountException() {
            super("Propose between 2 and " + MAX_PROPOSED_SLOTS + " time slots for the candidate to choose from");
        }
    }

    public static class RoundNotAwaitingSelectionException extends RuntimeException {
        public RoundNotAwaitingSelectionException() {
            super("This round isn't awaiting a slot selection");
        }
    }

    public static class InvalidSlotChoiceException extends RuntimeException {
        public InvalidSlotChoiceException() {
            super("That time wasn't one of the proposed slots");
        }
    }

    public static class NotTheCandidateException extends RuntimeException {
        public NotTheCandidateException() {
            super("Only the candidate for this application can confirm an interview slot");
        }
    }

    private final InterviewRoundRepository interviewRoundRepository;
    private final ApplicationRepository applicationRepository;
    private final RealtimeNotifier realtimeNotifier;
    private final NotificationFeedService notificationFeedService;
    private final List<String> stunUrls;
    private final String turnUrl;
    private final String turnUsername;
    private final String turnCredential;
    private final String frontendUrl;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public InterviewRoundService(InterviewRoundRepository interviewRoundRepository,
                                  ApplicationRepository applicationRepository,
                                  RealtimeNotifier realtimeNotifier,
                                  NotificationFeedService notificationFeedService,
                                  @Value("${app.webrtc.stun-urls:stun:stun.l.google.com:19302,stun:stun1.l.google.com:19302}") String stunUrls,
                                  @Value("${app.webrtc.turn-url:}") String turnUrl,
                                  @Value("${app.webrtc.turn-username:}") String turnUsername,
                                  @Value("${app.webrtc.turn-credential:}") String turnCredential,
                                  @Value("${app.frontend-url:http://localhost:3000}") String frontendUrl) {
        this.interviewRoundRepository = interviewRoundRepository;
        this.applicationRepository = applicationRepository;
        this.realtimeNotifier = realtimeNotifier;
        this.notificationFeedService = notificationFeedService;
        this.stunUrls = List.of(stunUrls.split("\\s*,\\s*"));
        this.turnUrl = turnUrl;
        this.turnUsername = turnUsername;
        this.turnCredential = turnCredential;
        this.frontendUrl = frontendUrl;
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
        notificationFeedService.record(candidateUserId(application), "INTERVIEW_ROUND_SCHEDULED",
            "Interview round scheduled: " + title,
            scheduledAt != null ? "Scheduled for " + scheduledAt : "Time to be confirmed.",
            "/dashboard");

        return saved;
    }

    /** Offers the candidate a choice of times instead of dictating one - an alternative to
     *  scheduleRound(), not a replacement. The round sits in status PROPOSED with no scheduledAt
     *  until confirmSlot() picks one. */
    public InterviewRound proposeSlots(Long applicationId, String title, String roundType, List<LocalDateTime> slots) {
        Application application = applicationRepository.findById(applicationId)
            .orElseThrow(() -> new ApplicationNotFoundException(applicationId));
        if (!ROUND_TYPES.contains(roundType)) {
            throw new InvalidRoundTypeException(roundType);
        }
        if (slots == null || slots.size() < 2 || slots.size() > MAX_PROPOSED_SLOTS) {
            throw new InvalidSlotCountException();
        }

        int nextRoundNumber = (int) interviewRoundRepository.countByApplicationId(applicationId) + 1;
        InterviewRound round = new InterviewRound(applicationId, nextRoundNumber, title, roundType,
            UUID.randomUUID().toString(), null);
        round.setStatus(STATUS_PROPOSED);
        round.setProposedSlots(writeSlots(slots));
        InterviewRound saved = interviewRoundRepository.save(round);

        realtimeNotifier.notifyUser(candidateUserId(application), "INTERVIEW_ROUND_PROPOSED", Map.of(
            "applicationId", applicationId, "roundId", saved.getId(), "title", title
        ));
        notificationFeedService.record(candidateUserId(application), "INTERVIEW_ROUND_PROPOSED",
            "Pick a time for: " + title,
            "The recruiter offered " + slots.size() + " possible times - choose one that works for you.",
            "/dashboard");

        return saved;
    }

    /** The candidate's response to proposeSlots(): locks in one of the offered times. Only the
     *  candidate on this application may call this - not the recruiter, and not participatesInRound's
     *  usual "either side" rule, since only the candidate has an opinion on which time works for them. */
    public InterviewRound confirmSlot(Long roundId, Long callerId, LocalDateTime chosenSlot) {
        InterviewRound round = requireRound(roundId);
        Application application = applicationRepository.findById(round.getApplicationId())
            .orElseThrow(() -> new ApplicationNotFoundException(round.getApplicationId()));

        if (!callerId.equals(candidateUserId(application))) {
            throw new NotTheCandidateException();
        }
        if (!STATUS_PROPOSED.equals(round.getStatus())) {
            throw new RoundNotAwaitingSelectionException();
        }
        if (!readSlots(round.getProposedSlots()).contains(chosenSlot)) {
            throw new InvalidSlotChoiceException();
        }

        round.setScheduledAt(chosenSlot);
        round.setStatus("SCHEDULED");
        round.setUpdatedAt(LocalDateTime.now());
        InterviewRound saved = interviewRoundRepository.save(round);

        Long recruiterId = recruiterUserId(application);
        realtimeNotifier.notifyUser(recruiterId, "INTERVIEW_ROUND_SLOT_CONFIRMED", Map.of(
            "applicationId", round.getApplicationId(), "roundId", roundId, "scheduledAt", String.valueOf(chosenSlot)
        ));
        if (recruiterId != null) {
            notificationFeedService.record(recruiterId, "INTERVIEW_ROUND_SLOT_CONFIRMED",
                "Interview time confirmed: " + round.getTitle(),
                "The candidate picked " + chosenSlot + ".",
                "/recruiter/dashboard");
        }

        return saved;
    }

    private String writeSlots(List<LocalDateTime> slots) {
        try {
            return objectMapper.writeValueAsString(slots.stream().map(String::valueOf).toList());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize proposed slots", e);
        }
    }

    private List<LocalDateTime> readSlots(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> raw = objectMapper.readValue(json, new TypeReference<List<String>>() {});
            return raw.stream().map(LocalDateTime::parse).toList();
        } catch (Exception e) {
            return List.of();
        }
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
        if (counterpartId != null) {
            notificationFeedService.record(counterpartId, "INTERVIEW_ROUND_STATUS_CHANGED",
                "Interview round " + status.toLowerCase().replace('_', ' '),
                "Round \"" + round.getTitle() + "\" is now " + status.toLowerCase().replace('_', ' ') + ".",
                counterpartId.equals(recruiterId) ? "/recruiter/dashboard" : "/dashboard");
        }

        return saved;
    }

    public InterviewRound rescheduleRound(Long roundId, LocalDateTime scheduledAt) {
        InterviewRound round = requireRound(roundId);
        round.setScheduledAt(scheduledAt);
        round.setStatus("SCHEDULED");
        round.setUpdatedAt(LocalDateTime.now());
        InterviewRound saved = interviewRoundRepository.save(round);

        applicationRepository.findById(round.getApplicationId()).ifPresent(application -> {
            realtimeNotifier.notifyUser(candidateUserId(application), "INTERVIEW_ROUND_RESCHEDULED", Map.of(
                "applicationId", round.getApplicationId(),
                "roundId", roundId,
                "scheduledAt", String.valueOf(scheduledAt)
            ));
            notificationFeedService.record(candidateUserId(application), "INTERVIEW_ROUND_RESCHEDULED",
                "Interview round rescheduled: " + round.getTitle(),
                "New time: " + scheduledAt,
                "/dashboard");
        });

        return saved;
    }

    /** A minimal, hand-built .ics file (RFC 5545) for one round - no library needed for a format
     *  this small. Times are written "floating" (no timezone/UTC suffix), same simplification the
     *  rest of this codebase already makes by storing every date as a plain LocalDateTime; a
     *  calendar app interprets a floating time in the viewer's own local timezone. */
    public String generateIcs(Long roundId) {
        InterviewRound round = requireRound(roundId);
        LocalDateTime start = round.getScheduledAt() != null ? round.getScheduledAt() : LocalDateTime.now();
        LocalDateTime end = start.plusHours(1);
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
        String joinUrl = frontendUrl + "/interview-rounds/" + roundId;

        return "BEGIN:VCALENDAR\r\n"
            + "VERSION:2.0\r\n"
            + "PRODID:-//DreamRole//Interview Pipeline//EN\r\n"
            + "BEGIN:VEVENT\r\n"
            + "UID:interview-round-" + roundId + "@dreamrole\r\n"
            + "DTSTAMP:" + LocalDateTime.now().format(fmt) + "\r\n"
            + "DTSTART:" + start.format(fmt) + "\r\n"
            + "DTEND:" + end.format(fmt) + "\r\n"
            + "SUMMARY:" + escapeIcsText(round.getTitle()) + "\r\n"
            + "DESCRIPTION:" + escapeIcsText("Join the interview: " + joinUrl) + "\r\n"
            + "URL:" + joinUrl + "\r\n"
            + "END:VEVENT\r\n"
            + "END:VCALENDAR\r\n";
    }

    private String escapeIcsText(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n");
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
