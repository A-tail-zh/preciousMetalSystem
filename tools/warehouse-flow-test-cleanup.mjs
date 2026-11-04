/** Called only after the fixture environment and factory ownership checks succeed. */
export function warehouseFlowCleanupSql(ids,query){
 if(!/^\d+(,\d+)*$/.test(ids))throw Error('invalid workflow fixture scope');
 if(query("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='metal_warehouse_flow'")!=='1')return '';
 return `
 DELETE u FROM metal_work_order_residual_use u JOIN metal_work_order_residual r ON r.residual_id=u.residual_id JOIN metal_inventory i ON i.inventory_id=r.inventory_id WHERE i.factory_id IN (${ids});
 DELETE l FROM metal_customer_residual_closeout_line l JOIN metal_customer_residual_closeout c ON c.closeout_id=l.closeout_id WHERE c.factory_id IN (${ids});
 DELETE r FROM metal_work_order_residual r JOIN metal_inventory i ON i.inventory_id=r.inventory_id WHERE i.factory_id IN (${ids});
 DELETE FROM metal_stock_reservation WHERE factory_id IN (${ids});
 UPDATE metal_warehouse_order SET workflow_group_id=NULL,warehouse_flow_id=NULL WHERE factory_id IN (${ids});
 DELETE l FROM metal_warehouse_flow_line l JOIN metal_warehouse_flow_group g ON g.group_id=l.group_id JOIN metal_warehouse_flow f ON f.flow_id=g.flow_id WHERE f.factory_id IN (${ids});
 DELETE g FROM metal_warehouse_flow_group g JOIN metal_warehouse_flow f ON f.flow_id=g.flow_id WHERE f.factory_id IN (${ids});
 DELETE e FROM metal_warehouse_flow_event e JOIN metal_warehouse_flow f ON f.flow_id=e.flow_id WHERE f.factory_id IN (${ids});
 DELETE p FROM metal_warehouse_flow_processor p JOIN metal_warehouse_flow f ON f.flow_id=p.flow_id WHERE f.factory_id IN (${ids});
 DELETE r FROM metal_warehouse_flow_request r JOIN metal_warehouse_flow f ON f.flow_id=r.flow_id WHERE f.factory_id IN (${ids});
 DELETE FROM metal_warehouse_flow WHERE factory_id IN (${ids});
 DELETE FROM metal_customer_residual_closeout WHERE factory_id IN (${ids});
 `;
}
