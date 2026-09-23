package com.jobera.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.jobera.entity.AdminAuditLog;
import com.jobera.entity.User;
import com.jobera.repository.AdminAuditLogRepository;

@Service
public class AdminAuditService {

    private final AdminAuditLogRepository auditLogRepository;

    public AdminAuditService(AdminAuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void record(User admin, String action, String targetType, Long targetId, String targetDescription) {
        AdminAuditLog entry = new AdminAuditLog();
        entry.setAdminUserId(admin.getId());
        entry.setAdminEmail(admin.getEmail());
        entry.setAction(action);
        entry.setTargetType(targetType);
        entry.setTargetId(targetId);
        entry.setTargetDescription(targetDescription);
        auditLogRepository.save(entry);
    }

    public List<AdminAuditLog> recent() {
        return auditLogRepository.findTop200ByOrderByCreatedAtDesc();
    }
}
