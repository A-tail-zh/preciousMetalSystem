package com.ruoyi.metal.service;

import java.math.BigDecimal;
import java.util.*;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.metal.catalog.WarehouseRequests.Line;
import com.ruoyi.metal.security.FactoryScope;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ConfigurableWarehouseImportTest {
    private static final class Fixture {
        final JdbcTemplate jdbc=mock(JdbcTemplate.class);
        final Map<String,Object> step=new HashMap<>(),batch=new HashMap<>(),stock=new HashMap<>();
        final WarehouseImportService service;
        Fixture(String mode,int marker,boolean rawTask){
            String snapshot="{\"measurementPolicyVersion\":"+marker+",\"metalTypeId\":1}";
            step.putAll(Map.of("item_id",9L,"work_order_id",8L,"sequence_no",1L,"measurement_mode",mode,"process_role","PREPARATION","output_stage","RAW","processing_policy_version",3,"product_snapshot",snapshot));
            step.put("product_id",rawTask?null:4L);step.put("material_id",rawTask?3L:null);
            batch.putAll(step);batch.putAll(Map.of("batch_id",20L,"planned_quantity",10L,"status","OPEN","factory_id",1L));
            stock.putAll(Map.of("inventory_id",200L,"factory_id",1L,"warehouse_id",2L,"category","RAW","reference_id",3L,"batch_no","R","net_weight",BigDecimal.TEN,"quantity",5L));
            stock.putAll(Map.of("origin_batch_id",101L,"measurement_mode","PIECE","code","M","name","原料","material_ownership","FACTORY","organization","工厂自有","ownership_status","NORMAL"));
            when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(call->{
                String q=call.getArgument(0);
                if(q.contains("metal_warehouse WHERE"))return List.of(Map.of("factory_id",1L,"status","0"));
                if(q.startsWith("SELECT factory_id,status FROM metal_work_order"))return List.of(Map.of("factory_id",1L,"status","IN_PROGRESS"));
                if(q.startsWith("SELECT s.*"))return List.of(new HashMap<>(step));
                if(q.startsWith("SELECT batch_id"))return List.of(Map.of("batch_id",20L));
                if(q.startsWith("SELECT b.batch_id,b.status batch_status"))return List.of(Map.of("batch_status","OPEN","issued_weight",new BigDecimal("100"),"output_weight",BigDecimal.ZERO,"returned_weight",BigDecimal.ZERO));
                if(q.startsWith("SELECT l.*,o.type FROM metal_warehouse_order"))return List.of(new HashMap<>(Map.of("line_id",700L,"inventory_id",300L,"type","PRODUCTION_OUT","category","RAW","measurement_mode","WEIGHT","net_weight",BigDecimal.TEN,"quantity",0L)));
                if(q.startsWith("SELECT inventory_id,origin_batch_id")&&Arrays.asList(call.getArguments()).contains(300L))return List.of(Map.of("inventory_id",300L));
                if(q.contains("CAST(i.product_snapshot AS CHAR)")&&q.contains("FROM metal_process_batch"))return List.of(Map.of("batch_id",101L,"measurement_mode","PIECE","product_snapshot","{\"measurementPolicyVersion\":3}"));
                if(q.startsWith("SELECT * FROM metal_process_batch"))return List.of(new HashMap<>(batch));
                if(q.startsWith("SELECT completion_policy"))return List.of(Map.of("completion_policy","LEGACY"));
                if(q.startsWith("SELECT l.*,o.factory_id")){var source=new HashMap<>(stock);source.putAll(Map.of("line_id",500L,"batch_id",20L,"type","PRODUCTION_OUT","status","POSTED"));return List.of(source);}
                if(q.startsWith("SELECT COALESCE(SUM(l.net_weight)"))return List.of(Map.of("weight",BigDecimal.ZERO,"quantity",0L));
                if(q.contains("FROM metal_inventory"))return List.of(new HashMap<>(stock));
                if(q.contains("FROM metal_material"))return List.of(Map.of("code","M","name","原料","specification","M","material_type","旧料","metal_type_id",1L,"metal_type_name","铱"));
                if(q.contains("FROM metal_product"))return List.of(Map.of("code","P","name","产品","specification","P"));
                if(q.startsWith("SELECT * FROM metal_work_order"))return List.of(Map.of("factory_id",1L,"processing_type","MATERIAL"));
                return List.of();
            });
            when(jdbc.queryForObject(anyString(),eq(Long.class),any(Object[].class))).thenAnswer(c->c.<String>getArgument(0).contains("MAX(sequence_no)")?3L:0L);
            when(jdbc.queryForObject(anyString(),eq(String.class),any(Object[].class))).thenReturn(rawTask?"{}":snapshot);
            service=new WarehouseImportService(jdbc,mock(FactoryScope.class));
        }
        List<Map<String,Object>> preview(String type,Line...lines){return service.inspect(new WarehouseImportService.Input(1L,2L,type,8L,9L,10L,List.of(lines),Map.of(),20L));}
    }
    private Line output(long q,Long reference){return new Line(null,null,"RAW",3L,null,BigDecimal.ONE,q,null,null,null,null,null,null,null,null,null,null,reference);}
    private Line returned(long q,String weight){return new Line(null,500L,"RAW",null,null,new BigDecimal(weight),q,null,null,null,null,null,null);}

    @Test void newProductRawPieceOutputRequiresAndReturnsActualPieces(){
        var f=new Fixture("PIECE",3,false);
        var row=assertDoesNotThrow(()->f.preview("RAW_PROCESS_IN",output(2,null))).get(0);
        assertEquals(2L,row.get("quantity"));assertEquals("PIECE",row.get("effectiveMeasurementMode"));assertEquals(3,row.get("measurementPolicyVersion"));
        assertThrows(ServiceException.class,()->f.preview("RAW_PROCESS_IN",output(0,null)));
    }
    @Test void newProductRawWeightAllowsReferencePiecesWithoutActualPieceCapacity(){
        var row=assertDoesNotThrow(()->new Fixture("WEIGHT",3,false).preview("RAW_PROCESS_IN",output(0,7L))).get(0);
        assertEquals("WEIGHT",row.get("effectiveMeasurementMode"));assertEquals(7L,row.get("referenceQuantity"));assertEquals(0L,row.get("quantity"));
    }
    @Test void legacyAndIndependentRawTasksKeepZeroQuantityAndRejectReferencePieces(){
        for(var f:List.of(new Fixture("WEIGHT",2,false),new Fixture("WEIGHT",3,true))){
            var row=assertDoesNotThrow(()->f.preview("RAW_PROCESS_IN",output(0,null))).get(0);
            assertEquals("WEIGHT",row.get("effectiveMeasurementMode"));assertEquals(0L,row.get("quantity"));
            assertThrows(ServiceException.class,()->f.preview("RAW_PROCESS_IN",output(0,7L)));
            assertThrows(ServiceException.class,()->f.preview("RAW_PROCESS_IN",output(1,null)));
        }
    }
    @Test void rawPieceReturnsFollowSourceModeEvenWhenTargetStepIsWeight(){
        var row=assertDoesNotThrow(()->new Fixture("WEIGHT",3,false).preview("RAW_RETURN",returned(2,"1"))).get(0);
        assertEquals(2L,row.get("quantity"));assertEquals("PIECE",row.get("effectiveMeasurementMode"));
    }
    @Test void zeroWeightRawPieceReturnCannotCarryPieces(){
        var error=assertThrows(ServiceException.class,()->new Fixture("WEIGHT",3,false).preview("RAW_RETURN",returned(1,"0")));
        assertTrue(error.getMessage().contains("净重"));
    }
    @Test void repeatedRawPieceReturnLinesShareTheOriginalQuantityAllowance(){
        var f=new Fixture("WEIGHT",3,false);
        assertEquals(2,assertDoesNotThrow(()->f.preview("RAW_RETURN",returned(2,"1"),returned(3,"1"))).size());
        assertTrue(assertThrows(ServiceException.class,()->f.preview("RAW_RETURN",returned(3,"1"),returned(3,"1"))).getMessage().contains("累计退还"));
    }
    @Test void rawPieceOutputCannotExceedTheBatchPlan(){
        var f=new Fixture("PIECE",3,false);
        assertTrue(assertThrows(ServiceException.class,()->f.preview("RAW_PROCESS_IN",output(11,null))).getMessage().contains("批次计划"));
    }
    @Test void rawPieceProductionIssueUsesRealStockModeAndRequiresPositiveQuantity(){
        var f=new Fixture("PIECE",3,false);
        try(var processing=mockStatic(ProcessingInputService.class);var plans=mockStatic(RawPlanService.class);var recovery=mockConstruction(ProcessRawReturnService.class)){
            var line=new Line(200L,null,null,null,null,BigDecimal.ONE,2L,null,null,null,null,null,null);
            var row=assertDoesNotThrow(()->f.preview("PRODUCTION_OUT",line)).get(0);
            assertEquals(2L,row.get("quantity"));assertEquals("PIECE",row.get("effectiveMeasurementMode"));assertEquals(3,row.get("measurementPolicyVersion"));
            var zero=new Line(200L,null,null,null,null,BigDecimal.ONE,0L,null,null,null,null,null,null);
            assertThrows(ServiceException.class,()->f.preview("PRODUCTION_OUT",zero));
        }
    }
    @Test void oneImportCannotMixPieceAndWeightSemiInTheSamePieceBatch(){
        var f=new Fixture("PIECE",3,false);f.step.put("process_role","POST_PROCESS");f.step.put("output_stage","SEMI");
        doAnswer(call->{long id=((Number)call.getArgument(1)).longValue();var stock=new HashMap<>(f.stock);stock.put("inventory_id",id);stock.put("category","SEMI");stock.put("measurement_mode",id==200L?"PIECE":"WEIGHT");return List.of(stock);}).when(f.jdbc).queryForList(eq("SELECT * FROM metal_inventory WHERE inventory_id=? AND del_flag='0'"),any(Object[].class));
        try(var processing=mockStatic(ProcessingInputService.class);var semi=mockConstruction(SemiFlowService.class,(mock,context)->when(mock.sourceBatch(anyMap())).thenReturn(Map.of("sequence_no",1L)));
            var policy=mockConstruction(SemiSourcePolicyService.class,(mock,context)->when(mock.decide(anyMap(),anyMap(),any(),any())).thenReturn(Map.of("processed",List.of(1L),"skipped",List.of(),"newSkipped",List.of())))){
            processing.when(()->ProcessingInputService.requireBatchModes(eq(f.jdbc),anyMap(),anyList(),any())).thenCallRealMethod();
            var piece=new Line(200L,null,null,null,null,BigDecimal.ONE,1L,null,null,null,null,null,null);
            var weight=new Line(201L,null,null,null,null,BigDecimal.ONE,0L,null,null,null,null,null,null);
            var service=new WarehouseImportService(f.jdbc,mock(FactoryScope.class));
            var input=new WarehouseImportService.Input(1L,2L,"PRODUCTION_OUT",8L,9L,10L,List.of(piece,weight),Map.of(),20L);
            assertTrue(assertThrows(ServiceException.class,()->service.inspect(input)).getMessage().contains("分批"));
        }
    }
    @Test void contiguousPieceRawOutputUsesNetIssuedQuantityAndWeightInputAllowsRebuildingPieces(){
        var f=new Fixture("PIECE",3,false);f.step.put("sequence_no",2L);f.batch.put("sequence_no",2L);
        try(var weight=mockStatic(WeightStageProgressService.class);var semi=mockStatic(SemiFlowService.class)){
            weight.when(()->WeightStageProgressService.hasWeightInput(eq(f.jdbc),anyMap())).thenReturn(false);
            semi.when(()->SemiFlowService.netIssued(eq(f.jdbc),anyMap())).thenReturn(5L);
            assertTrue(assertThrows(ServiceException.class,()->f.preview("RAW_PROCESS_IN",output(6,null))).getMessage().contains("实际领用件数"));
            weight.when(()->WeightStageProgressService.hasWeightInput(eq(f.jdbc),anyMap())).thenReturn(true);
            assertEquals(6L,assertDoesNotThrow(()->f.preview("RAW_PROCESS_IN",output(6,null))).get(0).get("quantity"));
        }
    }
}
