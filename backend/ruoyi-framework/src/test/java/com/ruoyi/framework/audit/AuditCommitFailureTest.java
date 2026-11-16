package com.ruoyi.framework.audit;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import com.alibaba.fastjson2.JSON;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class AuditCommitFailureTest {
 static class Manager extends AbstractPlatformTransactionManager {
  String failure;
  Manager(String f){failure=f;}
  protected Object doGetTransaction(){return new Object();}
  protected void doBegin(Object tx,TransactionDefinition def){}
  protected void doCommit(DefaultTransactionStatus status){String f=failure;failure=null;if("rollback".equals(f))throw new UnexpectedRollbackException("fixture rollback");if("unknown".equals(f))throw new TransactionSystemException("fixture unknown");}
  protected void doRollback(DefaultTransactionStatus status){}
 }
 private void check(String failure){
  var jdbc=mock(JdbcTemplate.class);var manager=new Manager(failure);var recorder=new AuditRecorder(jdbc,manager);var c=new AuditContext();c.path="/metal/work-order";c.verb="POST";
  List<String> failures=new ArrayList<>();when(jdbc.update(startsWith("INSERT IGNORE INTO audit_outbox"),any(Object[].class))).thenAnswer(a->{failures.add(((Object[])a.getRawArguments()[1])[1].toString());return 1;});
  var template=new TransactionTemplate(manager);
  if(failure==null)template.executeWithoutResult(t->recorder.success(c,Map.of("fixture",true)));
  else assertThrows(TransactionException.class,()->template.executeWithoutResult(t->recorder.success(c,Map.of("fixture",true))));
  assertTrue(c.logged);
  if(failure==null){assertTrue(failures.isEmpty());return;}
  assertEquals(1,failures.size());var e=JSON.parseObject(failures.get(0));assertEquals(c.event,e.getString("event"));assertEquals("SYSTEM_ERROR",e.getString("code"));assertEquals("",e.getString("response"));assertTrue(e.getJSONObject("changes").getJSONObject("after").isEmpty());
  assertEquals("unknown".equals(failure),e.getString("error").contains("未知"));recorder.failure(c,"SYSTEM_ERROR","duplicate");assertEquals(1,failures.size());
 }
 @Test void rollbackAtCommitWritesIndependentFailure(){check("rollback");}
 @Test void unknownCommitResultIsNotPresentedAsSuccessful(){check("unknown");}
 @Test void successfulCommitDoesNotWriteFailure(){check(null);}
}
