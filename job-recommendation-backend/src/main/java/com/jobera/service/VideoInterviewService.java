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
import com.jobera.entity.VideoInterview;
import com.jobera.repository.ApplicationRepository;
import com.jobera.repository.VideoInterviewRepository;

/** Schedules and manages the single video-interview "room" attached to an application. The
 *  WebRTC signaling itself (SDP offer/answer, ICE candidates) happens over a separate WebSocket
 *  (see VideoSignalWebSocketHandler) - this service only manages the room's lifecycle and hands
 *  out the ICE server list a browser's RTCPeerConnection needs to actually connect. */
@Service
public class VideoInterviewService {

    private static final Set<String> VALID_STATUSES = Set.of("SCHEDULED", "IN_PROGRESS", "COMPLETED", "CANCELLED");

    public static class ApplicationNotFoundException extends RuntimeException {
        public ApplicationNotFoundException(Long applicationId) {
            super("Application not found: " + applicationId);
        }
    }

    public static class InterviewNotFoundException extends RuntimeException {
        public InterviewNotFoundException(Long applicationId) {
            super("No video interview has been scheduled for application " + applicationId);
        }
    }

    public static class InvalidStatusException extends RuntimeException {
        public InvalidStatusException(String status) {
            super("Unknown interview status: " + status);
        }
    }

    private final VideoInterviewRepository videoInterviewRepository;
    private final ApplicationRepository applicationRepository;
    private final RealtimeNotifier realtimeNotifier;
    private final List<String> stunUrls;
    private final String turnUrl;
    private final String turnUsername;
    private final String turnCredential;

    public VideoInterviewService(VideoInterviewRepository videoInterviewRepository,
                                  ApplicationRepository applicationRepository,
                                  RealtimeNotifier realtimeNotifier,
                                  @Value("${app.webrtc.stun-urls:stun:stun.l.google.com:19302,stun:stun1.l.google.com:19302}") String stunUrls,
                                  @Value("${app.webrtc.turn-url:}") String turnUrl,
                                  @Value("${app.webrtc.turn-username:}") String turnUsername,
                                  @Value("${app.webrtc.turn-credential:}") String turnCredential) {
        this.videoInterviewRepository = videoInterviewRepository;
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

    public VideoInterview scheduleInterview(Long applicationId, LocalDateTime scheduledAt) {
        Application application = applicationRepository.findById(applicationId)
            .orElseThrow(() -> new ApplicationNotFoundException(applicationId));

        VideoInterview interview = videoInterviewRepository.findByApplicationId(applicationId)
            .orElseGet(() -> new VideoInterview(applicationId, UUID.randomUUID().toString(), null));
        interview.setScheduledAt(scheduledAt);
        interview.setStatus("SCHEDULED");
        interview.setUpdatedAt(LocalDateTime.now());
        VideoInterview saved = videoInterviewRepository.save(interview);

        Long candidateId = candidateUserId(application);
        realtimeNotifier.notifyUser(candidateId, "VIDEO_INTERVIEW_SCHEDULED", Map.of(
            "applicationId", applicationId,
            "scheduledAt", String.valueOf(scheduledAt)
        ));

        return saved;
    }

    public Map<String, Object> getJoinInfo(Long applicationId, Long callerId) {
        Application application = applicationRepository.findById(applicationId)
            .orElseThrow(() -> new ApplicationNotFoundException(applicationId));
        VideoInterview interview = videoInterviewRepository.findByApplicationId(applicationId)
            .orElseThrow(() -> new InterviewNotFoundException(applicationId));

        Long candidateId = candidateUserId(application);
        Long recruiterId = recruiterUserId(application);
        boolean callerIsCandidate = callerId.equals(candidateId);
        String counterpartName = callerIsCandidate
            ? recruiterDisplayName(application)
            : (application.getUser() != null ? application.getUser().getFullName() : "Candidate");

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("applicationId", applicationId);
        info.put("roomId", interview.getRoomId());
        info.put("status", interview.getStatus());
        info.put("scheduledAt", interview.getScheduledAt());
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

    public VideoInterview updateStatus(Long applicationId, String status, Long callerId) {
        if (!VALID_STATUSES.contains(status)) {
            throw new InvalidStatusException(status);
        }
        Application application = applicationRepository.findById(applicationId)
            .orElseThrow(() -> new ApplicationNotFoundException(applicationId));
        VideoInterview interview = videoInterviewRepository.findByApplicationId(applicationId)
            .orElseThrow(() -> new InterviewNotFoundException(applicationId));

        interview.setStatus(status);
        interview.setUpdatedAt(LocalDateTime.now());
        VideoInterview saved = videoInterviewRepository.save(interview);

        Long candidateId = candidateUserId(application);
        Long recruiterId = recruiterUserId(application);
        Long counterpartId = callerId.equals(candidateId) ? recruiterId : candidateId;
        realtimeNotifier.notifyUser(counterpartId, "VIDEO_INTERVIEW_STATUS_CHANGED", Map.of(
            "applicationId", applicationId,
            "status", status
        ));

        return saved;
    }

    /** Used by the WebSocket handshake to check whether a given user may join a given room -
     *  the same participant rule AccessPolicy already applies to every other application-scoped
     *  route, re-checked here because a WebSocket handshake never goes through AuthInterceptor. */
    public boolean isParticipant(String roomId, Long userId) {
        return videoInterviewRepository.findByRoomId(roomId)
            .flatMap(interview -> applicationRepository.findById(interview.getApplicationId()))
            .map(application -> userId.equals(candidateUserId(application)) || userId.equals(recruiterUserId(application)))
            .orElse(false);
    }
}
