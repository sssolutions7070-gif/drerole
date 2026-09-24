package com.jobera.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.jobera.config.AuthInterceptor;
import com.jobera.entity.RecruiterTeamMember;
import com.jobera.entity.User;
import com.jobera.service.RecruiterTeamService;

import jakarta.servlet.http.HttpServletRequest;

@ExtendWith(MockitoExtension.class)
class RecruiterTeamControllerTest {

    @Mock RecruiterTeamService recruiterTeamService;

    RecruiterTeamController controller;

    @BeforeEach
    void setUp() {
        controller = new RecruiterTeamController(recruiterTeamService);
    }

    private HttpServletRequest requestFor(long userId) {
        User user = new User();
        user.setId(userId);
        HttpServletRequest request = mock(HttpServletRequest.class);
        lenient().when(request.getAttribute(AuthInterceptor.AUTHENTICATED_USER)).thenReturn(user);
        return request;
    }

    @Test
    void listReturnsTheRoster() {
        when(recruiterTeamService.listMembers(100L)).thenReturn(List.of(Map.of("role", "OWNER")));

        ResponseEntity<?> response = controller.list(100L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void listReturns404WhenRecruiterMissing() {
        when(recruiterTeamService.listMembers(999L))
            .thenThrow(new RecruiterTeamService.RecruiterNotFoundException(999L));

        ResponseEntity<?> response = controller.list(999L);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void inviteReturnsTheNewMember() {
        RecruiterTeamMember member = new RecruiterTeamMember(100L, 2L, "ADMIN");
        when(recruiterTeamService.inviteMember(100L, 1L, "a@b.com", "ADMIN")).thenReturn(member);

        ResponseEntity<?> response = controller.invite(100L, Map.of("email", "a@b.com", "role", "ADMIN"), requestFor(1L));

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void inviteReturns403WhenCallerCannotManageTeam() {
        when(recruiterTeamService.inviteMember(100L, 5L, "a@b.com", "ADMIN"))
            .thenThrow(new RecruiterTeamService.NotAuthorizedToManageTeamException());

        ResponseEntity<?> response = controller.invite(100L, Map.of("email", "a@b.com", "role", "ADMIN"), requestFor(5L));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void inviteReturns404WhenUserNotFound() {
        when(recruiterTeamService.inviteMember(100L, 1L, "nobody@example.com", "ADMIN"))
            .thenThrow(new RecruiterTeamService.UserNotFoundException("nobody@example.com"));

        ResponseEntity<?> response = controller.invite(100L, Map.of("email", "nobody@example.com", "role", "ADMIN"), requestFor(1L));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void inviteReturns400ForAnInvalidRole() {
        when(recruiterTeamService.inviteMember(100L, 1L, "a@b.com", "BOSS"))
            .thenThrow(new RecruiterTeamService.InvalidTeamRoleException("BOSS"));

        ResponseEntity<?> response = controller.invite(100L, Map.of("email", "a@b.com", "role", "BOSS"), requestFor(1L));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void removeReturnsOkOnSuccess() {
        ResponseEntity<?> response = controller.remove(100L, 500L, requestFor(1L));

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void removeReturns400WhenRemovingSelf() {
        org.mockito.Mockito.doThrow(new RecruiterTeamService.CannotRemoveSelfException())
            .when(recruiterTeamService).removeMember(100L, 500L, 1L);

        ResponseEntity<?> response = controller.remove(100L, 500L, requestFor(1L));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }
}
