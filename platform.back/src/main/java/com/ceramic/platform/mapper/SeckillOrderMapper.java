package com.ceramic.platform.mapper;

import com.ceramic.platform.entity.SeckillOrder;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface SeckillOrderMapper {

    @Insert("INSERT INTO seckill_orders(activity_id, user_id, product_id, quantity, seckill_price, " +
            "pay_amount, status, order_no, created_at) " +
            "VALUES(#{activityId}, #{userId}, #{productId}, #{quantity}, #{seckillPrice}, " +
            "#{payAmount}, #{status}, #{orderNo}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SeckillOrder order);

    @Select("SELECT * FROM seckill_orders WHERE id = #{id}")
    SeckillOrder findById(@Param("id") Long id);

    @Select("SELECT * FROM seckill_orders WHERE order_no = #{orderNo}")
    SeckillOrder findByOrderNo(@Param("orderNo") String orderNo);

    @Select("SELECT o.*, p.name AS product_name, p.image_url AS product_image " +
            "FROM seckill_orders o LEFT JOIN products p ON p.id = o.product_id " +
            "WHERE o.user_id = #{userId} ORDER BY o.created_at DESC, o.id DESC")
    List<SeckillOrder> findByUserId(@Param("userId") Long userId);

    @Select("SELECT o.*, p.name AS product_name, p.image_url AS product_image " +
            "FROM seckill_orders o LEFT JOIN products p ON p.id = o.product_id " +
            "WHERE o.order_no = #{orderNo}")
    SeckillOrder findWithProductByOrderNo(@Param("orderNo") String orderNo);

    /** 状态机推进：仅期望状态可流转（CAS），支付/取消与并发操作互斥 */
    @Update("UPDATE seckill_orders SET status = #{target} WHERE id = #{id} AND status = #{expect}")
    int updateStatusGuarded(@Param("id") Long id, @Param("target") String target, @Param("expect") String expect);

    /** 转正式订单后回填正式订单号（幂等：重复回填无害） */
    @Update("UPDATE seckill_orders SET convert_order_no = #{convertOrderNo} WHERE id = #{id} AND status = 'PAID'")
    int markConverted(@Param("id") Long id, @Param("convertOrderNo") String convertOrderNo);

    /** 售后退款完成：按正式订单号联动，PAID → REFUNDED（CAS 幂等，重复退款消息无害） */
    @Update("UPDATE seckill_orders SET status = 'REFUNDED' WHERE convert_order_no = #{convertOrderNo} AND status = 'PAID'")
    int markRefundedByConvertOrderNo(@Param("convertOrderNo") String convertOrderNo);
}
