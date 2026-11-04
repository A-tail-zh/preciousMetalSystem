package com.ruoyi.framework.audit;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;import java.util.*;import org.aspectj.lang.ProceedingJoinPoint;import org.springframework.transaction.support.TransactionSynchronizationManager;
class AuditServiceAspectTest{
 @Test void auditFailurePropagatesBeforeTransactionCommit()throws Throwable{AuditRecorder recorder=mock(AuditRecorder.class);ProceedingJoinPoint p=mock(ProceedingJoinPoint.class);when(p.getArgs()).thenReturn(new Object[]{Map.of("customer","test")});when(p.proceed()).thenReturn("1");AuditContext c=new AuditContext();c.path="/metal/work-order";c.verb="POST";c.title="工单";AuditContext.CURRENT.set(c);TransactionSynchronizationManager.setActualTransactionActive(true);doThrow(new IllegalStateException("outbox unavailable")).when(recorder).success(eq(c),any());try{assertThrows(IllegalStateException.class,()->new AuditServiceAspect(recorder).around(p));assertFalse(c.logged);assertFalse(c.inside);}finally{AuditContext.CURRENT.remove();TransactionSynchronizationManager.clear();}}
 @Test void snapshotsDoNotRecordNestedCredentialSentinel(){String text=AuditRedactor.text("{\"nested\":{\"Authorization\":\"Bearer secret-sentinel\",\"password\":\"secret-sentinel\"}}");assertFalse(text.contains("secret-sentinel"));}
 @Test void readOnlyProbeDoesNotConsumeTheFollowingSubmissionAudit()throws Throwable{
  var recorder=mock(AuditRecorder.class);var probe=mock(ProceedingJoinPoint.class);var submit=mock(ProceedingJoinPoint.class);
  when(probe.getArgs()).thenReturn(new Object[]{"fixture-request"});when(probe.proceed()).thenReturn(false);when(submit.getArgs()).thenReturn(new Object[]{Map.of("requestId","fixture-request")});when(submit.proceed()).thenReturn("100");
  doAnswer(call->{((AuditContext)call.getArgument(0)).logged=true;return null;}).when(recorder).success(any(),any());
  var c=new AuditContext();c.path="/metal/warehouse-order/inbound-submissions";c.verb="POST";c.title="多仓入库";AuditContext.CURRENT.set(c);TransactionSynchronizationManager.setActualTransactionActive(true);
  try{
   TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
   assertEquals(false,new AuditServiceAspect(recorder).around(probe));assertFalse(c.logged);verifyNoInteractions(recorder);
   TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
   assertEquals("100",new AuditServiceAspect(recorder).around(submit));assertTrue(c.logged);verify(recorder,times(1)).success(eq(c),any());
  }finally{AuditContext.CURRENT.remove();TransactionSynchronizationManager.clear();}
 }
 @Test void refusalAfterReadOnlyProbeCanStillProduceFailureAudit()throws Throwable{
  var recorder=mock(AuditRecorder.class);var probe=mock(ProceedingJoinPoint.class);var submit=mock(ProceedingJoinPoint.class);
  when(probe.getArgs()).thenReturn(new Object[]{"rejected-request"});when(probe.proceed()).thenReturn(false);when(submit.getArgs()).thenReturn(new Object[]{Map.of("requestId","rejected-request")});when(submit.proceed()).thenThrow(new com.ruoyi.common.exception.ServiceException("仓库不存在"));
  doAnswer(call->{((AuditContext)call.getArgument(0)).logged=true;return null;}).when(recorder).success(any(),any());
  var c=new AuditContext();c.path="/metal/warehouse-order/outbound-submissions";c.verb="POST";c.title="多仓出库";AuditContext.CURRENT.set(c);TransactionSynchronizationManager.setActualTransactionActive(true);
  try{
   TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);new AuditServiceAspect(recorder).around(probe);
   TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
   assertThrows(com.ruoyi.common.exception.ServiceException.class,()->new AuditServiceAspect(recorder).around(submit));assertFalse(c.logged);assertFalse(c.inside);verify(recorder,never()).success(any(),any());
  }finally{AuditContext.CURRENT.remove();TransactionSynchronizationManager.clear();}
 }
 @Test void actualWarehousePreflightMethodsDoNotAuditBeforeSubmission(){
  var jdbc=mock(org.springframework.jdbc.core.JdbcTemplate.class);when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of());
  var scope=mock(com.ruoyi.metal.security.FactoryScope.class);var warehouse=mock(com.ruoyi.metal.service.WarehouseService.class);var drafts=mock(com.ruoyi.metal.service.OutboundDraftService.class);
  var recorder=mock(AuditRecorder.class);var manager=new ProbeTransactionManager();
  var inbound=proxy(new com.ruoyi.metal.service.InboundSubmissionService(jdbc,scope,warehouse),recorder,manager);
  var outbound=proxy(new com.ruoyi.metal.service.OutboundSubmissionService(jdbc,scope,warehouse,drafts),recorder,manager);
  var workflow=proxy(new com.ruoyi.metal.service.WarehouseWorkflowService(jdbc,scope,warehouse,mock(com.ruoyi.metal.service.WarehouseImportService.class),drafts),recorder,manager);
  var c=new AuditContext();c.path="/metal/warehouse-order/inbound-submissions";c.verb="POST";c.title="多仓入库";AuditContext.CURRENT.set(c);
  try{
   assertFalse(inbound.hasRequest("probe-in"));assertFalse(outbound.hasRequest("probe-out"));assertFalse(workflow.legacyBatch(1L,null,null));
   verifyNoInteractions(recorder);assertFalse(c.logged);assertEquals(List.of(true,true,true),manager.readOnlyTransactions);
  }finally{AuditContext.CURRENT.remove();TransactionSynchronizationManager.clear();}
 }
 @SuppressWarnings("unchecked") private <T>T proxy(T target,AuditRecorder recorder,ProbeTransactionManager manager){
  var factory=new org.springframework.aop.aspectj.annotation.AspectJProxyFactory(target);factory.addAspect(new AuditServiceAspect(recorder));
  factory.addAdvice(0,new org.springframework.transaction.interceptor.TransactionInterceptor(manager,new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
  return (T)factory.getProxy();
 }
 private static class ProbeTransactionManager extends org.springframework.transaction.support.AbstractPlatformTransactionManager{
  final List<Boolean> readOnlyTransactions=new ArrayList<>();
  protected Object doGetTransaction(){return new Object();}
  protected void doBegin(Object transaction,org.springframework.transaction.TransactionDefinition definition){readOnlyTransactions.add(definition.isReadOnly());}
  protected void doCommit(org.springframework.transaction.support.DefaultTransactionStatus status){}
  protected void doRollback(org.springframework.transaction.support.DefaultTransactionStatus status){}
 }
}
