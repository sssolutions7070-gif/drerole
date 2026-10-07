package com.jobera.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jobera.entity.Application;
import com.jobera.entity.JobPosting;
import com.jobera.entity.Recruiter;
import com.jobera.entity.User;
import com.jobera.entity.UserSkill;
import com.jobera.repository.ApplicationRepository;
import com.jobera.repository.UserSkillRepository;

@ExtendWith(MockitoExtension.class)
class SilverMedalistServiceTest {

    @Mock ApplicationRepository applicationRepository;
    @Mock UserSkillRepository userSkillRepository;
    @Mock NotificationFeedService notificationFeedService;
    @Mock RealtimeNotifier realtimeNotifier;

    SilverMedalistService service;

    @BeforeEach
    void setUp() {
        service = new SilverMedalistService(applicationRepository, userSkillRepository,
            notificationFeedService, realtimeNotifier);
    }

    private JobPosting jobWithSkills(Long id, Long recruiterId, String required, String preferred) {
        Recruiter recruiter = new Recruiter();
        recruiter.setId(recruiterId);
        JobPosting job = new JobPosting();
        job.setId(id);
        job.setTitle("Backend Developer");
        job.setCompany("Acme");
        job.setRequiredSkills(required);
        job.setPreferredSkills(preferred);
        job.setRecruiter(recruiter);
        return job;
    }

    private Application pastMiss(Long candidateId, String status) {
        User user = new User();
        user.setId(candidateId);
        Application application = new Application();
        application.setUser(user);
        application.setStatus(status);
        return application;
    }

    private UserSkill skill(String name) {
        UserSkill s = new UserSkill();
        s.setSkillName(name);
        return s;
    }

    @Test
    void notifiesAPastRejectedCandidateWhoseSkillsMatchWell() {
        JobPosting job = jobWithSkills(1L, 10L, "java,spring,sql", "docker");
        when(applicationRepository.findByJobPostingId(1L)).thenReturn(List.of());
        when(applicationRepository.findByRecruiterIdAndStatusIn(eq(10L), any()))
            .thenReturn(List.of(pastMiss(50L, "REJECTED")));
        when(userSkillRepository.findByUserId(50L))
            .thenReturn(List.of(skill("Java"), skill("Spring"), skill("SQL")));

        int notified = service.notifyMatchingPastCandidates(job);

        assertEquals(1, notified);
        verify(notificationFeedService).record(eq(50L), eq("SILVER_MEDALIST_MATCH"), any(), any(), eq("/jobs/1?source=silver_medalist"));
        verify(realtimeNotifier).notifyUser(eq(50L), eq("SILVER_MEDALIST_MATCH"), any());
    }

    @Test
    void doesNotNotifyACandidateBelowTheMatchThreshold() {
        JobPosting job = jobWithSkills(1L, 10L, "java,spring,sql,aws,kubernetes", null);
        when(applicationRepository.findByJobPostingId(1L)).thenReturn(List.of());
        when(applicationRepository.findByRecruiterIdAndStatusIn(eq(10L), any()))
            .thenReturn(List.of(pastMiss(50L, "REJECTED")));
        when(userSkillRepository.findByUserId(50L)).thenReturn(List.of(skill("Java")));

        int notified = service.notifyMatchingPastCandidates(job);

        assertEquals(0, notified);
        verifyNoInteractions(notificationFeedService, realtimeNotifier);
    }

    @Test
    void doesNotNotifyACandidateWhoAlreadyAppliedToTheNewJob() {
        JobPosting job = jobWithSkills(1L, 10L, "java,spring", null);
        Application alreadyApplied = pastMiss(50L, "APPLICATION_SUBMITTED");
        when(applicationRepository.findByJobPostingId(1L)).thenReturn(List.of(alreadyApplied));
        when(applicationRepository.findByRecruiterIdAndStatusIn(eq(10L), any()))
            .thenReturn(List.of(pastMiss(50L, "REJECTED")));

        int notified = service.notifyMatchingPastCandidates(job);

        assertEquals(0, notified);
        verify(userSkillRepository, never()).findByUserId(anyLong());
        verifyNoInteractions(notificationFeedService, realtimeNotifier);
    }

    @Test
    void aJobWithNoParseableSkillsNotifiesNoOne() {
        JobPosting job = jobWithSkills(1L, 10L, null, "");

        int notified = service.notifyMatchingPastCandidates(job);

        assertEquals(0, notified);
        verifyNoInteractions(applicationRepository, notificationFeedService, realtimeNotifier);
    }

    @Test
    void aRecruiterWithNoPastNearMissesNotifiesNoOne() {
        JobPosting job = jobWithSkills(1L, 10L, "java", null);
        when(applicationRepository.findByJobPostingId(1L)).thenReturn(List.of());
        when(applicationRepository.findByRecruiterIdAndStatusIn(eq(10L), any())).thenReturn(List.of());

        int notified = service.notifyMatchingPastCandidates(job);

        assertEquals(0, notified);
        verifyNoInteractions(notificationFeedService, realtimeNotifier);
    }
}
