package com.jobera.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.jobera.entity.Message;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByApplicationIdOrderBySentAtAsc(Long applicationId);

    List<Message> findBySenderIdOrRecipientIdOrderBySentAtAsc(Long senderId, Long recipientId);

    long countByApplicationIdAndRecipientIdAndReadAtIsNull(Long applicationId, Long recipientId);

    long countByRecipientIdAndReadAtIsNull(Long recipientId);

    @Transactional
    @Modifying
    @Query("UPDATE Message m SET m.readAt = :now "
        + "WHERE m.applicationId = :applicationId AND m.recipientId = :recipientId AND m.readAt IS NULL")
    int markThreadRead(@Param("applicationId") Long applicationId,
                        @Param("recipientId") Long recipientId,
                        @Param("now") LocalDateTime now);

    @Transactional
    void deleteBySenderIdOrRecipientId(@Param("senderId") Long senderId, @Param("recipientId") Long recipientId);
}
