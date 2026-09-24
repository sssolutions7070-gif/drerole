package com.jobera.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jobera.entity.SkillBadge;
import com.jobera.repository.SkillBadgeRepository;

@ExtendWith(MockitoExtension.class)
class SkillBadgeServiceTest {

    @Mock SkillBadgeRepository skillBadgeRepository;

    SkillBadgeService service;

    @BeforeEach
    void setUp() {
        service = new SkillBadgeService(skillBadgeRepository);
    }

    @Test
    void awardingANewBadgeCreatesIt() {
        when(skillBadgeRepository.findByUserIdAndSourceTypeAndSourceTestId(1L, "APTITUDE_TEST", 5L))
            .thenReturn(Optional.empty());
        when(skillBadgeRepository.save(any(SkillBadge.class))).thenAnswer(inv -> inv.getArgument(0));

        SkillBadge badge = service.awardBadge(1L, SkillBadge.SOURCE_APTITUDE_TEST, 5L, "Logic Test", 85);

        assertEquals(1L, badge.getUserId());
        assertEquals("Logic Test", badge.getBadgeName());
        assertEquals(85, badge.getScore());
    }

    @Test
    void reEarningTheSameBadgeUpdatesTheScoreInPlaceRatherThanDuplicating() {
        SkillBadge existing = new SkillBadge(1L, SkillBadge.SOURCE_APTITUDE_TEST, 5L, "Logic Test", 60);
        existing.setId(99L);
        when(skillBadgeRepository.findByUserIdAndSourceTypeAndSourceTestId(1L, "APTITUDE_TEST", 5L))
            .thenReturn(Optional.of(existing));
        when(skillBadgeRepository.save(any(SkillBadge.class))).thenAnswer(inv -> inv.getArgument(0));

        SkillBadge updated = service.awardBadge(1L, SkillBadge.SOURCE_APTITUDE_TEST, 5L, "Logic Test", 90);

        assertEquals(99L, updated.getId());
        assertEquals(90, updated.getScore());
    }

    @Test
    void listingBadgesDelegatesToTheRepository() {
        SkillBadge badge = new SkillBadge(1L, SkillBadge.SOURCE_DREAM_ROLE_TEST, 7L, "Frontend Mastery", 95);
        when(skillBadgeRepository.findByUserIdOrderByEarnedAtDesc(1L)).thenReturn(List.of(badge));

        List<SkillBadge> badges = service.listBadges(1L);

        assertEquals(1, badges.size());
    }
}
