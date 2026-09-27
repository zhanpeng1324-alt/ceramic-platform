package com.ceramic.platform.mapper;

import com.ceramic.platform.entity.UserAddress;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface UserAddressMapper {
    
    @Insert("INSERT INTO user_addresses (user_id, receiver_name, receiver_phone, address, is_default) VALUES (#{userId}, #{receiverName}, #{receiverPhone}, #{address}, #{isDefault})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UserAddress address);
    
    @Select("SELECT * FROM user_addresses WHERE user_id = #{userId} ORDER BY is_default DESC, id DESC")
    List<UserAddress> findByUserId(Long userId);
    
    @Select("SELECT * FROM user_addresses WHERE id = #{id}")
    UserAddress findById(Long id);
    
    @Update("UPDATE user_addresses SET receiver_name = #{receiverName}, receiver_phone = #{receiverPhone}, address = #{address}, is_default = #{isDefault} WHERE id = #{id}")
    int update(UserAddress address);
    
    @Delete("DELETE FROM user_addresses WHERE id = #{id}")
    int deleteById(Long id);
    
    @Update("UPDATE user_addresses SET is_default = FALSE WHERE user_id = #{userId}")
    int clearDefault(Long userId);
    
    @Select("SELECT * FROM user_addresses WHERE user_id = #{userId} AND is_default = TRUE LIMIT 1")
    UserAddress findDefaultByUserId(Long userId);
}
