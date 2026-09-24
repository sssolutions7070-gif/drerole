package com.jobera.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class JobPostingTest {

    @Test
    void setExperienceLevelCanonicalizesKnownVariants() {
        JobPosting job = new JobPosting("Title", "Company", "desc");

        job.setExperienceLevel("SENIOR");
        assertEquals("Senior", job.getExperienceLevel());

        job.setExperienceLevel("mid_level");
        assertEquals("Mid-level", job.getExperienceLevel());

        job.setExperienceLevel("Mid-Level");
        assertEquals("Mid-level", job.getExperienceLevel());

        job.setExperienceLevel("entry level");
        assertEquals("Entry", job.getExperienceLevel());

        job.setExperienceLevel("Junior");
        assertEquals("Junior", job.getExperienceLevel());
    }

    @Test
    void setExperienceLevelLeavesUnrecognizedValuesUntouchedButTrimmed() {
        JobPosting job = new JobPosting("Title", "Company", "desc");

        job.setExperienceLevel("  Staff Engineer  ");

        assertEquals("Staff Engineer", job.getExperienceLevel());
    }

    @Test
    void setExperienceLevelPassesThroughNullAndBlank() {
        JobPosting job = new JobPosting("Title", "Company", "desc");

        job.setExperienceLevel(null);
        assertNull(job.getExperienceLevel());

        job.setExperienceLevel("");
        assertEquals("", job.getExperienceLevel());
    }

    @Test
    void canonicalizeExperienceLevelIsCaseAndSeparatorInsensitive() {
        assertEquals("Senior", JobPosting.canonicalizeExperienceLevel("senior"));
        assertEquals("Senior", JobPosting.canonicalizeExperienceLevel("SENIOR"));
        assertEquals("Mid-level", JobPosting.canonicalizeExperienceLevel("MID-LEVEL"));
        assertEquals("Mid-level", JobPosting.canonicalizeExperienceLevel("midlevel"));
        assertEquals("Principal", JobPosting.canonicalizeExperienceLevel("principal"));
    }
}
