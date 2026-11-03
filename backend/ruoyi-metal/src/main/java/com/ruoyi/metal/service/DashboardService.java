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
    public Map<String, Object> dashboard() { return dashboard(null); }

    /** A selected factory narrows the existing server-side scope. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Map<String, Object> dashboard(Long selectedFactory) {
        Long factory = scope.currentFactory();
        if(selectedFactory!=null){scope.check(selectedFactory);factory=selectedFactory;}
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai")), month = today.withDayOfMonth(1), start = today.minusDays(29);
        String w = filter(factory, "w"), r = filter(factory, "r"), q = filter(factory, "q");
        var work = data("SELECT COUNT(*) total,COALESCE(SUM(w.status='IN_PROGRESS'),0) inProgress,COALESCE(SUM(w.status='COMPLETED'),0) completed FROM metal_work_order w WHERE w.del_flag='0'" + w).get(0);
        var warnings = data("SELECT COALESCE(SUM(w.status='PENDING'),0) pending,COALESCE(SUM(DATE(w.trigger_time)=?),0) newToday,COALESCE(SUM(DATE(w.handled_time)=? AND w.status='HANDLED'),0) handledToday FROM metal_warning_record w WHERE w.source_void=FALSE AND w.del_flag='0'" + w, today, today).get(0);
        Long quoteCount = jdbc.queryForObject("SELECT COUNT(*) FROM metal_saved_quotation q WHERE q.create_time>=? AND q.create_time<?" + q, Long.class, month, today.plusDays(1));
        var cats = data("SELECT q.category_labels categories,COUNT(*) quoteCount FROM metal_saved_quotation q WHERE q.create_time>=? AND q.create_time<?" + q + " GROUP BY q.category_labels ORDER BY MIN(q.quote_id)", month, today.plusDays(1));
        var categoryCounts = quotationCategoryCounts(cats);
        var categories = categoryCounts.keySet();
        var trend = data("SELECT DATE_FORMAT(r.create_time,'%Y-%m-%d') day,CAST(SUM(r.loss_weight) AS DECIMAL(20,6)) loss FROM metal_work_report r WHERE r.voided=FALSE AND r.create_time>=? AND r.create_time<?" + r + " GROUP BY DATE_FORMAT(r.create_time,'%Y-%m-%d') ORDER BY DATE_FORMAT(r.create_time,'%Y-%m-%d')", start, today.plusDays(1));
        var byDay = new HashMap<String, String>();
        trend.forEach(x -> byDay.put(x.get("day").toString(), x.get("loss").toString()));
        var completeTrend = new ArrayList<Map<String, Object>>();
        for (LocalDate d = start; !d.isAfter(today); d = d.plusDays(1))
            completeTrend.add(Map.of("day", d.toString(), "loss", byDay.getOrDefault(d.toString(), "0.000000")));
        var ranking = data("SELECT CAST(r.process_id AS CHAR) processId,p.process_name processName,CAST(SUM(r.loss_weight) AS DECIMAL(20,6)) loss FROM metal_work_report r JOIN metal_process p ON p.process_id=r.process_id WHERE r.voided=FALSE AND r.create_time>=? AND r.create_time<?" + r + " GROUP BY r.process_id,p.process_name ORDER BY SUM(r.loss_weight) DESC,r.process_id LIMIT 5", month, today.plusDays(1));
        var factories = data("SELECT CAST(f.factory_id AS CHAR) factoryId,f.factory_name factoryName,CAST(COALESCE((SELECT SUM(r.loss_weight) FROM metal_work_report r WHERE r.factory_id=f.factory_id AND r.voided=FALSE AND r.create_time>=? AND r.create_time<?),0) AS DECIMAL(20,6)) loss FROM metal_factory f WHERE f.del_flag='0'" + (factory == null ? "" : " AND f.factory_id=" + factory) + " ORDER BY f.factory_id", month, today.plusDays(1));
        var distribution = data("SELECT CAST(w.factory_id AS CHAR) factoryId,f.factory_name factoryName,COUNT(*) count FROM metal_warning_record w JOIN metal_factory f ON f.factory_id=w.factory_id WHERE w.source_void=FALSE AND w.del_flag='0' AND w.trigger_time>=? AND w.trigger_time<?" + w + " GROUP BY w.factory_id,f.factory_name ORDER BY w.factory_id", month, today.plusDays(1));
        var center = data("SELECT CAST(w.warning_id AS CHAR) id,CAST(w.factory_id AS CHAR) factoryId,DATE_FORMAT(w.trigger_time,'%Y-%m-%d %H:%i:%s') triggerTime,f.factory_name factoryName,w.process_name processName,CAST(w.threshold_value AS CHAR) thresholdValue,w.loss_unit lossUnit,w.status FROM metal_warning_record w JOIN metal_factory f ON f.factory_id=w.factory_id WHERE w.source_void=FALSE AND w.del_flag='0'" + w + " ORDER BY w.trigger_time DESC,w.warning_id DESC LIMIT 6");
        return Map.of("workOrders", work, "warnings", warnings, "quotations", Map.of("count", quoteCount, "categories", categories, "categoryCounts", categoryCounts), "trend", completeTrend, "ranking", ranking, "factories", factories, "distribution", distribution, "warningCenter", center, "asOf", today.toString(), "metal", "铱Ir");
    }

    /** Count quotation documents per category, not rows or product quantities. */
    static Map<String,Long> quotationCategoryCounts(List<Map<String,Object>> grouped){
        var counts=new LinkedHashMap<String,Long>();
        for(var row:grouped){
            Object labels=row.get("categories");if(labels==null)continue;
            long documents=((Number)row.get("quoteCount")).longValue();
            Arrays.stream(labels.toString().split("、")).map(String::trim).filter(label->!label.isEmpty()).distinct()
                .forEach(label->counts.merge(label,documents,Long::sum));
        }
        return counts;
    }

    public Map<String,Object> dashboardFactories(){
        Long own=scope.currentFactory();
        var factories=own==null?data("SELECT CAST(factory_id AS CHAR) id,factory_name name FROM metal_factory WHERE del_flag='0' ORDER BY factory_id"):
            data("SELECT CAST(factory_id AS CHAR) id,factory_name name FROM metal_factory WHERE del_flag='0' AND factory_id=?",own);
        return Map.of("canSelectAll",scope.isAdmin(),"factories",factories);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.REPEATABLE_READ)
    public Map<String, Object> saveQuotation(QuotationSaveRequest input) {
        scope.lockFactories(List.of(input.factoryId()));
        long actor=SecurityUtils.getUserId();
        boolean legacy=input.items().stream().allMatch(i->i.processingType()==null&&i.powderWeight()==null&&i.nonPowderWeight()==null&&i.lossWeight()==null);
        String canonical=legacy?input.factoryId()+":"+input.items().stream().map(i->i.category()+":"+i.quantity()+":"+new TreeMap<>(i.dimensions())).toList():
            "v2:"+JSON.toJSONString(Arrays.asList(input.factoryId(),input.items().stream().map(i->Arrays.asList(i.category(),i.metalTypeId(),i.productType(),i.quantity(),new TreeMap<>(i.dimensions()),i.processingType(),i.powderWeight(),i.nonPowderWeight(),i.lossWeight())).toList()));
        String fingerprint=hash(canonical.getBytes(StandardCharsets.UTF_8));
        var mappings=jdbc.queryForList("SELECT quote_id,request_hash,request_actor FROM metal_quotation_request WHERE request_id=?",input.requestId());
        if(!mappings.isEmpty())return replay(mappings.get(0),fingerprint,actor);
        var original=jdbc.queryForList("SELECT quote_id,request_hash,request_actor FROM metal_saved_quotation WHERE request_id=?",input.requestId());
        if(!original.isEmpty())return replay(original.get(0),fingerprint,actor);
        if(legacy)throw new ServiceException("新报价单必须选择加工类型并填写重量");
        List<Map<String,Object>> items=new ArrayList<>();BigDecimal total=new BigDecimal("0.00");
        for(int index=0;index<input.items().size();index++){
            Map<String,Object> item;
            try{item=new LinkedHashMap<>(quotes.calculate(input.items().get(index)));if(!Boolean.TRUE.equals(item.get("pricingEnabled")))throw new ServiceException("请选择加工类型");}
            catch(ServiceException e){throw new ServiceException("第"+(index+1)+"项商品："+e.getMessage());}
            item.put("dimensionFields",ProductGeometry.FIELDS.get(item.get("productType").toString()).stream().map(key->Map.of("key",key,"label",ProductGeometry.label(item.get("productType").toString(),key),"unit","angle".equals(key)?"°":"cm")).toList());
            item.put("diagramVersion",QuotationDiagram.VERSION);
            items.add(item);total=BusinessDecimal.round(total.add(new BigDecimal(item.get("totalPrice").toString())));
        }
        // Timestamps are not business inputs: exclude them from the long-lived reuse signature.
        List<Map<String,Object>> signing=new ArrayList<>();
        for(var item:items){var value=new LinkedHashMap<>(item);for(String key:List.of("rule","volumeRule")){var rule=new TreeMap<String,Object>((Map<String,Object>)item.get(key));rule.remove("updateTime");value.put(key,rule);}signing.add(value);}
        String content=hash(JSON.toJSONBytes(canonical(signing)));
        var same=jdbc.queryForList("SELECT quote_id FROM metal_saved_quotation WHERE factory_id=? AND request_actor=? AND content_hash=?",input.factoryId(),actor,content);
        long id;boolean reused=!same.isEmpty();
        if(reused)id=((Number)same.get(0).get("quote_id")).longValue();
        else{
            List<byte[]> pictures=new ArrayList<>();
            for(var item:items){byte[] png=QuotationDiagram.render(item);pictures.add(png);item.put("imageHash",hash(png));}
            String factory=jdbc.queryForObject("SELECT factory_name FROM metal_factory WHERE factory_id=?",String.class,input.factoryId());
            String categories=items.stream().map(i->i.get("category").toString()).distinct().collect(java.util.stream.Collectors.joining("、"));
            if(categories.length()>1000)throw new ServiceException("报价商品类别汇总超过1000字符，请减少商品种类");
            jdbc.update("INSERT INTO metal_saved_quotation(quote_no,request_id,request_hash,request_actor,factory_id,category_labels,item_snapshot,total_price,rule_version,snapshot_version,factory_name_snapshot,content_hash) VALUES(?,?,?,?,?,?,?,?,0,2,?,?)",nextQuote(),input.requestId(),fingerprint,actor,input.factoryId(),categories,JSON.toJSONString(items),total,factory,content);
            id=jdbc.queryForObject("SELECT quote_id FROM metal_saved_quotation WHERE request_id=?",Long.class,input.requestId());
            for(int index=0;index<items.size();index++)jdbc.update("INSERT INTO metal_quotation_image(quote_id,item_index,image_png,image_hash,renderer_version) VALUES(?,?,?,?,?)",id,index+1,pictures.get(index),items.get(index).get("imageHash"),QuotationDiagram.VERSION);
        }
        jdbc.update("INSERT INTO metal_quotation_request(request_id,request_hash,request_actor,quote_id) VALUES(?,?,?,?)",input.requestId(),fingerprint,actor,id);
        var result=quotation(id);result.put("reused",reused);return result;
    }

    private Map<String,Object> replay(Map<String,Object> row,String fingerprint,long actor){
        if(!fingerprint.equals(row.get("request_hash"))||actor!=((Number)row.get("request_actor")).longValue())throw new ServiceException("同一请求标识不能保存不同报价内容或更换操作者",409);
        var result=new LinkedHashMap<>(quotation(((Number)row.get("quote_id")).longValue()));result.put("reused",true);return result;
    }
    static String hash(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private static Object canonical(Object value){if(value instanceof Map<?,?> map){var sorted=new TreeMap<String,Object>();map.forEach((k,v)->sorted.put(k.toString(),canonical(v)));return sorted;}if(value instanceof List<?> list)return list.stream().map(DashboardService::canonical).toList();return value;}

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
        var values = data("SELECT CAST(quote_id AS CHAR) id,quote_no quoteNo,CAST(factory_id AS CHAR) factoryId,category_labels categories,CAST(total_price AS CHAR) totalPrice,CAST(item_snapshot AS CHAR) items,rule_version ruleVersion,snapshot_version snapshotVersion,factory_name_snapshot factoryName,DATE_FORMAT(create_time,'%Y-%m-%d %H:%i:%s') createTime FROM metal_saved_quotation WHERE quote_id=?", id);
        if (values.isEmpty()) throw new ServiceException("报价单不存在");
        var result = new LinkedHashMap<>(values.get(0));
        scope.check(Long.valueOf(result.get("factoryId").toString()));
        result.put("items", JSON.parseArray(result.get("items").toString()));
        if("2".equals(String.valueOf(result.get("snapshotVersion"))))result.put("totalPrice",BusinessDecimal.display(result.get("totalPrice")));
        if(result.get("factoryName")==null){var factories=data("SELECT factory_name name FROM metal_factory WHERE factory_id=?",result.get("factoryId"));result.put("factoryName",factories.isEmpty()?result.get("factoryId"):factories.get(0).get("name"));}
        return result;
    }

    public Map<String,Object> quotationList(Long selectedFactory,String quoteNo,int pageNum,int pageSize){
        if(pageNum<1||pageNum>1000000||pageSize<1||pageSize>100)throw new ServiceException("分页参数无效");
        if(quoteNo!=null&&quoteNo.length()>32)throw new ServiceException("报价单号最多32个字符");
        Long factory=scope.currentFactory();if(selectedFactory!=null){scope.check(selectedFactory);factory=selectedFactory;}
        List<Object> args=new ArrayList<>();String where=" WHERE 1=1";
        if(factory!=null){where+=" AND q.factory_id=?";args.add(factory);}
        if(quoteNo!=null&&!quoteNo.isBlank()){where+=" AND q.quote_no LIKE ? ESCAPE '!'";args.add(com.ruoyi.metal.catalog.ListQuery.pattern(quoteNo.strip()));}
        Long count=jdbc.queryForObject("SELECT COUNT(*) FROM metal_saved_quotation q"+where,Long.class,args.toArray());
        args.add(pageSize);args.add((long)(pageNum-1)*pageSize);
        var rows=data("SELECT CAST(q.quote_id AS CHAR) id,q.quote_no quoteNo,CAST(q.factory_id AS CHAR) factoryId,COALESCE(q.factory_name_snapshot,f.factory_name) factoryName,q.category_labels categories,CAST(q.total_price AS CHAR) totalPrice,q.snapshot_version snapshotVersion,DATE_FORMAT(q.create_time,'%Y-%m-%d %H:%i:%s') createTime FROM metal_saved_quotation q JOIN metal_factory f ON f.factory_id=q.factory_id"+where+" ORDER BY q.create_time DESC,q.quote_id DESC LIMIT ? OFFSET ?",args.toArray());
        rows.forEach(row->{if("2".equals(String.valueOf(row.get("snapshotVersion"))))row.put("totalPrice",BusinessDecimal.display(row.get("totalPrice")));});
        return Map.of("rows",rows,"total",count);
    }
    public byte[] quotationImage(Long id,int index){
        var quote=quotation(id);var items=(List<?>)quote.get("items");if(index<1||index>items.size())throw new ServiceException("报价明细不存在");
        if(!"2".equals(String.valueOf(quote.get("snapshotVersion"))))throw new ServiceException("旧报价请使用原尺寸模板");
        var rows=jdbc.query("SELECT image_png FROM metal_quotation_image WHERE quote_id=? AND item_index=?",(rs,n)->rs.getBytes(1),id,index);
        if(rows.isEmpty())throw new ServiceException("报价规格图片缺失，请核对历史记录");return rows.get(0);
    }

    /**
     * 获取当前用户可选择的报价工厂。
     */
    public List<Map<String, Object>> quotationFactories() {
        Long factory = scope.currentFactory();
        return data("SELECT CAST(factory_id AS CHAR) id,factory_name name FROM metal_factory WHERE del_flag='0'" + (factory == null ? "" : " AND factory_id=" + factory) + " ORDER BY factory_id");
    }
}
