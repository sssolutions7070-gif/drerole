package com.jobera.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.jobera.entity.InterviewRound;

@Repository
public interface InterviewRoundRepository extends JpaRepository<InterviewRound, Long> {
    List<InterviewRound> findByApplicationIdOrderByRoundNumberAsc(Long applicationId);
    Optional<InterviewRound> findByRoomId(String roomId);
    long countByApplicationId(Long applicationId);
}
