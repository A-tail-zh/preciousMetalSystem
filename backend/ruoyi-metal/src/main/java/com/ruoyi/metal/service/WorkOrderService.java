package com.ruoyi.metal.service;

import java.util.*;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.sql.*;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.*;
import org.springframework.transaction.annotation.Transactional;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.metal.catalog.*;
import com.ruoyi.metal.mapper.WorkOrderMapper;
import com.ruoyi.metal.security.FactoryScope;

/**
 * 生产工单业务服务，负责业务校验、数据处理和相关持久化操作。
 */
@Service
public class WorkOrderService {
    private final JdbcTemplate jdbc;
    private final WorkOrderMapper mapper;
    private final FactoryScope scope;
    private final ProductService products;
    @org.springframework.beans.factory.annotation.Value("${metal.auto-semi-release-enabled:true}")
    private boolean autoSemiReleaseEnabled=true;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String HEADER = "SELECT CAST(work_order_id AS CHAR) id,order_no orderNo,customer,CAST(customer_id AS CHAR) customerId,customer_version customerVersion,CAST(customer_snapshot AS CHAR) customerSnapshot,processing_type processingType,CAST(customer_incoming_weight AS CHAR) customerIncomingWeight,task_type taskType,processing_policy_version processingPolicyVersion,CAST(factory_id AS CHAR) factoryId,CAST(production_line_id AS CHAR) productionLineId,CAST(owner_id AS CHAR) ownerId,factory_name factoryName,line_name lineName,owner_name ownerName,DATE_FORMAT(order_date,'%Y-%m-%d') orderDate,status,reject_reason rejectReason,create_by createBy,creator_name creatorName,DATE_FORMAT(create_time,'%Y-%m-%d %H:%i:%s') createTime,version,(EXISTS(SELECT 1 FROM metal_process_batch b WHERE b.work_order_id=metal_work_order.work_order_id) OR EXISTS(SELECT 1 FROM metal_warehouse_order o WHERE o.work_order_id=metal_work_order.work_order_id) OR EXISTS(SELECT 1 FROM metal_work_report r JOIN metal_work_order_item i ON i.item_id=r.item_id WHERE i.work_order_id=metal_work_order.work_order_id)) hasBusinessReferences FROM metal_work_order WHERE work_order_id=? AND del_flag='0'";

