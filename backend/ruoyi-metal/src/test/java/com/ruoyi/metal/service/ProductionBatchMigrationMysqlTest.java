package com.ruoyi.metal.service;

import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.metal.security.FactoryScope;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Requires a newly created disposable schema, never an existing application database. */
@EnabledIfEnvironmentVariable(named="PRODUCTION_BATCH_TEST_JDBC_URL",matches="jdbc:mysql://127\\.0\\.0\\.1:3323/production_batch_p1_20261112_[a-f0-9]{10}(?:\\?.*)?")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProductionBatchMigrationMysqlTest {
    static final Path MIGRATIONS=Path.of("../ruoyi-admin/src/main/resources/db/migration");
    static DriverManagerDataSource ds;
    static JdbcTemplate jdbc;
    static TransactionTemplate tx;
    static final Map<String,List<Map<String,Object>>> before=new LinkedHashMap<>();
    static final Map<String,String> oldColumns=new LinkedHashMap<>();

    @BeforeAll static void migrateOnlyNewDisposableSchema()throws Exception{
        String url=System.getenv("PRODUCTION_BATCH_TEST_JDBC_URL");
        assertTrue(url.matches("jdbc:mysql://127\\.0\\.0\\.1:3323/production_batch_p1_20261112_[a-f0-9]{10}(?:\\?.*)?"));
        ds=new DriverManagerDataSource(url,System.getenv("PRODUCTION_BATCH_TEST_USER"),System.getenv("PRODUCTION_BATCH_TEST_PASSWORD"));
        jdbc=new JdbcTemplate(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        assertEquals(3323,jdbc.queryForObject("SELECT @@port",Integer.class));
        assertTrue(jdbc.queryForObject("SELECT DATABASE()",String.class).matches("production_batch_p1_20261112_[a-f0-9]{10}"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()",Integer.class),"Refuse to touch a previously used schema");
        try(Connection connection=ds.getConnection()){
            var one=new JdbcTemplate(new SingleConnectionDataSource(connection,true));
            List<Path> sql;
            try(var paths=Files.list(MIGRATIONS)){sql=paths.filter(p->p.getFileName().toString().matches("V[0-9]+__.*\\.sql")).filter(p->version(p)<81).sorted(Comparator.comparingInt(ProductionBatchMigrationMysqlTest::version)).toList();}
            boolean java73=false;
            for(Path path:sql){
                if(version(path)>73&&!java73){ProcessTypeMigration.migrate(one);java73=true;}
                executeMigration(connection,path);
            }
            // These are the same business migration functions called by Java migrations V73/V79.
            if(!java73)ProcessTypeMigration.migrate(one);
            ProcessRuleMigration.migrate(one);
            assertEquals(0,one.queryForObject("SELECT COUNT(*) FROM metal_material WHERE NULLIF(TRIM(material_name),'') IS NULL AND NULLIF(TRIM(material_type),'') IS NULL",Integer.class));
            one.update("UPDATE metal_material SET material_name=TRIM(material_type) WHERE NULLIF(TRIM(material_name),'') IS NULL");
        }
        seedSyntheticHistory();
        for(String table:List.of("metal_production_order","metal_production_order_item","metal_production_order_step","metal_production_execution","metal_production_execution_event","metal_production_execution_operation","metal_storage_document","metal_storage_document_line","metal_storage_stock","metal_storage_ownership","metal_storage_debt","metal_storage_entry")){
            String columns=String.join(",",jdbc.queryForList("SELECT COLUMN_NAME FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=? ORDER BY ORDINAL_POSITION",String.class,table).stream().map(c->"`"+c+"`").toList());
            oldColumns.put(table,columns);before.put(table,jdbc.queryForList("SELECT "+columns+" FROM "+table+" ORDER BY 1"));
        }
        try(Connection connection=ds.getConnection()){executeMigration(connection,MIGRATIONS.resolve("V81__production_batch_identity.sql"));}
        assertOriginalColumnsPreserved();
        // Keep the V81 preservation assertion, then install the schema required by today's service code.
        try(Connection connection=ds.getConnection()){executeMigration(connection,MIGRATIONS.resolve("V82__production_batch_storage_slices.sql"));}
    }

    static int version(Path path){return Integer.parseInt(path.getFileName().toString().split("__")[0].substring(1));}
    static void executeMigration(Connection connection,Path path)throws Exception{
        String sql=Files.readString(path,StandardCharsets.UTF_8);
        if(sql.startsWith("\uFEFF"))sql=sql.substring(1);
        ScriptUtils.executeSqlScript(connection,new EncodedResource(new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8),path.getFileName().toString()),StandardCharsets.UTF_8));
    }
    static void login(){var user=new SysUser();user.setUserId(1L);user.setUserName("p1-test");user.setFactoryId(7001L);var login=new LoginUser(1L,1L,user,Set.of("*:*:*"));SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(login,null,login.getAuthorities()));}
    @AfterEach void logout(){SecurityContextHolder.clearContext();}

    static void seedSyntheticHistory(){
        jdbc.update("INSERT INTO metal_factory(factory_id,factory_name,area,address) VALUES(7001,'P1 synthetic A',1,'isolated'),(7002,'P1 synthetic B',1,'isolated')");
        jdbc.update("UPDATE sys_user SET factory_id=7001 WHERE user_id=1");
        jdbc.update("INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT 1,menu_id FROM sys_menu WHERE perms='metal:warehouse-order:receive' AND status='0'");
        assertTrue(StorageSupport.personCan(jdbc,1,7001,"metal:warehouse-order:receive"),"Synthetic processor must have real database receive permission");
        jdbc.update("INSERT INTO metal_production_line(production_line_id,production_line_code,production_line_name,factory_id) VALUES(7101,'P1-L1','P1 line',7001),(7102,'P1-L2','P1 line B',7002)");
        jdbc.update("INSERT INTO metal_product(product_id,factory_id,product_code,product_name,product_category,product_type,metal_type_id,dimensions,unit) VALUES(7201,7001,'P1-P','P1 product','铱杆实心','杆实心',1,'{}','件')");
        jdbc.update("INSERT INTO metal_process_type(type_id,factory_id,type_name) VALUES(7301,7001,'P1 ordinary')");
        jdbc.update("INSERT INTO metal_process(process_id,factory_id,process_name,process_type,semantic_code) VALUES(7401,7001,'P1 first',7301,'NORMAL'),(7402,7001,'P1 second',7301,'NORMAL')");
        for(long order:List.of(7501L,7502L,7503L,7504L,7505L)){
            long factory=order==7504?7002:7001;long line=order==7504?7102:7101;
            jdbc.update("INSERT INTO metal_production_order(id,order_no,factory_id,production_line_id,owner_id,processing_type,order_date,factory_name,line_name,owner_name,request_id,request_actor,request_hash,create_by,creator_name,flow_version) VALUES(?,?,?,?,1,'DIRECT_CUSTOM',CURRENT_DATE,'P1 factory','P1 line','test',?,1,REPEAT('a',64),'test','test',1)",order,"P1-ORDER-"+order,factory,line,UUID.randomUUID().toString());
            long item=order+100;
            jdbc.update("INSERT INTO metal_production_order_item(id,order_id,sequence_no,product_id,quantity,product_snapshot) VALUES(?,?,1,7201,100,JSON_OBJECT('name','frozen P1','metalTypeId','1','specification','P1','purity','99'))",item,order);
            for(int seq=1;seq<=2;seq++)jdbc.update("INSERT INTO metal_production_order_step(id,item_id,sequence_no,process_id,process_name,process_snapshot,semantic_code,output_category,output_form,measurement_mode) VALUES(?,?,?,?,?,JSON_OBJECT('frozen',?), 'NORMAL',?,'SEMI','PIECE')",item*10+seq,item,seq,7400+seq,"P1 step "+seq,seq,seq==1?"SEMI":"PRODUCT");
        }
        jdbc.update("INSERT INTO metal_production_execution(id,factory_id,order_id,order_item_id,order_step_id,processor_id,planned_quantity,inherited_snapshot,create_by,input_weight,output_weight,input_quantity,output_quantity) VALUES(7701,7001,7501,7601,76011,1,100,'[]',1,1000,300,100,30)");
        jdbc.update("INSERT INTO metal_storage_document(id,factory_id,number,kind,type,status,order_id,execution_id,creator_id,reviewer_id,business_date,data) VALUES(7801,7001,'P1-HISTORY','IN','SEMI_IN','POSTED',7501,7701,1,1,CURRENT_DATE,JSON_OBJECT('frozen','history'))");
        jdbc.update("INSERT INTO metal_storage_document_line(id,factory_id,document_id,sequence_no,data) VALUES(7802,7001,7801,1,JSON_OBJECT('netWeight','300.00','quantity',30,'remark','preserve'))");
        jdbc.update("INSERT INTO metal_production_execution_event(id,factory_id,execution_id,document_id,line_id,event_type,weight,quantity,customer_weight,factory_weight) VALUES(7803,7001,7701,7801,7802,'OUTPUT',300,30,100,200)");
        jdbc.update("INSERT INTO metal_production_execution_operation(factory_id,actor_id,request_id,action,request_hash,result) VALUES(7001,1,?,'legacy-test',REPEAT('b',64),JSON_OBJECT('keep',true))",UUID.randomUUID().toString());
        jdbc.update("INSERT INTO metal_warehouse(warehouse_id,factory_id,name,create_by) VALUES(7901,7001,'P1 warehouse','test')");
        jdbc.update("INSERT INTO metal_storage_event(id,factory_id,document_id,line_id,action,request_hash,result) VALUES(7902,7001,7801,7802,'INBOUND',REPEAT('c',64),'{}')");
        jdbc.update("INSERT INTO metal_storage_stock(id,factory_id,warehouse_id,category,product_id,identity_key,identity_snapshot,measurement_mode,batch_no,origin_order_id,origin_step_id,origin_document_id,origin_line_id,net_weight,quantity) VALUES(7903,7001,7901,'SEMI',7201,REPEAT('d',64),JSON_OBJECT('frozen','stock','originCustomerWeight','100.00','originFactoryWeight','200.00'),'PIECE','ST-P1',7501,76011,7801,7802,300,30)");
        jdbc.update("INSERT INTO metal_storage_ownership VALUES(7001,7903,0,300,30)");
        jdbc.update("INSERT INTO metal_storage_entry(event_id,factory_id,stock_id,owner_id,ledger,weight_delta,quantity_delta,reason) VALUES(7902,7001,7903,0,'PHYSICAL',300,30,'RECEIPT')");
        jdbc.update("INSERT INTO metal_storage_debt(factory_id,event_id,stock_id,work_order_id,creditor_id,debtor_id,debt_kind,identity_key,identity_snapshot,category,measurement_mode,original_weight,original_quantity,remaining_weight,remaining_quantity) VALUES(7001,7902,7903,7501,0,88,'ADVANCE',REPEAT('d',64),'{}','SEMI','PIECE',20,2,20,2)");
    }

    @Test @Order(1) void migrationPreservesEveryOriginalColumnAndDoesNotGuessHistoricalIdentity(){
        assertOriginalColumnsPreserved();
        assertNull(jdbc.queryForObject("SELECT batch_id FROM metal_production_execution WHERE id=7701",Long.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM metal_production_batch",Integer.class));
    }
    static void assertOriginalColumnsPreserved(){
        before.forEach((table,rows)->assertEquals(rows,jdbc.queryForList("SELECT "+oldColumns.get(table)+" FROM "+table+" ORDER BY 1"),table));
    }

    @Test @Order(2) void compositeForeignKeysRejectForeignFactoryOrderAndProductGroup(){
        tx.executeWithoutResult(status->{
            jdbc.update("INSERT INTO metal_production_batch(id,factory_id,number,order_id,order_item_id,route_snapshot,planned_quantity,create_by) VALUES(8001,7001,'PB-CONSTRAINT',7502,7602,'{}',100,1)");
            assertThrows(DataAccessException.class,()->jdbc.update("UPDATE metal_production_execution SET batch_id=8001 WHERE id=7701"));
            assertThrows(DataAccessException.class,()->jdbc.update("INSERT INTO metal_production_batch(factory_id,number,order_id,order_item_id,route_snapshot,create_by) VALUES(7002,'PB-WRONG-FACTORY',7502,7602,'{}',1)"));
            assertThrows(DataAccessException.class,()->jdbc.update("INSERT INTO metal_production_batch(factory_id,number,order_id,order_item_id,route_snapshot,create_by) VALUES(7001,'PB-WRONG-ITEM',7502,7603,'{}',1)"));
            assertThrows(DataAccessException.class,()->jdbc.update("INSERT INTO metal_production_batch(factory_id,number,order_id,order_item_id,route_snapshot,create_by) VALUES(7001,'PB-CONSTRAINT',7502,7602,'{}',1)"));
            assertThrows(DataAccessException.class,()->jdbc.update("UPDATE metal_production_batch SET planned_quantity=0 WHERE id=8001"));
            assertThrows(DataAccessException.class,()->jdbc.update("UPDATE metal_production_batch SET lifecycle='OPEN' WHERE id=8001"));
            status.setRollbackOnly();
        });
    }

    FactoryScope scope(){
        var scope=mock(FactoryScope.class);when(scope.isAdmin()).thenReturn(true);
        when(scope.resolveFactory(any())).thenAnswer(invocation->{Long f=invocation.getArgument(0);return f==null?7001L:f;});
        doAnswer(invocation->{Collection<Long> factories=invocation.getArgument(0);for(long f:factories)jdbc.queryForList("SELECT factory_id FROM metal_factory WHERE factory_id=? FOR UPDATE",f);return null;}).when(scope).lockFactories(any());
        return scope;
    }
    ProductionBatchService service(){var scope=scope();return new ProductionBatchService(jdbc,scope,new ProductionExecutionService(jdbc,scope));}
    Map<String,Object> createBody(long order,int planned,String request){return Map.of("factoryId","7001","requestId",request,"orderId",String.valueOf(order),"orderItemId",String.valueOf(order+100),"orderVersion",0,"plannedQuantity",planned);}

    @Test @Order(3) void realTransactionsCreateFreezeReplayAndRollbackWithoutTouchingWarehouse(){
        login();var service=service();var warehouse=jdbc.queryForList("SELECT * FROM metal_storage_stock ORDER BY id");
        tx.executeWithoutResult(status->{
            var body=createBody(7502,100,UUID.randomUUID().toString());var created=service.create(body);long batch=Long.parseLong(created.get("batchId").toString());
            assertEquals(created,service.create(body));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM metal_production_batch WHERE id=?",Integer.class,batch));
            assertEquals(true,service.operation(body.get("requestId").toString(),7001L).get("found"));
            var changed=new HashMap<>(body);changed.put("plannedQuantity",99);assertThrows(ServiceException.class,()->service.create(changed));
            var snapshot=jdbc.queryForObject("SELECT CAST(route_snapshot AS CHAR) FROM metal_production_batch WHERE id=?",String.class,batch);
            jdbc.update("UPDATE metal_production_order_step SET process_name='changed after creation' WHERE id=76021");
            assertEquals(snapshot,jdbc.queryForObject("SELECT CAST(route_snapshot AS CHAR) FROM metal_production_batch WHERE id=?",String.class,batch));
            assertTrue(snapshot.contains("P1 step 1"));
            assertThrows(ServiceException.class,()->service.start(batch,Map.of("factoryId","7001","requestId",UUID.randomUUID().toString(),"batchVersion",0,"intent","START_STAGE","orderStepId","76021","processorId","1")));
            jdbc.update("UPDATE metal_production_order_step SET process_name='P1 step 1' WHERE id=76021");
            assertThrows(ServiceException.class,()->service.create(createBody(7502,1,UUID.randomUUID().toString())));
            assertThrows(ServiceException.class,()->service.create(createBody(7501,100,UUID.randomUUID().toString())));
            assertThrows(ServiceException.class,()->service.create(Map.of("factoryId","7002","requestId",UUID.randomUUID().toString(),"orderId","7502","orderItemId","7602","orderVersion",0,"plannedQuantity",1)));
            assertThrows(ServiceException.class,()->service.start(batch,Map.of("factoryId","7001","requestId",UUID.randomUUID().toString(),"batchVersion",1,"intent","START_STAGE","orderStepId","76021","processorId","1")));
            assertThrows(ServiceException.class,()->service.start(batch,Map.of("factoryId","7001","requestId",UUID.randomUUID().toString(),"batchVersion",0,"intent","START_STAGE","orderStepId","76022","processorId","1")));
            assertThrows(ServiceException.class,()->service.start(batch,Map.of("factoryId","7001","requestId",UUID.randomUUID().toString(),"batchVersion",0,"intent","START_STAGE","orderStepId","76021","processorId","9999999")));
            var startBody=Map.<String,Object>of("factoryId","7001","requestId",UUID.randomUUID().toString(),"batchVersion",0,"intent","START_STAGE","orderStepId","76021","processorId","1");
            var started=service.start(batch,startBody);assertEquals(started,service.start(batch,startBody));
            assertEquals(batch,jdbc.queryForObject("SELECT batch_id FROM metal_production_execution WHERE id=?",Long.class,Long.parseLong(started.get("executionId").toString())));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM metal_production_execution_event WHERE execution_id=?",Integer.class,Long.parseLong(started.get("executionId").toString())));
            assertThrows(ServiceException.class,()->service.start(batch,Map.of("factoryId","7001","requestId",UUID.randomUUID().toString(),"batchVersion",1,"intent","START_STAGE","orderStepId","76022","processorId","1")));
            var executionService=new ProductionExecutionService(jdbc,scope());
            assertThrows(ServiceException.class,()->executionService.create(7502,Map.of("factoryId","7001","requestId",UUID.randomUUID().toString(),"orderItemId","7602","orderStepId","76021","processorId","1","plannedQuantity",1)));
            var productionDocument=new LinkedHashMap<String,Object>(Map.of("type","PRODUCTION_OUT","orderId","7502","executionId",started.get("executionId")));
            assertDoesNotThrow(()->executionService.prepareDocument(7001,productionDocument));
            assertEquals(String.valueOf(batch),productionDocument.get("batchId"));
            assertEquals("7602",productionDocument.get("orderItemId"));
            assertEquals("76021",productionDocument.get("orderStepId"));
            assertEquals(warehouse,jdbc.queryForList("SELECT * FROM metal_storage_stock ORDER BY id"));
            status.setRollbackOnly();
        });
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM metal_production_batch WHERE order_id=7502",Integer.class));
    }

    @Test @Order(4) void factoryLockMakesConcurrentTargetAllocationAndRequestReplayAtomic()throws Exception{
        var executor=Executors.newFixedThreadPool(2);
        try{
            String request=UUID.randomUUID().toString();var barrier=new CyclicBarrier(2);
            Callable<Map<String,Object>> replay=()->{login();try{barrier.await(10,TimeUnit.SECONDS);return tx.execute(status->service().create(createBody(7503,100,request)));}finally{SecurityContextHolder.clearContext();}};
            var first=executor.submit(replay);var second=executor.submit(replay);assertEquals(first.get(30,TimeUnit.SECONDS),second.get(30,TimeUnit.SECONDS));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM metal_production_batch WHERE order_id=7503",Integer.class));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM metal_production_execution_operation WHERE request_id=?",Integer.class,request));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM metal_production_execution WHERE order_id=7503",Integer.class));
        }finally{executor.shutdownNow();}
    }

    @Test @Order(5) void staleRepeatableReadSnapshotCannotAllocateTwoSixtiesAgainstOneHundred()throws Exception{
        assertEquals("REPEATABLE-READ",jdbc.queryForObject("SELECT @@transaction_isolation",String.class));
        var executor=Executors.newFixedThreadPool(2);var barrier=new CyclicBarrier(2);
        try{
            Callable<Boolean> allocate=()->{
                login();
                try{
                    return tx.execute(status->{
                        jdbc.queryForObject("SELECT COUNT(*) FROM metal_production_batch WHERE order_item_id=7602",Long.class);
                        try{barrier.await(10,TimeUnit.SECONDS);}catch(Exception e){throw new IllegalStateException(e);}
                        service().create(createBody(7502,60,UUID.randomUUID().toString()));return true;
                    });
                }catch(ServiceException expected){return false;}
                finally{SecurityContextHolder.clearContext();}
            };
            var first=executor.submit(allocate);var second=executor.submit(allocate);
            assertNotEquals(first.get(30,TimeUnit.SECONDS),second.get(30,TimeUnit.SECONDS),"Exactly one allocation must commit");
            assertEquals(60,jdbc.queryForObject("SELECT SUM(planned_quantity) FROM metal_production_batch WHERE order_id=7502",Integer.class));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM metal_production_batch WHERE order_id=7502",Integer.class));
        }finally{executor.shutdownNow();}
    }

    @Test @Order(6) void exceptionAfterIdentityAndExecutionInsertsRollsBackBusinessAndIdempotencyRows(){
        login();var failingJdbc=spy(jdbc);
        doThrow(new IllegalStateException("injected failure after operation INSERT")).when(failingJdbc).update(eq("UPDATE metal_production_execution_operation SET result=? WHERE factory_id=? AND id=?"),any(),any(),any());
        var scope=scope();var faulty=new ProductionBatchService(failingJdbc,scope,new ProductionExecutionService(failingJdbc,scope));
        String createRequest=UUID.randomUUID().toString();
        assertThrows(IllegalStateException.class,()->tx.execute(status->faulty.create(createBody(7505,100,createRequest))));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM metal_production_batch WHERE order_id=7505",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM metal_production_execution_operation WHERE request_id=?",Integer.class,createRequest));
        var created=tx.execute(status->service().create(createBody(7505,100,UUID.randomUUID().toString())));
        long batch=Long.parseLong(created.get("batchId").toString());String startRequest=UUID.randomUUID().toString();
        assertThrows(IllegalStateException.class,()->tx.execute(status->faulty.start(batch,Map.of("factoryId","7001","requestId",startRequest,"batchVersion",0,"intent","START_STAGE","orderStepId","76051","processorId","1"))));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM metal_production_execution WHERE batch_id=?",Integer.class,batch));
        assertEquals(0,jdbc.queryForObject("SELECT version FROM metal_production_batch WHERE id=?",Integer.class,batch));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM metal_production_execution_operation WHERE request_id=?",Integer.class,startRequest));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM metal_production_execution_event WHERE execution_id IN (SELECT id FROM metal_production_execution WHERE batch_id=?)",Integer.class,batch));
    }
}
