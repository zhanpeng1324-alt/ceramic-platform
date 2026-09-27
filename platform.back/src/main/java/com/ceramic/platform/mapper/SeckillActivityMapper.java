package com.ceramic.platform.mapper;

import com.ceramic.platform.entity.SeckillActivity;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface SeckillActivityMapper {

    @Insert("INSERT INTO seckill_activities(product_id, seckill_price, total_stock, available_stock, " +
            "start_time, end_time, status, created_at) " +
            "VALUES(#{productId}, #{seckillPrice}, #{totalStock}, #{availableStock}, " +
            "#{startTime}, #{endTime}, #{status}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SeckillActivity activity);

    @Select("SELECT a.*, p.name AS product_name, p.image_url AS product_image, p.price AS original_price " +
            "FROM seckill_activities a LEFT JOIN products p ON p.id = a.product_id " +
            "WHERE a.id = #{id}")
    SeckillActivity findById(@Param("id") Long id);

    @Select("SELECT a.*, p.name AS product_name, p.image_url AS product_image, p.price AS original_price " +
            "FROM seckill_activities a LEFT JOIN products p ON p.id = a.product_id " +
            "ORDER BY a.start_time DESC, a.id DESC")
    List<SeckillActivity> findAll();

    /** 库存回补（超时取消/落库失败）：available + delta，且不超过 total */
    @Update("UPDATE seckill_activities SET available_stock = LEAST(available_stock + #{delta}, total_stock) " +
            "WHERE id = #{id}")
    int restock(@Param("id") Long id, @Param("delta") int delta);

    /** Redis 不可用时的 DB 降级扣减：CAS 保证不超卖 */
    @Update("UPDATE seckill_activities SET available_stock = available_stock - 1 " +
            "WHERE id = #{id} AND available_stock > 0")
    int deductStock(@Param("id") Long id);

    @Update("UPDATE seckill_activities SET status = #{target} WHERE id = #{id} AND status = #{expect}")
    int updateStatusGuarded(@Param("id") Long id, @Param("target") String target, @Param("expect") String expect);
}
