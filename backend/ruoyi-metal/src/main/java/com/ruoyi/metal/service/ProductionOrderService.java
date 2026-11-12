package com.ruoyi.metal.service;

import java.math.BigDecimal;
import java.sql.Statement;
import java.time.*;
import java.time.format.*;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.metal.security.FactoryScope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class ProductionOrderService {
    public record Step(@NotNull @Positive Long processId, String start, String end) {}
    public record Item(@NotNull @Positive Long productId, @NotNull @Positive Integer quantity,
                       @NotNull @Size(max=500) List<@NotNull @Valid Step> processes) {}
    public record Create(@Positive Long factoryId, @NotBlank @Size(max=36) String requestId,
                         @Positive Long customerId, @NotBlank String processingType, String weight,
                         @NotNull @Positive Long lineId, @NotBlank String orderDate,
                         @NotNull @Positive Long ownerId, @NotEmpty @Size(max=100) List<@NotNull @Valid Item> products,
                         @Size(max=500) List<Map<String,Object>> arrivals) {
        public Create(Long factoryId,String requestId,Long customerId,String processingType,String weight,Long lineId,String orderDate,Long ownerId,List<Item> products){this(factoryId,requestId,customerId,processingType,weight,lineId,orderDate,ownerId,products,null);}
    }
    public record Edit(@NotNull @PositiveOrZero Integer version, @NotNull @Valid Create data) {}
    public record Target(@NotNull @Positive Long id,@NotNull @PositiveOrZero Integer version) {}
    public record Delete(@Positive Long factoryId,@NotBlank String requestId,@NotEmpty @Size(max=100) List<@NotNull @Valid Target> orders) {}
    public record Page(List<Map<String,Object>> rows, long total) {}
    private final JdbcTemplate jdbc;
    private final FactoryScope scope;
    @org.springframework.beans.factory.annotation.Autowired private StorageOrderService storageOrders;
    private static final DateTimeFormatter TIME=DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);
    private static final String HEADER="SELECT CAST(id AS CHAR) id,order_no number,CAST(factory_id AS CHAR) factoryId,factory_name factory,customer_name customer,line_name line,owner_name owner,CAST(CAST(incoming_weight AS DECIMAL(20,2)) AS CHAR) weight,status,version,deleted,flow_version flowVersion,first_issue_confirmed_at firstIssueConfirmedAt,creator_name creator,DATE_FORMAT(create_time,'%Y-%m-%d %H:%i:%s') createdAt FROM metal_production_order";
    public ProductionOrderService(JdbcTemplate jdbc,FactoryScope scope){this.jdbc=jdbc;this.scope=scope;}
    private void allow(String action){if(!scope.isAdmin()&&!SecurityUtils.hasPermi("metal:work-order:"+action))throw new ServiceException("没有工单操作权限",403);}
    public Page list(Long requested,int page,int size){
        allow("list");Long factory=scope.resolveFactory(requested);
        if(page<1||size<1||size>64)throw new ServiceException("分页参数无效",400);
        return new Page(jdbc.queryForList(HEADER+" WHERE factory_id=? AND deleted=0 ORDER BY create_time DESC,id DESC LIMIT ? OFFSET ?",factory,size,((long)page-1)*size).stream().map(r->permissions(r,factory)).toList(),
                jdbc.queryForObject("SELECT COUNT(*) FROM metal_production_order WHERE factory_id=? AND deleted=0",Long.class,factory));
    }
    public Map<String,Object> detail(Long id,Long requested){allow("query");return read(id,scope.resolveFactory(requested));}
    public Map<String,Object> submission(String requestId,Long requested){
        allow("add");Long factory=scope.resolveFactory(requested);validRequestId(requestId);
        var rows=jdbc.queryForList(HEADER+" WHERE factory_id=? AND request_actor=? AND request_id=?",factory,SecurityUtils.getUserId(),requestId);
        if(rows.isEmpty())throw new ServiceException("尚未查到此次提交的工单",404);return rows.get(0);
    }
    private Map<String,Object> read(Long id,Long factory){
        var rows=jdbc.queryForList(HEADER+" WHERE id=? AND factory_id=? AND deleted=0",id,factory);
        if(rows.isEmpty())throw new ServiceException("工单不存在或不属于当前工厂",404);
        var row=permissions(rows.get(0),factory);
        var fields=jdbc.queryForMap("SELECT CAST(customer_id AS CHAR) customerId,processing_type processingType,CAST(production_line_id AS CHAR) lineId,CAST(owner_id AS CHAR) ownerId,DATE_FORMAT(order_date,'%Y-%m-%d') orderDate,CAST(customer_snapshot AS CHAR) customerSnapshot,CAST(arrivals_snapshot AS CHAR) arrivalsSnapshot FROM metal_production_order WHERE id=? AND factory_id=?",id,factory);
        var snapshot=fields.remove("customerSnapshot");fields.put("customerInfo",snapshot==null?null:JSON.parseObject(snapshot.toString()));row.putAll(fields);Object arrivals=row.remove("arrivalsSnapshot");row.put("arrivals",arrivals==null?List.of():JSON.parseArray(arrivals.toString(),Map.class));
        var items=jdbc.queryForList("SELECT CAST(id AS CHAR) id,CAST(product_id AS CHAR) productId,quantity,CAST(product_snapshot AS CHAR) snapshot FROM metal_production_order_item WHERE order_id=? ORDER BY sequence_no",id);
        for(var item:items){item.put("product",JSON.parseObject(item.remove("snapshot").toString()));item.put("processes",jdbc.queryForList("SELECT CAST(id AS CHAR) id,CAST(id AS CHAR) orderStepId,sequence_no sequenceNo,semantic_code semanticCode,output_category outputCategory,output_form outputForm,CAST(output_material_id AS CHAR) outputMaterialId,measurement_mode measurementMode,CAST(process_id AS CHAR) processId,process_name processName,CAST(process_snapshot AS CHAR) snapshot,DATE_FORMAT(start_time,'%Y-%m-%d %H:%i:%s') start,DATE_FORMAT(end_time,'%Y-%m-%d %H:%i:%s') end FROM metal_production_order_step WHERE item_id=? ORDER BY sequence_no",item.get("id")));}
        row.put("products",items);return row;
    }
    static void validRequestId(String value){if(value==null||!value.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}"))throw new ServiceException("提交标识无效",400);}
    public static BigDecimal weight(String value,String processingType){
        if(processingType==null||!Set.of("MATERIAL_CUSTOM","DIRECT_CUSTOM","SHORTAGE_CUSTOM").contains(processingType))throw new ServiceException("加工类型无效",400);
        if(value==null||value.isBlank())return null;
        if(!processingType.equals("MATERIAL_CUSTOM")||!value.strip().matches("\\d{1,14}(\\.\\d{1,2})?"))throw new ServiceException("来料净重仅限来料定制，须为非负数，最多14位整数和2位小数",400);
        return new BigDecimal(value.strip()).setScale(2);
    }
    public static LocalDateTime time(String value){
        if(value==null||value.isBlank())return null;
        try{LocalDateTime t=LocalDateTime.parse(value,TIME);if(t.getYear()<1000||t.getYear()>9999)throw new IllegalArgumentException();return t;}
        catch(Exception e){throw new ServiceException("工序时间格式无效",400);}
    }
    static LocalDate date(String value){try{LocalDate date=LocalDate.parse(value);if(date.getYear()<1000||date.getYear()>9999)throw new IllegalArgumentException();return date;}catch(Exception e){throw new ServiceException("开单日期无效",400);}}
    private Map<String,Object> one(String sql,Object...args){var rows=jdbc.queryForList(sql,args);if(rows.isEmpty())throw new ServiceException("关联资料已失效或不属于当前工厂，请刷新后重新选择",409);return rows.get(0);}
    private long insert(String sql,Object...args){var key=new GeneratedKeyHolder();jdbc.update(c->{var s=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);return s;},key);return Objects.requireNonNull(key.getKey()).longValue();}
    public Map<String,Object> editContext(Long id,Long requested){allow("edit");return read(id,scope.resolveFactory(requested));}
    public Map<String,Object> operation(String requestId,Long requested,String action){
        if(!Set.of("edit","remove").contains(action))throw new ServiceException("操作无效",400);
        allow(action);Long factory=scope.resolveFactory(requested);validRequestId(requestId);
        var rows=jdbc.queryForList("SELECT CAST(result AS CHAR) result FROM metal_production_order_operation WHERE factory_id=? AND actor_id=? AND request_id=? AND action=?",factory,SecurityUtils.getUserId(),requestId.toLowerCase(Locale.ROOT),action);
        if(rows.isEmpty())throw new ServiceException("尚未查到此次操作",404);
        return JSON.parseObject(rows.get(0).get("result").toString());
    }
    private Map<String,Object> replay(Long factory,String requestId,String action,String hash){
        var rows=jdbc.queryForList("SELECT action,request_hash,CAST(result AS CHAR) result FROM metal_production_order_operation WHERE factory_id=? AND actor_id=? AND request_id=? FOR UPDATE",factory,SecurityUtils.getUserId(),requestId.toLowerCase(Locale.ROOT));
        if(rows.isEmpty())return null;
        if(!action.equals(rows.get(0).get("action"))||!hash.equals(rows.get(0).get("request_hash")))throw new ServiceException("同一请求标识的内容已改变",409);
        var result=JSON.parseObject(rows.get(0).get("result").toString());result.put("replayed",true);return result;
    }
    private void remember(Long factory,String requestId,String action,String hash,Map<String,Object> result){jdbc.update("INSERT INTO metal_production_order_operation(factory_id,actor_id,request_id,action,request_hash,result) VALUES(?,?,?,?,?,?)",factory,SecurityUtils.getUserId(),requestId.toLowerCase(Locale.ROOT),action,hash,JSON.toJSONString(result));}
    private void lockEditable(Long id,Long factory,Integer version){
        var row=one("SELECT version,status,deleted,first_issue_confirmed_at,flow_version FROM metal_production_order WHERE id=? AND factory_id=? FOR UPDATE",id,factory);
        if(((Number)row.get("deleted")).intValue()!=0||!"CREATED".equals(row.get("status")))throw new ServiceException("工单已删除或当前状态不允许修改",409);
        if(row.get("first_issue_confirmed_at")!=null)throw new ServiceException("工单已实际领料，不能编辑或删除",409);
        if(!jdbc.queryForList("SELECT id FROM metal_production_batch WHERE factory_id=? AND order_id=? FOR UPDATE",factory,id).isEmpty())throw new ServiceException("工单已有统一生产批次，产品与冻结路线不能编辑或删除",409);
        if(jdbc.queryForObject("SELECT COUNT(*) FROM metal_storage_document WHERE order_id=? AND factory_id=? AND type<>'CUSTOMER_IN' AND deleted=0 AND status IN ('DRAFT','PENDING_REVIEW','RETURNED')",Integer.class,id,factory)>0)throw new ServiceException("工单有待处理的领料或加工单据，请先撤销后修改",409);
        if(version==null||version<0||((Number)row.get("version")).intValue()!=version)throw new ServiceException("工单已被修改，请重新打开核对",409);
    }
    @Transactional(rollbackFor=Exception.class)
    public Map<String,Object> delete(Delete input){
        allow("remove");Long factory=scope.resolveFactory(input.factoryId());validRequestId(input.requestId());scope.lockFactories(List.of(factory));
        String hash=java.util.HexFormat.of().formatHex(sha256(JSON.toJSONBytes(input)));
        var previous=replay(factory,input.requestId(),"remove",hash);if(previous!=null)return previous;
        if(input.orders()==null||input.orders().isEmpty()||input.orders().size()>100)throw new ServiceException("请选择1至100张工单",400);
        Set<Long> ids=new TreeSet<>();for(var target:input.orders()){if(target==null||target.id()==null||!ids.add(target.id()))throw new ServiceException("工单列表无效",400);lockEditable(target.id(),factory,target.version());}
        for(Long id:ids){if(jdbc.queryForObject("SELECT COUNT(*) FROM metal_storage_document WHERE factory_id=? AND order_id=? AND status='POSTED'",Integer.class,factory,id)>0)throw new ServiceException("工单已有真实入库，须先冲销关联单据，不能直接删除",409);storageOrders.cancelUnpostedArrivals(factory,id);}
        for(Long id:ids)jdbc.update("UPDATE metal_production_order SET deleted=1,version=version+1,update_by=?,update_time=NOW() WHERE id=? AND factory_id=?",SecurityUtils.getUsername(),id,factory);
        Map<String,Object> result=Map.of("ids",ids.stream().map(String::valueOf).toList(),"deleted",true);remember(factory,input.requestId(),"remove",hash,result);return result;
    }
    @Transactional(rollbackFor=Exception.class)
    public Map<String,Object> create(Create input){return save(input,null,null);}
    @Transactional(rollbackFor=Exception.class)
    public Map<String,Object> edit(Long id,Edit input){return save(input.data(),id,input.version());}
    private Map<String,Object> save(Create input,Long editing,Integer version){
        allow(editing==null?"add":"edit");Long factory=scope.resolveFactory(input.factoryId());validRequestId(input.requestId());
        BigDecimal net=weight(input.weight(),input.processingType());LocalDate orderDate=date(input.orderDate());
        boolean material="MATERIAL_CUSTOM".equals(input.processingType());
        boolean live=!material||editing==null||input.arrivals()!=null;
        if(!material&&input.arrivals()!=null&&!input.arrivals().isEmpty())throw new ServiceException("直接定制和欠料定制不填写初始来料明细",400);
        if(live&&input.customerId()==null)throw new ServiceException("生产工单必须选择客户",400);
        List<Map<String,Object>> arrivals=material&&live?normalizeArrivals(input.arrivals()):List.of();
        if(material&&live){BigDecimal sum=arrivals.stream().map(a->new BigDecimal(a.get("netWeight").toString())).reduce(BigDecimal.ZERO,BigDecimal::add);if(net!=null&&net.compareTo(sum)!=0)throw new ServiceException("客户来料总净重必须等于明细合计",400);net=sum.setScale(2);}
        if(input.products()==null||input.products().isEmpty()||input.products().size()>100)throw new ServiceException("请选择1至100组产品",400);
        var normalizedItems=new ArrayList<Item>();
        for(Item item:input.products()){
            if(item==null||item.quantity()==null||item.quantity()<1||item.productId()==null||item.processes()==null||item.processes().size()>500)throw new ServiceException("产品数量或工序配置无效",400);
            var normalizedSteps=new ArrayList<Step>();
            for(Step step:item.processes()){
                if(step==null||step.processId()==null)throw new ServiceException("工序标识无效",400);
                LocalDateTime start=time(step.start()),end=time(step.end());
                if(start!=null&&end!=null&&end.isBefore(start))throw new ServiceException("工序结束时间不得早于开始时间",400);
                normalizedSteps.add(new Step(step.processId(),start==null?null:start.format(TIME),end==null?null:end.format(TIME)));
            }
            normalizedItems.add(new Item(item.productId(),item.quantity(),normalizedSteps));
        }
        var normalized=new Create(factory,input.requestId().toLowerCase(Locale.ROOT),input.customerId(),input.processingType(),net==null?null:net.toPlainString(),input.lineId(),orderDate.toString(),input.ownerId(),normalizedItems,arrivals);
        String hash=java.util.HexFormat.of().formatHex(sha256(JSON.toJSONBytes(editing==null?normalized:List.of(editing,version,normalized))));
        scope.lockFactories(List.of(factory));Long actor=SecurityUtils.getUserId();
        if(editing!=null){var previous=replay(factory,input.requestId(),"edit",hash);if(previous!=null)return previous;}
        boolean preserveArrivals=false;
        if(editing!=null){var old=one("SELECT customer_id,CAST(arrivals_snapshot AS CHAR) arrivals_snapshot,flow_version FROM metal_production_order WHERE id=? AND factory_id=? FOR UPDATE",editing,factory);if(number(old.get("flow_version"))>0&&!live)throw new ServiceException("真实来料工单编辑须保留完整来料明细",400);preserveArrivals=jdbc.queryForObject("SELECT COUNT(*) FROM metal_storage_document WHERE factory_id=? AND order_id=? AND type='CUSTOMER_IN' AND status='POSTED'",Integer.class,factory,editing)>0;if(preserveArrivals&&(!Objects.equals(String.valueOf(input.customerId()),String.valueOf(old.get("customer_id")))||!Objects.equals(JSON.toJSON(arrivals),JSON.parse(Objects.toString(old.get("arrivals_snapshot"),"[]")))))throw new ServiceException("已复验来料不能通过编辑工单更改客户或明细，请先办理冲销或补料",409);}
        if(editing!=null)lockEditable(editing,factory,version);
        var prior=editing!=null?List.<Map<String,Object>>of():jdbc.queryForList("SELECT id,request_hash FROM metal_production_order WHERE factory_id=? AND request_actor=? AND request_id=? FOR UPDATE",factory,actor,normalized.requestId());
        if(!prior.isEmpty()){
            if(!hash.equals(prior.get(0).get("request_hash")))throw new ServiceException("同一提交标识的内容已改变，请核实原工单",409);
            return receipt(((Number)prior.get(0).get("id")).longValue(),factory,true);
        }
        var factoryRow=one("SELECT factory_name name FROM metal_factory WHERE factory_id=? AND del_flag='0' FOR UPDATE",factory);
        var line=one("SELECT production_line_name name FROM metal_production_line WHERE production_line_id=? AND factory_id=? FOR UPDATE",input.lineId(),factory);
        var owner=one("SELECT nick_name name FROM sys_user WHERE user_id=? AND factory_id=? AND status='0' AND del_flag='0' FOR UPDATE",input.ownerId(),factory);
        Map<String,Object> customer=input.customerId()==null?null:one("SELECT CAST(customer_id AS CHAR) id,customer_name name,company_name companyName,address,contact_name contactName,contact_phone contactPhone,department FROM metal_customer WHERE customer_id=? AND factory_id=? AND del_flag='0' FOR UPDATE",input.customerId(),factory);
        if(input.products()==null||input.products().isEmpty()||input.products().size()>100)throw new ServiceException("请选择1至100组产品",400);
        var productSnapshots=new ArrayList<Map<String,Object>>();var processSnapshots=new ArrayList<List<Map<String,Object>>>();
        for(Item item:input.products()){
            if(item==null||item.quantity()==null||item.quantity()<1||item.processes()==null)throw new ServiceException("产品数量必须为正整数，工序配置不能为空",400);
            var product=one("SELECT CAST(product_id AS CHAR) id,product_code code,product_name name,product_type productType,specification,dimensions,CAST(volume AS CHAR) volume,CAST(theoretical_weight AS CHAR) weight,CAST(purity AS CHAR) purity,CAST(metal_type_id AS CHAR) metalTypeId,calculation_version calculationVersion FROM metal_product WHERE product_id=? AND factory_id=? AND del_flag='0' FOR UPDATE",item.productId(),factory);
            one("SELECT product_id FROM metal_line_product WHERE production_line_id=? AND product_id=? FOR UPDATE",input.lineId(),item.productId());
            var steps=jdbc.queryForList("SELECT CAST(p.process_id AS CHAR) processId,p.process_name processName,pp.sequence_no sequenceNo,CAST(pp.theoretical_loss AS CHAR) loss,pp.loss_unit lossUnit,pp.measurement_mode measurementMode,p.semantic_code semanticCode,pp.output_category outputCategory,pp.output_form outputForm,CAST(pp.output_material_id AS CHAR) outputMaterialId FROM metal_product_process pp JOIN metal_process p ON p.process_id=pp.process_id WHERE pp.product_id=? AND p.factory_id=? AND p.del_flag='0' ORDER BY pp.sequence_no FOR UPDATE",item.productId(),factory);
            int configured=jdbc.queryForObject("SELECT COUNT(*) FROM metal_product_process WHERE product_id=?",Integer.class,item.productId());
            if(configured!=steps.size()||steps.size()!=item.processes().size())throw new ServiceException("产品工序配置已变化，请刷新配置",409);
            for(int i=0;i<steps.size();i++){
                Step step=item.processes().get(i);
                if(step==null||!Objects.equals(steps.get(i).get("processId"),String.valueOf(step.processId())))throw new ServiceException("产品工序或顺序已变化，请刷新配置",409);
                LocalDateTime start=time(step.start()),end=time(step.end());if(start!=null&&end!=null&&end.isBefore(start))throw new ServiceException("工序结束时间不得早于开始时间",400);
            }
            if(live)validateRoute(steps,factory);
            productSnapshots.add(product);processSnapshots.add(steps);
        }
        long id;
        if(editing==null){
        LocalDate today=LocalDate.now(ZoneId.of("Asia/Shanghai"));
        jdbc.update("INSERT IGNORE INTO metal_production_order_sequence(business_date,sequence_value) VALUES(?,0)",today);
        int next=jdbc.queryForObject("SELECT sequence_value FROM metal_production_order_sequence WHERE business_date=? FOR UPDATE",Integer.class,today)+1;
        if(next>999999)throw new ServiceException("当日工单编号已用尽",409);
        jdbc.update("UPDATE metal_production_order_sequence SET sequence_value=? WHERE business_date=?",next,today);
        String number="GD"+today.format(DateTimeFormatter.BASIC_ISO_DATE)+String.format(Locale.ROOT,"%06d",next);
        id=insert("INSERT INTO metal_production_order(order_no,factory_id,customer_id,production_line_id,owner_id,processing_type,incoming_weight,order_date,factory_name,customer_name,line_name,owner_name,customer_snapshot,request_id,request_actor,request_hash,create_by,creator_name) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",number,factory,input.customerId(),input.lineId(),input.ownerId(),input.processingType(),net,orderDate,factoryRow.get("name"),customer==null?null:customer.get("name"),line.get("name"),owner.get("name"),customer==null?null:JSON.toJSONString(customer),normalized.requestId(),actor,hash,SecurityUtils.getUsername(),SecurityUtils.getLoginUser().getUser().getNickName());
        }else{
            id=editing;
            jdbc.update("UPDATE metal_production_order SET customer_id=?,production_line_id=?,owner_id=?,processing_type=?,incoming_weight=?,order_date=?,factory_name=?,customer_name=?,line_name=?,owner_name=?,customer_snapshot=?,version=version+1,update_by=?,update_time=NOW() WHERE id=? AND factory_id=?",input.customerId(),input.lineId(),input.ownerId(),input.processingType(),net,orderDate,factoryRow.get("name"),customer==null?null:customer.get("name"),line.get("name"),owner.get("name"),customer==null?null:JSON.toJSONString(customer),SecurityUtils.getUsername(),id,factory);
            if(jdbc.queryForObject("SELECT COUNT(*) FROM metal_production_execution_event ev JOIN metal_production_execution e ON e.id=ev.execution_id WHERE e.order_id=?",Integer.class,id)>0)throw new ServiceException("工单已有执行事实，不能改写产品工序",409);
            jdbc.update("DELETE FROM metal_production_execution WHERE order_id=?",id);
            jdbc.update("DELETE s FROM metal_production_order_step s JOIN metal_production_order_item i ON i.id=s.item_id WHERE i.order_id=?",id);
            jdbc.update("DELETE FROM metal_production_order_item WHERE order_id=?",id);
        }
        for(int i=0;i<input.products().size();i++){
            Item item=input.products().get(i);long itemId=insert("INSERT INTO metal_production_order_item(order_id,sequence_no,product_id,quantity,product_snapshot) VALUES(?,?,?,?,?)",id,i+1,item.productId(),item.quantity(),JSON.toJSONString(productSnapshots.get(i)));
            for(int j=0;j<item.processes().size();j++){
                Step step=item.processes().get(j);var snapshot=processSnapshots.get(i).get(j);
                insert("INSERT INTO metal_production_order_step(item_id,sequence_no,process_id,process_name,process_snapshot,start_time,end_time,semantic_code,output_category,output_form,output_material_id,measurement_mode) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",itemId,j+1,step.processId(),snapshot.get("processName"),JSON.toJSONString(snapshot),time(step.start()),time(step.end()),snapshot.get("semanticCode"),snapshot.get("outputCategory"),snapshot.get("outputForm"),snapshot.get("outputMaterialId"),snapshot.get("measurementMode"));
            }
        }
        if(live){jdbc.update("UPDATE metal_production_order SET flow_version=1,arrivals_snapshot=? WHERE id=?",JSON.toJSONString(arrivals),id);if(!preserveArrivals){if(editing!=null)storageOrders.cancelUnpostedArrivals(factory,id);if(material)storageOrders.createArrival(factory,id,input.customerId(),arrivals,input.requestId());}}
        var result=receipt(id,factory,false);if(editing!=null)remember(factory,input.requestId(),"edit",hash,result);return result;
    }
    private Map<String,Object> receipt(long id,long factory,boolean replayed){var result=jdbc.queryForMap(HEADER+" WHERE id=? AND factory_id=?",id,factory);result.put("replayed",replayed);return permissions(result,factory);}

    private static long number(Object value){return value==null?0:Long.parseLong(value.toString());}
    private Map<String,Object> permissions(Map<String,Object> row,long factory){boolean locked=row.get("firstIssueConfirmedAt")!=null||!"CREATED".equals(row.get("status"));String reason=locked?"已领料或已完工，不能编辑、删除":"";if(!locked&&jdbc.queryForObject("SELECT COUNT(*) FROM metal_storage_document WHERE factory_id=? AND order_id=? AND type<>'CUSTOMER_IN' AND deleted=0 AND status IN ('DRAFT','PENDING_REVIEW','RETURNED')",Integer.class,factory,row.get("id"))>0){locked=true;reason="请先撤销待处理的领料/加工单据";}boolean posted=jdbc.queryForObject("SELECT COUNT(*) FROM metal_storage_document WHERE factory_id=? AND order_id=? AND status='POSTED'",Integer.class,factory,row.get("id"))>0;row.put("canEdit",!locked);row.put("canDelete",!locked&&!posted);row.put("disabledReason",reason.isEmpty()&&posted?"已有真实入库，不能直接删除":reason);return row;}
    static List<Map<String,Object>> normalizeArrivals(List<Map<String,Object>> input){if(input==null||input.isEmpty()||input.size()>500)throw new ServiceException("请填写1至500条完整客户来料明细",400);var result=new ArrayList<Map<String,Object>>();for(var raw:input){if(raw==null)throw new ServiceException("来料明细无效",400);var line=new LinkedHashMap<String,Object>();for(String key:List.of("materialId","warehouseId","reviewerId")){Long value=StorageConfigurationService.id(raw.get(key));if(value==null)throw new ServiceException("来料须指定物料、仓库和仓管复验人",400);line.put(key,value.toString());}BigDecimal net=weight(Objects.toString(raw.get("netWeight"),""),"MATERIAL_CUSTOM");if(net==null||net.signum()<=0)throw new ServiceException("来料净重须大于0",400);line.put("netWeight",net.toPlainString());Object q=raw.get("quantity");if(q!=null&&!q.toString().isBlank()){Long quantity=StorageConfigurationService.id(q);if(quantity==null||quantity>Integer.MAX_VALUE)throw new ServiceException("来料件数须为正整数",400);line.put("quantity",quantity);}line.put("purity",StorageLedgerService.optionalPurity(raw.get("purity")));line.put("form",StorageConfigurationService.choice(raw.get("form"),Set.of("POWDER","UNTREATED","METAL"),"来料形态"));result.add(line);}return result;}
    private void validateRoute(List<Map<String,Object>> steps,long factory){ProductionRoutePolicy.validate(steps);for(var s:steps){String semantic=Objects.toString(s.get("semanticCode"),""),category=Objects.toString(s.get("outputCategory"),"");if(category.equals("RAW")){Long material=StorageConfigurationService.id(s.get("outputMaterialId"));if(material==null)throw new ServiceException("原材料产出工序须配置目标物料",400);var materials=jdbc.queryForList("SELECT measurement_mode,material_form FROM metal_material WHERE material_id=? AND factory_id=? AND del_flag='0' FOR UPDATE",material,factory);if(materials.isEmpty())throw new ServiceException("工序“"+s.get("processName")+"”的产出物料（编号"+material+"）已失效或不属于当前工厂，请在产品管理中重新配置该工序的产出物料",409);var m=materials.get(0);if(!Objects.equals(s.get("measurementMode"),m.get("measurement_mode"))||!Objects.equals(s.get("outputForm"),m.get("material_form")))throw new ServiceException("原材料产出配置与物料目录不符",409);if(semantic.equals("ACID_WASH")&&!"METAL".equals(s.get("outputForm")))throw new ServiceException("酸洗必须产出金属料",400);}}}
    private static byte[] sha256(byte[] bytes){try{return java.security.MessageDigest.getInstance("SHA-256").digest(bytes);}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
}
