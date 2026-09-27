package com.ceramic.platform.mapper;

import com.ceramic.platform.entity.SupportTicket;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface SupportTicketMapper {

    @Select("SELECT * FROM support_tickets WHERE user_id=#{userId} ORDER BY id DESC")
    List<SupportTicket> findByUserId(Long userId);

    @Select("""
            SELECT st.*, COALESCE(u.nickname, u.username) as customerName
            FROM support_tickets st
            LEFT JOIN users u ON u.id = st.user_id
            ORDER BY st.id DESC
            """)
    List<SupportTicket> findAll();

    @Insert("""
            INSERT INTO support_tickets(user_id, order_id, type, title, content, contact_name, contact_phone, priority, status)
            VALUES(#{userId}, #{orderId}, #{type}, #{title}, #{content}, #{contactName}, #{contactPhone}, #{priority}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SupportTicket ticket);

    @Update("UPDATE support_tickets SET status=#{status}, reply=#{reply}, updated_at=NOW() WHERE id=#{id}")
    int reply(SupportTicket ticket);

    @Update("UPDATE support_tickets SET priority=#{priority}, updated_at=NOW() WHERE id=#{id}")
    int updatePriority(Long id, String priority);

    @Update("UPDATE support_tickets SET status=#{status}, updated_at=NOW() WHERE id=#{id}")
    int updateStatus(Long id, String status);
}
