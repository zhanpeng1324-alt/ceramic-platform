package com.ceramic.platform.mapper;

import com.ceramic.platform.entity.RecommendationRule;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface RecommendationRuleMapper {

    @Select("SELECT * FROM recommendation_rules ORDER BY id DESC")
    List<RecommendationRule> findAll();

    @Select("SELECT * FROM recommendation_rules WHERE is_active = 1 ORDER BY id DESC")
    List<RecommendationRule> findActiveRules();

    @Select("SELECT * FROM recommendation_rules WHERE id = #{id}")
    RecommendationRule findById(Long id);

    @Insert("INSERT INTO recommendation_rules(name, type, criteria, weight, is_active) VALUES(#{name}, #{type}, #{criteria}, #{weight}, #{isActive})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(RecommendationRule rule);

    @Update("UPDATE recommendation_rules SET name=#{name}, type=#{type}, criteria=#{criteria}, weight=#{weight}, is_active=#{isActive} WHERE id=#{id}")
    int update(RecommendationRule rule);

    @Update("UPDATE recommendation_rules SET is_active=#{isActive} WHERE id=#{id}")
    int updateStatus(Long id, Integer isActive);
}