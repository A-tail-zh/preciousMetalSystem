package com.ruoyi.metal.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import jakarta.validation.constraints.*;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.metal.security.FactoryScope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Raw plans count external input once; route output issued again is not new input. */
@Service
public class RawPlanService {
    private final JdbcTemplate jdbc;
    private final FactoryScope scope;
    public RawPlanService(JdbcTemplate jdbc,FactoryScope scope){this.jdbc=jdbc;this.scope=scope;}

    public record Change(
            @NotBlank @Pattern(regexp="[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") String requestId,
            @NotNull @Min(0) Integer version,@NotNull @Positive Long itemId,
            @NotNull @DecimalMin(value="0",inclusive=false) BigDecimal plannedInputWeight,
            @NotBlank @Size(max=500) String reason) {}

    private static final String INTERNAL="EXISTS(SELECT 1 FROM metal_process_batch origin JOIN metal_warehouse_order produced ON produced.batch_id=origin.batch_id AND produced.type='RAW_PROCESS_IN' AND produced.status='POSTED' WHERE origin.batch_id=stock.origin_batch_id AND origin.item_id=b.item_id)";
    private static BigDecimal decimal(Object value){return value==null?BigDecimal.ZERO:new BigDecimal(value.toString());}

    public static boolean internalInput(JdbcTemplate jdbc,long inventoryId,long itemId){
        return jdbc.queryForObject("SELECT COUNT(*) FROM metal_inventory stock JOIN metal_process_batch b ON b.batch_id=stock.origin_batch_id WHERE stock.inventory_id=? AND b.item_id=? AND EXISTS(SELECT 1 FROM metal_warehouse_order o WHERE o.batch_id=b.batch_id AND o.type='RAW_PROCESS_IN' AND o.status='POSTED')",Long.class,inventoryId,itemId)>0;
    }

    public static BigDecimal committedInput(JdbcTemplate jdbc,long itemId){
        return decimal(jdbc.queryForObject("SELECT COALESCE(SUM(l.net_weight-COALESCE((SELECT SUM(r.net_weight) FROM metal_warehouse_order_line r JOIN metal_warehouse_order returned ON returned.order_id=r.order_id WHERE r.source_line_id=l.line_id AND returned.type='RAW_RETURN' AND returned.status='POSTED'),0)),0) FROM metal_warehouse_order o JOIN metal_process_batch b ON b.batch_id=o.batch_id JOIN metal_warehouse_order_line l ON l.order_id=o.order_id JOIN metal_inventory stock ON stock.inventory_id=l.inventory_id WHERE b.item_id=? AND o.type='PRODUCTION_OUT' AND o.status='POSTED' AND l.category='RAW' AND NOT "+INTERNAL,BigDecimal.class,itemId));
    }

    public static BigDecimal pendingInput(JdbcTemplate jdbc,long itemId){return pendingInput(jdbc,itemId,null);}
    public static BigDecimal pendingInput(JdbcTemplate jdbc,long itemId,Long excludedFlowId){
        return decimal(jdbc.queryForObject("SELECT COALESCE(SUM(r.weight),0) FROM metal_stock_reservation r JOIN metal_inventory stock ON stock.inventory_id=r.inventory_id JOIN metal_process_batch b ON b.batch_id=r.batch_id WHERE r.item_id=? AND r.category='RAW' AND r.status='ACTIVE'"+(excludedFlowId==null?"":" AND r.flow_id<>?")+" AND NOT "+INTERNAL,BigDecimal.class,excludedFlowId==null?new Object[]{itemId}:new Object[]{itemId,excludedFlowId}));
    }

    public static void requireMinimum(BigDecimal planned,BigDecimal committed,BigDecimal pending){
        BigDecimal minimum=committed.add(pending);
        if(planned.compareTo(minimum)<0)throw new ServiceException("计划投入重量不能低于已投入及待确认投入合计"+BusinessDecimal.display(minimum)+"g");
    }

