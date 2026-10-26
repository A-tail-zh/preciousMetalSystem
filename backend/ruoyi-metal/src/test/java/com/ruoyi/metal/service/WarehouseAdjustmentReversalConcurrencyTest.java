package com.ruoyi.metal.service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import com.ruoyi.metal.security.FactoryScope;
import com.ruoyi.metal.catalog.WarehouseRequests.Action;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Service/transaction fixtures in the disposable MySQL copy, never the business database. */
@EnabledIfEnvironmentVariable(named="METAL_FLOW_ISOLATED_JDBC_URL",matches=".*3310/heavy_metal_plan_upgrade_20261022.*")
class WarehouseAdjustmentReversalConcurrencyTest {
    static class Fixture implements AutoCloseable {
        final DriverManagerDataSource ds = new DriverManagerDataSource(System.getenv("METAL_FLOW_ISOLATED_JDBC_URL"), "root", "");
        final JdbcTemplate jdbc = new JdbcTemplate(ds);
        final TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        final FactoryScope scope = mock(FactoryScope.class);
        final WarehouseService service = new WarehouseService(jdbc, scope);
        final CountDownLatch waiting = new CountDownLatch(1);
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        final long factory, stock, order, work;
        final Object oldWorkStatus;
        final String request = UUID.randomUUID().toString();
        Fixture() {
            tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
            tx.setTimeout(15);
            var template = jdbc.queryForMap("SELECT i.*,b.work_order_id,w.status work_status FROM metal_inventory i JOIN metal_process_batch b ON b.batch_id=i.origin_batch_id JOIN metal_work_order w ON w.work_order_id=b.work_order_id WHERE i.del_flag='0' AND b.status='SETTLED' LIMIT 1");
            factory = SemiFlowService.num(template.get("factory_id"));
            work=SemiFlowService.num(template.get("work_order_id"));oldWorkStatus=template.get("work_status");
            // Explicit service fixture in the disposable copy, not an adjustment-free business scenario.
            jdbc.update("UPDATE metal_work_order SET status='COMPLETED' WHERE work_order_id=?",work);
            jdbc.update("INSERT INTO metal_inventory(factory_id,warehouse_id,category,reference_id,batch_no,code,name,specification,organization,origin_batch_id,quantity,net_weight) SELECT i.factory_id,i.warehouse_id,'SEMI',p.product_id,?,i.code,i.name,JSON_UNQUOTE(JSON_EXTRACT(p.product_snapshot,'$.specification')),w.customer,i.origin_batch_id,4,40 FROM metal_inventory i JOIN metal_process_batch b ON b.batch_id=i.origin_batch_id JOIN metal_work_order_item p ON p.item_id=b.item_id JOIN metal_work_order w ON w.work_order_id=b.work_order_id WHERE i.inventory_id=?", request, template.get("inventory_id"));
            stock=jdbc.queryForObject("SELECT inventory_id FROM metal_inventory WHERE batch_no=?",Long.class,request);
            jdbc.update("INSERT INTO metal_warehouse_order(order_no,request_id,request_hash,request_actor,factory_id,warehouse_id,kind,type,order_date,keeper_id,operator_name,keeper_name,warehouse_name) SELECT ?,?,REPEAT('a',64),1,factory_id,warehouse_id,'ADJ','ADJUST',CURRENT_DATE,keeper_id,operator_name,keeper_name,warehouse_name FROM metal_warehouse_order WHERE warehouse_id=? LIMIT 1", "ADJ-"+request.substring(0,20),request,template.get("warehouse_id"));
            order=jdbc.queryForObject("SELECT order_id FROM metal_warehouse_order WHERE request_id=?",Long.class,request);
            jdbc.update("INSERT INTO metal_warehouse_order_line(order_id,inventory_id,sequence_no,category,reference_id,code,name,batch_no,quantity,net_weight) SELECT ?,inventory_id,1,category,reference_id,code,name,batch_no,4,40 FROM metal_inventory WHERE inventory_id=?",order,stock);
            jdbc.update("INSERT INTO metal_stock_ledger(inventory_id,order_id,line_id,delta_quantity,delta_weight,balance_quantity,balance_weight) SELECT ?,?,line_id,4,40,4,40 FROM metal_warehouse_order_line WHERE order_id=?",stock,order,order);
            doAnswer(a->{waiting.countDown();jdbc.queryForMap("SELECT factory_id FROM metal_factory WHERE factory_id=? FOR UPDATE",factory);return null;}).when(scope).lockFactories(anyList());
            when(scope.isAdmin()).thenReturn(true);
        }
        void lockFactory(){jdbc.queryForMap("SELECT factory_id FROM metal_factory WHERE factory_id=? FOR UPDATE",factory);}
        long release(String mode) {
            if("MANUAL".equals(mode))return asAdmin(()->new SemiFlowService(jdbc,scope).release(stock,UUID.randomUUID().toString(),jdbc.queryForObject("SELECT version FROM metal_inventory WHERE inventory_id=?",Integer.class,stock),4,new BigDecimal("40"),"isolated actual manual release"));
            jdbc.update("INSERT INTO metal_semi_release(inventory_id,factory_id,work_order_id,request_id,request_hash,actor_id,quantity,net_weight,reason,release_mode) SELECT i.inventory_id,i.factory_id,b.work_order_id,?,REPEAT('b',64),1,4,40,'isolated concurrency fixture',? FROM metal_inventory i JOIN metal_process_batch b ON b.batch_id=i.origin_batch_id WHERE i.inventory_id=?",UUID.randomUUID().toString(),mode,stock);
            return jdbc.queryForObject("SELECT release_id FROM metal_semi_release WHERE inventory_id=?",Long.class,stock);
        }
        <T>T asAdmin(Supplier<T> action){try(var security=mockStatic(SecurityUtils.class)){security.when(SecurityUtils::getUserId).thenReturn(1L);return action.get();}}
        void cancel(long release){asAdmin(()->{new SemiFlowService(jdbc,scope).cancel(release,UUID.randomUUID().toString(),0,"isolated actual cancellation");return null;});}
        void reverse(){tx.executeWithoutResult(s->service.reverseOrder(order,new Action(0,"isolated reversal",null)));}
        void assertUnchanged(int expectedVersion){assertEquals(4L,jdbc.queryForObject("SELECT quantity FROM metal_inventory WHERE inventory_id=?",Long.class,stock));assertEquals("POSTED",jdbc.queryForObject("SELECT status FROM metal_warehouse_order WHERE order_id=?",String.class,order));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM metal_stock_ledger WHERE order_id=?",Integer.class,order));assertEquals(expectedVersion,jdbc.queryForObject("SELECT version FROM metal_inventory WHERE inventory_id=?",Integer.class,stock));}
        public void close(){executor.shutdownNow();jdbc.update("DELETE FROM metal_semi_flow_event WHERE release_id IN (SELECT release_id FROM metal_semi_release WHERE inventory_id=?)",stock);jdbc.update("DELETE FROM metal_semi_release WHERE inventory_id=?",stock);jdbc.update("DELETE FROM metal_stock_ledger WHERE order_id=? AND reversal_of IS NOT NULL",order);jdbc.update("DELETE FROM metal_stock_ledger WHERE order_id=?",order);jdbc.update("DELETE FROM metal_warehouse_order_line WHERE order_id=?",order);jdbc.update("DELETE FROM metal_warehouse_order WHERE order_id=?",order);jdbc.update("DELETE FROM metal_inventory WHERE inventory_id=?",stock);jdbc.update("UPDATE metal_work_order SET status=? WHERE work_order_id=?",oldWorkStatus,work);}
    }

