package com.ceramic.platform.mapper;

import com.ceramic.platform.entity.Payment;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface PaymentMapper {

    @Insert("""
            INSERT INTO payments(payment_no, biz_type, biz_id, user_id, amount, channel, status)
            VALUES(#{paymentNo}, #{bizType}, #{bizId}, #{userId}, #{amount}, #{channel}, 'PENDING')
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Payment payment);

    /** 直接落一条「已支付成功」的流水（用于兼容旧的直接支付入口 / 补记退款前的成功流水）。 */
    @Insert("""
            INSERT INTO payments(payment_no, biz_type, biz_id, user_id, amount, channel, status, transaction_id, paid_at)
            VALUES(#{paymentNo}, #{bizType}, #{bizId}, #{userId}, #{amount}, #{channel}, 'SUCCESS', #{transactionId}, NOW())
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertSuccess(Payment payment);

    @Select("SELECT * FROM payments WHERE payment_no = #{paymentNo}")
    Payment findByPaymentNo(String paymentNo);

    @Select("SELECT * FROM payments WHERE user_id = #{userId} ORDER BY id DESC")
    List<Payment> findByUserId(Long userId);

    /** 取某业务单最近一笔待支付(PENDING)的流水（用于去重，避免重复发起产生多条 PENDING）。 */
    @Select("""
            SELECT * FROM payments
            WHERE biz_type = #{bizType} AND biz_id = #{bizId} AND status = 'PENDING'
            ORDER BY id DESC LIMIT 1
            """)
    Payment findLatestPendingByBiz(@Param("bizType") String bizType, @Param("bizId") Long bizId);

    /** 取某业务单最近一笔已支付成功的流水（用于原路退款）。 */
    @Select("""
            SELECT * FROM payments
            WHERE biz_type = #{bizType} AND biz_id = #{bizId} AND status = 'SUCCESS'
            ORDER BY id DESC LIMIT 1
            """)
    Payment findLatestSuccessByBiz(@Param("bizType") String bizType, @Param("bizId") Long bizId);

    /**
     * 幂等置为已支付：仅当当前为 PENDING 时才更新。
     * 返回受影响行数——0 表示已被处理过（防止回调重复触发业务副作用）。
     */
    @Update("""
            UPDATE payments SET status='SUCCESS', transaction_id=#{transactionId}, paid_at=NOW()
            WHERE payment_no=#{paymentNo} AND status='PENDING'
            """)
    int markSuccess(@Param("paymentNo") String paymentNo, @Param("transactionId") String transactionId);

    /** 退款：仅当当前为 SUCCESS 时才更新，返回受影响行数。 */
    @Update("""
            UPDATE payments SET status='REFUNDED', refund_no=#{refundNo}, refund_amount=#{refundAmount}, refunded_at=NOW()
            WHERE id=#{id} AND status='SUCCESS'
            """)
    int markRefunded(@Param("id") Long id, @Param("refundNo") String refundNo, @Param("refundAmount") BigDecimal refundAmount);
}
