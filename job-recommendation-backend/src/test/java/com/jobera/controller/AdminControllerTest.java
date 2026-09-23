package com.jobera.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

import jakarta.servlet.http.HttpServletRequest;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock UserRepository userRepository;
    @Mock RecruiterRepository recruiterRepository;
    @Mock JobPostingRepository jobPostingRepository;
    @Mock ApplicationRepository applicationRepository;
    @Mock ResumeRepository resumeRepository;
    @Mock AdminAuditService adminAuditService;

    AdminController controller;
    HttpServletRequest request;
    User admin;

    @BeforeEach
    void setUp() {
        controller = new AdminController(userRepository, recruiterRepository, jobPostingRepository,
            applicationRepository, resumeRepository, adminAuditService);
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
}
