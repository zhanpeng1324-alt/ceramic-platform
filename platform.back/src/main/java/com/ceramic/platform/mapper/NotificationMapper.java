package com.ceramic.platform.mapper;

import com.ceramic.platform.entity.Notification;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface NotificationMapper {

    @Insert("""
            INSERT INTO notifications(user_id, type, title, content, biz_type, biz_id)
            VALUES(#{userId}, #{type}, #{title}, #{content}, #{bizType}, #{bizId})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Notification notification);

    @Select("SELECT * FROM notifications WHERE user_id = #{userId} ORDER BY id DESC LIMIT #{limit}")
    List<Notification> findByUser(@Param("userId") Long userId, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM notifications WHERE user_id = #{userId} AND is_read = 0")
    int unreadCount(Long userId);

    @Update("UPDATE notifications SET is_read = 1, read_at = NOW() WHERE id = #{id} AND user_id = #{userId}")
    int markRead(@Param("id") Long id, @Param("userId") Long userId);

    @Update("UPDATE notifications SET is_read = 1, read_at = NOW() WHERE user_id = #{userId} AND is_read = 0")
    int markAllRead(Long userId);
}
