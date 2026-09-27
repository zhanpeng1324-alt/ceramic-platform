package com.ceramic.platform.mapper;

import com.ceramic.platform.entity.AuditLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AuditLogMapper {

    @Insert("INSERT INTO audit_logs(operator_id, operator_role, action, resource_type, resource_id, detail, ip_address, created_at) " +
            "VALUES(#{operatorId}, #{operatorRole}, #{action}, #{resourceType}, #{resourceId}, #{detail}, #{ipAddress}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AuditLog log);

    @Select("SELECT * FROM audit_logs ORDER BY created_at DESC LIMIT #{limit}")
    List<AuditLog> findRecent(@Param("limit") int limit);

    @Select("SELECT * FROM audit_logs WHERE operator_id=#{operatorId} ORDER BY created_at DESC LIMIT #{limit}")
    List<AuditLog> findByOperator(@Param("operatorId") Long operatorId, @Param("limit") int limit);
}
