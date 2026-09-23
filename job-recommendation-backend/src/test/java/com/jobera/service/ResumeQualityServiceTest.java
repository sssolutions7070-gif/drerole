package com.jobera.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.jobera.entity.Resume;

class ResumeQualityServiceTest {

    private final ResumeQualityService service = new ResumeQualityService();

    private Resume resumeWithText(String text) {
        Resume resume = new Resume();
        resume.setParsedText(text);
        resume.setSkills("[\"Java\", \"Spring Boot\", \"MySQL\", \"React\"]");
        resume.setEducationLevel("Bachelor's");
        return resume;
    }

    @SuppressWarnings("unchecked")
    private boolean checkPassed(Map<String, Object> result, String checkId) {
        List<Map<String, Object>> checks = (List<Map<String, Object>>) result.get("checks");
        return checks.stream()
            .filter(c -> checkId.equals(c.get("id")))
            .findFirst()
            .map(c -> (boolean) c.get("passed"))
            .orElseThrow(() -> new AssertionError("No check with id " + checkId));
    }

    @Test
    void aWellRoundedResumeScoresHighAndPassesEveryCheck() {
        String text = "John Doe john.doe@example.com (555) 123-4567. "
            + "Led a team of 5 engineers and built a payment platform, improving checkout "
            + "conversion by 25%. Designed and implemented a caching layer that reduced average "
            + "response time by 40%. Developed and launched three internal tools, increasing "
            + "team productivity. Managed a migration that delivered a 30% cost reduction. "
            + "Architected a microservices platform serving 10 million requests per day. "
            + "Mentored two junior engineers and streamlined the on-call rotation. "
            + "Bachelor of Science in Computer Science. "
            + String.join(" ", java.util.Collections.nCopies(20, "additional relevant detail about the role and its scope."));

        Map<String, Object> result = service.analyze(resumeWithText(text));

        assertTrue((int) result.get("score") >= 80, "expected a high score, got " + result.get("score"));
        assertTrue(checkPassed(result, "contact_email"));
        assertTrue(checkPassed(result, "contact_phone"));
        assertTrue(checkPassed(result, "skills_listed"));
        assertTrue(checkPassed(result, "education"));
        assertTrue(checkPassed(result, "quantified_achievements"));
        assertTrue(checkPassed(result, "weak_phrases"));
        assertTrue(checkPassed(result, "action_verbs"));
    }

    @Test
    void flagsMissingContactInformation() {
        Resume resume = resumeWithText("Just some resume text with no way to reach this person at all, "
            + "padded out to a reasonable length so the length check alone doesn't fail: "
            + String.join(" ", java.util.Collections.nCopies(30, "word")));

        Map<String, Object> result = service.analyze(resume);

        assertFalse(checkPassed(result, "contact_email"));
        assertFalse(checkPassed(result, "contact_phone"));
    }

    @Test
    void flagsPassivePhrasingOverActionVerbs() {
        Resume resume = resumeWithText("john@example.com (555) 123-4567. "
            + "Responsible for the backend systems. Duties included maintaining servers. "
            + "Worked on various projects as tasked with by management. "
            + String.join(" ", java.util.Collections.nCopies(30, "word")));

        Map<String, Object> result = service.analyze(resume);

        assertFalse(checkPassed(result, "weak_phrases"));
        assertFalse(checkPassed(result, "action_verbs"));
    }

    @Test
    void flagsAResumeWithNoNumbersAsUnquantified() {
        // Deliberately no phone number here either - it would contain digits and defeat the point.
        Resume resume = resumeWithText("john@example.com. Led the engineering team and "
            + "built a great platform, improved performance and delivered many features. "
            + String.join(" ", java.util.Collections.nCopies(30, "word")));

        Map<String, Object> result = service.analyze(resume);

        assertFalse(checkPassed(result, "quantified_achievements"));
    }

    @Test
    void flagsAResumeThatIsTooShort() {
        Resume resume = resumeWithText("Short resume.");

        Map<String, Object> result = service.analyze(resume);

        assertFalse(checkPassed(result, "length"));
        assertEquals(2, result.get("wordCount"));
    }

    @Test
    void flagsFewOrNoSkillsListed() {
        Resume resume = resumeWithText(String.join(" ", java.util.Collections.nCopies(200, "word")));
        resume.setSkills("[\"Java\"]");

        Map<String, Object> result = service.analyze(resume);

        assertFalse(checkPassed(result, "skills_listed"));
    }

    @Test
    void handlesAResumeWithNoParsedTextAtAllWithoutCrashing() {
        Resume resume = new Resume();

        Map<String, Object> result = service.analyze(resume);

        assertEquals(0, result.get("wordCount"));
        assertFalse(checkPassed(result, "contact_email"));
    }
}
