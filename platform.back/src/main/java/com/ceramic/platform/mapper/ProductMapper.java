package com.ceramic.platform.mapper;

import com.ceramic.platform.entity.Product;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Mapper
public interface ProductMapper {

    @Select("""
            <script>
            SELECT * FROM products
            WHERE status = 'active'
            <if test='categoryId != null'>AND category_id = #{categoryId}</if>
            <if test='keyword != null and keyword != ""'>
                AND (name LIKE CONCAT('%', #{keyword}, '%') OR subtitle LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test='priceMin != null'>AND price &gt;= #{priceMin}</if>
            <if test='priceMax != null'>AND price &lt;= #{priceMax}</if>
            <choose>
                <when test='sort == "price_asc"'>ORDER BY price ASC, id DESC</when>
                <when test='sort == "price_desc"'>ORDER BY price DESC, id DESC</when>
                <when test='sort == "sales"'>
                    ORDER BY (SELECT COALESCE(SUM(oi.quantity), 0) FROM order_items oi
                              JOIN orders o ON oi.order_id = o.id
                              WHERE oi.product_id = products.id
                                AND o.status IN ('PAID','SHIPPED','COMPLETED','paid','shipped','delivered')) DESC, id DESC
                </when>
                <otherwise>ORDER BY id DESC</otherwise>
            </choose>
            </script>
            """)
    List<Product> search(@Param("categoryId") Long categoryId, @Param("keyword") String keyword,
                         @Param("priceMin") BigDecimal priceMin, @Param("priceMax") BigDecimal priceMax,
                         @Param("sort") String sort);

    /** 分页搜索：与 search 同条件，追加 LIMIT，避免大表全量返回 */
    @Select("""
            <script>
            SELECT * FROM products
            WHERE status = 'active'
            <if test='categoryId != null'>AND category_id = #{categoryId}</if>
            <if test='keyword != null and keyword != ""'>
                AND (name LIKE CONCAT('%', #{keyword}, '%') OR subtitle LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test='priceMin != null'>AND price &gt;= #{priceMin}</if>
            <if test='priceMax != null'>AND price &lt;= #{priceMax}</if>
            <choose>
                <when test='sort == "price_asc"'>ORDER BY price ASC, id DESC</when>
                <when test='sort == "price_desc"'>ORDER BY price DESC, id DESC</when>
                <when test='sort == "sales"'>
                    ORDER BY (SELECT COALESCE(SUM(oi.quantity), 0) FROM order_items oi
                              JOIN orders o ON oi.order_id = o.id
                              WHERE oi.product_id = products.id
                                AND o.status IN ('PAID','SHIPPED','COMPLETED','paid','shipped','delivered')) DESC, id DESC
                </when>
                <otherwise>ORDER BY id DESC</otherwise>
            </choose>
            LIMIT #{offset}, #{size}
            </script>
            """)
    List<Product> searchPage(@Param("categoryId") Long categoryId, @Param("keyword") String keyword,
                             @Param("priceMin") BigDecimal priceMin, @Param("priceMax") BigDecimal priceMax,
                             @Param("sort") String sort, @Param("offset") long offset, @Param("size") int size);

    /** 分页总数：与 search 同条件 */
    @Select("""
            <script>
            SELECT COUNT(*) FROM products
            WHERE status = 'active'
            <if test='categoryId != null'>AND category_id = #{categoryId}</if>
            <if test='keyword != null and keyword != ""'>
                AND (name LIKE CONCAT('%', #{keyword}, '%') OR subtitle LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test='priceMin != null'>AND price &gt;= #{priceMin}</if>
            <if test='priceMax != null'>AND price &lt;= #{priceMax}</if>
            </script>
            """)
    long countSearch(@Param("categoryId") Long categoryId, @Param("keyword") String keyword,
                     @Param("priceMin") BigDecimal priceMin, @Param("priceMax") BigDecimal priceMax);

    @Select("SELECT * FROM products WHERE id = #{id}")
    Product findById(Long id);

