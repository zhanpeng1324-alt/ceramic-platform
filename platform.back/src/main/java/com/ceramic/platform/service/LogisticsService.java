package com.ceramic.platform.service;

import com.ceramic.platform.dto.LogisticsTrace;
import com.ceramic.platform.entity.Order;
import com.ceramic.platform.mapper.OrderMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 模拟物流网关：依据订单发货时间推演物流轨迹节点。
 * 当前为本地模拟，未来可替换为真实承运商 API（快递100 / 快递鸟）——
 * 密钥经环境变量 LOGISTICS_API_KEY 注入，本类对外接口不变，前端与其余后端无需改动。
 */
@Service
public class LogisticsService {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final OrderMapper orderMapper;

    public LogisticsService(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    /** 顾客/客服查询某订单的物流轨迹（含归属校验）。未发货返回空列表。 */
    public List<LogisticsTrace> trace(Long orderId, Long userId, String role) {
        Order order = orderMapper.findById(orderId);
        if (order == null) throw new IllegalArgumentException("订单不存在");
        if ("customer".equals(role) && !userId.equals(order.getUserId())) {
            throw new IllegalArgumentException("无权查看该订单物流");
        }
        return buildTrace(order);
    }

    private List<LogisticsTrace> buildTrace(Order order) {
        List<LogisticsTrace> nodes = new ArrayList<>();
        String company = (order.getShippingCompany() == null || order.getShippingCompany().isBlank())
                ? "快递" : order.getShippingCompany();
        String destCity = cityOf(order.getReceiverAddress());
        LocalDateTime now = LocalDateTime.now();

        LocalDateTime t0 = parse(order.getTrackingNo() == null ? null : order.getShipTime());
        // 未发货（无单号或无发货时间）：商品在【江西景德镇】仓库待发货，仍展示所在位置
        if (order.getTrackingNo() == null || order.getTrackingNo().isBlank() || t0 == null) {
            LocalDateTime at = firstNonNull(parse(order.getPayTime()), parse(order.getCreatedAt()), now);
            nodes.add(new LogisticsTrace(FMT.format(at), "待发货",
                    "商品在【" + ORIGIN + "】仓库打包中，等待商家发货"));
            return nodes;
        }

        boolean completed = "COMPLETED".equalsIgnoreCase(order.getStatus());

        // 轨迹脚本：相对发货时间的偏移（小时），仅展示已到达的节点
        addIfDue(nodes, t0, 0, now, "已揽收", "【揽收】" + company + " 已在【" + ORIGIN + "】揽收快件");
        addIfDue(nodes, t0, 3, now, "运输中", "快件离开【" + ORIGIN + "】，发往 " + HUB);
        addIfDue(nodes, t0, 12, now, "运输中", "快件已到达【" + HUB + "】转运中心");
        addIfDue(nodes, t0, 24, now, "运输中", "快件已到达【" + destCity + "】转运中心");
        addIfDue(nodes, t0, 30, now, "派送中", "快件正在【" + destCity + "】派送，请保持电话畅通");

        // 已签收：到达签收时间，或订单已「确认收货」时出现
        LocalDateTime signAt = t0.plusHours(40);
        if (completed || !now.isBefore(signAt)) {
            LocalDateTime st = (completed && now.isBefore(signAt)) ? now : signAt;
            nodes.add(new LogisticsTrace(FMT.format(st), "已签收", "快件已签收，签收人：本人，感谢使用 " + company));
        }

        Collections.reverse(nodes); // 最新节点在前
        return nodes;
    }

    /** 始发地（发货城市），统一按江西景德镇处理。 */
    private static final String ORIGIN = "江西景德镇";

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T v : values) {
            if (v != null) return v;
        }
        return null;
    }

    private static final String HUB = "杭州";

    private void addIfDue(List<LogisticsTrace> nodes, LocalDateTime t0, int offsetHours,
                          LocalDateTime now, String status, String desc) {
        LocalDateTime at = t0.plusHours(offsetHours);
        if (!now.isBefore(at)) {
            nodes.add(new LogisticsTrace(FMT.format(at), status, desc));
        }
    }

    /** 粗略取收货地址前缀作为目的城市名（模拟展示用）。 */
    private String cityOf(String address) {
        if (address == null || address.isBlank()) return "目的城市";
        String a = address.trim();
        return a.length() > 6 ? a.substring(0, 6) : a;
    }

    private LocalDateTime parse(String s) {
        if (s == null || s.isBlank()) return null;
        String v = s.trim().replace('T', ' ');
        try {
            if (v.length() >= 16) {
                return LocalDateTime.parse(v.substring(0, 16), FMT);
            }
        } catch (Exception ignored) {
            // 落到下面的兜底解析
        }
        try {
            return LocalDateTime.parse(v.substring(0, 19),
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception e) {
            return null;
        }
    }
}