    /** Caller holds the factory lock, including while creating or confirming reservations. */
    public static void requireCapacity(JdbcTemplate jdbc,long itemId,BigDecimal additional,Long excludedFlowId){
        var rows=jdbc.queryForList("SELECT i.planned_input_weight FROM metal_work_order_item i JOIN metal_work_order w ON w.work_order_id=i.work_order_id WHERE i.item_id=? AND w.task_type='RAW_PROCESSING'",itemId);
        if(rows.isEmpty())return;
        if(additional==null||additional.signum()<0)throw new ServiceException("新增投入重量无效");
        requireMinimum(decimal(rows.get(0).get("planned_input_weight")),committedInput(jdbc,itemId),pendingInput(jdbc,itemId,excludedFlowId).add(additional));
    }

    @Transactional(rollbackFor=Exception.class,isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void change(Long work,Change input){
        if(input==null||input.requestId()==null||!input.requestId().matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")||input.version()==null||input.version()<0||input.itemId()==null||input.itemId()<=0||input.reason()==null||input.reason().isBlank()||input.reason().length()>500)throw new ServiceException("计划变更须提供UUID、版本、明细和变更原因");
        BigDecimal weight=BusinessDecimal.positive(input.plannedInputWeight(),"计划投入重量");
        var initial=jdbc.queryForList("SELECT factory_id FROM metal_work_order WHERE work_order_id=?",work);
        if(initial.isEmpty())throw new ServiceException("工单不存在或已删除");
        long factory=Long.parseLong(initial.get(0).get("factory_id").toString());scope.check(factory);scope.lockFactories(List.of(factory));
        var order=jdbc.queryForMap("SELECT factory_id,task_type,status,version,del_flag FROM metal_work_order WHERE work_order_id=? FOR UPDATE",work);
        if(factory!=Long.parseLong(order.get("factory_id").toString()))throw new ServiceException("工单归属已变化，请刷新后重试");
        String hash=hash(work+":"+input);
        var replay=jdbc.queryForList("SELECT request_hash,actor_id FROM metal_raw_plan_change WHERE request_id=? FOR UPDATE",input.requestId());
        if(!replay.isEmpty()){
            if(!hash.equals(replay.get(0).get("request_hash"))||!SecurityUtils.getUserId().toString().equals(replay.get(0).get("actor_id").toString()))throw new ServiceException("请求标识已使用，不能提交不同内容");
            return;
        }
        if(!"0".equals(Objects.toString(order.get("del_flag"),"0")))throw new ServiceException("工单不存在或已删除");
        if(!"RAW_PROCESSING".equals(order.get("task_type")))throw new ServiceException("仅原料加工任务允许变更投入计划");
        if(((Number)order.get("version")).intValue()!=input.version())throw new ServiceException("工单已被修改，请刷新后重试");
        if(!Set.of("WAITING_MATERIAL","IN_PROGRESS","COMPLETED").contains(order.get("status")))throw new ServiceException("当前工单状态不允许变更投入计划");
        var items=jdbc.queryForList("SELECT planned_input_weight FROM metal_work_order_item WHERE item_id=? AND work_order_id=? FOR UPDATE",input.itemId(),work);
        if(items.isEmpty())throw new ServiceException("原料明细不属于本工单",403);
        requireMinimum(weight,committedInput(jdbc,input.itemId()),pendingInput(jdbc,input.itemId()));
        jdbc.update("UPDATE metal_work_order_item SET planned_input_weight=? WHERE item_id=?",weight,input.itemId());
        jdbc.update("UPDATE metal_work_order SET version=version+1,update_by=?,update_time=NOW() WHERE work_order_id=?",SecurityUtils.getUsername(),work);
        String actor=SecurityUtils.getLoginUser().getUser().getNickName();
        jdbc.update("INSERT INTO metal_raw_plan_change(request_id,request_hash,work_order_id,item_id,old_weight,new_weight,reason,actor_id,actor_name) VALUES(?,?,?,?,?,?,?,?,?)",input.requestId(),hash,work,input.itemId(),items.get(0).get("planned_input_weight"),weight,input.reason().trim(),SecurityUtils.getUserId(),actor==null||actor.isBlank()?SecurityUtils.getUsername():actor);
    }
    private static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
