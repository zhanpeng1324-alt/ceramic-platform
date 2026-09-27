package com.ceramic.platform.mapper;

import com.ceramic.platform.entity.Category;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CategoryMapper {

    @Select("SELECT * FROM categories ORDER BY sort_order, id")
    List<Category> findAll();

    @Insert("INSERT INTO categories(name, description, sort_order) VALUES(#{name}, #{description}, #{sortOrder})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Category category);
}
