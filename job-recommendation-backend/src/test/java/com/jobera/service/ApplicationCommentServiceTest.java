package com.jobera.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jobera.entity.ApplicationComment;
import com.jobera.entity.User;
import com.jobera.repository.ApplicationCommentRepository;
import com.jobera.repository.ApplicationRepository;
import com.jobera.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class ApplicationCommentServiceTest {

    @Mock ApplicationCommentRepository commentRepository;
    @Mock ApplicationRepository applicationRepository;
    @Mock UserRepository userRepository;

    ApplicationCommentService service;

    @BeforeEach
    void setUp() {
        service = new ApplicationCommentService(commentRepository, applicationRepository, userRepository);
    }

    private User user(long id, String name) {
        User u = new User();
        u.setId(id);
        u.setFullName(name);
        return u;
    }

    private ApplicationComment comment(long id, long applicationId, long authorId, String body) {
        ApplicationComment c = new ApplicationComment(applicationId, authorId, body);
        c.setId(id);
        return c;
    }

    @Test
    void addsACommentAndResolvesTheAuthorName() {
        when(applicationRepository.existsById(1L)).thenReturn(true);
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(10L, "Priya Sharma")));
        when(commentRepository.save(org.mockito.ArgumentMatchers.any(ApplicationComment.class)))
            .thenAnswer(invocation -> {
                ApplicationComment c = invocation.getArgument(0);
                c.setId(99L);
                return c;
            });

        Map<String, Object> result = service.addComment(1L, 10L, "Strong on system design.");

        assertEquals("Priya Sharma", result.get("authorName"));
        assertEquals("Strong on system design.", result.get("body"));
        assertEquals(99L, result.get("id"));
    }

    @Test
    void rejectsABlankComment() {
        when(applicationRepository.existsById(1L)).thenReturn(true);

        assertThrows(ApplicationCommentService.BlankCommentException.class,
            () -> service.addComment(1L, 10L, "   "));
    }

    @Test
    void rejectsACommentOnAnUnknownApplication() {
        when(applicationRepository.existsById(404L)).thenReturn(false);

        assertThrows(ApplicationCommentService.ApplicationNotFoundException.class,
            () -> service.addComment(404L, 10L, "hello"));
    }

    @Test
    void listsCommentsOldestFirstWithAuthorNames() {
        when(applicationRepository.existsById(1L)).thenReturn(true);
        when(commentRepository.findByApplicationIdOrderByCreatedAtAsc(1L))
            .thenReturn(List.of(comment(1, 1, 10, "First"), comment(2, 1, 20, "Second")));
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(10L, "Priya")));
        when(userRepository.findById(20L)).thenReturn(Optional.of(user(20L, "Dev")));

        List<Map<String, Object>> results = service.getComments(1L);

        assertEquals(2, results.size());
        assertEquals("Priya", results.get(0).get("authorName"));
        assertEquals("Dev", results.get(1).get("authorName"));
    }

    @Test
    void fallsBackToUnknownWhenTheAuthorNoLongerExists() {
        when(applicationRepository.existsById(1L)).thenReturn(true);
        when(commentRepository.findByApplicationIdOrderByCreatedAtAsc(1L))
            .thenReturn(List.of(comment(1, 1, 999, "Orphaned")));
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        List<Map<String, Object>> results = service.getComments(1L);

        assertEquals("Unknown", results.get(0).get("authorName"));
    }

    @Test
    void deletesACommentWhenRequestedByItsAuthor() {
        ApplicationComment c = comment(5, 1, 10, "to delete");
        when(commentRepository.findById(5L)).thenReturn(Optional.of(c));

        service.deleteComment(1L, 5L, 10L);

        verify(commentRepository).delete(c);
    }

    @Test
    void refusesToDeleteSomeoneElsesComment() {
        ApplicationComment c = comment(5, 1, 10, "not yours");
        when(commentRepository.findById(5L)).thenReturn(Optional.of(c));

        assertThrows(ApplicationCommentService.NotTheAuthorException.class,
            () -> service.deleteComment(1L, 5L, 20L));
        verify(commentRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsDeletingACommentThatBelongsToADifferentApplication() {
        ApplicationComment c = comment(5, 1, 10, "wrong app");
        when(commentRepository.findById(5L)).thenReturn(Optional.of(c));

        assertThrows(ApplicationCommentService.CommentNotFoundException.class,
            () -> service.deleteComment(2L, 5L, 10L));
    }
}
