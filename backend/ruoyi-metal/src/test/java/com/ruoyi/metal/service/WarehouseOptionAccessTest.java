package com.ruoyi.metal.service;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.metal.security.FactoryScope;
class WarehouseOptionAccessTest {
 final JdbcTemplate jdbc=mock(JdbcTemplate.class);
 final FactoryScope scope=mock(FactoryScope.class);
 final WarehouseService service=new WarehouseService(jdbc,scope);
 void login(String permission){var user=new LoginUser();user.setPermissions(Set.of(permission));SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,List.of()));}
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 @Test void warehouseListCannotReadSensitiveOrUnneededOptions(){login("metal:warehouse:list");for(String kind:List.of("stocks","out-lines","people","materials","products","work-orders","batches","warehouses","processes")){var error=assertThrows(ServiceException.class,()->service.options(kind,3L,null),kind);assertEquals(403,error.getCode());}verifyNoInteractions(jdbc);}
 @Test void unrelatedListPermissionsCannotReadStocksOrOutLines(){for(String permission:List.of("metal:warning-record:list","metal:warning-rule:list","metal:process-batch:list")){login(permission);for(String kind:List.of("stocks","out-lines"))assertThrows(ServiceException.class,()->service.options(kind,3L,null));}verifyNoInteractions(jdbc);}
 @Test void orderListDoesNotGrantSourceLineDetails(){login("metal:warehouse-order:list");assertThrows(ServiceException.class,()->service.options("out-lines",3L,null));verifyNoInteractions(jdbc);login("metal:warehouse-order:query");assertDoesNotThrow(()->service.options("out-lines",3L,null));}
 @Test void inventoryReadDoesNotGrantSourceDocuments(){login("metal:inventory:list");assertDoesNotThrow(()->service.options("stocks",3L,null));assertThrows(ServiceException.class,()->service.options("out-lines",3L,null));verify(scope).check(3L);}
 @Test void addOnlyOrderCanLoadRequiredOptions(){login("metal:warehouse-order:add");for(String kind:List.of("factories","warehouses","people","stocks","out-lines","materials","products","work-orders","batches"))assertDoesNotThrow(()->service.options(kind,3L,null),kind);}
 @Test void openingAndAdjustmentOnlyPermissionsRemainUsable(){login("metal:inventory:add");for(String kind:List.of("factories","warehouses","people","materials","products"))assertDoesNotThrow(()->service.options(kind,3L,null),kind);assertThrows(ServiceException.class,()->service.options("stocks",3L,null));login("metal:inventory:adjust");assertDoesNotThrow(()->service.options("people",3L,null));assertDoesNotThrow(()->service.options("stocks",3L,null));assertThrows(ServiceException.class,()->service.options("out-lines",3L,null));}
 @Test void authorizedStillChecksFactoryBeforeQuery(){login("metal:warehouse-order:add");doThrow(new ServiceException("跨厂",403)).when(scope).check(9L);assertThrows(ServiceException.class,()->service.options("stocks",9L,null));verifyNoInteractions(jdbc);}
 @Test void wildcardAndAdminWorkButUnknownKindsNeverQuery(){login("metal:inventory:*");assertDoesNotThrow(()->service.options("stocks",3L,null));when(scope.isAdmin()).thenReturn(true);assertDoesNotThrow(()->service.options("out-lines",3L,null));assertThrows(ServiceException.class,()->service.options("unknown",3L,null));}
}
