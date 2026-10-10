package com.jobera.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import com.jobera.service.MockInterviewService;

/** Only the new /history endpoint - MockInterviewController's existing start/list/get/answer
 *  endpoints have no prior test coverage to extend. */
@ExtendWith(MockitoExtension.class)
class MockInterviewControllerHistoryTest {

    @Mock MockInterviewService mockInterviewService;

    MockInterviewController controller;

    @BeforeEach
    void setUp() {
        controller = new MockInterviewController(mockInterviewService);
    }

    @Test
    void returnsTheAggregatedHistoryForTheGivenUser() {
        Map<String, Object> expected = Map.of("totalSessions", 3, "overallAverageScore", 72.5);
        when(mockInterviewService.getPracticeHistory(10L)).thenReturn(expected);

        ResponseEntity<Map<String, Object>> response = controller.history(10L);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(expected, response.getBody());
    }
}
