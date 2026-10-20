package com.ruoyi.metal.service;

import java.util.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.math.*;
import java.sql.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.metal.catalog.WarehouseRequests.*;
import com.ruoyi.metal.catalog.WarehouseWorkOrderContext;
import com.ruoyi.metal.security.FactoryScope;

@Service
@Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
/**
 * 仓储业务服务，负责业务校验、数据处理和相关持久化操作。
 */
public class WarehouseService {
    private final JdbcTemplate jdbc;
    private final FactoryScope scope;

    public WarehouseService(JdbcTemplate jdbc, FactoryScope scope) {
        this.jdbc = jdbc;
        this.scope = scope;
    }

    private static ServiceException fail(String message) {
        return new ServiceException(message);
    }

    private static Long number(Object v) {
        return v == null ? null : Long.valueOf(v.toString());
    }

    private static BigDecimal decimal(Object v) {
        return v == null ? BigDecimal.ZERO : new BigDecimal(v.toString());
    }

    private static String str(Object v) {
        return v == null ? null : v.toString();
    }

    private static String actor() {
        String n = SecurityUtils.getLoginUser().getUser().getNickName();
        return n == null ? SecurityUtils.getUsername() : n;
    }

    private void permit(String p) {
        if (!scope.isAdmin() && !SecurityUtils.getLoginUser().getPermissions().contains(p))
            throw new ServiceException("没有此操作权限", 403);
    }

