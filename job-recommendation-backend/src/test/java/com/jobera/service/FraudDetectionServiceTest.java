package com.jobera.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.jobera.entity.JobPosting;

class FraudDetectionServiceTest {

    private final FraudDetectionService service = new FraudDetectionService();

    private JobPosting job(String title, String description, String company, String salaryRange) {
        JobPosting job = new JobPosting();
        job.setId(1L);
        job.setTitle(title);
        job.setDescription(description);
        job.setCompany(company);
        job.setSalaryRange(salaryRange);
        return job;
    }

    @Test
    void aClearlyLegitimatePostingScoresLow() {
        JobPosting job = job("Backend Engineer", "Build and maintain our Java/Spring APIs. 3+ years experience required.",
            "Acme Corp", "$90,000 - $120,000");

        Map<String, Object> result = service.scan(job);

        assertEquals(0, result.get("riskScore"));
        assertEquals(FraudDetectionService.RISK_LOW, result.get("riskLevel"));
        assertTrue(((List<?>) result.get("flags")).isEmpty());
    }

    @Test
    void flagsAnUpfrontFeeRequest() {
        JobPosting job = job("Work from home", "Pay a small registration fee to get started today!",
            "Quick Cash LLC", null);

        Map<String, Object> result = service.scan(job);

        assertTrue((Integer) result.get("riskScore") > 0);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> flags = (List<Map<String, Object>>) result.get("flags");
        assertTrue(flags.stream().anyMatch(f -> f.get("reason").toString().contains("pay a fee")));
    }

    @Test
    void flagsCryptoAndWireTransferLanguage() {
        JobPosting job = job("Remote assistant", "We'll wire transfer your setup bonus in bitcoin once you start.",
            "Global Remote Jobs", null);

        Map<String, Object> result = service.scan(job);

        assertEquals(FraudDetectionService.RISK_HIGH, result.get("riskLevel"));
    }

    @Test
    void flagsMlmLanguage() {
        JobPosting job = job("Be your own boss", "Join our team and recruit your friends for unlimited earning potential!",
            "FreedomBiz", null);

        Map<String, Object> result = service.scan(job);

        assertTrue((Integer) result.get("riskScore") >= 25);
    }

    @Test
    void flagsHighPayPromisedForNoExperience() {
        JobPosting job = job("Easy money", "No experience needed, earn $5000 per week starting immediately!",
            "FastCash", null);

        Map<String, Object> result = service.scan(job);

        assertTrue((Integer) result.get("riskScore") > 0);
    }

    @Test
    void flagsOffPlatformContactRequests() {
        JobPosting job = job("Data entry", "Interested? Text this number or message us on Telegram to proceed.",
            "QuickHire", null);

        Map<String, Object> result = service.scan(job);

        assertTrue((Integer) result.get("riskScore") > 0);
    }

    @Test
    void capsTheScoreAtOneHundred() {
        JobPosting job = job("Pyramid easy money", "registration fee, processing fee, wire transfer, bitcoin, "
            + "bank account number, pyramid, be your own boss, guaranteed income, telegram, no interview needed",
            "Scammy Co", null);

        Map<String, Object> result = service.scan(job);

        assertEquals(100, result.get("riskScore"));
    }

    @Test
    void scansAllJobsKeyedById() {
        JobPosting legit = job("Backend Engineer", "Build APIs", "Acme", "$100k");
        legit.setId(1L);
        JobPosting scam = job("Easy money", "Pay a registration fee", "Scam Co", null);
        scam.setId(2L);

        Map<Long, Map<String, Object>> results = service.scanAll(List.of(legit, scam));

        assertEquals(0, results.get(1L).get("riskScore"));
        assertTrue((Integer) results.get(2L).get("riskScore") > 0);
    }
}
