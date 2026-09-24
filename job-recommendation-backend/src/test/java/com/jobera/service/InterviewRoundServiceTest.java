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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jobera.entity.Application;
import com.jobera.entity.InterviewRound;
import com.jobera.entity.JobPosting;
import com.jobera.entity.Recruiter;
import com.jobera.entity.User;
import com.jobera.repository.ApplicationRepository;
import com.jobera.repository.InterviewRoundRepository;

@ExtendWith(MockitoExtension.class)
class InterviewRoundServiceTest {

    @Mock InterviewRoundRepository interviewRoundRepository;
    @Mock ApplicationRepository applicationRepository;
    @Mock RealtimeNotifier realtimeNotifier;

    InterviewRoundService service;

    User candidate;
    User recruiterUser;
    Application application;

    @BeforeEach
    void setUp() {
        service = new InterviewRoundService(interviewRoundRepository, applicationRepository, realtimeNotifier,
            "stun:stun.l.google.com:19302", "", "", "");

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
        lenient().when(interviewRoundRepository.save(any(InterviewRound.class))).thenAnswer(inv -> {
            InterviewRound round = inv.getArgument(0);
            if (round.getId() == null) {
                round.setId(5000L);
            }
            return round;
        });
    }

    @Test
    void schedulingTheFirstRoundStartsAtNumberOne() {
        when(interviewRoundRepository.countByApplicationId(1000L)).thenReturn(0L);

        InterviewRound round = service.scheduleRound(1000L, "Phone screen", "PHONE_SCREEN", LocalDateTime.now());

        assertEquals(1, round.getRoundNumber());
        assertEquals("SCHEDULED", round.getStatus());
        assertNotNull(round.getRoomId());
        verify(realtimeNotifier).notifyUser(anyLong(), org.mockito.ArgumentMatchers.eq("INTERVIEW_ROUND_SCHEDULED"), any());
    }

    @Test
    void schedulingASecondRoundIncrementsTheRoundNumber() {
        when(interviewRoundRepository.countByApplicationId(1000L)).thenReturn(1L);

        InterviewRound round = service.scheduleRound(1000L, "Technical round", "TECHNICAL", LocalDateTime.now());

        assertEquals(2, round.getRoundNumber());
    }

    @Test
    void schedulingWithAnInvalidRoundTypeFails() {
        assertThrows(InterviewRoundService.InvalidRoundTypeException.class,
            () -> service.scheduleRound(1000L, "Round", "BOGUS", LocalDateTime.now()));
        verify(interviewRoundRepository, never()).save(any());
    }

    @Test
    void schedulingForAMissingApplicationFails() {
        when(applicationRepository.findById(1000L)).thenReturn(Optional.empty());

        assertThrows(InterviewRoundService.ApplicationNotFoundException.class,
            () -> service.scheduleRound(1000L, "Round", "TECHNICAL", LocalDateTime.now()));
    }

    @Test
    void listingRoundsReturnsThemInOrder() {
        InterviewRound r1 = new InterviewRound(1000L, 1, "Phone screen", "PHONE_SCREEN", "room-1", LocalDateTime.now());
        InterviewRound r2 = new InterviewRound(1000L, 2, "Technical", "TECHNICAL", "room-2", LocalDateTime.now());
        when(interviewRoundRepository.findByApplicationIdOrderByRoundNumberAsc(1000L)).thenReturn(List.of(r1, r2));

        List<InterviewRound> rounds = service.listRounds(1000L);

        assertEquals(2, rounds.size());
    }

    @Test
    void listingRoundsForAMissingApplicationFails() {
        when(applicationRepository.findById(1000L)).thenReturn(Optional.empty());

        assertThrows(InterviewRoundService.ApplicationNotFoundException.class, () -> service.listRounds(1000L));
    }

