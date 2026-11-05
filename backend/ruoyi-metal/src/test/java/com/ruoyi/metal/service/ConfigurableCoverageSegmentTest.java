package com.ruoyi.metal.service;

import java.sql.Connection;
import java.util.UUID;
import java.util.function.Consumer;
import com.ruoyi.metal.security.FactoryScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="METAL_FLOW_ISOLATED_JDBC_URL",matches=WarehouseIntegrationEnvironment.URL_PATTERN)
class ConfigurableCoverageSegmentTest {
    private static final class Fixture {
        final JdbcTemplate jdbc;
        final long factory,work,item,product,warehouse,actor;
        final long[] processes=new long[4];
        final String key=UUID.randomUUID().toString();
        final long firstBatch;

        Fixture(JdbcTemplate j){
            jdbc=j;factory=Long.parseLong(System.getenv("METAL_FLOW_FACTORY_ID"));
            assertEquals(System.getenv("METAL_FLOW_FIXTURE_PREFIX")+"厂A",j.queryForObject("SELECT factory_name FROM metal_factory WHERE factory_id=? FOR UPDATE",String.class,factory));
            long line=j.queryForObject("SELECT production_line_id FROM metal_production_line WHERE factory_id=? ORDER BY production_line_id LIMIT 1",Long.class,factory);
            actor=j.queryForObject("SELECT user_id FROM sys_user WHERE factory_id=? AND del_flag='0' ORDER BY user_id LIMIT 1",Long.class,factory);
            warehouse=j.queryForObject("SELECT warehouse_id FROM metal_warehouse WHERE factory_id=? AND del_flag='0' ORDER BY warehouse_id LIMIT 1",Long.class,factory);
            product=j.queryForObject("SELECT product_id FROM metal_product WHERE factory_id=? AND del_flag='0' ORDER BY product_id LIMIT 1",Long.class,factory);
            long type=j.queryForObject("SELECT process_type FROM metal_process WHERE factory_id=? AND del_flag='0' ORDER BY process_id LIMIT 1",Long.class,factory);
            assertEquals(1,j.update("INSERT INTO metal_work_order(order_no,request_id,request_hash,request_actor,customer,factory_id,production_line_id,owner_id,factory_name,line_name,owner_name,order_date,status,create_by,creator_name,update_by,task_type,processing_policy_version,processing_type) VALUES(?,?,REPEAT('a',64),?,'coverage fixture',?,?,?,'coverage fixture','coverage fixture','coverage fixture',CURRENT_DATE,'IN_PROGRESS','coverage-test','coverage-test','coverage-test','PRODUCTION',3,'DIRECT')","COV-"+key.substring(0,28),key,actor,factory,line,actor));
            work=lastId();
            j.update("INSERT INTO metal_work_order_item(work_order_id,sequence_no,product_id,quantity,product_snapshot) VALUES(?,1,?,10,JSON_OBJECT('name','coverage fixture','measurementPolicyVersion',3))",work,product);item=lastId();
            for(int n=0;n<4;n++){
                j.update("INSERT INTO metal_process(process_name,process_type,factory_id,create_by) VALUES(?,?,?,'coverage-test')","coverage-"+key+"-"+n,type,factory);processes[n]=lastId();
                j.update("INSERT INTO metal_work_order_step(item_id,process_id,sequence_no,process_name,theoretical_loss,start_time,end_time,measurement_mode,loss_unit,process_role,output_stage) VALUES(?,?,?,'coverage fixture',0,'2026-11-04 09:00:00','2026-11-04 10:00:00',?,'PERCENT','POST_PROCESS',?)",item,processes[n],n+1,n==1?"WEIGHT":"PIECE",n==3?"FINISHED":"SEMI");
            }
            firstBatch=batch(1,"SETTLED",9,9);batch(3,"SETTLED",4,4);
        }

        long lastId(){return jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);}
        long batch(int sequence,String status,int planned,int completed){
            jdbc.update("INSERT INTO metal_process_batch(request_id,request_hash,request_actor,factory_id,work_order_id,item_id,process_id,sequence_no,planned_quantity,completed_quantity,status,measurement_mode,processing_policy_version,process_role,output_stage) VALUES(?,REPEAT('a',64),?,?,?,?,?,?,?,?,?,'PIECE',3,'POST_PROCESS',?)",UUID.randomUUID().toString(),actor,factory,work,item,processes[sequence-1],sequence,planned,completed,status,sequence==4?"FINISHED":"SEMI");
            return lastId();
        }

