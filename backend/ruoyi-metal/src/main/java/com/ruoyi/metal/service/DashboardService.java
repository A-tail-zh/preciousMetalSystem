package com.ruoyi.metal.service;

import java.util.*;
import java.time.*;
import java.math.*;
import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.metal.catalog.QuotationSaveRequest;
import com.ruoyi.metal.security.FactoryScope;

/**
 * 首页概览与报价单业务服务，负责业务校验、数据处理和相关持久化操作。
 */
@Service
public class DashboardService {
    private final JdbcTemplate jdbc;
    private final FactoryScope scope;
    private final QuoteRuleService quotes;

    public DashboardService(JdbcTemplate jdbc, FactoryScope scope, QuoteRuleService quotes) {
        this.jdbc = jdbc;
        this.scope = scope;
        this.quotes = quotes;
    }

    private String filter(Long factory, String alias) {
        return factory == null ? "" : " AND " + alias + ".factory_id=" + factory;
    }

    private List<Map<String, Object>> data(String sql, Object... args) {
        return jdbc.queryForList(sql, args).stream().map(row -> {
            var result = new LinkedHashMap<String, Object>();
            row.forEach((k, v) -> {
                if (v instanceof BigDecimal || k.endsWith("Id") || k.equals("id"))
                    result.put(k, v == null ? null : v.toString());
                else result.put(k, v);
            });
            return (Map<String, Object>) result;
        }).toList();
    }

