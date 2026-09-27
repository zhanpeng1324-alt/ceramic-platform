package com.ceramic.platform.mapper;

import com.ceramic.platform.entity.Customization;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface CustomizationMapper {

    @Select("SELECT * FROM custom_orders WHERE user_id = #{userId} ORDER BY id DESC")
    List<Customization> findByUserId(Long userId);

    @Select("SELECT * FROM custom_orders ORDER BY id DESC")
    List<Customization> findAll();

    @Select("SELECT * FROM custom_orders WHERE id = #{id}")
    Customization findById(Long id);

    @Insert("""
            INSERT INTO custom_orders(user_id, product_id, design_specifications, design_image_url, price, shape, glaze_color,
            pattern, inscription, size, quantity, budget, contact_name, contact_phone, shipping_address, requirement, status, notes)
            VALUES(#{userId}, #{productId}, #{designSpecifications}, #{designImageUrl}, #{price}, #{shape}, #{glazeColor},
            #{pattern}, #{inscription}, #{size}, #{quantity}, #{budget}, #{contactName}, #{contactPhone}, #{shippingAddress},
            #{requirement}, #{status}, #{notes})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Customization customization);

    @Update("UPDATE custom_orders SET status=#{status}, updated_at=NOW() WHERE id=#{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    @Update("UPDATE custom_orders SET deposit_paid=1, updated_at=NOW() WHERE id=#{id}")
    int markDepositPaid(@Param("id") Long id);

    @Update("UPDATE custom_orders SET final_paid=1, final_pay_time=NOW(), updated_at=NOW() WHERE id=#{id}")
    int markBalancePaid(@Param("id") Long id);

    @Update("UPDATE custom_orders SET status=#{status}, finished_product_url=#{finishedProductUrl}, updated_at=NOW() WHERE id=#{id}")
    int updateStatusAndFinished(@Param("id") Long id, @Param("status") String status, @Param("finishedProductUrl") String finishedProductUrl);

    @Update("""
            UPDATE custom_orders
            SET quoted_price=#{quotedPrice}, deposit_amount=#{depositAmount}, final_amount=#{finalAmount},
                timeline_note=#{timelineNote}, expected_complete_date=#{expectedCompleteDate},
                status='QUOTED', updated_at=NOW()
            WHERE id=#{id}
            """)
    int updateQuote(@Param("id") Long id, @Param("quotedPrice") BigDecimal quotedPrice,
                    @Param("depositAmount") BigDecimal depositAmount, @Param("finalAmount") BigDecimal finalAmount,
                    @Param("timelineNote") String timelineNote,
                    @Param("expectedCompleteDate") String expectedCompleteDate);
}