        // A release-use ledger fixture exercises coverage SQL, without claiming a posted service flow.
        long releaseUse(){
            long target=batch(4,"OPEN",6,0);
            jdbc.update("INSERT INTO metal_inventory(factory_id,warehouse_id,category,reference_id,batch_no,code,name,origin_batch_id,quantity,net_weight,measurement_mode) VALUES(?,?,'SEMI',?,?,'COVERAGE','coverage fixture',?,6,60,'PIECE')",factory,warehouse,product,"COV-"+key,firstBatch);long stock=lastId();
            jdbc.update("INSERT INTO metal_semi_release(inventory_id,factory_id,work_order_id,request_id,request_hash,actor_id,quantity,net_weight,reason,measurement_mode) VALUES(?,?,?,?,REPEAT('a',64),?,6,60,'coverage-test','PIECE')",stock,factory,work,UUID.randomUUID().toString(),actor);long release=lastId();
            jdbc.update("INSERT INTO metal_warehouse_order(order_no,request_id,request_hash,request_actor,factory_id,warehouse_id,kind,type,order_date,work_order_id,batch_id,operator_id,keeper_id,operator_name,keeper_name,warehouse_name) VALUES(?,?,REPEAT('a',64),?,?,?,'OUT','PRODUCTION_OUT',CURRENT_DATE,?,?,?,?,'coverage fixture','coverage fixture','coverage fixture')","COVO-"+key.substring(0,27),UUID.randomUUID().toString(),actor,factory,warehouse,work,target,actor,actor);long order=lastId();
            jdbc.update("INSERT INTO metal_warehouse_order_line(order_id,inventory_id,sequence_no,category,reference_id,code,name,batch_no,net_weight,quantity,measurement_mode) VALUES(?,?,1,'SEMI',?,'COVERAGE','coverage fixture',?,60,6,'PIECE')",order,stock,product,"COV-"+key);long line=lastId();
            jdbc.update("INSERT INTO metal_semi_release_use(release_id,line_id,route_snapshot) VALUES(?,?,'{}')",release,line);
            return line;
        }
    }

    private void withFixture(Consumer<Fixture> test)throws Exception{
        try(Connection connection=WarehouseIntegrationEnvironment.dataSource().getConnection()){
            connection.setAutoCommit(false);
            var jdbc=new JdbcTemplate(new SingleConnectionDataSource(connection,true));
            Fixture fixture=null;
            try{fixture=new Fixture(jdbc);test.accept(fixture);}
            finally{
                connection.rollback();
                if(fixture!=null)assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM metal_work_order WHERE request_id=? AND factory_id=?",Integer.class,fixture.key,fixture.factory));
                connection.rollback();
            }
        }
    }

    @Test void newPieceSegmentDoesNotRestoreTheEarlierPieceCeiling()throws Exception{
        withFixture(f->{
            var semi=new SemiFlowService(f.jdbc,mock(FactoryScope.class));
            assertEquals(6,semi.entryCapacity(f.item,4));
            f.jdbc.update("UPDATE metal_work_order_step SET measurement_mode='PIECE' WHERE item_id=? AND sequence_no=2",f.item);
            assertEquals(1,semi.entryCapacity(f.item,4));
        });
    }

    @Test void downstreamReleasedPiecesCannotFillEarlierLocalWorkAcrossWeightConversion()throws Exception{
        withFixture(f->{
            var semi=new SemiFlowService(f.jdbc,mock(FactoryScope.class));long line=f.releaseUse();
            assertEquals(0,semi.credit(f.item,1));assertEquals(6,semi.credit(f.item,3));
            assertEquals(9L,f.jdbc.queryForObject("SELECT completed_quantity FROM metal_process_batch WHERE batch_id=?",Long.class,f.firstBatch));
            assertDoesNotThrow(()->semi.checkCapacity(f.item));
            f.jdbc.update("UPDATE metal_warehouse_order_line SET quantity=7 WHERE line_id=?",line);
            assertThrows(com.ruoyi.common.exception.ServiceException.class,()->semi.checkCapacity(f.item));
        });
    }
}
