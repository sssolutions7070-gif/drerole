package com.jobera.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.jobera.service.DiversityAnalyticsService;
import com.jobera.service.RecruiterService;

/** Only the new diversity-analytics endpoint - this controller's other routes are covered by
 *  RecruiterControllerBrandingTest. */
@ExtendWith(MockitoExtension.class)
class RecruiterControllerDiversityAnalyticsTest {

    @Mock RecruiterService recruiterService;
    @Mock DiversityAnalyticsService diversityAnalyticsService;

    RecruiterController controller;

    @BeforeEach
    void setUp() {
        controller = new RecruiterController(recruiterService, diversityAnalyticsService);
    }

    @Test
    void returnsTheAggregateFromTheService() {
        when(diversityAnalyticsService.getAggregateForRecruiter(5L))
            .thenReturn(Map.of("totalApplicants", 12));

        ResponseEntity<Map<String, Object>> response = controller.getDiversityAnalytics(5L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(12, response.getBody().get("totalApplicants"));
    }
}
