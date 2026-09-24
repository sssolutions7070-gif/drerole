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

import com.jobera.service.PlatformAnalyticsService;

@ExtendWith(MockitoExtension.class)
class AdminAnalyticsControllerTest {

    @Mock PlatformAnalyticsService platformAnalyticsService;

    AdminAnalyticsController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminAnalyticsController(platformAnalyticsService);
    }

    @Test
    void overviewReturnsTheServiceResult() {
        when(platformAnalyticsService.getOverview()).thenReturn(Map.of("users", Map.of("total", 5L)));

        ResponseEntity<Map<String, Object>> response = controller.overview();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(Map.of("total", 5L), response.getBody().get("users"));
    }
}
