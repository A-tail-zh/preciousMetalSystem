package com.ruoyi.metal.service;

import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;

import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.*;
import org.springframework.transaction.annotation.*;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.metal.security.FactoryScope;

/**
 * Semi stock release/closure ledger. All callers serialize writes through the existing factory lock.
 */
@Service
@Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
public class SemiFlowService {
    private final JdbcTemplate jdbc;
    private final FactoryScope scope;
    private final SemiSourcePolicyService policy;

    public SemiFlowService(JdbcTemplate jdbc, FactoryScope scope) {
        this.jdbc = jdbc;
        this.scope = scope;
        this.policy = new SemiSourcePolicyService(jdbc, scope);
    }

    static long num(Object v) {
        return v == null ? 0 : new BigDecimal(v.toString()).longValueExact();
    }

    static BigDecimal dec(Object v) {
        return v == null ? BigDecimal.ZERO : new BigDecimal(v.toString());
    }

    static String text(Object v) {
        return v == null ? "" : v.toString();
    }

    private static ServiceException fail(String s) {
        return new ServiceException(s);
    }

    private Map<String, Object> one(String sql, Object... args) {
        var rows = jdbc.queryForList(sql, args);
        if (rows.size() != 1) throw fail("半成品关联记录不存在或已归档");
        return rows.get(0);
    }

