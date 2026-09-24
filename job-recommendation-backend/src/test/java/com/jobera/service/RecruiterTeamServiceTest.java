package com.jobera.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jobera.entity.Recruiter;
import com.jobera.entity.RecruiterTeamMember;
import com.jobera.entity.User;
import com.jobera.repository.RecruiterRepository;
import com.jobera.repository.RecruiterTeamMemberRepository;
import com.jobera.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class RecruiterTeamServiceTest {

    @Mock RecruiterRepository recruiterRepository;
    @Mock RecruiterTeamMemberRepository teamMemberRepository;
    @Mock UserRepository userRepository;

    RecruiterTeamService service;

    User owner;
    Recruiter recruiter;

    @BeforeEach
    void setUp() {
        service = new RecruiterTeamService(recruiterRepository, teamMemberRepository, userRepository);
        owner = new User();
        owner.setId(1L);
        owner.setRoleId(3L);
        recruiter = new Recruiter();
        recruiter.setId(100L);
        recruiter.setUser(owner);
        lenient().when(recruiterRepository.findById(100L)).thenReturn(Optional.of(recruiter));
    }

    private User user(long id) {
        User u = new User();
        u.setId(id);
        u.setEmail("teammate" + id + "@example.com");
        u.setFullName("Teammate " + id);
        u.setRoleId(1L);
        return u;
    }

    @Test
    void ownerCanInviteATeammate() {
        User invitee = user(2L);
        when(userRepository.findByEmailIgnoreCase("teammate2@example.com")).thenReturn(Optional.of(invitee));
        when(teamMemberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RecruiterTeamMember member = service.inviteMember(100L, 1L, "teammate2@example.com", "HIRING_MANAGER");

        assertEquals(2L, member.getUserId());
        assertEquals("HIRING_MANAGER", member.getRole());
        verify(userRepository).save(invitee);
        assertEquals(3L, invitee.getRoleId());
    }

    @Test
    void nonOwnerNonAdminCannotInvite() {
        when(teamMemberRepository.findByRecruiterIdAndUserId(100L, 5L)).thenReturn(
            Optional.of(new RecruiterTeamMember(100L, 5L, RecruiterTeamMember.ROLE_INTERVIEWER)));

        assertThrows(RecruiterTeamService.NotAuthorizedToManageTeamException.class,
            () -> service.inviteMember(100L, 5L, "someone@example.com", "INTERVIEWER"));
    }

    @Test
    void teamAdminCanInvite() {
        when(teamMemberRepository.findByRecruiterIdAndUserId(100L, 5L)).thenReturn(
            Optional.of(new RecruiterTeamMember(100L, 5L, RecruiterTeamMember.ROLE_ADMIN)));
        User invitee = user(2L);
        when(userRepository.findByEmailIgnoreCase("teammate2@example.com")).thenReturn(Optional.of(invitee));
        when(teamMemberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RecruiterTeamMember member = service.inviteMember(100L, 5L, "teammate2@example.com", "INTERVIEWER");

        assertEquals("INTERVIEWER", member.getRole());
    }

    @Test
    void invitingAnUnknownEmailFails() {
        when(userRepository.findByEmailIgnoreCase("nobody@example.com")).thenReturn(Optional.empty());

        assertThrows(RecruiterTeamService.UserNotFoundException.class,
            () -> service.inviteMember(100L, 1L, "nobody@example.com", "ADMIN"));
    }

    @Test
    void invitingTheOwnerAgainFails() {
        when(userRepository.findByEmailIgnoreCase("owner@example.com")).thenReturn(Optional.of(owner));
        owner.setEmail("owner@example.com");

        assertThrows(RecruiterTeamService.AlreadyOnTeamException.class,
            () -> service.inviteMember(100L, 1L, "owner@example.com", "ADMIN"));
    }

    @Test
    void invitingAnExistingMemberAgainFails() {
        User invitee = user(2L);
        when(userRepository.findByEmailIgnoreCase("teammate2@example.com")).thenReturn(Optional.of(invitee));
        when(teamMemberRepository.existsByRecruiterIdAndUserId(100L, 2L)).thenReturn(true);

        assertThrows(RecruiterTeamService.AlreadyOnTeamException.class,
            () -> service.inviteMember(100L, 1L, "teammate2@example.com", "ADMIN"));
    }

    @Test
    void invalidRoleIsRejected() {
        assertThrows(RecruiterTeamService.InvalidTeamRoleException.class,
            () -> service.inviteMember(100L, 1L, "teammate2@example.com", "SUPERUSER"));
    }

    @Test
    void listMembersIncludesTheOwnerAndInvitedMembers() {
        RecruiterTeamMember membership = new RecruiterTeamMember(100L, 2L, "HIRING_MANAGER");
        membership.setId(500L);
        when(teamMemberRepository.findByRecruiterIdOrderByInvitedAtAsc(100L)).thenReturn(List.of(membership));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L)));

        List<Map<String, Object>> members = service.listMembers(100L);

        assertEquals(2, members.size());
        assertEquals("OWNER", members.get(0).get("role"));
        assertEquals("HIRING_MANAGER", members.get(1).get("role"));
    }

    @Test
    void ownerCanRemoveAMember() {
        RecruiterTeamMember membership = new RecruiterTeamMember(100L, 2L, "INTERVIEWER");
        membership.setId(500L);
        when(teamMemberRepository.findByIdAndRecruiterId(500L, 100L)).thenReturn(Optional.of(membership));

        service.removeMember(100L, 500L, 1L);

        verify(teamMemberRepository).delete(membership);
    }

    @Test
    void cannotRemoveYourself() {
        RecruiterTeamMember membership = new RecruiterTeamMember(100L, 1L, "INTERVIEWER");
        membership.setId(500L);
        when(teamMemberRepository.findByIdAndRecruiterId(500L, 100L)).thenReturn(Optional.of(membership));

        assertThrows(RecruiterTeamService.CannotRemoveSelfException.class,
            () -> service.removeMember(100L, 500L, 1L));
        verify(teamMemberRepository, never()).delete(any());
    }

    @Test
    void recruiterNotFoundIsReported() {
        when(recruiterRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(RecruiterTeamService.RecruiterNotFoundException.class,
            () -> service.listMembers(999L));
    }
}
