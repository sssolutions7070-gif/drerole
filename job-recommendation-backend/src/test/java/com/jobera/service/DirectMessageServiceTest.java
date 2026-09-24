package com.jobera.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
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
import com.jobera.entity.Recruiter;
import com.jobera.entity.User;
import com.jobera.repository.DirectMessageRepository;
import com.jobera.repository.RecruiterRepository;
import com.jobera.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class DirectMessageServiceTest {

    @Mock DirectMessageRepository directMessageRepository;
    @Mock RecruiterRepository recruiterRepository;
    @Mock UserRepository userRepository;
    @Mock RealtimeNotifier realtimeNotifier;
    @Mock NotificationFeedService notificationFeedService;

    DirectMessageService service;

    User recruiterOwner;
    User candidate;
    Recruiter recruiter;

    @BeforeEach
    void setUp() {
        service = new DirectMessageService(directMessageRepository, recruiterRepository, userRepository,
            realtimeNotifier, notificationFeedService);

        recruiterOwner = new User();
        recruiterOwner.setId(1L);
        recruiterOwner.setFullName("Owner");

        candidate = new User();
        candidate.setId(50L);
        candidate.setFullName("Candidate");

        recruiter = new Recruiter();
        recruiter.setId(100L);
        recruiter.setUser(recruiterOwner);
        recruiter.setCompanyName("Acme");

        lenient().when(recruiterRepository.findById(100L)).thenReturn(Optional.of(recruiter));
        lenient().when(userRepository.findById(50L)).thenReturn(Optional.of(candidate));
        lenient().when(directMessageRepository.save(any())).thenAnswer(inv -> {
            DirectMessage m = inv.getArgument(0);
            m.setId(500L);
            m.setSentAt(LocalDateTime.now());
            return m;
        });
    }

    @Test
    void recruiterSendingAMessageNotifiesTheCandidate() {
        DirectMessage saved = service.sendMessage(100L, 50L, 1L, "Interested in chatting?");

        assertEquals(1L, saved.getSenderId());
        assertEquals(50L, saved.getRecipientId());
        verify(realtimeNotifier).notifyUser(50L, "NEW_MESSAGE", Map.of(
            "recruiterId", 100L, "candidateUserId", 50L, "preview", "Interested in chatting?"));
        verify(notificationFeedService).record(50L, "NEW_MESSAGE", "New message", "Interested in chatting?", "/dashboard");
    }

    @Test
    void candidateReplyingNotifiesTheRecruiterOwner() {
        DirectMessage saved = service.sendMessage(100L, 50L, 50L, "Sure, tell me more");

        assertEquals(50L, saved.getSenderId());
        assertEquals(1L, saved.getRecipientId());
        verify(realtimeNotifier).notifyUser(1L, "NEW_MESSAGE", Map.of(
            "recruiterId", 100L, "candidateUserId", 50L, "preview", "Sure, tell me more"));
    }

    @Test
    void sendingToAnUnknownRecruiterFails() {
        when(recruiterRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(DirectMessageService.RecruiterNotFoundException.class,
            () -> service.sendMessage(999L, 50L, 1L, "hi"));
    }

    @Test
    void sendingToAnUnknownCandidateFails() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(DirectMessageService.CandidateNotFoundException.class,
            () -> service.sendMessage(100L, 999L, 1L, "hi"));
    }

    @Test
    void getThreadReturnsMessagesInOrder() {
        DirectMessage m1 = new DirectMessage(100L, 50L, 1L, 50L, "hi");
        when(directMessageRepository.findByRecruiterIdAndCandidateUserIdOrderBySentAtAsc(100L, 50L))
            .thenReturn(List.of(m1));

        List<DirectMessage> thread = service.getThread(100L, 50L);

        assertEquals(1, thread.size());
    }

    @Test
    void listThreadsForRecruiterReturnsOneRowPerCandidate() {
        DirectMessage newest = new DirectMessage(100L, 50L, 1L, 50L, "latest");
        newest.setSentAt(LocalDateTime.now());
        DirectMessage older = new DirectMessage(100L, 50L, 50L, 1L, "older");
        older.setSentAt(LocalDateTime.now().minusDays(1));
        when(directMessageRepository.findByRecruiterIdOrderBySentAtDesc(100L)).thenReturn(List.of(newest, older));

        List<Map<String, Object>> threads = service.listThreadsForRecruiter(100L);

        assertEquals(1, threads.size());
        assertEquals("Candidate", threads.get(0).get("candidateName"));
        assertEquals("latest", threads.get(0).get("lastMessage"));
    }

    @Test
    void listThreadsForCandidateReturnsOneRowPerRecruiter() {
        DirectMessage message = new DirectMessage(100L, 50L, 1L, 50L, "hello");
        message.setSentAt(LocalDateTime.now());
        when(directMessageRepository.findByCandidateUserIdOrderBySentAtDesc(50L)).thenReturn(List.of(message));

        List<Map<String, Object>> threads = service.listThreadsForCandidate(50L);

        assertEquals(1, threads.size());
        assertEquals("Acme", threads.get(0).get("companyName"));
    }
}
