package com.jobera.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobera.entity.Application;

/**
 * Aggregates the {@code missingSkills} JSON array every {@link Application} already carries
 * (set at apply time by the resume matcher) into a "skills candidates most often lack" report.
 * That field has always been recorded per-application but, until now, never rolled up anywhere -
 * each application only ever showed its own gap, one candidate at a time.
 */
public final class SkillGapAnalyzer {

    private SkillGapAnalyzer() {
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Top {@code topN} most frequently missing skills across {@code applications}, as
     *  {@code [{"skill": "...", "count": N}, ...]} sorted by count descending. Skill names are
     *  deduplicated case-insensitively (so "Docker" and "docker" count as one) but displayed
     *  using whichever casing was seen first. */
    public static List<Map<String, Object>> topMissingSkills(List<Application> applications, int topN) {
        Map<String, String> displayNameByKey = new LinkedHashMap<>();
        Map<String, Integer> countByKey = new LinkedHashMap<>();

        for (Application application : applications) {
            for (String skill : parseSkillList(application.getMissingSkills())) {
                if (skill == null || skill.isBlank()) continue;
                String trimmed = skill.trim();
                String key = trimmed.toLowerCase();
                displayNameByKey.putIfAbsent(key, trimmed);
                countByKey.merge(key, 1, Integer::sum);
            }
        }

        return countByKey.entrySet().stream()
            .sorted((a, b) -> b.getValue() - a.getValue())
            .limit(topN)
            .map(entry -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("skill", displayNameByKey.get(entry.getKey()));
                row.put("count", entry.getValue());
                return row;
            })
            .collect(Collectors.toList());
    }

    /** Parses a {@code matchedSkills}/{@code missingSkills}-shaped JSON array column into a
     *  plain list, tolerating null/blank/malformed input - exposed publicly so callers that need
     *  a single application's own skill list (not an aggregate) don't have to duplicate this. */
    @SuppressWarnings("unchecked")
    public static List<String> parseSkillList(String skillsJson) {
        if (skillsJson == null || skillsJson.isBlank()) {
            return List.of();
        }
        try {
            Object parsed = MAPPER.readValue(skillsJson, Object.class);
            if (parsed instanceof List<?> list) {
                return (List<String>) list.stream().map(String::valueOf).collect(Collectors.toList());
            }
            return List.of();
        } catch (Exception e) {
            return List.of();
        }
    }
}
