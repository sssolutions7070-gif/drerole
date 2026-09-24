package com.jobera.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.jobera.entity.SkillBadge;
import com.jobera.repository.SkillBadgeRepository;

/** Awards a shareable badge when a candidate passes an aptitude or Dream Role test - reusable
 *  proof of that skill across every future application, instead of re-testing every time a
 *  recruiter asks. Only ever called internally from the test-submission flows; there is no
 *  user-facing "create a badge" endpoint. */
@Service
public class SkillBadgeService {

    private final SkillBadgeRepository skillBadgeRepository;

    public SkillBadgeService(SkillBadgeRepository skillBadgeRepository) {
        this.skillBadgeRepository = skillBadgeRepository;
    }

    /** Called only when the attempt passed. A later re-attempt overwrites the score/date on the
     *  same badge rather than creating a duplicate. */
    public SkillBadge awardBadge(Long userId, String sourceType, Long sourceTestId, String badgeName, Integer score) {
        SkillBadge badge = skillBadgeRepository.findByUserIdAndSourceTypeAndSourceTestId(userId, sourceType, sourceTestId)
            .orElseGet(() -> new SkillBadge(userId, sourceType, sourceTestId, badgeName, score));
        badge.setBadgeName(badgeName);
        badge.setScore(score);
        badge.setEarnedAt(LocalDateTime.now());
        return skillBadgeRepository.save(badge);
    }

    public List<SkillBadge> listBadges(Long userId) {
        return skillBadgeRepository.findByUserIdOrderByEarnedAtDesc(userId);
    }
}
