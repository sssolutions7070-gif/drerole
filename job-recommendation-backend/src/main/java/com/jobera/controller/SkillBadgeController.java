package com.jobera.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jobera.entity.SkillBadge;
import com.jobera.service.SkillBadgeService;

/** A candidate's earned skill badges - deliberately public (AuthInterceptor.PUBLIC_GET_PATTERNS),
 *  same as a public company profile: a badge only has value as something a candidate can show a
 *  recruiter without them needing to already have an account or a relationship to that candidate. */
@RestController
@RequestMapping("/api/users/{userId}/badges")
public class SkillBadgeController {

    private final SkillBadgeService skillBadgeService;

    public SkillBadgeController(SkillBadgeService skillBadgeService) {
        this.skillBadgeService = skillBadgeService;
    }

    @GetMapping
    public ResponseEntity<List<SkillBadge>> list(@PathVariable Long userId) {
        return ResponseEntity.ok(skillBadgeService.listBadges(userId));
    }
}
