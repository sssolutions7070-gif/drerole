package com.jobera.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jobera.entity.Application;
import com.jobera.entity.JobPosting;
import com.jobera.entity.Message;
import com.jobera.entity.Recruiter;
import com.jobera.entity.User;
import com.jobera.repository.ApplicationRepository;
import com.jobera.repository.MessageRepository;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock MessageRepository messageRepository;
    @Mock ApplicationRepository applicationRepository;
    @Mock RealtimeNotifier realtimeNotifier;
    @Mock NotificationFeedService notificationFeedService;

    MessageService service;

    User candidate;
    User recruiterUser;
    Application application;

    @BeforeEach
    void setUp() {
        service = new MessageService(messageRepository, applicationRepository, realtimeNotifier, notificationFeedService);

        candidate = new User("candidate@example.com", "hash", "Candidate");
        candidate.setId(1L);

        recruiterUser = new User("recruiter@example.com", "hash", "Recruiter");
        recruiterUser.setId(2L);

        Recruiter recruiter = new Recruiter();
        recruiter.setId(10L);
        recruiter.setUser(recruiterUser);

        JobPosting job = new JobPosting();
        job.setId(100L);
        job.setRecruiter(recruiter);

        application = new Application();
        application.setId(1000L);
        application.setUser(candidate);
        application.setJobPosting(job);
    }

    @Test
    void candidateCanMessageTheRecruiterBehindTheirApplication() {
        when(applicationRepository.findById(1000L)).thenReturn(Optional.of(application));
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> {
            Message m = inv.getArgument(0);
            m.setId(1L);
            return m;
        });

        Message result = service.sendMessage(1000L, candidate.getId(), "Hi, when's the interview?");

        assertEquals(candidate.getId(), result.getSenderId());
        assertEquals(recruiterUser.getId(), result.getRecipientId());
        verify(realtimeNotifier).notifyUser(eq(recruiterUser.getId()), eq("NEW_MESSAGE"), any());
    }

    @Test
    void recruiterCanReplyToTheCandidate() {
        when(applicationRepository.findById(1000L)).thenReturn(Optional.of(application));
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));

        Message result = service.sendMessage(1000L, recruiterUser.getId(), "Sure, how about Friday?");

        assertEquals(recruiterUser.getId(), result.getSenderId());
        assertEquals(candidate.getId(), result.getRecipientId());
        verify(realtimeNotifier).notifyUser(eq(candidate.getId()), eq("NEW_MESSAGE"), any());
    }

    @Test
    void aThirdPartyCannotSendOnThisThread() {
        when(applicationRepository.findById(1000L)).thenReturn(Optional.of(application));

        assertThrows(MessageService.NotAParticipantException.class,
            () -> service.sendMessage(1000L, 999L, "I shouldn't be able to send this"));

        verify(messageRepository, never()).save(any());
        verify(realtimeNotifier, never()).notifyUser(anyLong(), any(), any());
    }

    @Test
    void sendingToANonExistentApplicationFails() {
        when(applicationRepository.findById(1000L)).thenReturn(Optional.empty());

        assertThrows(MessageService.ApplicationNotFoundException.class,
            () -> service.sendMessage(1000L, candidate.getId(), "hello?"));
    }

    @Test
    void markThreadReadDelegatesToTheRepositoryWithACurrentTimestamp() {
        service.markThreadRead(1000L, candidate.getId());

        verify(messageRepository).markThreadRead(eq(1000L), eq(candidate.getId()), any(LocalDateTime.class));
    }

    @Test
    void getUnreadCountDelegatesToTheRepository() {
        when(messageRepository.countByRecipientIdAndReadAtIsNull(candidate.getId())).thenReturn(3L);

        assertEquals(3L, service.getUnreadCount(candidate.getId()));
    }
}
