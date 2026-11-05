package com.ruoyi.metal.service;

import java.util.UUID;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.metal.domain.Factory;
import com.ruoyi.metal.mapper.FactoryMapper;
import com.ruoyi.metal.security.FactoryScope;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="METAL_FLOW_ISOLATED_JDBC_URL",matches=WarehouseIntegrationEnvironment.URL_PATTERN)
class FactoryWarehouseReferenceMysqlTest {
 @ParameterizedTest
 @CsvSource({"2,0,false","0,0,true","0,1,true"})
 void onlyUndeletedWarehousesPreventFactoryDeletion(String deleted,String status,boolean blocked)throws Exception {
  try(var connection=WarehouseIntegrationEnvironment.dataSource().getConnection()){
   connection.setAutoCommit(false);
   try{
    var jdbc=new JdbcTemplate(new SingleConnectionDataSource(connection,true));
    jdbc.update("INSERT INTO metal_factory(factory_name,area,address,create_by) VALUES(?,1,'隔离回归','test')","仓库引用回归_"+UUID.randomUUID());
    long id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
    jdbc.update("INSERT INTO metal_warehouse(factory_id,name,del_flag,status,create_by) VALUES(?,'隔离仓库',?,?,'test')",id,deleted,status);
    var mapper=mock(FactoryMapper.class);var factory=new Factory();factory.setFactoryId(Long.toString(id));factory.setFactoryName("隔离工厂");
    when(mapper.lockFactory(id)).thenReturn(factory);
    var service=new FactoryService(mapper,mock(FactoryScope.class),jdbc);
    if(blocked){assertThrows(ServiceException.class,()->service.delete(new Long[]{id},"test"));verify(mapper,never()).delete(anyLong(),anyString());}
    else{assertDoesNotThrow(()->service.delete(new Long[]{id},"test"));verify(mapper).delete(id,"test");}
   }finally{connection.rollback();}
  }
 }
}