    private long insert(String sql, Object... args) {
        KeyHolder h = new GeneratedKeyHolder();
        jdbc.update(c -> {
            PreparedStatement p = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) p.setObject(i + 1, args[i]);
            return p;
        }, h);
        return h.getKey().longValue();
    }

    private long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    private static String hash(Object o) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JSON.toJSONString(o).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void reason(String r) {
        if (r == null || r.isBlank() || r.length() > 500) throw fail("请填写1至500字操作原因");
    }

    private void permit(String p) {
        if (!scope.isAdmin() && !SecurityUtils.getLoginUser().getPermissions().contains(p))
            throw new ServiceException("没有此操作权限", 403);
    }

    private static void version(Map<String, Object> row, Integer v) {
        if (v == null || num(row.get("version")) != v) throw fail("版本冲突，请刷新后重试");
    }

    private Long replay(String table, String id, String request, String fingerprint) {
        if (request == null || !request.matches("[0-9a-fA-F-]{36}")) throw fail("请求UUID不合法");
        var rows = jdbc.queryForList("SELECT " + id + ",request_hash,actor_id FROM " + table + " WHERE request_id=?", request);
        if (rows.isEmpty()) return null;
        var row = rows.get(0);
        if (!fingerprint.equals(row.get("request_hash")) || num(row.get("actor_id")) != SecurityUtils.getUserId())
            throw fail("重复请求的内容或操作人不一致");
        return num(row.get(id));
    }

    private Map<String, Object> work(Long id) {
        var w = one("SELECT * FROM metal_work_order WHERE work_order_id=? AND del_flag='0'", id);
        scope.check(num(w.get("factory_id")));
        return w;
    }

    private void lockFactory(long factory) {
        scope.lockFactories(List.of(factory));
    }

    private List<Long> route(long item, long sequence) {
        return jdbc.queryForList("SELECT process_id FROM metal_work_order_step WHERE item_id=? AND sequence_no<=? ORDER BY sequence_no", item, sequence).stream().map(r -> num(r.get("process_id"))).toList();
    }

    public Map<String, Object> sourceBatch(Map<String, Object> stock) {
        if (stock.get("origin_batch_id") == null) throw fail("半成品缺少来源批次，请人工核对");
        return one("SELECT b.*,i.product_id,i.product_snapshot,w.customer,w.customer_id,w.status work_status FROM metal_process_batch b JOIN metal_work_order_item i ON i.item_id=b.item_id JOIN metal_work_order w ON w.work_order_id=b.work_order_id WHERE b.batch_id=? AND w.del_flag='0'", stock.get("origin_batch_id"));
    }

    public boolean isOwn(Map<String, Object> source, Map<String, Object> target) {
        return num(source.get("work_order_id")) == num(target.get("work_order_id")) && num(source.get("item_id")) == num(target.get("item_id"));
    }

    public Map<String, Object> validateSource(Map<String, Object> stock, Map<String, Object> target, Long releaseId) {
        return validateSource(stock, target, releaseId, null, null);
    }

    public Map<String, Object> validateSource(Map<String, Object> stock, Map<String, Object> target, Long releaseId, Long skipPermitId, Integer skipPermitVersion) {
        if (!"SEMI".equals(stock.get("category"))) throw fail("请选择半成品库存");
        if (!"NORMAL".equals(stock.get("ownership_status"))) throw fail("半成品来源待核对，不能领用或释放");
        WarehouseService.validateOwnership(stock);
        scope.requireCatalog("product",num(stock.get("reference_id")),num(stock.get("factory_id")));
        scope.requireCatalog("process",num(target.get("process_id")),num(stock.get("factory_id")));
        var source = sourceBatch(stock);
        var item = one("SELECT i.*,w.customer,w.customer_id,w.factory_id FROM metal_work_order_item i JOIN metal_work_order w ON w.work_order_id=i.work_order_id WHERE i.item_id=?", target.get("item_id"));
        if (num(stock.get("factory_id")) != num(item.get("factory_id")) || num(source.get("factory_id")) != num(item.get("factory_id")) || num(item.get("work_order_id")) != num(target.get("work_order_id")))
            throw fail("半成品不属于当前工厂或工单上下文");
        ProcessingInputService.require(jdbc,stock,target);
        boolean qualification=SemiSourcePolicyService.qualificationEntry(target);
        if (!HandoverSource.canEnter(jdbc,stock,source,target) || !qualification && num(target.get("sequence_no")) <= 1)
            throw fail("半成品来源须已结算并与当前工序衔接");
        var sp = JSON.parseObject(text(source.get("product_snapshot")));
        var tp = JSON.parseObject(text(item.get("product_snapshot")));
        if (sp==null || tp==null || text(sp.get("specification")).isBlank() || text(tp.get("specification")).isBlank()
                || num(stock.get("reference_id"))!=num(source.get("product_id")) || num(source.get("product_id"))!=num(item.get("product_id"))
                || !sp.getString("specification").trim().equals(tp.getString("specification").trim()))
            throw fail("半成品产品或规格与目标工单不匹配");
        if (qualification) {
            if(skipPermitId!=null || skipPermitVersion!=null || num(source.get("work_order_id"))==num(target.get("work_order_id"))
                    || !SemiSourcePolicyService.completeSavedRoute(jdbc,source)) throw fail("首工序资格接入须来自其他工单的完整半成品路线，不能冒用跳步许可");
        } else if (route(num(source.get("item_id")), num(source.get("sequence_no"))).size() != num(source.get("sequence_no")) || route(num(target.get("item_id")), num(target.get("sequence_no")) - 1).size() != num(target.get("sequence_no")) - 1 || !SemiFlowRules.compatible(num(source.get("product_id")), sp.getString("specification"), route(num(source.get("item_id")), num(source.get("sequence_no"))), num(item.get("product_id")), tp.getString("specification"), route(num(target.get("item_id")), isOwn(source, target) ? num(source.get("sequence_no")) : num(target.get("sequence_no")) - 1)))
            throw fail("半成品产品、规格或已完成工序路线与目标工序不匹配");
        if (SemiMeasurementRules.weight(stock) != SemiMeasurementRules.weight(source))
            throw fail("库存计量方式与来源批次快照不一致");
        if (!qualification && !isOwn(source, target)) {
            var targetModes = jdbc.queryForList("SELECT measurement_mode FROM metal_work_order_step WHERE item_id=? AND sequence_no<=? ORDER BY sequence_no", target.get("item_id"), source.get("sequence_no"));
            var sourceModes = jdbc.queryForList("SELECT measurement_mode FROM metal_work_order_step WHERE item_id=? AND sequence_no<=? ORDER BY sequence_no", source.get("item_id"), source.get("sequence_no"));
            if (!targetModes.equals(sourceModes)) throw fail("来源与目标工艺计量方式快照不一致");
        }
        if (!text(stock.get("specification")).trim().equals(sp.getString("specification").trim()))
            throw fail("库存规格与来源工单快照不一致，请人工核对");
        if (!qualification && num(source.get("sequence_no")) >= num(target.get("sequence_no"))) throw fail("来源工序必须早于当前工序");
        if (!qualification && (num(source.get("sequence_no")) + 1 != num(target.get("sequence_no")) || skipPermitId != null || source.get("execution_snapshot") != null))
            policy.decide(source, target, skipPermitId, skipPermitVersion);
        if (!isOwn(source, target) && SemiSourcePolicyService.hasSkipped(source))
            throw fail("来源含跳步轨迹，不符合跨工单完整加工路线");
        if (isOwn(source, target)) {
            if (releaseId != null) throw fail("本工单半成品不能冒用跨工单释放记录");
        } else {
            if (num(source.get("work_order_id")) == num(target.get("work_order_id")))
                throw fail("同一工单不同产品明细不能相互替代");
            if (!Set.of("COMPLETED", "CLOSED_PARTIAL").contains(text(source.get("work_status"))) || !MaterialOwnership.sameCustomer(stock,source,item))
                throw fail("跨工单半成品须来自已关闭工单且属于同厂同客户");
            if (releaseId == null) throw fail("跨工单半成品须选择有效释放记录");
            var release = one("SELECT * FROM metal_semi_release WHERE release_id=?", releaseId);
            if (!"ACTIVE".equals(release.get("status")) || num(release.get("inventory_id")) != num(stock.get("inventory_id")) || num(release.get("work_order_id")) != num(source.get("work_order_id")))
                throw fail("半成品释放记录已失效或来源不一致");
        }
        return source;
    }

    private static final String USES = "SELECT (SELECT sequence_no FROM metal_process_batch WHERE batch_id=o.batch_id) target_sequence,u.release_id,l.line_id,l.quantity-COALESCE(r.quantity,0) used_quantity,l.net_weight-COALESCE(r.weight,0) used_weight FROM metal_semi_release_use u JOIN metal_warehouse_order_line l ON l.line_id=u.line_id JOIN metal_warehouse_order o ON o.order_id=l.order_id LEFT JOIN (SELECT x.source_line_id,SUM(x.quantity) quantity,SUM(x.net_weight) weight FROM metal_warehouse_order_line x JOIN metal_warehouse_order d ON d.order_id=x.order_id WHERE d.status='POSTED' AND d.type='SEMI_RETURN' GROUP BY x.source_line_id) r ON r.source_line_id=l.line_id WHERE o.status='POSTED'";

    public Map<String, Object> availableRelease(long releaseId) {return releaseBalance(releaseId,true);}
    private Map<String, Object> releaseBalance(long releaseId,boolean reservations) {
        var release = one("SELECT * FROM metal_semi_release WHERE release_id=?", releaseId);
        long used = 0;
        BigDecimal weight = BigDecimal.ZERO;
        for (var r : jdbc.queryForList(USES + " AND u.release_id=?", releaseId)) {
            used += num(r.get("used_quantity"));
            weight = weight.add(dec(r.get("used_weight")));
        }
        var pending=reservations?WarehouseReservations.release(jdbc,releaseId):Map.<String,Object>of("weight",BigDecimal.ZERO,"quantity",0L);
        release.put("available_quantity", num(release.get("quantity")) - used - num(pending.get("quantity")));
        release.put("available_weight", dec(release.get("net_weight")).subtract(weight).subtract(dec(pending.get("weight"))));
        return release;
    }

    public void reserveUse(Long releaseId, long lineId, Map<String, Object> stock, Map<String, Object> target, long qty, BigDecimal weight) {
        if (releaseId == null) return;
        if (!SemiMeasurementRules.weight(stock) && qty > entryCapacity(num(target.get("item_id")), num(target.get("sequence_no")),lineId))
            throw fail("外部接入件数超过前置步骤剩余覆盖数量");
        var release = availableRelease(releaseId);
        if (SemiMeasurementRules.weight(stock))
            SemiMeasurementRules.requireWeight(dec(release.get("available_weight")), weight);
        else
            SemiFlowRules.release(num(release.get("available_quantity")), dec(release.get("available_weight")), qty, weight);
        insert("INSERT INTO metal_semi_release_use(release_id,line_id,route_snapshot) VALUES(?,?,?)", releaseId, lineId, JSON.toJSONString(Map.of("sourceBatchId", stock.get("origin_batch_id"), "targetItemId", target.get("item_id"), "targetSequence", target.get("sequence_no"), "requiredRoute", route(num(target.get("item_id")), num(target.get("sequence_no")) - 1))));
    }

    public static long netIssued(JdbcTemplate jdbc, long batch) {
        Long n = jdbc.queryForObject("SELECT COALESCE(SUM(CASE WHEN o.type='PRODUCTION_OUT' AND l.category='SEMI' THEN l.quantity WHEN o.type='SEMI_RETURN' THEN -l.quantity ELSE 0 END),0) FROM metal_warehouse_order o JOIN metal_warehouse_order_line l ON l.order_id=o.order_id WHERE o.batch_id=? AND o.status='POSTED'", Long.class, batch);
        return n == null ? 0 : n;
    }

    public static long netIssued(JdbcTemplate jdbc, Map<String, ?> batch) {
        if (!MeasurementPolicy.configurable(jdbc,batch)) return netIssued(jdbc,num(batch.get("batch_id")));
        long quantity=0;
        for(var line:measurementInputs(jdbc,batch))
            if(!InventoryMeasurement.weight(line)) quantity+=("PRODUCTION_OUT".equals(line.get("type"))?1:-1)*num(line.get("quantity"));
        return quantity;
    }

    static List<Map<String,Object>> measurementInputs(JdbcTemplate jdbc, Map<String, ?> batch) {
        var lines=jdbc.queryForList("SELECT l.*,o.type FROM metal_warehouse_order o JOIN metal_warehouse_order_line l ON l.order_id=o.order_id WHERE o.batch_id=? AND o.status='POSTED' AND o.type IN ('PRODUCTION_OUT','RAW_RETURN','SEMI_RETURN') AND l.category IN ('RAW','SEMI')",batch.get("batch_id"));
        return InventoryMeasurement.enrich(jdbc,lines);
    }

    public long credit(long item, long sequence) {
        long n = policy.skipCredit(item, sequence);
        var uses = jdbc.queryForList(USES + " AND o.batch_id IN (SELECT batch_id FROM metal_process_batch WHERE item_id=? AND sequence_no>? AND status<>'VOID')", item, sequence);
        if (uses.isEmpty()) return n;
        var modes = jdbc.queryForList("SELECT measurement_mode FROM metal_work_order_step WHERE item_id=? ORDER BY sequence_no", item).stream().map(x -> text(x.get("measurement_mode"))).toList();
        for (var r : uses)
            if (SemiMeasurementRules.pieceCreditApplies(modes, sequence, num(r.get("target_sequence"))))
                n += num(r.get("used_quantity"));
        return n;
    }

    public long entryCapacity(long item, long sequence) {
        return entryCapacity(item,sequence,-1);
    }

    private long entryCapacity(long item,long sequence,long excludeLine) {
        if(sequence<1)return 0;
        if(sequence==1){
            var steps=jdbc.queryForList("SELECT s.*,w.processing_policy_version FROM metal_work_order_step s JOIN metal_work_order_item i ON i.item_id=s.item_id JOIN metal_work_order w ON w.work_order_id=i.work_order_id WHERE s.item_id=? AND s.sequence_no=1",item);
            if(steps.size()!=1 || !SemiSourcePolicyService.qualificationEntry(steps.get(0)))return 0;
            long issued=count("SELECT COALESCE(SUM(CASE WHEN o.type='PRODUCTION_OUT' AND l.category='SEMI' THEN l.quantity WHEN o.type='SEMI_RETURN' THEN -l.quantity ELSE 0 END),0) FROM metal_warehouse_order o JOIN metal_warehouse_order_line l ON l.order_id=o.order_id JOIN metal_process_batch b ON b.batch_id=o.batch_id WHERE b.item_id=? AND b.sequence_no=1 AND b.status<>'VOID' AND o.status='POSTED' AND l.line_id<>?",item,excludeLine);
            long room=count("SELECT quantity FROM metal_work_order_item WHERE item_id=?",item)-issued-WarehouseReservations.external(jdbc,item,num(steps.get(0).get("process_id")));
            if(room<0)throw fail("首工序半成品接入超过计划件数，请人工核对");
            return room;
        }
        long planned = count("SELECT quantity FROM metal_work_order_item WHERE item_id=?", item), room = planned;
        for (var step : jdbc.queryForList("SELECT sequence_no FROM metal_work_order_step WHERE item_id=? AND sequence_no<? AND measurement_mode='PIECE' AND sequence_no>COALESCE((SELECT MAX(w.sequence_no) FROM metal_work_order_step w WHERE w.item_id=metal_work_order_step.item_id AND w.sequence_no<? AND w.measurement_mode='WEIGHT'),0)", item, sequence, sequence)) {
            long seq = num(step.get("sequence_no"));
            long local = count("SELECT COALESCE(SUM(CASE WHEN status='SETTLED' THEN completed_quantity ELSE planned_quantity END),0) FROM metal_process_batch WHERE item_id=? AND sequence_no=? AND status IN ('OPEN','SETTLED')", item, seq);
            room = Math.min(room, planned - local - credit(item, seq) - WarehouseReservations.pendingCredit(jdbc,item,seq));
        }
        if (room < 0) throw fail("历史加工或接入数量超过计划，请人工核对");
        return room;
    }

    public void checkCapacity(long item) {
        ProcessCoverageService.check(jdbc, item, this);
    }

    public Map<String, Object> inventoryBalance(Map<String, Object> stock) {
        long held = 0;
        BigDecimal weight = BigDecimal.ZERO;
        for (var r : jdbc.queryForList("SELECT release_id FROM metal_semi_release WHERE inventory_id=? AND status='ACTIVE'", stock.get("inventory_id"))) {
            var a = releaseBalance(num(r.get("release_id")),false);
            held += num(a.get("available_quantity"));
            weight = weight.add(dec(a.get("available_weight")));
        }
        return Map.of("releasedQuantity", held, "releasedWeight", weight.setScale(6).toPlainString(), "unreleasedQuantity", num(stock.get("quantity")) - held, "unreleasedWeight", dec(stock.get("net_weight")).subtract(weight).setScale(6).toPlainString());
    }

    public void guardStock(long stock) {
        for (var r : jdbc.queryForList("SELECT release_id FROM metal_semi_release WHERE inventory_id=? AND status='ACTIVE'", stock)) {
            var a = releaseBalance(num(r.get("release_id")),false);
            if (num(a.get("available_quantity")) > 0 || dec(a.get("available_weight")).signum() > 0)
                throw fail("库存存在已释放额度，请先撤销释放；跨工单领用须通过生产出库");
        }
    }

    /**
     * Called under the factory and stock locks, before reversing any adjustment ledger.
     */
    public void guardAdjustmentReversal(long inventoryId) {
        var releases = jdbc.queryForList("SELECT release_id,release_mode FROM metal_semi_release WHERE inventory_id=? AND status='ACTIVE' ORDER BY release_id LIMIT 3", inventoryId);
        if (releases.isEmpty()) return;
        var blockers = new ArrayList<String>();
        for (var release : releases) {
            var downstream = jdbc.queryForList("SELECT DISTINCT o.order_no FROM (" + USES + " AND u.release_id=?) used JOIN metal_warehouse_order_line l ON l.line_id=used.line_id JOIN metal_warehouse_order o ON o.order_id=l.order_id WHERE (used.used_quantity<>0 OR used.used_weight<>0) AND o.factory_id=(SELECT factory_id FROM metal_inventory WHERE inventory_id=?) ORDER BY o.order_no LIMIT 3", release.get("release_id"), inventoryId);
            String mode = "AUTO".equals(release.get("release_mode")) ? "自动" : "手工";
            blockers.add(mode + "释放#" + release.get("release_id") + (downstream.isEmpty() ? "：请先撤销释放" : "：请先处理下游单据" + downstream.stream().map(r -> text(r.get("order_no"))).toList() + "，再撤销释放"));
        }
        throw fail("半成品库存#" + inventoryId + "存在有效释放，不能冲销调整单（最多列出3条）：" + String.join("；", blockers));
    }

    public void guardBatch(long batch) {
        if (count("SELECT COUNT(*) FROM metal_semi_release r JOIN metal_inventory i ON i.inventory_id=r.inventory_id WHERE i.origin_batch_id=? AND r.status='ACTIVE'", batch) > 0)
            throw fail("来源批次存在有效释放记录，请先处理释放及下游工单依赖");
    }

    public List<Map<String, Object>> candidates(long factory, long warehouse, long work, long item, long process) {
        scope.check(factory);
        var w = work(work);
        if (num(w.get("factory_id")) != factory || !Set.of("WAITING_MATERIAL", "IN_PROGRESS").contains(text(w.get("status"))))
            throw fail("请选择当前工厂的待领料或生产中工单");
        var target = one("SELECT s.*,i.work_order_id,w.processing_policy_version,COALESCE(i.material_snapshot,i.product_snapshot) product_snapshot FROM metal_work_order_step s JOIN metal_work_order_item i ON i.item_id=s.item_id JOIN metal_work_order w ON w.work_order_id=i.work_order_id WHERE s.item_id=? AND s.process_id=? AND i.work_order_id=?", item, process, work);
        scope.requireCatalog("process",process,factory);
        target.put("item_id", item);
        var out = new ArrayList<Map<String, Object>>();
        String candidateSql="SELECT i.*,m.material_type,COALESCE(i.metal_type_id_snapshot,m.metal_type_id) metal_type_id,EXISTS(SELECT 1 FROM metal_process_batch b JOIN metal_warehouse_order o ON o.batch_id=b.batch_id WHERE b.batch_id=i.origin_batch_id AND b.work_order_id=? AND o.type='RAW_PROCESS_IN' AND o.status='POSTED') same_work_raw_output FROM metal_inventory i LEFT JOIN metal_material m ON i.category='RAW' AND m.material_id=i.reference_id WHERE i.factory_id=? AND i.warehouse_id=? AND i.del_flag='0' AND i.ownership_status='NORMAL' AND i.net_weight>0 AND i.category IN ('RAW','SEMI') AND ((i.category='RAW' AND EXISTS(SELECT 1 FROM metal_material c JOIN metal_type mt ON mt.metal_type_id=c.metal_type_id AND mt.factory_id=c.factory_id WHERE c.material_id=i.reference_id AND c.factory_id=i.factory_id AND c.del_flag='0')) OR (i.category='SEMI' AND EXISTS(SELECT 1 FROM metal_product c JOIN metal_type mt ON mt.metal_type_id=c.metal_type_id AND mt.factory_id=c.factory_id WHERE c.product_id=i.reference_id AND c.factory_id=i.factory_id AND c.del_flag='0')))";
        if(!ProcessingPolicy.v2(target))candidateSql+=" AND (i.category<>'RAW' OR m.del_flag='0' AND m.metal_type_id=1)";
        var candidateStocks=jdbc.queryForList(candidateSql+" ORDER BY CASE i.category WHEN 'RAW' THEN 0 ELSE 1 END,i.inventory_id",work,factory,warehouse);
        InventoryMeasurement.enrich(jdbc,candidateStocks);
        for (var stock : candidateStocks) {
            if ("RAW".equals(stock.get("category"))) {
                try {
                    ProcessingInputService.requireResolved(jdbc,stock,target);
                    new ProcessRawReturnService(jdbc, scope).guardProduction(num(stock.get("inventory_id")), w);
                    MaterialOwnership.production(stock,w);
                    var candidate = stockCandidate(stock, null);
                    if(dec(candidate.get("available_weight")).signum()<=0||!InventoryMeasurement.weight(candidate)&&num(candidate.get("available_quantity"))<=0)continue;
                    candidate.putAll(new ProcessRawReturnService(jdbc, scope).provenance(num(stock.get("inventory_id"))));
                    out.add(candidate);
                } catch (ServiceException ignored) {
                }
                continue;
            }
            try {
                var source = sourceBatch(stock);
                if (isOwn(source, target)) {
                    Long permit = null;
                    Integer pv = null;
                    if (num(source.get("sequence_no")) + 1 < num(target.get("sequence_no"))) {
                        for (var p : policy.permits(item)) {
                            if (truthEnabled(p.get("enabled")) && num(p.get("source_sequence")) == num(source.get("sequence_no")) && num(p.get("target_sequence")) == num(target.get("sequence_no"))) {
                                permit = num(p.get("permit_id"));
                                pv = (int) num(p.get("version"));
                                break;
                            }
                        }
                    }
                    validateSource(stock, target, null, permit, pv);
                    var candidate = stockCandidate(stock, null);
                    if (permit != null) {
                        candidate.put("skip_permit_id", permit);
                        candidate.put("skip_permit_version", pv);
                        candidate.put("skipped_sequences", RouteExecutionRules.skipped(num(source.get("sequence_no")), num(target.get("sequence_no"))));
                    }
                    out.add(candidate);
                } else {
                    for (var r : jdbc.queryForList("SELECT release_id FROM metal_semi_release WHERE inventory_id=? AND status='ACTIVE' ORDER BY release_id", stock.get("inventory_id"))) {
                        long id = num(r.get("release_id"));
                        validateSource(stock, target, id);
                        var a = availableRelease(id);
                        if (SemiMeasurementRules.weight(stock)) {
                            if (dec(a.get("available_weight")).signum() > 0) out.add(stockCandidate(stock, a));
                            continue;
                        }
                        long room = entryCapacity(item, num(target.get("sequence_no")));
                        long available = Math.min(room, num(a.get("available_quantity")));
                        if (available > 0 && dec(a.get("available_weight")).signum() > 0) {
                            var candidate = stockCandidate(stock, a);
                            candidate.put("available_quantity", Math.min(num(stock.get("quantity")), available));
                            if (num(candidate.get("available_quantity")) < num(a.get("available_quantity")) && dec(candidate.get("available_weight")).compareTo(dec(a.get("available_weight"))) == 0)
                                candidate.put("available_weight", dec(candidate.get("available_weight")).subtract(new BigDecimal("0.000001")));
                            if (dec(candidate.get("available_weight")).signum() > 0) out.add(candidate);
                        }
                    }
                }
            } catch (
                    ServiceException e) {/* Invalid candidates are omitted; direct save still rejects with a reason. */}
        }
        out.sort(Comparator.comparingInt(r -> ProcessingPolicy.v2(target) && "RAW".equals(r.get("category")) && truthEnabled(r.get("same_work_raw_output")) ? -1 : "RAW".equals(r.get("category")) ? 0 : r.get("release_id") == null ? 1 : 2));
        return out;
    }

    private static boolean truthEnabled(Object v) {
        return Boolean.TRUE.equals(v) || "1".equals(text(v));
    }

    public void guardWork(long work) {
        WarehouseReservations.work(jdbc,work);
        var blockers = jdbc.queryForList("SELECT r.release_id,w.order_no FROM metal_semi_release r JOIN metal_work_order w ON w.work_order_id=r.work_order_id WHERE r.work_order_id=? AND r.status='ACTIVE' ORDER BY r.release_id", work);
        if (!blockers.isEmpty()) throw fail("工单存在有效释放/下游依赖，请先处理：" + blockers);
    }

    private Map<String, Object> stockCandidate(Map<String, Object> stock, Map<String, Object> release) {
        var pending=WarehouseReservations.stock(jdbc,num(stock.get("inventory_id")));
        var r = new LinkedHashMap<>(stock);r.put("reserved_weight",pending.get("weight"));r.put("reserved_quantity",pending.get("quantity"));
        r.put("effective_measurement_mode",InventoryMeasurement.mode(stock));
        r.put("id", text(stock.get("inventory_id")));
        r.put("choice_key", text(stock.get("inventory_id")) + (release == null ? "" : ":" + release.get("release_id")));
        r.put("available_quantity", release == null ? Math.max(0,num(stock.get("quantity"))-num(pending.get("quantity"))) : Math.max(0,Math.min(num(stock.get("quantity"))-num(pending.get("quantity")), num(release.get("available_quantity")))));
        if(InventoryMeasurement.weight(stock))r.put("available_quantity",0L);
        r.put("available_weight", release == null ? dec(stock.get("net_weight")).subtract(dec(pending.get("weight"))).max(BigDecimal.ZERO) : dec(stock.get("net_weight")).subtract(dec(pending.get("weight"))).min(dec(release.get("available_weight"))).max(BigDecimal.ZERO));
        if (release != null) {
            r.put("release_id", release.get("release_id"));
            r.put("release_remaining_quantity", release.get("available_quantity"));
            r.put("release_remaining_weight", release.get("available_weight"));
        }
        if (stock.get("origin_batch_id") != null) {
            var b = sourceBatch(stock);
            r.put("origin_batch_status", b.get("status"));
            r.put("source_ready",HandoverSource.ready(jdbc,stock,b));
            r.put("origin_work_order_no", one("SELECT order_no FROM metal_work_order WHERE work_order_id=?", b.get("work_order_id")).get("order_no"));
            r.put("origin_process_sequence", b.get("sequence_no"));
            r.put("origin_process_name", one("SELECT process_name FROM metal_work_order_step WHERE item_id=? AND process_id=?", b.get("item_id"), b.get("process_id")).get("process_name"));
        }
        return r;
    }

    public Map<String, Object> overview(Long workId) {
        var w = work(workId);
        boolean raw=ProcessingPolicy.v2(w)&&"RAW_PROCESSING".equals(w.get("task_type"));
        var items = jdbc.queryForList("SELECT i.item_id,i.quantity planned_quantity,i.product_snapshot,i.material_snapshot,i.planned_input_weight,COALESCE((SELECT SUM(b.completed_quantity) FROM metal_process_batch b WHERE b.item_id=i.item_id AND b.status='SETTLED' AND b.sequence_no=(SELECT MAX(sequence_no) FROM metal_work_order_step WHERE item_id=i.item_id)),0) completed_quantity FROM metal_work_order_item i WHERE i.work_order_id=? ORDER BY i.sequence_no", workId);
        var configurableItems=new HashSet<Long>();
        for (var i : items) {
            if(MeasurementPolicy.configurable(jdbc,i))configurableItems.add(num(i.get("item_id")));
            if(raw){
                i.put("planned_quantity",null);i.put("completed_quantity",null);i.put("cancelled_quantity",null);
                i.put("committed_input_weight",RawPlanService.committedInput(jdbc,num(i.get("item_id"))));
            }else i.put("cancelled_quantity", SemiFlowRules.cancelled(num(i.get("planned_quantity")), num(i.get("completed_quantity"))));
        }
        var stocks = jdbc.queryForList("SELECT i.*,b.item_id,s.process_name,b.sequence_no origin_process_sequence FROM metal_inventory i JOIN metal_process_batch b ON b.batch_id=i.origin_batch_id JOIN metal_work_order_step s ON s.item_id=b.item_id AND s.process_id=b.process_id WHERE b.work_order_id=? AND b.status='SETTLED' AND i.category IN ('RAW','SEMI') AND i.del_flag='0' ORDER BY i.inventory_id", workId);
        InventoryMeasurement.enrich(jdbc,stocks);
        for (var stock : stocks) {
            long held = 0;
            BigDecimal weight = BigDecimal.ZERO;
            if("SEMI".equals(stock.get("category"))) {
                for (var r : jdbc.queryForList("SELECT release_id FROM metal_semi_release WHERE inventory_id=? AND status='ACTIVE'", stock.get("inventory_id"))) {
                    var a = availableRelease(num(r.get("release_id")));
                    held += num(a.get("available_quantity"));
                    weight = weight.add(dec(a.get("available_weight")));
                }
            }
            stock.put("released_quantity", held);
            stock.put("released_weight", weight);
            stock.put("unreleased_quantity", num(stock.get("quantity")) - held);
            stock.put("unreleased_weight", dec(stock.get("net_weight")).subtract(weight));
        }
        var releases = jdbc.queryForList("SELECT r.*,i.code,i.name,i.batch_no FROM metal_semi_release r JOIN metal_inventory i ON i.inventory_id=r.inventory_id WHERE r.work_order_id=? ORDER BY r.release_id DESC", workId);
        for (var r : releases) {
            var a = availableRelease(num(r.get("release_id")));
            r.put("available_quantity", a.get("available_quantity"));
            r.put("available_weight", a.get("available_weight"));
        }
        var progress = new ArrayList<Map<String, Object>>();
        for (var s : jdbc.queryForList("SELECT s.item_id,s.process_id,s.sequence_no,s.process_name,s.measurement_mode,s.weight_stage_closed,s.process_role,s.output_stage FROM metal_work_order_step s JOIN metal_work_order_item i ON i.item_id=s.item_id WHERE i.work_order_id=? ORDER BY i.sequence_no,s.sequence_no", workId)) {
            long local = count("SELECT COALESCE(SUM(completed_quantity),0) FROM metal_process_batch WHERE item_id=? AND process_id=? AND status='SETTLED'", s.get("item_id"), s.get("process_id"));
            s.put("local_quantity", SemiMeasurementRules.weight(s) ? null : local);
            s.put("route_covered", WeightStageProgressService.externalCovered(jdbc, num(s.get("item_id")), num(s.get("sequence_no"))));
            s.put("output_weight", jdbc.queryForObject("SELECT COALESCE(SUM(l.net_weight),0) FROM metal_warehouse_order o JOIN metal_process_batch b ON b.batch_id=o.batch_id JOIN metal_warehouse_order_line l ON l.order_id=o.order_id WHERE b.item_id=? AND b.process_id=? AND b.status='SETTLED' AND o.status='POSTED' AND o.type IN ('RAW_PROCESS_IN','SEMI_IN','FINISHED_IN')", BigDecimal.class, s.get("item_id"), s.get("process_id")));
            long skipped = policy.skipCredit(num(s.get("item_id")), num(s.get("sequence_no")));
            s.put("skipped_quantity", skipped);
            s.put("external_quantity", credit(num(s.get("item_id")), num(s.get("sequence_no"))) - skipped);
            s.put("covered_quantity", local + num(s.get("external_quantity")) + skipped);
            if(raw||configurableItems.contains(num(s.get("item_id")))&&SemiMeasurementRules.weight(s))for(String field:List.of("local_quantity","skipped_quantity","external_quantity","covered_quantity"))s.put(field,null);
            progress.add(s);
        }
        var uses = jdbc.queryForList("SELECT u.use_id,u.release_id,l.line_id,l.quantity,l.net_weight,o.order_no,o.work_order_id target_work_order_id,w.order_no target_work_order_no,o.status,u.route_snapshot FROM metal_semi_release_use u JOIN metal_semi_release r ON r.release_id=u.release_id JOIN metal_warehouse_order_line l ON l.line_id=u.line_id JOIN metal_warehouse_order o ON o.order_id=l.order_id JOIN metal_work_order w ON w.work_order_id=o.work_order_id WHERE r.work_order_id=? OR o.work_order_id=? ORDER BY u.use_id", workId, workId);
        return Map.of("workOrder", w, "items", items, "stocks", stocks, "releases", releases, "progress", progress, "uses", uses, "closures", jdbc.queryForList("SELECT * FROM metal_work_order_close WHERE work_order_id=? ORDER BY close_id DESC", workId));
    }

    private void verifyHistory(long work) {
        for (var r : jdbc.queryForList("SELECT b.completed_quantity,r.report_id,r.voided,r.issued_weight report_issued,r.returned_weight report_returned,r.loss_weight report_loss,r.completed_quantity report_quantity,COALESCE(x.issued,0) issued,COALESCE(x.returned,0) returned,COALESCE(x.output_quantity,0) output_quantity FROM metal_process_batch b LEFT JOIN metal_work_report r ON r.batch_id=b.batch_id LEFT JOIN (SELECT o.batch_id,SUM(CASE WHEN o.type='PRODUCTION_OUT' THEN COALESCE(l.processor_net_weight,l.net_weight) ELSE 0 END) issued,SUM(CASE WHEN o.type IN ('RAW_RETURN','SEMI_RETURN','PROCESS_RAW_RETURN','RAW_PROCESS_IN','SEMI_IN','FINISHED_IN') THEN COALESCE(l.processor_net_weight,l.net_weight) ELSE 0 END) returned,SUM(CASE WHEN o.type IN ('RAW_PROCESS_IN','SEMI_IN','FINISHED_IN') THEN l.quantity ELSE 0 END) output_quantity FROM metal_warehouse_order o JOIN metal_warehouse_order_line l ON l.order_id=o.order_id WHERE o.status='POSTED' GROUP BY o.batch_id) x ON x.batch_id=b.batch_id WHERE b.work_order_id=? AND b.status='SETTLED'", work)) {
            if (r.get("report_id") == null || Boolean.TRUE.equals(r.get("voided")) || dec(r.get("issued")).signum() <= 0 || dec(r.get("issued")).compareTo(dec(r.get("returned"))) < 0 || dec(r.get("report_issued")).compareTo(dec(r.get("issued"))) != 0 || dec(r.get("report_returned")).compareTo(dec(r.get("returned"))) != 0 || dec(r.get("report_loss")).compareTo(dec(r.get("issued")).subtract(dec(r.get("returned")))) != 0 || num(r.get("completed_quantity")) != num(r.get("output_quantity")) || num(r.get("report_quantity")) != num(r.get("completed_quantity")))
                throw fail("历史报工、重量或完成件数异常，请人工核对后关闭");
        }
    }

    public void close(Long id, String request, Integer v, String reason, boolean confirmed, boolean reopen) {
        permit(reopen ? "metal:work-order:reopen" : "metal:work-order:partial-close");
        reason(reason);
        var w = work(id);
        lockFactory(num(w.get("factory_id")));
        w = one("SELECT * FROM metal_work_order WHERE work_order_id=? FOR UPDATE", id);
        String fp = hash(List.of(id, request, v, reason, confirmed, reopen));
        if (replay("metal_work_order_close", "close_id", request, fp) != null) return;
        version(w, v);
        if (reopen) {
            if (!"CLOSED_PARTIAL".equals(w.get("status"))) throw fail("仅部分关闭工单可撤销关闭");
            if (count("SELECT COUNT(*) FROM metal_semi_release WHERE work_order_id=? AND status='ACTIVE'", id) > 0)
                throw fail("存在有效释放及下游引用，请先撤销释放");
        } else {
            if (!Set.of("WAITING_MATERIAL", "IN_PROGRESS").contains(text(w.get("status"))) || !confirmed)
                throw fail("请核对余料并选择可部分关闭的生产工单");
            if (count("SELECT COUNT(*) FROM metal_process_batch WHERE work_order_id=? AND status='OPEN'", id) > 0)
                throw fail("存在未结算批次，请先还库、结算或冲销");
            if (count("SELECT COUNT(*) FROM metal_warehouse_order WHERE work_order_id=? AND status='POSTED' AND type='PRODUCTION_OUT'", id) == 0)
                throw fail("未发生生产业务的工单不能部分关闭");
            for (var item : jdbc.queryForList("SELECT item_id FROM metal_work_order_item WHERE work_order_id=?", id))
                checkCapacity(num(item.get("item_id")));
            verifyHistory(id);
            boolean unfinished = false;
            if(ProcessingPolicy.v2(w)&&"RAW_PROCESSING".equals(w.get("task_type"))){
                WarehouseReservations.work(jdbc,id);
                unfinished=count("SELECT COUNT(*) FROM metal_work_order_step s JOIN metal_work_order_item i ON i.item_id=s.item_id WHERE i.work_order_id=? AND (s.weight_stage_closed=FALSE OR NOT EXISTS(SELECT 1 FROM metal_process_batch b WHERE b.item_id=s.item_id AND b.process_id=s.process_id AND b.status='SETTLED'))",id)>0;
            }else for (var item : (List<Map<String, Object>>) overview(id).get("items"))
                unfinished |= num(item.get("cancelled_quantity")) > 0;
            if (!unfinished) throw fail("工单已足额完成，应按正常完成处理");
            if (count("SELECT COUNT(*) FROM metal_inventory i JOIN metal_process_batch b ON b.batch_id=i.origin_batch_id WHERE b.work_order_id=? AND (i.net_weight<0 OR i.quantity<0 OR i.ownership_status<>'NORMAL')", id) > 0)
                throw fail("存在待核对库存，请先处理");
        }
        var full = overview(id);
        var snapshot = Map.of("items", full.get("items"), "progress", full.get("progress"), "stocks", full.get("stocks"));
        insert("INSERT INTO metal_work_order_close(work_order_id,factory_id,request_id,request_hash,actor_id,action,reason,snapshot) VALUES(?,?,?,?,?,?,?,?)", id, w.get("factory_id"), request, fp, SecurityUtils.getUserId(), reopen ? "REOPEN" : "CLOSE", reason, JSON.toJSONString(snapshot));
        jdbc.update("UPDATE metal_work_order SET status=?,version=version+1,update_time=NOW() WHERE work_order_id=?", reopen ? "IN_PROGRESS" : "CLOSED_PARTIAL", id);
    }

    public long release(Long stockId, String request, Integer v, long quantity, BigDecimal weight, String reason) {
        return release(stockId, request, v, quantity, weight, reason, null);
    }

    public long release(Long stockId, String request, Integer v, Long pieces, BigDecimal weight, String reason, Long reference) {
        long quantity = pieces == null ? 0 : pieces;
        permit("metal:semi-release:add");
        reason(reason);
        var stock = one("SELECT * FROM metal_inventory WHERE inventory_id=? AND del_flag='0'", stockId);
        lockFactory(num(stock.get("factory_id")));
        String fp = hash(reference == null ? Arrays.asList(stockId, request, v, quantity, weight, reason) : Arrays.asList(stockId, request, v, quantity, weight, reason, reference));
        Long old = replay("metal_semi_release", "release_id", request, fp);
        if (old != null) return old;
        weight=BusinessDecimal.positive(weight,"释放净重");
        stock = one("SELECT * FROM metal_inventory WHERE inventory_id=? AND del_flag='0' FOR UPDATE", stockId);
        version(stock, v);
        scope.requireCatalog("product",num(stock.get("reference_id")),num(stock.get("factory_id")));
        var source = sourceBatch(stock);
        if (SemiSourcePolicyService.hasSkipped(source)) throw fail("来源含跳步轨迹，不符合跨工单完整路线，不能释放");
        if (!"SEMI".equals(stock.get("category")) || !"SETTLED".equals(source.get("status")) || !Set.of("COMPLETED", "CLOSED_PARTIAL").contains(text(source.get("work_status"))) || !MaterialOwnership.sameCustomer(stock,source,source))
            throw fail("仅已关闭工单的同客户半成品可释放");
        if (!"NORMAL".equals(stock.get("ownership_status"))) throw fail("半成品来源待核对，不能释放");
        WarehouseService.validateOwnership(stock);
        var snapshot = JSON.parseObject(text(source.get("product_snapshot")));
        if (snapshot == null || snapshot.getString("specification") == null || snapshot.getString("specification").isBlank() || route(num(source.get("item_id")), num(source.get("sequence_no"))).size() != num(source.get("sequence_no")) || !text(stock.get("specification")).trim().equals(snapshot.getString("specification").trim()))
            throw fail("历史半成品缺少产品或路线快照，请人工核对");
        long held = 0;
        BigDecimal heldWeight = BigDecimal.ZERO;
        for (var r : jdbc.queryForList("SELECT release_id FROM metal_semi_release WHERE inventory_id=? AND status='ACTIVE'", stockId)) {
            var a = availableRelease(num(r.get("release_id")));
            held += num(a.get("available_quantity"));
            heldWeight = heldWeight.add(dec(a.get("available_weight")));
        }
        if (SemiMeasurementRules.weight(stock)) {
            if (quantity != 0) throw fail("重量型半成品请填写释放净重及可选参考件数");
            SemiMeasurementRules.reference(reference);
            SemiMeasurementRules.requireWeight(dec(stock.get("net_weight")).subtract(heldWeight), weight);
        } else
            SemiFlowRules.release(num(stock.get("quantity")) - held, dec(stock.get("net_weight")).subtract(heldWeight), quantity, weight);
        long released = new SemiReleaseCore(jdbc).releaseManual(stock, source, quantity, weight, reason, SecurityUtils.getUserId(), request, fp);
        if (reference != null)
            jdbc.update("UPDATE metal_semi_release SET reference_quantity=? WHERE release_id=?", reference, released);
        return released;
    }

    public void weightStage(Long workId, Long itemId, Long processId, String request, Integer version, String reason, boolean confirmed, boolean reopen) {
        permit(reopen ? "metal:work-order:reopen" : "metal:process-batch:settle");
        reason(reason);
        if (!confirmed) throw fail("请确认全部批次及实物已核对");
        if (request == null || !request.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
            throw fail("请求UUID不合法");
        var w = work(workId);
        lockFactory(num(w.get("factory_id")));
        w = one("SELECT * FROM metal_work_order WHERE work_order_id=? FOR UPDATE", workId);
        String fp = hash(Arrays.asList(workId, itemId, processId, request, version, reason, confirmed, reopen));
        var old = jdbc.queryForList("SELECT request_hash,actor_id FROM metal_weight_stage_event WHERE request_id=?", request);
        if (!old.isEmpty()) {
            if (!fp.equals(old.get(0).get("request_hash")) || num(old.get(0).get("actor_id")) != SecurityUtils.getUserId())
                throw fail("重复请求内容或人员不一致");
            return;
        }
        WarehouseReservations.work(jdbc,workId);
        version(w, version);
        if (!Set.of("WAITING_MATERIAL", "IN_PROGRESS").contains(text(w.get("status"))))
            throw fail("当前工单状态不能变更工序投产状态");
        var s = one("SELECT s.* FROM metal_work_order_step s JOIN metal_work_order_item i ON i.item_id=s.item_id WHERE s.item_id=? AND s.process_id=? AND i.work_order_id=? FOR UPDATE", itemId, processId, workId);
        if (!SemiMeasurementRules.weight(s)) throw fail("仅按重量管理工序可确认结束投产");
        if (truthEnabled(s.get("weight_stage_closed")) == !reopen) throw fail("工序投产状态已变化，请刷新");
        if (count("SELECT COUNT(*) FROM metal_process_batch WHERE item_id=? AND process_id=? AND status='OPEN'", itemId, processId) > 0)
            throw fail("存在未结算批次，请先确认余料并结算或冲销");
        if (!reopen && count("SELECT COUNT(*) FROM metal_process_batch WHERE item_id=? AND process_id=? AND status='SETTLED'", itemId, processId) == 0)
            throw fail("尚无有效已结算加工产出，不能结束投产");
        if (reopen && count("SELECT COUNT(*) FROM metal_process_batch WHERE item_id=? AND sequence_no>? AND status<>'VOID'", itemId, s.get("sequence_no")) > 0)
            throw fail("存在下游加工批次，请先处理依赖再重新投产");
        jdbc.update("UPDATE metal_work_order_step SET weight_stage_closed=? WHERE item_id=? AND process_id=?", !reopen, itemId, processId);
        jdbc.update("INSERT INTO metal_weight_stage_event(item_id,process_id,request_id,request_hash,actor_id,action,reason) VALUES(?,?,?,?,?,?,?)", itemId, processId, request, fp, SecurityUtils.getUserId(), reopen ? "REOPEN" : "CLOSE", reason);
        new WarehouseService(jdbc, scope).refreshOrder(workId);
    }

    public void cancel(Long id, String request, Integer v, String reason) {
        permit("metal:semi-release:cancel");
        reason(reason);
        var r = one("SELECT * FROM metal_semi_release WHERE release_id=?", id);
        lockFactory(num(r.get("factory_id")));
        r = one("SELECT * FROM metal_semi_release WHERE release_id=? FOR UPDATE", id);
        String fp = hash(Arrays.asList(id, request, v, reason));
        if (replay("metal_semi_flow_event", "event_id", request, fp) != null) return;
        version(r, v);
        WarehouseReservations.releaseReference(jdbc,id);
        var a = availableRelease(id);
        if (!"ACTIVE".equals(r.get("status")) || num(a.get("available_quantity")) != num(r.get("quantity")) || dec(a.get("available_weight")).compareTo(dec(r.get("net_weight"))) != 0)
            throw fail("释放记录已被使用，请先处理下游单据");
        jdbc.update("UPDATE metal_semi_release SET status='CANCELLED',version=version+1,cancel_actor=?,cancel_reason=?,cancel_time=NOW() WHERE release_id=?", SecurityUtils.getUserId(), reason, id);
        jdbc.update("UPDATE metal_inventory SET version=version+1 WHERE inventory_id=?", r.get("inventory_id"));
        jdbc.update("INSERT INTO metal_semi_flow_event(release_id,action,actor_id,reason,request_id,request_hash) VALUES(?,'CANCEL',?,?,?,?)", id, SecurityUtils.getUserId(), reason, request, fp);
    }

    private void event(long id, String action, String reason) {
        jdbc.update("INSERT INTO metal_semi_flow_event(release_id,action,actor_id,reason) VALUES(?,?,?,?)", id, action, SecurityUtils.getUserId(), reason);
    }
}
