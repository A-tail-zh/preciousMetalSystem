/** Called only after the fixture environment and factory ownership checks succeed. */
export function warehouseFlowCleanupSql(ids,query){
 if(!/^\d+(,\d+)*$/.test(ids))throw Error('invalid workflow fixture scope');
 if(query("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='metal_warehouse_flow'")!=='1')return '';
 const partial=query("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='metal_batch_wip_balance'")==='1'?`DELETE a FROM metal_warehouse_flow f FORCE INDEX (idx_flow_factory_status) STRAIGHT_JOIN metal_handover_allocation a ON a.flow_id=f.flow_id WHERE f.factory_id IN (${ids}); DELETE w FROM metal_process_batch b STRAIGHT_JOIN metal_batch_wip_balance w ON w.batch_id=b.batch_id WHERE b.factory_id IN (${ids}); UPDATE metal_inventory SET origin_handover_flow_id=NULL WHERE factory_id IN (${ids}); DELETE h FROM metal_warning_record w STRAIGHT_JOIN metal_warning_history h ON h.warning_id=w.warning_id WHERE w.factory_id IN (${ids}); DELETE FROM metal_warning_record WHERE factory_id IN (${ids}); DELETE m FROM metal_work_report r STRAIGHT_JOIN metal_report_material m ON m.report_id=r.report_id WHERE r.factory_id IN (${ids}); DELETE FROM metal_work_report WHERE factory_id IN (${ids});`: '';
 return `${partial}
 DELETE u FROM metal_work_order_residual_use u JOIN metal_work_order_residual r ON r.residual_id=u.residual_id JOIN metal_inventory i ON i.inventory_id=r.inventory_id WHERE i.factory_id IN (${ids});
 DELETE l FROM metal_customer_residual_closeout_line l JOIN metal_customer_residual_closeout c ON c.closeout_id=l.closeout_id WHERE c.factory_id IN (${ids});
 DELETE r FROM metal_work_order_residual r JOIN metal_inventory i ON i.inventory_id=r.inventory_id WHERE i.factory_id IN (${ids});
 DELETE FROM metal_stock_reservation WHERE factory_id IN (${ids});
 UPDATE metal_warehouse_order SET workflow_group_id=NULL,warehouse_flow_id=NULL WHERE factory_id IN (${ids});
 DELETE l FROM metal_warehouse_flow f FORCE INDEX (idx_flow_factory_status) STRAIGHT_JOIN metal_warehouse_flow_group g ON g.flow_id=f.flow_id STRAIGHT_JOIN metal_warehouse_flow_line l ON l.group_id=g.group_id WHERE f.factory_id IN (${ids});
 DELETE g FROM metal_warehouse_flow f FORCE INDEX (idx_flow_factory_status) STRAIGHT_JOIN metal_warehouse_flow_group g ON g.flow_id=f.flow_id WHERE f.factory_id IN (${ids});
 DELETE e FROM metal_warehouse_flow f FORCE INDEX (idx_flow_factory_status) STRAIGHT_JOIN metal_warehouse_flow_event e ON e.flow_id=f.flow_id WHERE f.factory_id IN (${ids});
 DELETE p FROM metal_warehouse_flow f FORCE INDEX (idx_flow_factory_status) STRAIGHT_JOIN metal_warehouse_flow_processor p ON p.flow_id=f.flow_id WHERE f.factory_id IN (${ids});
 DELETE r FROM metal_warehouse_flow f FORCE INDEX (idx_flow_factory_status) STRAIGHT_JOIN metal_warehouse_flow_request r ON r.flow_id=f.flow_id WHERE f.factory_id IN (${ids});
 DELETE FROM metal_warehouse_flow WHERE factory_id IN (${ids});
 DELETE FROM metal_customer_residual_closeout WHERE factory_id IN (${ids});
 `;
}
