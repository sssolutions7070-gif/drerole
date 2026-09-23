package com.jobera.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.jobera.entity.SavedJob;

@Repository
public interface SavedJobRepository extends JpaRepository<SavedJob, Long> {
    List<SavedJob> findByUserIdOrderBySavedAtDesc(Long userId);

    Optional<SavedJob> findByUserIdAndJobPostingId(Long userId, Long jobPostingId);

    boolean existsByUserIdAndJobPostingId(Long userId, Long jobPostingId);

    // Spring Data derived delete methods aren't transactional by default the way inherited
    // save()/delete() are - this bit us once already with AuthTokenRepository (a live 500 on
    // logout), so every one of these gets @Transactional explicitly.
    @Transactional
    void deleteByUserIdAndJobPostingId(@Param("userId") Long userId, @Param("jobPostingId") Long jobPostingId);

    @Transactional
    void deleteByJobPostingId(@Param("jobPostingId") Long jobPostingId);

    @Transactional
    void deleteByUserId(@Param("userId") Long userId);
}
