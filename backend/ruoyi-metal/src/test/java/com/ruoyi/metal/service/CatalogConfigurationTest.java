package com.ruoyi.metal.service;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.ruoyi.metal.catalog.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class CatalogConfigurationTest {
 private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
 private final StorageConfigurationService service=new StorageConfigurationService(jdbc,FactoryCatalogTestFixtures.scope());
 private ProductRecord product(String...semantics){
  var p=new ProductRecord();p.setFactoryId(7L);var steps=new ArrayList<ProductStep>();var route=new ArrayList<Map<String,Object>>();
  for(int i=0;i<semantics.length;i++){long id=i+1;var step=new ProductStep();step.setProcessId(id);steps.add(step);var r=new LinkedHashMap<String,Object>();r.put("processId",String.valueOf(id));r.put("measurementMode","PIECE");r.put("outputCategory","SEMI");r.put("outputMaterialId","8");r.put("semanticCode","FORGED");route.add(r);when(jdbc.queryForList(startsWith("SELECT * FROM metal_process"),eq(id),eq(7L))).thenReturn(List.of(Map.of("semantic_code",semantics[i],"process_name","步骤"+id)));}
  p.setProcesses(steps);p.setStorageConfiguration(new LinkedHashMap<>(Map.of("steps",route)));return p;
 }
 @SuppressWarnings("unchecked") private List<Map<String,Object>> route(ProductRecord p){return (List<Map<String,Object>>)p.getStorageConfiguration().get("steps");}
 @Test void ordinaryRouteUsesDatabaseSemanticAndAutomaticFinalCategory(){var p=product("NORMAL","NORMAL");service.prepareCatalog("product",p);assertEquals("SEMI",route(p).get(0).get("outputCategory"));assertEquals("PRODUCT",route(p).get(1).get("outputCategory"));assertEquals("NORMAL",route(p).get(0).get("semanticCode"));}
 @Test void acidAndSmeltingDeriveOutputsFromCatalog(){var p=product("ACID_WASH","SMELTING","NORMAL");when(jdbc.queryForList(startsWith("SELECT * FROM metal_material"),eq(8L),eq(7L))).thenReturn(List.of(Map.of("measurement_mode","WEIGHT","material_form","METAL","material_code","M8")));service.prepareCatalog("product",p);assertEquals(List.of("RAW","SEMI","PRODUCT"),route(p).stream().map(r->r.get("outputCategory")).toList());assertEquals("WEIGHT",route(p).get(0).get("measurementMode"));assertEquals("METAL",route(p).get(0).get("outputForm"));}
 @Test void acidRejectsPowderAndForeignMaterial(){var p=product("ACID_WASH","NORMAL");when(jdbc.queryForList(startsWith("SELECT * FROM metal_material"),eq(8L),eq(7L))).thenReturn(List.of(Map.of("measurement_mode","WEIGHT","material_form","POWDER","material_code","M8")));assertThrows(RuntimeException.class,()->service.prepareCatalog("product",p));when(jdbc.queryForList(startsWith("SELECT * FROM metal_material"),eq(8L),eq(7L))).thenReturn(List.of());assertThrows(RuntimeException.class,()->service.prepareCatalog("product",p));verify(jdbc,never()).update(anyString(),any(Object[].class));}
 @Test void legacyMissingConfigurationDoesNotWriteDefaults(){var m=new MaterialRecord();m.setId(8L);m.setFactoryId(7L);service.prepareCatalog("material",m);service.writeCatalog("material",m);verifyNoInteractions(jdbc);}
 @Test void adjustmentLabelsUseFactoryScopedStockAndOwner(){var orders=new StorageOrderService(jdbc,FactoryCatalogTestFixtures.scope(),mock(StorageLedgerService.class),mock(ProductionExecutionService.class));
 when(jdbc.queryForList(startsWith("SELECT batch_no"),eq(7L),eq("9"))).thenReturn(List.of(Map.of("batch_no","ST9","category","RAW","measurement_mode","WEIGHT","identity_snapshot","{\"code\":\"M9\",\"name\":\"金属料\"}")));
 var line=new LinkedHashMap<String,Object>(Map.of("stockId","9","ownerId","0","ownerName","伪造","name","伪造","deltaWeight","100"));orders.fillAdjustmentDisplay(7L,line);assertEquals("工厂自有",line.get("ownerName"));assertEquals("金属料",line.get("name"));assertEquals("ST9",line.get("batchNo"));assertEquals("100",line.get("deltaWeight"));
 orders.fillAdjustmentDisplay(8L,line);assertEquals("物料资料不可用",line.get("name"));assertNull(line.get("measurementMode"));}
}