    @Test void releaseCommittedWhileReversalWaitsIsSeenForAutoAndManual() throws Exception {
        for(String mode:List.of("AUTO","MANUAL"))try(var f=new Fixture()) {
            Future<?>[] future=new Future<?>[1];
            f.tx.executeWithoutResult(s->{f.lockFactory();future[0]=f.executor.submit(f::reverse);try{assertTrue(f.waiting.await(5,TimeUnit.SECONDS));}catch(InterruptedException e){throw new RuntimeException(e);}f.release(mode);});
            var error=assertThrows(ExecutionException.class,()->future[0].get(10,TimeUnit.SECONDS));
            assertInstanceOf(ServiceException.class,error.getCause());f.assertUnchanged("MANUAL".equals(mode)?1:0);
        }
    }
    @Test void cancellationCommittedWhileReversalWaitsAllowsReversal() throws Exception {
        try(var f=new Fixture()) {
            long release=f.release("AUTO");Future<?>[] future=new Future<?>[1];
            f.tx.executeWithoutResult(s->{f.lockFactory();future[0]=f.executor.submit(f::reverse);try{assertTrue(f.waiting.await(5,TimeUnit.SECONDS));}catch(InterruptedException e){throw new RuntimeException(e);}f.cancel(release);});
            future[0].get(10,TimeUnit.SECONDS);
            assertEquals(0L,f.jdbc.queryForObject("SELECT quantity FROM metal_inventory WHERE inventory_id=?",Long.class,f.stock));
            assertEquals("VOID",f.jdbc.queryForObject("SELECT status FROM metal_warehouse_order WHERE order_id=?",String.class,f.order));
        }
    }
    @Test void reversalCommittedBeforeReleaseValidationLeavesNoReleasableStock() throws Exception {
        try(var f=new Fixture()) {
            var started=new CountDownLatch(1);Future<?>[] future=new Future<?>[1];
            f.tx.executeWithoutResult(s->{f.lockFactory();f.service.reverseOrder(f.order,new Action(0,"isolated reversal",null));future[0]=f.executor.submit(()->f.tx.executeWithoutResult(t->{started.countDown();f.release("MANUAL");}));try{assertTrue(started.await(5,TimeUnit.SECONDS));}catch(InterruptedException e){throw new RuntimeException(e);}});
            assertThrows(ExecutionException.class,()->future[0].get(10,TimeUnit.SECONDS));
            assertEquals(0,f.jdbc.queryForObject("SELECT COUNT(*) FROM metal_semi_release WHERE inventory_id=?",Integer.class,f.stock));
        }
    }
    @Test void realAutomaticWorkerAndReversalSerializeInBothOrders() throws Exception {
        for(boolean automaticFirst:List.of(true,false))try(var f=new Fixture()) {
            int oldGeneration=f.jdbc.queryForObject("SELECT completion_generation FROM metal_work_order WHERE work_order_id=?",Integer.class,f.work);
            int generation=1000000+new Random().nextInt(1000000);
            f.jdbc.update("UPDATE metal_work_order SET completion_generation=? WHERE work_order_id=?",generation,f.work);
            f.jdbc.update("INSERT INTO metal_semi_auto_release_event(work_order_id,factory_id,generation,actor_id) VALUES(?,?,?,1)",f.work,f.factory,generation);
            long event=f.jdbc.queryForObject("SELECT event_id FROM metal_semi_auto_release_event WHERE work_order_id=? AND generation=?",Long.class,f.work,generation);
            var started=new CountDownLatch(1);Future<?>[] future=new Future<?>[1];
            try {
                var workerJdbc=new JdbcTemplate(f.ds){@Override public Map<String,Object> queryForMap(String sql,Object...args){var r=super.queryForMap(sql,args);if(sql.startsWith("SELECT factory_id,work_order_id FROM metal_semi_auto_release_event"))started.countDown();return r;}};
                var worker=new SemiAutoReleaseService(workerJdbc,new DataSourceTransactionManager(f.ds));
                f.tx.executeWithoutResult(s->{
                    f.lockFactory();
                    if(automaticFirst){worker.process(event);future[0]=f.executor.submit(f::reverse);try{assertTrue(f.waiting.await(5,TimeUnit.SECONDS));}catch(InterruptedException e){throw new RuntimeException(e);}}
                    else{f.service.reverseOrder(f.order,new Action(0,"isolated reversal",null));future[0]=f.executor.submit(()->f.tx.executeWithoutResult(t->worker.process(event)));try{assertTrue(started.await(5,TimeUnit.SECONDS));}catch(InterruptedException e){throw new RuntimeException(e);}}
                });
                if(automaticFirst){var failure=assertThrows(ExecutionException.class,()->future[0].get(10,TimeUnit.SECONDS));assertInstanceOf(ServiceException.class,failure.getCause());f.assertUnchanged(1);assertEquals("RELEASED",f.jdbc.queryForObject("SELECT status FROM metal_semi_auto_release_event WHERE event_id=?",String.class,event));}
                else{future[0].get(10,TimeUnit.SECONDS);assertEquals("NO_BALANCE",f.jdbc.queryForObject("SELECT status FROM metal_semi_auto_release_event WHERE event_id=?",String.class,event));assertEquals(0,f.jdbc.queryForObject("SELECT COUNT(*) FROM metal_semi_release WHERE auto_event_id=?",Integer.class,event));}
            } finally {
                f.jdbc.update("DELETE FROM metal_semi_flow_event WHERE release_id IN (SELECT release_id FROM metal_semi_release WHERE auto_event_id=?)",event);
                f.jdbc.update("DELETE FROM metal_semi_release WHERE auto_event_id=?",event);
                f.jdbc.update("DELETE FROM metal_semi_auto_release_result WHERE event_id=?",event);
                f.jdbc.update("DELETE FROM metal_semi_auto_release_event WHERE event_id=?",event);
                f.jdbc.update("UPDATE metal_work_order SET completion_generation=? WHERE work_order_id=?",oldGeneration,f.work);
            }
        }
    }
    @Test void rawAndFinishedAdjustmentReversalsRetainExistingBehavior() {
        for(String category:List.of("RAW","FINISHED"))try(var f=new Fixture()) {
            f.jdbc.update("UPDATE metal_inventory SET category=? WHERE inventory_id=?",category,f.stock);
            f.jdbc.update("UPDATE metal_warehouse_order_line SET category=? WHERE order_id=?",category,f.order);
            assertDoesNotThrow(f::reverse);
            assertEquals(0L,f.jdbc.queryForObject("SELECT quantity FROM metal_inventory WHERE inventory_id=?",Long.class,f.stock));
            assertEquals("VOID",f.jdbc.queryForObject("SELECT status FROM metal_warehouse_order WHERE order_id=?",String.class,f.order));
        }
    }
    @Test void negativeSemiAdjustmentStillRequiresReleaseRemoval() {
        for(boolean active:List.of(true,false))try(var f=new Fixture()) {
            f.jdbc.update("UPDATE metal_stock_ledger SET delta_quantity=-4,delta_weight=-40 WHERE order_id=?",f.order);
            f.jdbc.update("UPDATE metal_warehouse_order_line SET quantity=-4,net_weight=-40 WHERE order_id=?",f.order);
            if(active){f.release("AUTO");assertThrows(ServiceException.class,f::reverse);f.assertUnchanged(0);}
            else{assertDoesNotThrow(f::reverse);assertEquals(8L,f.jdbc.queryForObject("SELECT quantity FROM metal_inventory WHERE inventory_id=?",Long.class,f.stock));assertEquals("80.000000",f.jdbc.queryForObject("SELECT CAST(net_weight AS CHAR) FROM metal_inventory WHERE inventory_id=?",String.class,f.stock));}
        }
    }
    @Test void failedMultiStockAdjustmentLeavesAllRowsUntouched() {
        try(var f=new Fixture()) {
            f.release("AUTO");
            // An earlier line has no release. It too must remain untouched when another line blocks.
            f.jdbc.update("INSERT INTO metal_inventory(factory_id,warehouse_id,category,reference_id,batch_no,code,name,quantity,net_weight) SELECT factory_id,warehouse_id,'RAW',reference_id,?,code,name,4,40 FROM metal_inventory WHERE inventory_id=?",f.request+"-raw",f.stock);
            long raw=f.jdbc.queryForObject("SELECT inventory_id FROM metal_inventory WHERE batch_no=?",Long.class,f.request+"-raw");
            try {
                f.jdbc.update("INSERT INTO metal_warehouse_order_line(order_id,inventory_id,sequence_no,category,reference_id,code,name,batch_no,quantity,net_weight) SELECT ?,inventory_id,2,category,reference_id,code,name,batch_no,4,40 FROM metal_inventory WHERE inventory_id=?",f.order,raw);
                f.jdbc.update("INSERT INTO metal_stock_ledger(inventory_id,order_id,line_id,delta_quantity,delta_weight,balance_quantity,balance_weight) SELECT ?,?,line_id,4,40,4,40 FROM metal_warehouse_order_line WHERE order_id=? AND inventory_id=?",raw,f.order,f.order,raw);
                assertThrows(ServiceException.class,f::reverse);
                assertEquals(4L,f.jdbc.queryForObject("SELECT quantity FROM metal_inventory WHERE inventory_id=?",Long.class,raw));
                assertEquals(0,f.jdbc.queryForObject("SELECT version FROM metal_inventory WHERE inventory_id=?",Integer.class,raw));
                assertEquals(0,f.jdbc.queryForObject("SELECT COUNT(*) FROM metal_stock_ledger WHERE order_id=? AND reversal_of IS NOT NULL",Integer.class,f.order));
            } finally {f.jdbc.update("DELETE FROM metal_stock_ledger WHERE inventory_id=?",raw);f.jdbc.update("DELETE FROM metal_warehouse_order_line WHERE inventory_id=?",raw);f.jdbc.update("DELETE FROM metal_inventory WHERE inventory_id=?",raw);}
        }
    }
}
