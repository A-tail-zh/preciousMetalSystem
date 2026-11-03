package com.ruoyi.metal.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.support.KeyHolder;
import com.ruoyi.metal.security.FactoryScope;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;

class WarehouseHistoricalLossSettlementTest {
 private void settle(String unit,String mode,String theory,String expected,String status)throws Exception{try(var security=mockStatic(SecurityUtils.class)){var user=new SysUser();user.setNickName("验收");var login=new LoginUser();login.setUser(user);security.when(SecurityUtils::getLoginUser).thenReturn(login);security.when(SecurityUtils::getUserId).thenReturn(7L);
  var jdbc=mock(JdbcTemplate.class);var service=spy(new WarehouseService(jdbc,mock(FactoryScope.class)));doNothing().when(service).refreshOrder(anyLong());var semi=mock(SemiFlowService.class);var field=WarehouseService.class.getDeclaredField("semi");field.setAccessible(true);field.set(service,semi);
  boolean weight=mode.equals("WEIGHT");var step=new HashMap<String,Object>(Map.of("item_id",2L,"sequence_no",1L,"product_id",3L,"product_snapshot","{}","theoretical_loss",new BigDecimal(theory),"loss_unit",unit,"measurement_mode",mode));var reports=new ArrayList<List<Object>>();var warnings=new ArrayList<List<Object>>();
  when(jdbc.queryForObject(anyString(),eq(BigDecimal.class),any(Object[].class))).thenReturn(BigDecimal.ZERO);when(jdbc.queryForObject(anyString(),eq(Long.class),any(Object[].class))).thenReturn(1L);
  when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(c->{String q=c.getArgument(0);if(q.startsWith("SELECT s.*"))return List.of(step);if(q.startsWith("SELECT COALESCE(SUM(CASE"))return List.of(Map.of("issued",new BigDecimal("100"),"returned",new BigDecimal("99.80"),"output_qty",weight?0L:100L));if(q.startsWith("SELECT COALESCE(SUM(COALESCE"))return List.of(Map.of("weight",new BigDecimal("99.80")));if(q.startsWith("SELECT l.code"))return List.of(Map.of("code","IR","name","铱","weight",new BigDecimal("100")));if(q.startsWith("SELECT order_no"))return List.of(Map.of("order_no","TEST"));return List.of();});
  doAnswer(c->{PreparedStatementCreator creator=c.getArgument(0);KeyHolder key=c.getArgument(1);var connection=mock(Connection.class);when(connection.prepareStatement(anyString(),anyInt())).thenAnswer(call->{String q=call.getArgument(0);var values=new ArrayList<Object>();if(q.startsWith("INSERT INTO metal_work_report("))reports.add(values);if(q.startsWith("INSERT INTO metal_warning_record("))warnings.add(values);var statement=mock(PreparedStatement.class);doAnswer(set->{int index=set.getArgument(0);while(values.size()<index)values.add(null);values.set(index-1,set.getArgument(1));return null;}).when(statement).setObject(anyInt(),any());return statement;});creator.createPreparedStatement(connection);key.getKeyList().add(Map.of("GENERATED_KEY",1L));return 1;}).when(jdbc).update(any(PreparedStatementCreator.class),any(KeyHolder.class));
  var batch=Map.<String,Object>of("batch_id",1L,"factory_id",2L,"work_order_id",4L,"item_id",2L,"process_id",3L,"sequence_no",1L,"status","OPEN","measurement_mode",mode,"planned_quantity",100L,"completion_policy","LEGACY");var method=WarehouseService.class.getDeclaredMethod("settleInternal",Map.class,Integer.class);method.setAccessible(true);method.invoke(service,batch,weight?0:100);
  assertEquals(1,reports.size());assertEquals(0,new BigDecimal(expected).compareTo((BigDecimal)reports.get(0).get(9)));assertEquals(status,reports.get(0).get(14));assertEquals(new BigDecimal(theory),step.get("theoretical_loss"),"不得改写读取到的历史快照");assertEquals(status.equals("ABNORMAL")?1:0,warnings.size());if(!warnings.isEmpty()){assertEquals(reports.get(0).get(9),warnings.get(0).get(7));assertEquals(reports.get(0).get(11),warnings.get(0).get(6));}
 }}
 @Test void historicalSixDecimalLossKeepsOriginalThresholdAndNormalResult()throws Exception{settle("G_PER_PIECE","PIECE","0.004001","0.004001","NORMAL");}
 @Test void historicalSmallThresholdStillGeneratesAnActualWarning()throws Exception{settle("G_PER_PIECE","PIECE","0.001","0.001","ABNORMAL");}
 @Test void newPercentageStillUsesTwoDecimalThreshold()throws Exception{settle("PERCENT","PIECE","0.195","0.20","NORMAL");}
 @Test void weightModePercentageKeepsTheSameThresholdRule()throws Exception{settle("PERCENT","WEIGHT","0.195","0.20","NORMAL");}
}
