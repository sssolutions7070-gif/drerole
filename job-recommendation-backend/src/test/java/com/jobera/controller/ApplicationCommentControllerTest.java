package com.jobera.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.springframework.mock.web.MockHttpServletRequest;

import com.jobera.config.AuthInterceptor;
import com.jobera.entity.User;
import com.jobera.service.ApplicationCommentService;

@ExtendWith(MockitoExtension.class)
class ApplicationCommentControllerTest {

    @Mock ApplicationCommentService commentService;

    ApplicationCommentController controller;

    @BeforeEach
    void setUp() {
        controller = new ApplicationCommentController(commentService);
    }

    private MockHttpServletRequest requestFor(Long userId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        User user = new User();
        user.setId(userId);
        request.setAttribute(AuthInterceptor.AUTHENTICATED_USER, user);
        return request;
    }

    @Test
    void addsACommentOnBehalfOfTheCallingUser() {
        when(commentService.addComment(1L, 10L, "Great candidate"))
            .thenReturn(Map.of("id", 99L, "authorName", "Priya"));

        ResponseEntity<?> response = controller.addComment(1L, Map.of("body", "Great candidate"), requestFor(10L));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("Priya", ((Map<?, ?>) response.getBody()).get("authorName"));
    }

    @Test
    void returnsNotFoundForAnUnknownApplicationOnAdd() {
        when(commentService.addComment(404L, 10L, "x"))
            .thenThrow(new ApplicationCommentService.ApplicationNotFoundException(404L));

        ResponseEntity<?> response = controller.addComment(404L, Map.of("body", "x"), requestFor(10L));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void rejectsABlankCommentBody() {
        when(commentService.addComment(1L, 10L, " "))
            .thenThrow(new ApplicationCommentService.BlankCommentException());

        ResponseEntity<?> response = controller.addComment(1L, Map.of("body", " "), requestFor(10L));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void listsCommentsForAnApplication() {
        when(commentService.getComments(1L)).thenReturn(List.of(Map.of("id", 1L, "body", "note")));

        ResponseEntity<?> response = controller.getComments(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, ((List<?>) response.getBody()).size());
    }

    @Test
    void deletesACommentAsItsAuthor() {
        ResponseEntity<?> response = controller.deleteComment(1L, 5L, requestFor(10L));

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test
    void refusesToDeleteSomeoneElsesComment() {
        org.mockito.Mockito.doThrow(new ApplicationCommentService.NotTheAuthorException())
            .when(commentService).deleteComment(1L, 5L, 20L);

        ResponseEntity<?> response = controller.deleteComment(1L, 5L, requestFor(20L));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }
}
