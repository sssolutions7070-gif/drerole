package com.jobera.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jobera.entity.JobPosting;
import com.jobera.repository.JobPostingRepository;

@ExtendWith(MockitoExtension.class)
class SalaryInsightsServiceTest {

    @Mock JobPostingRepository jobPostingRepository;
    @Mock AiInterviewClient aiInterviewClient;

    SalaryInsightsService service;

    @BeforeEach
    void setUp() {
        service = new SalaryInsightsService(jobPostingRepository, aiInterviewClient);
    }

    private JobPosting posting(String title, String location, String experienceLevel, String salaryRange) {
        JobPosting job = new JobPosting(title, "Acme", "desc");
        job.setLocation(location);
        job.setExperienceLevel(experienceLevel);
        job.setSalaryRange(salaryRange);
        return job;
    }

    @Test
    void parsesADollarRangeWithCommas() {
        Optional<SalaryInsightsService.SalaryRange> range = SalaryInsightsService.parseRange("$80,000 - $120,000");

        assertTrue(range.isPresent());
        assertEquals(80000, range.get().min());
        assertEquals(120000, range.get().max());
    }

    @Test
    void parsesAKSuffixedRange() {
        Optional<SalaryInsightsService.SalaryRange> range = SalaryInsightsService.parseRange("90k-110k");

        assertTrue(range.isPresent());
        assertEquals(90000, range.get().min());
        assertEquals(110000, range.get().max());
    }

    @Test
    void treatsASingleNumberAsBothMinAndMax() {
        Optional<SalaryInsightsService.SalaryRange> range = SalaryInsightsService.parseRange("$95,000");

        assertTrue(range.isPresent());
        assertEquals(95000, range.get().min());
        assertEquals(95000, range.get().max());
    }

    @Test
    void returnsEmptyForNonNumericText() {
        assertFalse(SalaryInsightsService.parseRange("Competitive").isPresent());
        assertFalse(SalaryInsightsService.parseRange(null).isPresent());
        assertFalse(SalaryInsightsService.parseRange("").isPresent());
    }

    @Test
    void aggregatesMatchingPostingsByTitleLocationAndExperience() {
        List<JobPosting> postings = List.of(
            posting("Backend Engineer", "Remote", "Senior", "$100,000 - $140,000"),
            posting("Backend Engineer", "Remote", "Entry", "$60,000 - $80,000"),
            posting("Frontend Engineer", "Remote", "Senior", "$90,000 - $130,000"),
            posting("Backend Engineer", "New York", "Senior", "Competitive")
        );
        when(jobPostingRepository.findByIsActiveTrueAndModerationStatusOrderByPostedDateDesc(JobPosting.MODERATION_APPROVED))
            .thenReturn(postings);

        Map<String, Object> insights = service.getInsights("Backend", "Remote", null);

        assertEquals(2, insights.get("matchingPostings"));
        Map<String, Object> overall = (Map<String, Object>) insights.get("overall");
        assertEquals(2, overall.get("count"));
        assertEquals(60000L, overall.get("minSalary"));
        assertEquals(140000L, overall.get("maxSalary"));

        Map<String, Object> byLevel = (Map<String, Object>) insights.get("byExperienceLevel");
        assertTrue(byLevel.containsKey("Senior"));
        assertTrue(byLevel.containsKey("Entry"));
    }

    @Test
    void groupsDifferentlyCasedExperienceLevelsIntoOneBucket() {
        // JobPosting.setExperienceLevel canonicalizes casing, so postings written as "SENIOR"
        // by older/raw-API code and "Senior" by the job-post form land in the same bucket here.
        List<JobPosting> postings = List.of(
            posting("Backend Engineer", "Remote", "SENIOR", "$100,000 - $140,000"),
            posting("Backend Engineer", "Remote", "Senior", "$120,000 - $160,000")
        );
        when(jobPostingRepository.findByIsActiveTrueAndModerationStatusOrderByPostedDateDesc(JobPosting.MODERATION_APPROVED))
            .thenReturn(postings);

        Map<String, Object> insights = service.getInsights(null, null, null);

        Map<String, Object> byLevel = (Map<String, Object>) insights.get("byExperienceLevel");
        assertEquals(1, byLevel.size());
        Map<String, Object> senior = (Map<String, Object>) byLevel.get("Senior");
        assertEquals(2, senior.get("count"));
    }

    @Test
    void ignoresPostingsWithUnparseableSalaryRanges() {
        when(jobPostingRepository.findByIsActiveTrueAndModerationStatusOrderByPostedDateDesc(JobPosting.MODERATION_APPROVED))
            .thenReturn(List.of(posting("Designer", "Remote", "Mid", "Negotiable")));

        Map<String, Object> insights = service.getInsights(null, null, null);

        assertEquals(0, insights.get("matchingPostings"));
    }

    @Test
    void computesMedianAcrossAnOddAndEvenNumberOfPostings() {
        List<JobPosting> postings = List.of(
            posting("Analyst", "Remote", "Mid", "$50,000"),
            posting("Analyst", "Remote", "Mid", "$70,000"),
            posting("Analyst", "Remote", "Mid", "$90,000")
        );
        when(jobPostingRepository.findByIsActiveTrueAndModerationStatusOrderByPostedDateDesc(JobPosting.MODERATION_APPROVED))
            .thenReturn(postings);

        Map<String, Object> insights = service.getInsights("Analyst", null, null);
        Map<String, Object> overall = (Map<String, Object>) insights.get("overall");

        assertEquals(70000L, overall.get("medianSalary"));
    }

    @Test
    void negotiationAdvicePassesTheRealMarketNumbersAndParsedOfferToTheAiClient() {
        List<JobPosting> postings = List.of(
            posting("Backend Engineer", "Remote", "Senior", "$100,000 - $140,000"),
            posting("Backend Engineer", "Remote", "Senior", "$120,000 - $160,000")
        );
        when(jobPostingRepository.findByIsActiveTrueAndModerationStatusOrderByPostedDateDesc(JobPosting.MODERATION_APPROVED))
            .thenReturn(postings);
        Map<String, Object> aiResult = Map.of(
            "suggestedTargetMin", 120000.0, "suggestedTargetMax", 140000.0,
            "assessment", "Below market", "talkingPoints", List.of("Ask for more"), "openingLine", "Hi"
        );
        when(aiInterviewClient.generateNegotiationAdvice("Backend Engineer", 95000.0, 100000.0, 130000.0, 160000.0, 2))
            .thenReturn(aiResult);

        Map<String, Object> result = service.getNegotiationAdvice("Backend Engineer", "Remote", "Senior", "$95,000");

        assertEquals(120000.0, result.get("suggestedTargetMin"));
        assertEquals(95000.0, result.get("offeredSalary"));
        assertTrue(result.containsKey("marketData"));
    }

    @Test
    void negotiationAdviceWorksWithNoOfferedSalaryProvided() {
        when(jobPostingRepository.findByIsActiveTrueAndModerationStatusOrderByPostedDateDesc(JobPosting.MODERATION_APPROVED))
            .thenReturn(List.of());
        when(aiInterviewClient.generateNegotiationAdvice("Analyst", null, null, null, null, 0))
            .thenReturn(Map.of("suggestedTargetMin", 0.0, "suggestedTargetMax", 0.0, "assessment", "No data",
                "talkingPoints", List.of(), "openingLine", ""));

        Map<String, Object> result = service.getNegotiationAdvice("Analyst", null, null, null);

        assertEquals(null, result.get("offeredSalary"));
    }
}
