package com.jobera.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.jobera.entity.ApplicationComment;
import com.jobera.entity.User;
import com.jobera.repository.ApplicationCommentRepository;
import com.jobera.repository.ApplicationRepository;
import com.jobera.repository.UserRepository;

/** Collaborative hiring notes - a recruiter or any teammate on their account can leave a comment
 *  on an application, visible only to the hiring team (see AccessPolicy.recruiterManagesApplication). */
@Service
public class ApplicationCommentService {

    private final ApplicationCommentRepository commentRepository;
    private final ApplicationRepository applicationRepository;
    private final UserRepository userRepository;

    public ApplicationCommentService(ApplicationCommentRepository commentRepository,
                                      ApplicationRepository applicationRepository,
                                      UserRepository userRepository) {
        this.commentRepository = commentRepository;
        this.applicationRepository = applicationRepository;
        this.userRepository = userRepository;
    }

    public Map<String, Object> addComment(Long applicationId, Long authorUserId, String body) {
        if (!applicationRepository.existsById(applicationId)) {
            throw new ApplicationNotFoundException(applicationId);
        }
        if (body == null || body.isBlank()) {
            throw new BlankCommentException();
        }

        ApplicationComment comment = new ApplicationComment(applicationId, authorUserId, body.trim());
        return toView(commentRepository.save(comment));
    }

    public List<Map<String, Object>> getComments(Long applicationId) {
        if (!applicationRepository.existsById(applicationId)) {
            throw new ApplicationNotFoundException(applicationId);
        }
        return commentRepository.findByApplicationIdOrderByCreatedAtAsc(applicationId).stream()
            .map(this::toView)
            .toList();
    }

    public void deleteComment(Long applicationId, Long commentId, Long requestingUserId) {
        ApplicationComment comment = commentRepository.findById(commentId)
            .filter(c -> c.getApplicationId().equals(applicationId))
            .orElseThrow(() -> new CommentNotFoundException(commentId));
        if (!comment.getAuthorUserId().equals(requestingUserId)) {
            throw new NotTheAuthorException();
        }
        commentRepository.delete(comment);
    }

    private Map<String, Object> toView(ApplicationComment comment) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", comment.getId());
        view.put("applicationId", comment.getApplicationId());
        view.put("authorUserId", comment.getAuthorUserId());
        view.put("authorName", userRepository.findById(comment.getAuthorUserId())
            .map(User::getFullName).orElse("Unknown"));
        view.put("body", comment.getBody());
        view.put("createdAt", comment.getCreatedAt());
        return view;
    }

    public static class ApplicationNotFoundException extends RuntimeException {
        public ApplicationNotFoundException(Long id) {
            super("Application not found: " + id);
        }
    }

    public static class BlankCommentException extends RuntimeException {
        public BlankCommentException() {
            super("Comment cannot be empty");
        }
    }

    public static class CommentNotFoundException extends RuntimeException {
        public CommentNotFoundException(Long id) {
            super("Comment not found: " + id);
        }
    }

    public static class NotTheAuthorException extends RuntimeException {
        public NotTheAuthorException() {
            super("Only the comment's author can delete it");
        }
    }
}