    @Test
    void candidateGetsTheRecruitersCompanyNameAsTheCounterpart() {
        InterviewRound round = new InterviewRound(1000L, 1, "Technical", "TECHNICAL", "room-1", LocalDateTime.now());
        round.setId(5000L);
        when(interviewRoundRepository.findById(5000L)).thenReturn(Optional.of(round));

        Map<String, Object> info = service.getJoinInfo(5000L, candidate.getId());

        assertEquals("room-1", info.get("roomId"));
        assertEquals("candidate", info.get("role"));
        assertEquals("Acme Corp", info.get("counterpartName"));
        assertNotNull(info.get("iceServers"));
    }

    @Test
    void recruiterGetsTheCandidatesNameAsTheCounterpart() {
        InterviewRound round = new InterviewRound(1000L, 1, "Technical", "TECHNICAL", "room-1", LocalDateTime.now());
        round.setId(5000L);
        when(interviewRoundRepository.findById(5000L)).thenReturn(Optional.of(round));

        Map<String, Object> info = service.getJoinInfo(5000L, recruiterUser.getId());

        assertEquals("recruiter", info.get("role"));
        assertEquals("Candidate", info.get("counterpartName"));
    }

    @Test
    void joinInfoForAnUnknownRoundFails() {
        when(interviewRoundRepository.findById(5000L)).thenReturn(Optional.empty());

        assertThrows(InterviewRoundService.RoundNotFoundException.class, () -> service.getJoinInfo(5000L, 1L));
    }

    @Test
    void updatingStatusNotifiesTheOtherParticipant() {
        InterviewRound round = new InterviewRound(1000L, 1, "Technical", "TECHNICAL", "room-1", LocalDateTime.now());
        round.setId(5000L);
        when(interviewRoundRepository.findById(5000L)).thenReturn(Optional.of(round));

        InterviewRound updated = service.updateStatus(5000L, "IN_PROGRESS", candidate.getId());

        assertEquals("IN_PROGRESS", updated.getStatus());
        verify(realtimeNotifier).notifyUser(recruiterUser.getId(), "INTERVIEW_ROUND_STATUS_CHANGED", Map.of(
            "applicationId", 1000L, "roundId", 5000L, "status", "IN_PROGRESS"));
    }

    @Test
    void anUnknownStatusIsRejected() {
        assertThrows(InterviewRoundService.InvalidStatusException.class,
            () -> service.updateStatus(5000L, "BOGUS", 1L));
        verify(interviewRoundRepository, never()).findById(anyLong());
    }

    @Test
    void reschedulingUpdatesTheTimeAndResetsToScheduled() {
        InterviewRound round = new InterviewRound(1000L, 1, "Technical", "TECHNICAL", "room-1", LocalDateTime.now());
        round.setId(5000L);
        round.setStatus("CANCELLED");
        when(interviewRoundRepository.findById(5000L)).thenReturn(Optional.of(round));
        LocalDateTime newTime = LocalDateTime.now().plusDays(3);

        InterviewRound rescheduled = service.rescheduleRound(5000L, newTime);

        assertEquals(newTime, rescheduled.getScheduledAt());
        assertEquals("SCHEDULED", rescheduled.getStatus());
    }

    @Test
    void isParticipantIsTrueForBothCandidateAndRecruiter() {
        InterviewRound round = new InterviewRound(1000L, 1, "Technical", "TECHNICAL", "room-1", LocalDateTime.now());
        when(interviewRoundRepository.findByRoomId("room-1")).thenReturn(Optional.of(round));

        assertTrue(service.isParticipant("room-1", candidate.getId()));
        assertTrue(service.isParticipant("room-1", recruiterUser.getId()));
    }

    @Test
    void isParticipantIsFalseForAnUnrelatedUser() {
        InterviewRound round = new InterviewRound(1000L, 1, "Technical", "TECHNICAL", "room-1", LocalDateTime.now());
        when(interviewRoundRepository.findByRoomId("room-1")).thenReturn(Optional.of(round));

        assertEquals(false, service.isParticipant("room-1", 999L));
    }

    @Test
    void isParticipantIsFalseForAnUnknownRoom() {
        when(interviewRoundRepository.findByRoomId("unknown")).thenReturn(Optional.empty());

        assertEquals(false, service.isParticipant("unknown", candidate.getId()));
    }
}
