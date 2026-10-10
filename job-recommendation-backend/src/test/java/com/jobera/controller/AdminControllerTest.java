package com.jobera.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import com.jobera.config.AuthInterceptor;
import com.jobera.entity.AdminAuditLog;
import com.jobera.entity.JobPosting;
import com.jobera.entity.Recruiter;
import com.jobera.entity.User;
import com.jobera.repository.ApplicationRepository;
import com.jobera.repository.JobPostingRepository;
import com.jobera.repository.RecruiterRepository;
import com.jobera.repository.ResumeRepository;
import com.jobera.repository.UserRepository;
import com.jobera.service.AdminAuditService;
import com.jobera.service.JobPostingService;
import com.jobera.service.NotificationService;

import jakarta.servlet.http.HttpServletRequest;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock UserRepository userRepository;
    @Mock RecruiterRepository recruiterRepository;
    @Mock JobPostingRepository jobPostingRepository;
    @Mock ApplicationRepository applicationRepository;
    @Mock ResumeRepository resumeRepository;
    @Mock AdminAuditService adminAuditService;
    @Mock JobPostingService jobPostingService;
    @Mock NotificationService notificationService;
    @Mock com.jobera.service.CompanyReviewService companyReviewService;
    @Mock com.jobera.service.InterviewExperienceService interviewExperienceService;

    AdminController controller;
    HttpServletRequest request;
    User admin;

    @BeforeEach
    void setUp() {
        controller = new AdminController(userRepository, recruiterRepository, jobPostingRepository,
            applicationRepository, resumeRepository, adminAuditService, jobPostingService, notificationService,
            companyReviewService, interviewExperienceService);
        admin = user(1L, "admin@example.com", 2L);
        request = mock(HttpServletRequest.class);
        lenient().when(request.getAttribute(AuthInterceptor.AUTHENTICATED_USER)).thenReturn(admin);
    }

    private User user(long id, String email, long roleId) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        u.setRoleId(roleId);
        u.setIsActive(true);
        return u;
    }

    @Test
    void deletingAUserLogsTheAudit() {
        User target = user(5L, "target@example.com", 1L);
        when(userRepository.findById(5L)).thenReturn(Optional.of(target));
        when(recruiterRepository.findByUserId(5L)).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.deleteUser(5L, request);

        assertEquals(200, response.getStatusCode().value());
        verify(userRepository).deleteById(5L);
        verify(adminAuditService).record(admin, "USER_DELETED", "USER", 5L, "target@example.com");
    }

    @Test
    void deletingAnAdminIsRejectedWithNoAuditEntry() {
        User target = user(5L, "otheradmin@example.com", 2L);
        when(userRepository.findById(5L)).thenReturn(Optional.of(target));

        ResponseEntity<?> response = controller.deleteUser(5L, request);

        assertEquals(403, response.getStatusCode().value());
        verify(userRepository, never()).deleteById(any());
        verify(adminAuditService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void deletingARecruiterAlsoDeletesItsUserAndLogsTheCompanyName() {
        User recruiterUser = user(6L, "rec@example.com", 3L);
        Recruiter recruiter = new Recruiter();
        recruiter.setId(9L);
        recruiter.setUser(recruiterUser);
        recruiter.setCompanyName("Acme Inc");
        when(recruiterRepository.findById(9L)).thenReturn(Optional.of(recruiter));

        ResponseEntity<?> response = controller.deleteRecruiter(9L, request);

        assertEquals(200, response.getStatusCode().value());
        verify(recruiterRepository).deleteById(9L);
        verify(userRepository).deleteById(6L);
        verify(adminAuditService).record(admin, "RECRUITER_DELETED", "RECRUITER", 9L, "Acme Inc");
    }

    @Test
    void togglingAUserOffThenOnIsLoggedWithDistinctActions() {
        User target = user(5L, "target@example.com", 1L);
        target.setIsActive(true);
        when(userRepository.findById(5L)).thenReturn(Optional.of(target));

        controller.toggleUserActive(5L, request);

        assertEquals(false, target.getIsActive());
        verify(adminAuditService).record(admin, "USER_DEACTIVATED", "USER", 5L, "target@example.com");
    }

    @Test
    void reactivatingADeactivatedUserIsLoggedAsReactivated() {
        User target = user(5L, "target@example.com", 1L);
        target.setIsActive(false);
        when(userRepository.findById(5L)).thenReturn(Optional.of(target));

        controller.toggleUserActive(5L, request);

        assertEquals(true, target.getIsActive());
        verify(adminAuditService).record(admin, "USER_REACTIVATED", "USER", 5L, "target@example.com");
    }

    @Test
    void cannotDeactivateAnAdmin() {
        User target = user(2L, "otheradmin@example.com", 2L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));

        ResponseEntity<?> response = controller.toggleUserActive(2L, request);

        assertEquals(403, response.getStatusCode().value());
        verify(userRepository, never()).save(any());
        verify(adminAuditService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void deletingAJobLogsItsTitle() {
        JobPosting job = new JobPosting();
        job.setId(7L);
        job.setTitle("Backend Developer");
        when(jobPostingRepository.findById(7L)).thenReturn(Optional.of(job));

        controller.deleteJob(7L, request);

        verify(jobPostingRepository).deleteById(7L);
        verify(adminAuditService).record(admin, "JOB_DELETED", "JOB", 7L, "Backend Developer");
    }

    @Test
    void togglingAJobActiveLogsActivatedOrDeactivated() {
        JobPosting job = new JobPosting();
        job.setId(7L);
        job.setTitle("Backend Developer");
        job.setIsActive(true);
        when(jobPostingRepository.findById(7L)).thenReturn(Optional.of(job));

        controller.toggleJobActive(7L, request);

        assertEquals(false, job.getIsActive());
        verify(adminAuditService).record(admin, "JOB_DEACTIVATED", "JOB", 7L, "Backend Developer");
    }

    @Test
    void auditLogEndpointMapsEntriesForTheResponse() {
        AdminAuditLog entry = new AdminAuditLog();
        entry.setId(1L);
        entry.setAdminEmail("admin@example.com");
        entry.setAction("JOB_DELETED");
        entry.setTargetType("JOB");
        entry.setTargetId(7L);
        entry.setTargetDescription("Backend Developer");
        when(adminAuditService.recent()).thenReturn(List.of(entry));

        ResponseEntity<List<java.util.Map<String, Object>>> response = controller.getAuditLog();

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().size());
        assertEquals("JOB_DELETED", response.getBody().get(0).get("action"));
        assertEquals("Backend Developer", response.getBody().get(0).get("targetDescription"));
    }

    // ---------------------------------------------------------------------------------------
    // CSV exports - a recruiter could already export their own applicants for one job; admins
    // had no equivalent way to pull users/jobs/applications platform-wide at all.
    // ---------------------------------------------------------------------------------------

    @Test
    void exportUsersCsvIncludesEveryUserAsARow() {
        User u = user(10L, "asha@example.com", 1L);
        u.setFullName("Asha, Rao"); // deliberately contains a comma to exercise CSV escaping
        when(userRepository.findAll()).thenReturn(List.of(u));

        ResponseEntity<byte[]> response = controller.exportUsersCsv();

        assertEquals(200, response.getStatusCode().value());
        String body = new String(response.getBody());
        assertTrue(body.startsWith("ID,Full Name,Email,Role,Created At,Last Login\n"));
        assertTrue(body.contains("\"Asha, Rao\""));
        assertTrue(body.contains("asha@example.com"));
        assertEquals("text/csv", response.getHeaders().getContentType().toString());
        assertTrue(response.getHeaders().getFirst("Content-Disposition").contains("users.csv"));
    }

    @Test
    void exportJobsCsvIncludesEveryJobAsARow() {
        JobPosting job = new JobPosting("Backend Engineer", "Acme Corp", "Build APIs");
        job.setId(3L);
        job.setLocation("Remote");
        job.setJobType("Full-time");
        job.setModerationStatus(JobPosting.MODERATION_APPROVED);
        job.setIsActive(true);
        when(jobPostingRepository.findAll()).thenReturn(List.of(job));

        ResponseEntity<byte[]> response = controller.exportJobsCsv();

        assertEquals(200, response.getStatusCode().value());
        String body = new String(response.getBody());
        assertTrue(body.contains("Backend Engineer"));
        assertTrue(body.contains("Acme Corp"));
        assertTrue(body.contains("Remote"));
        assertTrue(response.getHeaders().getFirst("Content-Disposition").contains("jobs.csv"));
    }

    @Test
    void exportApplicationsCsvIncludesEveryApplicationAsARow() {
        com.jobera.entity.Application application = new com.jobera.entity.Application();
        application.setId(42L);
        application.setApplicantName("Bob Smith");
        application.setApplicantEmail("bob@example.com");
        application.setStatus("SHORTLISTED");
        application.setMatchPercentage(88.0);
        JobPosting job = new JobPosting("Frontend Engineer", "Beta Inc", "Build UI");
        application.setJobPosting(job);
        when(applicationRepository.findAll()).thenReturn(List.of(application));

        ResponseEntity<byte[]> response = controller.exportApplicationsCsv();

        assertEquals(200, response.getStatusCode().value());
        String body = new String(response.getBody());
        assertTrue(body.contains("Bob Smith"));
        assertTrue(body.contains("Frontend Engineer"));
        assertTrue(body.contains("SHORTLISTED"));
        assertTrue(response.getHeaders().getFirst("Content-Disposition").contains("applications.csv"));
    }
}
