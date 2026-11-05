package com.ruoyi.metal.service;

import org.springframework.jdbc.core.JdbcTemplate;
import com.ruoyi.common.exception.ServiceException;

/**
 * 检查基础资料的仓储引用，保护库存、单据和历史业务记录。
 */
public final class WarehouseReferences {
    private WarehouseReferences() {
    }

    /**
     * 检查工厂是否存在受保护的业务引用。
     */
    public static void factory(JdbcTemplate j, Long id) {
        //判断报价历史中是否有工厂
        deny(j, "SELECT quote_id FROM metal_saved_quotation WHERE factory_id=? LIMIT 1", id);
        //判断仓库中是否有工厂
        deny(j, "SELECT warehouse_id FROM metal_warehouse WHERE factory_id=? AND del_flag='0' LIMIT 1", id);
    }

    /**
     * 检查产品是否存在受保护的业务引用。
     */
    public static void product(JdbcTemplate j, Long id) {
        pendingReference(j,id,false);
        //判断库存中是否有产品
        deny(j, "SELECT inventory_id FROM metal_inventory WHERE reference_id=? AND category<>'RAW' LIMIT 1", id);
        //判断预警历史中是否有产品
        deny(j, "SELECT rule_id FROM metal_warning_rule WHERE product_id=? AND del_flag='0' LIMIT 1", id);
    }

    /**
     * 检查物料是否存在库存引用。
     */
    public static void material(JdbcTemplate j, Long id) {
        if (materialReferenced(j, id)) throw new ServiceException("物料已有库存、仓储或原料工单引用，不能删除");
    }

    public static boolean materialReferenced(JdbcTemplate j, Long id) {
        // Current reads must see an inbound committed while the material row lock was waiting.
        return !j.queryForList("SELECT inventory_id FROM metal_inventory WHERE reference_id=? AND category='RAW' LIMIT 1 FOR SHARE", id).isEmpty()
                || !j.queryForList("SELECT line_id FROM metal_warehouse_order_line WHERE reference_id=? AND category='RAW' LIMIT 1 FOR SHARE", id).isEmpty()
                || !j.queryForList("SELECT i.item_id FROM metal_work_order_item i JOIN metal_work_order w ON w.work_order_id=i.work_order_id WHERE i.material_id=? AND w.task_type='RAW_PROCESSING' AND w.del_flag='0' LIMIT 1 FOR SHARE", id).isEmpty()
                || pending(j,id,true);
    }
    private static boolean pending(JdbcTemplate j,Long id,boolean raw){
        if(id==null)return false;
        // A JSON table scan locks unrelated handovers before filtering; seek only this catalog identity.
        try{return !j.queryForList("SELECT l.flow_line_id FROM metal_warehouse_flow_line l FORCE INDEX (idx_flow_line_reference) STRAIGHT_JOIN metal_warehouse_flow_group g ON g.group_id=l.group_id STRAIGHT_JOIN metal_warehouse_flow f ON f.flow_id=g.flow_id WHERE l.reference_id_key=? AND l.category_key"+(raw?"='RAW'":" IN ('SEMI','FINISHED')")+" AND f.status IN ('WAITING','COLLECTING','RETURNED') AND g.status<>'SUPERSEDED' LIMIT 1 FOR SHARE NOWAIT",id.toString()).isEmpty();}
        catch(org.springframework.dao.DataAccessException e){if(e.getMostSpecificCause() instanceof java.sql.SQLException sql&&sql.getErrorCode()==3572)throw new ServiceException("关联仓储记录正在处理中，请稍后重试");throw e;}
    }
    private static void pendingReference(JdbcTemplate j,Long id,boolean raw){if(pending(j,id,raw))throw new ServiceException("资料存在待确认仓储或加工交接引用，请先处理对应申请");}

    /**
     * 检查工序是否存在受保护的业务引用。
     */
    public static void process(JdbcTemplate j, Long id) {
        deny(j, "SELECT batch_id FROM metal_process_batch WHERE process_id=? LIMIT 1", id);
        deny(j, "SELECT acid_wash_id FROM metal_acid_wash_batch WHERE process_id=? LIMIT 1", id);
        deny(j, "SELECT rule_id FROM metal_warning_rule WHERE process_id=? AND del_flag='0' LIMIT 1", id);
    }

    /**
     * 检查工作人员是否存在仓储单据等业务引用。
     */
    public static void person(JdbcTemplate j, Long id) {
        deny(j, "SELECT order_id FROM metal_warehouse_order WHERE JSON_CONTAINS(operator_ids,JSON_QUOTE(CAST(? AS CHAR))) LIMIT 1", id);
        if (!j.queryForList("SELECT order_id FROM metal_warehouse_order WHERE operator_id=? OR keeper_id=? OR recipient_id=? OR inspector_id=? OR reviewer_id=? LIMIT 1", id, id, id, id, id).isEmpty())
            throw new ServiceException("人员有仓储历史引用，不能删除");
        deny(j, "SELECT warning_id FROM metal_warning_record WHERE handler_id=? LIMIT 1", id);
        deny(j, "SELECT history_id FROM metal_warning_history WHERE actor_id=? LIMIT 1", id);
        deny(j, "SELECT batch_id FROM metal_process_batch WHERE request_actor=? LIMIT 1", id);
        deny(j, "SELECT order_id FROM metal_warehouse_order WHERE request_actor=? LIMIT 1", id);
    }

    private static void deny(JdbcTemplate j, String sql, Long id) {
        if (!j.queryForList(sql, id).isEmpty()) throw new ServiceException("基础资料存在仓储或预警引用，不能删除");
    }
}
