package com.ruoyi.metal.service;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.ruoyi.metal.security.FactoryScope;
import com.ruoyi.common.exception.ServiceException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class StorageOrderServiceTest {
 @Test void headerNamesAreBatchedAndReplaceUntrustedDisplayValues(){
  when(jdbc.queryForList("SELECT id,order_no FROM metal_production_order WHERE factory_id=? AND id IN (?,?)",7L,10L,99L)).thenReturn(List.of(Map.of("id",10L,"order_no","GD10")));
  when(jdbc.queryForList("SELECT user_id,nick_name,user_name FROM sys_user WHERE (factory_id=? OR user_id=1) AND user_id IN (?,?,?,?)",7L,1L,20L,21L,99L)).thenReturn(List.of(Map.of("user_id",1L,"nick_name","admin","user_name","admin"),Map.of("user_id",20L,"nick_name","仓管甲","user_name","account20"),Map.of("user_id",21L,"nick_name"," ","user_name","account21")));
  var rows=new ArrayList<Map<String,Object>>();
  for(int i=0;i<64;i++)rows.add(new LinkedHashMap<>(Map.of("orderId","10","creatorId","1","reviewerId","20","orderNumber","forged","creatorName","forged","reviewerName","forged")));
  rows.add(new LinkedHashMap<>(Map.of("orderId","99","creatorId","21","reviewerId","99")));rows.add(new LinkedHashMap<>());
  service.fillHeaderNames(7,rows);assertEquals(66,rows.size());
  for(int i=0;i<64;i++){assertEquals("GD10",rows.get(i).get("orderNumber"));assertEquals("管理员",rows.get(i).get("creatorName"));assertEquals("仓管甲",rows.get(i).get("reviewerName"));}
  assertEquals("关联工单不可用",rows.get(64).get("orderNumber"));assertEquals("account21",rows.get(64).get("creatorName"));assertEquals("人员#99（资料不可用）",rows.get(64).get("reviewerName"));assertEquals("—",rows.get(65).get("orderNumber"));assertEquals("—",rows.get(65).get("reviewerName"));
  verify(jdbc).queryForList("SELECT id,order_no FROM metal_production_order WHERE factory_id=? AND id IN (?,?)",7L,10L,99L);
  verify(jdbc).queryForList("SELECT user_id,nick_name,user_name FROM sys_user WHERE (factory_id=? OR user_id=1) AND user_id IN (?,?,?,?)",7L,1L,20L,21L,99L);verifyNoMoreInteractions(jdbc);
 }
 @Test void emptyHeaderPageDoesNotReadRelatedTables(){service.fillHeaderNames(7,List.of());verifyNoInteractions(jdbc);}
 @Test void mixedSourcesKeepIndependentBearersAndValidateTotals(){
  var a=new HashMap<String,Object>(Map.of("ownerId","8","weight","6.00","receivedWeight","5.00","quantity",6,"receivedQuantity",5,"lossBearer","CUSTOMER"));
  var b=new HashMap<String,Object>(Map.of("ownerId","0","weight","4.00","receivedWeight","3.00","quantity",4,"receivedQuantity",3,"lossBearer","FACTORY"));
  var line=new HashMap<String,Object>(Map.of("netWeight","10.00","actualNetWeight","8.00","issuedNetWeight","10.00","quantity",10,"actualQuantity",8,"issuedQuantity",10,"measurementMode","PIECE","differenceMode","LOSS","differenceReason","逐项交接损失","allocations",List.of(a,b)));
  var forged=new HashMap<>(line);forged.put("actualNetWeight","9.00");assertThrows(ServiceException.class,()->service.prepareActual(forged,"PRODUCTION_OUT"));
  b.remove("lossBearer");assertThrows(ServiceException.class,()->service.prepareActual(new HashMap<>(line),"PRODUCTION_OUT"));b.put("lossBearer","FACTORY");
  service.prepareActual(line,"PRODUCTION_OUT");assertEquals("10.00",line.get("sentWeight"));assertEquals("8.00",line.get("netWeight"));assertNull(line.get("lossBearer"));assertEquals("CUSTOMER",a.get("lossBearer"));assertEquals("FACTORY",b.get("lossBearer"));
 }
 @Test void mixedReweighMustUseEqualSourceSentAndReceived(){
  var a=new HashMap<String,Object>(Map.of("weight","0.10","receivedWeight","0.10"));var b=new HashMap<String,Object>(Map.of("weight","0.20","receivedWeight","0.20"));
  var line=new HashMap<String,Object>(Map.of("netWeight","0.30","actualNetWeight","0.30","issuedNetWeight","0.30","measurementMode","WEIGHT","differenceMode","REWEIGH","allocations",List.of(a,b)));
  service.prepareActual(line,"PRODUCTION_OUT");assertEquals("0.30",line.get("netWeight"));a.put("weight","0.11");assertThrows(ServiceException.class,()->service.prepareActual(line,"PRODUCTION_OUT"));
 }
 final JdbcTemplate jdbc=mock(JdbcTemplate.class);final FactoryScope scope=mock(FactoryScope.class);
 final StorageOrderService service=new StorageOrderService(jdbc,scope,mock(StorageLedgerService.class),mock(ProductionExecutionService.class));
 @Test void foreignScopeRejectedBeforeAnyRead(){when(scope.isAdmin()).thenReturn(true);when(scope.resolveFactory(8L)).thenThrow(new ServiceException("禁止跨厂",403));assertThrows(ServiceException.class,()->service.list(Map.of("factoryId","8")));assertThrows(ServiceException.class,()->service.detail(1L,8L));assertThrows(ServiceException.class,()->service.options(8L,null));verifyNoInteractions(jdbc);}
 @Test void reweighUsesActualWithoutCreatingNegativeLoss(){var line=new HashMap<String,Object>(Map.of("netWeight","10.00","actualNetWeight","10.25","differenceMode","REWEIGH","differenceReason","复称修正"));service.prepareActual(line,"CUSTOMER_IN");assertEquals("10.25",line.get("sentWeight"));assertEquals("10.25",line.get("netWeight"));assertEquals("10.00",line.get("declaredNetWeight"));}
 @Test void lossKeepsSentAndReceivedSeparate(){var line=new HashMap<String,Object>(Map.of("netWeight","10","actualNetWeight","9","differenceMode","LOSS","differenceReason","交接损失","lossBearer","FACTORY"));service.prepareActual(line,"PRODUCTION_OUT");assertEquals("10.00",line.get("sentWeight"));assertEquals("9.00",line.get("netWeight"));}
 @Test void pieceLossNeedsExplicitBearerEvenWithoutWeightLoss(){var line=new HashMap<String,Object>(Map.of("netWeight","10","actualNetWeight","10","measurementMode","PIECE","quantity",10,"actualQuantity",9,"differenceMode","LOSS","differenceReason","缺件"));assertThrows(ServiceException.class,()->service.prepareActual(line,"RAW_RETURN"));}
 @Test void negativeLossAndUnexplainedReweighRejected(){var line=new HashMap<String,Object>(Map.of("netWeight","10","actualNetWeight","11","differenceMode","LOSS","differenceReason","复称"));assertThrows(ServiceException.class,()->service.prepareActual(line,"RAW_IN"));line.put("differenceMode","REWEIGH");line.remove("differenceReason");assertThrows(ServiceException.class,()->service.prepareActual(line,"RAW_IN"));}
 @Test void decimalsAndCanonicalRequestsAreStrict(){for(String value:List.of("-1","1e2","0.001","NaN","100000000000000"))assertThrows(ServiceException.class,()->StorageSupport.decimal(value,false));assertThrows(ServiceException.class,()->StorageSupport.quantity("1.10"));assertThrows(ServiceException.class,()->StorageSupport.quantity("2147483648"));assertEquals(StorageSupport.hash(Map.of("b",1,"a",2)),StorageSupport.hash(Map.of("a",2,"b",1)));assertNotEquals(StorageSupport.hash(Map.of("a",1)),StorageSupport.hash(Map.of("a",2)));}

 @Test void missingReviewLabelsUseFactoryScopedSnapshotAndCustomer(){
  when(jdbc.queryForList("SELECT CAST(identity_snapshot AS CHAR) snapshot FROM metal_storage_stock WHERE factory_id=? AND id=?",7L,"3")).thenReturn(List.of(Map.of("snapshot","{\"code\":\"M3\",\"materialType\":\"旧料\"}")));
  when(jdbc.queryForList("SELECT customer_name name FROM metal_customer WHERE factory_id=? AND customer_id=?",7L,"8")).thenReturn(List.of(Map.of("name","客户甲")));
  var line=new LinkedHashMap<String,Object>(Map.of("stockId","3","name","","netWeight","500.00","allocations",List.of(Map.of("ownerId","8","weight","500.00"),Map.of("ownerId","0","weight","0.00"))));
  service.fillDisplayNames(7,line);assertEquals("旧料",line.get("name"));assertEquals("500.00",line.get("netWeight"));var allocations=StorageSupport.maps(line.get("allocations"));assertEquals("客户甲",allocations.get(0).get("ownerName"));assertEquals("工厂自有",allocations.get(1).get("ownerName"));assertEquals("500.00",allocations.get(0).get("weight"));
 }
 @Test void existingDisplaySnapshotsStayUnchanged(){
  var line=new LinkedHashMap<String,Object>(Map.of("name","保存时物料","allocations",List.of(Map.of("ownerId","8","ownerName","保存时客户"))));service.fillDisplayNames(7,line);assertEquals("保存时物料",line.get("name"));assertEquals("保存时客户",StorageSupport.maps(line.get("allocations")).get(0).get("ownerName"));verifyNoInteractions(jdbc);
 }

 @Test void singleSourceActualsFollowMeasuredTotalsBeforeReservationCeilingCheck(){
  var line=new LinkedHashMap<String,Object>(Map.of("netWeight","50.00","actualNetWeight","40.00","measurementMode","PIECE","quantity",5,"actualQuantity",4,"differenceMode","REWEIGH","differenceReason","实测少领","allocations",List.of(Map.of("ownerId","0","weight","50.00","quantity",5))));
  service.prepareActual(line,"PRODUCTION_OUT");var allocation=StorageSupport.maps(line.get("allocations")).get(0);assertEquals("40.00",allocation.get("weight"));assertEquals("40.00",allocation.get("receivedWeight"));assertEquals("4",allocation.get("quantity"));assertEquals("4",allocation.get("receivedQuantity"));
 }

 @Test void reviewCannotEmptyDropDuplicateOrReplaceDeclaredSources(){
  var line=Map.<String,Object>of("allocations",List.of(Map.of("ownerId","0"),Map.of("ownerId","8")));
  for(var sources:List.of(List.<Map<String,Object>>of(),List.<Map<String,Object>>of(Map.of("ownerId","0")),List.<Map<String,Object>>of(Map.of("ownerId","0"),Map.of("ownerId","0")),List.<Map<String,Object>>of(Map.of("ownerId","0"),Map.of("ownerId","9"))))assertThrows(ServiceException.class,()->service.checkReviewSources(line,Map.of("allocations",sources)));
  assertDoesNotThrow(()->service.checkReviewSources(line,Map.of("allocations",List.of(Map.of("ownerId","8"),Map.of("ownerId","0")))));
 }

 @Test void weightReviewDoesNotRequireActualPieceQuantity(){
  for(String type:List.of("RAW_OUT","PRODUCTION_OUT"))for(String shape:List.of("missing","null","zero")){var line=new LinkedHashMap<String,Object>(Map.of("measurementMode","WEIGHT","netWeight","40.00","actualNetWeight","38.00","quantity",0,"differenceMode","REWEIGH","differenceReason","按重量复称","allocations",List.of(Map.of("ownerId","8","weight","40.00","quantity",0))));if(shape.equals("missing"))line.remove("quantity");else for(String key:List.of("quantity","actualQuantity","issuedQuantity"))line.put(key,shape.equals("null")?null:0);service.prepareActual(line,type);assertEquals("38.00",line.get("sentWeight"));assertEquals(0,line.get("quantity"));assertEquals(0,line.get("sentQuantity"));if(type.equals("PRODUCTION_OUT"))assertEquals(0,StorageSupport.maps(line.get("allocations")).get(0).get("quantity"));}
 }
}
