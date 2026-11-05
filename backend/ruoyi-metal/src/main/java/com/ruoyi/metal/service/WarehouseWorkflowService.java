package com.ruoyi.metal.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.metal.catalog.WarehouseRequests.*;
import com.ruoyi.metal.catalog.WarehouseFlowRequests.*;
import com.ruoyi.metal.security.FactoryScope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Owns reservations and real-person confirmations; only the final transaction writes physical ledgers. */
@Service
@Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
public class WarehouseWorkflowService {
    private final JdbcTemplate jdbc;
    private final FactoryScope scope;
    private final WarehouseService warehouse;
    private final WarehouseImportService imports;
    private final OutboundDraftService drafts;
    public WarehouseWorkflowService(JdbcTemplate jdbc,FactoryScope scope,WarehouseService warehouse,
                                    WarehouseImportService imports,OutboundDraftService drafts) {
        this.jdbc=jdbc;this.scope=scope;this.warehouse=warehouse;this.imports=imports;this.drafts=drafts;
    }
    private static final tools.jackson.databind.json.JsonMapper FLOW_JSON=tools.jackson.databind.json.JsonMapper.builder().build();
    static String json(Object value){return FLOW_JSON.writeValueAsString(value).replace(",\"powderOrigin\":null", "").replace(",\"pieceQuantities\":null", "").replace(",\"batchAction\":null", "").replace(",\"plannedQuantity\":null", "").replace(",\"wipBalances\":null", "").replace(",\"flowId\":null", "");}
    static long n(Object value){return value==null?0:new BigDecimal(value.toString()).longValueExact();}
    static BigDecimal d(Object value){return value==null?BigDecimal.ZERO:new BigDecimal(value.toString());}
    private static Long id(Object value){return value==null?null:Long.valueOf(value.toString());}
    private static ServiceException fail(String message){return new ServiceException(message);}
    private void permit(String permission){if(!scope.isAdmin()&&!SecurityUtils.hasPermi(permission))throw new ServiceException("没有此操作权限",403);}
    private void readPermit(){if(!scope.isAdmin()&&!List.of("query","add","receive","handover","accept").stream().anyMatch(p->SecurityUtils.hasPermi("metal:warehouse-order:"+p)))throw new ServiceException("没有仓储查询权限",403);}
    private Map<String,Object> one(String sql,Object...args){var rows=jdbc.queryForList(sql,args);if(rows.size()!=1)throw fail("仓储记录不存在或已失效");return new LinkedHashMap<String,Object>(rows.get(0));}
    private Map<String,Object> inventory(String sql,Object...args){var row=one(sql,args);if(row.get("factory_id")!=null)scope.check(n(row.get("factory_id")));InventoryMeasurement.enrich(jdbc,List.of(row));return row;}
    private long insert(String sql,Object...args){var key=new GeneratedKeyHolder();jdbc.update(c->{var ps=c.prepareStatement(sql,java.sql.Statement.RETURN_GENERATED_KEYS);for(int i=0;i<args.length;i++)ps.setObject(i+1,args[i]);return ps;},key);return key.getKey().longValue();}
    private static String hash(Object value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json(value).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private static String child(String request,int index){return UUID.nameUUIDFromBytes(("warehouse-confirmation:"+request+":"+index).getBytes(StandardCharsets.UTF_8)).toString();}
    private Map<String,Object> root(long flow,boolean lock){var r=one("SELECT * FROM metal_warehouse_flow WHERE flow_id=?",flow);scope.check(n(r.get("factory_id")));if(lock){scope.lockFactories(List.of(n(r.get("factory_id"))));r=one("SELECT * FROM metal_warehouse_flow WHERE flow_id=? FOR UPDATE",flow);}return r;}
    private void version(Map<String,Object> root,Integer version){if(version==null||n(root.get("version"))!=version)throw new ServiceException("单据版本已变化，请重新加载后确认",409);}
    private void pending(Map<String,Object> root){if(!Set.of("WAITING","COLLECTING","RETURNED").contains(root.get("status")))throw fail("申请已生效、取消或冲销，不能重复操作");}
    private Long replay(String request,String operation,String fingerprint,long flow){
        WarehouseFlowRules.requestId(request);var rows=jdbc.queryForList("SELECT * FROM metal_warehouse_flow_request WHERE request_id=?",request);
        if(rows.isEmpty())return null;var r=rows.get(0);
        if(n(r.get("actor_id"))!=SecurityUtils.getUserId()||!operation.equals(r.get("operation"))||!fingerprint.equals(r.get("request_hash"))||flow!=0&&n(r.get("flow_id"))!=flow)throw new ServiceException("同一阶段请求的内容、目标或操作人不一致",409);
        return n(r.get("flow_id"));
    }
    private void remember(long flow,String request,String operation,String fingerprint){jdbc.update("INSERT INTO metal_warehouse_flow_request(request_id,flow_id,operation,actor_id,request_hash) VALUES(?,?,?,?,?)",request,flow,operation,SecurityUtils.getUserId(),fingerprint);}
    private void event(long flow,String action,Object content){jdbc.update("INSERT INTO metal_warehouse_flow_event(flow_id,version,actor_id,action,content) SELECT flow_id,version,?,?,? FROM metal_warehouse_flow WHERE flow_id=?",SecurityUtils.getUserId(),action,json(content),flow);}
    private List<Map<String,Object>> groups(long flow){return jdbc.queryForList("SELECT * FROM metal_warehouse_flow_group WHERE flow_id=? AND status<>'SUPERSEDED' ORDER BY sequence_no,group_id",flow);}
    private List<Map<String,Object>> lines(long group){return jdbc.queryForList("SELECT * FROM metal_warehouse_flow_line WHERE group_id=? ORDER BY sequence_no",group);}
    private Order order(Map<String,Object> group){return JSON.parseObject(group.get("order_json").toString(),Order.class);}
    private void readable(long flow){
        if(scope.isAdmin()||SecurityUtils.hasPermi("metal:warehouse-order:query")||SecurityUtils.hasPermi("metal:warehouse-order:add"))return;
        long actor=SecurityUtils.getUserId();
        if(groups(flow).stream().noneMatch(g->n(g.get("recipient_id"))==actor||n(g.get("keeper_id"))==actor||n(g.get("processor_id"))==actor)
                &&jdbc.queryForList("SELECT actor_id FROM metal_warehouse_flow_processor WHERE flow_id=? AND actor_id=?",flow,actor).isEmpty())throw new ServiceException("只能查看本人参与的仓储申请",403);
    }

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> detail(long flow){
        readPermit();var r=root(flow,false);readable(flow);var result=warehouse.clean(r);
        result.remove("requestHash");result.remove("planJson");result.put("id",Long.toString(flow));
        if(PartialHandoverLedger.partial(r)&&"HANDOVER".equals(r.get("kind")))result.put("wipAllocations",warehouse.cleaned(jdbc.queryForList("SELECT w.group_key,SUM(a.allocated_weight) allocated_weight,SUM(a.after_weight) after_weight FROM metal_handover_allocation a JOIN metal_batch_wip_balance w ON w.source_line_id=a.source_line_id WHERE a.flow_id=? AND a.voided=FALSE GROUP BY w.group_key",flow)));
        var data=new ArrayList<Map<String,Object>>();var history=new ArrayList<Map<String,Object>>();
        var modeStocks=InventoryMeasurement.enrich(jdbc,jdbc.queryForList("SELECT l.flow_line_id mode_flow_line_id,i.* FROM metal_warehouse_flow_line l JOIN metal_warehouse_flow_group g ON g.group_id=l.group_id LEFT JOIN metal_warehouse_order_line source ON source.line_id=l.source_line_id JOIN metal_inventory i ON i.inventory_id=COALESCE(l.inventory_id,source.inventory_id) WHERE g.flow_id=? ORDER BY l.flow_line_id",flow).stream().map(row->(Map<String,Object>)new LinkedHashMap<String,Object>(row)).toList());
        var stocksByLine=new HashMap<Long,Map<String,Object>>();for(var stock:modeStocks)stocksByLine.put(n(stock.get("mode_flow_line_id")),stock);
        var outputsByBatch=new HashMap<Long,Map<String,Object>>();

        for(var g:jdbc.queryForList("SELECT * FROM metal_warehouse_flow_group WHERE flow_id=? ORDER BY group_id",flow)){
            var value=warehouse.clean(g);value.remove("orderJson");value.put("id",g.get("group_id").toString());
            value.putAll(warehouse.clean(one("SELECT order_no,warehouse_name,operator_name,keeper_name,recipient_name,reviewer_name,u.nick_name confirmed_by_name,u.del_flag confirmed_by_deleted FROM metal_warehouse_order o LEFT JOIN sys_user u ON u.user_id=? WHERE o.order_id=?",g.get("confirmed_by"),g.get("order_id"))));
            var header=JSON.parseObject(g.get("order_json").toString());header.remove("lines");value.put("order",warehouse.clean(header));
            var details=new ArrayList<Map<String,Object>>();for(var l:lines(n(g.get("group_id")))){
                var row=warehouse.clean(l);row.remove("lineJson");row.put("id",l.get("flow_line_id").toString());
                var displayLine=warehouse.clean(JSON.parseObject(l.get("line_json").toString()));
                displayLine.remove("effectiveMeasurementMode");displayLine.remove("measurementPolicyVersion");
                var stock=stocksByLine.get(n(l.get("flow_line_id")));
                if(stock!=null){var saved=warehouse.clean(stock);saved.remove("modeFlowLineId");row.put("stock",saved);displayLine.put("effectiveMeasurementMode",InventoryMeasurement.mode(stock));displayLine.put("measurementPolicyVersion",stock.getOrDefault("measurement_policy_version",0));}
                else if(header.get("batchId")!=null&&ProcessingPolicy.primaryOutput(Objects.toString(g.get("type"),""))){
                    long batchId=n(header.get("batchId"));var facts=outputsByBatch.computeIfAbsent(batchId,key->{var values=new LinkedHashMap<String,Object>();warehouse.batchMeasurement(values,one("SELECT * FROM metal_process_batch WHERE batch_id=?",key));return values;});displayLine.putAll(facts);
                }else if("RAW".equals(displayLine.get("category"))){displayLine.put("effectiveMeasurementMode","WEIGHT");displayLine.put("measurementPolicyVersion",0);}
                row.put("line",displayLine);
                details.add(row);
            }
            value.put("lines",details);if("SUPERSEDED".equals(g.get("status")))history.add(value);else data.add(value);
        }
        result.put("revisions",history);
        result.put("groups",data);result.put("processors",warehouse.cleaned(jdbc.queryForList("SELECT p.*,u.nick_name name FROM metal_warehouse_flow_processor p JOIN sys_user u ON u.user_id=p.actor_id WHERE p.flow_id=? ORDER BY p.actor_id",flow)));
        result.put("events",warehouse.cleaned(jdbc.queryForList("SELECT e.event_id,e.version,e.actor_id,e.action,e.content,e.create_time,u.nick_name actor_name,u.del_flag actor_deleted FROM metal_warehouse_flow_event e LEFT JOIN sys_user u ON u.user_id=e.actor_id WHERE e.flow_id=? ORDER BY e.event_id",flow)));
        return result;
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> list(long factory){
        readPermit();scope.check(factory);boolean all=scope.isAdmin()||SecurityUtils.hasPermi("metal:warehouse-order:query")||SecurityUtils.hasPermi("metal:warehouse-order:add");long actor=SecurityUtils.getUserId();return warehouse.cleaned(jdbc.queryForList("SELECT f.flow_id id,f.kind,f.status,f.stage,f.version,f.batch_id,f.work_order_id,w.order_no work_order_no,u.nick_name creator_name,f.create_time,(SELECT GROUP_CONCAT(DISTINCT o.order_no ORDER BY o.order_id SEPARATOR '、') FROM metal_warehouse_order o WHERE o.warehouse_flow_id=f.flow_id AND o.status NOT IN ('CANCELLED','VOID')) order_numbers FROM metal_warehouse_flow f LEFT JOIN metal_work_order w ON w.work_order_id=f.work_order_id LEFT JOIN sys_user u ON u.user_id=f.request_actor WHERE f.factory_id=?"+(all?"":" AND (f.request_actor=? OR EXISTS(SELECT 1 FROM metal_warehouse_flow_group g WHERE g.flow_id=f.flow_id AND g.status<>'SUPERSEDED' AND (g.keeper_id=? OR g.recipient_id=? OR g.processor_id=?)))")+" ORDER BY CASE WHEN f.status IN ('WAITING','COLLECTING','RETURNED') THEN 0 ELSE 1 END,f.flow_id DESC LIMIT 100",all?new Object[]{factory}:new Object[]{factory,actor,actor,actor,actor}));
    }

    private static Order copy(Order o,String request,Long batch,Integer batchVersion,List<Line> lines){return new Order(request,o.factoryId(),o.warehouseId(),o.type(),o.orderDate(),o.operatorId(),o.operatorIds(),o.keeperId(),o.recipientId(),o.inspectorId(),o.reviewerId(),o.workOrderId(),batch,batchVersion,false,null,o.reason(),lines,o.itemId(),o.processId());}
    private static Line measured(Line l,BigDecimal weight){return new Line(l.inventoryId(),l.sourceLineId(),l.category(),l.referenceId(),l.batchNo(),weight,l.quantity(),null,null,l.purity(),l.organization(),l.purpose(),l.remark(),l.receivingOrganization(),l.releaseId(),l.skipPermitId(),l.skipPermitVersion(),l.referenceQuantity(),l.customerId(),l.customerVersion(),l.materialOwnership(),l.powderOrigin());}

    public Map<String,Object> create(Create input){return create(input.requestId(),input.factoryId(),input.documents(),hash(List.of("FLOW",input)));}
    public Map<String,Object> single(Order input){
        permit("metal:warehouse-order:add");
        var old=jdbc.queryForList("SELECT order_id,warehouse_flow_id FROM metal_warehouse_order WHERE request_id=?",input.requestId());
        if(!old.isEmpty()&&old.get(0).get("warehouse_flow_id")==null){String document=warehouse.post(input);return Map.of("id",document,"legacy",true);}
        if(Set.of("SEMI_IN","FINISHED_IN","RAW_PROCESS_IN","RAW_RETURN","SEMI_RETURN","PROCESS_RAW_RETURN").contains(input.type())){
            if(legacyBatch(input.batchId(),input.itemId(),input.processId()))return Map.of("id",warehouse.post(input),"legacy",true);
            throw fail("加工产出及全部余料须由加工人员通过交接清单提交，库管验收后统一入库");
        }
        var result=create(input.requestId(),input.factoryId(),List.of(input),hash(List.of("SINGLE",input)));
        return submissionResult(result);
    }
    public Map<String,Object> outbound(com.ruoyi.metal.catalog.OutboundSubmissionRequests.Request r){
        if("PRODUCTION_OUT".equals(r.type()))OutboundSubmissionRules.validate(r,false);else if(!Set.of("SEMI_OUT","PRODUCT_OUT").contains(r.type())||r.workOrderId()!=null||r.itemId()!=null||r.processId()!=null||r.batchId()!=null||r.batchAction()!=null||r.plannedQuantity()!=null||r.groups()==null||r.groups().isEmpty())throw fail("请选择有效普通出库类型及仓库，普通出库不推进生产工单");
        var documents=new ArrayList<Order>();int index=0;
        for(var g:r.groups()){var p=g.peopleOverride()==null?new com.ruoyi.metal.catalog.OutboundSubmissionRequests.People(r.operatorIds(),r.recipientId(),r.keeperId(),r.reviewerId()):g.peopleOverride();documents.add(new Order(child(r.requestId(),index++),r.factoryId(),g.warehouseId(),r.type(),r.orderDate(),null,p.operatorIds(),p.keeperId(),p.recipientId(),null,p.reviewerId(),r.workOrderId(),r.batchId(),r.batchVersion(),false,null,null,g.lines(),r.itemId(),r.processId()));}
        return submissionResult(create(r.requestId(),r.factoryId(),documents,hash(List.of("MULTI_OUT",r)),r));
    }
    public Map<String,Object> inbound(com.ruoyi.metal.catalog.InboundSubmissionRequests.Request r){
        if(!"RAW_IN".equals(r.type()))throw fail("半成品及成品由加工交接验收形成入库单，不能重复手工建单");
        InboundSubmissionRules.validate(r);var documents=new ArrayList<Order>();int index=0;
        for(var g:r.groups()){var p=g.peopleOverride()==null?new com.ruoyi.metal.catalog.InboundSubmissionRequests.People(r.operatorIds(),r.keeperId(),r.inspectorId(),r.reviewerId()):g.peopleOverride();documents.add(new Order(child(r.requestId(),index++),r.factoryId(),g.warehouseId(),r.type(),r.orderDate(),null,p.operatorIds(),p.keeperId(),null,p.inspectorId(),p.reviewerId(),null,null,null,false,null,null,g.lines(),null,null));}
        return submissionResult(create(r.requestId(),r.factoryId(),documents,hash(List.of("MULTI_IN",r))));
    }
    @Transactional(readOnly=true,isolation=Isolation.READ_COMMITTED)
    public boolean legacyBatch(Long batch,Long item,Long process){
        var rows=batch!=null?jdbc.queryForList("SELECT completion_policy FROM metal_process_batch WHERE batch_id=?",batch):item==null||process==null?List.<Map<String,Object>>of():jdbc.queryForList("SELECT completion_policy FROM metal_process_batch WHERE item_id=? AND process_id=? AND status='OPEN'",item,process);
        return rows.size()==1&&!WarehouseFlowRules.POLICY.equals(rows.get(0).get("completion_policy"));
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> handoverBatches(long factory){readPermit();scope.check(factory);return warehouse.cleaned(jdbc.queryForList("SELECT b.*,w.order_no,w.customer,w.customer_id,COALESCE(i.material_snapshot,i.product_snapshot) product_snapshot,w.task_type,i.material_id,i.planned_input_weight,s.process_name FROM metal_process_batch b JOIN metal_work_order w ON w.work_order_id=b.work_order_id JOIN metal_work_order_item i ON i.item_id=b.item_id JOIN metal_work_order_step s ON s.item_id=b.item_id AND s.process_id=b.process_id WHERE b.factory_id=? AND b.status='OPEN' AND EXISTS(SELECT 1 FROM metal_warehouse_order o WHERE o.batch_id=b.batch_id AND o.status='POSTED' AND o.type='PRODUCTION_OUT' AND o.recipient_id=? AND o.confirmed_by=?) ORDER BY b.batch_id",factory,SecurityUtils.getUserId(),SecurityUtils.getUserId()));}
    private Map<String,Object> submissionResult(Map<String,Object> result){
        var out=new LinkedHashMap<String,Object>();out.put("flowId",result.get("id"));out.put("flow",result);out.put("batchId",result.get("batchId"));out.put("status",result.get("status"));out.put("nextAction","CONFIRM");
        if(result.containsKey("reused"))out.put("reused",result.get("reused"));
        var orders=jdbc.queryForList("SELECT CAST(order_id AS CHAR) id,order_no orderNo,CAST(warehouse_id AS CHAR) warehouseId,warehouse_name warehouseName,status FROM metal_warehouse_order WHERE warehouse_flow_id=? AND status<>'CANCELLED' ORDER BY order_id",result.get("id"));out.put("orders",orders);if(!orders.isEmpty())out.put("id",orders.get(0).get("id"));
        if(result.get("batchId")!=null){var b=one("SELECT batch_no,version,status FROM metal_process_batch WHERE batch_id=?",result.get("batchId"));out.put("batchNo",b.get("batch_no"));out.put("batchVersion",b.get("version"));out.put("batchStatus",b.get("status"));}return out;
    }
    Map<String,Object> create(String request,long factory,List<Order> requested,String fingerprint){return create(request,factory,requested,fingerprint,null);}
    private Map<String,Object> create(String request,long factory,List<Order> requested,String fingerprint,com.ruoyi.metal.catalog.OutboundSubmissionRequests.Request batchPlan){
        permit("metal:warehouse-order:add");WarehouseFlowRules.requestId(request);scope.lockFactories(List.of(factory));
        var previous=jdbc.queryForList("SELECT * FROM metal_warehouse_flow WHERE request_id=?",request);
        if(!previous.isEmpty()){var old=previous.get(0);if(n(old.get("request_actor"))!=SecurityUtils.getUserId()||!fingerprint.equals(old.get("request_hash")))throw new ServiceException("提交请求已使用，内容或操作人不一致",409);return createResult(n(old.get("flow_id")),true);}
        if(requested==null||requested.isEmpty()||requested.stream().mapToInt(o->o.lines()==null?1001:o.lines().size()).sum()>1000)throw fail("每次须填写1至1000条仓储明细");
        String type=requested.get(0).type();String kind=switch(type){case "PRODUCTION_OUT","SEMI_OUT","PRODUCT_OUT"->"OUTBOUND";case "RAW_IN"->"RAW_INBOUND";case "PRODUCT_RETURN"->"PRODUCT_RETURN";case "EXCHANGE_OUT"->"CUSTOMER_RETURN";default->throw fail("生产产出及余料须从加工交接入口统一提交");};
        if("EXCHANGE_OUT".equals(type))throw fail("返还客户须由本工单实际加工人员从客户余料收尾发起，并由指定库管确认交付");
        var seen=new HashSet<Long>();for(var o:requested){if(o.factoryId()==null||o.factoryId()!=factory||!type.equals(o.type())||!seen.add(o.warehouseId())||o.lines()==null||o.lines().isEmpty()||o.orderDate()==null)throw fail("仓储类型、工厂或仓库明细无效");if(Boolean.TRUE.equals(o.settle()))throw fail("请通过交接验收统一结算，不能在建单时直接结算");warehouse.workflowHeader(o);}
        var documents=requested.stream().map(WarehousePrecision::order).toList();Long batch=documents.get(0).batchId();boolean created=false;
        if("PRODUCTION_OUT".equals(type)&&batch!=null){
            var context=one("SELECT factory_id,work_order_id,item_id,process_id,version FROM metal_process_batch WHERE batch_id=?",batch);if(n(context.get("factory_id"))!=factory)throw new ServiceException("批次不属于当前工厂",403);var resolved=new ArrayList<Order>();
            for(var o:documents)if(o.batchVersion()==null||o.batchVersion()!=n(context.get("version")))throw new ServiceException("加工批次版本已变化，请重新加载",409);
            for(var o:documents)resolved.add(new Order(o.requestId(),o.factoryId(),o.warehouseId(),o.type(),o.orderDate(),o.operatorId(),o.operatorIds(),o.keeperId(),o.recipientId(),o.inspectorId(),o.reviewerId(),o.workOrderId()==null?id(context.get("work_order_id")):o.workOrderId(),o.batchId(),o.batchVersion(),o.settle(),o.completedQuantity(),o.reason(),o.lines(),o.itemId()==null?id(context.get("item_id")):o.itemId(),o.processId()==null?id(context.get("process_id")):o.processId()));documents=resolved;
        }
        Order first=documents.get(0);
        if("PRODUCTION_OUT".equals(type)){
            if(first.workOrderId()==null||first.itemId()==null||first.processId()==null)throw fail("请选择生产工单、产品及工序");
            for(var o:documents)if(!Objects.equals(o.workOrderId(),first.workOrderId())||!Objects.equals(o.itemId(),first.itemId())||!Objects.equals(o.processId(),first.processId())||!Objects.equals(o.batchId(),batch))throw fail("各仓生产上下文须一致");
            var planning=new ContinuousBatchPlanning(jdbc,scope);
            if(batchPlan!=null&&"NEW".equals(batchPlan.batchAction())){
                drafts.requireValid(drafts.inspect(batchPlan));
                Integer autoQuantity=batchPlan.plannedQuantity();
                if(autoQuantity==null){var selectedStocks=new ArrayList<Map<String,Object>>();for(var doc:documents)for(var line:doc.lines())selectedStocks.add(one("SELECT * FROM metal_inventory WHERE inventory_id=?",line.inventoryId()));InventoryMeasurement.enrich(jdbc,selectedStocks);int i=0;long count=0;for(var doc:documents)for(var line:doc.lines()){var stock=selectedStocks.get(i++);if(!InventoryMeasurement.weight(stock))count+=line.quantity()==null?0:line.quantity();}if(count>0&&!"WEIGHT".equals(one("SELECT measurement_mode FROM metal_work_order_step WHERE item_id=? AND process_id=?",first.itemId(),first.processId()).get("measurement_mode")))autoQuantity=Math.toIntExact(count);}
                int quantity=planning.newQuantity(factory,first.workOrderId(),first.itemId(),first.processId(),autoQuantity);
                batch=Long.valueOf(warehouse.createBatch(new Batch(child(request,1001),first.workOrderId(),first.itemId(),first.processId(),quantity)));created=true;
            }else{
                var selected=planning.select(first.itemId(),first.processId(),batch,first.batchVersion(),batchPlan==null?null:batchPlan.batchAction());
                var bound=warehouse.bindInboundSubmission(first);batch=bound.batchId();created=selected==null;
            }

            int v=jdbc.queryForObject("SELECT version FROM metal_process_batch WHERE batch_id=?",Integer.class,batch);var updated=new ArrayList<Order>();for(var o:documents)updated.add(copy(o,o.requestId(),batch,v,o.lines()));documents=updated;
        }
        for(var o:documents)if(!"PRODUCTION_OUT".equals(type)&&o.batchId()!=null)throw fail("普通出入库不能绑定加工批次");
        validateDocuments(documents);
        long flow=insert("INSERT INTO metal_warehouse_flow(request_id,request_hash,request_actor,factory_id,kind,stage,work_order_id,batch_id,created_batch,plan_json) VALUES(?,?,?,?,?,?,?,?,?,?)",request,fingerprint,SecurityUtils.getUserId(),factory,kind,"OUTBOUND".equals(kind)?"RECIPIENT":"KEEPER",first.workOrderId(),batch,created,json(requested));
        int index=0;for(var o:documents)addGroup(flow,copy(o,documents.size()==1?request:child(request,index++),o.batchId(),o.batchVersion(),o.lines()),null);
        reserve(flow);remember(flow,request,"CREATE",fingerprint);event(flow,"CREATE",requested);return createResult(flow,false);
    }

    private Map<String,Object> createResult(long flow,boolean reused){var result=detail(flow);result.put("reused",reused);return result;}

    static void validateHandoverPieces(JdbcTemplate jdbc,Map<String,Object> batch,long flow){
        if(SemiMeasurementRules.weight(batch))return;
        long pieces=jdbc.queryForObject("SELECT COALESCE(SUM(CAST(JSON_UNQUOTE(JSON_EXTRACT(l.line_json,'$.quantity')) AS UNSIGNED)),0) FROM metal_warehouse_flow_line l JOIN metal_warehouse_flow_group g ON g.group_id=l.group_id WHERE g.flow_id=? AND g.status<>'SUPERSEDED' AND g.type IN ('SEMI_IN','FINISHED_IN','RAW_PROCESS_IN')",Long.class,flow);
        if(pieces>n(batch.get("planned_quantity")))throw fail("全部加工人员申报产出件数合计超过本批计划，请核对各人实际产出");
        String returnedTypes=MeasurementPolicy.configurable(jdbc,batch)?"g.type IN ('RAW_RETURN','SEMI_RETURN')":"g.type='SEMI_RETURN'";
        long returning=jdbc.queryForObject("SELECT COALESCE(SUM(CAST(JSON_UNQUOTE(JSON_EXTRACT(l.line_json,'$.quantity')) AS UNSIGNED)),0) FROM metal_warehouse_flow_line l JOIN metal_warehouse_flow_group g ON g.group_id=l.group_id WHERE g.flow_id=? AND g.status<>'SUPERSEDED' AND "+returnedTypes,Long.class,flow);
        if(PartialHandoverLedger.partial(batch)) {
            long prior=jdbc.queryForObject("SELECT COALESCE(SUM(CAST(JSON_UNQUOTE(JSON_EXTRACT(l.line_json,'$.quantity')) AS UNSIGNED)),0) FROM metal_warehouse_flow f JOIN metal_warehouse_flow_group g ON g.flow_id=f.flow_id AND g.status<>'SUPERSEDED' JOIN metal_warehouse_flow_line l ON l.group_id=g.group_id WHERE f.batch_id=? AND f.flow_id<>? AND f.kind='HANDOVER' AND f.status NOT IN ('VOID','CANCELLED') AND (g.type IN ('SEMI_IN','FINISHED_IN','RAW_PROCESS_IN') OR "+returnedTypes+")",Long.class,batch.get("batch_id"),flow);
            pieces+=prior;
            if(pieces+returning>n(batch.get("planned_quantity")))throw fail("已验收、待验收及本次交接合计超过本批计划件数");
        }
        WarehouseService.validateDownstreamOutput(jdbc,batch,pieces+returning);
    }

    private void validateDocuments(List<Order> documents){
        if(!documents.isEmpty()&&"PRODUCTION_OUT".equals(documents.get(0).type())&&documents.get(0).batchId()!=null){
            var target=one("SELECT * FROM metal_process_batch WHERE batch_id=?",documents.get(0).batchId());
            var selected=new ArrayList<Map<String,Object>>();for(var order:documents)for(var line:order.lines())if(line.inventoryId()!=null)selected.add(inventory("SELECT * FROM metal_inventory WHERE inventory_id=?",line.inventoryId()));
            ProcessingInputService.requireBatchModes(jdbc,target,selected,null);
        }
        for(var o:documents){warehouse.workflowHeader(o);warehouse.lockPostingSources(o);
            imports.inspect(new WarehouseImportService.Input(o.factoryId(),o.warehouseId(),o.type(),o.workOrderId(),o.itemId(),o.processId(),o.lines(),Map.of(),o.batchId()));
            if("PRODUCTION_OUT".equals(o.type())){var work=one("SELECT * FROM metal_work_order WHERE work_order_id=? AND del_flag='0'",o.workOrderId());for(var l:o.lines()){var stock=inventory("SELECT * FROM metal_inventory WHERE inventory_id=? AND del_flag='0' FOR UPDATE",l.inventoryId());if("RAW".equals(stock.get("category")))MaterialOwnership.production(stock,work);}}
        }
        if("PRODUCTION_OUT".equals(documents.get(0).type())){
            var first=documents.get(0);var groups=documents.stream().map(o->new com.ruoyi.metal.catalog.OutboundSubmissionRequests.Group(o.warehouseId(),new com.ruoyi.metal.catalog.OutboundSubmissionRequests.People(o.operatorIds(),o.recipientId(),o.keeperId(),o.reviewerId()),o.lines())).toList();
            var request=new com.ruoyi.metal.catalog.OutboundSubmissionRequests.Request(first.requestId(),first.factoryId(),first.type(),first.orderDate(),first.operatorIds(),first.recipientId(),first.keeperId(),first.reviewerId(),first.workOrderId(),first.itemId(),first.processId(),first.batchId(),first.batchVersion(),groups);drafts.requireValid(drafts.inspectConfirmation(request));
        }
    }

    private long addGroup(long flow,Order order,Long processor){
        int revision=jdbc.queryForObject("SELECT revision FROM metal_warehouse_flow WHERE flow_id=?",Integer.class,flow);
        int sequence=jdbc.queryForObject("SELECT COUNT(*) FROM metal_warehouse_flow_group WHERE flow_id=?",Integer.class,flow)+1;
        long document=warehouse.pendingWorkflowOrder(order,flow);
        long group=insert("INSERT INTO metal_warehouse_flow_group(flow_id,revision,sequence_no,order_id,warehouse_id,type,keeper_id,recipient_id,processor_id,order_json) VALUES(?,?,?,?,?,?,?,?,?,?)",flow,revision,sequence,document,order.warehouseId(),order.type(),order.keeperId(),order.recipientId(),processor,json(order));
        jdbc.update("UPDATE metal_warehouse_order SET workflow_group_id=? WHERE order_id=?",group,document);
        int line=0;for(var l:order.lines()){
            Map<String,Object> catalogSource;
            if(l.inventoryId()!=null)catalogSource=one("SELECT category,reference_id FROM metal_inventory WHERE inventory_id=?",l.inventoryId());
            else if(l.sourceLineId()!=null)catalogSource=one("SELECT category,reference_id FROM metal_warehouse_order_line WHERE line_id=?",l.sourceLineId());
            else {catalogSource=new LinkedHashMap<>();catalogSource.put("category",l.category());catalogSource.put("reference_id",l.referenceId());}
            scope.requireCatalog("RAW".equals(catalogSource.get("category"))?"material":"product",catalogSource.get("reference_id")==null?null:n(catalogSource.get("reference_id")),order.factoryId());
            var snapshot=JSON.parseObject(json(l));Map<String,Object> source;
            if(l.inventoryId()!=null)source=one("SELECT code,name,specification,category FROM metal_inventory WHERE inventory_id=?",l.inventoryId());
            else if(l.sourceLineId()!=null)source=one("SELECT code,name,specification,category FROM metal_warehouse_order_line WHERE line_id=?",l.sourceLineId());
            else if("RAW".equals(l.category()))source=one("SELECT material_code code,material_type name,specification,'RAW' category FROM metal_material WHERE material_id=?",l.referenceId());
            else if(order.batchId()!=null){var product=JSON.parseObject(one("SELECT i.product_snapshot FROM metal_process_batch b JOIN metal_work_order_item i ON i.item_id=b.item_id WHERE b.batch_id=?",order.batchId()).get("product_snapshot").toString());source=new LinkedHashMap<>();source.put("code",product.get("code"));source.put("name",product.get("name"));source.put("specification",product.get("specification"));}
            else source=one("SELECT product_code code,product_name name,specification FROM metal_product WHERE product_id=?",l.referenceId());
            if("RAW_IN".equals(order.type())&&"RAW".equals(l.category())){
                var customer=new RawInventoryCustomers(jdbc).selected(order.factoryId(),l,true);
                snapshot.put("customerSnapshot","CUSTOMER".equals(customer.get("material_ownership"))?RawInventoryCustomers.snapshot(customer):null);
            }
            source.forEach(snapshot::put);insert("INSERT INTO metal_warehouse_flow_line(group_id,sequence_no,line_json,inventory_id,source_line_id,planned_weight,processor_weight) VALUES(?,?,?,?,?,?,?)",group,++line,json(snapshot),l.inventoryId(),l.sourceLineId(),l.netWeight(),processor==null?null:l.netWeight());
        }
        return group;
    }

    private void reserve(long flow){
        var root=root(flow,false);
        for(var group:groups(flow)){var o=order(group);boolean outbound=Set.of("PRODUCTION_OUT","SEMI_OUT","PRODUCT_OUT","EXCHANGE_OUT","OWNERSHIP_OUT","RESIDUAL_STORAGE").contains(o.type());
            for(var row:lines(n(group.get("group_id")))){var l=JSON.parseObject(row.get("line_json").toString(),Line.class);
                if(outbound){var stock=inventory("SELECT * FROM metal_inventory WHERE inventory_id=? AND del_flag='0' FOR UPDATE",l.inventoryId());if(n(stock.get("factory_id"))!=n(root.get("factory_id"))||n(stock.get("warehouse_id"))!=o.warehouseId())throw new ServiceException("库存不属于所选工厂或仓库",403);
                    scope.requireCatalog("RAW".equals(stock.get("category"))?"material":"product",n(stock.get("reference_id")),o.factoryId());MaterialOwnership.inherit(l.materialOwnership(),stock);if("CUSTOMER_RETURN".equals(root.get("kind"))&&(!"CUSTOMER".equals(MaterialOwnership.of(stock))||stock.get("customer_id")==null))throw fail("返还客户必须使用已明确关联客户的原料，不能使用厂料或未核对材料");if(stock.get("origin_batch_id")!=null&&!HandoverSource.outboundReady(jdbc,stock,one("SELECT status,handover_policy_version FROM metal_process_batch WHERE batch_id=?",stock.get("origin_batch_id")),o.type()))throw fail("半成品或成品来源批次尚未结算");
                    if(stock.get("origin_acid_wash_id")!=null&&!"SETTLED".equals(one("SELECT status FROM metal_acid_wash_batch WHERE acid_wash_id=?",stock.get("origin_acid_wash_id")).get("status")))throw fail("酸洗金属料尚未结算");
                    if(!"PRODUCTION_OUT".equals(o.type()))new SemiFlowService(jdbc,scope).guardStock(l.inventoryId());
                    if("PRODUCTION_OUT".equals(o.type())){var target=one("SELECT * FROM metal_process_batch WHERE batch_id=?",o.batchId());ProcessingInputService.require(jdbc,stock,target);ProcessingInputService.capacity(jdbc,stock,target,l.netWeight(),null);}
                    var held=WarehouseReservations.stock(jdbc,l.inventoryId());
                    // This root's earlier rows are already included in the reservation sum.
                    WarehouseFlowRules.available(d(stock.get("net_weight")),n(stock.get("quantity")),d(held.get("weight")),n(held.get("quantity")),l.netWeight(),l.quantity()==null?0:l.quantity(),InventoryMeasurement.weight(stock));
                    insert("INSERT INTO metal_stock_reservation(flow_line_id,flow_id,factory_id,inventory_id,release_id,skip_permit_id,batch_id,item_id,process_id,category,weight,quantity) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",row.get("flow_line_id"),flow,root.get("factory_id"),l.inventoryId(),l.releaseId(),l.skipPermitId(),o.batchId(),o.itemId(),o.processId(),stock.get("category"),l.netWeight(),l.quantity()==null?0:l.quantity());
                    if("PRODUCTION_OUT".equals(o.type())&&"SEMI".equals(stock.get("category"))){
                        var target=one("SELECT * FROM metal_process_batch WHERE batch_id=?",o.batchId());var semi=new SemiFlowService(jdbc,scope);var trace=new SemiSourcePolicyService(jdbc,scope).decide(semi.sourceBatch(stock),target,l.skipPermitId(),l.skipPermitVersion());
                        String profile=json(new TreeMap<>(Map.of("processed",trace.get("processed"),"skipped",trace.get("skipped"))));
                        for(var pendingProfile:jdbc.queryForList("SELECT route_profile FROM metal_stock_reservation WHERE batch_id=? AND status='ACTIVE' AND route_profile IS NOT NULL",o.batchId())){var p=JSON.parseObject(pendingProfile.get("route_profile").toString());RouteExecutionRules.sameProfile(json(new TreeMap<>(Map.of("processed",p.get("processed"),"skipped",p.get("skipped")))),profile);}
                        jdbc.update("UPDATE metal_stock_reservation SET route_profile=? WHERE flow_line_id=?",json(trace),row.get("flow_line_id"));
                    }
                } else if("PRODUCT_RETURN".equals(o.type())){
                    var source=one("SELECT l.*,o.status source_status,o.type source_type FROM metal_warehouse_order_line l JOIN metal_warehouse_order o ON o.order_id=l.order_id WHERE l.line_id=? FOR UPDATE",l.sourceLineId());
                    scope.requireCatalog("product",n(source.get("reference_id")),o.factoryId());
                    if(!"PRODUCT_OUT".equals(source.get("source_type"))||!"POSTED".equals(source.get("source_status")))throw fail("成品退回须关联有效成品出库明细");
                    var returned=one("SELECT COALESCE(SUM(l.net_weight),0) weight,COALESCE(SUM(l.quantity),0) quantity FROM metal_warehouse_order_line l JOIN metal_warehouse_order o ON o.order_id=l.order_id WHERE l.source_line_id=? AND o.status='POSTED'",l.sourceLineId());var held=one("SELECT COALESCE(SUM(weight),0) weight,COALESCE(SUM(quantity),0) quantity FROM metal_stock_reservation WHERE source_line_id=? AND status='ACTIVE'",l.sourceLineId());
                    WarehouseFlowRules.available(d(source.get("net_weight")).subtract(d(returned.get("weight"))),n(source.get("quantity"))-n(returned.get("quantity")),d(held.get("weight")),n(held.get("quantity")),l.netWeight(),l.quantity()==null?0:l.quantity(),false);
                    insert("INSERT INTO metal_stock_reservation(flow_line_id,flow_id,factory_id,source_line_id,category,weight,quantity) VALUES(?,?,?,?,?,?,?)",row.get("flow_line_id"),flow,root.get("factory_id"),l.sourceLineId(),"FINISHED",l.netWeight(),l.quantity());
                }
            }
        }
        for(var group:groups(flow)){var o=order(group);if(o.itemId()!=null)new SemiFlowService(jdbc,scope).checkCapacity(o.itemId());}
    }

    public Map<String,Object> confirm(long flow,long group,Confirm input){
        var root=root(flow,true);boolean recipient="OUTBOUND".equals(root.get("kind"));permit("metal:warehouse-order:"+(recipient?"receive":"accept"));String fingerprint=hash(List.of(flow,group,input));if(replay(input.requestId(),"CONFIRM",fingerprint,flow)!=null)return detail(flow);
        pending(root);version(root,input.version());if("RETURNED".equals(root.get("status"))||"PROCESSORS".equals(root.get("stage")))throw fail("请先完成修订及加工人员交接");
        var g=one("SELECT * FROM metal_warehouse_flow_group WHERE group_id=? AND flow_id=? AND status<>'SUPERSEDED' FOR UPDATE",group,flow);
        long expected=n(g.get(recipient?"recipient_id":"keeper_id"));if(expected!=SecurityUtils.getUserId())throw new ServiceException("只能由本单指定人员使用本人账号确认，管理员不能代签",403);
        if("HANDOVER".equals(root.get("kind")))permit("metal:process-batch:settle");if(!"WAITING".equals(g.get("status")))throw fail("本仓已确认，请刷新查看当前进度");
        var rows=lines(group);if(input.lines().size()!=rows.size())throw fail("须逐条填写本仓全部实收重量");var measured=new HashMap<Long,Measured>();for(var l:input.lines())if(measured.put(l.lineId(),l)!=null)throw fail("实收明细不能重复");
        for(var row:rows){var m=measured.get(n(row.get("flow_line_id")));if(m==null)throw fail("称重明细不属于当前仓库");var declared=JSON.parseObject(row.get("line_json").toString(),Line.class);BigDecimal actual=WarehouseFlowRules.weight(m.netWeight());WarehouseFlowRules.difference(d(row.get("planned_weight")),actual,m.differenceReason());
            if(Set.of("OFFSET","STORAGE").contains(root.get("kind"))&&actual.compareTo(d(row.get("planned_weight")))!=0)throw fail("代存或归属转移须与已核对余料重量一致，有差异请退回核对");
            if(root.get("closeout_id")!=null&&actual.compareTo(d(row.get("planned_weight")))>0)throw fail("客户收尾不能超过本工单该行实际剩余余料");
            if(declared.quantity()!=null&&declared.quantity()>0&&m.quantity()==null)throw fail("请核对本行实物件数，件数不符须退回修订");
            if(m.quantity()!=null&&m.quantity()!=(declared.quantity()==null?0:declared.quantity()))throw fail("实物件数不符，请退回库管修订，不能直接改变件数扣库");
            jdbc.update("UPDATE metal_warehouse_flow_line SET actual_weight=?,difference_reason=? WHERE flow_line_id=?",actual,m.differenceReason(),row.get("flow_line_id"));
        }
        jdbc.update("UPDATE metal_stock_reservation SET status='VALIDATING' WHERE flow_id=? AND status='ACTIVE'",flow);
        var actualOrders=actualOrders(flow);if(!"HANDOVER".equals(root.get("kind"))&&root.get("closeout_id")==null)validateDocuments(actualOrders);
        jdbc.update("UPDATE metal_stock_reservation SET status='RELEASED' WHERE flow_id=? AND status='VALIDATING'",flow);reserveActual(flow);
        jdbc.update("UPDATE metal_warehouse_flow_group SET status='CONFIRMED',confirmed_by=?,confirmed_at=NOW() WHERE group_id=?",SecurityUtils.getUserId(),group);
        jdbc.update("UPDATE metal_warehouse_order SET confirmed_by=?,confirmed_at=NOW(),version=version+1 WHERE order_id=?",SecurityUtils.getUserId(),g.get("order_id"));
        jdbc.update("UPDATE metal_warehouse_flow SET version=version+1,status='COLLECTING' WHERE flow_id=?",flow);event(flow,"CONFIRM",input);
        if(groups(flow).stream().allMatch(x->"CONFIRMED".equals(x.get("status"))))post(flow);
        remember(flow,input.requestId(),"CONFIRM",fingerprint);return detail(flow);
    }

    private List<Order> actualOrders(long flow){var result=new ArrayList<Order>();for(var group:groups(flow)){var o=order(group);var actual=new ArrayList<Line>();for(var row:lines(n(group.get("group_id")))){var l=JSON.parseObject(row.get("line_json").toString(),Line.class);actual.add(measured(l,row.get("actual_weight")==null?d(row.get("planned_weight")):d(row.get("actual_weight"))));}Integer version=o.batchId()==null?null:jdbc.queryForObject("SELECT version FROM metal_process_batch WHERE batch_id=?",Integer.class,o.batchId());result.add(copy(o,o.requestId(),o.batchId(),version,actual));}return result;}
    private void reserveActual(long flow){
        // Reuse reservation row identities, while validating the entire root including unconfirmed warehouses.
        jdbc.update("UPDATE metal_stock_reservation r JOIN metal_warehouse_flow_line l ON l.flow_line_id=r.flow_line_id JOIN metal_warehouse_flow_group g ON g.group_id=l.group_id SET r.weight=COALESCE(l.actual_weight,l.planned_weight),r.status='ACTIVE' WHERE r.flow_id=? AND r.status='RELEASED' AND g.status<>'SUPERSEDED'",flow);
        for(var row:jdbc.queryForList("SELECT DISTINCT inventory_id FROM metal_stock_reservation WHERE flow_id=? AND inventory_id IS NOT NULL AND status='ACTIVE'",flow)){long stock=n(row.get("inventory_id"));var i=inventory("SELECT * FROM metal_inventory WHERE inventory_id=? FOR UPDATE",stock);var held=WarehouseReservations.stock(jdbc,stock);WarehouseFlowRules.available(d(i.get("net_weight")),n(i.get("quantity")),d(held.get("weight")),n(held.get("quantity")),BigDecimal.ZERO,0,InventoryMeasurement.weight(i));}
        for(var item:jdbc.queryForList("SELECT DISTINCT item_id FROM metal_stock_reservation WHERE flow_id=? AND item_id IS NOT NULL AND status='ACTIVE'",flow))RawPlanService.requireCapacity(jdbc,n(item.get("item_id")),BigDecimal.ZERO,null);
        for(var row:jdbc.queryForList("SELECT DISTINCT source_line_id FROM metal_stock_reservation WHERE flow_id=? AND source_line_id IS NOT NULL AND status='ACTIVE'",flow)){
            var source=one("SELECT net_weight,quantity FROM metal_warehouse_order_line WHERE line_id=? FOR UPDATE",row.get("source_line_id"));
            var returned=one("SELECT COALESCE(SUM(l.net_weight),0) weight,COALESCE(SUM(l.quantity),0) quantity FROM metal_warehouse_order_line l JOIN metal_warehouse_order o ON o.order_id=l.order_id WHERE l.source_line_id=? AND o.status='POSTED'",row.get("source_line_id"));
            var held=one("SELECT COALESCE(SUM(weight),0) weight,COALESCE(SUM(quantity),0) quantity FROM metal_stock_reservation WHERE source_line_id=? AND status='ACTIVE'",row.get("source_line_id"));
            WarehouseFlowRules.available(d(source.get("net_weight")).subtract(d(returned.get("weight"))),n(source.get("quantity"))-n(returned.get("quantity")),d(held.get("weight")),n(held.get("quantity")),BigDecimal.ZERO,0,false);
        }
    }
    private void post(long flow){
        var root=root(flow,false);jdbc.update("UPDATE metal_stock_reservation SET status='CONSUMED' WHERE flow_id=? AND status='ACTIVE'",flow);
        var groups=groups(flow);groups.sort(Comparator.comparingInt(g->Set.of("SEMI_IN","FINISHED_IN","RAW_PROCESS_IN").contains(g.get("type"))?0:"PROCESS_RAW_RETURN".equals(g.get("type"))?2:1));
        var inputs=actualOrders(flow);for(var g:groups){var o=inputs.stream().filter(x->x.requestId().equals(order(g).requestId())).findFirst().orElseThrow();if(o.batchId()!=null)o=copy(o,o.requestId(),o.batchId(),jdbc.queryForObject("SELECT version FROM metal_process_batch WHERE batch_id=?",Integer.class,o.batchId()),o.lines());if(Set.of("OWNERSHIP_IN","RESIDUAL_STORAGE").contains(o.type()))warehouse.postResidualTransfer(o,n(g.get("order_id")));else warehouse.postWorkflowDocument(o,n(g.get("order_id")));}
        if("OUTBOUND".equals(root.get("kind"))&&root.get("batch_id")!=null)reopenSupplementRecipients(n(root.get("batch_id")),flow);
        if("HANDOVER".equals(root.get("kind")))warehouse.settleWorkflow(n(root.get("batch_id")),flow);
        if(root.get("closeout_id")!=null)finishCloseout(root);
        jdbc.update("UPDATE metal_warehouse_flow SET status='POSTED',stage='DONE',posted_time=NOW(),version=version+1 WHERE flow_id=?",flow);event(flow,"POST",Map.of("allWarehousesConfirmed",true));
    }

    private void reopenSupplementRecipients(long batch,long outboundFlow){
        if(PartialHandoverLedger.partial(one("SELECT handover_policy_version FROM metal_process_batch WHERE batch_id=?",batch)))return;
        for(var handover:jdbc.queryForList("SELECT flow_id FROM metal_warehouse_flow WHERE batch_id=? AND kind='HANDOVER' AND status NOT IN ('VOID','CANCELLED','POSTED')",batch)){
            long flow=n(handover.get("flow_id"));
            // Additional physical material invalidates the recipient's earlier mass declaration, not other batches.
            jdbc.update("INSERT IGNORE INTO metal_warehouse_flow_processor(flow_id,actor_id,submitted) SELECT DISTINCT ?,recipient_id,FALSE FROM metal_warehouse_order WHERE warehouse_flow_id=? AND type='PRODUCTION_OUT' AND status='POSTED' AND recipient_id=confirmed_by",flow,outboundFlow);
            jdbc.update("UPDATE metal_warehouse_flow_processor p JOIN metal_warehouse_order o ON o.recipient_id=p.actor_id AND o.warehouse_flow_id=? AND o.type='PRODUCTION_OUT' AND o.status='POSTED' SET p.submitted=FALSE,p.submitted_time=NULL WHERE p.flow_id=?",outboundFlow,flow);
            jdbc.update("UPDATE metal_warehouse_flow SET stage='PROCESSORS',status='COLLECTING',version=version+1 WHERE flow_id=?",flow);
            event(flow,"REVISE",Map.of("reason","补料已实领，请相关加工人员重新核对并提交全部交接","outboundFlowId",outboundFlow));
        }
    }

    public Map<String,Object> reject(long flow,Change input){
        var r=root(flow,true);String fingerprint=hash(List.of(flow,input));if(replay(input.requestId(),"REJECT",fingerprint,flow)!=null)return detail(flow);pending(r);version(r,input.version());long actor=SecurityUtils.getUserId();
        boolean recipient="RECIPIENT".equals(r.get("stage"));permit("metal:warehouse-order:"+(recipient?"receive":"accept"));if(groups(flow).stream().noneMatch(g->n(g.get(recipient?"recipient_id":"keeper_id"))==actor))throw new ServiceException("只能由指定领用人或仓管员退回",403);
        if(input.reason()==null||input.reason().isBlank())throw fail("请填写退回原因");
        jdbc.update("UPDATE metal_stock_reservation SET status='RELEASED' WHERE flow_id=? AND status='ACTIVE'",flow);
        jdbc.update("UPDATE metal_warehouse_order SET status='RETURNED',version=version+1 WHERE warehouse_flow_id=? AND status IN ('WAITING_OUT','WAITING_IN')",flow);
        jdbc.update("UPDATE metal_warehouse_flow_group SET status='WAITING',confirmed_by=NULL,confirmed_at=NULL WHERE flow_id=? AND status<>'SUPERSEDED'",flow);
        jdbc.update("UPDATE metal_warehouse_flow SET status='RETURNED',version=version+1,reason=? WHERE flow_id=?",input.reason(),flow);event(flow,"REJECT",input);remember(flow,input.requestId(),"REJECT",fingerprint);return detail(flow);
    }

    public Map<String,Object> cancel(long flow,Change input){
        var r=root(flow,true);permit("metal:warehouse-order:"+("HANDOVER".equals(r.get("kind"))||r.get("closeout_id")!=null?"handover":"add"));String fingerprint=hash(List.of(flow,input));if(replay(input.requestId(),"CANCEL",fingerprint,flow)!=null)return detail(flow);pending(r);version(r,input.version());
        if(n(r.get("request_actor"))!=SecurityUtils.getUserId())throw new ServiceException("仅申请创建人可以取消",403);if(input.reason()==null||input.reason().isBlank())throw fail("请填写取消原因");
        if(PartialHandoverLedger.partial(r)&&"HANDOVER".equals(r.get("kind"))) {
            new PartialHandoverLedger(jdbc).release(flow);
            jdbc.update("UPDATE metal_process_batch SET version=version+1 WHERE batch_id=?",r.get("batch_id"));
        }
        jdbc.update("UPDATE metal_stock_reservation SET status='RELEASED' WHERE flow_id=? AND status IN ('ACTIVE','VALIDATING')",flow);
        jdbc.update("UPDATE metal_warehouse_order SET status='CANCELLED',version=version+1 WHERE warehouse_flow_id=? AND status IN ('WAITING_OUT','WAITING_IN','RETURNED')",flow);
        jdbc.update("UPDATE metal_warehouse_flow SET status='CANCELLED',version=version+1,reason=? WHERE flow_id=?",input.reason(),flow);
        if(r.get("batch_id")!=null&&jdbc.queryForObject("SELECT COUNT(*) FROM metal_warehouse_flow WHERE batch_id=? AND created_batch=TRUE",Long.class,r.get("batch_id"))>0&&jdbc.queryForObject("SELECT COUNT(*) FROM metal_warehouse_order WHERE batch_id=? AND status='POSTED'",Long.class,r.get("batch_id"))==0&&jdbc.queryForObject("SELECT COUNT(*) FROM metal_warehouse_flow WHERE batch_id=? AND flow_id<>? AND status IN ('WAITING','COLLECTING','RETURNED')",Long.class,r.get("batch_id"),flow)==0)jdbc.update("UPDATE metal_process_batch SET status='VOID',version=version+1 WHERE batch_id=? AND status='OPEN'",r.get("batch_id"));
        if(r.get("closeout_id")!=null)jdbc.update("UPDATE metal_customer_residual_closeout SET status=CASE WHEN EXISTS(SELECT 1 FROM metal_warehouse_flow f WHERE f.closeout_id=? AND f.status='POSTED') THEN 'PARTIAL' ELSE 'VOID' END,version=version+1 WHERE closeout_id=?",r.get("closeout_id"),r.get("closeout_id"));
        event(flow,"CANCEL",input);remember(flow,input.requestId(),"CANCEL",fingerprint);return detail(flow);
    }

    public Map<String,Object> revise(long flow,Change input){
        permit("metal:warehouse-order:add");var r=root(flow,true);String fingerprint=hash(List.of(flow,input));if(replay(input.requestId(),"REVISE",fingerprint,flow)!=null)return detail(flow);pending(r);version(r,input.version());
        if("HANDOVER".equals(r.get("kind")))throw fail("加工交接请由各加工人员修订本人的清单");
        if(r.get("closeout_id")!=null)throw fail("客户收尾请取消本次确认后，从收尾入口重新核对剩余量");
        long actor=SecurityUtils.getUserId();if(n(r.get("request_actor"))!=actor&&groups(flow).stream().noneMatch(g->n(g.get("keeper_id"))==actor))throw new ServiceException("仅创建人或本单仓管员可以修订",403);
        if(input.documents()==null||input.documents().isEmpty()||input.documents().stream().mapToInt(o->o.lines().size()).sum()>1000)throw fail("请填写1至1000条修订明细");
        String expected=groups(flow).get(0).get("type").toString();var documents=new ArrayList<Order>();int i=0;
        for(var original:input.documents()){
            if(!expected.equals(original.type())||n(r.get("factory_id"))!=original.factoryId()||!Objects.equals(id(r.get("work_order_id")),original.workOrderId())||!Objects.equals(id(r.get("batch_id")),original.batchId()))throw fail("修订不能更换业务类型、工厂、工单或批次");
            Integer batchVersion=original.batchId()==null?null:jdbc.queryForObject("SELECT version FROM metal_process_batch WHERE batch_id=?",Integer.class,original.batchId());documents.add(copy(WarehousePrecision.order(original),child(input.requestId(),i++),original.batchId(),batchVersion,WarehousePrecision.lines(original.lines())));
        }
        jdbc.update("UPDATE metal_stock_reservation SET status='RELEASED' WHERE flow_id=? AND status='ACTIVE'",flow);validateDocuments(documents);
        jdbc.update("UPDATE metal_warehouse_order SET status='CANCELLED',version=version+1 WHERE warehouse_flow_id=? AND status IN ('WAITING_OUT','WAITING_IN','RETURNED')",flow);
        jdbc.update("UPDATE metal_warehouse_flow_group SET status='SUPERSEDED' WHERE flow_id=? AND status<>'SUPERSEDED'",flow);
        jdbc.update("UPDATE metal_warehouse_flow SET revision=revision+1,version=version+1,status='WAITING',reason=?,plan_json=? WHERE flow_id=?",input.reason(),json(input.documents()),flow);
        for(var o:documents)addGroup(flow,o,null);reserve(flow);event(flow,"REVISE",input);remember(flow,input.requestId(),"REVISE",fingerprint);return detail(flow);
    }

    public Map<String,Object> reverse(long flow,Change input){
        permit("metal:warehouse-order:reverse");var r=root(flow,true);String fingerprint=hash(List.of(flow,input));if(replay(input.requestId(),"REVERSE",fingerprint,flow)!=null)return detail(flow);version(r,input.version());if(!"POSTED".equals(r.get("status")))throw fail("只有已生效申请可以冲销");warehouse.reverseWorkflow(flow,input.reason());jdbc.update("UPDATE metal_warehouse_flow SET status='VOID',reason=?,version=version+1 WHERE flow_id=?",input.reason(),flow);event(flow,"REVERSE",input);remember(flow,input.requestId(),"REVERSE",fingerprint);return detail(flow);
    }
    private void contextParticipant(long work,Long batch){
        if(scope.isAdmin()||SecurityUtils.hasPermi("metal:work-order:query")||SecurityUtils.hasPermi("metal:warehouse-order:query")||SecurityUtils.hasPermi("metal:warehouse-order:add"))return;
        long actor=SecurityUtils.getUserId();
        var issued=jdbc.queryForList("SELECT order_id FROM metal_warehouse_order WHERE work_order_id=? AND status='POSTED' AND type='PRODUCTION_OUT' AND recipient_id=? AND confirmed_by=?"+(batch==null?"":" AND batch_id=?")+" LIMIT 1",batch==null?new Object[]{work,actor,actor}:new Object[]{work,actor,actor,batch});
        var assigned=jdbc.queryForList("SELECT g.group_id FROM metal_warehouse_flow_group g JOIN metal_warehouse_flow f ON f.flow_id=g.flow_id WHERE f.work_order_id=? AND g.status<>'SUPERSEDED' AND g.keeper_id=?"+(batch==null?"":" AND f.batch_id=?")+" LIMIT 1",batch==null?new Object[]{work,actor}:new Object[]{work,actor,batch});
        if(issued.isEmpty()&&assigned.isEmpty())throw new ServiceException("只能查看本人实际领料或被指定验收的生产上下文",403);
    }
    private List<Map<String,Object>> residualLots(long work){
        var lots=jdbc.queryForList("SELECT r.*,i.factory_id,i.warehouse_id,i.code,i.name,i.specification,i.net_weight,i.quantity,i.category,i.measurement_mode,i.origin_batch_id,i.batch_no,h.name warehouse_name,o.order_no source_order_no FROM metal_work_order_residual r JOIN metal_inventory i ON i.inventory_id=r.inventory_id JOIN metal_warehouse h ON h.warehouse_id=i.warehouse_id JOIN metal_warehouse_order_line l ON l.line_id=r.source_line_id JOIN metal_warehouse_order o ON o.order_id=l.order_id WHERE r.work_order_id=? AND o.status='POSTED' AND r.remaining_weight>0 ORDER BY r.residual_id",work).stream().map(row->(Map<String,Object>)new LinkedHashMap<String,Object>(row)).toList();
        var stocks=residualStocks(lots,false);var byId=new HashMap<Long,Map<String,Object>>();for(var stock:stocks)byId.put(n(stock.get("inventory_id")),stock);
        for(var lot:lots){var stock=byId.get(n(lot.get("inventory_id")));if(stock!=null)for(String field:List.of("effective_measurement_mode","measurement_policy_version","available_weight","available_quantity"))lot.put(field,stock.get(field));}
        return lots;
    }
    private List<Map<String,Object>> residualStocks(List<Map<String,Object>> lots,boolean lock){
        var ids=new TreeSet<Long>();for(var lot:lots)ids.add(n(lot.get("inventory_id")));if(ids.isEmpty())return List.of();
        String marks=String.join(",",Collections.nCopies(ids.size(),"?"));
        return InventoryMeasurement.enrich(jdbc,jdbc.queryForList("SELECT i.*,i.net_weight-COALESCE(r.weight,0) available_weight,i.quantity-COALESCE(r.quantity,0) available_quantity FROM metal_inventory i LEFT JOIN (SELECT inventory_id,SUM(weight) weight,SUM(quantity) quantity FROM metal_stock_reservation WHERE status='ACTIVE' GROUP BY inventory_id) r ON r.inventory_id=i.inventory_id WHERE i.inventory_id IN ("+marks+") ORDER BY i.inventory_id"+(lock?" FOR UPDATE":""),ids.toArray()));
    }

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> closeoutContext(long work){
        if(!SecurityUtils.hasPermi("metal:work-order:query"))readPermit();var w=one("SELECT work_order_id,order_no,customer_id,customer,customer_snapshot,factory_id,status,version FROM metal_work_order WHERE work_order_id=? AND del_flag='0'",work);scope.check(n(w.get("factory_id")));contextParticipant(work,null);var out=warehouse.clean(w);out.remove("customerSnapshot");var lots=residualLots(work);out.put("lots",warehouse.cleaned(lots));out.put("customerRemainingWeight",lots.stream().filter(l->"CUSTOMER".equals(l.get("material_ownership"))).map(l->d(l.get("remaining_weight"))).reduce(BigDecimal.ZERO,BigDecimal::add));out.put("factoryRemainingWeight",lots.stream().filter(l->"FACTORY".equals(l.get("material_ownership"))).map(l->d(l.get("remaining_weight"))).reduce(BigDecimal.ZERO,BigDecimal::add));
        var supplement=jdbc.queryForList("SELECT o.order_id,o.order_no,o.order_date,o.type,o.status,l.code,l.net_weight keeper_weight,COALESCE(l.processor_net_weight,l.net_weight) processing_weight,l.production_supply_kind,l.line_id,l.material_ownership FROM metal_warehouse_order o JOIN metal_warehouse_order_line l ON l.order_id=o.order_id WHERE o.work_order_id=? AND l.category='RAW' AND l.material_ownership='FACTORY' AND o.type IN ('PRODUCTION_OUT','RAW_RETURN','PROCESS_RAW_RETURN') ORDER BY o.order_id,l.sequence_no",work);out.put("factorySupplement",warehouse.cleaned(supplement));BigDecimal issued=BigDecimal.ZERO,returned=BigDecimal.ZERO;for(var l:supplement)if("POSTED".equals(l.get("status"))){if("PRODUCTION_OUT".equals(l.get("type")))issued=issued.add(d(l.get("processing_weight")));else returned=returned.add(d(l.get("processing_weight")));}out.put("factoryIssuedWeight",issued);out.put("factoryReturnedWeight",returned);out.put("factoryNetInputWeight",issued.subtract(returned));
        var roots=jdbc.queryForList("SELECT * FROM metal_customer_residual_closeout WHERE work_order_id=? AND status<>'VOID'",work);if(!roots.isEmpty()){var closeout=roots.get(0);out.put("closeout",warehouse.clean(closeout));out.put("version",closeout.get("version"));out.put("flows",warehouse.cleaned(jdbc.queryForList("SELECT flow_id id,status,stage,version FROM metal_warehouse_flow WHERE closeout_id=? ORDER BY flow_id",closeout.get("closeout_id"))));}
        out.put("offsetOrders",warehouse.cleaned(jdbc.queryForList("SELECT work_order_id id,order_no,order_date,status FROM metal_work_order WHERE factory_id=? AND customer_id=? AND processing_type='SHORTAGE' AND work_order_id<>? AND del_flag='0' AND (order_date<? OR order_date=? AND work_order_id<?) ORDER BY order_date,work_order_id",w.get("factory_id"),w.get("customer_id"),work,one("SELECT order_date FROM metal_work_order WHERE work_order_id=?",work).get("order_date"),one("SELECT order_date FROM metal_work_order WHERE work_order_id=?",work).get("order_date"),work)));return out;
    }
    public Map<String,Object> closeout(long work,Closeout input){
        permit("metal:warehouse-order:handover");var w=one("SELECT * FROM metal_work_order WHERE work_order_id=? AND del_flag='0'",work);scope.lockFactories(List.of(n(w.get("factory_id"))));w=one("SELECT * FROM metal_work_order WHERE work_order_id=? AND del_flag='0' FOR UPDATE",work);String fingerprint=hash(List.of(work,input));Long previous=replay(input.requestId(),"CLOSEOUT",fingerprint,0);if(previous!=null)return detail(previous);
        if(!Set.of("COMPLETED","CLOSED_PARTIAL").contains(w.get("status")))throw fail("请先完成生产或受控部分关闭，再办理客户余料收尾");if(w.get("customer_id")==null)throw fail("历史客户尚未关联档案，请人工核对，不能按名称推断");
        WarehouseReservations.work(jdbc,work);if(!Set.of("RETURN","STORAGE","OFFSET").contains(input.mode()))throw fail("请选择返还、代存或抵旧欠料其中一种方向");
        long actor=SecurityUtils.getUserId();if(jdbc.queryForList("SELECT order_id FROM metal_warehouse_order WHERE work_order_id=? AND type='PRODUCTION_OUT' AND status='POSTED' AND recipient_id=? AND confirmed_by=? LIMIT 1",work,actor,actor).isEmpty())throw new ServiceException("客户余料须由本单实际加工人员发起",403);
        var old=jdbc.queryForList("SELECT * FROM metal_customer_residual_closeout WHERE work_order_id=? AND status<>'VOID' FOR UPDATE",work);long closeout;int expected=old.isEmpty()?(int)n(w.get("version")):(int)n(old.get(0).get("version"));if(input.version()!=expected)throw new ServiceException("余料收尾资料已变化，请重新加载",409);
        if(!old.isEmpty()&&(!input.mode().equals(old.get(0).get("mode"))||!Objects.equals(input.offsetWorkOrderId(),id(old.get(0).get("offset_work_order_id")))||!"PARTIAL".equals(old.get(0).get("status"))))throw fail("本工单已选定收尾方向或已全部完成，不能更换方向或抵料工单");
        if("OFFSET".equals(input.mode())){if(input.offsetWorkOrderId()==null)throw fail("请选择同厂同客户较早的欠料定制工单");var target=one("SELECT * FROM metal_work_order WHERE work_order_id=? AND del_flag='0' FOR UPDATE",input.offsetWorkOrderId());if(n(target.get("factory_id"))!=n(w.get("factory_id"))||!Objects.equals(target.get("customer_id"),w.get("customer_id"))||!"SHORTAGE".equals(target.get("processing_type"))||target.get("order_date").toString().compareTo(w.get("order_date").toString())>0||target.get("order_date").equals(w.get("order_date"))&&n(target.get("work_order_id"))>=work)throw fail("抵料须关联同厂同客户较早的欠料定制工单，不登记钱款或欠料结清");}else if(input.offsetWorkOrderId()!=null)throw fail("返还和代存不能关联抵料工单");
        Object customerId=w.get("customer_id");var lots=residualLots(work).stream().filter(l->"CUSTOMER".equals(l.get("material_ownership"))&&Objects.equals(l.get("customer_id"),customerId)&&"OPEN".equals(l.get("status"))).toList();BigDecimal available=lots.stream().map(l->d(l.get("remaining_weight"))).reduce(BigDecimal.ZERO,BigDecimal::add),weight=WarehouseFlowRules.weight(input.weight());if(weight.compareTo(available)>0||!"OFFSET".equals(input.mode())&&weight.compareTo(available)!=0)throw fail("返还、代存须整笔确认实际剩余客户料；抵料不能超过实际剩余量");
        if(old.isEmpty())closeout=insert("INSERT INTO metal_customer_residual_closeout(request_id,request_hash,actor_id,factory_id,work_order_id,customer_id,mode,weight,offset_work_order_id,reason) VALUES(?,?,?,?,?,?,?,?,?,?)",input.requestId(),fingerprint,actor,w.get("factory_id"),work,w.get("customer_id"),input.mode(),weight,input.offsetWorkOrderId(),input.reason());else{closeout=n(old.get(0).get("closeout_id"));jdbc.update("UPDATE metal_customer_residual_closeout SET status='WAITING',version=version+1 WHERE closeout_id=?",closeout);}
        String kind="RETURN".equals(input.mode())?"CUSTOMER_RETURN":input.mode();long flow=insert("INSERT INTO metal_warehouse_flow(request_id,request_hash,request_actor,factory_id,kind,stage,work_order_id,closeout_id,plan_json,reason) VALUES(?,?,?,?,?,'KEEPER',?,?,?,?)",input.requestId(),fingerprint,actor,w.get("factory_id"),kind,work,closeout,json(input),input.reason());var byWarehouse=new LinkedHashMap<Long,List<Line>>();BigDecimal remaining=weight;
        var selectedWeights=new LinkedHashMap<Long,BigDecimal>();
        var selectedLots=new ArrayList<Map<String,Object>>();
        for(var lot:lots){if(remaining.signum()==0)break;BigDecimal take=remaining.min(d(lot.get("remaining_weight")));remaining=remaining.subtract(take);selectedWeights.merge(n(lot.get("inventory_id")),take,BigDecimal::add);var selected=new LinkedHashMap<String,Object>(lot);selected.put("selected_weight",take);selectedLots.add(selected);}
        var stocks=residualStocks(selectedLots,true);
        for(var stock:stocks)if(n(stock.get("factory_id"))!=n(w.get("factory_id"))||!"CUSTOMER".equals(MaterialOwnership.of(stock))||!Objects.equals(stock.get("customer_id"),w.get("customer_id")))throw fail("收尾库存须属于本厂当前客户，不能使用厂料或他人库存");
        var quantities=RawCloseoutPieces.allocate(stocks,selectedWeights,input.pieceQuantities());
        for(var lot:selectedLots)jdbc.update("INSERT INTO metal_customer_residual_closeout_line(closeout_id,residual_id,weight) VALUES(?,?,?) ON DUPLICATE KEY UPDATE weight=weight+?",closeout,lot.get("residual_id"),lot.get("selected_weight"),lot.get("selected_weight"));
        for(var stock:stocks){long inventory=n(stock.get("inventory_id"));byWarehouse.computeIfAbsent(n(stock.get("warehouse_id")),x->new ArrayList<>()).add(new Line(inventory,null,"RAW",null,null,selectedWeights.get(inventory),quantities.get(inventory),null,null,null,null,null,null,null,null,null,null,null,null,null,null));}
        int sequence=0;for(var entry:byWarehouse.entrySet()){String type=switch(input.mode()){case "RETURN"->"EXCHANGE_OUT";case "STORAGE"->"RESIDUAL_STORAGE";default->"OWNERSHIP_OUT";};var o=new Order(child(input.requestId(),sequence++),n(w.get("factory_id")),entry.getKey(),type,java.time.LocalDate.now(),null,List.of(actor),input.keeperId(),null,null,null,work,null,null,false,null,input.reason(),entry.getValue(),null,null);addGroup(flow,o,actor);if("OFFSET".equals(input.mode()))addGroup(flow,new Order(child(input.requestId(),sequence++),o.factoryId(),o.warehouseId(),"OWNERSHIP_IN",o.orderDate(),null,o.operatorIds(),o.keeperId(),null,null,null,work,null,null,false,null,o.reason(),o.lines(),null,null),actor);}
        reserve(flow);event(flow,"CLOSEOUT",input);remember(flow,input.requestId(),"CLOSEOUT",fingerprint);return detail(flow);
    }
    private void finishCloseout(Map<String,Object> root){
        long closeout=n(root.get("closeout_id"));var c=one("SELECT * FROM metal_customer_residual_closeout WHERE closeout_id=? FOR UPDATE",closeout);String status=switch(c.get("mode").toString()){case "STORAGE"->"STORED";case "OFFSET"->"OFFSET";default->"RETURNED";};
        jdbc.update("UPDATE metal_work_order_residual r JOIN metal_customer_residual_closeout_line l ON l.residual_id=r.residual_id SET r.status=?,r.version=r.version+1 WHERE l.closeout_id=? AND (?='STORED' OR r.remaining_weight=0)",status,closeout,status);
        BigDecimal remaining=residualLots(n(c.get("work_order_id"))).stream().filter(l->"CUSTOMER".equals(l.get("material_ownership"))&&"OPEN".equals(l.get("status"))).map(l->d(l.get("remaining_weight"))).reduce(BigDecimal.ZERO,BigDecimal::add);jdbc.update("UPDATE metal_customer_residual_closeout SET status=?,version=version+1 WHERE closeout_id=?",remaining.signum()==0?"DONE":"PARTIAL",closeout);
    }
    public Map<String,Object> reverseCloseout(long work,Change input){
        permit("metal:warehouse-order:reverse");var w=one("SELECT factory_id FROM metal_work_order WHERE work_order_id=? AND del_flag='0'",work);scope.lockFactories(List.of(n(w.get("factory_id"))));String fingerprint=hash(List.of(work,input));Long previous=replay(input.requestId(),"CLOSEOUT_REVERSE",fingerprint,0);if(previous!=null)return closeoutContext(work);
        var c=one("SELECT * FROM metal_customer_residual_closeout WHERE work_order_id=? AND status<>'VOID' FOR UPDATE",work);if(input.version()!=n(c.get("version")))throw new ServiceException("收尾版本已变化，请重新加载",409);if(input.reason()==null||input.reason().isBlank())throw fail("请填写撤回原因");if(!Set.of("DONE","PARTIAL").contains(c.get("status")))throw fail("请先取消尚未确认的收尾申请");
        long closeout=n(c.get("closeout_id"));if(!jdbc.queryForList("SELECT flow_id FROM metal_warehouse_flow WHERE closeout_id=? AND status IN ('WAITING','COLLECTING','RETURNED')",closeout).isEmpty())throw fail("请先取消未完成的收尾确认");warehouse.reverseCloseoutDocuments(closeout,input.reason());jdbc.update("UPDATE metal_customer_residual_closeout SET status='VOID',version=version+1 WHERE closeout_id=?",closeout);
        long replayFlow=0;for(var flow:jdbc.queryForList("SELECT flow_id FROM metal_warehouse_flow WHERE closeout_id=? AND status='POSTED'",closeout)){long id=n(flow.get("flow_id"));jdbc.update("UPDATE metal_warehouse_flow SET status='VOID',reason=?,version=version+1 WHERE flow_id=?",input.reason(),id);event(id,"CLOSEOUT_REVERSE",input);replayFlow=id;}
        if(replayFlow!=0)remember(replayFlow,input.requestId(),"CLOSEOUT_REVERSE",fingerprint);
        return closeoutContext(work);
    }
    private List<Map<String,Object>> processingSources(long batch){return InventoryMeasurement.enrich(jdbc,jdbc.queryForList("SELECT l.*,o.order_no,o.warehouse_id,o.warehouse_name,o.recipient_id,o.confirmed_by,o.confirmed_at,COALESCE(i.metal_type_id_snapshot,m.metal_type_id,p.metal_type_id) input_metal_type_id FROM metal_warehouse_order_line l JOIN metal_warehouse_order o ON o.order_id=l.order_id LEFT JOIN metal_inventory i ON i.inventory_id=l.inventory_id LEFT JOIN metal_material m ON l.category='RAW' AND m.material_id=l.reference_id LEFT JOIN metal_product p ON l.category<>'RAW' AND p.product_id=l.reference_id WHERE o.batch_id=? AND o.type='PRODUCTION_OUT' AND o.status='POSTED' ORDER BY l.line_id",batch).stream().map(row->(Map<String,Object>)new LinkedHashMap<String,Object>(row)).toList());}
    private Map<String,Object> processingBatch(long batch,boolean lock){String query="SELECT b.*,w.customer_id,w.customer_snapshot,w.customer,w.processing_type,w.task_type,i.product_id,i.material_id,i.planned_input_weight,COALESCE(i.material_snapshot,i.product_snapshot) product_snapshot FROM metal_process_batch b JOIN metal_work_order w ON w.work_order_id=b.work_order_id AND w.del_flag='0' JOIN metal_work_order_item i ON i.item_id=b.item_id WHERE b.batch_id=?";var b=one(query,batch);scope.check(n(b.get("factory_id")));if(lock){scope.lockFactories(List.of(n(b.get("factory_id"))));b=one(query+" FOR UPDATE",batch);}return b;}

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> handoverContext(long batch){
        readPermit();var b=processingBatch(batch,false);var sources=processingSources(batch);long actor=SecurityUtils.getUserId();boolean participant=sources.stream().anyMatch(s->n(s.get("recipient_id"))==actor&&n(s.get("confirmed_by"))==actor);
        contextParticipant(n(b.get("work_order_id")),batch);
        var roots=jdbc.queryForList("SELECT flow_id FROM metal_warehouse_flow WHERE batch_id=? AND kind='HANDOVER' AND status NOT IN ('VOID','CANCELLED')",batch);
        var result=warehouse.clean(b);warehouse.batchMeasurement(result,b);result.remove("customerSnapshot");result.put("batchId",Long.toString(batch));result.put("product",JSON.parseObject(b.get("product_snapshot").toString()));result.put("canSubmit",participant&&"OPEN".equals(b.get("status"))&&WarehouseFlowRules.POLICY.equals(b.get("completion_policy")));
        long last=jdbc.queryForObject("SELECT MAX(sequence_no) FROM metal_work_order_step WHERE item_id=?",Long.class,b.get("item_id"));result.put("outputType",ProcessingPolicy.outputType(b,n(b.get("sequence_no")),last));
        var ledger=new PartialHandoverLedger(jdbc);boolean partial=PartialHandoverLedger.partial(b),convertible=!partial&&ledger.convertible(b);
        result.put("partialHandover",partial||convertible);result.put("canConvert",convertible);
        result.put("canSubmit",participant&&"OPEN".equals(b.get("status"))&&(partial||convertible||WarehouseFlowRules.POLICY.equals(b.get("completion_policy"))));
        if(!partial&&!convertible)result.put("conversionBlockedReason",WarehouseFlowRules.POLICY.equals(b.get("completion_policy"))?"此记录已有旧版交接，请先沿用原流程完成这份交接验收；后续加工任务再采用分次交接。":"此历史记录已有交接、产出、报工或缺少可核对的真实领料及金属来源，请沿用原流程处理，系统不会自动归并");
        if(partial||convertible){
            result.put("wipGroups",ledger.groups(ledger.balances(b,sources,actor,false)));
            var quantities=one("SELECT COALESCE(SUM(CASE WHEN f.status='POSTED' THEN JSON_UNQUOTE(JSON_EXTRACT(l.line_json,'$.quantity')) ELSE 0 END),0) accepted,COALESCE(SUM(CASE WHEN f.status<>'POSTED' THEN JSON_UNQUOTE(JSON_EXTRACT(l.line_json,'$.quantity')) ELSE 0 END),0) pending FROM metal_warehouse_flow f JOIN metal_warehouse_flow_group g ON g.flow_id=f.flow_id AND g.status<>'SUPERSEDED' JOIN metal_warehouse_flow_line l ON l.group_id=g.group_id WHERE f.batch_id=? AND f.kind='HANDOVER' AND f.status NOT IN ('VOID','CANCELLED') AND g.type IN ('SEMI_IN','FINISHED_IN','RAW_PROCESS_IN')",batch);
            result.put("acceptedQuantity",n(quantities.get("accepted")));result.put("pendingQuantity",n(quantities.get("pending")));result.put("remainingQuantity",Math.max(0,n(b.get("planned_quantity"))-n(quantities.get("accepted"))-n(quantities.get("pending"))));
            result.put("handovers",warehouse.cleaned(jdbc.queryForList("SELECT flow_id id,segment_no,status,version,request_actor FROM metal_warehouse_flow WHERE batch_id=? AND kind='HANDOVER' ORDER BY segment_no,flow_id",batch)));
        }
        result.put("sources",warehouse.cleaned(sources));if(!partial&&!convertible&&!roots.isEmpty()){if(roots.size()!=1)throw fail("存在多份历史交接，不能自动选择，请核对具体记录");var flow=detail(n(roots.get(0).get("flow_id")));result.put("flow",flow);result.put("flowId",flow.get("id"));result.put("version",flow.get("version"));}return result;
    }

    public Map<String,Object> handover(long batch,Handover input){
        permit("metal:warehouse-order:handover");var b=processingBatch(batch,true);String fingerprint=hash(List.of(batch,input));Long played=replay(input.requestId(),"HANDOVER",fingerprint,0);if(played!=null)return detail(played);
        var ledger=new PartialHandoverLedger(jdbc);if(input.wipBalances()!=null)ledger.upgrade(b);boolean partial=PartialHandoverLedger.partial(b);
        if(!"OPEN".equals(b.get("status"))||!WarehouseFlowRules.POLICY.equals(b.get("completion_policy")))throw fail("本批次不属于待交接的新加工策略，历史批次请沿用原流程");
        if(!Boolean.TRUE.equals(input.physicalChecked()))throw fail("请核对全部产出及余料，或明确确认无余料");
        if(!Set.of("NONE","RETURNED").contains(input.residualMode()))throw fail("请选择全部余料或无余料确认");
        var sources=processingSources(batch);long actor=SecurityUtils.getUserId();var own=sources.stream().filter(s->n(s.get("recipient_id"))==actor&&n(s.get("confirmed_by"))==actor).toList();if(own.isEmpty())throw new ServiceException("只能由实际领料人使用本人账号提交加工交接",403);
        var existing=partial?(input.flowId()==null?List.<Map<String,Object>>of():jdbc.queryForList("SELECT * FROM metal_warehouse_flow WHERE batch_id=? AND flow_id=? AND kind='HANDOVER' AND handover_policy_version=2 FOR UPDATE",batch,input.flowId())):jdbc.queryForList("SELECT * FROM metal_warehouse_flow WHERE batch_id=? AND kind='HANDOVER' AND status NOT IN ('VOID','CANCELLED') FOR UPDATE",batch);long flow;
        if(input.flowId()!=null&&existing.isEmpty())throw fail("指定交接分段不存在或不属于本加工记录");
        if(existing.isEmpty()){
            if(input.version()!=n(b.get("version")))throw new ServiceException("加工批次版本已变化，请重新加载",409);if(!partial)WarehouseReservations.batch(jdbc,batch,0);
            Integer segment=partial?jdbc.queryForObject("SELECT COALESCE(MAX(segment_no),0)+1 FROM metal_warehouse_flow WHERE batch_id=?",Integer.class,batch):null;
            flow=insert("INSERT INTO metal_warehouse_flow(request_id,request_hash,request_actor,factory_id,kind,stage,status,work_order_id,batch_id,plan_json,handover_policy_version,segment_no) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",input.requestId(),fingerprint,actor,b.get("factory_id"),"HANDOVER","PROCESSORS","COLLECTING",b.get("work_order_id"),batch,json(input),partial?2:1,segment);
            var people=new TreeSet<Long>();for(var s:partial?own:sources){if(s.get("confirmed_by")==null||n(s.get("confirmed_by"))!=n(s.get("recipient_id")))throw fail("存在未由指定人员真实确认的领料记录，请核对");people.add(n(s.get("recipient_id")));}for(long person:people)jdbc.update("INSERT INTO metal_warehouse_flow_processor(flow_id,actor_id) VALUES(?,?)",flow,person);
        }else{var r=existing.get(0);flow=n(r.get("flow_id"));pending(r);version(r,input.version());if(partial){if(n(r.get("request_actor"))!=actor)throw new ServiceException("只能修订本人提交的交接分段",403);ledger.release(flow);}else WarehouseReservations.batch(jdbc,batch,flow);}
        if(input.documents().stream().mapToInt(x->x.lines().size()).sum()>1000)throw fail("本次交接最多1000条明细");
        boolean last=n(b.get("sequence_no"))==n(one("SELECT MAX(sequence_no) last_sequence FROM metal_work_order_step WHERE item_id=?",b.get("item_id")).get("last_sequence"));String outputType=ProcessingPolicy.outputType(b,n(b.get("sequence_no")),last?n(b.get("sequence_no")):n(b.get("sequence_no"))+1);
        BigDecimal output=BigDecimal.ZERO,returned=BigDecimal.ZERO;boolean hasReturn=false;var sourceWeights=new HashMap<Long,BigDecimal>();var sourcePieces=new HashMap<Long,Long>();var ownerReturns=new HashMap<String,BigDecimal>();var ownerIssued=new HashMap<String,BigDecimal>();for(var s:own)ownerIssued.merge(MaterialOwnership.of(s),d(s.get("net_weight")),BigDecimal::add);
        for(var document:input.documents()){
            if(!Set.of(outputType,"RAW_RETURN","SEMI_RETURN","PROCESS_RAW_RETURN").contains(document.type()))throw fail("中间工序进半成品、末道进成品，余料须按来源类别退回");
            for(var raw:document.lines()){
                Line line=WarehousePrecision.line(raw);WarehouseFlowRules.weight(line.netWeight());
                if(outputType.equals(document.type())){if("RAW_PROCESS_IN".equals(outputType)){if(!"RAW".equals(line.category())||line.referenceId()==null)throw fail("原料加工产出须另选同金属目标物料编码");var material=one("SELECT metal_type_id FROM metal_material WHERE material_id=? AND del_flag='0' FOR UPDATE",line.referenceId());if(n(material.get("metal_type_id"))!=n(b.get("metal_type_id")))throw fail("产出物料必须与本批金属一致");if(line.referenceQuantity()!=null&&(!MeasurementPolicy.configurable(jdbc,b)||!SemiMeasurementRules.weight(b)||"RAW_PROCESSING".equals(b.get("task_type"))))throw fail("该原料产出不填写参考件数");}else if(line.referenceId()==null||line.referenceId()!=n(b.get("product_id"))||!Objects.equals(line.category(),"FINISHED_IN".equals(outputType)?"FINISHED":"SEMI"))throw fail("产出须选择本批产品及正确库存类别");if(line.inventoryId()!=null||line.sourceLineId()!=null||line.batchNo()!=null&&!line.batchNo().isBlank())throw fail("产出来源与批次由系统生成");if(SemiMeasurementRules.weight(b)){if(line.quantity()!=null&&line.quantity()!=0)throw fail("重量型产出不填写有效件数");SemiMeasurementRules.reference(line.referenceQuantity());}else if(line.quantity()==null||line.quantity()<1||line.quantity()>n(b.get("planned_quantity"))||line.referenceQuantity()!=null)throw fail("按件产出须填写批次范围内的有效件数，不使用参考件数");output=output.add(line.netWeight());}
                else {hasReturn=true;returned=returned.add(line.netWeight());String owner;
                    if("PROCESS_RAW_RETURN".equals(document.type())){ProcessRawReturnService.validateLine(line,Objects.toString(b.get("customer"),""));var material=one("SELECT metal_type_id FROM metal_material WHERE material_id=? AND del_flag='0' FOR UPDATE",line.referenceId());if(ProcessingPolicy.v2(b)){if(n(material.get("metal_type_id"))!=n(b.get("metal_type_id")))throw fail("回收物料与工单金属不一致");}else MaterialTypes.requireProduction(id(material.get("metal_type_id")));if(line.materialOwnership()==null)throw fail("加工回收余料须分别申报客户料或厂料");owner=WarehouseFlowRules.newOwner(line.materialOwnership(),"FACTORY".equals(line.materialOwnership())?line.customerId():id(b.get("customer_id")));RawInventoryCustomers.inherited(line,"FACTORY".equals(owner)?null:id(b.get("customer_id")));}
                    else {var source=own.stream().filter(s->Objects.equals(id(s.get("line_id")),line.sourceLineId())).findFirst().orElseThrow(()->new ServiceException("余料须关联本人在本批实领的原出库明细",403));boolean rawReturn="RAW_RETURN".equals(document.type());if(!Objects.equals(source.get("category"),rawReturn?"RAW":"SEMI")||document.warehouseId()!=n(source.get("warehouse_id")))throw fail("未用原料和未加工半成品须分别退回原库存、原仓库");MaterialOwnership.inherit(line.materialOwnership(),source);owner=MaterialOwnership.of(source);BigDecimal weight=sourceWeights.merge(line.sourceLineId(),line.netWeight(),BigDecimal::add);long quantity=sourcePieces.merge(line.sourceLineId(),line.quantity()==null?0:line.quantity(),Long::sum);if(weight.compareTo(d(source.get("net_weight")))>0||!InventoryMeasurement.weight(source)&&quantity>n(source.get("quantity")))throw fail("累计申报余料超过本人原出库重量或件数");}
                    ownerReturns.merge(owner,line.netWeight(),BigDecimal::add);
                }
            }
        }
        if("NONE".equals(input.residualMode())&&hasReturn||"RETURNED".equals(input.residualMode())&&!hasReturn)throw fail("余料确认方式与清单不一致");
        for(var e:ownerReturns.entrySet())if(e.getValue().compareTo(ownerIssued.getOrDefault(e.getKey(),BigDecimal.ZERO))>0)throw fail("该归属余料超过本人领入的相应客户料或厂料，不能改变材料归属");
        BigDecimal issued=own.stream().map(s->d(s.get("net_weight"))).reduce(BigDecimal.ZERO,BigDecimal::add);if(output.add(returned).compareTo(issued)>0)throw fail("本人加工产出和余料合计超过实际领入重量");
        if(partial&&output.signum()==0&&jdbc.queryForObject("SELECT COUNT(*) FROM metal_warehouse_flow f JOIN metal_warehouse_flow_group g ON g.flow_id=f.flow_id AND g.status<>'SUPERSEDED' JOIN metal_warehouse_flow_line l ON l.group_id=g.group_id WHERE f.batch_id=? AND f.flow_id<>? AND f.kind='HANDOVER' AND f.status NOT IN ('VOID','CANCELLED') AND g.type IN ('SEMI_IN','FINISHED_IN','RAW_PROCESS_IN') AND l.processor_weight>0",Long.class,batch,flow)==0)throw fail("仅还料交接须已有真实产出申报；未加工的领料请按撤回流程处理");
        if(partial)ledger.allocate(flow,b,sources,actor,input,output.add(returned));
        var oldGroups=groups(flow);for(var g:oldGroups)if(n(g.get("processor_id"))==actor){jdbc.update("UPDATE metal_warehouse_flow_group SET status='SUPERSEDED' WHERE group_id=?",g.get("group_id"));jdbc.update("UPDATE metal_warehouse_order SET status='RETURNED',version=version+1 WHERE order_id=? AND status IN ('WAITING_IN','RETURNED')",g.get("order_id"));}
        jdbc.update("UPDATE metal_warehouse_flow SET revision=revision+1,version=version+1,status='COLLECTING',stage='PROCESSORS' WHERE flow_id=?",flow);
        if(!partial){
            jdbc.update("UPDATE metal_warehouse_flow_group SET status='WAITING',confirmed_by=NULL,confirmed_at=NULL WHERE flow_id=? AND status<>'SUPERSEDED'",flow);
            jdbc.update("UPDATE metal_warehouse_flow_group g FORCE INDEX (idx_flow_group_revision) STRAIGHT_JOIN metal_warehouse_order o ON o.order_id=g.order_id SET o.status='WAITING_IN',o.confirmed_by=NULL,o.confirmed_at=NULL,o.version=o.version+1 WHERE g.flow_id=? AND g.status<>'SUPERSEDED' AND o.status IN ('WAITING_IN','RETURNED')",flow);
            jdbc.update("UPDATE metal_warehouse_flow_group g FORCE INDEX (idx_flow_group_revision) STRAIGHT_JOIN metal_warehouse_flow_line l ON l.group_id=g.group_id SET l.actual_weight=NULL,l.difference_reason=NULL WHERE g.flow_id=? AND g.status<>'SUPERSEDED'",flow);
        }
        int index=0;for(var document:input.documents()){
            // Separate source orders retain the existing raw-return provenance rule.
            var split=new LinkedHashMap<Long,List<Line>>();for(var line:document.lines()){long source=0;if(Set.of("RAW_RETURN","SEMI_RETURN").contains(document.type()))source=n(one("SELECT order_id FROM metal_warehouse_order_line WHERE line_id=?",line.sourceLineId()).get("order_id"));split.computeIfAbsent(source,k->new ArrayList<>()).add(WarehousePrecision.line(line));}
            for(var part:split.values()){var o=new Order(child(input.requestId(),index++),n(b.get("factory_id")),document.warehouseId(),document.type(),input.orderDate(),null,List.of(actor),document.keeperId(),null,null,document.reviewerId(),n(b.get("work_order_id")),batch,(int)n(b.get("version")),false,null,input.reason(),part,n(b.get("item_id")),n(b.get("process_id")));addGroup(flow,o,actor);}
        }
        jdbc.update("UPDATE metal_warehouse_flow_processor SET submitted=TRUE,submitted_time=NOW() WHERE flow_id=? AND actor_id=?",flow,actor);
        validateHandoverPieces(jdbc,b,flow);
        if(jdbc.queryForObject("SELECT COUNT(*) FROM metal_warehouse_flow_processor WHERE flow_id=? AND submitted=FALSE",Long.class,flow)==0){
            var sums=one("SELECT COALESCE(SUM(CASE WHEN g.type IN ('SEMI_IN','FINISHED_IN','RAW_PROCESS_IN') THEN l.processor_weight ELSE 0 END),0) output,COALESCE(SUM(CASE WHEN g.type IN ('RAW_RETURN','SEMI_RETURN','PROCESS_RAW_RETURN') THEN l.processor_weight ELSE 0 END),0) returned FROM metal_warehouse_flow_line l JOIN metal_warehouse_flow_group g ON g.group_id=l.group_id WHERE g.flow_id=? AND g.status<>'SUPERSEDED'",flow);
            BigDecimal allIssued=partial?d(one("SELECT SUM(allocated_weight) weight FROM metal_handover_allocation WHERE flow_id=? AND voided=FALSE",flow).get("weight")):sources.stream().map(s->d(s.get("net_weight"))).reduce(BigDecimal.ZERO,BigDecimal::add);WarehouseFlowRules.mass(allIssued,d(sums.get("output")),d(sums.get("returned")),d(sums.get("output")).add(d(sums.get("returned"))),partial);
            jdbc.update("UPDATE metal_warehouse_flow SET stage='KEEPER',status='WAITING' WHERE flow_id=?",flow);
        }
        event(flow,"HANDOVER",input);remember(flow,input.requestId(),"HANDOVER",fingerprint);return detail(flow);
    }
}
