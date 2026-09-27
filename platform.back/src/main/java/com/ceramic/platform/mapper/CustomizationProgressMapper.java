package com.ceramic.platform.mapper;

import com.ceramic.platform.entity.CustomizationProgress;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CustomizationProgressMapper {

    @Select("SELECT * FROM customization_progress WHERE customization_id = #{customizationId} ORDER BY created_at ASC, id ASC")
    List<CustomizationProgress> findByCustomizationId(Long customizationId);

    @Insert("""
            INSERT INTO customization_progress(customization_id, stage, description, image_url,
                operator_id, operator_role, from_status, to_status)
            VALUES(#{customizationId}, #{stage}, #{description}, #{imageUrl},
                #{operatorId}, #{operatorRole}, #{fromStatus}, #{toStatus})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(CustomizationProgress progress);
}
