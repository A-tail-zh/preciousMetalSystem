package com.ruoyi.metal.service;

import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.metal.mapper.CustomerMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.managed.ManagedTransactionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.*;
import static org.junit.jupiter.api.Assertions.*;

/** Uses the inherited empty-schema guard and synthetic fixtures, never an application database. */
class CustomerWarehouseReferenceMysqlTest extends AutomaticBatchFlowMysqlTest {
    static final long OTHER=9202;
    SqlSession customers() throws Exception {
        var tf=new ManagedTransactionFactory();var p=new Properties();p.setProperty("closeConnection","false");tf.setProperties(p);
        var ds=new SingleConnectionDataSource(DataSourceUtils.getConnection(jdbc.getDataSource()),true);
        var config=new Configuration(new Environment("isolated",tf,ds));
        try(var input=Files.newInputStream(Path.of("src/main/resources/mapper/metal/CustomerMapper.xml"))){new XMLMapperBuilder(input,config,"CustomerMapper.xml",config.getSqlFragments()).parse();}
        return new SqlSessionFactoryBuilder().build(config).openSession(false);
    }
    void other(){jdbc.update("INSERT INTO metal_customer(customer_id,factory_id,customer_name,company_name,address,contact_name,contact_phone) VALUES(9202,9001,'Synthetic extra','Synthetic','isolated','tester','19900000002')");}
    void rejectDelete(CustomerService service,long customer){assertEquals(409,assertThrows(ServiceException.class,()->service.delete(new Long[]{customer})).getCode());assertEquals("0",jdbc.queryForObject("SELECT del_flag FROM metal_customer WHERE customer_id=?",String.class,customer));}
    long raw(long owner,String weight,int quantity){var l=line(weight,quantity);l.put("materialId",MATERIAL);l.put("ownerId",owner);l.put("form","METAL");l.put("purity","99");return id(StorageSupport.maps(post(document("RAW_IN",List.of(l))).get("lines")).get(0).get("stockId"));}

    @Test void customerPendingRealArrivalRejectsDeletionAndStillApproves(){login();tx.executeWithoutResult(status->{seedCurrentBusiness();
        jdbc.update("UPDATE metal_material SET material_form='UNTREATED' WHERE material_id=?",MATERIAL);
        jdbc.update("INSERT INTO metal_line_product(production_line_id,product_id) VALUES(9151,9501)");
        jdbc.update("INSERT INTO metal_product_process(product_id,process_id,sequence_no,theoretical_loss,loss_unit,measurement_mode,output_category,output_form) SELECT 9501,process_id,sequence_no,0,'G_PER_PIECE','PIECE',output_category,output_form FROM metal_production_order_step WHERE item_id=9801");
        com.ruoyi.common.utils.SecurityUtils.getLoginUser().getUser().setNickName("Synthetic customer guard");
        var service=new ProductionOrderService(jdbc,scope());
        try{
            var field=ProductionOrderService.class.getDeclaredField("storageOrders");field.setAccessible(true);field.set(service,orders());
            var body=new ProductionOrderService.Create(F,UUID.randomUUID().toString(),CUSTOMER,"MATERIAL_CUSTOM","1000.00",9151L,"2026-11-12",1L,
                List.of(new ProductionOrderService.Item(PRODUCT,100,List.of(new ProductionOrderService.Step(9602L,null,null),new ProductionOrderService.Step(9603L,null,null),new ProductionOrderService.Step(9604L,null,null)))),
                List.of(Map.of("materialId",MATERIAL,"warehouseId",WAREHOUSE,"reviewerId",1,"netWeight","1000.00","quantity",100,"purity","99","form","UNTREATED")));
            long order=id(service.create(body).get("id"));long document=jdbc.queryForObject("SELECT id FROM metal_storage_document WHERE order_id=? AND type='CUSTOMER_IN'",Long.class,order);
            var pending=orders().detail(document,F);assertEquals("PENDING_REVIEW",pending.get("status"));
            try(var session=customers()){rejectDelete(new CustomerService(session.getMapper(CustomerMapper.class),scope()),CUSTOMER);}
            assertEquals("POSTED",approve(pending).get("status"));assertAllBalanced();
        }catch(Exception e){throw new IllegalStateException(e);}status.setRollbackOnly();
    });}

