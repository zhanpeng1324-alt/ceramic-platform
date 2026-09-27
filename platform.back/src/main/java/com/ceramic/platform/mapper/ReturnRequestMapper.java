package com.ceramic.platform.mapper;

import com.ceramic.platform.entity.ReturnRequest;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface ReturnRequestMapper {

    @Select("SELECT * FROM return_requests WHERE user_id=#{userId} ORDER BY id DESC")
    List<ReturnRequest> findByUserId(@Param("userId") Integer userId);

    @Select("SELECT * FROM return_requests WHERE order_id=#{orderId} ORDER BY id DESC")
    List<ReturnRequest> findByOrderId(@Param("orderId") Integer orderId);

    @Select("SELECT * FROM return_requests ORDER BY id DESC")
    List<ReturnRequest> findAll();

    @Select("SELECT * FROM return_requests WHERE id=#{id}")
    ReturnRequest findById(@Param("id") Integer id);

    @Insert("""
            INSERT INTO return_requests(order_id, order_item_id, user_id, product_id, product_name, image_url,
                unit_price, quantity, return_type, reason, description, status, refund_amount, evidence_images)
            VALUES(#{orderId}, #{orderItemId}, #{userId}, #{productId}, #{productName}, #{imageUrl},
                #{unitPrice}, #{quantity}, #{returnType}, #{reason}, #{description}, #{status}, #{refundAmount}, #{evidenceImages})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ReturnRequest request);

    @Update("UPDATE return_requests SET status=#{status}, updated_at=NOW() WHERE id=#{id}")
    int updateStatus(@Param("id") Integer id, @Param("status") String status);

    @Update("UPDATE return_requests SET status=#{status}, reject_reason=#{rejectReason}, updated_at=NOW() WHERE id=#{id}")
    int updateReject(@Param("id") Integer id, @Param("status") String status, @Param("rejectReason") String rejectReason);

    @Update("UPDATE return_requests SET return_address=#{returnAddress}, updated_at=NOW() WHERE id=#{id}")
    int updateReturnAddress(@Param("id") Integer id, @Param("returnAddress") String returnAddress);

    @Update("UPDATE return_requests SET status=#{status}, tracking_no=#{trackingNo}, updated_at=NOW() WHERE id=#{id}")
    int updateTracking(@Param("id") Integer id, @Param("status") String status, @Param("trackingNo") String trackingNo);

    @Update("UPDATE return_requests SET status=#{status}, refund_amount=#{refundAmount}, updated_at=NOW() WHERE id=#{id}")
    int updateRefund(@Param("id") Integer id, @Param("status") String status, @Param("refundAmount") BigDecimal refundAmount);
}