    /**
     * 获取当前用户可访问的首页概览数据。
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Map<String, Object> dashboard() {
        Long factory = scope.currentFactory();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai")), month = today.withDayOfMonth(1), start = today.minusDays(29);
        String w = filter(factory, "w"), r = filter(factory, "r"), q = filter(factory, "q");
        var work = data("SELECT COUNT(*) total,COALESCE(SUM(w.status='IN_PROGRESS'),0) inProgress,COALESCE(SUM(w.status='COMPLETED'),0) completed FROM metal_work_order w WHERE w.del_flag='0'" + w).get(0);
        var warnings = data("SELECT COALESCE(SUM(w.status='PENDING'),0) pending,COALESCE(SUM(DATE(w.trigger_time)=?),0) newToday,COALESCE(SUM(DATE(w.handled_time)=? AND w.status='HANDLED'),0) handledToday FROM metal_warning_record w WHERE w.source_void=FALSE AND w.del_flag='0'" + w, today, today).get(0);
        Long quoteCount = jdbc.queryForObject("SELECT COUNT(*) FROM metal_saved_quotation q WHERE q.create_time>=? AND q.create_time<?" + q, Long.class, month, today.plusDays(1));
        var cats = data("SELECT q.category_labels categories FROM metal_saved_quotation q WHERE q.create_time>=? AND q.create_time<?" + q, month, today.plusDays(1));
        var categories = new LinkedHashSet<String>();
        for (var row : cats) categories.addAll(Arrays.asList(row.get("categories").toString().split("、")));
        var trend = data("SELECT DATE_FORMAT(r.create_time,'%Y-%m-%d') day,CAST(SUM(r.loss_weight) AS DECIMAL(20,6)) loss FROM metal_work_report r WHERE r.voided=FALSE AND r.create_time>=? AND r.create_time<?" + r + " GROUP BY DATE_FORMAT(r.create_time,'%Y-%m-%d') ORDER BY DATE_FORMAT(r.create_time,'%Y-%m-%d')", start, today.plusDays(1));
        var byDay = new HashMap<String, String>();
        trend.forEach(x -> byDay.put(x.get("day").toString(), x.get("loss").toString()));
        var completeTrend = new ArrayList<Map<String, Object>>();
        for (LocalDate d = start; !d.isAfter(today); d = d.plusDays(1))
            completeTrend.add(Map.of("day", d.toString(), "loss", byDay.getOrDefault(d.toString(), "0.000000")));
        var ranking = data("SELECT CAST(r.process_id AS CHAR) processId,p.process_name processName,CAST(SUM(r.loss_weight) AS DECIMAL(20,6)) loss FROM metal_work_report r JOIN metal_process p ON p.process_id=r.process_id WHERE r.voided=FALSE AND r.create_time>=? AND r.create_time<?" + r + " GROUP BY r.process_id,p.process_name ORDER BY SUM(r.loss_weight) DESC,r.process_id LIMIT 5", month, today.plusDays(1));
        var factories = data("SELECT CAST(f.factory_id AS CHAR) factoryId,f.factory_name factoryName,CAST(COALESCE((SELECT SUM(r.loss_weight) FROM metal_work_report r WHERE r.factory_id=f.factory_id AND r.voided=FALSE AND r.create_time>=? AND r.create_time<?),0) AS DECIMAL(20,6)) loss FROM metal_factory f WHERE f.del_flag='0'" + (factory == null ? "" : " AND f.factory_id=" + factory) + " ORDER BY f.factory_id", month, today.plusDays(1));
        var distribution = data("SELECT CAST(w.factory_id AS CHAR) factoryId,f.factory_name factoryName,COUNT(*) count FROM metal_warning_record w JOIN metal_factory f ON f.factory_id=w.factory_id WHERE w.source_void=FALSE AND w.del_flag='0' AND w.trigger_time>=? AND w.trigger_time<?" + w + " GROUP BY w.factory_id,f.factory_name ORDER BY w.factory_id", month, today.plusDays(1));
        var center = data("SELECT CAST(w.warning_id AS CHAR) id,CAST(w.factory_id AS CHAR) factoryId,DATE_FORMAT(w.trigger_time,'%Y-%m-%d %H:%i:%s') triggerTime,f.factory_name factoryName,w.process_name processName,CAST(w.threshold_value AS CHAR) thresholdValue,w.status FROM metal_warning_record w JOIN metal_factory f ON f.factory_id=w.factory_id WHERE w.source_void=FALSE AND w.del_flag='0'" + w + " ORDER BY w.trigger_time DESC,w.warning_id DESC LIMIT 6");
        return Map.of("workOrders", work, "warnings", warnings, "quotations", Map.of("count", quoteCount, "categories", categories), "trend", completeTrend, "ranking", ranking, "factories", factories, "distribution", distribution, "warningCenter", center, "asOf", today.toString(), "metal", "铱Ir");
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    /**
     * 保存当前报价单及其业务快照。
     */
    public Map<String, Object> saveQuotation(QuotationSaveRequest input) {
        scope.lockFactories(List.of(input.factoryId()));
        String fingerprint;
        try {
            String canonical = input.factoryId() + ":" + input.items().stream().map(i -> i.category() + ":" + i.quantity() + ":" + new TreeMap<>(i.dimensions())).toList();
            fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        var existing = jdbc.queryForList("SELECT * FROM metal_saved_quotation WHERE request_id=? FOR UPDATE", input.requestId());
        if (!existing.isEmpty()) {
            var row = existing.get(0);
            if (!fingerprint.equals(row.get("request_hash")) || !SecurityUtils.getUserId().equals(((Number) row.get("request_actor")).longValue()))
                throw new ServiceException("同一请求标识不能保存不同报价内容");
            return quotation(((Number) row.get("quote_id")).longValue());
        }
        jdbc.queryForList("SELECT rule_id FROM metal_quote_rule WHERE rule_id=1 FOR UPDATE");
        var rule = quotes.get();
        var snapshots = new ArrayList<Map<String, Object>>();
        var categories = new LinkedHashSet<String>();
        BigDecimal total = BigDecimal.ZERO;
        for (var item : input.items()) {
            var calculation = quotes.calculate(item);
            var snapshot = new LinkedHashMap<String, Object>(calculation);
            snapshot.put("rule", Map.of("basePrice", rule.getBasePrice().toPlainString(), "piValue", rule.getPiValue().toPlainString(), "version", rule.getVersion()));
            snapshot.put("category", item.category());
            snapshot.put("quantity", item.quantity());
            snapshot.put("dimensions", new TreeMap<>(item.dimensions()));
            snapshots.add(snapshot);
            categories.add(item.category());
            total = total.add(new BigDecimal(calculation.get("totalPrice").toString()));
        }
        if (total.precision() - total.scale() > 26) throw new ServiceException("报价合计超出保存范围");
        String no = nextQuote();
        jdbc.update("INSERT INTO metal_saved_quotation(quote_no,request_id,request_hash,request_actor,factory_id,category_labels,item_snapshot,total_price,rule_version) VALUES(?,?,?,?,?,?,?,?,?)", no, input.requestId(), fingerprint, SecurityUtils.getUserId(), input.factoryId(), String.join("、", categories), JSON.toJSONString(snapshots), total, rule.getVersion());
        Long id = jdbc.queryForObject("SELECT quote_id FROM metal_saved_quotation WHERE request_id=?", Long.class, input.requestId());
        return quotation(id);
    }

    private String nextQuote() {
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        jdbc.update("INSERT IGNORE INTO metal_stock_sequence(sequence_date,prefix,sequence_value) VALUES(?,'QT',0)", date);
        Integer current = jdbc.queryForObject("SELECT sequence_value FROM metal_stock_sequence WHERE sequence_date=? AND prefix='QT' FOR UPDATE", Integer.class, date);
        if (current >= 999999) throw new ServiceException("当日报价单编号已用完");
        jdbc.update("UPDATE metal_stock_sequence SET sequence_value=sequence_value+1 WHERE sequence_date=? AND prefix='QT'", date);
        return "QT" + date.format(DateTimeFormatter.BASIC_ISO_DATE) + String.format("%06d", current + 1);
    }

    /**
     * 读取报价单并校验所属工厂的访问权限。
     */
    public Map<String, Object> quotation(Long id) {
        var values = data("SELECT CAST(quote_id AS CHAR) id,quote_no quoteNo,CAST(factory_id AS CHAR) factoryId,category_labels categories,CAST(total_price AS CHAR) totalPrice,CAST(item_snapshot AS CHAR) items,rule_version ruleVersion,DATE_FORMAT(create_time,'%Y-%m-%d %H:%i:%s') createTime FROM metal_saved_quotation WHERE quote_id=?", id);
        if (values.isEmpty()) throw new ServiceException("报价单不存在");
        var result = new LinkedHashMap<>(values.get(0));
        scope.check(Long.valueOf(result.get("factoryId").toString()));
        result.put("items", JSON.parseArray(result.get("items").toString()));
        var factories = data("SELECT factory_name name FROM metal_factory WHERE factory_id=?", result.get("factoryId"));
        result.put("factoryName", factories.isEmpty() ? result.get("factoryId") : factories.get(0).get("name"));
        return result;
    }

    /**
     * 获取当前用户可选择的报价工厂。
     */
    public List<Map<String, Object>> quotationFactories() {
        Long factory = scope.currentFactory();
        return data("SELECT CAST(factory_id AS CHAR) id,factory_name name FROM metal_factory WHERE del_flag='0'" + (factory == null ? "" : " AND factory_id=" + factory) + " ORDER BY factory_id");
    }
}
