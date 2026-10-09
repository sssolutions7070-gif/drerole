package com.jobera.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.jobera.entity.Application;

class SkillGapAnalyzerTest {

    private Application withMissingSkills(String json) {
        Application application = new Application();
        application.setMissingSkills(json);
        return application;
    }

    @Test
    void countsEachSkillOnceAcrossApplicationsAndSortsByFrequency() {
        List<Application> applications = List.of(
            withMissingSkills("[\"Docker\", \"Kubernetes\"]"),
            withMissingSkills("[\"Docker\", \"AWS\"]"),
            withMissingSkills("[\"Docker\"]")
        );

        List<Map<String, Object>> result = SkillGapAnalyzer.topMissingSkills(applications, 10);

        assertEquals("Docker", result.get(0).get("skill"));
        assertEquals(3, result.get(0).get("count"));
    }

    @Test
    void deduplicatesSkillsCaseInsensitivelyButKeepsTheFirstSeenCasingForDisplay() {
        List<Application> applications = List.of(
            withMissingSkills("[\"docker\"]"),
            withMissingSkills("[\"Docker\"]"),
            withMissingSkills("[\"DOCKER\"]")
        );

        List<Map<String, Object>> result = SkillGapAnalyzer.topMissingSkills(applications, 10);

        assertEquals(1, result.size());
        assertEquals("docker", result.get(0).get("skill"));
        assertEquals(3, result.get(0).get("count"));
    }

    @Test
    void respectsTheTopNLimit() {
        List<Application> applications = List.of(
            withMissingSkills("[\"A\", \"B\", \"C\"]")
        );

        List<Map<String, Object>> result = SkillGapAnalyzer.topMissingSkills(applications, 2);

        assertEquals(2, result.size());
    }

    @Test
    void ignoresApplicationsWithNoMissingSkillsRecorded() {
        Application noSkillsData = new Application(); // missingSkills left null
        Application emptyArray = withMissingSkills("[]");
        Application malformedJson = withMissingSkills("not valid json");

        List<Map<String, Object>> result = SkillGapAnalyzer.topMissingSkills(
            List.of(noSkillsData, emptyArray, malformedJson), 10);

        assertTrue(result.isEmpty());
    }

    @Test
    void returnsNothingForAnEmptyApplicationList() {
        assertTrue(SkillGapAnalyzer.topMissingSkills(List.of(), 10).isEmpty());
    }
}
