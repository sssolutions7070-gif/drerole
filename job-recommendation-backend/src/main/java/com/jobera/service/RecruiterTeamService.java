package com.jobera.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.jobera.entity.Recruiter;
import com.jobera.entity.RecruiterTeamMember;
import com.jobera.entity.User;
import com.jobera.repository.RecruiterRepository;
import com.jobera.repository.RecruiterTeamMemberRepository;
import com.jobera.repository.UserRepository;

/** Lets a recruiter (company) account bring teammates onto its own pipeline - hiring managers and
 *  interviewers who share the same jobs/applications/interviews instead of everyone needing their
 *  own separate company account. AccessPolicy.ownsRecruiterId is the one place every existing
 *  recruiter-owned-resource check in the app funnels through, so a team member added here
 *  automatically gets the same access as the owner everywhere else, with no other route changes. */
@Service
public class RecruiterTeamService {

    private static final Set<String> VALID_ROLES = Set.of(
        RecruiterTeamMember.ROLE_ADMIN, RecruiterTeamMember.ROLE_HIRING_MANAGER, RecruiterTeamMember.ROLE_INTERVIEWER);
    private static final long ROLE_RECRUITER = 3L;

    private final RecruiterRepository recruiterRepository;
    private final RecruiterTeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;

    public RecruiterTeamService(RecruiterRepository recruiterRepository,
                                 RecruiterTeamMemberRepository teamMemberRepository,
                                 UserRepository userRepository) {
        this.recruiterRepository = recruiterRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.userRepository = userRepository;
    }

    public static class RecruiterNotFoundException extends RuntimeException {
        public RecruiterNotFoundException(Long recruiterId) {
            super("Recruiter not found: " + recruiterId);
        }
    }

    public static class NotAuthorizedToManageTeamException extends RuntimeException {
        public NotAuthorizedToManageTeamException() {
            super("Only the account owner or a team admin can manage the team");
        }
    }

    public static class UserNotFoundException extends RuntimeException {
        public UserNotFoundException(String email) {
            super("No DreamRole account found for " + email + " - they need to sign up first");
        }
    }

    public static class AlreadyOnTeamException extends RuntimeException {
        public AlreadyOnTeamException() {
            super("This person is already the owner or a member of this team");
        }
    }

    public static class InvalidTeamRoleException extends RuntimeException {
        public InvalidTeamRoleException(String role) {
            super("Unknown team role: " + role);
        }
    }

    public static class MemberNotFoundException extends RuntimeException {
        public MemberNotFoundException(Long memberId) {
            super("Team member not found: " + memberId);
        }
    }

    public static class CannotRemoveSelfException extends RuntimeException {
        public CannotRemoveSelfException() {
            super("Remove yourself by leaving the team from your own account, not by removing another member");
        }
    }

    private Recruiter requireRecruiter(Long recruiterId) {
        return recruiterRepository.findById(recruiterId)
            .orElseThrow(() -> new RecruiterNotFoundException(recruiterId));
    }

    /** The owner (Recruiter.user) can always manage the team; among invited members, only ADMIN can. */
    private void requireCanManageTeam(Recruiter recruiter, Long callerId) {
        if (recruiter.getUser() != null && recruiter.getUser().getId().equals(callerId)) {
            return;
        }
        boolean isTeamAdmin = teamMemberRepository.findByRecruiterIdAndUserId(recruiter.getId(), callerId)
            .map(m -> RecruiterTeamMember.ROLE_ADMIN.equals(m.getRole()))
            .orElse(false);
        if (!isTeamAdmin) {
            throw new NotAuthorizedToManageTeamException();
        }
    }

    public RecruiterTeamMember inviteMember(Long recruiterId, Long callerId, String email, String role) {
        Recruiter recruiter = requireRecruiter(recruiterId);
        requireCanManageTeam(recruiter, callerId);

        if (role == null || !VALID_ROLES.contains(role)) {
            throw new InvalidTeamRoleException(role);
        }

        if (email == null || email.isBlank()) {
            throw new UserNotFoundException(String.valueOf(email));
        }
        User invitee = userRepository.findByEmailIgnoreCase(email.trim())
            .orElseThrow(() -> new UserNotFoundException(email));

        boolean isOwner = recruiter.getUser() != null && recruiter.getUser().getId().equals(invitee.getId());
        boolean alreadyMember = teamMemberRepository.existsByRecruiterIdAndUserId(recruiterId, invitee.getId());
        if (isOwner || alreadyMember) {
            throw new AlreadyOnTeamException();
        }

        if (!Long.valueOf(ROLE_RECRUITER).equals(invitee.getRoleId())) {
            invitee.setRoleId(ROLE_RECRUITER);
            userRepository.save(invitee);
        }

        return teamMemberRepository.save(new RecruiterTeamMember(recruiterId, invitee.getId(), role));
    }

    public List<Map<String, Object>> listMembers(Long recruiterId) {
        Recruiter recruiter = requireRecruiter(recruiterId);
        List<Map<String, Object>> members = new java.util.ArrayList<>();

        if (recruiter.getUser() != null) {
            members.add(memberView(null, recruiter.getUser(), "OWNER", null));
        }
        for (RecruiterTeamMember member : teamMemberRepository.findByRecruiterIdOrderByInvitedAtAsc(recruiterId)) {
            userRepository.findById(member.getUserId())
                .ifPresent(user -> members.add(memberView(member.getId(), user, member.getRole(), member.getInvitedAt())));
        }
        return members;
    }

    private Map<String, Object> memberView(Long membershipId, User user, String role, Object invitedAt) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("membershipId", membershipId);
        view.put("userId", user.getId());
        view.put("fullName", user.getFullName());
        view.put("email", user.getEmail());
        view.put("role", role);
        view.put("invitedAt", invitedAt);
        return view;
    }

    public void removeMember(Long recruiterId, Long membershipId, Long callerId) {
        Recruiter recruiter = requireRecruiter(recruiterId);
        requireCanManageTeam(recruiter, callerId);

        RecruiterTeamMember member = teamMemberRepository.findByIdAndRecruiterId(membershipId, recruiterId)
            .orElseThrow(() -> new MemberNotFoundException(membershipId));
        if (member.getUserId().equals(callerId)) {
            throw new CannotRemoveSelfException();
        }
        teamMemberRepository.delete(member);
    }
}
