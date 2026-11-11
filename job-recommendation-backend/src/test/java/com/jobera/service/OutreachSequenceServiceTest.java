package com.jobera.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

import com.jobera.entity.DirectMessage;
import com.jobera.entity.OutreachSequence;
import com.jobera.entity.Recruiter;
import com.jobera.entity.User;
import com.jobera.repository.DirectMessageRepository;
import com.jobera.repository.OutreachSequenceRepository;
import com.jobera.repository.RecruiterRepository;
import com.jobera.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class OutreachSequenceServiceTest {

    @Mock OutreachSequenceRepository outreachSequenceRepository;
    @Mock RecruiterRepository recruiterRepository;
    @Mock UserRepository userRepository;
    @Mock DirectMessageService directMessageService;
    @Mock DirectMessageRepository directMessageRepository;

    OutreachSequenceService service;

    @BeforeEach
    void setUp() {
        service = new OutreachSequenceService(outreachSequenceRepository, recruiterRepository, userRepository,
            directMessageService, directMessageRepository);
    }

    private Recruiter recruiter(long id, long userId) {
        Recruiter r = new Recruiter();
        r.setId(id);
        User user = new User();
        user.setId(userId);
        r.setUser(user);
        return r;
    }

    @Test
    void startingASequenceSendsTheFirstStepImmediately() {
        when(recruiterRepository.findById(1L)).thenReturn(Optional.of(recruiter(1L, 50L)));
        when(outreachSequenceRepository.existsByRecruiterIdAndCandidateUserIdAndStatus(1L, 10L, OutreachSequence.STATUS_ACTIVE))
            .thenReturn(false);
        when(outreachSequenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Map<String, Object>> steps = List.of(
            Map.of("delayDays", 0, "message", "Hi there!"),
            Map.of("delayDays", 3, "message", "Following up"));

        OutreachSequence sequence = service.startSequence(1L, 10L, steps);

        verify(directMessageService).sendMessage(1L, 10L, 50L, "Hi there!");
        assertEquals(1, sequence.getCurrentStep());
        assertEquals(OutreachSequence.STATUS_ACTIVE, sequence.getStatus());
    }

    @Test
    void aSingleStepSequenceCompletesImmediately() {
        when(recruiterRepository.findById(1L)).thenReturn(Optional.of(recruiter(1L, 50L)));
        when(outreachSequenceRepository.existsByRecruiterIdAndCandidateUserIdAndStatus(1L, 10L, OutreachSequence.STATUS_ACTIVE))
            .thenReturn(false);
        when(outreachSequenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OutreachSequence sequence = service.startSequence(1L, 10L, List.of(Map.of("delayDays", 0, "message", "Hi!")));

        assertEquals(OutreachSequence.STATUS_COMPLETED, sequence.getStatus());
    }

    @Test
    void rejectsStartingASecondActiveSequenceForTheSameCandidate() {
        when(recruiterRepository.findById(1L)).thenReturn(Optional.of(recruiter(1L, 50L)));
        when(outreachSequenceRepository.existsByRecruiterIdAndCandidateUserIdAndStatus(1L, 10L, OutreachSequence.STATUS_ACTIVE))
            .thenReturn(true);

        assertThrows(OutreachSequenceService.SequenceAlreadyActiveException.class,
            () -> service.startSequence(1L, 10L, List.of(Map.of("delayDays", 0, "message", "Hi"))));
    }

    @Test
    void rejectsAnEmptyStepsList() {
        when(recruiterRepository.findById(1L)).thenReturn(Optional.of(recruiter(1L, 50L)));

        assertThrows(OutreachSequenceService.InvalidStepsException.class,
            () -> service.startSequence(1L, 10L, List.of()));
    }

    @Test
    void throwsForAnUnknownRecruiter() {
        when(recruiterRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(OutreachSequenceService.RecruiterNotFoundException.class,
            () -> service.startSequence(404L, 10L, List.of(Map.of("delayDays", 0, "message", "Hi"))));
    }

    private OutreachSequence activeSequence(long id, long recruiterId, long candidateUserId, String stepsJson,
                                             int currentStep, LocalDateTime lastStepSentAt) {
        OutreachSequence s = new OutreachSequence(recruiterId, candidateUserId, stepsJson);
        s.setId(id);
        s.setCurrentStep(currentStep);
        s.setLastStepSentAt(lastStepSentAt);
        return s;
    }

    @Test
    void advancesToTheNextStepOnceItsDelayHasElapsed() {
        OutreachSequence sequence = activeSequence(1L, 1L, 10L,
            "[{\"delayDays\":0,\"message\":\"Hi\"},{\"delayDays\":3,\"message\":\"Following up\"}]",
            1, LocalDateTime.now().minusDays(4));
        when(outreachSequenceRepository.findByStatus(OutreachSequence.STATUS_ACTIVE)).thenReturn(List.of(sequence));
        when(directMessageRepository.findByRecruiterIdAndCandidateUserIdOrderBySentAtAsc(1L, 10L)).thenReturn(List.of());
        when(recruiterRepository.findById(1L)).thenReturn(Optional.of(recruiter(1L, 50L)));
        when(outreachSequenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.processDueSteps();

        verify(directMessageService).sendMessage(1L, 10L, 50L, "Following up");
        assertEquals(2, sequence.getCurrentStep());
        assertEquals(OutreachSequence.STATUS_COMPLETED, sequence.getStatus());
    }

    @Test
    void doesNotAdvanceBeforeTheDelayHasElapsed() {
        OutreachSequence sequence = activeSequence(1L, 1L, 10L,
            "[{\"delayDays\":0,\"message\":\"Hi\"},{\"delayDays\":3,\"message\":\"Following up\"}]",
            1, LocalDateTime.now().minusHours(1));
        when(outreachSequenceRepository.findByStatus(OutreachSequence.STATUS_ACTIVE)).thenReturn(List.of(sequence));
        when(directMessageRepository.findByRecruiterIdAndCandidateUserIdOrderBySentAtAsc(1L, 10L)).thenReturn(List.of());

        service.processDueSteps();

        verify(directMessageService, never()).sendMessage(eq(1L), eq(10L), any(), any());
        assertEquals(1, sequence.getCurrentStep());
        verify(outreachSequenceRepository, never()).save(any());
    }

    @Test
    void marksTheSequenceRepliedWhenTheCandidateHasResponded() {
        LocalDateTime lastStep = LocalDateTime.now().minusDays(4);
        OutreachSequence sequence = activeSequence(1L, 1L, 10L,
            "[{\"delayDays\":0,\"message\":\"Hi\"},{\"delayDays\":3,\"message\":\"Following up\"}]",
            1, lastStep);
        DirectMessage reply = new DirectMessage(1L, 10L, 10L, 50L, "Sure, let's talk");
        reply.setSentAt(lastStep.plusHours(1));
        when(outreachSequenceRepository.findByStatus(OutreachSequence.STATUS_ACTIVE)).thenReturn(List.of(sequence));
        when(directMessageRepository.findByRecruiterIdAndCandidateUserIdOrderBySentAtAsc(1L, 10L)).thenReturn(List.of(reply));
        when(outreachSequenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.processDueSteps();

        verify(directMessageService, never()).sendMessage(eq(1L), eq(10L), any(), any());
        assertEquals(OutreachSequence.STATUS_REPLIED, sequence.getStatus());
    }

    @Test
    void stopsASequence() {
        OutreachSequence sequence = activeSequence(1L, 1L, 10L, "[]", 0, null);
        when(outreachSequenceRepository.findById(1L)).thenReturn(Optional.of(sequence));
        when(outreachSequenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.stopSequence(1L, 1L);

        assertEquals(OutreachSequence.STATUS_STOPPED, sequence.getStatus());
    }

    @Test
    void throwsWhenStoppingASequenceThatBelongsToAnotherRecruiter() {
        OutreachSequence sequence = activeSequence(1L, 2L, 10L, "[]", 0, null);
        when(outreachSequenceRepository.findById(1L)).thenReturn(Optional.of(sequence));

        assertThrows(OutreachSequenceService.SequenceNotFoundException.class, () -> service.stopSequence(1L, 1L));
    }

    @Test
    void listsSequencesWithTheCandidateNameResolved() {
        OutreachSequence sequence = activeSequence(1L, 1L, 10L,
            "[{\"delayDays\":0,\"message\":\"Hi\"}]", 1, LocalDateTime.now());
        when(outreachSequenceRepository.findByRecruiterIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(sequence));
        User candidate = new User();
        candidate.setId(10L);
        candidate.setFullName("Asha Patel");
        when(userRepository.findById(10L)).thenReturn(Optional.of(candidate));

        List<Map<String, Object>> results = service.listForRecruiter(1L);

        assertEquals("Asha Patel", results.get(0).get("candidateName"));
        assertEquals(1, results.get(0).get("totalSteps"));
    }
}
