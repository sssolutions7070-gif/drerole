package com.jobera.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.jobera.entity.RecruiterTeamMember;

@Repository
public interface RecruiterTeamMemberRepository extends JpaRepository<RecruiterTeamMember, Long> {
    boolean existsByRecruiterIdAndUserId(Long recruiterId, Long userId);
    Optional<RecruiterTeamMember> findByRecruiterIdAndUserId(Long recruiterId, Long userId);
    List<RecruiterTeamMember> findByRecruiterIdOrderByInvitedAtAsc(Long recruiterId);
    Optional<RecruiterTeamMember> findByIdAndRecruiterId(Long id, Long recruiterId);
}
