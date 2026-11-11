package com.ruoyi.metal.service;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.ruoyi.metal.security.FactoryScope;
import com.ruoyi.common.exception.ServiceException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class OutboundBalanceValidationTest {
 final JdbcTemplate jdbc=mock(JdbcTemplate.class);
 final StorageOrderService service=new StorageOrderService(jdbc,mock(FactoryScope.class),mock(StorageLedgerService.class),mock(ProductionExecutionService.class));
 Map<String,Object> line(String weight,String mode,String count){return new LinkedHashMap<>(Map.of("stockId","3","netWeight",weight,"measurementMode",mode,"quantity",count,"allocations",List.of(Map.of("ownerId","0","weight",weight,"quantity",count))));}
 Map<String,Object> doc(){return Map.of("type","PRODUCTION_OUT");}
 void balances(String physical,String weight,String count){when(jdbc.queryForList("SELECT batch_no,net_weight,quantity FROM metal_storage_stock WHERE factory_id=? AND id=? FOR UPDATE",7L,3L)).thenReturn(List.of(Map.of("batch_no","ST3","net_weight",physical,"quantity",count)));when(jdbc.queryForList("SELECT weight,quantity FROM metal_storage_ownership WHERE factory_id=? AND stock_id=? AND owner_id=? FOR UPDATE",7L,3L,0L)).thenReturn(List.of(Map.of("weight",weight,"quantity",count)));}
 @Test void missingFactoryOwnershipRejectedBeforeReview(){balances("505","0","0");assertTrue(assertThrows(ServiceException.class,()->service.checkOutboundBalances(7,doc(),List.of(line("100","WEIGHT","0")))).getMessage().contains("工厂自有"));}
 @Test void cumulativeLinesCannotReuseOneShare(){balances("100","100","0");assertThrows(ServiceException.class,()->service.checkOutboundBalances(7,doc(),List.of(line("60","WEIGHT","0"),line("60","WEIGHT","0"))));}
 @Test void exactBalancePassesWithoutWrites(){balances("100","100","0");service.checkOutboundBalances(7,doc(),List.of(line("60","WEIGHT","0"),line("40","WEIGHT","0")));assertTrue(mockingDetails(jdbc).getInvocations().stream().allMatch(i->i.getMethod().getName().equals("queryForList")));}
 @Test void pieceCountAndWeightAreBothChecked(){balances("100","100","2");assertThrows(ServiceException.class,()->service.checkOutboundBalances(7,doc(),List.of(line("10","PIECE","3"))));balances("5","5","2");assertThrows(ServiceException.class,()->service.checkOutboundBalances(7,doc(),List.of(line("10","PIECE","1"))));}
 @Test void actualReviewQuantitiesAreRechecked(){balances("100","100","0");var l=line("100","WEIGHT","0");l.put("sentWeight","101");assertThrows(ServiceException.class,()->service.checkOutboundBalances(7,Map.of("type","PRODUCTION_OUT","status","PENDING_REVIEW"),List.of(l)));}
 @Test void otherFactoryStockCannotPass(){assertThrows(ServiceException.class,()->service.checkOutboundBalances(8,doc(),List.of(line("1","WEIGHT","0"))));}
}
