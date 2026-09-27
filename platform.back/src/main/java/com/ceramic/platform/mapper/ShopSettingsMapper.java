package com.ceramic.platform.mapper;

import com.ceramic.platform.entity.ShopSettings;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ShopSettingsMapper {

    @Select("SELECT * FROM shop_settings ORDER BY id LIMIT 1")
    ShopSettings find();

    @Insert("""
            INSERT INTO shop_settings(shop_name, contact_name, contact_phone, address)
            VALUES(#{shopName}, #{contactName}, #{contactPhone}, #{address})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ShopSettings settings);

    @Update("""
            UPDATE shop_settings SET shop_name=#{shopName}, contact_name=#{contactName},
                contact_phone=#{contactPhone}, address=#{address}, updated_at=NOW()
            WHERE id=#{id}
            """)
    int update(ShopSettings settings);
}
