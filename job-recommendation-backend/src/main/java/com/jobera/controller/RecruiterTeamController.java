package com.jobera.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jobera.config.AuthInterceptor;
import com.jobera.entity.User;
import com.jobera.service.RecruiterTeamService;

import jakarta.servlet.http.HttpServletRequest;

/** Managing who's on a recruiter's (company's) hiring team - invited teammates get the same
 *  access as the account owner everywhere else in the app (see AccessPolicy.ownsRecruiterId),
 *  so this controller only covers the roster itself: who's on it, and adding/removing people. */
@RestController
@RequestMapping("/api/recruiters/{recruiterId}/team")
public class RecruiterTeamController {

    private static final String ERROR_KEY = "error";

    private final RecruiterTeamService recruiterTeamService;

    public RecruiterTeamController(RecruiterTeamService recruiterTeamService) {
        this.recruiterTeamService = recruiterTeamService;
    }

    private Long callingUserId(HttpServletRequest request) {
        return ((User) request.getAttribute(AuthInterceptor.AUTHENTICATED_USER)).getId();
    }

    @GetMapping
    public ResponseEntity<?> list(@PathVariable Long recruiterId) {
        try {
            return ResponseEntity.ok(recruiterTeamService.listMembers(recruiterId));
        } catch (RecruiterTeamService.RecruiterNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(ERROR_KEY, e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<?> invite(@PathVariable Long recruiterId, @RequestBody Map<String, String> payload,
                                     HttpServletRequest request) {
        try {
            return ResponseEntity.ok(recruiterTeamService.inviteMember(
                recruiterId, callingUserId(request), payload.get("email"), payload.get("role")));
        } catch (RecruiterTeamService.RecruiterNotFoundException | RecruiterTeamService.UserNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(ERROR_KEY, e.getMessage()));
        } catch (RecruiterTeamService.NotAuthorizedToManageTeamException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(ERROR_KEY, e.getMessage()));
        } catch (RecruiterTeamService.InvalidTeamRoleException | RecruiterTeamService.AlreadyOnTeamException e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, e.getMessage()));
        }
    }

    @DeleteMapping("/{membershipId}")
    public ResponseEntity<?> remove(@PathVariable Long recruiterId, @PathVariable Long membershipId,
                                     HttpServletRequest request) {
        try {
            recruiterTeamService.removeMember(recruiterId, membershipId, callingUserId(request));
            return ResponseEntity.ok().build();
        } catch (RecruiterTeamService.RecruiterNotFoundException | RecruiterTeamService.MemberNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(ERROR_KEY, e.getMessage()));
        } catch (RecruiterTeamService.NotAuthorizedToManageTeamException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(ERROR_KEY, e.getMessage()));
        } catch (RecruiterTeamService.CannotRemoveSelfException e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, e.getMessage()));
        }
    }
}
