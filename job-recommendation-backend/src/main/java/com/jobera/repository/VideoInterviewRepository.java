package com.jobera.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.jobera.entity.VideoInterview;

@Repository
public interface VideoInterviewRepository extends JpaRepository<VideoInterview, Long> {
    Optional<VideoInterview> findByApplicationId(Long applicationId);
    Optional<VideoInterview> findByRoomId(String roomId);
}
