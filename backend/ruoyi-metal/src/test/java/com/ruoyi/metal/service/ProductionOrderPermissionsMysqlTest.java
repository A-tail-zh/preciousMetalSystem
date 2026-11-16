package com.ruoyi.metal.service;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Uses the inherited fresh-schema/3323 guard and synthetic fixtures only. */
class ProductionOrderPermissionsMysqlTest extends ProductionBatchP4MysqlTest {
    private ProductionOrderService service() throws Exception {
        SecurityUtils.getLoginUser().getUser().setNickName("Synthetic permissions test");
        var service=new ProductionOrderService(jdbc,scope());
        var storage=ProductionOrderService.class.getDeclaredField("storageOrders");storage.setAccessible(true);storage.set(service,orders());
        return service;
    }
    private void catalog(){
        jdbc.update("INSERT INTO metal_line_product(production_line_id,product_id) VALUES(9151,9501)");
        jdbc.update("INSERT INTO metal_product_process(product_id,process_id,sequence_no,theoretical_loss,loss_unit,measurement_mode,output_category,output_form) SELECT 9501,process_id,sequence_no,0,'G_PER_PIECE','PIECE',output_category,output_form FROM metal_production_order_step WHERE item_id=9801");
    }
    private ProductionOrderService.Create body(String request){
        return new ProductionOrderService.Create(F,request,CUSTOMER,"DIRECT_CUSTOM",null,9151L,"2026-11-12",1L,List.of(new ProductionOrderService.Item(PRODUCT,10,List.of(new ProductionOrderService.Step(9602L,null,null),new ProductionOrderService.Step(9603L,null,null),new ProductionOrderService.Step(9604L,null,null)))));
    }
    @Test void permissionsReflectBatchFreezeWithoutWeakeningWriteProtection() throws Exception {
        login();var service=service();tx.executeWithoutResult(status->{
            seedCurrentBusiness();var before=service.detail(ORDER,F);assertEquals(true,before.get("canEdit"));assertEquals(true,before.get("canDelete"));
            createBatch(100);var detail=service.detail(ORDER,F);assertEquals(false,detail.get("canEdit"));assertEquals(false,detail.get("canDelete"));assertTrue(detail.get("disabledReason").toString().contains("统一生产批次"));
            var listed=service.list(F,1,16).rows().stream().filter(row->row.get("id").equals(Long.toString(ORDER))).findFirst().orElseThrow();assertEquals(false,listed.get("canEdit"));assertEquals(false,listed.get("canDelete"));
            assertEquals(409,assertThrows(ServiceException.class,()->service.delete(new ProductionOrderService.Delete(F,UUID.randomUUID().toString(),List.of(new ProductionOrderService.Target(ORDER,0))))).getCode());
            assertEquals(409,assertThrows(ServiceException.class,()->service.edit(ORDER,new ProductionOrderService.Edit(0,body(UUID.randomUUID().toString())))).getCode());
            assertEquals(0,jdbc.queryForObject("SELECT deleted FROM metal_production_order WHERE id=?",Integer.class,ORDER));status.setRollbackOnly();
        });
    }
    @Test void permissionsOnDeletedCreateReplayAreDisabledWithoutCreatingAnotherOrder() throws Exception {
        login();var service=service();tx.executeWithoutResult(status->{
            seedCurrentBusiness();catalog();String request=UUID.randomUUID().toString();var created=service.create(body(request));long order=id(created.get("id"));
            assertEquals(true,created.get("canEdit"));assertEquals(true,created.get("canDelete"));
            service.delete(new ProductionOrderService.Delete(F,UUID.randomUUID().toString(),List.of(new ProductionOrderService.Target(order,0))));
            var replay=service.create(body(request));assertEquals(created.get("id"),replay.get("id"));assertEquals(true,replay.get("replayed"));assertEquals(1,((Number)replay.get("deleted")).intValue());assertEquals(false,replay.get("canEdit"));assertEquals(false,replay.get("canDelete"));assertTrue(replay.get("disabledReason").toString().contains("已删除"));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM metal_production_order WHERE request_id=?",Integer.class,request));assertEquals(404,assertThrows(ServiceException.class,()->service.detail(order,F)).getCode());status.setRollbackOnly();
        });
    }
}
