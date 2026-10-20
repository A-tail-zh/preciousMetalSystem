package com.ruoyi.metal.service;

import java.util.*;
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
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String HEADER = "SELECT CAST(work_order_id AS CHAR) id,order_no orderNo,customer,CAST(factory_id AS CHAR) factoryId,CAST(production_line_id AS CHAR) productionLineId,CAST(owner_id AS CHAR) ownerId,factory_name factoryName,line_name lineName,owner_name ownerName,DATE_FORMAT(order_date,'%Y-%m-%d') orderDate,status,reject_reason rejectReason,create_by createBy,creator_name creatorName,DATE_FORMAT(create_time,'%Y-%m-%d %H:%i:%s') createTime,version,(EXISTS(SELECT 1 FROM metal_process_batch b WHERE b.work_order_id=metal_work_order.work_order_id) OR EXISTS(SELECT 1 FROM metal_warehouse_order o WHERE o.work_order_id=metal_work_order.work_order_id) OR EXISTS(SELECT 1 FROM metal_work_report r JOIN metal_work_order_item i ON i.item_id=r.item_id WHERE i.work_order_id=metal_work_order.work_order_id)) hasBusinessReferences FROM metal_work_order WHERE work_order_id=? AND del_flag='0'";

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
    public Map<String, Object> detail(Long id) {
        var order = header(id, false);
        access(order);
        capabilities(order);
        order.put("items", items(id));
        order.put("reviews", jdbc.queryForList("SELECT CAST(review_id AS CHAR) id,action,from_status fromStatus,to_status toStatus,reason,CAST(actor_id AS CHAR) actorId,actor_name actorName,DATE_FORMAT(create_time,'%Y-%m-%d %H:%i:%s') createTime FROM metal_work_order_review WHERE work_order_id=? ORDER BY review_id", id));
        order.put("reports", List.of());
        order.put("reportingAvailable", false);
        return order;
    }

    private List<Map<String, Object>> items(Long orderId) {
        var result = jdbc.queryForList("SELECT CAST(item_id AS CHAR) id,CAST(product_id AS CHAR) productId,sequence_no sequenceNo,quantity,CAST(product_snapshot AS CHAR) snapshot FROM metal_work_order_item WHERE work_order_id=? ORDER BY sequence_no", orderId);
        for (var item : result) {
            item.put("snapshot", JSON.parseObject(text(item, "snapshot")));
            item.put("plans", jdbc.queryForList("SELECT CAST(process_id AS CHAR) processId,sequence_no sequenceNo,process_name processName,CAST(theoretical_loss AS CHAR) loss,DATE_FORMAT(start_time,'%Y-%m-%d %H:%i:%s') startTime,DATE_FORMAT(end_time,'%Y-%m-%d %H:%i:%s') endTime,DATE_FORMAT(actual_start_time,'%Y-%m-%d %H:%i:%s') actualStartTime,DATE_FORMAT(actual_end_time,'%Y-%m-%d %H:%i:%s') actualEndTime FROM metal_work_order_step WHERE item_id=? ORDER BY sequence_no", id(item.get("id"))));
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
        Set<Long> productIds = new TreeSet<>(additional);
        for (var row : jdbc.queryForList("SELECT product_id FROM metal_work_order_item WHERE work_order_id=?", orderId))
            productIds.add(id(row.get("product_id")));
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
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(request.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, Object> snapshot(ProductRecord product) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("code", product.getCode());
        value.put("name", product.getName());
        value.put("category", product.getCategory());
        value.put("specification", product.getSpecification());
        value.put("dimensions", product.getDimensions());
        value.put("dimensionUnit", "cm");
        value.put("volume", product.getVolume() == null ? null : product.getVolume().toPlainString());
        value.put("purity", product.getPurity() == null ? null : product.getPurity().toPlainString());
        value.put("weight", product.getWeight() == null ? null : product.getWeight().toPlainString());
        return value;
    }

    private record Prepared(Map<String, Object> snapshot, List<Map<String, Object>> steps,
                            WorkOrderRequest.Item input) {
    }

    @SuppressWarnings("unchecked")
    private List<Prepared> prepare(WorkOrderRequest request, List<Map<String, Object>> previous) {
        line(request.productionLineId(), request.factoryId(), true);
        if (request.orderDate().getYear() < 1000 || request.orderDate().getYear() > 9999)
            throw invalid("开单日期超出支持范围");
        Map<Long, Map<String, Object>> old = new HashMap<>();
        previous.forEach(item -> old.put(id(item.get("id")), item));
        Set<Long> used = new HashSet<>();
        for (Long productId : request.items().stream().map(WorkOrderRequest.Item::productId).distinct().sorted().toList())
            if (jdbc.queryForList("SELECT product_id FROM metal_product WHERE product_id=? AND del_flag='0' FOR UPDATE", productId).isEmpty())
                throw invalid("产品不存在或已删除");
        List<Prepared> result = new ArrayList<>();
        Set<Long> processIds = new TreeSet<>();
        for (var input : request.items()) {
            if (input.id() != null && (!old.containsKey(input.id()) || !used.add(input.id())))
                throw new ServiceException("产品明细不属于本工单或重复", 403);
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
                    s.put("loss", step.getLoss().toPlainString());
                    steps.add(s);
                }
            }
            if (steps.isEmpty()) throw invalid("产品尚未配置工序，请先维护产品工艺");
            validatePlans(input.plans(), steps.stream().map(s -> id(s.get("processId"))).toList());
            steps.forEach(s -> processIds.add(id(s.get("processId"))));
            result.add(new Prepared(productSnapshot, steps, input));
        }
        for (Long processId : processIds)
            if (jdbc.queryForList("SELECT process_id FROM metal_process WHERE process_id=? AND del_flag='0' FOR UPDATE", processId).isEmpty())
                throw invalid("工序不存在或已删除");
        owner(request.factoryId(), request.ownerId(), true);
        return result;
    }

    /**
     * 校验工序计划与产品工序配置的匹配关系。
     */
    public static void validatePlans(List<WorkOrderRequest.Plan> plans, List<Long> expected) {
        if (plans == null || plans.size() != expected.size())
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

    private Map<String, Object> owner(Long factoryId, Long ownerId, boolean lock) {
        var rows = jdbc.queryForList("SELECT COALESCE(NULLIF(nick_name,''),user_name) name FROM sys_user WHERE user_id=? AND factory_id=? AND status='0' AND del_flag='0'" + (lock ? " FOR UPDATE" : ""), ownerId, factoryId);
        if (rows.isEmpty()) throw invalid("负责人必须为本厂有效启用人员");
        return rows.get(0);
    }

    private void writeItems(Long orderId, List<Prepared> items) {
        int sequence = 0;
        for (var item : items) {
            long itemId = insert("INSERT INTO metal_work_order_item(work_order_id,sequence_no,product_id,quantity,product_snapshot) VALUES(?,?,?,?,?)", orderId, ++sequence, item.input().productId(), item.input().quantity(), JSON.toJSONString(item.snapshot()));
            for (int i = 0; i < item.steps().size(); i++) {
                var step = item.steps().get(i);
                var plan = item.input().plans().get(i);
                jdbc.update("INSERT INTO metal_work_order_step(item_id,process_id,sequence_no,process_name,theoretical_loss,start_time,end_time) VALUES(?,?,?,?,?,?,?)", itemId, plan.processId(), i + 1, text(step, "processName"), new BigDecimal(text(step, "loss")), Timestamp.valueOf(plan.startTime()), Timestamp.valueOf(plan.endTime()));
            }
        }
    }

    private void review(Long orderId, String action, String from, String to, String reason) {
        jdbc.update("INSERT INTO metal_work_order_review(work_order_id,action,from_status,to_status,reason,actor_id,actor_name) VALUES(?,?,?,?,?,?,?)", orderId, action, from, to, reason, SecurityUtils.getUserId(), actorName());
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
        var prepared = prepare(request, List.of());
        String factory = jdbc.queryForObject("SELECT factory_name FROM metal_factory WHERE factory_id=?", String.class, request.factoryId());
        String lineName = text(line(request.productionLineId(), request.factoryId(), true), "name");
        String ownerName = text(owner(request.factoryId(), request.ownerId(), true), "name");
        long orderId = insert("INSERT INTO metal_work_order(order_no,request_id,request_hash,request_actor,customer,factory_id,production_line_id,owner_id,factory_name,line_name,owner_name,order_date,create_by,creator_name,update_by,status) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'WAITING_MATERIAL')", reservedNumber, request.requestId(), fingerprint, SecurityUtils.getUserId(), request.customer().strip(), request.factoryId(), request.productionLineId(), request.ownerId(), factory, lineName, ownerName, java.sql.Date.valueOf(request.orderDate()), SecurityUtils.getUsername(), actorName(), SecurityUtils.getUsername());
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
        var prepared = prepare(request, items(request.id()));
        String factory = request.factoryId().equals(id(current.get("factoryId"))) ? text(current, "factoryName") : jdbc.queryForObject("SELECT factory_name FROM metal_factory WHERE factory_id=?", String.class, request.factoryId());
        String lineName = request.productionLineId().equals(id(current.get("productionLineId"))) ? text(current, "lineName") : text(line(request.productionLineId(), request.factoryId(), true), "name");
        String ownerName = request.ownerId().equals(id(current.get("ownerId"))) ? text(current, "ownerName") : text(owner(request.factoryId(), request.ownerId(), true), "name");
        if (jdbc.update("UPDATE metal_work_order SET customer=?,factory_id=?,production_line_id=?,owner_id=?,factory_name=?,line_name=?,owner_name=?,order_date=?,version=version+1,update_by=?,update_time=NOW() WHERE work_order_id=? AND version=? AND del_flag='0'", request.customer().strip(), request.factoryId(), request.productionLineId(), request.ownerId(), factory, lineName, ownerName, java.sql.Date.valueOf(request.orderDate()), SecurityUtils.getUsername(), request.id(), request.version()) != 1)
            throw invalid("工单已被修改，请刷新后重试");
        jdbc.update("DELETE s FROM metal_work_order_step s JOIN metal_work_order_item i ON i.item_id=s.item_id WHERE i.work_order_id=?", request.id());
        jdbc.update("DELETE FROM metal_work_order_item WHERE work_order_id=?", request.id());
        writeItems(request.id(), prepared);
        review(request.id(), "EDIT", text(current, "status"), text(current, "status"), null);
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
        return jdbc.queryForList("SELECT CAST(p.product_id AS CHAR) id,p.product_name name,p.product_category category,EXISTS(SELECT 1 FROM metal_product_process pp WHERE pp.product_id=p.product_id) hasProcesses FROM metal_line_product lp JOIN metal_product p ON p.product_id=lp.product_id WHERE lp.production_line_id=? AND p.del_flag='0' ORDER BY p.product_id", lineId);
    }

    /**
     * 读取所选产线产品的工序及理论损耗配置。
     */
    public Map<String, Object> productPlans(Long productId, Long lineId) {
        line(lineId, null, false);
        if (jdbc.queryForList("SELECT p.product_id FROM metal_line_product lp JOIN metal_product p ON p.product_id=lp.product_id WHERE lp.production_line_id=? AND lp.product_id=? AND p.del_flag='0'", lineId, productId).isEmpty())
            throw invalid("产品不属于所选产线");
        var product = products.detail(productId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("snapshot", snapshot(product));
        List<Map<String, Object>> steps = new ArrayList<>();
        for (var step : product.getProcesses()) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("processId", step.getProcessId().toString());
            value.put("processName", step.getProcessName());
            value.put("loss", step.getLoss().toPlainString());
            value.put("startTime", null);
            value.put("endTime", null);
            steps.add(value);
        }
        result.put("plans", steps);
        return result;
    }
}
