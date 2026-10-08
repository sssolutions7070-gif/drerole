package com.jobera.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.jobera.entity.ProfileView;

@Repository
public interface ProfileViewRepository extends JpaRepository<ProfileView, Long> {
    Optional<ProfileView> findByRecruiterIdAndViewedUserId(Long recruiterId, Long viewedUserId);

    List<ProfileView> findByViewedUserIdOrderByViewedAtDesc(Long viewedUserId);

    long countByViewedUserId(Long viewedUserId);
}
