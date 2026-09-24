package com.jobera.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.jobera.service.SalaryInsightsService;

@ExtendWith(MockitoExtension.class)
class SalaryInsightsControllerTest {

    @Mock SalaryInsightsService salaryInsightsService;

    SalaryInsightsController controller;

    @BeforeEach
    void setUp() {
        controller = new SalaryInsightsController(salaryInsightsService);
    }

    @Test
    void returnsInsightsFromTheService() {
        Map<String, Object> stats = Map.of("matchingPostings", 5);
        when(salaryInsightsService.getInsights("Backend", "Remote", "Senior")).thenReturn(stats);

        ResponseEntity<Map<String, Object>> response = controller.getInsights("Backend", "Remote", "Senior");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(5, response.getBody().get("matchingPostings"));
    }

    @Test
    void worksWithNoFiltersAtAll() {
        when(salaryInsightsService.getInsights(null, null, null)).thenReturn(Map.of("matchingPostings", 0));

        ResponseEntity<Map<String, Object>> response = controller.getInsights(null, null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(salaryInsightsService).getInsights(null, null, null);
    }
}