    public WorkOrderService(JdbcTemplate jdbc, WorkOrderMapper mapper, FactoryScope scope, ProductService products) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.scope = scope;
        this.products = products;
    }

    private static Long id(Object value) {
        return Long.valueOf(value.toString());
    }

    private static String text(Map<String, Object> row, String key) {
        return row.get(key) == null ? null : row.get(key).toString();
    }

    private static ServiceException invalid(String message) {
        return new ServiceException(message);
    }

    private static String actorName() {
        String name = SecurityUtils.getLoginUser().getUser().getNickName();
        return name == null || name.isBlank() ? SecurityUtils.getUsername() : name;
    }

    private Map<String, Object> header(Long id, boolean lock) {
        var rows = jdbc.queryForList(HEADER + (lock ? " FOR UPDATE" : ""), id);
        if (rows.isEmpty()) throw invalid("工单不存在或已删除");
        return rows.get(0);
    }

    private void access(Map<String, Object> order) {
        scope.check(id(order.get("factoryId")));
    }

    /**
     * 查询列表并补充关联资料。
     */
    public List<Map<String, Object>> list() {
        var rows = mapper.list(scope.currentFactory());
        rows.forEach(WorkOrderService::capabilities);
        return rows;
    }

    /**
     * 查询指定记录详情；业务层执行相应的访问权限检查。
     */
    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Map<String, Object> detail(Long id) {
        var order = header(id, false);
        access(order);
        capabilities(order);
        if(order.get("customerSnapshot")!=null)order.put("customerSnapshot",JSON.parseObject(text(order,"customerSnapshot")));
        var detailItems=items(id);
        executionTimes(id,detailItems);
        order.put("items", detailItems);
        order.put("reviews", jdbc.queryForList("SELECT CAST(review_id AS CHAR) id,action,from_status fromStatus,to_status toStatus,reason,CAST(actor_id AS CHAR) actorId,actor_name actorName,DATE_FORMAT(create_time,'%Y-%m-%d %H:%i:%s') createTime FROM metal_work_order_review WHERE work_order_id=? ORDER BY review_id", id));
        order.put("reports", List.of());
        order.put("reportingAvailable", false);
        return order;
    }

    private List<Map<String, Object>> items(Long orderId) {
        var result = jdbc.queryForList("SELECT CAST(item_id AS CHAR) id,CAST(product_id AS CHAR) productId,CAST(material_id AS CHAR) materialId,CAST(planned_input_weight AS CHAR) plannedInputWeight,sequence_no sequenceNo,quantity,CAST(COALESCE(material_snapshot,product_snapshot) AS CHAR) snapshot FROM metal_work_order_item WHERE work_order_id=? ORDER BY sequence_no", orderId);
        for (var item : result) {
            item.put("snapshot", JSON.parseObject(text(item, "snapshot")));
            item.put("plans", jdbc.queryForList("SELECT CAST(process_id AS CHAR) processId,sequence_no sequenceNo,loss_unit lossUnit,measurement_mode measurementMode,process_role processRole,output_stage outputStage,weight_stage_closed weightStageClosed,process_name processName,CAST(theoretical_loss AS CHAR) loss,DATE_FORMAT(start_time,'%Y-%m-%d %H:%i:%s') startTime,DATE_FORMAT(end_time,'%Y-%m-%d %H:%i:%s') endTime,DATE_FORMAT(actual_start_time,'%Y-%m-%d %H:%i:%s') actualStartTime,DATE_FORMAT(actual_end_time,'%Y-%m-%d %H:%i:%s') actualEndTime FROM metal_work_order_step WHERE item_id=? ORDER BY sequence_no", id(item.get("id"))));
        }
        return result;
    }

    private Map<String, Object> line(Long lineId, Long factoryId, boolean lock) {
        var rows = jdbc.queryForList("SELECT factory_id,production_line_name name FROM metal_production_line WHERE production_line_id=?" + (lock ? " FOR UPDATE" : ""), lineId);
        if (rows.isEmpty() || rows.get(0).get("factory_id") == null) throw invalid("请选择已分配工厂的有效产线");
        var line = rows.get(0);
        Long actual = id(line.get("factory_id"));
        scope.check(actual);
        if (factoryId != null && !factoryId.equals(actual)) throw invalid("产线必须属于所选工厂");
        return line;
    }

    private void lockLines(Collection<Long> ids) {
        for (Long lineId : ids.stream().distinct().sorted().toList()) line(lineId, null, true);
    }

    private Map<String, Object> lockOrder(Long orderId) {
        var initial = header(orderId, false);
        access(initial);
        scope.lockFactories(List.of(id(initial.get("factoryId"))));
        lockLines(List.of(id(initial.get("productionLineId"))));
        lockDependencies(orderId, List.of(), id(initial.get("ownerId")));
        var current = header(orderId, true);
        access(current);
        if (!Objects.equals(current.get("factoryId"), initial.get("factoryId")) || !Objects.equals(current.get("productionLineId"), initial.get("productionLineId")))
            throw invalid("工单归属已改变，请刷新后重试");
        return current;
    }

    private void lockDependencies(Long orderId, List<Long> additional, Long ownerId) {
        // Match material edits: lock materials before the work header/reference rows.
        Set<Long> materialIds = new TreeSet<>();
        for (var row : jdbc.queryForList("SELECT material_id FROM metal_work_order_item WHERE work_order_id=?", orderId))
            if (row.get("material_id") != null) materialIds.add(id(row.get("material_id")));
        for (Long key : materialIds)
            jdbc.queryForList("SELECT material_id FROM metal_material WHERE material_id=? FOR UPDATE", key);
        Set<Long> productIds = new TreeSet<>(additional.stream().filter(Objects::nonNull).toList());
        for (var row : jdbc.queryForList("SELECT product_id FROM metal_work_order_item WHERE work_order_id=?", orderId))
            if(row.get("product_id")!=null)productIds.add(id(row.get("product_id")));
        for (Long key : productIds)
            jdbc.queryForList("SELECT product_id FROM metal_product WHERE product_id=? FOR UPDATE", key);
        Set<Long> processIds = new TreeSet<>();
        for (var row : jdbc.queryForList("SELECT s.process_id FROM metal_work_order_step s JOIN metal_work_order_item i ON i.item_id=s.item_id WHERE i.work_order_id=?", orderId))
            processIds.add(id(row.get("process_id")));
        for (Long key : productIds)
            for (var row : jdbc.queryForList("SELECT process_id FROM metal_product_process WHERE product_id=?", key))
                processIds.add(id(row.get("process_id")));
        for (Long key : processIds)
            jdbc.queryForList("SELECT process_id FROM metal_process WHERE process_id=? FOR UPDATE", key);
        jdbc.queryForList("SELECT user_id FROM sys_user WHERE user_id=? FOR UPDATE", ownerId);
    }

    static void capabilities(Map<String, Object> order) {
        boolean referenced = Boolean.TRUE.equals(order.get("hasBusinessReferences")) || order.get("hasBusinessReferences") instanceof Number n && n.longValue() != 0;
        String status = text(order, "status");
        String reason = Set.of("PENDING", "APPROVED", "REJECTED").contains(status) ? "旧流程工单仅可查看" : !"WAITING_MATERIAL".equals(status) ? "仅待领料工单允许编辑或删除" : referenced ? "工单已有业务引用，不能编辑或删除" : "";
        order.put("canEdit", reason.isEmpty());
        order.put("canDelete", reason.isEmpty());
        order.put("operationDisabledReason", reason);
    }

    private void editable(Map<String, Object> order) {
        capabilities(order);
        if (!Boolean.TRUE.equals(order.get("canEdit"))) throw invalid(text(order, "operationDisabledReason"));
    }

    private void version(Map<String, Object> order, Integer expected) {
        if (expected == null || ((Number) order.get("version")).intValue() != expected)
            throw invalid("工单已被修改，请刷新后重试");
    }

    private long insert(String sql, Object... args) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            return statement;
        }, key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }

    private String nextNumber() {
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        jdbc.update("INSERT INTO metal_work_order_sequence(business_date,sequence_value) VALUES(?,0) ON DUPLICATE KEY UPDATE sequence_value=sequence_value", java.sql.Date.valueOf(date));
        Integer number = jdbc.queryForObject("SELECT sequence_value FROM metal_work_order_sequence WHERE business_date=? FOR UPDATE", Integer.class, java.sql.Date.valueOf(date));
        if (number >= 999999) throw invalid("当天工单编号已达到上限");
        jdbc.update("UPDATE metal_work_order_sequence SET sequence_value=sequence_value+1 WHERE business_date=?", java.sql.Date.valueOf(date));
        return "WO" + date.format(DateTimeFormatter.BASIC_ISO_DATE) + String.format("%06d", number + 1);
    }

    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    /**
     * 根据请求标识预留单据编号。
     */
    public String reserveNumber(String requestId) {
        if (requestId == null || !requestId.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
            throw invalid("编号申请须提供UUID请求标识");
        jdbc.update("INSERT INTO metal_work_order_number(request_id,actor_id) VALUES(?,?) ON DUPLICATE KEY UPDATE request_id=request_id", requestId, SecurityUtils.getUserId());
        var row = jdbc.queryForMap("SELECT actor_id,order_no FROM metal_work_order_number WHERE request_id=? FOR UPDATE", requestId);
        if (!SecurityUtils.getUserId().equals(id(row.get("actor_id")))) throw invalid("编号申请不属于当前人员");
        if (row.get("order_no") != null) return row.get("order_no").toString();
        String number = nextNumber();
        jdbc.update("UPDATE metal_work_order_number SET order_no=? WHERE request_id=?", number, requestId);
        return number;
    }

    private String hash(WorkOrderRequest request) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(request.fingerprintInput().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, Object> snapshot(ProductRecord product) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("measurementPolicyVersion", MeasurementPolicy.CONFIGURABLE_VERSION);
        value.put("code", product.getCode());
        value.put("name", product.getName());
        value.put("category", product.getCategory());
        value.put("calculationVersion",ProductService.version(product));
        value.put("metalTypeId",product.getMetalTypeId()==null?"1":product.getMetalTypeId().toString());
        value.put("metalTypeName",product.getMetalTypeName()==null?"铱":product.getMetalTypeName());
        value.put("productType",product.getProductType()==null?ProductTypes.legacy(product.getCategory()):product.getProductType());
        value.put("specification", product.getSpecification());
        var dimensionSnapshot=new TreeMap<String,String>();
        if(ProductService.version(product)==2)dimensionSnapshot.putAll(ProductGeometry.normalize(product.getProductType(),product.getDimensions()));
        else product.getDimensions().forEach((key,v)->dimensionSnapshot.put(key,BusinessDecimal.positive(new BigDecimal(v),"尺寸").toPlainString()));
        value.put("dimensions", dimensionSnapshot);
        value.put("dimensionUnit", "cm");
        if(ProductService.version(product)==2&&product.getDimensions().containsKey("angle"))value.put("dimensionUnits",Map.of("angle","°"));
        value.put("volume", product.getVolume() == null ? null : BusinessDecimal.display(product.getVolume()));
        value.put("purity", product.getPurity() == null ? null : BusinessDecimal.display(product.getPurity()));
        value.put("weight", product.getWeight() == null ? null : BusinessDecimal.display(product.getWeight()));
        return value;
    }

    private record Prepared(Map<String, Object> snapshot, List<Map<String, Object>> steps,
                            WorkOrderRequest.Item input) {
    }

    @SuppressWarnings("unchecked")
    private List<Prepared> prepare(WorkOrderRequest request, List<Map<String, Object>> previous,int policy,String taskType) {
        line(request.productionLineId(), request.factoryId(), true);
        for(var item:request.items()){if(item.productId()!=null)scope.requireCatalog("product",item.productId(),request.factoryId());if(item.materialId()!=null)scope.requireCatalog("material",item.materialId(),request.factoryId());for(var plan:item.plans())scope.requireCatalog("process",plan.processId(),request.factoryId());}
        if (request.orderDate().getYear() < 1000 || request.orderDate().getYear() > 9999)
            throw invalid("开单日期超出支持范围");
        Map<Long, Map<String, Object>> old = new HashMap<>();
        previous.forEach(item -> old.put(id(item.get("id")), item));
        Set<Long> used = new HashSet<>();
        boolean raw="RAW_PROCESSING".equals(taskType);
        request.items().forEach(item->validateItem(item,raw));
        if (!previous.isEmpty()) validateRetainedItems(request.items(), previous, raw);
        for (Long productId : request.items().stream().map(WorkOrderRequest.Item::productId).filter(Objects::nonNull).distinct().sorted().toList())
            if (jdbc.queryForList("SELECT product_id FROM metal_product WHERE product_id=? AND del_flag='0' FOR UPDATE", productId).isEmpty())
                throw invalid("产品不存在或已删除");
        for(Long materialId:request.items().stream().map(WorkOrderRequest.Item::materialId).filter(Objects::nonNull).distinct().sorted().toList())
            if(jdbc.queryForList("SELECT material_id FROM metal_material WHERE material_id=? AND del_flag='0' FOR UPDATE",materialId).isEmpty())throw invalid("物料不存在或已删除");
        List<Prepared> result = new ArrayList<>();
        Set<Long> processIds = new TreeSet<>();
        if(raw)for(var input:request.items())for(var plan:input.plans())processIds.add(plan.processId());
        else{
            for(var productId:request.items().stream().map(WorkOrderRequest.Item::productId).distinct().toList())
                for(var row:jdbc.queryForList("SELECT process_id FROM metal_product_process WHERE product_id=?",productId))processIds.add(id(row.get("process_id")));
            for(var item:previous)for(var step:(List<Map<String,Object>>)item.get("plans"))processIds.add(id(step.get("processId")));
        }
        for(Long processId:processIds)
            if(jdbc.queryForList("SELECT process_id FROM metal_process WHERE process_id=? AND del_flag='0' FOR UPDATE",processId).isEmpty())throw invalid("工序不存在或已删除");
        for (var input : request.items()) {
            if (input.id() != null && (!old.containsKey(input.id()) || !used.add(input.id())))
                throw new ServiceException("产品明细不属于本工单或重复", 403);
            if(raw){
                var retained=input.id()==null?null:old.get(input.id());
                var material=jdbc.queryForList("SELECT m.material_code code,CONCAT(t.name,m.material_type) name,m.material_type materialType,CAST(m.metal_type_id AS CHAR) metalTypeId,t.name metalTypeName,m.specification FROM metal_material m JOIN metal_type t ON t.metal_type_id=m.metal_type_id WHERE m.material_id=? AND m.del_flag='0'",input.materialId());
                if(material.isEmpty())throw invalid("物料不存在或金属无效，请重新选择");
                boolean sameMaterial=retained!=null&&Objects.equals(text(retained,"materialId"),input.materialId().toString());
                Map<String,Object> materialSnapshot=sameMaterial?(Map<String,Object>)retained.get("snapshot"):material.get(0);
                if (!sameMaterial) {materialSnapshot=new LinkedHashMap<>(materialSnapshot);materialSnapshot.put("measurementPolicyVersion",MeasurementPolicy.CONFIGURABLE_VERSION);}
                var previousSteps=sameMaterial?(List<Map<String,Object>>)retained.get("plans"):List.<Map<String,Object>>of();
                boolean sameRoute=previousSteps.stream().map(s->id(s.get("processId"))).toList().equals(input.plans().stream().map(WorkOrderRequest.Plan::processId).toList());
                var steps=new ArrayList<Map<String,Object>>();
                for(int i=0;i<input.plans().size();i++){
                    var plan=input.plans().get(i);
                    var step=new LinkedHashMap<String,Object>(sameRoute?previousSteps.get(i):process(plan.processId()));
                    BigDecimal loss=sameRoute?new BigDecimal(text(step,"loss")):BusinessDecimal.round(plan.theoreticalLoss());
                    if(loss==null||loss.signum()<0||loss.compareTo(new BigDecimal("100"))>0)throw invalid("原料工序须填写0至100的理论损耗百分比");
                    step.put("loss",loss.toPlainString());
                    if(!sameRoute){step.put("lossUnit","PERCENT");step.put("measurementMode","WEIGHT");}
                    steps.add(step);
                }
                validatePlans(input.plans(),steps.stream().map(s->id(s.get("processId"))).toList());
                if(!sameRoute)route(steps,taskType);
                result.add(new Prepared(materialSnapshot,steps,input));
                continue;
            }
            if (jdbc.queryForList("SELECT product_id FROM metal_line_product WHERE production_line_id=? AND product_id=? FOR UPDATE", request.productionLineId(), input.productId()).isEmpty())
                throw invalid("产品必须关联到所选产线");
            var retained = input.id() == null ? null : old.get(input.id());
            Map<String, Object> productSnapshot;
            List<Map<String, Object>> steps = new ArrayList<>();
            if (retained != null && input.productId().equals(id(retained.get("productId")))) {
                productSnapshot = (Map<String, Object>) retained.get("snapshot");
                steps.addAll((List<Map<String, Object>>) retained.get("plans"));
            } else {
                ProductRecord product = products.detail(input.productId());
                productSnapshot = snapshot(product);
                for (ProductStep step : product.getProcesses()) {
                    Map<String, Object> s = new LinkedHashMap<>();
                    s.put("processId", step.getProcessId().toString());
                    s.put("processName", step.getProcessName());
                    s.put("loss", BusinessDecimal.display(step.getLoss()));
                    s.put("measurementMode",step.getMeasurementMode());
                    s.put("lossUnit",step.getLossUnit());
                    if(!"PERCENT".equals(step.getLossUnit()))throw invalid("产品工序尚为历史g/件，请先配置理论损耗百分比");
                    steps.add(s);
                }
            }
            requireMetal(productSnapshot);
            if (steps.isEmpty()) throw invalid("产品尚未配置工序，请先维护产品工艺");
            validatePlans(input.plans(), steps.stream().map(s -> id(s.get("processId"))).toList());
            if(policy>=ProcessingPolicy.VERSION&&(retained==null||!Objects.equals(text(retained,"productId"),input.productId().toString())))route(steps,taskType);
            steps.forEach(s -> processIds.add(id(s.get("processId"))));
            result.add(new Prepared(productSnapshot, steps, input));
        }
        owner(request.factoryId(), request.ownerId(), true);
        return result;
    }

    static void validateRetainedItems(List<WorkOrderRequest.Item> inputs, List<Map<String,Object>> previous, boolean raw) {
        if (inputs.size()!=previous.size()) throw invalid("已保存工单的产品或物料明细不可增删，请新建工单调整工艺");
        var saved=new HashMap<Long,Map<String,Object>>();
        previous.forEach(item->saved.put(id(item.get("id")),item));
        for(var input:inputs){
            var old=saved.remove(input.id());
            String key=raw?"materialId":"productId";
            Long incoming=raw?input.materialId():input.productId();
            if(old==null||!Objects.equals(id(old.get(key)),incoming))throw invalid("已保存工单的产品或物料快照不可替换，请新建工单");
            @SuppressWarnings("unchecked") var steps=(List<Map<String,Object>>)old.get("plans");
            if(input.plans().size()!=steps.size())throw invalid("已保存工单的工艺快照不可更改，请新建工单");
            for(int i=0;i<steps.size();i++){
                var plan=input.plans().get(i);var step=steps.get(i);
                if(!Objects.equals(plan.processId(),id(step.get("processId"))))throw invalid("已保存工单的工艺顺序不可更改，请新建工单");
                if(plan.theoreticalLoss()!=null&&plan.theoreticalLoss().compareTo(new BigDecimal(text(step,"loss")))!=0)throw invalid("已保存工单的理论损耗快照不可更改，请新建工单");
            }
        }
    }

    static void validateItem(WorkOrderRequest.Item input,boolean raw){
        if(input==null)throw invalid("请填写工单明细");
        if(raw){
            if(input.productId()!=null||input.quantity()!=null&&input.quantity()!=0||input.materialId()==null||input.materialId()<=0)throw invalid("原料任务须选择物料，不能指定产品或件数");
            BusinessDecimal.positive(input.plannedInputWeight(),"计划投入重量");
        }else if(input.productId()==null||input.productId()<=0||input.quantity()==null||input.quantity()<1||input.quantity()>1000000||input.materialId()!=null||input.plannedInputWeight()!=null)throw invalid("产品任务须选择产品及有效件数");
    }

    private void requireMetal(Map<String,Object> snapshot){
        long metal=ProcessingPolicy.metalId(snapshot);
        if(jdbc.queryForList("SELECT metal_type_id FROM metal_type WHERE metal_type_id=?",metal).isEmpty())throw invalid("金属无效，请重新选择产品或物料");
    }

    private Map<String,Object> process(Long processId){
        var rows=jdbc.queryForList("SELECT CAST(p.process_id AS CHAR) processId,p.process_name processName,t.type_name typeName FROM metal_process p JOIN metal_process_type t ON t.type_id=p.process_type WHERE p.process_id=? AND p.del_flag='0'",processId);
        if(rows.isEmpty())throw invalid("工序或工序类型不存在或已删除");
        return rows.get(0);
    }

    private void route(List<Map<String,Object>> steps,String taskType){
        var types=steps.stream().map(step->text(process(id(step.get("processId"))),"typeName")).toList();
        var modes=steps.stream().map(step->Objects.toString(step.get("measurementMode"),"PIECE")).toList();
        var route=ProcessingPolicy.route(taskType,types,modes);
        for(int i=0;i<steps.size();i++){
            var step=steps.get(i);var selected=route.get(i);
            step.put("processRole",selected.processRole());step.put("outputStage",selected.outputStage());step.put("measurementMode",selected.measurementMode());
            if("RAW".equals(selected.outputStage()))step.put("lossUnit","PERCENT");
        }
    }

    /**
     * 校验工序计划与产品工序配置的匹配关系。
     */
    public static void validatePlans(List<WorkOrderRequest.Plan> plans, List<Long> expected) {
        if (plans == null || plans.isEmpty() || plans.size() != expected.size() || new HashSet<>(expected).size()!=expected.size())
            throw invalid("工序计划时间必须完整填写，不得更改工艺顺序");
        LocalDateTime previous = null;
        for (int i = 0; i < plans.size(); i++) {
            var plan = plans.get(i);
            if (plan == null || !expected.get(i).equals(plan.processId()))
                throw invalid("工序计划时间顺序与工艺不一致");
            if (plan.startTime() == null || plan.endTime() == null || !plan.startTime().isBefore(plan.endTime()))
                throw invalid("工序开始时间必须早于结束时间");
            if (plan.startTime().getSecond() != 0 || plan.endTime().getSecond() != 0 || plan.startTime().getNano() != 0 || plan.endTime().getNano() != 0)
                throw invalid("计划时间须精确到分钟");
            if (plan.startTime().getYear() < 1000 || plan.endTime().getYear() > 9999)
                throw invalid("计划时间超出支持范围");
            if (previous != null && plan.startTime().isBefore(previous))
                throw invalid("后一工序开始不得早于前一工序结束");
            previous = plan.endTime();
        }
    }

    private static void requireProcessing(String value,boolean legacy){
        if(value==null&&legacy)return;
        if(value==null||!Set.of("MATERIAL","DIRECT","SHORTAGE").contains(value))throw invalid("请选择加工类型");
    }

    private static String taskType(String value){
        if(value==null)return "PRODUCTION";
        if(!Set.of("PRODUCTION","RAW_PROCESSING").contains(value))throw invalid("请选择有效任务类型");
        return value;
    }

    private static void rawProcessing(String task,String processing){
        if("RAW_PROCESSING".equals(task)&&!Set.of("DIRECT","MATERIAL").contains(Objects.toString(processing,"")))throw invalid("原料任务请选择客户来料或工厂自有直接加工类型");
    }

    private Map<String,Object> customer(WorkOrderRequest request,Map<String,Object> current){
        if("RAW_PROCESSING".equals(request.taskType()==null&&current!=null?text(current,"taskType"):request.taskType())&&"DIRECT".equals(request.processingType()==null&&current!=null?text(current,"processingType"):request.processingType())){
            if(request.customerId()!=null||request.customerVersion()!=null)throw invalid("工厂自有原料任务不能指定客户");
            return new LinkedHashMap<>(Map.of("name","工厂自有"));
        }
        if(request.customerId()==null){
            if(current!=null&&current.get("customerId")==null&&request.factoryId().equals(id(current.get("factoryId"))))return new LinkedHashMap<>(Map.of("name",text(current,"customer")));
            throw invalid("请选择当前工厂的客户");
        }
        var rows=jdbc.queryForList("SELECT customer_id,factory_id,customer_name name,company_name companyName,address,contact_name contactName,contact_phone contactPhone,department,version FROM metal_customer WHERE customer_id=? AND del_flag='0' FOR UPDATE",request.customerId());
        if(rows.isEmpty())throw invalid("客户不存在或已删除，请重新选择");
        var selected=rows.get(0);
        if(!request.factoryId().equals(id(selected.get("factory_id"))))throw new ServiceException("客户不属于当前工厂",403);
        if(request.customerVersion()==null||((Number)selected.get("version")).intValue()!=request.customerVersion())throw invalid("客户资料已变化，请刷新客户选项后重新选择");
        if(current!=null&&Objects.equals(text(current,"customerId"),request.customerId().toString())&&current.get("customerSnapshot")!=null)return new LinkedHashMap<>(JSON.parseObject(text(current,"customerSnapshot")));
        selected=new LinkedHashMap<>(selected);selected.remove("customer_id");selected.remove("factory_id");return selected;
    }

    public List<Map<String,Object>> customers(Long factoryId){
        scope.check(factoryId);
        return jdbc.queryForList("SELECT CAST(customer_id AS CHAR) id,customer_name name,company_name companyName,address,contact_name contactName,contact_phone contactPhone,department,version FROM metal_customer WHERE factory_id=? AND del_flag='0' ORDER BY customer_name,company_name,customer_id",factoryId);
    }

    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public List<Map<String,Object>> materials(Long work){
        access(header(work,false));
        return jdbc.queryForList("SELECT CAST(l.reference_id AS CHAR) materialId,l.code,m.material_type materialType,l.specification,CAST(SUM(l.net_weight) AS CHAR) netWeight FROM metal_warehouse_order o JOIN metal_warehouse_order_line l ON l.order_id=o.order_id LEFT JOIN metal_material m ON m.material_id=l.reference_id WHERE o.work_order_id=? AND o.type='PRODUCTION_OUT' AND o.status='POSTED' AND l.category='RAW' GROUP BY l.reference_id,l.code,m.material_type,l.specification ORDER BY l.reference_id,l.code",work);
    }

    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public List<Map<String,Object>> reports(Long work){
        access(header(work,false));
        var result=jdbc.queryForList("SELECT CAST(r.report_id AS CHAR) id,CAST(r.batch_id AS CHAR) batchId,CAST(r.item_id AS CHAR) itemId,CAST(r.process_id AS CHAR) processId,b.batch_no batchNo,JSON_UNQUOTE(JSON_EXTRACT(COALESCE(i.material_snapshot,i.product_snapshot),'$.name')) productName,s.process_name processName,b.process_role processRole,b.output_stage outputStage,b.measurement_mode measurementMode,r.loss_unit lossUnit,r.loss_rate_basis lossRateBasis,CAST(r.loss_rate_denominator_weight AS CHAR) lossRateDenominatorWeight,CAST(r.theoretical_loss AS CHAR) theoreticalLoss,CAST(r.loss_weight AS CHAR) lossWeight,CAST(r.issued_weight AS CHAR) issuedWeight,CAST(r.returned_weight AS CHAR) returnedWeight,(SELECT COALESCE(SUM(COALESCE(l.processor_net_weight,l.net_weight)),0) FROM metal_warehouse_order o JOIN metal_warehouse_order_line l ON l.order_id=o.order_id WHERE o.batch_id=r.batch_id AND o.status='POSTED' AND o.type IN ('RAW_PROCESS_IN','SEMI_IN','FINISHED_IN')) outputWeight,r.handover_difference handoverDifference,r.keeper_received_weight keeperReceivedWeight,r.processor_output_weight processorOutputWeight,r.processor_returned_weight processorReturnedWeight,r.status,DATE_FORMAT(r.create_time,'%Y-%m-%d %H:%i:%s') settledTime,CASE WHEN r.loss_unit='PERCENT' THEN CAST(r.loss_rate_percent AS CHAR) ELSE NULL END lossRatePercent FROM metal_work_report r JOIN metal_process_batch b ON b.batch_id=r.batch_id JOIN metal_work_order_item i ON i.item_id=r.item_id JOIN metal_work_order_step s ON s.item_id=r.item_id AND s.process_id=r.process_id WHERE b.work_order_id=? AND b.status='SETTLED' AND r.voided=FALSE ORDER BY r.create_time DESC,r.report_id DESC",work);
        for(var row:result)if(!"PERCENT".equals(row.get("lossUnit"))){var loss=new BigDecimal(row.get("lossWeight").toString());var issued=new BigDecimal(row.get("issuedWeight").toString());var returned=new BigDecimal(row.get("returnedWeight").toString());var net=issued.subtract(returned).add(new BigDecimal(row.get("outputWeight").toString()));row.put("lossRatePercent",ReportMetrics.percentage(loss,net,loss.compareTo(issued.subtract(returned))==0));}
        return result;
    }

    @SuppressWarnings("unchecked")
    private void executionTimes(Long work,List<Map<String,Object>> items){
        var timings=new HashMap<String,Map<String,Object>>();
        for(var row:jdbc.queryForList("SELECT b.item_id,b.process_id,MIN(x.first_issue) firstIssue,MAX(CASE WHEN b.status='SETTLED' AND r.voided=FALSE THEN r.create_time END) lastSettlement,SUM(b.status='OPEN') openCount FROM metal_process_batch b LEFT JOIN (SELECT o.batch_id,MIN(CASE WHEN o.warehouse_flow_id IS NULL THEN o.create_time WHEN f.status='POSTED' THEN f.posted_time END) first_issue FROM metal_warehouse_order o LEFT JOIN metal_warehouse_flow f ON f.flow_id=o.warehouse_flow_id WHERE o.status='POSTED' AND o.type='PRODUCTION_OUT' GROUP BY o.batch_id) x ON x.batch_id=b.batch_id LEFT JOIN metal_work_report r ON r.batch_id=b.batch_id WHERE b.work_order_id=? AND b.status<>'VOID' GROUP BY b.item_id,b.process_id",work))timings.put(row.get("item_id")+":"+row.get("process_id"),row);
        var semi=new SemiFlowService(jdbc,scope);
        for(var item:items)for(var plan:(List<Map<String,Object>>)item.get("plans")){
            plan.put("actualStartTime",null);plan.put("actualEndTime",null);
            var timing=timings.get(item.get("id")+":"+plan.get("processId"));
            if(timing==null||timing.get("firstIssue")==null)continue;
            plan.put("actualStartTime",formatTime(timing.get("firstIssue")));
            var step=new LinkedHashMap<String,Object>();step.put("item_id",item.get("id"));step.put("sequence_no",plan.get("sequenceNo"));step.put("measurement_mode",plan.get("measurementMode"));step.put("weight_stage_closed",plan.get("weightStageClosed"));step.put("quantity",item.get("quantity"));
            if(((Number)timing.get("openCount")).longValue()!=0||timing.get("lastSettlement")==null||!WeightStageProgressService.covered(jdbc,step,semi))continue;
            Object end=timing.get("lastSettlement");
            if("WEIGHT".equals(plan.get("measurementMode"))){var events=jdbc.queryForList("SELECT create_time FROM metal_weight_stage_event WHERE item_id=? AND process_id=? AND action='CLOSE' ORDER BY event_id DESC LIMIT 1",item.get("id"),plan.get("processId"));if(events.isEmpty())continue;if(formatTime(events.get(0).get("create_time")).compareTo(formatTime(end))>0)end=events.get(0).get("create_time");}
            plan.put("actualEndTime",formatTime(end));
        }
    }
    private static String formatTime(Object value){return value instanceof Timestamp t?t.toLocalDateTime().format(TIME):value instanceof LocalDateTime t?t.format(TIME):value.toString();}

    private Map<String, Object> owner(Long factoryId, Long ownerId, boolean lock) {
        var rows = jdbc.queryForList("SELECT COALESCE(NULLIF(nick_name,''),user_name) name FROM sys_user WHERE user_id=? AND factory_id=? AND status='0' AND del_flag='0'" + (lock ? " FOR UPDATE" : ""), ownerId, factoryId);
        if (rows.isEmpty()) throw invalid("负责人必须为本厂有效启用人员");
        return rows.get(0);
    }

    private void writeItems(Long orderId, List<Prepared> items) {
        int sequence = 0;
        for (var item : items) {
            boolean raw=item.input().materialId()!=null;
            long itemId = insert("INSERT INTO metal_work_order_item(work_order_id,sequence_no,product_id,quantity,product_snapshot,material_id,material_snapshot,planned_input_weight) VALUES(?,?,?,?,?,?,?,?)", orderId, ++sequence, item.input().productId(), raw?0:item.input().quantity(), raw?"{}":JSON.toJSONString(item.snapshot()),item.input().materialId(),raw?JSON.toJSONString(item.snapshot()):null,raw?BusinessDecimal.positive(item.input().plannedInputWeight(),"计划投入重量"):null);
            for (int i = 0; i < item.steps().size(); i++) {
                var step = item.steps().get(i);
                var plan = item.input().plans().get(i);
                jdbc.update("INSERT INTO metal_work_order_step(item_id,process_id,sequence_no,process_name,theoretical_loss,start_time,end_time,measurement_mode,loss_unit,process_role,output_stage) VALUES(?,?,?,?,?,?,?,?,?,?,?)", itemId, plan.processId(), i + 1, text(step, "processName"), new BigDecimal(text(step, "loss")), Timestamp.valueOf(plan.startTime()), Timestamp.valueOf(plan.endTime()),SemiMeasurementRules.mode(step.get("measurementMode")),Objects.toString(step.get("lossUnit"),"G_PER_PIECE"),step.get("processRole"),step.get("outputStage"));
            }
        }
    }

    private void review(Long orderId, String action, String from, String to, String reason) {
        jdbc.update("INSERT INTO metal_work_order_review(work_order_id,action,from_status,to_status,reason,actor_id,actor_name) VALUES(?,?,?,?,?,?,?)", orderId, action, from, to, reason, SecurityUtils.getUserId(), actorName());
    }

    static BigDecimal incomingWeight(BigDecimal value,String task,String processing){
        if(value==null)return null;
        if("RAW_PROCESSING".equals(task)&&"DIRECT".equals(processing))throw invalid("工厂自有原料任务不能登记客户来料净重");
        if(value.signum()<0||value.precision()-value.scale()>14||value.scale()>6)throw invalid("客户来料净重须为非负数，最多14位整数、6位小数");
        return BusinessDecimal.round(value);
    }

    static String incomingChange(Object previous,BigDecimal weight,String reason){
        var before=previous==null?null:new BigDecimal(previous.toString());
        if(before==null?weight==null:weight!=null&&before.compareTo(weight)==0)return null;
        if(reason==null||reason.isBlank()||reason.length()>300)throw invalid("请填写来料更正原因，最多300字");
        return "客户来料净重："+(before==null?"未登记":BusinessDecimal.display(before)+"g")+" → "+(weight==null?"未登记":BusinessDecimal.display(weight)+"g")+"；原因："+reason.trim();
    }

    @Transactional(rollbackFor=Exception.class,isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void changeIncomingWeight(Long work,WorkOrderRequest.IncomingWeightChange input){
        if(input==null||input.version()==null||input.version()<0)throw invalid("更正来料净重须提供版本号");
        var initial=header(work,false);
        access(initial);
        scope.lockFactories(List.of(id(initial.get("factoryId"))));
        var current=header(work,true);
        access(current);
        if(!Objects.equals(initial.get("factoryId"),current.get("factoryId")))throw invalid("工单归属已变化，请刷新后重试");
        version(current,input.version());
        var weight=incomingWeight(input.customerIncomingWeight(),text(current,"taskType"),text(current,"processingType"));
        String reason=incomingChange(current.get("customerIncomingWeight"),weight,input.reason());
        if(reason==null)return;
        if(jdbc.update("UPDATE metal_work_order SET customer_incoming_weight=?,version=version+1,update_by=?,update_time=NOW() WHERE work_order_id=? AND version=? AND del_flag='0'",weight,SecurityUtils.getUsername(),work,input.version())!=1)throw invalid("工单已被修改，请刷新后重试");
        review(work,"INCOMING_CHANGE",text(current,"status"),text(current,"status"),reason);
    }

    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    /**
     * 新增记录并保存关联资料。
     */
    public String create(WorkOrderRequest request) {
        if (request.id() != null || request.requestId() == null)
            throw invalid("新增须提供UUID请求标识且不能指定工单ID");
        scope.lockFactories(List.of(request.factoryId()));
        lockLines(List.of(request.productionLineId()));
        line(request.productionLineId(), request.factoryId(), true);
        String reservedNumber = reserveNumber(request.requestId());
        String fingerprint = hash(request);
        var existing = jdbc.queryForList("SELECT work_order_id,request_actor,request_hash,del_flag FROM metal_work_order WHERE request_id=? FOR UPDATE", request.requestId());
        if (!existing.isEmpty()) {
            var row = existing.get(0);
            if (!SecurityUtils.getUserId().equals(id(row.get("request_actor"))) || !fingerprint.equals(row.get("request_hash")))
                throw invalid("请求标识已使用，不能提交不同内容");
            if (!"0".equals(row.get("del_flag"))) throw invalid("原工单已删除，不能重复创建");
            return row.get("work_order_id").toString();
        }
        String task=taskType(request.taskType());
        var customer=customer(request,null);
        requireProcessing(request.processingType(),false);
        rawProcessing(task,request.processingType());
        var incoming=incomingWeight(request.customerIncomingWeight(),task,request.processingType());
        var prepared = prepare(request, List.of(),ProcessingPolicy.EXECUTION_VERSION,task);
        String factory = jdbc.queryForObject("SELECT factory_name FROM metal_factory WHERE factory_id=?", String.class, request.factoryId());
        String lineName = text(line(request.productionLineId(), request.factoryId(), true), "name");
        String ownerName = text(owner(request.factoryId(), request.ownerId(), true), "name");
        long orderId = insert("INSERT INTO metal_work_order(order_no,request_id,request_hash,request_actor,customer,factory_id,production_line_id,owner_id,factory_name,line_name,owner_name,order_date,create_by,creator_name,update_by,status) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'WAITING_MATERIAL')", reservedNumber, request.requestId(), fingerprint, SecurityUtils.getUserId(), customer.get("name"), request.factoryId(), request.productionLineId(), request.ownerId(), factory, lineName, ownerName, java.sql.Date.valueOf(request.orderDate()), SecurityUtils.getUsername(), actorName(), SecurityUtils.getUsername());
        jdbc.update("UPDATE metal_work_order SET handover_policy_version=2 WHERE work_order_id=?",orderId);
        jdbc.update("UPDATE metal_work_order SET auto_semi_release_eligible=?,customer_id=?,customer_version=?,customer_snapshot=?,processing_type=?,task_type=?,processing_policy_version=? WHERE work_order_id=?",autoSemiReleaseEnabled&&"PRODUCTION".equals(task),request.customerId(),customer.get("version"),customer.size()==1?null:JSON.toJSONString(customer),request.processingType(),task,ProcessingPolicy.EXECUTION_VERSION,orderId);
        jdbc.update("UPDATE metal_work_order SET loss_rate_basis='OUTBOUND_TOTAL' WHERE work_order_id=?",orderId);
        jdbc.update("UPDATE metal_work_order SET customer_incoming_weight=? WHERE work_order_id=?",incoming,orderId);
        writeItems(orderId, prepared);
        review(orderId, "CREATE", null, "WAITING_MATERIAL", null);
        return Long.toString(orderId);
    }

    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    /**
     * 修改记录并更新关联资料。
     */
    public void edit(WorkOrderRequest request) {
        if (request.id() == null || request.version() == null) throw invalid("编辑须提供ID和版本号");
        var initial = header(request.id(), false);
        access(initial);
        if (!request.factoryId().equals(id(initial.get("factoryId")))) scope.adminOnly();
        scope.lockFactories(Arrays.asList(id(initial.get("factoryId")), request.factoryId()));
        lockLines(Arrays.asList(id(initial.get("productionLineId")), request.productionLineId()));
        lockDependencies(request.id(), request.items().stream().map(WorkOrderRequest.Item::productId).toList(), request.ownerId());
        var current = header(request.id(), true);
        access(current);
        version(current, request.version());
        editable(current);
        String task=taskType(request.taskType()==null?text(current,"taskType"):request.taskType());
        if(!Objects.equals(task,taskType(text(current,"taskType"))))throw invalid("任务类型创建后不能更改");
        var customer=customer(request,current);
        String processing=request.processingType()==null?text(current,"processingType"):request.processingType();
        requireProcessing(processing,current.get("processingType")==null);
        rawProcessing(task,processing);
        var incoming=incomingWeight(request.customerIncomingWeight(),task,processing);
        String incomingReason=incomingChange(current.get("customerIncomingWeight"),incoming,request.incomingWeightChangeReason());
        int policy=current.get("processingPolicyVersion")==null?1:((Number)current.get("processingPolicyVersion")).intValue();
        var prepared = prepare(request, items(request.id()),policy,task);
        String factory = request.factoryId().equals(id(current.get("factoryId"))) ? text(current, "factoryName") : jdbc.queryForObject("SELECT factory_name FROM metal_factory WHERE factory_id=?", String.class, request.factoryId());
        String lineName = request.productionLineId().equals(id(current.get("productionLineId"))) ? text(current, "lineName") : text(line(request.productionLineId(), request.factoryId(), true), "name");
        String ownerName = request.ownerId().equals(id(current.get("ownerId"))) ? text(current, "ownerName") : text(owner(request.factoryId(), request.ownerId(), true), "name");
        if (jdbc.update("UPDATE metal_work_order SET customer=?,factory_id=?,production_line_id=?,owner_id=?,factory_name=?,line_name=?,owner_name=?,order_date=?,version=version+1,update_by=?,update_time=NOW() WHERE work_order_id=? AND version=? AND del_flag='0'", customer.get("name"), request.factoryId(), request.productionLineId(), request.ownerId(), factory, lineName, ownerName, java.sql.Date.valueOf(request.orderDate()), SecurityUtils.getUsername(), request.id(), request.version()) != 1)
            throw invalid("工单已被修改，请刷新后重试");
        jdbc.update("UPDATE metal_work_order SET customer_id=?,customer_version=?,customer_snapshot=?,processing_type=? WHERE work_order_id=?",request.customerId(),customer.get("version"),customer.size()==1?null:JSON.toJSONString(customer),processing,request.id());
        jdbc.update("UPDATE metal_work_order SET customer_incoming_weight=? WHERE work_order_id=?",incoming,request.id());
        jdbc.update("DELETE s FROM metal_work_order_step s JOIN metal_work_order_item i ON i.item_id=s.item_id WHERE i.work_order_id=?", request.id());
        jdbc.update("DELETE FROM metal_work_order_item WHERE work_order_id=?", request.id());
        writeItems(request.id(), prepared);
        review(request.id(), "EDIT", text(current, "status"), text(current, "status"), null);
        if(incomingReason!=null)review(request.id(),"INCOMING_CHANGE",text(current,"status"),text(current,"status"),incomingReason);
    }

    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    /**
     * 校验版本及当前状态后执行工单审核、驳回或重新提交。
     */
    public void transition(Long orderId, WorkOrderRequest.Action input, String action) {
        access(header(orderId, false));
        throw new ServiceException("工单审批流程已停用，新建工单可直接领料", 410);
    }

    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    /**
     * 批量删除记录；业务层校验访问权限和引用限制。
     */
    public void delete(Long[] ids) {
        if (ids == null || ids.length < 1 || ids.length > 100 || Arrays.stream(ids).anyMatch(v -> v == null || v <= 0))
            throw invalid("请选择1至100张工单");
        var sorted = Arrays.stream(ids).distinct().sorted().toList();
        var initial = sorted.stream().map(i -> header(i, false)).toList();
        initial.forEach(this::access);
        scope.lockFactories(initial.stream().map(r -> id(r.get("factoryId"))).toList());
        lockLines(initial.stream().map(r -> id(r.get("productionLineId"))).toList());
        var locked = sorted.stream().map(i -> header(i, true)).toList();
        locked.forEach(this::access);
        locked.forEach(this::editable);
        for (var order : locked) {
            Long key = id(order.get("id"));
            jdbc.update("UPDATE metal_work_order SET del_flag='2',version=version+1,update_by=?,update_time=NOW() WHERE work_order_id=?", SecurityUtils.getUsername(), key);
            review(key, "DELETE", text(order, "status"), text(order, "status"), null);
        }
    }

    /**
     * 获取当前用户可访问的工厂选项。
     */
    public List<Map<String, Object>> factories() {
        Long factory = scope.currentFactory();
        return factory == null ? jdbc.queryForList("SELECT CAST(factory_id AS CHAR) id,factory_name name FROM metal_factory WHERE del_flag='0' ORDER BY factory_id") : jdbc.queryForList("SELECT CAST(factory_id AS CHAR) id,factory_name name FROM metal_factory WHERE factory_id=? AND del_flag='0'", factory);
    }

    /**
     * 获取指定工厂的产线选项。
     */
    public List<Map<String, Object>> lines(Long factoryId) {
        scope.check(factoryId);
        return jdbc.queryForList("SELECT CAST(production_line_id AS CHAR) id,production_line_name name FROM metal_production_line WHERE factory_id=? ORDER BY production_line_id", factoryId);
    }

    /**
     * 获取指定工厂的负责人选项。
     */
    public List<Map<String, Object>> owners(Long factoryId) {
        scope.check(factoryId);
        return jdbc.queryForList("SELECT CAST(u.user_id AS CHAR) id,COALESCE(NULLIF(u.nick_name,''),u.user_name) name,COALESCE(u.phonenumber,'') phone,COALESCE((SELECT GROUP_CONCAT(DISTINCT r.role_name ORDER BY r.role_name SEPARATOR '、') FROM sys_user_role ur JOIN sys_role r ON r.role_id=ur.role_id WHERE ur.user_id=u.user_id AND r.factory_id=u.factory_id AND r.del_flag='0' AND r.status='0'),'') roleNames FROM sys_user u WHERE u.factory_id=? AND u.del_flag='0' AND u.status='0' ORDER BY u.user_id", factoryId);
    }

    /**
     * 查询所选产线关联的有效产品。
     */
    public List<Map<String, Object>> availableProducts(Long lineId) {
        line(lineId, null, false);
        return jdbc.queryForList("SELECT CAST(p.product_id AS CHAR) id,p.product_name name,p.product_category category,p.version version,CAST(p.metal_type_id AS CHAR) metalTypeId,EXISTS(SELECT 1 FROM metal_product_process pp WHERE pp.product_id=p.product_id) hasProcesses FROM metal_line_product lp JOIN metal_product p ON p.product_id=lp.product_id JOIN metal_type t ON t.metal_type_id=p.metal_type_id WHERE lp.production_line_id=? AND p.del_flag='0' AND p.factory_id=t.factory_id AND p.factory_id=(SELECT factory_id FROM metal_production_line WHERE production_line_id=lp.production_line_id) ORDER BY p.product_id", lineId);
    }

    public List<Map<String,Object>> availableMaterials(Long factoryId){
        scope.check(factoryId);
        return jdbc.queryForList("SELECT CAST(m.material_id AS CHAR) id,m.material_code code,CONCAT(t.name,m.material_type) name,m.material_type materialType,m.specification,CAST(m.metal_type_id AS CHAR) metalTypeId,t.name metalTypeName FROM metal_material m JOIN metal_type t ON t.metal_type_id=m.metal_type_id WHERE m.del_flag='0' AND m.factory_id=? AND t.factory_id=m.factory_id ORDER BY m.material_id",factoryId);
    }

    public List<Map<String,Object>> rawProcesses(Long factoryId){
        scope.check(factoryId);
        var result=new ArrayList<Map<String,Object>>();
        for(var process:jdbc.queryForList("SELECT CAST(p.process_id AS CHAR) id,p.process_name name,t.type_name typeName FROM metal_process p JOIN metal_process_type t ON t.type_id=p.process_type WHERE p.del_flag='0' AND p.factory_id=? AND t.factory_id=p.factory_id ORDER BY p.process_id",factoryId)){
            try{ProcessingPolicy.route("RAW_PROCESSING",List.of(text(process,"typeName")),List.of("WEIGHT"));result.add(process);}
            catch(ServiceException ignored){}
        }
        return result;
    }

    /**
     * 读取所选产线产品的工序及理论损耗配置。
     */
    public Map<String, Object> productPlans(Long productId, Long lineId) {
        line(lineId, null, false);
        if (jdbc.queryForList("SELECT p.product_id FROM metal_line_product lp JOIN metal_product p ON p.product_id=lp.product_id WHERE lp.production_line_id=? AND lp.product_id=? AND p.del_flag='0'", lineId, productId).isEmpty())
            throw invalid("产品不属于所选产线");
        var product = products.detail(productId);
        var lineFactory=jdbc.queryForObject("SELECT factory_id FROM metal_production_line WHERE production_line_id=?",Long.class,lineId);
        scope.requireCatalog("product",productId,lineFactory);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("snapshot", snapshot(product));
        result.put("version",product.getVersion());
        List<Map<String, Object>> steps = new ArrayList<>();
        for (var step : product.getProcesses()) {
            scope.requireCatalog("process",step.getProcessId(),lineFactory);
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("processId", step.getProcessId().toString());
            value.put("processName", step.getProcessName());
            value.put("loss", step.getLoss().toPlainString());
            value.put("measurementMode",step.getMeasurementMode());
            value.put("lossUnit",step.getLossUnit());
            value.put("startTime", null);
            value.put("endTime", null);
            steps.add(value);
        }
        if(!steps.isEmpty())route(steps,"PRODUCTION");
        result.put("plans", steps);
        return result;
    }
}
