package com.ruoyi.metal.service;

import java.util.*;

import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.metal.catalog.QuoteRule;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;

/**
 * 报价规则业务服务，负责业务校验、数据处理和相关持久化操作。
 */
@Service
public class QuoteRuleService {
    private final JdbcTemplate jdbc;

    public QuoteRuleService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 读取当前报价规则。
     */
    public QuoteRule get() {
        return jdbc.queryForObject("SELECT base_price,pi_value,version FROM metal_quote_rule WHERE rule_id=1", (r, n) -> {
            QuoteRule q = new QuoteRule();
            q.setBasePrice(r.getBigDecimal(1));
            q.setPiValue(r.getBigDecimal(2));
            q.setVersion(r.getInt(3));
            return q;
        });
    }

    /**
     * 校验版本后保存报价规则。
     */
    @Transactional
    public void update(QuoteRule value) {
        if (jdbc.update("UPDATE metal_quote_rule SET base_price=?,pi_value=?,version=version+1,update_by=?,update_time=NOW() WHERE rule_id=1 AND version=?", value.getBasePrice(), value.getPiValue(), SecurityUtils.getUsername(), value.getVersion()) != 1)
            throw new ServiceException("配置已被修改，请刷新后重试");
    }

    /**
     * 使用当前报价规则计算产品体积并返回结果。
     */
    public Map<String, Object> calculate(com.ruoyi.metal.catalog.CalculationRequest input) {
        QuoteRule rule = get();
        var volume = VolumeCalculator.calculate(input.category(), input.dimensions(), rule.getPiValue());
        var stored = VolumeCalculator.stored(volume);
        var price = volume.multiply(rule.getBasePrice());
        return Map.of("volume", stored.toPlainString(), "unitPrice", price.setScale(6, java.math.RoundingMode.HALF_UP).toPlainString(), "totalPrice", price.multiply(java.math.BigDecimal.valueOf(input.quantity())).setScale(6, java.math.RoundingMode.HALF_UP).toPlainString(), "rule", rule);
    }

    /**
     * 获取报价所需的基础资料和规则上下文。
     */
    public Map<String, Object> context() {
        List<Map<String, Object>> products = jdbc.queryForList("SELECT CAST(product_id AS CHAR) id,product_name name,product_category category,specification,dimensions FROM metal_product WHERE del_flag='0' ORDER BY product_id");
        products.forEach(p -> p.put("dimensions", JSON.parseObject(p.get("dimensions").toString())));
        return Map.of("rule", get(), "products", products, "calculationEnabled", true);
    }
}