    @Test void customerLenderWithDebtCannotDeleteAndCanReceiveRepayment(){login();tx.executeWithoutResult(status->{seedCurrentBusiness();other();
        long stock=raw(OTHER,"500.00",50);var issue=line("500.00",50);issue.put("stockId",stock);issue.put("stockSliceId",slice(stock,0));issue.put("reservationLineId",UUID.randomUUID().toString());
        issue.put("stockVersion",jdbc.queryForObject("SELECT version FROM metal_storage_stock WHERE id=?",Integer.class,stock));issue.put("sliceVersion",jdbc.queryForObject("SELECT version FROM metal_storage_batch_slice WHERE id=?",Integer.class,slice(stock,0)));
        issue.put("allocations",List.of(Map.of("ownerId",OTHER,"weight","500.00","quantity",50)));automaticIssueLines(List.of(issue),1,null);
        long debt=jdbc.queryForObject("SELECT id FROM metal_storage_debt WHERE creditor_id=? AND debt_kind='BORROW'",Long.class,OTHER);
        try(var session=customers()){rejectDelete(new CustomerService(session.getMapper(CustomerMapper.class),scope()),OTHER);}catch(Exception e){throw new IllegalStateException(e);}
        long repayment=raw(0,"500.00",50);var l=line("500.00",50);l.put("stockId",repayment);l.put("ownerId",0);l.put("debtAllocations",List.of(Map.of("debtId",debt,"weight","500.00","quantity",50)));
        post(document("OWNERSHIP_RETURN",List.of(l)));assertEquals(BigDecimal.ZERO.setScale(2),jdbc.queryForObject("SELECT remaining_weight FROM metal_storage_debt WHERE id=?",BigDecimal.class,debt));assertAllBalanced();status.setRollbackOnly();
    });}

    @Test void customerPendingJsonReferencesMatchNumericAndStringIdsOnlyWhileActive(){login();tx.executeWithoutResult(status->{seedCurrentBusiness();other();
        jdbc.update("INSERT INTO metal_storage_document(factory_id,number,kind,type,creator_id,reviewer_id,business_date,data) VALUES(9001,'CUSTOMER-GUARD','IN','RAW_IN',1,1,CURRENT_DATE,'{}')");
        long document=jdbc.queryForObject("SELECT id FROM metal_storage_document WHERE number='CUSTOMER-GUARD'",Long.class);
        jdbc.update("INSERT INTO metal_storage_document_line(factory_id,document_id,sequence_no,data) VALUES(9001,?,1,'{}')",document);
        try(var session=customers()){
            var mapper=session.getMapper(CustomerMapper.class);var service=new CustomerService(mapper,scope());assertFalse(mapper.hasActiveReferences(F,OTHER));
            for(Object value:List.of(OTHER,Long.toString(OTHER)," \t"+OTHER+"\n"))for(String field:List.of("headerCustomer","ownerId","ownerCustomerId","customerId","borrowOwnerId","allocations")){
                String header=JSON.toJSONString(field.equals("headerCustomer")?Map.of("customerId",value):Map.of());
                String line=JSON.toJSONString(field.equals("headerCustomer")?Map.of():field.equals("allocations")?Map.of("allocations",List.of(Map.of("ownerId",value))):Map.of(field,value));
                jdbc.update("UPDATE metal_storage_document SET data=?,deleted=0 WHERE id=?",header,document);jdbc.update("UPDATE metal_storage_document_line SET data=? WHERE document_id=?",line,document);
                for(String state:List.of("DRAFT","PENDING_REVIEW","RETURNED")){jdbc.update("UPDATE metal_storage_document SET status=? WHERE id=?",state,document);session.clearCache();rejectDelete(service,OTHER);}
                for(String state:List.of("POSTED","CANCELLED","REVERSED")){jdbc.update("UPDATE metal_storage_document SET status=? WHERE id=?",state,document);session.clearCache();assertFalse(mapper.hasActiveReferences(F,OTHER));}
                jdbc.update("UPDATE metal_storage_document SET status='DRAFT',deleted=1 WHERE id=?",document);session.clearCache();assertFalse(mapper.hasActiveReferences(F,OTHER));
            }
            service.delete(new Long[]{OTHER});assertEquals("2",jdbc.queryForObject("SELECT del_flag FROM metal_customer WHERE customer_id=?",String.class,OTHER));
        }catch(Exception e){throw new IllegalStateException(e);}status.setRollbackOnly();
    });}

    @Test void customerWhitespaceOwnerInRealDraftRejectsDeletionAndApprovesWithoutCorrection(){login();tx.executeWithoutResult(status->{seedCurrentBusiness();other();
        var l=line("100.00",10);l.put("materialId",MATERIAL);l.put("ownerId"," \t"+OTHER+"\n");l.put("form","METAL");l.put("purity","99");
        var draft=orders().create(document("RAW_IN",List.of(l)));assertEquals("DRAFT",draft.get("status"));
        assertEquals(" \t"+OTHER+"\n",StorageSupport.maps(draft.get("lines")).get(0).get("ownerId"));
        try(var session=customers()){rejectDelete(new CustomerService(session.getMapper(CustomerMapper.class),scope()),OTHER);}catch(Exception e){throw new IllegalStateException(e);}
        var action=command();action.put("version",draft.get("version"));action.put("reviewRound",draft.get("reviewRound"));
        var posted=approve(orders().action(id(draft.get("id")),"submit",action));long stock=id(StorageSupport.maps(posted.get("lines")).get(0).get("stockId"));
        assertEquals(new BigDecimal("100.00"),jdbc.queryForObject("SELECT weight FROM metal_storage_ownership WHERE stock_id=? AND owner_id=?",BigDecimal.class,stock,OTHER));
        assertAllBalanced();status.setRollbackOnly();
    });}