    private long insert(String sql, Object... args) {
        KeyHolder holder = new GeneratedKeyHolder();
        jdbc.update(c -> {
            PreparedStatement p = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) p.setObject(i + 1, args[i]);
            return p;
        }, holder);
        return holder.getKey().longValue();
    }

    private List<Map<String, Object>> rows(String sql, Object... args) {
        return jdbc.queryForList(sql, args);
    }

    private Map<String, Object> one(String sql, Object... args) {
        var r = rows(sql, args);
        if (r.isEmpty()) throw fail("记录不存在或已归档");
        return r.get(0);
    }

    private Map<String, Object> clean(Map<String, Object> input) {
        var result = new LinkedHashMap<String, Object>();
        input.forEach((k, v) -> {
            StringBuilder n = new StringBuilder();
            boolean upper = false;
            for (char c : k.toCharArray()) {
                if (c == '_') {
                    upper = true;
                    continue;
                }
                n.append(upper ? Character.toUpperCase(c) : c);
                upper = false;
            }
            String key = n.toString();
            Object value = v;
            if (v instanceof BigDecimal) value = v.toString();
            else if (v instanceof java.sql.Timestamp t)
                value = t.toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            else if (v instanceof LocalDateTime t) value = t.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            else if (v instanceof java.sql.Date) value = v.toString();
            else if (v instanceof Number && (key.equals("id") || key.endsWith("Id") || key.endsWith("Actor")))
                value = v.toString();
            result.put(key, value);
        });
        return result;
    }

    private List<Map<String, Object>> cleaned(List<Map<String, Object>> data) {
        return data.stream().map(this::clean).toList();
    }

    public Map<String, Object> listOrders(int page, int size, com.ruoyi.metal.catalog.OrderListQuery q) {
        var p = new BusinessQueries(scope).orders(q);
        var args = new ArrayList<>(p.args());
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM metal_warehouse_order t WHERE " + p.sql(), Long.class, args.toArray());
        args.add(size);
        args.add((page - 1) * size);
        var data = rows("SELECT t.*,t.order_id id" + extraColumns("warehouse-order") + " FROM metal_warehouse_order t WHERE " + p.sql() + " ORDER BY t.order_id LIMIT ? OFFSET ?", args.toArray());
        return Map.of("rows", cleaned(data), "total", total, "code", 200, "msg", "查询成功");
    }

    private record Resource(String table, String pk, String extra, String where) {
    }

    private Resource resource(String key) {
        return switch (key) {
            case "warehouse" -> new Resource("metal_warehouse", "warehouse_id", "", "t.del_flag='0'");
            case "inventory" ->
                    new Resource("metal_inventory", "inventory_id", " LEFT JOIN metal_warehouse h ON h.warehouse_id=t.warehouse_id", "t.del_flag='0'");
            case "warehouse-order" -> new Resource("metal_warehouse_order", "order_id", "", "1=1");
            case "process-batch" ->
                    new Resource("metal_process_batch", "batch_id", " LEFT JOIN metal_work_order w ON w.work_order_id=t.work_order_id LEFT JOIN metal_work_order_step s ON s.item_id=t.item_id AND s.process_id=t.process_id", "1=1");
            case "work-report" ->
                    new Resource("metal_work_report", "report_id", " LEFT JOIN metal_process_batch b ON b.batch_id=t.batch_id LEFT JOIN metal_work_order w ON w.work_order_id=b.work_order_id LEFT JOIN metal_work_order_step s ON s.item_id=t.item_id AND s.process_id=t.process_id", "1=1");
            case "warning-rule" ->
                    new Resource("metal_warning_rule", "rule_id", " LEFT JOIN metal_product p ON p.product_id=t.product_id LEFT JOIN metal_process s ON s.process_id=t.process_id", "t.del_flag='0'");
            case "warning-record" -> new Resource("metal_warning_record", "warning_id", "", "t.del_flag='0'");
            default -> throw fail("资源无效");
        };
    }

    private String reportWeightColumns(String alias) {
        String base = " FROM metal_warehouse_order o JOIN metal_warehouse_order_line l ON l.order_id=o.order_id WHERE o.batch_id=" + alias + ".batch_id AND o.status='POSTED' AND o.type ";
        return ",CASE WHEN " + alias + ".voided=FALSE THEN (SELECT COALESCE(SUM(l.net_weight),0)" + base + "='RAW_RETURN') ELSE NULL END raw_returned_weight,CASE WHEN " + alias + ".voided=FALSE THEN (SELECT COALESCE(SUM(l.net_weight),0)" + base + "IN ('SEMI_IN','FINISHED_IN')) ELSE NULL END output_weight";
    }

    private String extraColumns(String key) {
        return switch (key) {
            case "warehouse" ->
                    ",(SELECT factory_name FROM metal_factory f WHERE f.factory_id=t.factory_id) factory_name";
            case "warehouse-order" ->
                    ",(SELECT order_no FROM metal_work_order w WHERE w.work_order_id=t.work_order_id) work_order_no,(SELECT o.order_no FROM metal_warehouse_order_line l JOIN metal_warehouse_order o ON o.order_id=l.order_id WHERE l.line_id=(SELECT MIN(x.source_line_id) FROM metal_warehouse_order_line x WHERE x.order_id=t.order_id)) source_order_no";
            case "inventory" ->
                    ",h.name warehouse_name,EXISTS(SELECT 1 FROM metal_warehouse_order_line l JOIN metal_warehouse_order o ON o.order_id=l.order_id WHERE l.inventory_id=t.inventory_id AND (o.type NOT IN ('OPENING','ADJUST','RAW_IN','FINISHED_IN','SEMI_IN') OR o.batch_id IS NOT NULL)) business_referenced";
            case "process-batch" -> ",w.order_no,s.process_name";
            case "work-report" -> ",w.order_no,s.process_name" + reportWeightColumns("t");
            case "warning-rule" ->
                    ",p.product_name,s.process_name,(SELECT factory_name FROM metal_factory f WHERE f.factory_id=t.factory_id) factory_name";
            default -> "";
        };
    }

    /**
     * 查询列表并补充关联资料。
     */
    public Map<String, Object> list(String key, int page, int size, String filter, Long factoryId) {
        Resource r = resource(key);
        Long own = scope.currentFactory();
        if (factoryId != null) {
            scope.check(factoryId);
            own = factoryId;
        }
        List<Object> args = new ArrayList<>();
        String where = r.where();
        if (own != null) {
            where += " AND t.factory_id=?";
            args.add(own);
        }
        if (filter != null && !filter.isBlank()) {
            if (key.equals("inventory")) {
                if (!Set.of("RAW", "FINISHED", "SEMI").contains(filter)) throw fail("库存分类无效");
                where += " AND t.category=?";
                args.add(filter);
            } else if (key.equals("warehouse-order")) {
                if (!Set.of("OUT", "IN", "RET", "ADJ").contains(filter)) throw fail("单据分类无效");
                where += " AND t.kind=?";
                args.add(filter);
            }
        }
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM " + r.table() + " t WHERE " + where, Long.class, args.toArray());
        args.add(size);
        args.add((page - 1) * size);
        var data = rows("SELECT t.*,t." + r.pk() + " id" + extraColumns(key) + " FROM " + r.table() + " t" + r.extra() + " WHERE " + where + " ORDER BY t." + r.pk() + " LIMIT ? OFFSET ?", args.toArray());
        return Map.of("rows", cleaned(data), "total", total, "code", 200, "msg", "查询成功");
    }

    private Map<String, Object> record(String key, Long id, boolean lock) {
        Resource r = resource(key);
        var row = one("SELECT t.*,t." + r.pk() + " id FROM " + r.table() + " t WHERE " + r.pk() + "=? AND " + r.where() + (lock ? " FOR UPDATE" : ""), id);
        scope.check(number(row.get("factory_id")));
        return row;
    }

    private Map<String, Object> lock(String key, Long id) {
        var initial = record(key, id, false);
        scope.lockFactories(List.of(number(initial.get("factory_id"))));
        return record(key, id, true);
    }

    private void version(Map<String, Object> row, Integer version) {
        if (version == null || !number(row.get("version")).equals(version.longValue()))
            throw fail("记录已被修改，请刷新后重试");
    }

    /**
     * 查询指定记录详情；业务层执行相应的访问权限检查。
     */
    public Map<String, Object> detail(String key, Long id) {
        var row = record(key, id, false);
        var result = clean(row);
        result.put("factoryName", str(one("SELECT factory_name FROM metal_factory WHERE factory_id=?", row.get("factory_id")).get("factory_name")));
        if (key.equals("warehouse-order")) {
            if (row.get("batch_id") != null) {
                var batchInfo = one("SELECT batch_no,status,version,planned_quantity,completed_quantity FROM metal_process_batch WHERE batch_id=?", row.get("batch_id"));
                result.put("batchNo", batchInfo.get("batch_no"));
                result.put("batchStatus", batchInfo.get("status"));
                result.put("batchVersion", batchInfo.get("version"));
                result.put("plannedQuantity", batchInfo.get("planned_quantity"));
                result.put("completedQuantity", batchInfo.get("completed_quantity"));
            }
            result.put("operatorIds", JSON.parseArray(str(row.get("operator_ids"))));
            if (row.get("work_order_id") != null)
                result.put("workOrderNo", str(one("SELECT order_no FROM metal_work_order WHERE work_order_id=?", row.get("work_order_id")).get("order_no")));
            var sources = rows("SELECT DISTINCT o.order_no FROM metal_warehouse_order_line x JOIN metal_warehouse_order_line l ON l.line_id=x.source_line_id JOIN metal_warehouse_order o ON o.order_id=l.order_id WHERE x.order_id=? ORDER BY o.order_no", id);
            result.put("sourceOrderNo", sources.stream().map(x -> str(x.get("order_no"))).collect(java.util.stream.Collectors.joining("、")));
        }
        if (key.equals("process-batch") || key.equals("work-report")) {
            Long work = key.equals("process-batch") ? number(row.get("work_order_id")) : number(one("SELECT work_order_id FROM metal_process_batch WHERE batch_id=?", row.get("batch_id")).get("work_order_id"));
            result.put("orderNo", str(one("SELECT order_no FROM metal_work_order WHERE work_order_id=?", work).get("order_no")));
            result.put("processName", str(step(number(row.get("item_id")), number(row.get("process_id"))).get("process_name")));
        }
        if (key.equals("warning-rule")) {
            result.put("productName", str(one("SELECT product_name FROM metal_product WHERE product_id=?", row.get("product_id")).get("product_name")));
            result.put("processName", str(one("SELECT process_name FROM metal_process WHERE process_id=?", row.get("process_id")).get("process_name")));
        }
        if (key.equals("inventory")) {
            result.put("businessReferenced", !rows("SELECT l.line_id FROM metal_warehouse_order_line l JOIN metal_warehouse_order o ON o.order_id=l.order_id WHERE l.inventory_id=? AND (o.type NOT IN ('OPENING','ADJUST','RAW_IN','FINISHED_IN','SEMI_IN') OR o.batch_id IS NOT NULL) LIMIT 1", id).isEmpty());
            result.put("warehouseName", str(one("SELECT name FROM metal_warehouse WHERE warehouse_id=?", row.get("warehouse_id")).get("name")));
            result.put("ledger", cleaned(rows("SELECT l.*,l.ledger_id id,o.order_no,CASE WHEN o.type<>'ADJUST' THEN NULL WHEN l.reversal_of IS NOT NULL THEN CASE WHEN NULLIF(TRIM(o.reason),'') IS NOT NULL THEN CONCAT('冲销：',o.reason) ELSE NULL END WHEN NULLIF(TRIM(d.remark),'') IS NOT NULL THEN d.remark WHEN o.status='POSTED' AND NULLIF(TRIM(o.reason),'') IS NOT NULL THEN o.reason ELSE NULL END adjustment_reason,(CASE WHEN l.reversal_of IS NULL THEN 1 ELSE -1 END)*(CASE WHEN o.kind='OUT' THEN -1 ELSE 1 END)*d.gross_weight delta_gross_weight,(CASE WHEN l.reversal_of IS NULL THEN 1 ELSE -1 END)*(CASE WHEN o.kind='OUT' THEN -1 ELSE 1 END)*d.impurity delta_impurity FROM metal_stock_ledger l JOIN metal_warehouse_order o ON o.order_id=l.order_id LEFT JOIN metal_warehouse_order_line d ON d.line_id=l.line_id WHERE l.inventory_id=? ORDER BY l.ledger_id", id)));
        }
        if (key.equals("warehouse-order"))
            result.put("lines", cleaned(rows("SELECT *,line_id id FROM metal_warehouse_order_line WHERE order_id=? ORDER BY sequence_no", id)));
        if (key.equals("process-batch")) {
            result.put("orders", cleaned(rows("SELECT *,order_id id FROM metal_warehouse_order WHERE batch_id=? ORDER BY order_id", id)));
            result.put("reports", cleaned(rows("SELECT *,report_id id FROM metal_work_report WHERE batch_id=?", id)));
        }
        if (key.equals("work-report")) {
            result.putAll(clean(one("SELECT " + reportWeightColumns("t").substring(1) + " FROM metal_work_report t WHERE t.report_id=?", id)));
            var reportBatch = one("SELECT version,status FROM metal_process_batch WHERE batch_id=?", row.get("batch_id"));
            result.put("batchVersion", reportBatch.get("version"));
            result.put("batchStatus", reportBatch.get("status"));
            result.put("materials", cleaned(rows("SELECT *,report_material_id id FROM metal_report_material WHERE report_id=? ORDER BY code", id)));
            result.put("orders", cleaned(rows("SELECT *,order_id id FROM metal_warehouse_order WHERE batch_id=? ORDER BY order_id", row.get("batch_id"))));
        }
        if (key.equals("warning-record")) {
            result.put("report", detail("work-report", number(row.get("report_id"))));
            result.put("history", cleaned(rows("SELECT * FROM metal_warning_history WHERE warning_id=? ORDER BY history_id", id)));
        }
        return result;
    }

    /**
     * 校验工厂和版本后新增或修改库房，新增请求使用请求标识防止重复创建。
     */
    public String saveWarehouse(Warehouse input) {
        if (input.id() == null) {
            scope.lockFactories(List.of(input.factoryId()));
            uuid(input.requestId());
            String existing = creation("warehouse", input.requestId(), fingerprint(input));
            if (existing != null) return existing;
        }
        String saved = saveWarehouseInternal(input);
        if (input.id() == null) remember("warehouse", input.requestId(), fingerprint(input), saved);
        return saved;
    }

    private String saveWarehouseInternal(Warehouse input) {
        scope.lockFactories(List.of(input.factoryId()));
        if (input.name().isBlank()) throw fail("请输入库房名称");
        String status = input.status() == null ? "0" : input.status();
        if (input.id() == null)
            return Long.toString(insert("INSERT INTO metal_warehouse(factory_id,name,sort_order,status,create_by) VALUES(?,?,?,?,?)", input.factoryId(), input.name().strip(), input.sortOrder(), status, SecurityUtils.getUsername()));
        var old = lock("warehouse", input.id());
        version(old, input.version());
        if (!number(old.get("factory_id")).equals(input.factoryId())) throw fail("库房不能转移工厂");
        jdbc.update("UPDATE metal_warehouse SET name=?,sort_order=?,status=?,version=version+1,update_time=NOW() WHERE warehouse_id=?", input.name().strip(), input.sortOrder(), status, input.id());
        return input.id().toString();
    }

    /**
     * 拒绝已停用的预警规则配置入口。
     */
    public String saveRule(Rule input) {
        throw fail("预警规则已停用，预警按工单工序理论损耗判断");
    }

    private String saveRuleInternal(Rule input) {
        scope.lockFactories(List.of(input.factoryId()));
        if (rows("SELECT p.product_id FROM metal_product p JOIN metal_product_process x ON x.product_id=p.product_id JOIN metal_process s ON s.process_id=x.process_id WHERE p.product_id=? AND x.process_id=? AND p.del_flag='0' AND s.del_flag='0' FOR UPDATE", input.productId(), input.processId()).isEmpty())
            throw fail("工序必须属于所选有效产品工艺");
        String status = input.status() == null ? "0" : input.status();
        if (input.id() == null) {
            var existing = rows("SELECT * FROM metal_warning_rule WHERE factory_id=? AND product_id=? AND process_id=? FOR UPDATE", input.factoryId(), input.productId(), input.processId());
            if (!existing.isEmpty()) {
                if (!"2".equals(existing.get(0).get("del_flag"))) throw fail("该产品工序已有预警规则");
                Long id = number(existing.get(0).get("rule_id"));
                jdbc.update("UPDATE metal_warning_rule SET tolerance=?,status=?,del_flag='0',version=version+1 WHERE rule_id=?", input.tolerance(), status, id);
                return id.toString();
            }
            return Long.toString(insert("INSERT INTO metal_warning_rule(factory_id,product_id,process_id,tolerance,status) VALUES(?,?,?,?,?)", input.factoryId(), input.productId(), input.processId(), input.tolerance(), status));
        }
        var old = lock("warning-rule", input.id());
        version(old, input.version());
        if (!number(old.get("factory_id")).equals(input.factoryId()) || !number(old.get("product_id")).equals(input.productId()) || !number(old.get("process_id")).equals(input.processId()))
            throw fail("已有规则不能改变工厂、产品或工序，请新增规则");
        jdbc.update("UPDATE metal_warning_rule SET tolerance=?,status=?,version=version+1 WHERE rule_id=?", input.tolerance(), status, input.id());
        return input.id().toString();
    }

    /**
     * 检查资源类型及业务引用后批量归档记录。
     */
    public void archive(String key, Long[] ids) {
        if (!Set.of("warehouse", "inventory", "warning-rule", "warning-record").contains(key) || ids.length < 1 || ids.length > 100)
            throw fail("请选择1～100条可归档记录");
        var sorted = Arrays.stream(ids).distinct().sorted().toList();
        var initial = sorted.stream().map(id -> record(key, id, false)).toList();
        scope.lockFactories(initial.stream().map(r -> number(r.get("factory_id"))).toList());
        for (Long id : sorted) {
            var row = record(key, id, true);
            if (key.equals("warehouse") && (!rows("SELECT inventory_id FROM metal_inventory WHERE warehouse_id=? LIMIT 1", id).isEmpty() || !rows("SELECT order_id FROM metal_warehouse_order WHERE warehouse_id=? LIMIT 1", id).isEmpty()))
                throw fail("库房存在库存或历史单据引用");
            if (key.equals("inventory") && (decimal(row.get("net_weight")).signum() != 0 || number(row.get("quantity")) != 0 || !rows("SELECT l.line_id FROM metal_warehouse_order_line l JOIN metal_warehouse_order o ON o.order_id=l.order_id WHERE l.inventory_id=? AND (o.type NOT IN ('OPENING','ADJUST','RAW_IN','FINISHED_IN','SEMI_IN') OR o.batch_id IS NOT NULL) LIMIT 1", id).isEmpty()))
                throw fail("库存须为零且无业务引用");
            if (key.equals("warning-record") && "PENDING".equals(row.get("status")) && !truth(row.get("source_void")))
                throw fail("待处理预警不能归档");
            Resource r = resource(key);
            jdbc.update("UPDATE " + r.table() + " SET del_flag='2',version=version+1 WHERE " + r.pk() + "=?", id);
            if (key.equals("warning-record")) history(id, "ARCHIVE", "归档记录");
        }
    }

    private static boolean truth(Object v) {
        return Boolean.TRUE.equals(v) || (v instanceof Number n && n.intValue() != 0);
    }

    private String creation(String kind, String requestId, String hash) {
        var values = rows("SELECT * FROM metal_warehouse_request WHERE request_id=? FOR UPDATE", requestId);
        if (values.isEmpty()) return null;
        var r = values.get(0);
        if (!kind.equals(r.get("kind")) || !hash.equals(r.get("request_hash")) || !SecurityUtils.getUserId().equals(number(r.get("actor_id"))))
            throw fail("请求标识已用于其他内容");
        return str(r.get("result_id"));
    }

    private void remember(String kind, String id, String hash, String result) {
        jdbc.update("INSERT INTO metal_warehouse_request(request_id,kind,request_hash,actor_id,result_id) VALUES(?,?,?,?,?)", id, kind, hash, SecurityUtils.getUserId(), number(result));
    }

    private String fingerprint(Object request) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(request.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void uuid(String value) {
        try {
            if (!UUID.fromString(value).toString().equalsIgnoreCase(value)) throw fail("无效UUID");
        } catch (IllegalArgumentException e) {
            throw fail("请求标识必须为UUID");
        }
    }

    private String duplicate(String table, String pk, String requestId, String hash) {
        uuid(requestId);
        var data = rows("SELECT " + pk + ",request_hash,request_actor,status FROM " + table + " WHERE request_id=? FOR UPDATE", requestId);
        if (data.isEmpty()) return null;
        var row = data.get(0);
        if (!SecurityUtils.getUserId().equals(number(row.get("request_actor"))) || !hash.equals(row.get("request_hash")))
            throw fail("同一请求标识不能提交不同内容");
        if ("VOID".equals(row.get("status"))) throw fail("原请求已冲销，请使用新的请求标识");
        return str(row.get(pk));
    }

    private String next(String prefix) {
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        jdbc.update("INSERT INTO metal_stock_sequence(sequence_date,prefix,sequence_value) VALUES(?,?,0) ON DUPLICATE KEY UPDATE sequence_value=sequence_value", date, prefix);
        var row = one("SELECT sequence_value FROM metal_stock_sequence WHERE sequence_date=? AND prefix=? FOR UPDATE", date, prefix);
        long value = number(row.get("sequence_value")) + 1;
        if (value > 999999) throw fail("当日单据编号已用完");
        jdbc.update("UPDATE metal_stock_sequence SET sequence_value=? WHERE sequence_date=? AND prefix=?", value, date, prefix);
        return prefix + date.format(DateTimeFormatter.BASIC_ISO_DATE) + String.format("%06d", value);
    }

    private Map<String, Object> workOrder(Long id) {
        var w = one("SELECT * FROM metal_work_order WHERE work_order_id=? AND del_flag='0' FOR UPDATE", id);
        scope.check(number(w.get("factory_id")));
        if (!Set.of("WAITING_MATERIAL", "IN_PROGRESS", "COMPLETED").contains(w.get("status")))
            throw fail("该工单不能执行生产业务");
        return w;
    }

    private Map<String, Object> step(Long itemId, Long processId) {
        return one("SELECT s.*,i.product_id,i.quantity item_quantity,i.product_snapshot,i.work_order_id FROM metal_work_order_step s JOIN metal_work_order_item i ON i.item_id=s.item_id WHERE s.item_id=? AND s.process_id=?", itemId, processId);
    }

    private long sumQuantity(Long itemId, int seq, String status) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(" + (status.equals("OPEN") ? "planned_quantity" : "completed_quantity") + "),0) FROM metal_process_batch WHERE item_id=? AND sequence_no=? AND status=?", Long.class, itemId, seq, status);
    }

    /**
     * 校验工单、工序和可加工数量，并按请求标识创建加工批次。
     */
    public String createBatch(Batch input) {
        var initial = one("SELECT factory_id FROM metal_work_order WHERE work_order_id=? AND del_flag='0'", input.workOrderId());
        scope.lockFactories(List.of(number(initial.get("factory_id"))));
        String hash = fingerprint(input), old = duplicate("metal_process_batch", "batch_id", input.requestId(), hash);
        if (old != null) return old;
        var w = workOrder(input.workOrderId());
        if ("COMPLETED".equals(w.get("status"))) throw fail("已完成工单不能新增加工批次");
        var s = step(input.itemId(), input.processId());
        if (!input.workOrderId().equals(number(s.get("work_order_id")))) throw fail("产品明细不属于所选工单");
        int seq = number(s.get("sequence_no")).intValue();
        long max = seq == 1 ? number(s.get("item_quantity")) : sumQuantity(input.itemId(), seq - 1, "SETTLED");
        long used = sumQuantity(input.itemId(), seq, "SETTLED") + sumQuantity(input.itemId(), seq, "OPEN");
        if (input.plannedQuantity() > max - used) throw fail("加工数量超过工单或前工序可用数量");
        long saved = insert("INSERT INTO metal_process_batch(request_id,request_hash,request_actor,factory_id,work_order_id,item_id,process_id,sequence_no,planned_quantity) VALUES(?,?,?,?,?,?,?,?,?)", input.requestId(), hash, SecurityUtils.getUserId(), w.get("factory_id"), input.workOrderId(), input.itemId(), input.processId(), seq, input.plannedQuantity());
        var product = JSON.parseObject(str(s.get("product_snapshot")));
        LocalDateTime stamp = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        String no;
        do {
            no = w.get("order_no") + "-" + stamp.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")) + "-" + product.get("code");
            stamp = stamp.plusNanos(1000000);
        } while (!rows("SELECT batch_id FROM metal_process_batch WHERE batch_no=?", no).isEmpty());
        jdbc.update("UPDATE metal_process_batch SET batch_no=? WHERE batch_id=?", no, saved);
        return Long.toString(saved);
    }

    private Map<String, Object> warehouse(Long id, Long factoryId, boolean active) {
        var h = one("SELECT * FROM metal_warehouse WHERE warehouse_id=? AND del_flag='0' FOR UPDATE", id);
        if (!factoryId.equals(number(h.get("factory_id")))) throw new ServiceException("库房不属于本厂", 403);
        if (active && !"0".equals(h.get("status"))) throw fail("库房已停用");
        return h;
    }

    private String person(Long id, Long factoryId, boolean required) {
        if (id == null) {
            if (required) throw fail("请选择经手人、仓管员或领用人");
            return null;
        }
        var data = rows("SELECT COALESCE(NULLIF(nick_name,''),user_name) name FROM sys_user WHERE user_id=? AND factory_id=? AND status='0' AND del_flag='0' FOR UPDATE", id, factoryId);
        if (data.isEmpty()) throw fail("单据人员必须属于本厂且启用");
        return str(data.get(0).get("name"));
    }

    private Map<String, Object> source(String category, Long referenceId) {
        if (category == null) throw fail("请选择库存分类");
        if (referenceId == null || referenceId <= 0) throw fail("请选择物料或产品");
        if ("RAW".equals(category))
            return one("SELECT material_id id,material_code code,material_type name,specification FROM metal_material WHERE material_id=? AND del_flag='0' FOR UPDATE", referenceId);
        if (Set.of("FINISHED", "SEMI").contains(category))
            return one("SELECT product_id id,product_code code,product_name name,specification,purity FROM metal_product WHERE product_id=? AND del_flag='0' FOR UPDATE", referenceId);
        throw fail("库存分类无效");
    }

    private record Type(String kind, String category, int sign) {
    }

    private Type type(String value) {
        return switch (value) {
            case "PRODUCTION_OUT" -> new Type("OUT", null, -1);
            case "PRODUCT_OUT" -> new Type("OUT", "FINISHED", -1);
            case "EXCHANGE_OUT" -> new Type("OUT", "RAW", -1);
            case "SEMI_OUT" -> new Type("OUT", "SEMI", -1);
            case "RAW_IN" -> new Type("IN", "RAW", 1);
            case "FINISHED_IN" -> new Type("IN", "FINISHED", 1);
            case "SEMI_IN" -> new Type("IN", "SEMI", 1);
            case "RAW_RETURN" -> new Type("RET", "RAW", 1);
            case "PRODUCT_RETURN" -> new Type("RET", "FINISHED", 1);
            case "OPENING", "ADJUST" -> new Type("ADJ", null, 1);
            default -> throw fail("单据类型无效");
        };
    }

    /**
     * 校验请求归属和单据类别，预留可重复获取的仓储单据编号。
     */
    public String reserveOrderNumber(String requestId, String kind) {
        uuid(requestId);
        if (!Set.of("OUT", "IN", "RET", "ADJ").contains(kind)) throw fail("单据类别无效");
        jdbc.update("INSERT INTO metal_warehouse_number(request_id,actor_id,kind) VALUES(?,?,?) ON DUPLICATE KEY UPDATE request_id=request_id", requestId, SecurityUtils.getUserId(), kind);
        var row = one("SELECT actor_id,kind,order_no FROM metal_warehouse_number WHERE request_id=? FOR UPDATE", requestId);
        if (!SecurityUtils.getUserId().equals(number(row.get("actor_id"))) || !kind.equals(row.get("kind")))
            throw fail("编号申请归属或单据类别不一致");
        if (row.get("order_no") != null) return str(row.get("order_no"));
        String no = next(kind);
        jdbc.update("UPDATE metal_warehouse_number SET order_no=? WHERE request_id=?", no, requestId);
        return no;
    }

    /**
     * 校验并过账仓储单据，同时更新相应库存。
     */
    public String post(Order input) {
        if (Set.of("OPENING", "ADJUST").contains(input.type())) throw fail("请使用期初或调整接口");
        return postInternal(input, false);
    }

    /**
     * 校验期初录入权限和单据类型后登记期初库存。
     */
    public String opening(Order input) {
        permit("metal:inventory:add");
        if (!"OPENING".equals(input.type()) || input.batchId() != null) throw fail("期初类型无效");
        return postInternal(input, true);
    }

    private Order autoBind(Order x) {
        if (x.workOrderId() == null || x.itemId() == null || x.processId() == null)
            throw fail("请选择生产工单、产品明细和工序");
        var w = workOrder(x.workOrderId());
        if (!x.factoryId().equals(number(w.get("factory_id")))) throw fail("工单不属于所选工厂");
        var s = step(x.itemId(), x.processId());
        if (!x.workOrderId().equals(number(s.get("work_order_id")))) throw fail("产品明细不属于工单");
        var open = rows("SELECT * FROM metal_process_batch WHERE item_id=? AND process_id=? AND status='OPEN' ORDER BY batch_id FOR UPDATE", x.itemId(), x.processId());
        if (open.size() > 1) throw fail("存在多个历史未结算批次，请先结算或冲销多余批次");
        Long id;
        if (open.isEmpty()) {
            if (!x.type().equals("PRODUCTION_OUT")) throw fail("请先生产领料，系统将自动生成批次号");
            int seq = number(s.get("sequence_no")).intValue();
            long available = (seq == 1 ? number(s.get("item_quantity")) : sumQuantity(x.itemId(), seq - 1, "SETTLED")) - sumQuantity(x.itemId(), seq, "SETTLED");
            if (available <= 0) throw fail("当前工序没有可加工数量，请先完成前道工序");
            id = Long.valueOf(createBatch(new Batch(UUID.randomUUID().toString(), x.workOrderId(), x.itemId(), x.processId(), (int) available)));
        } else id = number(open.get(0).get("batch_id"));
        var b = record("process-batch", id, true);
        return new Order(x.requestId(), x.factoryId(), x.warehouseId(), x.type(), x.orderDate(), x.operatorId(), x.operatorIds(), x.keeperId(), x.recipientId(), x.inspectorId(), x.reviewerId(), x.workOrderId(), id, number(b.get("version")).intValue(), x.settle(), x.completedQuantity(), x.reason(), x.lines(), x.itemId(), x.processId());
    }

    private String postInternal(Order input, boolean internal) {
        scope.lockFactories(List.of(input.factoryId()));
        Type t = type(input.type());
        String reservedNo = reserveOrderNumber(input.requestId(), t.kind());
        String hash = fingerprint(input), existing = duplicate("metal_warehouse_order", "order_id", input.requestId(), hash);
        if (existing != null) return existing;
        if (input.itemId() != null || input.processId() != null) input = autoBind(input);
        lockPostingSources(input);
        var h = warehouse(input.warehouseId(), input.factoryId(), true);
        List<Long> operators = input.operatorIds() == null ? (input.operatorId() == null ? List.of() : List.of(input.operatorId())) : input.operatorIds();
        if ((operators.isEmpty() && !input.type().equals("PRODUCTION_OUT")) || operators.size() > 20 || new HashSet<>(operators).size() != operators.size())
            throw fail("请选择1至20位不重复经办人");
        if (input.operatorId() != null && (operators.isEmpty() || !input.operatorId().equals(operators.get(0))))
            throw fail("经办人字段不一致");
        var operatorNames = new LinkedHashMap<Long, String>();
        for (Long operator : new TreeSet<>(operators))
            operatorNames.put(operator, person(operator, input.factoryId(), true));
        String op = operators.stream().map(operatorNames::get).collect(java.util.stream.Collectors.joining("、")), keeper = person(input.keeperId(), input.factoryId(), true), recipient = person(input.recipientId(), input.factoryId(), Set.of("PRODUCTION_OUT", "SEMI_OUT").contains(input.type())), inspector = person(input.inspectorId(), input.factoryId(), false), reviewer = person(input.reviewerId(), input.factoryId(), false);
        Map<String, Object> batch = null, w = null, s = null;
        if (input.batchId() != null) {
            batch = record("process-batch", input.batchId(), true);
            if (!input.factoryId().equals(number(batch.get("factory_id")))) throw fail("批次不属于所选工厂");
            version(batch, input.batchVersion());
            if (!"OPEN".equals(batch.get("status"))) throw fail("批次已结算或已冲销");
            if (!Set.of("PRODUCTION_OUT", "RAW_RETURN", "FINISHED_IN", "SEMI_IN").contains(input.type()))
                throw fail("该单据类型不能参与生产批次");
            w = workOrder(number(batch.get("work_order_id")));
            s = step(number(batch.get("item_id")), number(batch.get("process_id")));
            if (input.workOrderId() != null && !input.workOrderId().equals(number(batch.get("work_order_id"))))
                throw fail("生产工单和批次不一致");
        } else if (Set.of("PRODUCTION_OUT", "RAW_RETURN").contains(input.type()) || Boolean.TRUE.equals(input.settle()))
            throw fail("生产领料、回料和结算必须关联加工批次");
        if (input.workOrderId() != null && batch == null && Set.of("FINISHED_IN", "SEMI_IN").contains(input.type()))
            throw fail("生产产出须关联加工批次；外部入库请不选择生产工单");
        if (input.workOrderId() != null && w == null) {
            w = workOrder(input.workOrderId());
            if (!input.factoryId().equals(number(w.get("factory_id")))) throw fail("工单不属于所选工厂");
        }
        String no = reservedNo;
        Long workId = batch == null ? input.workOrderId() : number(batch.get("work_order_id"));
        long docId = insert("INSERT INTO metal_warehouse_order(order_no,request_id,request_hash,request_actor,factory_id,warehouse_id,kind,type,order_date,work_order_id,batch_id,operator_id,keeper_id,recipient_id,inspector_id,reviewer_id,operator_name,keeper_name,recipient_name,inspector_name,reviewer_name,warehouse_name,reason) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", no, input.requestId(), hash, SecurityUtils.getUserId(), input.factoryId(), input.warehouseId(), t.kind(), input.type(), input.orderDate(), workId, input.batchId(), operators.isEmpty() ? null : operators.get(0), input.keeperId(), input.recipientId(), input.inspectorId(), input.reviewerId(), op, keeper, recipient, inspector, reviewer, h.get("name"), input.reason());
        jdbc.update("UPDATE metal_warehouse_order SET operator_ids=? WHERE order_id=?", JSON.toJSONString(operators.stream().map(String::valueOf).toList()), docId);
        if (input.type().equals("RAW_RETURN")) {
            var sourceIds = input.lines().stream().map(Line::sourceLineId).filter(Objects::nonNull).toList();
            if (!sourceIds.isEmpty()) {
                String placeholders = String.join(",", Collections.nCopies(sourceIds.size(), "?"));
                Long count = jdbc.queryForObject("SELECT COUNT(DISTINCT order_id) FROM metal_warehouse_order_line WHERE line_id IN (" + placeholders + ")", Long.class, sourceIds.toArray());
                if (count != null && count > 1) throw fail("原料还库的物料明细必须来自同一张生产出库工单");
            }
        }
        int index = 0;
        for (Line line : input.lines()) {
            index++;
            postLine(input, line, t, docId, no, index, batch, s);
        }
        if (batch != null && Set.of("SEMI_IN", "FINISHED_IN", "RAW_RETURN").contains(input.type()))
            validateBatchWeight(input.batchId());
        if (batch != null) {
            jdbc.update("UPDATE metal_process_batch SET version=version+1 WHERE batch_id=?", input.batchId());
            if (input.type().equals("PRODUCTION_OUT") && "WAITING_MATERIAL".equals(w.get("status")))
                jdbc.update("UPDATE metal_work_order SET status='IN_PROGRESS',version=version+1,update_time=NOW() WHERE work_order_id=?", workId);
            if (Boolean.TRUE.equals(input.settle())) {
                permit("metal:process-batch:settle");
                if (!Set.of("RAW_RETURN", "FINISHED_IN", "SEMI_IN").contains(input.type()))
                    throw fail("只有回料或产出单据可结算");
                settleInternal(record("process-batch", input.batchId(), true), input.completedQuantity());
            }
        }
        return Long.toString(docId);
    }

    private void lockBatchSources(Map<String, Object> batch) {
        var s = step(number(batch.get("item_id")), number(batch.get("process_id")));
        source("FINISHED", number(s.get("product_id")));
        if (rows("SELECT process_id FROM metal_process WHERE process_id=? AND del_flag='0' FOR UPDATE", batch.get("process_id")).isEmpty())
            throw fail("工序已删除");
    }

    private void lockPostingSources(Order input) {
        var refs = new TreeMap<String, Map.Entry<String, Long>>();
        for (Line line : input.lines()) {
            String category = line.category();
            Long reference = line.referenceId();
            if (line.inventoryId() != null) {
                var stock = record("inventory", line.inventoryId(), false);
                if (!input.factoryId().equals(number(stock.get("factory_id"))))
                    throw new ServiceException("库存不属于所选工厂", 403);
                category = str(stock.get("category"));
                reference = number(stock.get("reference_id"));
            } else if (line.sourceLineId() != null) {
                var origin = one("SELECT l.category,l.reference_id,o.factory_id FROM metal_warehouse_order_line l JOIN metal_warehouse_order o ON o.order_id=l.order_id WHERE l.line_id=?", line.sourceLineId());
                scope.check(number(origin.get("factory_id")));
                if (!input.factoryId().equals(number(origin.get("factory_id"))))
                    throw new ServiceException("来源单据不属于本厂", 403);
                category = str(origin.get("category"));
                reference = number(origin.get("reference_id"));
            }
            if (category != null && reference != null)
                refs.put((category.equals("RAW") ? "M" : "P") + String.format("%020d", reference), Map.entry(category, reference));
        }
        if (input.batchId() != null) {
            var batch = record("process-batch", input.batchId(), false);
            if (!input.factoryId().equals(number(batch.get("factory_id"))))
                throw new ServiceException("批次不属于本厂", 403);
            var step = step(number(batch.get("item_id")), number(batch.get("process_id")));
            Long product = number(step.get("product_id"));
            refs.put("P" + String.format("%020d", product), Map.entry("FINISHED", product));
        }
        for (var ref : refs.values()) source(ref.getKey(), ref.getValue());
        if (input.batchId() != null) {
            var batch = record("process-batch", input.batchId(), false);
            if (rows("SELECT process_id FROM metal_process WHERE process_id=? AND del_flag='0' FOR UPDATE", batch.get("process_id")).isEmpty())
                throw fail("工序已删除");
        }
    }

    private void postLine(Order input, Line line, Type t, long docId, String no, int sequence, Map<String, Object> batch, Map<String, Object> step) {
        BigDecimal weight = line.netWeight();
        long qty = line.quantity() == null ? 0 : line.quantity();
        boolean adjust = input.type().equals("ADJUST");
        if (!adjust && (weight.signum() < 0 || (!"RAW_RETURN".equals(input.type()) && weight.signum() == 0) || qty < 0 || qty > 1000000))
            throw fail("RAW_RETURN".equals(input.type()) ? "净重不能小于0，数量须为合法非负整数" : "净重须大于0，数量须为合法非负整数");
        if (line.grossWeight() != null) {
            BigDecimal impurity = line.impurity() == null ? BigDecimal.ZERO : line.impurity();
            if ((!adjust && (line.grossWeight().signum() < 0 || impurity.signum() < 0 || impurity.compareTo(line.grossWeight()) > 0)) || line.grossWeight().subtract(impurity).compareTo(weight) != 0)
                throw fail("净重须等于毛重减杂质");
        }
        Map<String, Object> stock = null, origin = null;
        String category = line.category();
        Long reference = line.referenceId();
        String batchNo = line.batchNo();
        if (line.sourceLineId() != null) {
            origin = one("SELECT l.*,o.type source_type,o.status source_status,o.factory_id source_factory,o.batch_id source_batch FROM metal_warehouse_order_line l JOIN metal_warehouse_order o ON o.order_id=l.order_id WHERE l.line_id=? FOR UPDATE", line.sourceLineId());
            scope.check(number(origin.get("source_factory")));
            if (!input.factoryId().equals(number(origin.get("source_factory"))) || !"POSTED".equals(origin.get("source_status")))
                throw fail("来源出库单无效");
            boolean raw = input.type().equals("RAW_RETURN");
            if (raw && (!"PRODUCTION_OUT".equals(origin.get("source_type")) || !Objects.equals(input.batchId(), number(origin.get("source_batch"))) || !"RAW".equals(origin.get("category"))))
                throw fail("原料还库须关联同批次原料领料行");
            if (input.type().equals("PRODUCT_RETURN") && !"PRODUCT_OUT".equals(origin.get("source_type")))
                throw fail("成品退还须关联产品出库行");
            if (!Set.of("RAW_RETURN", "PRODUCT_RETURN").contains(input.type())) throw fail("该单据不能关联退还来源");
            var sum = one("SELECT COALESCE(SUM(l.net_weight),0) weight,COALESCE(SUM(l.quantity),0) quantity FROM metal_warehouse_order_line l JOIN metal_warehouse_order o ON o.order_id=l.order_id WHERE l.source_line_id=? AND o.status='POSTED'", line.sourceLineId());
            if (decimal(sum.get("weight")).add(weight).compareTo(decimal(origin.get("net_weight"))) > 0 || number(sum.get("quantity")) + qty > number(origin.get("quantity")))
                throw fail("累计退还超过原出库数量或净重");
            if (raw) {
                var allowance = RawReturnAllowance.load(jdbc, input.batchId());
                RawReturnAllowance.require(decimal(origin.get("net_weight")).subtract(decimal(sum.get("weight"))), allowance.remaining(), weight, allowance.status());
            }
            category = str(origin.get("category"));
            reference = number(origin.get("reference_id"));
            batchNo = str(origin.get("batch_no"));
        } else if (Set.of("RAW_RETURN", "PRODUCT_RETURN").contains(input.type())) throw fail("请选择原出库明细");
        if (t.sign() < 0 || adjust) {
            if (line.inventoryId() == null) throw fail("请选择库存记录");
            stock = record("inventory", line.inventoryId(), true);
            if (!input.factoryId().equals(number(stock.get("factory_id"))) || !input.warehouseId().equals(number(stock.get("warehouse_id"))))
                throw fail("库存不属于本厂所选库房");
            category = str(stock.get("category"));
            reference = number(stock.get("reference_id"));
            batchNo = str(stock.get("batch_no"));
        }
        if (stock != null && (t.sign() < 0 || adjust)) validateOwnership(stock);
        if (category == null || reference == null) throw fail("请选择物料、产品或库存来源");
        if (t.category() != null && !t.category().equals(category)) throw fail("物料分类与单据类型不匹配");
        if ("RAW".equals(category)) {
            if (qty != 0) throw fail("原材料按净重管理，数量须为0");
        } else if (!adjust && qty < 1) throw fail("成品、半成品数量须大于0");
        if (t.sign() < 0 && stock != null && number(stock.get("origin_batch_id")) != null) {
            var originBatch = record("process-batch", number(stock.get("origin_batch_id")), true);
            if (!"SETTLED".equals(originBatch.get("status")))
                throw fail("产出所属加工批次尚未结算或已冲销，不能出库，请先完成批次结算");
        }
        if (input.type().equals("PRODUCTION_OUT")) {
            if (!Set.of("RAW", "SEMI").contains(category)) throw fail("生产领料仅支持原料或前工序半成品");
            if ("SEMI".equals(category)) {
                Long upstream = number(stock.get("origin_batch_id"));
                if (upstream == null) throw fail("半成品须来自前工序生产批次");
                var prev = record("process-batch", upstream, true);
                if (!Objects.equals(reference, number(step.get("product_id"))) || !Objects.equals(prev.get("work_order_id"), batch.get("work_order_id")) || !Objects.equals(prev.get("factory_id"), batch.get("factory_id")) || !"SETTLED".equals(prev.get("status")) || !prev.get("item_id").equals(batch.get("item_id")) || number(prev.get("sequence_no")) + 1 != number(batch.get("sequence_no")))
                    throw fail("半成品必须来自同一产品明细的前一道已结算工序");
            }
        }
        if (batch != null && Set.of("FINISHED_IN", "SEMI_IN").contains(input.type())) {
            if (!reference.equals(number(step.get("product_id")))) throw fail("产出产品必须属于批次产品明细");
            long last = jdbc.queryForObject("SELECT MAX(sequence_no) FROM metal_work_order_step WHERE item_id=?", Long.class, batch.get("item_id"));
            if (input.type().equals("FINISHED_IN") && number(batch.get("sequence_no")) != last)
                throw fail("只有最后一道工序可登记成品产出");
            if (input.type().equals("SEMI_IN") && number(batch.get("sequence_no")) == last)
                throw fail("最后一道工序请登记成品产出");
            if (batchNo != null && !batchNo.isBlank()) throw fail("生产批次号由系统生成");
            long registered = jdbc.queryForObject("SELECT COALESCE(SUM(l.quantity),0) FROM metal_warehouse_order o JOIN metal_warehouse_order_line l ON l.order_id=o.order_id WHERE o.batch_id=? AND o.status='POSTED' AND o.type IN ('SEMI_IN','FINISHED_IN')", Long.class, input.batchId());
            validateOutputQuantity(number(batch.get("planned_quantity")), registered, qty);
            validateDownstreamOutput(batch, registered + qty);
            batchNo = str(batch.get("batch_no"));
        }
        String organization = normalizeOrganization(line.organization());
        if (batch != null && Set.of("FINISHED_IN", "SEMI_IN").contains(input.type()))
            organization = str(workOrder(number(batch.get("work_order_id"))).get("customer"));
        else if (origin != null) organization = normalizeOrganization(str(origin.get("organization")));
        else if (stock != null) organization = normalizeOrganization(str(stock.get("organization")));

        var source = source(category, reference);
        if (batchNo == null || batchNo.isBlank())
            batchNo = "RAW".equals(category) ? "" : input.orderDate().toString().replace("-", "") + "-" + no + "-" + sequence;
        Long expectedOrigin = batch != null && !"RAW".equals(category) ? input.batchId() : null;
        if (origin != null && !"RAW".equals(category))
            expectedOrigin = number(one("SELECT origin_batch_id FROM metal_inventory WHERE inventory_id=?", origin.get("inventory_id")).get("origin_batch_id"));
        if (origin != null && "RAW".equals(category)) {
            stock = record("inventory", number(origin.get("inventory_id")), true);
            if (!input.warehouseId().equals(number(stock.get("warehouse_id"))))
                throw fail("原料还库须退回原出库库存所在仓库");
            if (!"0".equals(stock.get("del_flag"))) throw fail("原出库库存已归档，请先核对来源");
        }
        if (stock == null) {
            var existing = "RAW".equals(category) ? rows("SELECT *,inventory_id id FROM metal_inventory WHERE factory_id=? AND warehouse_id=? AND category=? AND reference_id=? AND batch_no=? AND raw_organization=? AND ownership_status='NORMAL' FOR UPDATE", input.factoryId(), input.warehouseId(), category, reference, batchNo, organization) : rows("SELECT *,inventory_id id FROM metal_inventory WHERE factory_id=? AND warehouse_id=? AND category=? AND reference_id=? AND batch_no=? FOR UPDATE", input.factoryId(), input.warehouseId(), category, reference, batchNo);
            if (existing.isEmpty()) {
                Long originBatch = expectedOrigin;
                long stockId = insert("INSERT INTO metal_inventory(factory_id,warehouse_id,category,reference_id,batch_no,code,name,specification,purity,organization,origin_batch_id) VALUES(?,?,?,?,?,?,?,?,?,?,?)", input.factoryId(), input.warehouseId(), category, reference, batchNo, source.get("code"), source.get("name"), source.get("specification"), line.purity(), organization, originBatch);
                stock = record("inventory", stockId, true);
            } else {
                stock = existing.get(0);
                validateProductStockIdentity(category, str(stock.get("organization")), organization, number(stock.get("origin_batch_id")), expectedOrigin);
                if (!"0".equals(stock.get("del_flag"))) {
                    if (decimal(stock.get("net_weight")).signum() != 0 || number(stock.get("quantity")) != 0 || !rows("SELECT l.line_id FROM metal_warehouse_order_line l JOIN metal_warehouse_order o ON o.order_id=l.order_id WHERE l.inventory_id=? AND (o.type NOT IN ('OPENING','ADJUST','RAW_IN','FINISHED_IN','SEMI_IN') OR o.batch_id IS NOT NULL) LIMIT 1", stock.get("inventory_id")).isEmpty())
                        throw fail("归档库存存在余额或业务引用，不能恢复");
                    jdbc.update("UPDATE metal_inventory SET del_flag='0',version=version+1,update_time=NOW() WHERE inventory_id=? AND del_flag='2'", stock.get("inventory_id"));
                    stock.put("del_flag", "0");
                }
            }
        }
        Long stockId = number(stock.get("inventory_id"));
        long lineId = insert("INSERT INTO metal_warehouse_order_line(order_id,inventory_id,source_line_id,sequence_no,category,reference_id,code,name,specification,batch_no,net_weight,quantity,gross_weight,impurity,purity,organization,purpose,remark,receiving_organization) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", docId, stockId, line.sourceLineId(), sequence, category, reference, stock.get("code"), stock.get("name"), stock.get("specification"), batchNo, weight, qty, line.grossWeight(), line.impurity(), line.purity(), organization, line.purpose(), line.remark(), line.receivingOrganization());
        balance(stock, weight.multiply(BigDecimal.valueOf(t.sign())), qty * t.sign(), docId, lineId, null);
    }

    static void validateProductStockIdentity(String category, String existing, String incoming, Long origin, Long expectedOrigin) {
        validateCustomerIdentity(category, existing, incoming);
        if (!"RAW".equals(category) && !Objects.equals(origin, expectedOrigin))
            throw fail("相同批次库存的生产来源不一致，请使用其他批次号或核对退还来源");
    }

    static void validateCustomerIdentity(String category, String existing, String incoming) {
        if (!"RAW".equals(category) && !normalizeOrganization(existing).equals(normalizeOrganization(incoming)))
            throw fail("同一批次库存的所属客户不同，不能合并；请核对客户或更换批次号");
    }

    static String normalizeOrganization(String value) {
        return value == null ? "" : value.trim();
    }

    static void validateOwnership(Map<String, Object> stock) {
        if ("RAW".equals(stock.get("category")) && "REVIEW".equals(stock.get("ownership_status")))
            throw fail("该库存来源待核对，暂不能新增出库或调整，请先核实各单位余额");
    }

    private void validateBatchWeight(Long batchId) {
        var totals = one("SELECT COALESCE(SUM(CASE WHEN o.type='PRODUCTION_OUT' THEN l.net_weight ELSE 0 END),0) issued,COALESCE(SUM(CASE WHEN o.type IN ('RAW_RETURN','SEMI_IN','FINISHED_IN') THEN l.net_weight ELSE 0 END),0) returned FROM metal_warehouse_order o JOIN metal_warehouse_order_line l ON l.order_id=o.order_id WHERE o.batch_id=? AND o.status='POSTED'", batchId);
        validateProductionWeight(decimal(totals.get("issued")), decimal(totals.get("returned")));
    }

    static void validateProductionWeight(BigDecimal issued, BigDecimal returned) {
        if (issued.signum() <= 0) throw fail("本批次尚未领料，不能登记产出或还料");
        if (returned.compareTo(issued) > 0) throw fail("本批次累计产出净重与还料净重之和不能超过累计领料净重");
    }

    /**
     * 后续工序只按有效上游半成品领料计件，补充原料不产生可完成件数。
     */
    static void validateDownstreamQuantity(long sequence, long issued, long output) {
        if (sequence > 1 && (issued <= 0 || output > issued))
            throw fail("后续工序须先领用上一工序已结算半成品，累计产出及完成件数不能超过实际领用件数；历史异常请先人工核对");
    }

    static long eligibleSemiQuantity(JdbcTemplate jdbc, Map<String, Object> batch) {
        Long issued = jdbc.queryForObject("SELECT COALESCE(SUM(l.quantity),0) FROM metal_warehouse_order o JOIN metal_warehouse_order_line l ON l.order_id=o.order_id JOIN metal_inventory i ON i.inventory_id=l.inventory_id JOIN metal_process_batch p ON p.batch_id=i.origin_batch_id WHERE o.batch_id=? AND o.status='POSTED' AND o.type='PRODUCTION_OUT' AND l.category='SEMI' AND i.category='SEMI' AND p.status='SETTLED' AND p.item_id=? AND p.sequence_no=? AND p.work_order_id=? AND p.factory_id=o.factory_id AND l.reference_id=i.reference_id", Long.class, batch.get("batch_id"), batch.get("item_id"), number(batch.get("sequence_no")) - 1, batch.get("work_order_id"));
        return issued == null ? 0 : issued;
    }

    private void validateDownstreamOutput(Map<String, Object> batch, long output) {
        if (number(batch.get("sequence_no")) > 1)
            validateDownstreamQuantity(number(batch.get("sequence_no")), eligibleSemiQuantity(jdbc, batch), output);
    }

    static void validateOutputQuantity(long planned, long registered, long incoming) {
        if (incoming < 1 || registered > planned - incoming) throw fail("累计产出数量不能超过批次计划数量");
    }

    private void balance(Map<String, Object> stock, BigDecimal delta, long qty, long doc, long line, Long reversal) {
        BigDecimal result = decimal(stock.get("net_weight")).add(delta);
        long quantity = number(stock.get("quantity")) + qty;
        if (result.signum() < 0 || quantity < 0) throw fail("库存不足，不能过账或冲销");
        if (!"RAW".equals(stock.get("category")) && ((quantity == 0) != (result.signum() == 0)))
            throw fail("成品或半成品的数量与净重须同时归零或同时保留");
        jdbc.update("UPDATE metal_inventory SET net_weight=?,quantity=?,version=version+1,update_time=NOW() WHERE inventory_id=?", result, quantity, stock.get("inventory_id"));
        insert("INSERT INTO metal_stock_ledger(inventory_id,order_id,line_id,delta_weight,delta_quantity,balance_weight,balance_quantity,reversal_of) VALUES(?,?,?,?,?,?,?,?)", stock.get("inventory_id"), doc, line, delta, qty, result, quantity, reversal);
        stock.put("net_weight", result);
        stock.put("quantity", quantity);
    }

    /**
     * 按版本号修改库存基础信息，并限制已有单据引用的记录。
     */
    public void editStock(Stock input) {
        var stock = lock("inventory", input.id());
        version(stock, input.version());
        validateOwnership(stock);
        if ("RAW".equals(stock.get("category")) && !normalizeOrganization(str(stock.get("organization"))).equals(normalizeOrganization(input.organization())) && !rows("SELECT line_id FROM metal_warehouse_order_line WHERE inventory_id=? LIMIT 1", input.id()).isEmpty())
            throw fail("已有入库记录的原料不能修改来源单位");
        if (!rows("SELECT l.line_id FROM metal_warehouse_order_line l JOIN metal_warehouse_order o ON o.order_id=l.order_id WHERE l.inventory_id=? AND (o.type NOT IN ('OPENING','ADJUST','RAW_IN','FINISHED_IN','SEMI_IN') OR o.batch_id IS NOT NULL) LIMIT 1", input.id()).isEmpty())
            throw fail("库存已被业务引用，资料快照不能修改");
        jdbc.update("UPDATE metal_inventory SET specification=?,organization=?,purity=?,version=version+1,update_time=NOW() WHERE inventory_id=?", input.specification(), normalizeOrganization(input.organization()), input.purity(), input.id());
    }

    /**
     * 校验库存调整权限，并按请求标识生成调整单据。
     */
    static void validateAdjustmentComponents(String category, BigDecimal net, long quantity, BigDecimal gross, BigDecimal impurity) {
        if ((gross == null) != (impurity == null)) throw fail("毛重差额和杂质差额须同时填写");
        if (gross != null) {
            if (!"RAW".equals(category)) throw fail("仅原材料支持毛重和杂质差额");
            if (gross.subtract(impurity).compareTo(net) != 0) throw fail("净重差额须等于毛重差额减杂质差额");
        }
        if (net.signum() == 0 && quantity == 0 && (gross == null || gross.signum() == 0) && (impurity == null || impurity.signum() == 0))
            throw fail("调整差额不能全部为0");
    }

    public String adjustStock(Long id, Adjustment input) {
        permit("metal:inventory:adjust");
        var stock = lock("inventory", id);
        uuid(input.requestId());
        String hash = fingerprint(input.toString() + ":" + id);
        String prior = creation("inventory-adjust", input.requestId(), hash);
        if (prior != null) return prior;
        validateAdjustmentComponents(str(stock.get("category")), input.deltaWeight(), input.deltaQuantity(), input.deltaGrossWeight(), input.deltaImpurity());
        if ("RAW".equals(stock.get("category")) && input.deltaQuantity() != 0) throw fail("原料不能调整件数");
        Long factory = number(stock.get("factory_id"));
        Line line = new Line(id, null, str(stock.get("category")), number(stock.get("reference_id")), str(stock.get("batch_no")), input.deltaWeight(), input.deltaQuantity(), input.deltaGrossWeight(), input.deltaImpurity(), null, null, null, input.reason());
        Order order = new Order(input.requestId(), factory, number(stock.get("warehouse_id")), "ADJUST", LocalDate.now(ZoneId.of("Asia/Shanghai")), input.operatorId(), null, input.keeperId(), null, null, null, null, null, null, false, null, input.reason(), List.of(line), null, null);
        String previous = duplicate("metal_warehouse_order", "order_id", order.requestId(), fingerprint(order));
        if (previous != null) return previous;
        version(stock, input.version());
        String saved = postInternal(order, true);
        remember("inventory-adjust", input.requestId(), hash, saved);
        return saved;
    }

    /**
     * 校验加工批次状态和数量后结算，生成报工及损耗统计。
     */
    public void settle(Long id, Action input) {
        var initial = record("process-batch", id, false);
        scope.lockFactories(List.of(number(initial.get("factory_id"))));
        lockBatchSources(initial);
        var batch = lock("process-batch", id);
        version(batch, input.version());
        settleInternal(batch, input.completedQuantity());
    }

    private void settleInternal(Map<String, Object> batch, Integer completed) {
        if (!"OPEN".equals(batch.get("status"))) throw fail("批次已结算或已冲销");
        if (completed == null || completed < 1 || completed > number(batch.get("planned_quantity")))
            throw fail("完成数量须为1至批次加工数量");
        Long batchId = number(batch.get("batch_id"));
        var totals = one("SELECT COALESCE(SUM(CASE WHEN o.type='PRODUCTION_OUT' THEN l.net_weight ELSE 0 END),0) issued,COALESCE(SUM(CASE WHEN o.type IN ('RAW_RETURN','SEMI_IN','FINISHED_IN') THEN l.net_weight ELSE 0 END),0) returned,COALESCE(SUM(CASE WHEN o.type IN ('SEMI_IN','FINISHED_IN') THEN l.quantity ELSE 0 END),0) output_qty FROM metal_warehouse_order o JOIN metal_warehouse_order_line l ON l.order_id=o.order_id WHERE o.batch_id=? AND o.status='POSTED'", batchId);
        validateDownstreamOutput(batch, completed.longValue());
        if (number(totals.get("output_qty")) != completed.longValue())
            throw fail("半成品或成品产出数量合计必须等于实际完成数量");
        var s = step(number(batch.get("item_id")), number(batch.get("process_id")));
        Map<String, Object> rule = null;
        BigDecimal tolerance = BigDecimal.ZERO;
        var calculation = LossCalculator.calculate(decimal(totals.get("issued")), decimal(totals.get("returned")), completed, decimal(s.get("theoretical_loss")), tolerance);
        long report = insert("INSERT INTO metal_work_report(batch_id,factory_id,item_id,process_id,completed_quantity,issued_weight,returned_weight,loss_weight,average_loss,theoretical_loss,tolerance,threshold_value,rule_id,rule_version,status) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", batchId, batch.get("factory_id"), batch.get("item_id"), batch.get("process_id"), completed, totals.get("issued"), totals.get("returned"), calculation.loss(), calculation.average(), s.get("theoretical_loss"), tolerance, calculation.threshold(), rule == null ? null : rule.get("rule_id"), rule == null ? null : rule.get("version"), calculation.abnormal() ? "ABNORMAL" : "NORMAL");
        var materials = rows("SELECT l.code,l.name,SUM(l.net_weight) weight FROM metal_warehouse_order o JOIN metal_warehouse_order_line l ON l.order_id=o.order_id WHERE o.batch_id=? AND o.status='POSTED' AND o.type='PRODUCTION_OUT' GROUP BY l.category,l.reference_id,l.code,l.name ORDER BY l.code,l.category,l.reference_id", batchId);
        var allocated = LossCalculator.allocate(calculation.loss(), materials.stream().map(r -> decimal(r.get("weight"))).toList());
        for (int i = 0; i < materials.size(); i++) {
            var m = materials.get(i);
            insert("INSERT INTO metal_report_material(report_id,code,name,issued_weight,loss_weight,average_loss) VALUES(?,?,?,?,?,?)", report, m.get("code"), m.get("name"), m.get("weight"), allocated.get(i), allocated.get(i).divide(BigDecimal.valueOf(completed), 6, RoundingMode.HALF_UP));
        }
        if (calculation.abnormal()) {
            var product = JSON.parseObject(str(s.get("product_snapshot")));
            long warning = insert("INSERT INTO metal_warning_record(report_id,factory_id,process_name,product_category,specification,order_no,threshold_value,theoretical_loss,average_loss) VALUES(?,?,?,?,?,?,?,?,?)", report, batch.get("factory_id"), s.get("process_name"), product.get("category"), product.get("specification"), one("SELECT order_no FROM metal_work_order WHERE work_order_id=?", batch.get("work_order_id")).get("order_no"), calculation.threshold(), s.get("theoretical_loss"), calculation.average());
            history(warning, "GENERATED", rule == null ? "按工单工序理论损耗快照判定" : "按配置规则生成");
        }
        jdbc.update("UPDATE metal_process_batch SET status='SETTLED',completed_quantity=?,version=version+1 WHERE batch_id=?", completed, batchId);
        refreshOrder(number(batch.get("work_order_id")));
    }

    private void refreshOrder(Long id) {
        long incomplete = jdbc.queryForObject("SELECT COUNT(*) FROM metal_work_order_step s JOIN metal_work_order_item i ON i.item_id=s.item_id WHERE i.work_order_id=? AND i.quantity<>(SELECT COALESCE(SUM(b.completed_quantity),0) FROM metal_process_batch b WHERE b.item_id=i.item_id AND b.process_id=s.process_id AND b.status='SETTLED')", Long.class, id);
        long open = jdbc.queryForObject("SELECT COUNT(*) FROM metal_process_batch WHERE work_order_id=? AND status='OPEN'", Long.class, id);
        long issued = jdbc.queryForObject("SELECT COUNT(*) FROM metal_warehouse_order WHERE work_order_id=? AND type='PRODUCTION_OUT' AND status='POSTED'", Long.class, id);
        boolean newFlow = jdbc.queryForObject("SELECT COUNT(*) FROM metal_work_order_review WHERE work_order_id=? AND action='CREATE'", Long.class, id) > 0;
        String status = incomplete == 0 && open == 0 ? "COMPLETED" : issued > 0 ? "IN_PROGRESS" : newFlow ? "WAITING_MATERIAL" : "APPROVED";
        jdbc.update("UPDATE metal_work_order SET status=?,version=version+1,update_time=NOW() WHERE work_order_id=?", status, id);
    }

    /**
     * 校验预警状态和版本后登记处理结果。
     */
    public void handleWarning(Long id, Action input) {
        var warning = lock("warning-record", id);
        version(warning, input.version());
        if (!"PENDING".equals(warning.get("status")) || truth(warning.get("source_void")))
            throw fail("预警已处理或来源已作废");
        if (input.reason() == null || input.reason().isBlank()) throw fail("请填写处理结果");
        jdbc.update("UPDATE metal_warning_record SET status='HANDLED',result=?,handler_id=?,handler_name=?,handled_time=NOW(),version=version+1 WHERE warning_id=?", input.reason().strip(), SecurityUtils.getUserId(), actor(), id);
        history(id, "HANDLED", input.reason().strip());
    }

    private void history(Long id, String action, String result) {
        insert("INSERT INTO metal_warning_history(warning_id,action,result,actor_id,actor_name) VALUES(?,?,?,?,?)", id, action, result, SecurityUtils.getUserId(), actor());
    }

    /**
     * 查询指定工单的生产损耗汇总。
     */
    public List<Map<String, Object>> summaries(Long workOrderId) {
        var w = one("SELECT factory_id FROM metal_work_order WHERE work_order_id=? AND del_flag='0'", workOrderId);
        scope.check(number(w.get("factory_id")));
        return cleaned(rows("SELECT CAST(r.item_id AS CHAR) item_id,CAST(r.process_id AS CHAR) process_id,s.process_name,SUM(r.loss_weight) loss_weight,CAST(ROUND(SUM(r.loss_weight)/SUM(r.completed_quantity),6) AS DECIMAL(20,6)) average_loss,SUM(r.completed_quantity) completed_quantity,CASE WHEN SUM(r.status='ABNORMAL')>0 THEN 'ABNORMAL' ELSE 'NORMAL' END status FROM metal_work_report r JOIN metal_process_batch b ON b.batch_id=r.batch_id JOIN metal_work_order_step s ON s.item_id=r.item_id AND s.process_id=r.process_id WHERE b.work_order_id=? AND r.voided=FALSE GROUP BY r.item_id,r.process_id,s.process_name,s.sequence_no ORDER BY r.item_id,s.sequence_no", workOrderId));
    }

    /**
     * 查询指定工单产品工序的加工报工记录。
     */
    public List<Map<String, Object>> reportsForStep(Long itemId, Long processId) {
        var s = step(itemId, processId);
        var w = one("SELECT factory_id FROM metal_work_order WHERE work_order_id=?", s.get("work_order_id"));
        scope.check(number(w.get("factory_id")));
        return cleaned(rows("SELECT r.*,r.report_id id,b.batch_no" + reportWeightColumns("r") + " FROM metal_work_report r JOIN metal_process_batch b ON b.batch_id=r.batch_id WHERE r.item_id=? AND r.process_id=? ORDER BY r.report_id", itemId, processId));
    }

    /**
     * 校验版本和冲销原因后冲销非生产关联单据。
     */
    public void reverseOrder(Long id, Action input) {
        var doc = lock("warehouse-order", id);
        version(doc, input.version());
        if (doc.get("batch_id") != null) throw fail("生产单据请整体冲销关联加工批次");
        if (input.reason() == null || input.reason().isBlank()) throw fail("请填写冲销原因");
        reverseDocuments(List.of(doc), input.reason());
    }

    /**
     * 校验批次及后续业务引用后整体冲销加工批次。
     */
    public void reverseBatch(Long id, Action input) {
        var batch = lock("process-batch", id);
        version(batch, input.version());
        if ("VOID".equals(batch.get("status"))) throw fail("批次已冲销");
        if (input.reason() == null || input.reason().isBlank()) throw fail("请填写冲销原因");
        int seq = number(batch.get("sequence_no")).intValue();
        long after = sumQuantity(number(batch.get("item_id")), seq + 1, "SETTLED") + sumQuantity(number(batch.get("item_id")), seq + 1, "OPEN");
        long remaining = sumQuantity(number(batch.get("item_id")), seq, "SETTLED") - ("SETTLED".equals(batch.get("status")) ? number(batch.get("completed_quantity")) : 0);
        if (after > remaining) throw fail("批次存在后续工序数量引用，不能冲销");
        var docs = rows("SELECT *,order_id id FROM metal_warehouse_order WHERE batch_id=? AND status='POSTED' ORDER BY order_id DESC FOR UPDATE", id);
        reverseDocuments(docs, input.reason());
        jdbc.update("UPDATE metal_process_batch SET status='VOID',version=version+1 WHERE batch_id=?", id);
        jdbc.update("UPDATE metal_work_report SET voided=TRUE WHERE batch_id=?", id);
        for (var warning : rows("SELECT w.warning_id FROM metal_warning_record w JOIN metal_work_report r ON r.report_id=w.report_id WHERE r.batch_id=? FOR UPDATE", id)) {
            jdbc.update("UPDATE metal_warning_record SET source_void=TRUE,version=version+1 WHERE warning_id=?", warning.get("warning_id"));
            history(number(warning.get("warning_id")), "SOURCE_VOID", input.reason());
        }
        refreshOrder(number(batch.get("work_order_id")));
    }

    private void reverseDocuments(List<Map<String, Object>> docs, String reason) {
        Set<Long> ids = new HashSet<>();
        docs.forEach(d -> ids.add(number(d.get("order_id"))));
        if (docs.stream().anyMatch(d -> !"POSTED".equals(d.get("status")))) throw fail("单据已冲销");
        Set<Long> originStocks = new HashSet<>();
        for (var doc : docs) {
            Long id = number(doc.get("order_id"));
            for (var stock : rows("SELECT DISTINCT l.inventory_id FROM metal_warehouse_order_line l JOIN metal_inventory i ON i.inventory_id=l.inventory_id WHERE l.order_id=? AND (i.origin_batch_id IS NOT NULL AND ? IN ('SEMI_IN','FINISHED_IN') OR ?='OPENING')", id, doc.get("type"), doc.get("type")))
                originStocks.add(number(stock.get("inventory_id")));
            for (var ref : rows("SELECT DISTINCT o.order_id FROM metal_warehouse_order_line x JOIN metal_warehouse_order o ON o.order_id=x.order_id JOIN metal_warehouse_order_line source ON source.line_id=x.source_line_id WHERE source.order_id=? AND o.status='POSTED'", id))
                if (!ids.contains(number(ref.get("order_id")))) throw fail("单据存在退还引用，不能冲销");
        }
        for (var doc : docs)
            for (var l : rows("SELECT * FROM metal_stock_ledger WHERE order_id=? AND reversal_of IS NULL AND (delta_weight>0 OR delta_quantity>0)", doc.get("order_id")))
                for (var dependency : rows("SELECT DISTINCT x.order_id FROM metal_stock_ledger x JOIN metal_warehouse_order o ON o.order_id=x.order_id WHERE x.inventory_id=? AND x.ledger_id>? AND o.status='POSTED' AND x.reversal_of IS NULL", l.get("inventory_id"), l.get("ledger_id")))
                    if (!ids.contains(number(dependency.get("order_id"))))
                        throw fail("入库记录存在后续业务引用，不能冲销");
        for (Long stock : originStocks)
            for (var ref : rows("SELECT DISTINCT l.order_id FROM metal_stock_ledger l JOIN metal_warehouse_order o ON o.order_id=l.order_id WHERE l.inventory_id=? AND o.status='POSTED'", stock))
                if (!ids.contains(number(ref.get("order_id")))) throw fail("产出库存已被后续业务引用，不能冲销");
        var ledgers = new ArrayList<Map<String, Object>>();
        for (Long id : ids)
            ledgers.addAll(rows("SELECT * FROM metal_stock_ledger WHERE order_id=? AND reversal_of IS NULL ORDER BY ledger_id DESC", id));
        ledgers.sort(Comparator.comparing((Map<String, Object> r) -> number(r.get("ledger_id"))).reversed());
        for (var ledger : ledgers) {
            Long stockId = number(ledger.get("inventory_id"));
            var stock = record("inventory", stockId, true);
            balance(stock, decimal(ledger.get("delta_weight")).negate(), -number(ledger.get("delta_quantity")), number(ledger.get("order_id")), number(ledger.get("line_id")), number(ledger.get("ledger_id")));
        }
        for (Long id : ids)
            jdbc.update("UPDATE metal_warehouse_order SET status='VOID',reason=?,version=version+1 WHERE order_id=?", reason, id);
    }
    /**
     * 获取当前业务可选择的关联资料。
     */
    /**
     * Read only the entry snapshot; never expose the full production work-order detail.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public WarehouseWorkOrderContext workOrderContext(Long factoryId, Long workOrderId) {
        permitOption("work-order-context");
        if (factoryId == null || factoryId <= 0 || workOrderId == null || workOrderId <= 0)
            throw fail("请选择有效的工厂和生产工单");
        scope.check(factoryId);
        var headers = rows("SELECT customer,status FROM metal_work_order WHERE work_order_id=? AND factory_id=? AND del_flag='0'", workOrderId, factoryId);
        if (headers.isEmpty()) throw fail("工单不存在、已删除或不属于当前工厂，请刷新选择");
        var header = headers.get(0);
        if (!Set.of("WAITING_MATERIAL", "IN_PROGRESS").contains(header.get("status")))
            throw fail("工单状态已变化，请刷新选择待领料或生产中的工单");
        var itemRows = rows("SELECT item_id,product_id,CAST(product_snapshot AS CHAR) product_snapshot FROM metal_work_order_item WHERE work_order_id=? ORDER BY sequence_no,item_id", workOrderId);
        var planRows = rows("SELECT s.item_id,s.process_id,s.process_name,s.sequence_no FROM metal_work_order_step s JOIN metal_work_order_item i ON i.item_id=s.item_id WHERE i.work_order_id=? ORDER BY i.sequence_no,s.sequence_no,s.process_id", workOrderId);
        Map<Long, List<WarehouseWorkOrderContext.Plan>> plans = new HashMap<>();
        for (var row : planRows)
            plans.computeIfAbsent(number(row.get("item_id")), key -> new ArrayList<>()).add(new WarehouseWorkOrderContext.Plan(str(row.get("process_id")), str(row.get("process_name")), number(row.get("sequence_no")).intValue()));
        List<WarehouseWorkOrderContext.Item> items = new ArrayList<>();
        for (var row : itemRows) {
            var snapshot = JSON.parseObject(str(row.get("product_snapshot")));
            items.add(new WarehouseWorkOrderContext.Item(str(row.get("item_id")), str(row.get("product_id")), new WarehouseWorkOrderContext.Snapshot(snapshot.getString("code"), snapshot.getString("name"), snapshot.getString("specification")), List.copyOf(plans.getOrDefault(number(row.get("item_id")), List.of()))));
        }
        return new WarehouseWorkOrderContext(workOrderId.toString(), factoryId.toString(), str(header.get("customer")), List.copyOf(items));
    }

    // Candidate data has its own permissions: a warehouse dictionary reader must not read stock or source documents.
    private void permitOption(String kind) {
        String order = "metal:warehouse-order:add,metal:warehouse-order:import";
        String inventory = "metal:inventory:list,metal:inventory:query";
        String batch = "metal:process-batch:list,metal:process-batch:query,metal:process-batch:add";
        String rule = "metal:warning-rule:add,metal:warning-rule:edit";
        String allowed = switch (kind) {
            case "factories" ->
                    "metal:warehouse:list,metal:warehouse:query,metal:warehouse:add,metal:warehouse:edit," + inventory + ",metal:inventory:add,metal:inventory:adjust,metal:warehouse-order:list,metal:warehouse-order:query," + order + "," + batch + ",metal:warning-rule:list," + rule + ",metal:warning-record:list,metal:warning-record:query";
            case "warehouses" -> inventory + ",metal:inventory:add," + order;
            case "people" -> "metal:inventory:add,metal:inventory:adjust," + order;
            case "materials" -> "metal:inventory:add," + order;
            case "products" -> "metal:inventory:add," + order + "," + rule;
            case "processes" -> rule;
            case "work-order-context" -> order;
            case "work-orders" -> "metal:process-batch:add," + order;
            case "batches" -> batch + "," + order;
            case "stocks" -> inventory + ",metal:inventory:adjust," + order;
            case "out-lines" -> "metal:warehouse-order:query," + order;
            default -> throw fail("选项无效");
        };
        if (!scope.isAdmin() && Arrays.stream(allowed.split(",")).noneMatch(SecurityUtils::hasPermi))
            throw new ServiceException("没有此候选数据的访问权限", 403);
    }

    private List<Map<String, Object>> outLines(Long factoryId) {
        var data = rows("SELECT l.line_id id,o.order_id,o.order_no,CONCAT(o.order_no,' / ',l.name) name,l.code,l.specification,l.organization,l.category,l.reference_id,l.net_weight,l.quantity,o.batch_id,o.type,l.inventory_id,l.batch_no,i.warehouse_id,l.net_weight-COALESCE(r.returned,0) remaining_weight,b.batch_status,b.issued_weight,b.output_weight,b.returned_weight FROM metal_warehouse_order_line l JOIN metal_warehouse_order o ON o.order_id=l.order_id LEFT JOIN metal_inventory i ON i.inventory_id=l.inventory_id LEFT JOIN (" + RawReturnAllowance.totalsSql(true) + ") b ON b.batch_id=o.batch_id LEFT JOIN (SELECT rl.source_line_id,SUM(rl.net_weight) returned FROM metal_warehouse_order_line rl JOIN metal_warehouse_order ro ON ro.order_id=rl.order_id WHERE ro.factory_id=? AND ro.status='POSTED' AND rl.source_line_id IS NOT NULL GROUP BY rl.source_line_id) r ON r.source_line_id=l.line_id WHERE o.factory_id=? AND o.kind='OUT' AND o.status='POSTED' ORDER BY l.line_id", factoryId, factoryId, factoryId);
        for (var row : data) {
            if ("PRODUCTION_OUT".equals(row.get("type")) && "RAW".equals(row.get("category"))) {
                var available = RawReturnAllowance.remaining(row.get("issued_weight"), row.get("output_weight"), row.get("returned_weight"));
                row.put("batch_remaining_weight", available);
                row.put("returnable_weight", RawReturnAllowance.limit(decimal(row.get("remaining_weight")), available, row.get("batch_status")));
            }
            if (!"PRODUCTION_OUT".equals(row.get("type")) || !"RAW".equals(row.get("category"))) row.remove("batch_status");
            row.remove("issued_weight");
            row.remove("output_weight");
            row.remove("returned_weight");
        }
        return cleaned(data);
    }

    public List<Map<String, Object>> options(String kind, Long factoryId, Long referenceId) {
        permitOption(kind);
        if (kind.equals("factories")) {
            Long own = scope.currentFactory();
            return cleaned(own == null ? rows("SELECT factory_id id,factory_name name FROM metal_factory WHERE del_flag='0' ORDER BY factory_id") : rows("SELECT factory_id id,factory_name name FROM metal_factory WHERE factory_id=? AND del_flag='0'", own));
        }
        if (factoryId == null) throw fail("请选择工厂");
        scope.check(factoryId);
        return switch (kind) {
            case "warehouses" ->
                    cleaned(rows("SELECT warehouse_id id,name FROM metal_warehouse WHERE factory_id=? AND del_flag='0' AND status='0' ORDER BY sort_order,warehouse_id", factoryId));
            case "people" ->
                    cleaned(rows("SELECT u.user_id id,COALESCE(NULLIF(u.nick_name,''),u.user_name) name,u.phonenumber phone," + PersonOptions.roles("u") + " role_names FROM sys_user u WHERE u.factory_id=? AND u.status='0' AND u.del_flag='0' ORDER BY u.user_id", factoryId));
            case "materials" ->
                    cleaned(rows("SELECT material_id id,material_code code,material_type name,specification FROM metal_material WHERE del_flag='0' ORDER BY material_id"));
            case "products" ->
                    cleaned(rows("SELECT product_id id,product_code code,product_name name,product_category category,specification FROM metal_product WHERE del_flag='0' ORDER BY product_id"));
            case "processes" ->
                    cleaned(rows("SELECT p.process_id id,p.process_name name FROM metal_product_process x JOIN metal_process p ON p.process_id=x.process_id WHERE x.product_id=? AND p.del_flag='0' ORDER BY x.sequence_no", referenceId));
            case "work-orders" ->
                    cleaned(rows("SELECT work_order_id id,order_no name,status FROM metal_work_order WHERE factory_id=? AND del_flag='0' AND status IN ('WAITING_MATERIAL','IN_PROGRESS') ORDER BY work_order_id", factoryId));
            case "stocks" ->
                    cleaned(rows("SELECT i.inventory_id id,i.name,i.code,i.specification,i.batch_no,i.category,i.warehouse_id,i.reference_id,i.net_weight,i.quantity,i.organization,i.ownership_status,i.origin_batch_id,b.status origin_batch_status,s.process_name origin_process_name,b.sequence_no origin_process_sequence,w.order_no origin_work_order_no FROM metal_inventory i LEFT JOIN metal_process_batch b ON b.batch_id=i.origin_batch_id LEFT JOIN metal_work_order w ON w.work_order_id=b.work_order_id LEFT JOIN metal_work_order_step s ON s.item_id=b.item_id AND s.process_id=b.process_id WHERE i.factory_id=? AND i.del_flag='0' ORDER BY i.inventory_id", factoryId));
            case "batches" ->
                    cleaned(rows("SELECT b.batch_id id,CONCAT(b.batch_no,' / ',s.process_name) name,b.batch_no,b.planned_quantity,b.version,b.work_order_id,b.item_id,b.process_id FROM metal_process_batch b JOIN metal_work_order w ON w.work_order_id=b.work_order_id JOIN metal_work_order_step s ON s.item_id=b.item_id AND s.process_id=b.process_id WHERE b.factory_id=? AND b.status='OPEN' ORDER BY b.batch_id", factoryId));
            case "out-lines" -> outLines(factoryId);
            default -> throw fail("选项无效");
        };
    }
}
