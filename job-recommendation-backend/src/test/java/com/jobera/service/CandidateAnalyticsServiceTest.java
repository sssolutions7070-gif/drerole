package com.jobera.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jobera.entity.Application;
import com.jobera.repository.ApplicationRepository;
import com.jobera.repository.ProfileViewRepository;

@ExtendWith(MockitoExtension.class)
class CandidateAnalyticsServiceTest {

    @Mock ApplicationRepository applicationRepository;
    @Mock ProfileViewRepository profileViewRepository;

    CandidateAnalyticsService service;

    @BeforeEach
    void setUp() {
        service = new CandidateAnalyticsService(applicationRepository, profileViewRepository);
    }

    private Application application(String status, Double matchPercentage, String missingSkills) {
        Application application = new Application();
        application.setStatus(status);
        application.setMatchPercentage(matchPercentage);
        application.setMissingSkills(missingSkills);
        return application;
    }

    @Test
    void breaksDownACandidatesOwnApplicationsByStatus() {
        when(applicationRepository.findByUserId(1L)).thenReturn(List.of(
            application("SHORTLISTED", 80.0, null),
            application("SHORTLISTED", 70.0, null),
            application("REJECTED", 40.0, null)
        ));
        when(profileViewRepository.countByViewedUserId(1L)).thenReturn(0L);

        Map<String, Object> insights = service.getInsights(1L);

        assertEquals(3, insights.get("totalApplications"));
        @SuppressWarnings("unchecked")
        Map<String, Long> byStatus = (Map<String, Long>) insights.get("byStatus");
        assertEquals(2L, byStatus.get("SHORTLISTED"));
        assertEquals(1L, byStatus.get("REJECTED"));
    }

    @Test
    void averagesTheCandidatesOwnMatchPercentage() {
        when(applicationRepository.findByUserId(1L)).thenReturn(List.of(
            application("APPLICATION_SUBMITTED", 60.0, null),
            application("APPLICATION_SUBMITTED", 80.0, null)
        ));
        when(profileViewRepository.countByViewedUserId(1L)).thenReturn(0L);

        Map<String, Object> insights = service.getInsights(1L);

        assertEquals(70.0, (double) insights.get("averageMatchPercentage"), 0.1);
    }

    @Test
    void rollsUpTheCandidatesOwnMissingSkillsAcrossTheirApplications() {
        when(applicationRepository.findByUserId(1L)).thenReturn(List.of(
            application("APPLICATION_SUBMITTED", 60.0, "[\"Docker\", \"AWS\"]"),
            application("REJECTED", 40.0, "[\"Docker\"]")
        ));
        when(profileViewRepository.countByViewedUserId(1L)).thenReturn(0L);

        Map<String, Object> insights = service.getInsights(1L);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> topMissingSkills = (List<Map<String, Object>>) insights.get("topMissingSkills");
        assertEquals("Docker", topMissingSkills.get(0).get("skill"));
        assertEquals(2, topMissingSkills.get(0).get("count"));
    }

    @Test
    void includesHowManyRecruitersHaveViewedTheProfile() {
        when(applicationRepository.findByUserId(1L)).thenReturn(List.of());
        when(profileViewRepository.countByViewedUserId(1L)).thenReturn(7L);

        Map<String, Object> insights = service.getInsights(1L);

        assertEquals(7L, insights.get("profileViewCount"));
    }

    @Test
    void handlesACandidateWithNoApplicationsGracefully() {
        when(applicationRepository.findByUserId(1L)).thenReturn(List.of());
        when(profileViewRepository.countByViewedUserId(1L)).thenReturn(0L);

        Map<String, Object> insights = service.getInsights(1L);

        assertEquals(0, insights.get("totalApplications"));
        assertEquals(null, insights.get("averageMatchPercentage"));
    }
}
