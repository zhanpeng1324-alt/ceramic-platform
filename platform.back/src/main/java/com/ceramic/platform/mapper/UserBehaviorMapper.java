package com.ceramic.platform.mapper;

import com.ceramic.platform.entity.UserBehavior;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserBehaviorMapper {

    @Select("SELECT * FROM user_behaviors WHERE user_id = #{userId} ORDER BY timestamp DESC")
    List<UserBehavior> findByUserId(Long userId);

    @Select("SELECT * FROM user_behaviors WHERE product_id = #{productId} ORDER BY timestamp DESC")
    List<UserBehavior> findByProductId(Long productId);

    @Select("SELECT * FROM user_behaviors WHERE user_id = #{userId} AND product_id = #{productId} ORDER BY timestamp DESC")
    List<UserBehavior> findByUserAndProduct(Long userId, Long productId);

    @Insert("INSERT INTO user_behaviors(user_id, product_id, action, session_duration, referrer) VALUES(#{userId}, #{productId}, #{action}, #{sessionDuration}, #{referrer})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UserBehavior behavior);

    @Select("SELECT COUNT(*) FROM user_behaviors WHERE user_id = #{userId} AND action = #{action}")
    Integer countByUserAndAction(Long userId, String action);

    @Select("SELECT COUNT(*) FROM user_behaviors WHERE user_id = #{userId} AND product_id = #{productId} AND action = #{action}")
    Integer countByUserProductAndAction(Long userId, Long productId, String action);
}