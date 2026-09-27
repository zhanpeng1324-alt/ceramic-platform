package com.ceramic.platform.mapper;

import com.ceramic.platform.entity.ReturnRequestLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ReturnRequestLogMapper {

    @Insert("""
            INSERT INTO return_request_logs(return_request_id, from_status, to_status, operator_id, operator_role, note)
            VALUES(#{returnRequestId}, #{fromStatus}, #{toStatus}, #{operatorId}, #{operatorRole}, #{note})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ReturnRequestLog log);

    @Select("SELECT * FROM return_request_logs WHERE return_request_id=#{returnRequestId} ORDER BY created_at ASC, id ASC")
    List<ReturnRequestLog> findByReturnRequestId(@Param("returnRequestId") Long returnRequestId);
}
