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
 private static String text(Object value){return Objects.toString(value,"");}
 private static Map<String,Object> json(Object value){return JSON.parseObject(text(value));}
 private Map<String,Object> one(String sql,Object...args){var rows=jdbc.queryForList(sql,args);if(rows.isEmpty())throw new ServiceException("缺少实际来源流水",409);return rows.get(0);}
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
  if(!visiting.add(execution))throw new ServiceException("加工来源形成循环，请检查领料与产出批次",409);
  long order=jdbc.queryForObject("SELECT order_id FROM metal_production_execution WHERE id=?",Long.class,execution);
  boolean direct="DIRECT_CUSTOM".equals(jdbc.queryForObject("SELECT processing_type FROM metal_production_order WHERE id=?",String.class,order));
  var result=pool(zero());
  for(var event:jdbc.queryForList("SELECT ev.*,st.origin_order_id,CAST(st.identity_snapshot AS CHAR) identity_snapshot FROM metal_production_execution_event ev LEFT JOIN metal_storage_stock st ON st.id=ev.stock_id WHERE ev.execution_id=? AND ev.event_type='INPUT' AND ev.reversed=0 ORDER BY ev.id",execution)){
   if(inherited(event,order)&&number(event.get("stock_id"))>0){add(result,remainingIssue(number(event.get("line_id"))));continue;}
   var input=pool(input(event,order));
   var returned=jdbc.queryForMap("SELECT COALESCE(SUM(returned_weight),0) returned_weight,COALESCE(SUM(CASE WHEN source_owner_id<>customer_id THEN returned_weight ELSE 0 END),0) factory_returned_weight FROM metal_storage_issue_allocation WHERE document_id=? AND line_id=?",event.get("document_id"),event.get("line_id"));
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
  for(var event:jdbc.queryForList("SELECT id,weight FROM metal_production_execution_event WHERE execution_id=? AND reversed=0 AND event_type IN ('OUTPUT','RECOVERY') ORDER BY id",execution))parts.put(number(event.get("id")),take(remaining,amount(event.get("weight"))));
  outputs.put(execution,parts);losses.put(execution,List.copyOf(remaining));return parts;
 }
 List<BigDecimal> output(long execution,long event){var part=outputParts(execution).get(event);if(part==null)throw new ServiceException("缺少实际产出来源",409);return part;}
 List<BigDecimal> loss(long execution,BigDecimal expected){outputParts(execution);var result=losses.get(execution);if(result.get(0).add(result.get(1)).compareTo(expected)!=0)throw new ServiceException("来源损耗与实际加工损耗不一致",409);return result;}
 private List<BigDecimal> issue(long line){
  if(issues.containsKey(line))return issues.get(line);
  var event=one("SELECT id,CAST(result AS CHAR) result FROM metal_storage_event WHERE line_id=? AND action='ISSUE' AND reversed_by IS NULL",line);var actual=json(event.get("result"));
  var sent=pool(take(stockBefore(number(actual.get("stockId")),number(event.get("id"))),amount(actual.get("sentWeight"))));
  var received=take(sent,amount(actual.get("receivedWeight")));issues.put(line,received);return received;
 }
 private List<BigDecimal> remainingIssue(long line){
  if(remainingIssues.containsKey(line))return remainingIssues.get(line);
  var remaining=pool(issue(line));
  for(var event:jdbc.queryForList("SELECT DISTINCT e.id,CAST(e.result AS CHAR) result FROM metal_storage_return_allocation r JOIN metal_storage_issue_allocation a ON a.id=r.issue_allocation_id JOIN metal_storage_event e ON e.id=r.event_id WHERE a.line_id=? AND e.reversed_by IS NULL ORDER BY e.id",line)){
   var actual=json(event.get("result"));var sent=pool(take(remaining,amount(actual.get("sentWeight"))));returns.put(number(event.get("id")),take(sent,amount(actual.get("receivedWeight"))));
  }
  remainingIssues.put(line,List.copyOf(remaining));return remainingIssues.get(line);
 }
 private List<BigDecimal> returned(Map<String,Object> event){long id=number(event.get("id"));if(!returns.containsKey(id))remainingIssue(number(json(event.get("result")).get("issueLineId")));var result=returns.get(id);if(result==null)throw new ServiceException("退料缺少原领料来源",409);return result;}
 List<BigDecimal> initialStock(long stock){
  if(initialStocks.containsKey(stock))return initialStocks.get(stock);
  var rows=jdbc.queryForList("SELECT e.id,e.action,CAST(e.result AS CHAR) result,p.id production_event,p.execution_id FROM metal_storage_stock s JOIN metal_storage_event e ON e.document_id=s.origin_document_id AND e.line_id=s.origin_line_id LEFT JOIN metal_production_execution_event p ON p.document_id=e.document_id AND p.line_id=e.line_id AND p.event_type='OUTPUT' AND p.reversed=0 WHERE s.id=? AND e.reversed_by IS NULL",stock);
  if(rows.isEmpty())return zero(); // A reversed receipt has no active source balance.
  var event=rows.get(0);List<BigDecimal> result;
  if("RETURN".equals(event.get("action")))result=returned(event);
  else if(event.get("production_event")!=null)result=take(pool(output(number(event.get("execution_id")),number(event.get("production_event")))),amount(json(event.get("result")).get("netWeight")));
  else throw new ServiceException("库存缺少实际生产来源",409);
  initialStocks.put(stock,result);return result;
 }
 private List<BigDecimal> stockBefore(long stock,long before){
  var remaining=pool(zero());
  var entries=stockEntries.computeIfAbsent(stock,key->jdbc.queryForList("SELECT e.id,e.action,CAST(e.result AS CHAR) result,s.weight_delta FROM metal_storage_entry s JOIN metal_storage_event e ON e.id=s.event_id WHERE s.stock_id=? AND s.ledger='PHYSICAL' AND e.reversed_by IS NULL AND e.action<>'REVERSAL' ORDER BY e.id,s.id",key));
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
