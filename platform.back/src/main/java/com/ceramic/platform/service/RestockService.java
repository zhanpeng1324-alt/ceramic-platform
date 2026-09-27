package com.ceramic.platform.service;

import com.ceramic.platform.dto.RestockSuggestion;
import com.ceramic.platform.entity.Product;
import com.ceramic.platform.mapper.ProductMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 备货建议计算。
 *
 * 思路：用近 30 天真实销量估算日均销量，再据此判断库存是否偏低，并给出建议补货量。
 * - 安全库存 = 日均销量 × 7 天（快销品阈值自动更高，慢销品保底 {@link #BASE_THRESHOLD}）
 * - 补货目标 = 备够 30 天用量（同样保底 {@link #BASE_THRESHOLD}）
 * - 建议补货量 = 补货目标 − 当前库存
 * 只返回需要关注（缺货/紧急/偏低）的商品，按紧急程度排序。
 */
@Service
public class RestockService {

    /** 销量统计窗口（天） */
    private static final int WINDOW_DAYS = 30;
    /** 基础库存阈值，与看板“低库存”口径一致 */
    private static final int BASE_THRESHOLD = 10;
    /** 判定“紧急”的可售天数 */
    private static final int URGENT_DAYS = 7;

    private final ProductMapper productMapper;

    public RestockService(ProductMapper productMapper) {
        this.productMapper = productMapper;
    }

    public List<RestockSuggestion> suggest() {
        // 近 30 天各商品销量
        Map<Long, Integer> soldMap = new HashMap<>();
        for (Map<String, Object> row : productMapper.salesInLastDays(WINDOW_DAYS)) {
            Object pid = row.get("product_id");
            Object qty = row.get("sold_qty");
            if (pid instanceof Number && qty instanceof Number) {
                soldMap.put(((Number) pid).longValue(), ((Number) qty).intValue());
            }
        }

        List<RestockSuggestion> list = new ArrayList<>();
        for (Product p : productMapper.findAll()) {
            int stock = p.getStock() == null ? 0 : p.getStock();
            int sold30 = soldMap.getOrDefault(p.getId(), 0);
            double avgDaily = sold30 / (double) WINDOW_DAYS;

            int threshold = Math.max(BASE_THRESHOLD, (int) Math.ceil(avgDaily * URGENT_DAYS));
            int target = Math.max(BASE_THRESHOLD, (int) Math.ceil(avgDaily * WINDOW_DAYS));
            int suggested = Math.max(0, target - stock);
            Integer daysLeft = avgDaily > 0 ? (int) Math.floor(stock / avgDaily) : null;

            String level;
            String reason;
            if (stock == 0) {
                level = "OUT";
                reason = sold30 > 0 ? "已缺货，近30天售出 " + sold30 + " 件" : "已缺货";
            } else if (daysLeft != null && daysLeft <= URGENT_DAYS) {
                level = "URGENT";
                reason = "预计 " + daysLeft + " 天内售罄（日均约 " + String.format("%.1f", avgDaily) + " 件）";
            } else if (stock <= threshold) {
                level = "WARNING";
                reason = "库存偏低（低于安全库存 " + threshold + " 件）";
            } else {
                continue; // 库存充足，无需提示
            }

            RestockSuggestion s = new RestockSuggestion();
            s.setProductId(p.getId());
            s.setName(p.getName());
            s.setImageUrl(p.getImageUrl());
            s.setStock(stock);
            s.setSold30(sold30);
            s.setSuggestedRestock(suggested);
            s.setDaysLeft(daysLeft);
            s.setLevel(level);
            s.setReason(reason);
            list.add(s);
        }

        // 排序：缺货 > 紧急 > 偏低；同级按可售天数、库存升序
        Map<String, Integer> order = Map.of("OUT", 0, "URGENT", 1, "WARNING", 2);
        list.sort(Comparator
                .comparingInt((RestockSuggestion s) -> order.getOrDefault(s.getLevel(), 9))
                .thenComparingInt(s -> s.getDaysLeft() == null ? Integer.MAX_VALUE : s.getDaysLeft())
                .thenComparingInt(RestockSuggestion::getStock));
        return list;
    }
}
