package com.ruoyi.framework.audit;

import java.nio.file.*;import java.util.*;import java.lang.reflect.Method;
import com.alibaba.fastjson2.JSON;import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;import static org.junit.jupiter.api.Assumptions.*;
import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.jdbc.datasource.DriverManagerDataSource;import org.springframework.jdbc.datasource.DataSourceTransactionManager;import org.springframework.transaction.support.TransactionTemplate;

/** Explicit opt-in: expedites only the two newly created fixture events through production storage. */
class AuditFixtureDeliveryLiveTest {
 @Test void deliverOnlyNewFixtureWorkOrderEvent()throws Exception {
  String location=System.getProperty("audit.live.root");assumeTrue(location!=null,"需要显式指定本地隔离业务验收目标");
  Path root=Path.of(location);JSONObject target=JSON.parseObject(Files.readString(Path.of(System.getProperty("audit.live.target.dir",root.resolve(".runtime").toString())).resolve("audit-fixture-live-target.json")));
  Map<String,String> env=new HashMap<>();for(String line:Files.readAllLines(root.resolve(".env"))){int pos=line.indexOf('=');if(pos>0){String value=line.substring(pos+1);if(value.startsWith("'")&&value.endsWith("'"))value=value.substring(1,value.length()-1);env.put(line.substring(0,pos),value);}}
  DriverManagerDataSource source=new DriverManagerDataSource();source.setDriverClassName("com.mysql.cj.jdbc.Driver");source.setUrl("jdbc:mysql://127.0.0.1:"+env.getOrDefault("MYSQL_PORT","3308")+"/heavy_metal?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai");source.setUsername(env.get("MYSQL_USER"));source.setPassword(env.get("MYSQL_PASSWORD"));
  JdbcTemplate jdbc=new JdbcTemplate(source);var manager=new DataSourceTransactionManager(source);var delivery=new AuditDelivery(jdbc,manager);var tx=new TransactionTemplate(manager);
  String eventId=target.getString("eventId");assertTrue(eventId.matches("[a-f0-9-]{36}"));
  Long factoryId=target.getLong("factoryId");String prefix=target.getString("prefix");assertTrue(prefix.matches("工单验收_[0-9]+_[a-f0-9]{6}"));
  assertTrue(jdbc.queryForObject("SELECT factory_name FROM metal_factory WHERE factory_id=?",String.class,factoryId).startsWith(prefix));
  var events=jdbc.queryForList("SELECT event_id FROM audit_outbox WHERE event_id=? AND create_time>=?",eventId,target.getString("startedAt"));assertEquals(1,events.size());
  Method store=AuditDelivery.class.getDeclaredMethod("store",JSONObject.class);store.setAccessible(true);
  for(var row:events)tx.executeWithoutResult(t->{try{
   var queued=jdbc.queryForMap("SELECT event_json,status FROM audit_outbox WHERE event_id=? FOR UPDATE",row.get("event_id"));JSONObject e=JSON.parseObject(queued.get("event_json").toString());
   assertEquals("/metal/work-order",e.getString("path"));assertEquals(factoryId.toString(),JSON.parseObject(e.getString("request")).getJSONObject("body").getString("factoryId"));
   if("PENDING".equals(queued.get("status"))){store.invoke(delivery,e);jdbc.update("UPDATE audit_outbox SET status='DELIVERED',delivered_time=NOW() WHERE event_id=?",row.get("event_id"));}
  }catch(Exception failure){throw new IllegalStateException("新测试事件投递失败",failure);}});
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM sys_oper_log WHERE event_id=?",Integer.class,eventId));
 }
}