    @Test void customerOwnershipAndActiveOrderProtectButArchivedZeroBalanceCanDelete(){login();tx.executeWithoutResult(status->{seedCurrentBusiness();other();long stock=raw(OTHER,"100.00",10);
        try(var session=customers()){
            var service=new CustomerService(session.getMapper(CustomerMapper.class),scope());rejectDelete(service,OTHER);
            jdbc.update("UPDATE metal_storage_ownership SET weight=0,quantity=0 WHERE owner_id=?",OTHER);jdbc.update("UPDATE metal_storage_batch_slice_owner SET weight=0,quantity=0 WHERE owner_id=?",OTHER);jdbc.update("UPDATE metal_storage_stock SET net_weight=0,quantity=0 WHERE id=?",stock);
            session.clearCache();service.delete(new Long[]{OTHER});rejectDelete(service,CUSTOMER);
            jdbc.update("UPDATE metal_production_order SET status='COMPLETED' WHERE id=?",ORDER);session.clearCache();service.delete(new Long[]{CUSTOMER});
            assertEquals("2",jdbc.queryForObject("SELECT del_flag FROM metal_customer WHERE customer_id=?",String.class,CUSTOMER));
        }catch(Exception e){throw new IllegalStateException(e);}status.setRollbackOnly();
    });}

    @Test void customerPendingSettlementProtectsEvenAZeroBalanceDebtUntilCancelled(){login();tx.executeWithoutResult(status->{seedCurrentBusiness();other();long stock=raw(0,"100.00",10);
        long event=jdbc.queryForObject("SELECT e.id FROM metal_storage_event e JOIN metal_storage_stock s ON s.origin_document_id=e.document_id WHERE s.id=?",Long.class,stock);
        jdbc.update("INSERT INTO metal_storage_debt(factory_id,event_id,stock_id,creditor_id,debtor_id,debt_kind,identity_key,identity_snapshot,category,measurement_mode,original_weight,original_quantity,remaining_weight,remaining_quantity) SELECT factory_id,?,id,?,0,'BORROW',identity_key,identity_snapshot,category,measurement_mode,100,10,0,0 FROM metal_storage_stock WHERE id=?",event,OTHER,stock);
        long debt=jdbc.queryForObject("SELECT id FROM metal_storage_debt WHERE creditor_id=?",Long.class,OTHER);
        jdbc.update("INSERT INTO metal_storage_document(factory_id,number,kind,type,creator_id,reviewer_id,business_date,data) VALUES(9001,'SETTLEMENT-GUARD','IN','OWNERSHIP_RETURN',1,1,CURRENT_DATE,'{}')");
        long document=jdbc.queryForObject("SELECT id FROM metal_storage_document WHERE number='SETTLEMENT-GUARD'",Long.class);
        jdbc.update("INSERT INTO metal_storage_document_line(factory_id,document_id,sequence_no,data) VALUES(9001,?,1,?)",document,JSON.toJSONString(Map.of("debtAllocations",List.of(Map.of("debtId",debt)))));
        try(var session=customers()){
            var service=new CustomerService(session.getMapper(CustomerMapper.class),scope());
            for(Object value:List.of(debt,Long.toString(debt)," \t"+debt+"\n")){
                jdbc.update("UPDATE metal_storage_document_line SET data=? WHERE document_id=?",JSON.toJSONString(Map.of("debtAllocations",List.of(Map.of("debtId",value)))),document);
                session.clearCache();rejectDelete(service,OTHER);
            }
            jdbc.update("UPDATE metal_storage_document SET status='CANCELLED' WHERE id=?",document);session.clearCache();service.delete(new Long[]{OTHER});
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM metal_storage_debt WHERE creditor_id=? AND remaining_weight=0",Integer.class,OTHER));
        }catch(Exception e){throw new IllegalStateException(e);}status.setRollbackOnly();
    });}

    @Test void customerReferencedMemberPreventsWholeBatchDeletion(){login();tx.executeWithoutResult(status->{seedCurrentBusiness();other();
        try(var session=customers()){
            var service=new CustomerService(session.getMapper(CustomerMapper.class),scope());assertThrows(ServiceException.class,()->service.delete(new Long[]{OTHER,CUSTOMER}));
            assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM metal_customer WHERE customer_id IN (?,?) AND del_flag='0'",Integer.class,OTHER,CUSTOMER));service.delete(new Long[]{OTHER});
        }catch(Exception e){throw new IllegalStateException(e);}status.setRollbackOnly();
    });}
}
