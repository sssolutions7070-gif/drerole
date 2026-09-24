package com.jobera.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.jobera.entity.SkillBadge;

@Repository
public interface SkillBadgeRepository extends JpaRepository<SkillBadge, Long> {
    List<SkillBadge> findByUserIdOrderByEarnedAtDesc(Long userId);
    Optional<SkillBadge> findByUserIdAndSourceTypeAndSourceTestId(Long userId, String sourceType, Long sourceTestId);
}
