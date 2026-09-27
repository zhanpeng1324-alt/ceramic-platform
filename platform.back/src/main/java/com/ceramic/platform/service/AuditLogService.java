package com.ceramic.platform.service;

import com.ceramic.platform.entity.AuditLog;
import com.ceramic.platform.mapper.AuditLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditLogService {
    private final AuditLogMapper auditLogMapper;

    public void log(Long operatorId, String operatorRole, String action,
                    String resourceType, Long resourceId, String detail, String ipAddress) {
        AuditLog log = new AuditLog();
        log.setOperatorId(operatorId);
        log.setOperatorRole(operatorRole);
        log.setAction(action);
        log.setResourceType(resourceType);
        log.setResourceId(resourceId);
        log.setDetail(detail);
        log.setIpAddress(ipAddress);
        auditLogMapper.insert(log);
    }

    public List<AuditLog> getRecentLogs(int limit) {
        return auditLogMapper.findRecent(limit);
    }

    public List<AuditLog> getLogsByOperator(Long operatorId, int limit) {
        return auditLogMapper.findByOperator(operatorId, limit);
    }
}
