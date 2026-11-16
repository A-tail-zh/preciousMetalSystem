package com.ruoyi.metal.service;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.metal.security.FactoryScope;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.KeyHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProductionBatchServiceTest {
    private JdbcTemplate jdbc;
    private FactoryScope scope;
    private ProductionExecutionService executions;
    private ProductionBatchService service;
    private MockedStatic<SecurityUtils> security;
    private static final String REQUEST="11111111-1111-4111-8111-111111111111";

    @BeforeEach void setup(){
        jdbc=mock(JdbcTemplate.class);scope=mock(FactoryScope.class);executions=mock(ProductionExecutionService.class);
        when(scope.isAdmin()).thenReturn(true);when(scope.resolveFactory(7L)).thenReturn(7L);
        security=mockStatic(SecurityUtils.class);security.when(SecurityUtils::getUserId).thenReturn(5L);
        service=new ProductionBatchService(jdbc,scope,executions);
    }
    @AfterEach void cleanup(){security.close();}

    private Map<String,Object> createBody(){return new LinkedHashMap<>(Map.of("factoryId","7","requestId",REQUEST,"orderId","10","orderItemId","11","orderVersion",2,"plannedQuantity",30));}
    private void orderAndItem(){
        when(jdbc.queryForList("SELECT id,version,status,flow_version,deleted FROM metal_production_order WHERE factory_id=? AND id=? FOR UPDATE",7L,10L)).thenReturn(List.of(Map.of("id",10L,"version",2,"status","CREATED","flow_version",1,"deleted",0)));
        when(jdbc.queryForList("SELECT id,quantity FROM metal_production_order_item WHERE order_id=? AND id=? FOR UPDATE",10L,11L)).thenReturn(List.of(Map.of("id",11L,"quantity",100)));
    }

    @Test void forgedFactoryFailsBeforeAnyJdbcAccess(){
        when(scope.resolveFactory(7L)).thenThrow(new ServiceException("跨厂拒绝",403));
        assertEquals(403,assertThrows(ServiceException.class,()->service.create(createBody())).getCode());
        verifyNoInteractions(jdbc,executions);
    }

    @Test void permissionIsRequiredEvenForCreationAndOperationLookup(){
        when(scope.isAdmin()).thenReturn(false);
        assertEquals(403,assertThrows(ServiceException.class,()->service.create(createBody())).getCode());
        assertEquals(403,assertThrows(ServiceException.class,()->service.operation(REQUEST,7L)).getCode());
        verifyNoInteractions(jdbc,executions);
    }

    @Test void forgedStageAndInvalidPlanAreRejectedWithoutWriting(){
        var body=createBody();body.put("completedStepIds",List.of("21"));
        assertEquals(400,assertThrows(ServiceException.class,()->service.create(body)).getCode());
        body.remove("completedStepIds");body.put("plannedQuantity","1.50");
        assertEquals(400,assertThrows(ServiceException.class,()->service.create(body)).getCode());
        body.put("plannedQuantity","2147483648");
        assertEquals(400,assertThrows(ServiceException.class,()->service.create(body)).getCode());
        verifyNoInteractions(jdbc,executions);
    }

    @Test void planCannotOverallocateExistingProductTarget(){
        orderAndItem();when(jdbc.queryForList(startsWith("SELECT planned_quantity FROM metal_production_batch "),eq(7L),eq(11L))).thenReturn(List.of(Map.of("planned_quantity",80L)));
        assertTrue(assertThrows(ServiceException.class,()->service.create(createBody())).getMessage().contains("未分配目标"));
        verify(jdbc,never()).update(any(PreparedStatementCreator.class),any(KeyHolder.class));verifyNoInteractions(executions);
    }

    @Test void unmappedHistoricalExecutionMustNotBeGuessedIntoNewIdentity(){
        orderAndItem();when(jdbc.queryForList(startsWith("SELECT * FROM metal_production_execution "),eq(7L),eq(11L))).thenReturn(List.of(Map.of("id",500L)));
        assertTrue(assertThrows(ServiceException.class,()->service.create(createBody())).getMessage().contains("未映射"));
        verify(jdbc,never()).update(any(PreparedStatementCreator.class),any(KeyHolder.class));
    }

    @Test void pieceFirstStageCanFreezeRouteWithoutInferringAPlan() throws Exception {
        orderAndItem();var body=createBody();body.put("plannedQuantity",null);
        when(jdbc.queryForList("SELECT product_id,CAST(product_snapshot AS CHAR) product_snapshot FROM metal_production_order_item WHERE id=? FOR UPDATE",11L)).thenReturn(List.of(Map.of("product_id",12L,"product_snapshot","{}")));
        when(jdbc.queryForList(startsWith("SELECT id,process_id,process_name,sequence_no,"),eq(11L))).thenReturn(List.of(Map.of("id",21L,"process_id",22L,"sequence_no",1,"process_snapshot","{}","measurement_mode","PIECE")));
        when(jdbc.queryForObject(startsWith("SELECT value FROM metal_storage_sequence"),eq(Integer.class),any(java.time.LocalDate.class))).thenReturn(1);
        when(jdbc.queryForList(contains("FROM metal_production_batch WHERE factory_id=? AND id=?"),eq(7L),eq(100L))).thenReturn(List.of(Map.of("number","PB-SYNTHETIC")));
        var key=new java.util.concurrent.atomic.AtomicLong(100);
        doAnswer(call->{call.getArgument(1,KeyHolder.class).getKeyList().add(Map.of("id",key.getAndAdd(100)));return 1;}).when(jdbc).update(any(PreparedStatementCreator.class),any(KeyHolder.class));
        var result=service.create(body);assertEquals("100",result.get("batchId"));assertEquals(true,result.get("committed"));
        var inserts=org.mockito.ArgumentCaptor.forClass(PreparedStatementCreator.class);
        verify(jdbc,times(2)).update(inserts.capture(),any(KeyHolder.class));
        var connection=mock(java.sql.Connection.class);var statement=mock(java.sql.PreparedStatement.class);
        when(connection.prepareStatement(anyString(),eq(java.sql.Statement.RETURN_GENERATED_KEYS))).thenReturn(statement);
        inserts.getAllValues().get(0).createPreparedStatement(connection);
        verify(statement).setObject(6,null);
        verify(statement).setObject(eq(5),argThat(snapshot->snapshot.toString().contains("PIECE")));
    }

    @Test void originalSuccessReplaysWithoutRevalidatingChangedOrder(){
        var body=createBody();var normalized=new LinkedHashMap<String,Object>();normalized.put("factoryId","7");normalized.put("orderId","10");normalized.put("orderItemId","11");normalized.put("orderVersion",2);normalized.put("plannedQuantity",30L);
        var original=Map.of("batchId","100","operationId","200","committed",true,"requestId",REQUEST);
        when(jdbc.queryForList(startsWith("SELECT action,request_hash,CAST(result AS CHAR) result FROM metal_production_execution_operation"),eq(7L),eq(5L),eq(REQUEST))).thenReturn(List.of(Map.of("action","CREATE_BATCH","request_hash",StorageSupport.hash(normalized),"result",JSON.toJSONString(original))));
        assertEquals(original,service.create(body));
        verify(jdbc,never()).queryForList(startsWith("SELECT id,version,status"),eq(7L),eq(10L));
        verify(jdbc,never()).update(any(PreparedStatementCreator.class),any(KeyHolder.class));
    }

    @Test void sameRequestWithChangedIntentRejectsBeforeWriting(){
        when(jdbc.queryForList(startsWith("SELECT action,request_hash,CAST(result AS CHAR) result FROM metal_production_execution_operation"),eq(7L),eq(5L),eq(REQUEST))).thenReturn(List.of(Map.of("action","CREATE_BATCH","request_hash","different","result","{}")));
        assertEquals(409,assertThrows(ServiceException.class,()->service.create(createBody())).getCode());
        verify(jdbc,never()).update(any(PreparedStatementCreator.class),any(KeyHolder.class));
    }

    @Test void staleBatchVersionCannotStartAnExecution(){
        when(jdbc.queryForList("SELECT version FROM metal_production_batch WHERE factory_id=? AND id=? FOR UPDATE",7L,100L)).thenReturn(List.of(Map.of("version",1)));
        var body=Map.<String,Object>of("factoryId","7","requestId",REQUEST,"batchVersion",0,"intent","START_STAGE","orderStepId","21","processorId","6");
        assertEquals(409,assertThrows(ServiceException.class,()->service.start(100L,body)).getCode());
        verifyNoInteractions(executions);
    }

    @Test void repeatedStartReturnsOriginalAssociationWithoutOpeningAnotherExecution(){
        var fingerprint=StorageSupport.hash(Map.of("batchId","100","batchVersion",0,"orderStepId","21","processorId","6","factoryId","7","intent","START_STAGE"));
        var original=Map.of("batchId","100","batchVersion",1,"executionId","300","executionVersion",0,"committed",true,"operationId","200","requestId",REQUEST);
        when(jdbc.queryForList(startsWith("SELECT action,request_hash,CAST(result AS CHAR) result FROM metal_production_execution_operation"),eq(7L),eq(5L),eq(REQUEST))).thenReturn(List.of(Map.of("action","START_STAGE","request_hash",fingerprint,"result",JSON.toJSONString(original))));
        var body=Map.<String,Object>of("factoryId","7","requestId",REQUEST,"batchVersion",0,"intent","START_STAGE","orderStepId","21","processorId","6");
        assertEquals(original,service.start(100L,body));
        verifyNoInteractions(executions);
        verify(jdbc,never()).queryForList("SELECT version FROM metal_production_batch WHERE factory_id=? AND id=? FOR UPDATE",7L,100L);
    }

    @Test void startLinksExistingProductionIdentityAndRecordsOperation(){
        when(jdbc.queryForList("SELECT version FROM metal_production_batch WHERE factory_id=? AND id=? FOR UPDATE",7L,100L)).thenReturn(List.of(Map.of("version",0)));
        when(executions.createForBatch(7L,100L,Map.of("orderStepId","21","processorId","6"))).thenReturn(300L);
        doAnswer(call->{call.getArgument(1,KeyHolder.class).getKeyList().add(Map.of("id",200L));return 1;}).when(jdbc).update(any(PreparedStatementCreator.class),any(KeyHolder.class));
        var body=Map.<String,Object>of("factoryId","7","requestId",REQUEST,"batchVersion",0,"intent","START_STAGE","orderStepId","21","processorId","6");
        var result=service.start(100L,body);
        assertEquals("100",result.get("batchId"));assertEquals("300",result.get("executionId"));assertEquals(1,result.get("batchVersion"));assertEquals(true,result.get("committed"));assertEquals("200",result.get("operationId"));
        verify(jdbc).update(eq("UPDATE metal_production_batch SET version=version+1,update_time=? WHERE factory_id=? AND id=?"),any(java.time.LocalDateTime.class),eq(7L),eq(100L));
    }

    @Test void operationNotFoundIsReadOnlyAndDoesNotClaimDefiniteFailure(){
        assertEquals(Map.of("found",false,"committed",false,"requestId",REQUEST),service.operation(REQUEST,7L));
        verify(jdbc,never()).update(anyString(),any(Object[].class));verifyNoInteractions(executions);
    }

    @Test void listDefaultsToActiveAndBoundsPagingBeforeQuerying(){
        assertEquals(400,assertThrows(ServiceException.class,()->service.list(Map.of("factoryId","7","pageSize","65"))).getCode());
        assertEquals(400,assertThrows(ServiceException.class,()->service.list(Map.of("factoryId","7","lifecycle","forged"))).getCode());
        verifyNoInteractions(jdbc);
    }
}
