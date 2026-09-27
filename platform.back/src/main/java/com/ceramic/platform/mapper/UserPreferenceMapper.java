package com.ceramic.platform.mapper;

import com.ceramic.platform.entity.UserPreference;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserPreferenceMapper {

    @Select("SELECT * FROM user_preferences WHERE user_id = #{userId}")
    UserPreference findByUserId(Long userId);

    @Insert("INSERT INTO user_preferences(user_id, category_id, preferred_price_min, preferred_price_max, preferred_material, preferred_glaze_color) VALUES(#{userId}, #{categoryId}, #{preferredPriceMin}, #{preferredPriceMax}, #{preferredMaterial}, #{preferredGlazeColor})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UserPreference preference);

    @Update("UPDATE user_preferences SET category_id=#{categoryId}, preferred_price_min=#{preferredPriceMin}, preferred_price_max=#{preferredPriceMax}, preferred_material=#{preferredMaterial}, preferred_glaze_color=#{preferredGlazeColor} WHERE user_id=#{userId}")
    int update(UserPreference preference);
}