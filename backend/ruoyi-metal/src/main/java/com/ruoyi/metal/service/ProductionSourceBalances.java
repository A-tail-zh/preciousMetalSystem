package com.ruoyi.metal.service;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.exception.ServiceException;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.ruoyi.metal.service.ProductionExecutionService.amount;
import static com.ruoyi.metal.service.ProductionExecutionService.number;

/** Replays derived production sources only; physical stock, ownership and debts are never changed here. */
final class ProductionSourceBalances {
 private final JdbcTemplate jdbc;
 private final Map<Long,List<BigDecimal>> net=new HashMap<>(),issues=new HashMap<>(),remainingIssues=new HashMap<>(),returns=new HashMap<>(),initialStocks=new HashMap<>(),losses=new HashMap<>();
 private final Map<Long,Map<Long,List<BigDecimal>>> outputs=new HashMap<>();
 private final Map<Long,List<Map<String,Object>>> stockEntries=new HashMap<>();
 private final Set<Long> visiting=new HashSet<>();
 ProductionSourceBalances(JdbcTemplate jdbc){this.jdbc=jdbc;}
 private List<Map<String,Object>> rows(String sql,Object...args){return jdbc.queryForList(ProductionBatchMaterialService.currentRead(sql),args);}
 private <T> List<T> rows(String sql,Class<T> type,Object...args){return jdbc.queryForList(ProductionBatchMaterialService.currentRead(sql),type,args);}
 private Map<String,Object> values(String sql,Object...args){return jdbc.queryForMap(ProductionBatchMaterialService.currentRead(sql),args);}
 void requireAcyclicInput(long factory,long target,long stock){
  var checked=new HashSet<Long>();var visiting=new HashSet<Long>();
  for(long source:rows("SELECT DISTINCT execution_id FROM metal_production_execution_event WHERE factory_id=? AND stock_id=? AND event_type='OUTPUT' AND reversed=0 FOR UPDATE",Long.class,factory,stock))checkInputPath(factory,target,source,visiting,checked);
 }
 private void checkInputPath(long factory,long target,long source,Set<Long> visiting,Set<Long> checked){
  if(source==target)throw new ServiceException("加工来源形成循环：所选物料不能领入批次"+target+"，请改用新批次领料",409);
  if(checked.contains(source))return;
  if(!visiting.add(source))throw new ServiceException("加工来源形成循环，请先核对来源批次的领料与产出记录",409);
  for(long parent:rows("SELECT DISTINCT COALESCE(src.execution_id,ev.source_execution_id) FROM metal_production_execution_event ev LEFT JOIN metal_production_execution_event src ON src.factory_id=ev.factory_id AND src.stock_id=ev.stock_id AND src.event_type='OUTPUT' AND src.reversed=0 WHERE ev.factory_id=? AND ev.execution_id=? AND ev.event_type='INPUT' AND ev.reversed=0 AND COALESCE(src.execution_id,ev.source_execution_id) IS NOT NULL FOR UPDATE",Long.class,factory,source))checkInputPath(factory,target,parent,visiting,checked);
  visiting.remove(source);checked.add(source);
 }
 private static String text(Object value){return Objects.toString(value,"");}
 private static Map<String,Object> json(Object value){return JSON.parseObject(text(value));}
 private Map<String,Object> one(String sql,Object...args){var rows=rows(sql,args);if(rows.isEmpty())throw new ServiceException("缺少实际来源流水",409);return rows.get(0);}
 private static List<BigDecimal> pool(List<BigDecimal> source){return new ArrayList<>(source);}
 private static List<BigDecimal> zero(){return List.of(amount(null),amount(null));}
 private static void add(List<BigDecimal> pool,List<BigDecimal> shares){for(int i=0;i<2;i++)pool.set(i,pool.get(i).add(shares.get(i)));}
 static List<BigDecimal> take(List<BigDecimal> pool,BigDecimal weight){
  BigDecimal total=pool.get(0).add(pool.get(1));
  if(weight.signum()<0||weight.compareTo(total)>0)throw new ServiceException("来源分配超过剩余实际份额",409);
  if(weight.signum()==0)return zero();
  var shares=ProductionExecutionService.allocateLoss(weight,pool.get(0),pool.get(1));
  for(int i=0;i<2;i++)pool.set(i,pool.get(i).subtract(shares.get(i)));
  return shares;
 }
 List<BigDecimal> net(long execution){
  if(net.containsKey(execution))return net.get(execution);
  var historical=rows("SELECT * FROM metal_production_execution WHERE id=?",execution);if(!historical.isEmpty()){var adoption=ProductionExecutionService.legacyAdoption(jdbc,historical.get(0));if(adoption!=null){var frozen=List.of(amount(adoption.get("netCustomerWeight")),amount(adoption.get("netFactoryWeight")));net.put(execution,frozen);return frozen;}}
  var precise=new ProductionBatchMaterialService(jdbc).initialExecutionComposition(execution);if(precise!=null){net.put(execution,precise);return precise;}
  if(!visiting.add(execution))throw new ServiceException("加工来源形成循环，请检查领料与产出批次",409);
  long order=jdbc.queryForObject("SELECT order_id FROM metal_production_execution WHERE id=?",Long.class,execution);
  boolean direct="DIRECT_CUSTOM".equals(jdbc.queryForObject("SELECT processing_type FROM metal_production_order WHERE id=?",String.class,order));
  Long batch=jdbc.queryForObject("SELECT batch_id FROM metal_production_execution WHERE id=?",Long.class,execution);
  var result=pool(zero());
  for(var event:rows("SELECT ev.*,st.origin_order_id,CAST(st.identity_snapshot AS CHAR) identity_snapshot FROM metal_production_execution_event ev LEFT JOIN metal_storage_stock st ON st.id=ev.stock_id WHERE ev.execution_id=? AND ev.event_type='INPUT' AND ev.reversed=0 ORDER BY ev.id FOR UPDATE",execution)){
   if(inherited(event,order)&&number(event.get("stock_id"))>0){add(result,remainingIssue(number(event.get("line_id"))));continue;}
   var input=pool(input(event,order));
   Map<String,Object> returned;
   if(batch!=null&&batch>0){
    BigDecimal all=BigDecimal.ZERO,factory=BigDecimal.ZERO;
    for(var allocation:rows("SELECT source_owner_id,customer_id,returned_weight FROM metal_storage_issue_allocation WHERE document_id=? AND line_id=? ORDER BY id FOR UPDATE",event.get("document_id"),event.get("line_id"))){all=all.add(amount(allocation.get("returned_weight")));if(number(allocation.get("source_owner_id"))!=number(allocation.get("customer_id")))factory=factory.add(amount(allocation.get("returned_weight")));}
    returned=Map.of("returned_weight",all,"factory_returned_weight",factory);
   }else returned=values("SELECT COALESCE(SUM(returned_weight),0) returned_weight,COALESCE(SUM(CASE WHEN source_owner_id<>customer_id THEN returned_weight ELSE 0 END),0) factory_returned_weight FROM metal_storage_issue_allocation WHERE document_id=? AND line_id=? FOR UPDATE",event.get("document_id"),event.get("line_id"));
   BigDecimal weight=amount(returned.get("returned_weight"));
   if(number(event.get("origin_order_id"))==order)take(input,weight);
   else{BigDecimal factoryReturn=direct?amount(returned.get("factory_returned_weight")):weight.min(input.get(1));input.set(0,input.get(0).subtract(weight.subtract(factoryReturn)));input.set(1,input.get(1).subtract(factoryReturn));}
   if(input.stream().anyMatch(x->x.signum()<0))throw new ServiceException("原样退料来源超过实际净投入",409);
   add(result,input);
  }
  visiting.remove(execution);net.put(execution,List.copyOf(result));return net.get(execution);
 }
 private boolean inherited(Map<String,Object> event,long order){return number(event.get("origin_order_id"))==order&&!text(event.get("identity_snapshot")).isBlank()&&number(json(event.get("identity_snapshot")).get("sourceExecutionId"))>0;}
 List<BigDecimal> input(Map<String,Object> event,long order){
  if(inherited(event,order)){
   if(number(event.get("stock_id"))>0)return issue(number(event.get("line_id")));
   // Older events without a stock reference retain their original execution-level provenance.
   var source=net(number(json(event.get("identity_snapshot")).get("sourceExecutionId")));
   if(source.get(0).add(source.get(1)).signum()==0)throw new ServiceException("产出缺少已确认投入来源",409);
   return take(pool(source),amount(event.get("weight")));
  }
  return List.of(amount(event.get("customer_weight")),amount(event.get("factory_weight")));
 }
 private Map<Long,List<BigDecimal>> outputParts(long execution){
  if(outputs.containsKey(execution))return outputs.get(execution);
  var remaining=pool(net(execution));Map<Long,List<BigDecimal>> parts=new LinkedHashMap<>();
  Long batch=jdbc.queryForObject("SELECT batch_id FROM metal_production_execution WHERE id=?",Long.class,execution);
  boolean fixed=batch!=null&&batch>0;
  String columns=fixed?"id,weight,customer_weight,factory_weight":"id,weight";
  for(var event:rows("SELECT "+columns+" FROM metal_production_execution_event WHERE execution_id=? AND reversed=0 AND event_type IN ('OUTPUT','RECOVERY') ORDER BY id FOR UPDATE",execution)){
   List<BigDecimal> split;
   if(fixed){
    split=List.of(amount(event.get("customer_weight")),amount(event.get("factory_weight")));
    if(split.stream().anyMatch(w->w.signum()<0)||split.get(0).add(split.get(1)).compareTo(amount(event.get("weight")))!=0)throw new ServiceException("已确认产出来源快照与实物不一致",409);
    for(int i=0;i<2;i++){if(split.get(i).compareTo(remaining.get(i))>0)throw new ServiceException("退料或产出超过当前剩余来源份额，不能改写已确认产出来源",409);remaining.set(i,remaining.get(i).subtract(split.get(i)));}
   }else split=take(remaining,amount(event.get("weight")));
   parts.put(number(event.get("id")),split);
  }
  outputs.put(execution,parts);losses.put(execution,List.copyOf(remaining));return parts;
 }
 List<BigDecimal> remaining(long execution){var material=new ProductionBatchMaterialService(jdbc);if(material.enabled()&&material.initialExecutionComposition(execution)!=null){var r=material.remaining(execution);return List.of(amount(r.get("customerWeight")),amount(r.get("factoryWeight")));}outputParts(execution);var remaining=pool(losses.get(execution));var records=rows("SELECT * FROM metal_production_execution WHERE id=?",execution);if(!records.isEmpty()){var adoption=ProductionExecutionService.legacyAdoption(jdbc,records.get(0));if(adoption!=null&&"WIP".equals(adoption.get("kind")))take(remaining,amount(adoption.get("weight")));}return List.copyOf(remaining);}
 List<BigDecimal> output(long execution,long event){var part=outputParts(execution).get(event);if(part==null)throw new ServiceException("缺少实际产出来源",409);return part;}
 List<BigDecimal> loss(long execution,BigDecimal expected){outputParts(execution);var result=losses.get(execution);if(result.get(0).add(result.get(1)).compareTo(expected)!=0)throw new ServiceException("来源损耗与实际加工损耗不一致",409);return result;}
 private List<BigDecimal> issue(long line){
  if(issues.containsKey(line))return issues.get(line);
  var event=one("SELECT id,CAST(result AS CHAR) result FROM metal_storage_event WHERE line_id=? AND action='ISSUE' AND reversed_by IS NULL FOR UPDATE",line);var actual=json(event.get("result"));
  if(actual.containsKey("sourceCustomerWeight")&&actual.containsKey("sourceFactoryWeight")){
   var fixed=List.of(amount(actual.get("sourceCustomerWeight")),amount(actual.get("sourceFactoryWeight")));
   if(fixed.stream().anyMatch(w->w.signum()<0)||fixed.get(0).add(fixed.get(1)).compareTo(amount(actual.get("receivedWeight")))!=0)throw new ServiceException("实际领料来源快照与实收净重不一致",409);
   issues.put(line,fixed);return fixed;
  }
  var sent=pool(take(stockBefore(number(actual.get("stockId")),number(event.get("id"))),amount(actual.get("sentWeight"))));
  var received=take(sent,amount(actual.get("receivedWeight")));issues.put(line,received);return received;
 }
 List<BigDecimal> snapshotIssue(long stock,long event,BigDecimal sentWeight,BigDecimal receivedWeight){
  return take(pool(take(stockBefore(stock,event),sentWeight)),receivedWeight);
 }
 private List<BigDecimal> remainingIssue(long line){
  if(remainingIssues.containsKey(line))return remainingIssues.get(line);
  var remaining=pool(issue(line));
  for(var event:rows("SELECT DISTINCT e.id,CAST(e.result AS CHAR) result FROM metal_storage_return_allocation r JOIN metal_storage_issue_allocation a ON a.id=r.issue_allocation_id JOIN metal_storage_event e ON e.id=r.event_id WHERE a.line_id=? AND e.reversed_by IS NULL ORDER BY e.id FOR UPDATE",line)){
   var actual=json(event.get("result"));var sent=pool(take(remaining,amount(actual.get("sentWeight"))));returns.put(number(event.get("id")),take(sent,amount(actual.get("receivedWeight"))));
  }
  remainingIssues.put(line,List.copyOf(remaining));return remainingIssues.get(line);
 }
 private List<BigDecimal> returned(Map<String,Object> event){long id=number(event.get("id"));if(!returns.containsKey(id))remainingIssue(number(json(event.get("result")).get("issueLineId")));var result=returns.get(id);if(result==null)throw new ServiceException("退料缺少原领料来源",409);return result;}
 List<BigDecimal> initialStock(long stock){
  if(initialStocks.containsKey(stock))return initialStocks.get(stock);
  var rows=rows("SELECT e.id,e.action,CAST(e.result AS CHAR) result,p.id production_event,p.execution_id FROM metal_storage_stock s JOIN metal_storage_event e ON e.document_id=s.origin_document_id AND e.line_id=s.origin_line_id LEFT JOIN metal_production_execution_event p ON p.document_id=e.document_id AND p.line_id=e.line_id AND p.event_type IN ('OUTPUT','RECOVERY') AND p.reversed=0 WHERE s.id=? AND e.reversed_by IS NULL FOR UPDATE",stock);
  if(rows.isEmpty())return zero(); // A reversed receipt has no active source balance.
  var event=rows.get(0);List<BigDecimal> result;
  if("RETURN".equals(event.get("action")))result=returned(event);
  else if(event.get("production_event")!=null)result=take(pool(output(number(event.get("execution_id")),number(event.get("production_event")))),amount(json(event.get("result")).get("netWeight")));
  else throw new ServiceException("库存缺少实际生产来源",409);
  initialStocks.put(stock,result);return result;
 }
 private List<BigDecimal> stockBefore(long stock,long before){
  var remaining=pool(zero());
  var entries=stockEntries.computeIfAbsent(stock,key->rows("SELECT e.id,e.action,CAST(e.result AS CHAR) result,s.weight_delta FROM metal_storage_entry s JOIN metal_storage_event e ON e.id=s.event_id WHERE s.stock_id=? AND s.ledger='PHYSICAL' AND e.reversed_by IS NULL AND e.action<>'REVERSAL' ORDER BY e.id,s.id FOR UPDATE",key));
  for(var event:entries){if(number(event.get("id"))>=before)break;BigDecimal weight=amount(event.get("weight_delta"));
   if(weight.signum()<0)take(remaining,weight.negate());
   else if(weight.signum()>0){
    if("RETURN".equals(event.get("action")))add(remaining,returned(event));
    else if("ADJUSTMENT".equals(event.get("action"))){var origin=initialStock(stock);add(remaining,ProductionExecutionService.proportion(weight,origin.get(0),origin.get(1)));}
    else add(remaining,initialStock(stock));
   }
  }
  return remaining;
 }
}
