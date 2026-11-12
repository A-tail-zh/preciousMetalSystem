package com.ruoyi.metal.service;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.metal.security.FactoryScope;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
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

/** New disposable schema only; no application database or existing audit schema is accepted. */
@EnabledIfEnvironmentVariable(named="PRODUCTION_BATCH_P2_TEST_JDBC_URL",matches="jdbc:mysql://127\\.0\\.0\\.1:3323/production_batch_p2_20261112_[a-f0-9]{10}(?:\\?.*)?")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProductionBatchP2MysqlTest {
    static final long F=9001, WAREHOUSE=9101, CUSTOMER=9201, MATERIAL=9401, PRODUCT=9501, ORDER=9701, ITEM=9801;
    static final Path MIGRATIONS=Path.of("../ruoyi-admin/src/main/resources/db/migration");
    static JdbcTemplate jdbc;
    static TransactionTemplate tx;
    static final Map<String,List<Map<String,Object>>> history=new LinkedHashMap<>();
    static final Map<String,String> historyColumns=new LinkedHashMap<>();
    static final List<String> HISTORY_TABLES=List.of("metal_production_order","metal_production_order_item","metal_production_order_step","metal_production_execution","metal_production_execution_event","metal_storage_document","metal_storage_document_line","metal_storage_event","metal_storage_stock","metal_storage_ownership","metal_storage_entry");

    @BeforeAll static void initializeOnlyEmptyIsolatedSchema()throws Exception {
        String url=System.getenv("PRODUCTION_BATCH_P2_TEST_JDBC_URL");
        assertTrue(url.matches("jdbc:mysql://127\\.0\\.0\\.1:3323/production_batch_p2_20261112_[a-f0-9]{10}(?:\\?.*)?"));
        var ds=new DriverManagerDataSource(url,System.getenv("PRODUCTION_BATCH_P2_TEST_USER"),System.getenv("PRODUCTION_BATCH_P2_TEST_PASSWORD"));
        jdbc=new JdbcTemplate(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        assertEquals(3323,jdbc.queryForObject("SELECT @@port",Integer.class));
        assertTrue(jdbc.queryForObject("SELECT DATABASE()",String.class).matches("production_batch_p2_20261112_[a-f0-9]{10}"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()",Integer.class),"Refuse to modify a used schema");
        try(Connection connection=ds.getConnection()) {
            var same=new JdbcTemplate(new SingleConnectionDataSource(connection,true));
            List<Path> scripts;
            try(var paths=Files.list(MIGRATIONS)){scripts=paths.filter(p->p.getFileName().toString().matches("V[0-9]+__.*\\.sql")).filter(p->version(p)<82).sorted(Comparator.comparingInt(ProductionBatchP2MysqlTest::version)).toList();}
            boolean migrated73=false,migrated80=false;
            for(Path script:scripts){
                if(version(script)>73&&!migrated73){ProcessTypeMigration.migrate(same);migrated73=true;}
                if(version(script)>80&&!migrated80){ProcessRuleMigration.migrate(same);assertEquals(0,same.queryForObject("SELECT COUNT(*) FROM metal_material WHERE NULLIF(TRIM(material_name),'') IS NULL AND NULLIF(TRIM(material_type),'') IS NULL",Integer.class));same.update("UPDATE metal_material SET material_name=TRIM(material_type) WHERE NULLIF(TRIM(material_name),'') IS NULL");migrated80=true;}
                execute(connection,script);
            }
        }
        seedHistoricalStock();
        for(String table:HISTORY_TABLES){
            String columns=String.join(",",jdbc.queryForList("SELECT COLUMN_NAME FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=? ORDER BY ORDINAL_POSITION",String.class,table).stream().map(s->"`"+s+"`").toList());
            historyColumns.put(table,columns);history.put(table,jdbc.queryForList("SELECT "+columns+" FROM "+table+" ORDER BY 1"));
        }
        try(Connection connection=ds.getConnection()){execute(connection,MIGRATIONS.resolve("V82__production_batch_storage_slices.sql"));}
    }
    static int version(Path path){return Integer.parseInt(path.getFileName().toString().split("__")[0].substring(1));}
    static void execute(Connection connection,Path path)throws Exception {
        String sql=Files.readString(path,StandardCharsets.UTF_8);if(sql.startsWith("\uFEFF"))sql=sql.substring(1);
        ScriptUtils.executeSqlScript(connection,new EncodedResource(new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8),path.getFileName().toString()),StandardCharsets.UTF_8));
    }
    static void seedHistoricalStock(){
        jdbc.update("INSERT INTO metal_factory(factory_id,factory_name,area,address) VALUES(7001,'P2 historical',1,'synthetic')");
        jdbc.update("INSERT INTO metal_production_line(production_line_id,production_line_code,production_line_name,factory_id) VALUES(7101,'P2-H-L','history line',7001)");
        jdbc.update("INSERT INTO metal_product(product_id,factory_id,product_code,product_name,product_category,product_type,metal_type_id,dimensions,unit) VALUES(7201,7001,'P2-H-P','history product','铱杆实心','杆实心',1,'{}','件')");
        jdbc.update("INSERT INTO metal_process_type(type_id,factory_id,type_name) VALUES(7301,7001,'P2 history type')");
        jdbc.update("INSERT INTO metal_process(process_id,factory_id,process_name,process_type,semantic_code) VALUES(7401,7001,'history process',7301,'NORMAL')");
        jdbc.update("INSERT INTO metal_production_order(id,order_no,factory_id,production_line_id,owner_id,processing_type,order_date,factory_name,line_name,owner_name,request_id,request_actor,request_hash,create_by,creator_name,flow_version) VALUES(7501,'P2-HISTORY',7001,7101,1,'DIRECT_CUSTOM',CURRENT_DATE,'history','history','test',?,1,REPEAT('a',64),'test','test',1)",UUID.randomUUID().toString());
        jdbc.update("INSERT INTO metal_production_order_item(id,order_id,sequence_no,product_id,quantity,product_snapshot) VALUES(7601,7501,1,7201,30,JSON_OBJECT('name','history'))");
        jdbc.update("INSERT INTO metal_production_order_step(id,item_id,sequence_no,process_id,process_name,process_snapshot,semantic_code,output_category,output_form,measurement_mode) VALUES(76011,7601,1,7401,'history','{}','NORMAL','SEMI','SEMI','PIECE')");
        jdbc.update("INSERT INTO metal_production_execution(id,factory_id,order_id,order_item_id,order_step_id,processor_id,planned_quantity,inherited_snapshot,create_by,input_weight,output_weight,input_quantity,output_quantity) VALUES(7701,7001,7501,7601,76011,1,30,'[]',1,300,300,30,30)");
        jdbc.update("INSERT INTO metal_storage_document(id,factory_id,number,kind,type,status,order_id,execution_id,creator_id,reviewer_id,business_date,data) VALUES(7801,7001,'P2-HIST-DOC','IN','SEMI_IN','POSTED',7501,7701,1,1,CURRENT_DATE,JSON_OBJECT('history','keep'))");
        jdbc.update("INSERT INTO metal_storage_document_line(id,factory_id,document_id,sequence_no,data) VALUES(7802,7001,7801,1,JSON_OBJECT('netWeight','300.00','quantity',30))");
        jdbc.update("INSERT INTO metal_storage_event(id,factory_id,document_id,line_id,action,request_hash,result) VALUES(7902,7001,7801,7802,'INBOUND',REPEAT('c',64),'{}')");
        jdbc.update("INSERT INTO metal_warehouse(warehouse_id,factory_id,name,create_by) VALUES(7901,7001,'history warehouse','test')");
        jdbc.update("INSERT INTO metal_storage_stock(id,factory_id,warehouse_id,category,product_id,identity_key,identity_snapshot,measurement_mode,batch_no,origin_order_id,origin_step_id,origin_document_id,origin_line_id,net_weight,quantity) VALUES(7903,7001,7901,'SEMI',7201,REPEAT('d',64),JSON_OBJECT('frozen','history','originCustomerWeight','100.00','originFactoryWeight','200.00'),'PIECE','ST-P2-H',7501,76011,7801,7802,300,30)");
        jdbc.update("INSERT INTO metal_storage_ownership VALUES(7001,7903,0,300,30)");
        jdbc.update("INSERT INTO metal_storage_entry(event_id,factory_id,stock_id,owner_id,ledger,weight_delta,quantity_delta,reason) VALUES(7902,7001,7903,0,'PHYSICAL',300,30,'RECEIPT')");
        jdbc.update("INSERT INTO metal_production_execution_event(factory_id,execution_id,document_id,line_id,event_type,stock_id,weight,quantity,customer_weight,factory_weight) VALUES(7001,7701,7801,7802,'OUTPUT',7903,300,30,100,200)");
    }

    @Test @Order(1) void migrationPartitionsHistoricalStockWithoutRewritingAnyOriginalColumn(){
        history.forEach((table,rows)->assertEquals(rows,jdbc.queryForList("SELECT "+historyColumns.get(table)+" FROM "+table+" ORDER BY 1"),table));
        var slice=jdbc.queryForMap("SELECT * FROM metal_storage_batch_slice WHERE stock_id=7903");
        assertNull(slice.get("batch_id"));assertEquals("UNASSIGNED",slice.get("slice_kind"));
        assertEquals(new BigDecimal("300.00"),slice.get("weight"));assertEquals(30L,((Number)slice.get("quantity")).longValue());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM metal_storage_batch_slice_entry",Integer.class));
        assertAllBalanced();
    }

    @Test @Order(2) void schemaRejectsDuplicateUnassignedAndWrongStockOwnerReferences(){
        tx.executeWithoutResult(status->{
            assertThrows(DataAccessException.class,()->jdbc.update("INSERT INTO metal_storage_batch_slice(factory_id,stock_id,batch_id,slice_kind,weight,quantity) VALUES(7001,7903,NULL,'UNASSIGNED',0,0)"));
            assertThrows(DataAccessException.class,()->jdbc.update("INSERT INTO metal_storage_batch_slice(factory_id,stock_id,batch_id,slice_kind,weight,quantity) VALUES(7001,7903,NULL,'BATCH',0,0)"));
            assertThrows(DataAccessException.class,()->jdbc.update("UPDATE metal_storage_batch_slice SET weight=-1 WHERE stock_id=7903"));
            long historySlice=jdbc.queryForObject("SELECT id FROM metal_storage_batch_slice WHERE stock_id=7903",Long.class);
            assertThrows(DataAccessException.class,()->jdbc.update("INSERT INTO metal_storage_batch_slice_owner(factory_id,stock_slice_id,owner_id,weight,quantity) VALUES(9999,?,99,0,0)",historySlice));
            status.setRollbackOnly();
        });
    }

    static void seedCurrentBusiness(){
        jdbc.update("INSERT INTO metal_factory(factory_id,factory_name,area,address) VALUES(9001,'P2 current',1,'synthetic')");
        jdbc.update("UPDATE sys_user SET factory_id=9001 WHERE user_id=1");
        jdbc.update("INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT 1,menu_id FROM sys_menu WHERE perms IN ('metal:warehouse-order:receive','metal:warehouse-order:accept') AND status='0'");
        jdbc.update("INSERT INTO metal_warehouse(warehouse_id,factory_id,name,create_by) VALUES(9101,9001,'P2 warehouse','test')");
        jdbc.update("INSERT INTO metal_production_line(production_line_id,production_line_code,production_line_name,factory_id) VALUES(9151,'P2-C-L','P2 current line',9001)");
        jdbc.update("INSERT INTO metal_customer(customer_id,factory_id,customer_name,company_name,address,contact_name,contact_phone) VALUES(9201,9001,'P2 customer','P2 company','isolated','tester','13800138000')");
        jdbc.update("INSERT INTO metal_type(metal_type_id,factory_id,name) VALUES(9301,9001,'P2 iridium')");
        jdbc.update("INSERT INTO metal_material(material_id,factory_id,material_code,material_name,material_type,specification,metal_type_id,measurement_mode,material_form) VALUES(9401,9001,'P2-RAW','P2 raw','铱块','P2',9301,'PIECE','METAL')");
        jdbc.update("INSERT INTO metal_product(product_id,factory_id,product_code,product_name,product_category,product_type,metal_type_id,specification,purity,dimensions,unit) VALUES(9501,9001,'P2-P','P2 product','铱杆实心','杆实心',9301,'P2',99,'{}','件')");
        jdbc.update("INSERT INTO metal_process_type(type_id,factory_id,type_name) VALUES(9601,9001,'P2 ordinary')");
        for(int seq=1;seq<=3;seq++)jdbc.update("INSERT INTO metal_process(process_id,factory_id,process_name,process_type,semantic_code) VALUES(?,9001,?,9601,'NORMAL')",9601+seq,"P2 stage "+seq);
        jdbc.update("INSERT INTO metal_production_order(id,order_no,factory_id,customer_id,production_line_id,owner_id,processing_type,order_date,factory_name,line_name,owner_name,request_id,request_actor,request_hash,create_by,creator_name,flow_version) VALUES(9701,'P2-ORDER',9001,9201,9151,1,'SHORTAGE_CUSTOM',CURRENT_DATE,'P2 current','P2 line','tester',?,1,REPEAT('e',64),'test','test',1)",UUID.randomUUID().toString());
        jdbc.update("INSERT INTO metal_production_order_item(id,order_id,sequence_no,product_id,quantity,product_snapshot) VALUES(9801,9701,1,9501,100,JSON_OBJECT('name','P2 product','metalTypeId','9301','specification','P2','purity','99','code','P2-P'))");
        for(int seq=1;seq<=3;seq++)jdbc.update("INSERT INTO metal_production_order_step(id,item_id,sequence_no,process_id,process_name,process_snapshot,semantic_code,output_category,output_form,measurement_mode) VALUES(?,9801,?,?,?,JSON_OBJECT('node',?),'NORMAL',?,'SEMI','PIECE')",98010+seq,seq,9601+seq,"P2 stage "+seq,seq,seq==3?"PRODUCT":"SEMI");
    }
    static void login(){var user=new SysUser();user.setUserId(1L);user.setUserName("p2-test");user.setFactoryId(F);var login=new LoginUser(1L,1L,user,Set.of("*:*:*"));SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(login,null,login.getAuthorities()));}
    @AfterEach void logout(){SecurityContextHolder.clearContext();}
    FactoryScope scope(){
        var scope=mock(FactoryScope.class);when(scope.isAdmin()).thenReturn(true);when(scope.resolveFactory(any())).thenAnswer(i->{Long f=i.getArgument(0);return f==null?F:f;});
        doAnswer(i->{Collection<Long> ids=i.getArgument(0);for(long id:ids)jdbc.queryForList("SELECT factory_id FROM metal_factory WHERE factory_id=? FOR UPDATE",id);return null;}).when(scope).lockFactories(any());return scope;
    }
    ProductionExecutionService executions(){return new ProductionExecutionService(jdbc,scope());}
    ProductionBatchService batches(){var scope=scope();return new ProductionBatchService(jdbc,scope,new ProductionExecutionService(jdbc,scope));}
    StorageReservationService reservations(){return new StorageReservationService(jdbc,scope());}
    StorageOrderService orders(){var scope=scope();return new StorageOrderService(jdbc,scope,new StorageLedgerService(jdbc,scope),new ProductionExecutionService(jdbc,scope),new StorageReservationService(jdbc,scope));}
    static Map<String,Object> command(){var m=new LinkedHashMap<String,Object>();m.put("factoryId",String.valueOf(F));m.put("requestId",UUID.randomUUID().toString());return m;}
    static long id(Object id){return Long.parseLong(id.toString());}
    static Map<String,Object> line(String weight,int quantity){var l=new LinkedHashMap<String,Object>();l.put("warehouseId",String.valueOf(WAREHOUSE));l.put("netWeight",weight);l.put("quantity",quantity);return l;}
    Map<String,Object> post(Map<String,Object> body){
        return approve(submit(body));
    }
    Map<String,Object> submit(Map<String,Object> body){
        var service=orders();var created=service.create(body);long id=id(created.get("id"));
        var submit=command();submit.put("version",created.get("version"));submit.put("reviewRound",created.get("reviewRound"));
        return service.action(id,"submit",submit);
    }
    Map<String,Object> approve(Map<String,Object> submitted){
        var review=command();review.put("version",submitted.get("version"));review.put("reviewRound",submitted.get("reviewRound"));
        var actuals=new ArrayList<Map<String,Object>>();
        for(var raw:StorageSupport.maps(submitted.get("lines"))){var actual=new LinkedHashMap<String,Object>();actual.put("id",raw.get("id"));actual.put("actualNetWeight",raw.get("netWeight"));actual.put("actualQuantity",raw.get("quantity"));actuals.add(actual);}
        review.put("lines",actuals);var posted=orders().action(id(submitted.get("id")),"review",review);assertEquals("POSTED",posted.get("status"));return posted;
    }
    Map<String,Object> document(String type,List<Map<String,Object>> lines){var d=command();d.put("type",type);d.put("date","2026-11-12");d.put("reviewerId","1");d.put("lines",lines);return d;}
    long rawStock(){var l=line("1000.00",100);l.put("materialId",String.valueOf(MATERIAL));l.put("ownerId","0");l.put("form","METAL");l.put("purity","99");return id(StorageSupport.maps(post(document("RAW_IN",List.of(l))).get("lines")).get(0).get("stockId"));}
    Map<String,Object> dispatchReservation(long stock,long slice,String weight,int qty){
        var l=line(weight,qty);l.put("stockId",String.valueOf(stock));l.put("stockSliceId",String.valueOf(slice));l.put("sliceVersion",jdbc.queryForObject("SELECT version FROM metal_storage_batch_slice WHERE id=?",Integer.class,slice));l.put("stockVersion",jdbc.queryForObject("SELECT version FROM metal_storage_stock WHERE id=?",Integer.class,stock));l.put("reservationLineId",UUID.randomUUID().toString());
        var body=command();body.put("sessionId",UUID.randomUUID().toString());body.put("reservationVersion",0);body.put("type","RAW_OUT");body.put("customerId",String.valueOf(CUSTOMER));body.put("lines",List.of(l));return body;
    }
    static long slice(long stock,long batch){return jdbc.queryForObject("SELECT id FROM metal_storage_batch_slice WHERE factory_id=? AND stock_id=? AND batch_key=?",Long.class,F,stock,batch);}
    Map<String,Object> createBatch(int planned){var body=command();body.put("orderId",String.valueOf(ORDER));body.put("orderItemId",String.valueOf(ITEM));body.put("orderVersion",jdbc.queryForObject("SELECT version FROM metal_production_order WHERE id=?",Integer.class,ORDER));body.put("plannedQuantity",planned);return batches().create(body);}
    long start(long batch,int step){var body=command();body.put("batchVersion",jdbc.queryForObject("SELECT version FROM metal_production_batch WHERE id=?",Integer.class,batch));body.put("intent","START_STAGE");body.put("orderStepId",String.valueOf(98010+step));body.put("processorId","1");return id(batches().start(batch,body).get("executionId"));}
    Map<String,Object> issue(long batch,long execution,long stock,long sourceSlice,String weight,int qty,long owner){
        var l=line(weight,qty);l.put("stockId",String.valueOf(stock));l.put("stockSliceId",String.valueOf(sourceSlice));l.put("sliceVersion",jdbc.queryForObject("SELECT version FROM metal_storage_batch_slice WHERE id=?",Integer.class,sourceSlice));l.put("stockVersion",jdbc.queryForObject("SELECT version FROM metal_storage_stock WHERE id=?",Integer.class,stock));
        l.put("reservationLineId",UUID.randomUUID().toString());l.put("allocations",List.of(Map.of("ownerId",String.valueOf(owner),"weight",weight,"quantity",qty)));
        var reserve=command();reserve.put("sessionId",UUID.randomUUID().toString());reserve.put("reservationVersion",0);reserve.put("type","PRODUCTION_OUT");reserve.put("orderId",String.valueOf(ORDER));reserve.put("batchId",String.valueOf(batch));reserve.put("executionId",String.valueOf(execution));reserve.put("lines",List.of(l));
        var reserved=reservations().replace(reserve);var body=document("PRODUCTION_OUT",StorageSupport.maps(reserved.get("lines")));body.put("orderId",String.valueOf(ORDER));body.put("batchId",String.valueOf(batch));body.put("executionId",String.valueOf(execution));body.put("sessionId",reserved.get("sessionId"));body.put("reservationVersion",reserved.get("version"));return post(body);
    }
    void close(long execution){var body=command();body.put("version",jdbc.queryForObject("SELECT version FROM metal_production_execution WHERE id=?",Integer.class,execution));body.put("physicalChecked",true);body.put("reason","P2 isolated stage completion");executions().close(execution,body);}

    @Test @Order(3) void realReservationReviewAndThreeStagesKeepOneBatchAndDoNotRepeatDebt(){
        seedCurrentBusiness();login();
        tx.executeWithoutResult(status->{
            long stock=rawStock();assertAllBalanced();long batch=id(createBatch(100).get("batchId"));
            long firstExecution=0;String originalNumber=jdbc.queryForObject("SELECT number FROM metal_production_batch WHERE id=?",String.class,batch);
            for(int step=1;step<=3;step++){
                long execution=start(batch,step);if(step==1)firstExecution=execution;
                issue(batch,execution,stock,slice(stock,step==1?0:batch),"1000.00",100,step==1?0:CUSTOMER);
                assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM metal_storage_debt WHERE work_order_id=?",Integer.class,ORDER));
                var output=line("1000.00",100);output.put("productId",String.valueOf(PRODUCT));output.put("purity","99");var body=document(step==3?"PRODUCT_IN":"SEMI_IN",List.of(output));body.put("orderId",String.valueOf(ORDER));body.put("batchId",String.valueOf(batch));body.put("executionId",String.valueOf(execution));
                var posted=post(body);stock=id(StorageSupport.maps(posted.get("lines")).get(0).get("stockId"));assertEquals(batch,jdbc.queryForObject("SELECT batch_id FROM metal_storage_document WHERE id=?",Long.class,id(posted.get("id"))));
                assertEquals(batch,jdbc.queryForObject("SELECT batch_id FROM metal_storage_batch_slice WHERE stock_id=?",Long.class,stock));
                close(execution);assertAllBalanced();
            }
            assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM metal_production_execution WHERE batch_id=?",Integer.class,batch));assertEquals(originalNumber,jdbc.queryForObject("SELECT number FROM metal_production_batch WHERE id=?",String.class,batch));
            assertEquals(new BigDecimal("1000.00"),jdbc.queryForObject("SELECT SUM(remaining_weight) FROM metal_storage_debt WHERE work_order_id=?",BigDecimal.class,ORDER));
            assertEquals(6,jdbc.queryForObject("SELECT COUNT(*) FROM metal_production_execution_event WHERE execution_id IN (SELECT id FROM metal_production_execution WHERE batch_id=?)",Integer.class,batch));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM metal_storage_reservation_session WHERE factory_id=? AND status='BOUND'",Integer.class,F));
            var finish=command();finish.put("version",jdbc.queryForObject("SELECT version FROM metal_production_batch WHERE id=?",Integer.class,batch));finish.put("reason","All three stages and final receipt verified");
            var finished=batches().complete(batch,finish);assertEquals("COMPLETED",finished.get("lifecycle"));assertEquals(finished,batches().complete(batch,finish));
            var orderFinish=command();orderFinish.put("version",jdbc.queryForObject("SELECT version FROM metal_production_order WHERE id=?",Integer.class,ORDER));
            assertEquals("COMPLETED",executions().complete(ORDER,orderFinish).get("status"));
            assertEquals(new BigDecimal("1000.00"),jdbc.queryForObject("SELECT SUM(remaining_weight) FROM metal_storage_debt WHERE work_order_id=?",BigDecimal.class,ORDER),"Production completion must not settle material debt");
            assertAllBalanced();
            assertThrows(ServiceException.class,()->start(batch,1));assertTrue(firstExecution>0);status.setRollbackOnly();
        });
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM metal_production_batch WHERE factory_id=?",Integer.class,F));
    }

    @Test @Order(4) void multipleSlicesCannotSpendAnotherSlicesPhysicalQuantityOrOwnership(){
        login();tx.executeWithoutResult(status->{
            long stock=rawStock(),unassigned=slice(stock,0);long a=id(createBatch(30).get("batchId")),b=id(createBatch(70).get("batchId"));
            // Synthetic partitions exercise P2 guards; this is not a P3 split operation.
            jdbc.update("UPDATE metal_storage_batch_slice SET weight=0,quantity=0 WHERE id=?",unassigned);
            jdbc.update("UPDATE metal_storage_batch_slice_owner SET weight=0,quantity=0 WHERE stock_slice_id=?",unassigned);
            jdbc.update("UPDATE metal_storage_ownership SET weight=700,quantity=70 WHERE stock_id=? AND owner_id=0",stock);
            jdbc.update("INSERT INTO metal_storage_ownership VALUES(?,?,?,300,30)",F,stock,CUSTOMER);
            jdbc.update("INSERT INTO metal_storage_batch_slice(factory_id,stock_id,batch_id,slice_kind,weight,quantity) VALUES(?,?,?,'BATCH',300,30),(?,?,?,'BATCH',700,70)",F,stock,a,F,stock,b);
            long as=slice(stock,a),bs=slice(stock,b);
            jdbc.update("INSERT INTO metal_storage_batch_slice_owner VALUES(?,?,?,300,30),(?,?,0,700,70)",F,as,CUSTOMER,F,bs);
            assertAllBalanced();
            assertThrows(ServiceException.class,()->reservations().replace(dispatchReservation(stock,as,"400.00",40)));
            assertThrows(ServiceException.class,()->reservations().replace(dispatchReservation(stock,bs,"100.00",10)));
            var successful=reservations().replace(dispatchReservation(stock,as,"300.00",30));
            assertEquals("TEMP",successful.get("status"));
            assertEquals(as,jdbc.queryForObject("SELECT DISTINCT stock_slice_id FROM metal_storage_reservation WHERE session_id=?",Long.class,successful.get("sessionId")));
            assertThrows(ServiceException.class,()->reservations().replace(dispatchReservation(stock,as,"10.00",1)));
            assertAllBalanced();status.setRollbackOnly();
        });
    }

    @Test @Order(5) void rawReturnAndReversalKeepPartitionTotalsAndOriginalIssueReference(){
        login();tx.executeWithoutResult(status->{
            long stock=rawStock(),rawSlice=slice(stock,0),batch=id(createBatch(100).get("batchId")),execution=start(batch,1);
            var issued=issue(batch,execution,stock,rawSlice,"1000.00",100,0);long issueLine=id(StorageSupport.maps(issued.get("lines")).get(0).get("id"));
            var returned=line("100.00",10);returned.put("stockId",String.valueOf(stock));returned.put("stockSliceId",String.valueOf(rawSlice));returned.put("issueLineId",String.valueOf(issueLine));
            var returnBody=document("RAW_RETURN",List.of(returned));returnBody.put("orderId",String.valueOf(ORDER));returnBody.put("batchId",String.valueOf(batch));returnBody.put("executionId",String.valueOf(execution));
            var postedReturn=post(returnBody);assertEquals(new BigDecimal("100.00"),jdbc.queryForObject("SELECT weight FROM metal_storage_batch_slice WHERE id=?",BigDecimal.class,rawSlice));
            assertEquals(new BigDecimal("100.00"),jdbc.queryForObject("SELECT returned_weight FROM metal_storage_issue_allocation WHERE line_id=?",BigDecimal.class,issueLine));assertAllBalanced();
            var reverse=command();reverse.put("version",postedReturn.get("version"));reverse.put("reviewRound",postedReturn.get("reviewRound"));reverse.put("reviewerId","1");reverse.put("reason","isolated return reversal");
            var reverseDraft=orders().action(id(postedReturn.get("id")),"reverse",reverse);
            var review=command();review.put("version",reverseDraft.get("version"));review.put("reviewRound",reverseDraft.get("reviewRound"));review.put("lines",List.of());
            orders().action(id(reverseDraft.get("id")),"review",review);
            assertEquals(new BigDecimal("0.00"),jdbc.queryForObject("SELECT weight FROM metal_storage_batch_slice WHERE id=?",BigDecimal.class,rawSlice));assertEquals(new BigDecimal("0.00"),jdbc.queryForObject("SELECT returned_weight FROM metal_storage_issue_allocation WHERE line_id=?",BigDecimal.class,issueLine));assertAllBalanced();
            status.setRollbackOnly();
        });
    }

    @Test @Order(6) void overReservedReviewCannotConsumeStockAndLeavesReservationBound(){
        login();tx.executeWithoutResult(status->{
            long stock=rawStock(),source=slice(stock,0),batch=id(createBatch(100).get("batchId")),execution=start(batch,1);
            var l=line("1000.00",100);l.put("stockId",String.valueOf(stock));l.put("stockSliceId",String.valueOf(source));l.put("sliceVersion",jdbc.queryForObject("SELECT version FROM metal_storage_batch_slice WHERE id=?",Integer.class,source));l.put("stockVersion",jdbc.queryForObject("SELECT version FROM metal_storage_stock WHERE id=?",Integer.class,stock));l.put("reservationLineId",UUID.randomUUID().toString());l.put("allocations",List.of(Map.of("ownerId","0","weight","1000.00","quantity",100)));
            var reserve=command();reserve.put("type","PRODUCTION_OUT");reserve.put("orderId",String.valueOf(ORDER));reserve.put("batchId",String.valueOf(batch));reserve.put("executionId",String.valueOf(execution));reserve.put("sessionId",UUID.randomUUID().toString());reserve.put("reservationVersion",0);reserve.put("lines",List.of(l));
            var held=reservations().replace(reserve);var body=document("PRODUCTION_OUT",StorageSupport.maps(held.get("lines")));body.put("orderId",String.valueOf(ORDER));body.put("batchId",String.valueOf(batch));body.put("executionId",String.valueOf(execution));body.put("sessionId",held.get("sessionId"));body.put("reservationVersion",held.get("version"));
            var created=orders().create(body);var submit=command();submit.put("version",created.get("version"));submit.put("reviewRound",created.get("reviewRound"));var pending=orders().action(id(created.get("id")),"submit",submit);
            var review=command();review.put("version",pending.get("version"));review.put("reviewRound",pending.get("reviewRound"));review.put("lines",List.of(Map.of("id",StorageSupport.maps(pending.get("lines")).get(0).get("id"),"actualNetWeight","1001.00","actualQuantity",100,"differenceReason","must exceed reserved amount")));
            assertThrows(ServiceException.class,()->orders().action(id(created.get("id")),"review",review));
            assertEquals("BOUND",jdbc.queryForObject("SELECT status FROM metal_storage_reservation_session WHERE session_id=?",String.class,held.get("sessionId")));
            assertEquals(new BigDecimal("1000.00"),jdbc.queryForObject("SELECT weight FROM metal_storage_batch_slice WHERE id=?",BigDecimal.class,source));assertAllBalanced();status.setRollbackOnly();
        });
    }

    @Test @Order(7) void migrationPreflightRejectsUnbalancedOwnershipBeforePersistentDdl(){
        tx.executeWithoutResult(status->{
            jdbc.update("UPDATE metal_storage_ownership SET weight=299 WHERE stock_id=7903 AND owner_id=0");
            var error=assertThrows(RuntimeException.class,()->jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>)connection->{try{execute(connection,MIGRATIONS.resolve("V82__production_batch_storage_slices.sql"));return null;}catch(Exception e){throw new IllegalStateException(e);}}));
            String messages="";for(Throwable e=error;e!=null;e=e.getCause())messages+=e.getMessage();assertTrue(messages.contains("chk_p2_stock_ownership_balanced"),messages);
            status.setRollbackOnly();
        });assertAllBalanced();
    }

    @Test @Order(8) void concurrentReservationsCannotOverbookTheSameSliceFromOldRepeatableReadSnapshots()throws Exception {
        login();long stock=tx.execute(status->{var l=line("1000.00",100);l.put("materialId",String.valueOf(MATERIAL));l.put("ownerId",String.valueOf(CUSTOMER));l.put("form","METAL");return id(StorageSupport.maps(post(document("RAW_IN",List.of(l))).get("lines")).get(0).get("stockId"));});long source=slice(stock,0);
        var pool=Executors.newFixedThreadPool(2);var barrier=new CyclicBarrier(2);
        try{
            Callable<Boolean> reserve=()->{login();try{return tx.execute(status->{assertEquals("REPEATABLE-READ",jdbc.queryForObject("SELECT @@transaction_isolation",String.class));jdbc.queryForObject("SELECT COUNT(*) FROM metal_storage_reservation WHERE stock_slice_id=?",Integer.class,source);try{barrier.await(10,TimeUnit.SECONDS);}catch(Exception e){throw new IllegalStateException(e);}reservations().replace(dispatchReservation(stock,source,"600.00",60));return true;});}catch(ServiceException expected){return false;}finally{SecurityContextHolder.clearContext();}};
            var a=pool.submit(reserve);var b=pool.submit(reserve);assertNotEquals(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS));
            assertEquals(60,jdbc.queryForObject("SELECT SUM(quantity) FROM metal_storage_reservation WHERE stock_slice_id=?",Integer.class,source));assertAllBalanced();
        }finally{pool.shutdownNow();}
    }

    @Test @Order(9) void inventoryAdjustmentAndItsReversalMirrorTheExactSliceOwner(){
        login();tx.executeWithoutResult(status->{
            long stock=rawStock(),source=slice(stock,0);
            var l=line("10.00",1);l.put("stockId",String.valueOf(stock));l.put("stockSliceId",String.valueOf(source));l.put("ownerId","0");l.put("deltaWeight","10.00");l.put("deltaQuantity",1);l.put("reason","isolated adjustment");
            var body=document("ADJUSTMENT",List.of(l));body.put("reason","isolated adjustment");var adjusted=post(body);
            assertEquals(new BigDecimal("1010.00"),jdbc.queryForObject("SELECT weight FROM metal_storage_batch_slice WHERE id=?",BigDecimal.class,source));assertAllBalanced();
            var reverse=command();reverse.put("version",adjusted.get("version"));reverse.put("reviewRound",adjusted.get("reviewRound"));reverse.put("reviewerId","1");reverse.put("reason","isolated adjustment reversal");var pending=orders().action(id(adjusted.get("id")),"reverse",reverse);
            var review=command();review.put("version",pending.get("version"));review.put("reviewRound",pending.get("reviewRound"));review.put("lines",List.of());orders().action(id(pending.get("id")),"review",review);
            assertEquals(new BigDecimal("1000.00"),jdbc.queryForObject("SELECT weight FROM metal_storage_batch_slice WHERE id=?",BigDecimal.class,source));assertAllBalanced();status.setRollbackOnly();
        });
    }

    @Test @Order(10) void partialOutputSourceIsImmutableAndReturnCannotSpendAlreadyProducedMaterial(){
        login();tx.executeWithoutResult(status->{
            long stock=rawStock(),raw=slice(stock,0),batch=id(createBatch(100).get("batchId")),execution=start(batch,1);
            var issued=issue(batch,execution,stock,raw,"1000.00",100,0);long originalIssue=id(StorageSupport.maps(issued.get("lines")).get(0).get("id"));
            var output=line("300.00",30);output.put("productId",String.valueOf(PRODUCT));output.put("purity","99");var produced=document("SEMI_IN",List.of(output));produced.put("orderId",String.valueOf(ORDER));produced.put("batchId",String.valueOf(batch));produced.put("executionId",String.valueOf(execution));var result=post(produced);long outputStock=id(StorageSupport.maps(result.get("lines")).get(0).get("stockId"));
            String identity=jdbc.queryForObject("SELECT CAST(identity_snapshot AS CHAR) FROM metal_storage_stock WHERE id=?",String.class,outputStock);
            var origin=jdbc.queryForList("SELECT customer_weight,factory_weight FROM metal_production_execution_event WHERE execution_id=? AND event_type='OUTPUT'",execution);
            var tooMuch=line("800.00",80);tooMuch.put("stockId",String.valueOf(stock));tooMuch.put("stockSliceId",String.valueOf(raw));tooMuch.put("issueLineId",String.valueOf(originalIssue));var invalid=document("RAW_RETURN",List.of(tooMuch));invalid.put("orderId",String.valueOf(ORDER));invalid.put("batchId",String.valueOf(batch));invalid.put("executionId",String.valueOf(execution));
            assertThrows(ServiceException.class,()->post(invalid));assertEquals(new BigDecimal("0.00"),jdbc.queryForObject("SELECT net_weight FROM metal_storage_stock WHERE id=?",BigDecimal.class,stock));
            var returned=line("100.00",10);returned.put("stockId",String.valueOf(stock));returned.put("stockSliceId",String.valueOf(raw));returned.put("issueLineId",String.valueOf(originalIssue));var valid=document("RAW_RETURN",List.of(returned));valid.put("orderId",String.valueOf(ORDER));valid.put("batchId",String.valueOf(batch));valid.put("executionId",String.valueOf(execution));post(valid);
            assertEquals(identity,jdbc.queryForObject("SELECT CAST(identity_snapshot AS CHAR) FROM metal_storage_stock WHERE id=?",String.class,outputStock));assertEquals(origin,jdbc.queryForList("SELECT customer_weight,factory_weight FROM metal_production_execution_event WHERE execution_id=? AND event_type='OUTPUT'",execution));assertAllBalanced();status.setRollbackOnly();
        });
    }

    @Test @Order(11) void twoBoundDocumentsOnOneSliceRemainReviewableAfterTheFirstChangesItsVersion(){
        login();tx.executeWithoutResult(status->{
            var receipt=line("1000.00",100);receipt.put("materialId",String.valueOf(MATERIAL));receipt.put("ownerId",String.valueOf(CUSTOMER));receipt.put("form","METAL");long stock=id(StorageSupport.maps(post(document("RAW_IN",List.of(receipt))).get("lines")).get(0).get("stockId")),source=slice(stock,0);
            var firstHeld=reservations().replace(dispatchReservation(stock,source,"300.00",30));
            var secondHeld=reservations().replace(dispatchReservation(stock,source,"700.00",70));
            var first=document("RAW_OUT",StorageSupport.maps(firstHeld.get("lines")));first.put("customerId",String.valueOf(CUSTOMER));first.put("sessionId",firstHeld.get("sessionId"));first.put("reservationVersion",firstHeld.get("version"));
            var second=document("RAW_OUT",StorageSupport.maps(secondHeld.get("lines")));second.put("customerId",String.valueOf(CUSTOMER));second.put("sessionId",secondHeld.get("sessionId"));second.put("reservationVersion",secondHeld.get("version"));
            var firstPending=submit(first);var secondPending=submit(second);
            int prior=jdbc.queryForObject("SELECT version FROM metal_storage_batch_slice WHERE id=?",Integer.class,source);
            approve(firstPending);assertTrue(jdbc.queryForObject("SELECT version FROM metal_storage_batch_slice WHERE id=?",Integer.class,source)>prior);
            assertEquals("BOUND",jdbc.queryForObject("SELECT status FROM metal_storage_reservation_session WHERE session_id=?",String.class,secondHeld.get("sessionId")));
            approve(secondPending);
            assertEquals(new BigDecimal("0.00"),jdbc.queryForObject("SELECT weight FROM metal_storage_batch_slice WHERE id=?",BigDecimal.class,source));
            assertEquals("CONSUMED",jdbc.queryForObject("SELECT status FROM metal_storage_reservation_session WHERE session_id=?",String.class,secondHeld.get("sessionId")));assertAllBalanced();status.setRollbackOnly();
        });
    }

    @Test @Order(12) void reviewFailureAfterSliceWritesRollsBackPhysicalOwnershipSliceAndTaskTogether(){
        login();var receipt=line("1000.00",100);receipt.put("materialId",String.valueOf(MATERIAL));receipt.put("ownerId","0");receipt.put("form","METAL");
        var pending=tx.execute(status->submit(document("RAW_IN",List.of(receipt))));long document=id(pending.get("id"));
        var failure=spy(jdbc);doThrow(new IllegalStateException("P2 injected slice audit failure")).when(failure).update(startsWith("INSERT INTO metal_storage_batch_slice_entry"),any(Object[].class));
        var scope=scope();var broken=new StorageOrderService(failure,scope,new StorageLedgerService(failure,scope),new ProductionExecutionService(failure,scope),new StorageReservationService(failure,scope));
        var review=command();review.put("version",pending.get("version"));review.put("reviewRound",pending.get("reviewRound"));review.put("lines",List.of(Map.of("id",StorageSupport.maps(pending.get("lines")).get(0).get("id"),"actualNetWeight","1000.00","actualQuantity",100)));
        assertThrows(IllegalStateException.class,()->tx.execute(status->broken.action(document,"review",review)));
        assertEquals("PENDING_REVIEW",jdbc.queryForObject("SELECT status FROM metal_storage_document WHERE id=?",String.class,document));
        assertEquals(pending.get("version"),jdbc.queryForObject("SELECT version FROM metal_storage_document WHERE id=?",Integer.class,document));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM metal_storage_event WHERE document_id=?",Integer.class,document));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM metal_storage_stock WHERE origin_document_id=?",Integer.class,document));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM metal_storage_operation WHERE request_id=?",Integer.class,review.get("requestId")));
        assertEquals("OPEN",jdbc.queryForObject("SELECT status FROM metal_storage_review_task WHERE document_id=?",String.class,document));assertAllBalanced();
    }

    static void assertAllBalanced(){
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM metal_storage_stock s LEFT JOIN (SELECT factory_id,stock_id,SUM(weight) weight,SUM(quantity) quantity FROM metal_storage_batch_slice GROUP BY factory_id,stock_id) x ON x.factory_id=s.factory_id AND x.stock_id=s.id WHERE s.net_weight<>COALESCE(x.weight,0) OR s.quantity<>COALESCE(x.quantity,0)",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM metal_storage_batch_slice s LEFT JOIN (SELECT factory_id,stock_slice_id,SUM(weight) weight,SUM(quantity) quantity FROM metal_storage_batch_slice_owner GROUP BY factory_id,stock_slice_id) x ON x.factory_id=s.factory_id AND x.stock_slice_id=s.id WHERE s.weight<>COALESCE(x.weight,0) OR s.quantity<>COALESCE(x.quantity,0)",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM metal_storage_ownership o LEFT JOIN (SELECT s.factory_id,s.stock_id,o.owner_id,SUM(o.weight) weight,SUM(o.quantity) quantity FROM metal_storage_batch_slice_owner o JOIN metal_storage_batch_slice s ON s.id=o.stock_slice_id GROUP BY s.factory_id,s.stock_id,o.owner_id) x ON x.factory_id=o.factory_id AND x.stock_id=o.stock_id AND x.owner_id=o.owner_id WHERE o.weight<>COALESCE(x.weight,0) OR o.quantity<>COALESCE(x.quantity,0)",Integer.class));
    }
}
