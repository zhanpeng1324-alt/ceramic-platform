package com.ceramic.platform.mapper;

import com.ceramic.platform.entity.CartItem;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface CartMapper {

    @Select("""
            SELECT c.id, c.user_id, c.product_id, c.quantity, c.selected_options,
                   COALESCE(p.name, c.product_name) AS product_name,
                   COALESCE(p.image_url, c.image_url) AS image_url,
                   COALESCE(p.price, c.price) AS price,
                   p.status AS product_status,
                   p.stock AS stock,
                   c.created_at, c.updated_at
            FROM cart_items c
            LEFT JOIN products p ON p.id = c.product_id
            WHERE c.user_id = #{userId}
            ORDER BY c.id DESC
            """)
    List<CartItem> findByUserId(Long userId);

    @Select("SELECT * FROM cart_items WHERE user_id=#{userId} AND product_id=#{productId} LIMIT 1")
    CartItem findExisting(@Param("userId") Long userId, @Param("productId") Long productId);

    @Select("SELECT * FROM cart_items WHERE id=#{id}")
    CartItem findById(Long id);

    @Insert("INSERT INTO cart_items(user_id, product_id, quantity, selected_options, product_name, price, image_url) VALUES(#{userId}, #{productId}, #{quantity}, #{selectedOptions}, #{productName}, #{price}, #{imageUrl})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(CartItem item);

    @Update("UPDATE cart_items SET quantity = quantity + #{quantity}, updated_at=NOW() WHERE id=#{id}")
    int increase(CartItem item);

    @Update("UPDATE cart_items SET quantity=#{quantity}, selected_options=#{selectedOptions}, updated_at=NOW() WHERE id=#{id}")
    int update(CartItem item);

    @Delete("DELETE FROM cart_items WHERE id=#{id}")
    int delete(Long id);

    @Delete("DELETE FROM cart_items WHERE user_id=#{userId}")
    int clearByUserId(Long userId);
}
