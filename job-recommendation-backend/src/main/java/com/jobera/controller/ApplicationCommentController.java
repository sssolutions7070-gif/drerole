package com.jobera.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.jobera.config.AuthInterceptor;
import com.jobera.entity.User;
import com.jobera.service.ApplicationCommentService;

import jakarta.servlet.http.HttpServletRequest;

/** Collaborative hiring comments, recruiter-team-only - gated by AccessPolicy's
 *  recruiterManagesApplication() so a candidate never sees their own internal notes. */
@RestController
public class ApplicationCommentController {

    private static final String ERROR_KEY = "error";

    private final ApplicationCommentService commentService;

    public ApplicationCommentController(ApplicationCommentService commentService) {
        this.commentService = commentService;
    }

    private User callingUser(HttpServletRequest request) {
        return (User) request.getAttribute(AuthInterceptor.AUTHENTICATED_USER);
    }

    @PostMapping("/api/applications/{applicationId}/comments")
    public ResponseEntity<?> addComment(@PathVariable Long applicationId,
                                         @RequestBody Map<String, String> payload,
                                         HttpServletRequest request) {
        try {
            Map<String, Object> comment = commentService.addComment(applicationId, callingUser(request).getId(),
                payload.get("body"));
            return ResponseEntity.status(HttpStatus.CREATED).body(comment);
        } catch (ApplicationCommentService.ApplicationNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (ApplicationCommentService.BlankCommentException e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, e.getMessage()));
        }
    }

    @GetMapping("/api/applications/{applicationId}/comments")
    public ResponseEntity<?> getComments(@PathVariable Long applicationId) {
        try {
            List<Map<String, Object>> comments = commentService.getComments(applicationId);
            return ResponseEntity.ok(comments);
        } catch (ApplicationCommentService.ApplicationNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/api/applications/{applicationId}/comments/{commentId}")
    public ResponseEntity<?> deleteComment(@PathVariable Long applicationId, @PathVariable Long commentId,
                                            HttpServletRequest request) {
        try {
            commentService.deleteComment(applicationId, commentId, callingUser(request).getId());
            return ResponseEntity.noContent().build();
        } catch (ApplicationCommentService.CommentNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (ApplicationCommentService.NotTheAuthorException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(ERROR_KEY, e.getMessage()));
        }
    }
}
