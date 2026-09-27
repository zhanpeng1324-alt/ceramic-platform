package com.ceramic.platform.mapper;

import com.ceramic.platform.entity.Order;
import com.ceramic.platform.entity.OrderItem;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface OrderMapper {

    @Select("SELECT * FROM orders WHERE user_id=#{userId} ORDER BY id DESC")
    List<Order> findByUserId(Long userId);

    @Select("SELECT * FROM orders ORDER BY id DESC")
    List<Order> findAll();

    @Select("SELECT * FROM orders WHERE id=#{id}")
    Order findById(Long id);

    @Select("SELECT * FROM order_items WHERE order_id=#{orderId}")
    List<OrderItem> findItems(Long orderId);

    @Insert("""
            INSERT INTO orders(order_no, user_id, total_amount, status, pay_type, receiver_name,
                receiver_phone, receiver_address, remark)
            VALUES(#{orderNo}, #{userId}, #{totalAmount}, #{status}, #{payType}, #{receiverName},
                #{receiverPhone}, #{receiverAddress}, #{remark})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Order order);

    @Insert("""
            INSERT INTO order_items(order_id, product_id, product_name, image_url, unit_price, quantity, subtotal)
            VALUES(#{orderId}, #{productId}, #{productName}, #{imageUrl}, #{unitPrice}, #{quantity}, #{subtotal})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertItem(OrderItem item);

    @Update("UPDATE orders SET status=#{status}, pay_time=IF(#{status}='PAID', NOW(), pay_time), updated_at=NOW() WHERE id=#{id}")
    int updateStatus(Order order);

    /**
     * 带原状态条件的更新：仅当数据库里的状态仍等于 {@code expectedStatus} 时才更新成功。
     * 返回受影响行数：1 表示本线程抢到了这次流转，0 表示状态已被其它线程改掉(重复提交/并发)。
     * 这是防止「查了再改」并发问题(如重复支付重复扣库存)的关键闸门。
     */
    @Update("UPDATE orders SET status=#{status}, pay_time=IF(#{status}='PAID', NOW(), pay_time), updated_at=NOW() WHERE id=#{id} AND status=#{expectedStatus}")
    int updateStatusGuarded(@Param("id") Long id, @Param("status") String status, @Param("expectedStatus") String expectedStatus);

    @Update("""
            UPDATE orders SET status='SHIPPED', shipping_company=#{shippingCompany}, tracking_no=#{trackingNo},
                ship_time=NOW(), updated_at=NOW() WHERE id=#{id}
            """)
    int ship(@Param("id") Long id, @Param("shippingCompany") String shippingCompany, @Param("trackingNo") String trackingNo);

    @Update("""
            UPDATE orders SET receiver_name=#{receiverName}, receiver_phone=#{receiverPhone},
                receiver_address=#{receiverAddress}, updated_at=NOW() WHERE id=#{id}
            """)
    int updateReceiver(@Param("id") Long id, @Param("receiverName") String receiverName,
                       @Param("receiverPhone") String receiverPhone, @Param("receiverAddress") String receiverAddress);

    /**
     * 批量取消超时未支付订单：仅影响 PENDING_PAY 且创建时间早于给定分钟数的订单。
     * 待支付订单未占用库存（库存在支付时才扣减），因此无需回补库存。
     */
    @Update("""
            UPDATE orders SET status='CANCELLED', updated_at=NOW()
            WHERE status IN ('PENDING_PAY', 'pending_pay')
              AND created_at < DATE_SUB(NOW(), INTERVAL #{minutes} MINUTE)
            """)
    int cancelExpiredPending(@Param("minutes") int minutes);

    @Select("SELECT COUNT(*) FROM order_items oi JOIN orders o ON oi.order_id = o.id WHERE oi.product_id = #{productId} AND o.user_id = #{userId} AND o.status IN ('PAID', 'SHIPPED', 'COMPLETED', 'paid', 'shipped', 'delivered')")
    Integer countUserProductOrders(Long userId, Long productId);

    /** 仅统计「已完成」订单：用于评价资格校验(下单付款还不能评，需确认收货/完成) */
    @Select("SELECT COUNT(*) FROM order_items oi JOIN orders o ON oi.order_id = o.id WHERE oi.product_id = #{productId} AND o.user_id = #{userId} AND o.status IN ('COMPLETED', 'completed', 'delivered')")
    Integer countCompletedUserProductOrders(Long userId, Long productId);
}
