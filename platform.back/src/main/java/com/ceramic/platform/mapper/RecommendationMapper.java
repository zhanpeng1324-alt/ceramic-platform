package com.ceramic.platform.mapper;

import com.ceramic.platform.entity.Recommendation;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RecommendationMapper {

    @Select("SELECT * FROM recommendations WHERE user_id = #{userId} ORDER BY score DESC")
    List<Recommendation> findByUserId(Long userId);

    @Select("SELECT * FROM recommendations WHERE user_id = #{userId} ORDER BY score DESC LIMIT #{limit}")
    List<Recommendation> findTopByUserId(Long userId, Integer limit);

    @Insert("INSERT INTO recommendations(user_id, product_id, reason, score) VALUES(#{userId}, #{productId}, #{reason}, #{score})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Recommendation recommendation);

    @Delete("DELETE FROM recommendations WHERE user_id = #{userId}")
    int deleteByUserId(Long userId);

    @Select("SELECT COUNT(*) FROM recommendations WHERE user_id = #{userId} AND product_id = #{productId}")
    Integer countByUserAndProduct(Long userId, Long productId);
}