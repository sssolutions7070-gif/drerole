package com.jobera.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jobera.entity.Application;
import com.jobera.entity.JobPosting;
import com.jobera.entity.Recruiter;
import com.jobera.entity.User;
import com.jobera.entity.VideoInterview;
import com.jobera.repository.ApplicationRepository;
import com.jobera.repository.VideoInterviewRepository;

@ExtendWith(MockitoExtension.class)
class VideoInterviewServiceTest {

    @Mock VideoInterviewRepository videoInterviewRepository;
    @Mock ApplicationRepository applicationRepository;
    @Mock RealtimeNotifier realtimeNotifier;

    VideoInterviewService service;

    User candidate;
    User recruiterUser;
    Application application;

    @BeforeEach
    void setUp() {
        service = new VideoInterviewService(videoInterviewRepository, applicationRepository, realtimeNotifier,
            "stun:stun.l.google.com:19302,stun:stun1.l.google.com:19302", "", "", "");

        candidate = new User("candidate@example.com", "hash", "Candidate");
        candidate.setId(1L);

        recruiterUser = new User("recruiter@example.com", "hash", "Recruiter");
        recruiterUser.setId(2L);

        Recruiter recruiter = new Recruiter();
        recruiter.setId(10L);
        recruiter.setUser(recruiterUser);
        recruiter.setCompanyName("Acme Corp");

        JobPosting job = new JobPosting();
        job.setId(100L);
        job.setRecruiter(recruiter);

        application = new Application();
        application.setId(1000L);
        application.setUser(candidate);
        application.setJobPosting(job);

        lenient().when(applicationRepository.findById(1000L)).thenReturn(Optional.of(application));
        lenient().when(videoInterviewRepository.save(any(VideoInterview.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void schedulingCreatesANewRoomAndNotifiesTheCandidate() {
        when(videoInterviewRepository.findByApplicationId(1000L)).thenReturn(Optional.empty());
        LocalDateTime when = LocalDateTime.now().plusDays(1);

        VideoInterview interview = service.scheduleInterview(1000L, when);

        assertNotNull(interview.getRoomId());
        assertEquals("SCHEDULED", interview.getStatus());
        assertEquals(when, interview.getScheduledAt());
        verify(realtimeNotifier).notifyUser(anyLong(), org.mockito.ArgumentMatchers.eq("VIDEO_INTERVIEW_SCHEDULED"), any());
    }

    @Test
    void reschedulingReusesTheExistingRoomId() {
        VideoInterview existing = new VideoInterview(1000L, "existing-room-id", LocalDateTime.now());
        when(videoInterviewRepository.findByApplicationId(1000L)).thenReturn(Optional.of(existing));
        LocalDateTime newTime = LocalDateTime.now().plusDays(2);

        VideoInterview interview = service.scheduleInterview(1000L, newTime);

        assertEquals("existing-room-id", interview.getRoomId());
        assertEquals(newTime, interview.getScheduledAt());
    }

    @Test
    void schedulingForAMissingApplicationFails() {
        when(applicationRepository.findById(1000L)).thenReturn(Optional.empty());

        assertThrows(VideoInterviewService.ApplicationNotFoundException.class,
            () -> service.scheduleInterview(1000L, LocalDateTime.now()));
        verify(videoInterviewRepository, never()).save(any());
    }

    @Test
    void candidateGetsTheRecruitersCompanyNameAsTheCounterpart() {
        VideoInterview existing = new VideoInterview(1000L, "room-1", LocalDateTime.now());
        existing.setStatus("SCHEDULED");
        when(videoInterviewRepository.findByApplicationId(1000L)).thenReturn(Optional.of(existing));

        Map<String, Object> info = service.getJoinInfo(1000L, candidate.getId());

        assertEquals("room-1", info.get("roomId"));
        assertEquals("candidate", info.get("role"));
        assertEquals("Acme Corp", info.get("counterpartName"));
        assertNotNull(info.get("iceServers"));
    }

    @Test
    void recruiterGetsTheCandidatesNameAsTheCounterpart() {
        VideoInterview existing = new VideoInterview(1000L, "room-1", LocalDateTime.now());
        when(videoInterviewRepository.findByApplicationId(1000L)).thenReturn(Optional.of(existing));

        Map<String, Object> info = service.getJoinInfo(1000L, recruiterUser.getId());

        assertEquals("recruiter", info.get("role"));
        assertEquals("Candidate", info.get("counterpartName"));
    }

    @Test
    void joinInfoIncludesATurnServerWhenOneIsConfigured() {
        VideoInterviewService withTurn = new VideoInterviewService(videoInterviewRepository, applicationRepository,
            realtimeNotifier, "stun:stun.l.google.com:19302", "turn:turn.example.com:3478", "user", "cred");
        VideoInterview existing = new VideoInterview(1000L, "room-1", LocalDateTime.now());
        when(videoInterviewRepository.findByApplicationId(1000L)).thenReturn(Optional.of(existing));

        Map<String, Object> info = withTurn.getJoinInfo(1000L, candidate.getId());

        @SuppressWarnings("unchecked")
        var iceServers = (java.util.List<Map<String, Object>>) info.get("iceServers");
        assertEquals(2, iceServers.size());
        assertEquals("turn:turn.example.com:3478", iceServers.get(1).get("urls"));
    }

    @Test
    void gettingJoinInfoBeforeSchedulingFails() {
        when(videoInterviewRepository.findByApplicationId(1000L)).thenReturn(Optional.empty());

        assertThrows(VideoInterviewService.InterviewNotFoundException.class,
            () -> service.getJoinInfo(1000L, candidate.getId()));
    }

    @Test
    void updatingStatusNotifiesTheOtherParticipant() {
        VideoInterview existing = new VideoInterview(1000L, "room-1", LocalDateTime.now());
        when(videoInterviewRepository.findByApplicationId(1000L)).thenReturn(Optional.of(existing));

        VideoInterview updated = service.updateStatus(1000L, "IN_PROGRESS", candidate.getId());

        assertEquals("IN_PROGRESS", updated.getStatus());
        verify(realtimeNotifier).notifyUser(recruiterUser.getId(), "VIDEO_INTERVIEW_STATUS_CHANGED",
            Map.of("applicationId", 1000L, "status", "IN_PROGRESS"));
    }

    @Test
    void anUnknownStatusIsRejected() {
        assertThrows(VideoInterviewService.InvalidStatusException.class,
            () -> service.updateStatus(1000L, "BOGUS", candidate.getId()));
        verify(videoInterviewRepository, never()).findByApplicationId(anyLong());
    }

    @Test
    void isParticipantIsTrueForBothCandidateAndRecruiter() {
        VideoInterview existing = new VideoInterview(1000L, "room-1", LocalDateTime.now());
        when(videoInterviewRepository.findByRoomId("room-1")).thenReturn(Optional.of(existing));

        assertTrue(service.isParticipant("room-1", candidate.getId()));
        assertTrue(service.isParticipant("room-1", recruiterUser.getId()));
    }

    @Test
    void isParticipantIsFalseForAnUnrelatedUser() {
        VideoInterview existing = new VideoInterview(1000L, "room-1", LocalDateTime.now());
        when(videoInterviewRepository.findByRoomId("room-1")).thenReturn(Optional.of(existing));

        assertTrue(!service.isParticipant("room-1", 999L));
    }

    @Test
    void isParticipantIsFalseForAnUnknownRoom() {
        when(videoInterviewRepository.findByRoomId("unknown")).thenReturn(Optional.empty());

        assertTrue(!service.isParticipant("unknown", candidate.getId()));
    }
}