    /**
     * 管理端商品列表：不按上架状态过滤(含已下架),已上架排前、已下架排后。
     * 供后台管理页展示与「重新上架 / 删除」操作。
     */
    @Select("""
            <script>
            SELECT * FROM products
            <where>
            <if test='categoryId != null'>AND category_id = #{categoryId}</if>
            <if test='keyword != null and keyword != ""'>
                AND (name LIKE CONCAT('%', #{keyword}, '%') OR subtitle LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            </where>
            ORDER BY (CASE WHEN status = 'active' THEN 0 ELSE 1 END), id DESC
            </script>
            """)
    List<Product> adminSearch(@Param("categoryId") Long categoryId, @Param("keyword") String keyword);

    @Insert("""
            INSERT INTO products(category_id, name, subtitle, description, price, stock, image_url, images,
                glaze_color, material, size, customizable, status)
            VALUES(#{categoryId}, #{name}, #{subtitle}, #{description}, #{price}, #{stock}, #{imageUrl}, #{images},
                #{glazeColor}, #{material}, #{size}, #{customizable}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Product product);

    /** 全量商品 id：用于启动时预热布隆过滤器 */
    @Select("SELECT id FROM products")
    List<Long> findAllIds();

    @Update("""
            UPDATE products SET category_id=#{categoryId}, name=#{name}, subtitle=#{subtitle},
                description=#{description}, price=#{price}, stock=#{stock}, image_url=#{imageUrl}, images=#{images},
                glaze_color=#{glazeColor}, material=#{material}, size=#{size},
                customizable=#{customizable}, status=#{status}, updated_at=NOW()
            WHERE id=#{id}
            """)
    int update(Product product);

    /** 仅更新上架/下架状态：避免整行覆盖把其它字段写成 null */
    @Update("UPDATE products SET status=#{status}, updated_at=NOW() WHERE id=#{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    @Update("UPDATE products SET stock = stock - #{quantity}, updated_at=NOW() WHERE id=#{id} AND stock >= #{quantity}")
    int decreaseStock(@Param("id") Long id, @Param("quantity") Integer quantity);

    @Update("UPDATE products SET stock = stock + #{quantity}, updated_at=NOW() WHERE id=#{id}")
    int increaseStock(@Param("id") Long id, @Param("quantity") Integer quantity);

    @Delete("DELETE FROM products WHERE id = #{id}")
    int delete(Long id);

    @Select("SELECT COALESCE(SUM(oi.quantity), 0) FROM order_items oi JOIN orders o ON oi.order_id = o.id WHERE oi.product_id = #{productId} AND o.status IN ('PAID', 'SHIPPED', 'COMPLETED', 'paid', 'shipped', 'delivered')")
    Integer getSalesCount(Long productId);

    /** 批量销量：与 getSalesCount 同口径，按商品聚合，供列表页一次查询填充全部已售件数 */
    @Select("""
            <script>
            SELECT oi.product_id, COALESCE(SUM(oi.quantity), 0) AS sold
            FROM order_items oi JOIN orders o ON oi.order_id = o.id
            WHERE oi.product_id IN
            <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>
              AND o.status IN ('PAID', 'SHIPPED', 'COMPLETED', 'paid', 'shipped', 'delivered')
            GROUP BY oi.product_id
            </script>
            """)
    List<Map<String, Object>> salesByProductIds(@Param("ids") List<Long> ids);

    @Select("SELECT * FROM products WHERE status = 'active'")
    List<Product> findAll();

    /**
     * 统计近 N 天各商品的成交销量（仅含已付款/已发货/已完成订单）。
     * 返回列 product_id、sold_qty，供备货建议按商品聚合。
     */
    @Select("""
            SELECT oi.product_id AS product_id, COALESCE(SUM(oi.quantity), 0) AS sold_qty
            FROM order_items oi JOIN orders o ON oi.order_id = o.id
            WHERE o.status IN ('PAID', 'SHIPPED', 'COMPLETED', 'paid', 'shipped', 'delivered')
              AND o.created_at >= DATE_SUB(NOW(), INTERVAL #{days} DAY)
            GROUP BY oi.product_id
            """)
    List<Map<String, Object>> salesInLastDays(@Param("days") int days);
}
