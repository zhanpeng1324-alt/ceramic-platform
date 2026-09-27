package com.ceramic.platform.mapper;

import com.ceramic.platform.entity.Review;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ReviewMapper {

    @Select("""
            SELECT r.*, COALESCE(u.nickname, u.username) as username
            FROM reviews r
            LEFT JOIN users u ON u.id = r.user_id
            WHERE r.product_id=#{productId} AND r.status='VISIBLE'
            ORDER BY r.id DESC
            """)
    List<Review> findByProductId(Long productId);

    @Insert("INSERT INTO reviews(user_id, product_id, rating, title, content, images, status) VALUES(#{userId}, #{productId}, #{rating}, #{title}, #{content}, #{images}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Review review);

    @Select("SELECT * FROM reviews WHERE status='VISIBLE'")
    List<Review> findAll();

    /** 当前用户评价过的商品 ID 集合（用于「待评价」判断） */
    @Select("SELECT DISTINCT product_id FROM reviews WHERE user_id=#{userId}")
    List<Long> findReviewedProductIds(Long userId);

    /** 该用户是否已评价过某商品（用于下单评价查重） */
    @Select("SELECT COUNT(*) FROM reviews WHERE user_id=#{userId} AND product_id=#{productId}")
    Integer countByUserAndProduct(@Param("userId") Long userId, @Param("productId") Long productId);

    @Select("SELECT r.*, COALESCE(u.nickname, u.username) AS username, p.name AS product_name FROM reviews r LEFT JOIN users u ON u.id=r.user_id LEFT JOIN products p ON p.id=r.product_id ORDER BY r.id DESC")
    List<Review> findAllForAdmin();

    @Update("UPDATE reviews SET status=#{status}, updated_at=NOW() WHERE id=#{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    @Update("UPDATE reviews SET reply=#{reply}, reply_time=NOW(), updated_at=NOW() WHERE id=#{id}")
    int reply(@Param("id") Long id, @Param("reply") String reply);
}
