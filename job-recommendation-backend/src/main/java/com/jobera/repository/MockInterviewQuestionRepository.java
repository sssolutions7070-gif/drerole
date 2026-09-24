package com.jobera.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.jobera.entity.MockInterviewQuestion;

@Repository
public interface MockInterviewQuestionRepository extends JpaRepository<MockInterviewQuestion, Long> {
    List<MockInterviewQuestion> findBySessionIdOrderByQuestionNumberAsc(Long sessionId);
    long countBySessionIdAndAnswerTextIsNull(Long sessionId);
}
