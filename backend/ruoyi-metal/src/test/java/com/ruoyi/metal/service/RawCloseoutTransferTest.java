package com.ruoyi.metal.service;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import com.ruoyi.metal.catalog.WarehouseRequests.Line;
import com.ruoyi.metal.catalog.WarehouseRequests.Order;
import com.ruoyi.metal.security.FactoryScope;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.KeyHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RawCloseoutTransferTest {
    private record Insert(String sql,Map<Integer,Object> bindings) {
        Map<String,Object> values(){
            int start=sql.indexOf('('),end=sql.indexOf(')',start);
            String[] columns=sql.substring(start+1,end).split(",");
            int values=sql.indexOf("VALUES(");
            if(values<0)throw new AssertionError("Expected value insert: "+sql);
            String[] expressions=sql.substring(values+7,sql.lastIndexOf(')')).split(",");
            assertEquals(columns.length,expressions.length);
            var result=new HashMap<String,Object>();int parameter=1;
            for(int i=0;i<columns.length;i++){
                String expression=expressions[i].trim();Object value;
                if(expression.equals("?"))value=bindings.get(parameter++);
                else if(expression.startsWith("'"))value=expression.substring(1,expression.length()-1);
                else value=Long.valueOf(expression);
                result.put(columns[i].trim(),value);
            }
            return result;
        }
    }
    private static final class Fixture {
        final JdbcTemplate jdbc=mock(JdbcTemplate.class);
        final List<Insert> inserts=new ArrayList<>();
        final Map<String,Object> source=new HashMap<>(),target=new HashMap<>();
        final boolean storage,existing;final int marker;
        Fixture(boolean storage,boolean existing,int marker)throws Exception {
            this.storage=storage;this.existing=existing;this.marker=marker;
            source.putAll(Map.of("inventory_id",1L,"id",1L,"factory_id",43L,"warehouse_id",7L,"category","RAW","reference_id",8L,"batch_no","R","net_weight",BigDecimal.ZERO,"quantity",0L));
            source.putAll(Map.of("origin_batch_id",100L,"measurement_mode","PIECE","material_ownership","CUSTOMER","customer_id",9L,"organization","客户","code","M","name","原料","specification","M","raw_material_kind","UNPROCESSED","raw_origin_key","B:100"));
            target.putAll(source);target.putAll(Map.of("inventory_id",2L,"id",2L,"material_ownership","FACTORY","organization","工厂自有","del_flag","0"));target.remove("customer_id");
            when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(call->{String q=call.getArgument(0);
                if(q.startsWith("SELECT o.*,f.closeout_id"))return List.of(Map.of("status","WAITING_IN","type",storage?"RESIDUAL_STORAGE":"OWNERSHIP_IN","mode",storage?"STORAGE":"OFFSET"));
                if(q.startsWith("SELECT t.*,t.inventory_id"))return List.of(new HashMap<>(((Number)call.getArgument(1)).longValue()==1L?source:target));
                if(q.startsWith("SELECT inventory_id,origin_batch_id"))return List.of(Map.of("inventory_id",1L,"origin_batch_id",100L),Map.of("inventory_id",2L,"origin_batch_id",100L));
                if(q.startsWith("SELECT b.batch_id,b.measurement_mode,CAST(i.product_snapshot"))return List.of(Map.of("batch_id",100L,"measurement_mode","PIECE","product_snapshot","{\"measurementPolicyVersion\":"+marker+"}"));
                if(q.startsWith("SELECT line_id FROM metal_process_raw_return_line"))return List.of();
                if(q.startsWith("SELECT *,inventory_id id FROM metal_inventory"))return existing?List.of(new HashMap<>(target)):List.of();
                if(q.startsWith("SELECT o.work_order_id,o.type,b.completion_policy"))return List.of(Map.of("work_order_id",10L,"type","OWNERSHIP_IN"));
                if(q.startsWith("SELECT reference_quantity FROM metal_warehouse_order_line"))return List.of(new HashMap<String,Object>());
                if(q.startsWith("SELECT type FROM metal_warehouse_order"))return List.of(Map.of("type","OWNERSHIP_IN"));
                throw new AssertionError("Unexpected query: "+q);
            });
            when(jdbc.update(anyString(),any(Object[].class))).thenAnswer(call->{String q=call.getArgument(0);
                if(List.of("UPDATE metal_warehouse_order SET status=", "UPDATE metal_inventory SET origin_recovery_line_id=", "UPDATE metal_warehouse_order_line SET powder_origin=", "UPDATE metal_warehouse_flow_line l JOIN", "UPDATE metal_inventory SET net_weight=", "UPDATE metal_inventory SET reference_quantity=", "UPDATE metal_stock_ledger SET reference_before=", "UPDATE metal_inventory SET measurement_mode=").stream().noneMatch(q::startsWith))throw new AssertionError("Unexpected update: "+q);
                return 1;
            });
            var connection=mock(Connection.class);var prepared=new IdentityHashMap<PreparedStatement,Insert>();
            when(connection.prepareStatement(anyString(),eq(Statement.RETURN_GENERATED_KEYS))).thenAnswer(call->{String q=call.getArgument(0);
                if(!q.startsWith("INSERT INTO metal_warehouse_order_line(")&&!q.startsWith("INSERT INTO metal_stock_ledger(")&&!q.startsWith("INSERT INTO metal_inventory("))throw new AssertionError("Unexpected insert: "+q);
                var statement=mock(PreparedStatement.class);var insert=new Insert(q,new HashMap<>());prepared.put(statement,insert);
                doAnswer(binding->{insert.bindings.put(binding.getArgument(0),binding.getArgument(1));return null;}).when(statement).setObject(anyInt(),any());
                return statement;
            });
            when(jdbc.update(any(PreparedStatementCreator.class),any(KeyHolder.class))).thenAnswer(call->{
                var statement=call.<PreparedStatementCreator>getArgument(0).createPreparedStatement(connection);var insert=prepared.get(statement);inserts.add(insert);
                long key=insert.sql.startsWith("INSERT INTO metal_inventory(")?2L:insert.sql.startsWith("INSERT INTO metal_stock_ledger(")?2000L:1000L;
                call.<KeyHolder>getArgument(1).getKeyList().add(new HashMap<>(Map.of("GENERATED_KEY",key)));return 1;
            });
        }
        void post(long pieces){
            var line=new Line(1L,null,"RAW",8L,null,new BigDecimal("100"),pieces,null,null,null,null,null,null);
            var order=new Order(UUID.randomUUID().toString(),43L,7L,storage?"RESIDUAL_STORAGE":"OWNERSHIP_IN",LocalDate.of(2026,11,4),null,List.of(11L),12L,null,null,null,10L,null,null,false,null,null,List.of(line),null,null);
            new WarehouseService(jdbc,mock(FactoryScope.class)).postResidualTransfer(order,99L);
        }
        Insert line(){return inserts.stream().filter(i->i.sql.startsWith("INSERT INTO metal_warehouse_order_line(")).findFirst().orElseThrow();}
        List<Insert> ledgers(){return inserts.stream().filter(i->i.sql.startsWith("INSERT INTO metal_stock_ledger(")).toList();}
    }
    @Test void ownershipTransferSavesPiecesAndCreditsTheFactoryInventoryDespiteTheSourceAlreadyBeingEmpty()throws Exception {
        var f=new Fixture(false,true,3);
        assertDoesNotThrow(()->f.post(5L));
        assertEquals(5L,((Number)f.line().values().get("quantity")).longValue());
        assertEquals(1,f.ledgers().size());var ledger=f.ledgers().get(0).values();
        assertEquals(2L,((Number)ledger.get("inventory_id")).longValue());assertEquals(5L,((Number)ledger.get("delta_quantity")).longValue());assertEquals(5L,((Number)ledger.get("balance_quantity")).longValue());
        assertEquals(0L,f.source.get("quantity"));
    }
    @Test void storageSavesPiecesWithoutCreatingAnInventoryCredit()throws Exception {
        var f=new Fixture(true,true,3);f.post(5L);
        assertEquals(5L,((Number)f.line().values().get("quantity")).longValue());assertTrue(f.ledgers().isEmpty());
        verify(f.jdbc,never()).update(startsWith("UPDATE metal_inventory SET net_weight="),any(Object[].class));
    }
    @Test void aNewFactoryTargetReceivesTheSamePieceQuantity()throws Exception {
        var f=new Fixture(false,false,3);assertDoesNotThrow(()->f.post(5L));
        assertEquals(5L,((Number)f.line().values().get("quantity")).longValue());assertEquals(5L,((Number)f.ledgers().get(0).values().get("delta_quantity")).longValue());
    }
    @Test void legacyRawTransferKeepsZeroQuantity()throws Exception {
        var f=new Fixture(false,true,2);f.post(0L);
        assertEquals(0L,((Number)f.line().values().get("quantity")).longValue());assertEquals(0L,((Number)f.ledgers().get(0).values().get("delta_quantity")).longValue());
    }
}
