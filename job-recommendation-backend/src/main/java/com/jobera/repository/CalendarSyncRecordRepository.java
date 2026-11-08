package com.jobera.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.jobera.entity.CalendarSyncRecord;

@Repository
public interface CalendarSyncRecordRepository extends JpaRepository<CalendarSyncRecord, Long> {
    Optional<CalendarSyncRecord> findByInterviewRoundIdAndUserId(Long interviewRoundId, Long userId);
}
