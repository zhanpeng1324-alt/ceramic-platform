package com.ceramic.platform.mapper;

import com.ceramic.platform.entity.User;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface UserMapper {

    @Select("SELECT * FROM users")
    List<User> findAll();

    @Select("SELECT * FROM users WHERE username = #{username}")
    User findByUsername(@Param("username") String username);

    @Select("SELECT * FROM users WHERE email = #{email}")
    User findByEmail(@Param("email") String email);

    @Select("SELECT * FROM users WHERE phone = #{phone}")
    User findByPhone(@Param("phone") String phone);

    @Select("SELECT * FROM users WHERE id = #{id}")
    User findById(@Param("id") Long id);

    @Insert("INSERT INTO users(username, password, nickname, email, phone, role, created_at, updated_at) VALUES(#{username}, #{password}, #{nickname}, #{email}, #{phone}, #{role}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);

    @Update("UPDATE users SET username=#{username}, nickname=#{nickname}, avatar=#{avatar}, email=#{email}, phone=#{phone}, address=#{address}, role=#{role}, updated_at=NOW() WHERE id=#{id}")
    int update(User user);

    @Delete("DELETE FROM users WHERE id=#{id}")
    int delete(@Param("id") Long id);
}
