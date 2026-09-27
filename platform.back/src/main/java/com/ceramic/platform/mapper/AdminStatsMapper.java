package com.ceramic.platform.mapper;

import com.ceramic.platform.dto.AdminStatsDTO;
import com.ceramic.platform.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 管理员数据看板聚合查询。
 */
@Mapper
public interface AdminStatsMapper {

    /** 近 7 天每日订单量（按下单时间） */
    @Select("""
            SELECT DATE_FORMAT(created_at, '%Y-%m-%d') AS date, COUNT(*) AS value
            FROM orders
            WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 6 DAY)
            GROUP BY DATE_FORMAT(created_at, '%Y-%m-%d')
            ORDER BY date
            """)
    List<AdminStatsDTO.DatePoint> selectOrderCount7d();

    /** 近 7 天每日销售额（仅已成交订单） */
    @Select("""
            SELECT DATE_FORMAT(created_at, '%Y-%m-%d') AS date, COALESCE(SUM(total_amount), 0) AS value
            FROM orders
            WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 6 DAY)
              AND status IN ('PAID', 'SHIPPED', 'COMPLETED')
            GROUP BY DATE_FORMAT(created_at, '%Y-%m-%d')
            ORDER BY date
            """)
    List<AdminStatsDTO.DatePoint> selectSalesAmount7d();

    /** 订单状态分布 */
    @Select("SELECT status AS name, COUNT(*) AS value FROM orders GROUP BY status")
    List<AdminStatsDTO.NameValue> selectOrderStatusDistribution();

    /** 热销商品 Top 5（按已成交订单的销量） */
    @Select("""
            SELECT oi.product_name AS name, SUM(oi.quantity) AS value
            FROM order_items oi
            JOIN orders o ON oi.order_id = o.id
            WHERE o.status IN ('PAID', 'SHIPPED', 'COMPLETED')
            GROUP BY oi.product_id, oi.product_name
            ORDER BY value DESC
            LIMIT 5
            """)
    List<AdminStatsDTO.NameValue> selectTopProducts();

    /** 低库存商品列表（库存低于阈值且上架中） */
    @Select("""
            SELECT * FROM products
            WHERE stock < 10 AND status = 'active'
            ORDER BY stock ASC
            LIMIT 20
            """)
    List<Product> selectLowStockProducts();

    @Select("SELECT COUNT(*) FROM users")
    long countUsers();

    @Select("SELECT COUNT(*) FROM products")
    long countProducts();

    @Select("SELECT COUNT(*) FROM orders WHERE status = 'PENDING_PAY'")
    long countPendingPayOrders();

    @Select("SELECT COUNT(*) FROM return_requests WHERE status = 'pending'")
    long countPendingReturns();

    @Select("SELECT COUNT(*) FROM custom_orders WHERE status = 'PENDING'")
    long countPendingCustomizations();

    @Select("SELECT COUNT(*) FROM products WHERE stock < 10 AND status = 'active'")
    long countLowStock();
}
