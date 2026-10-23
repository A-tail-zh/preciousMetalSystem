import fs from 'node:fs';import assert from 'node:assert/strict';import {sql,api,ok,detail}from './local-test-client.mjs';import{warehouseFixture}from'./warehouse-test-fixture.mjs';
assert.equal(process.env.TEST_DB_SCHEMA,'audit_bulk_20261023');assert.equal(process.env.TEST_MYSQL_PORT,'3310');assert.equal(process.env.TEST_API_BASE_URL,'http://127.0.0.1:18080');
assert.equal(sql('SELECT DATABASE()'),'audit_bulk_20261023');
const out='.runtime/audit-repair-2026-10-23/bulk';
assert.equal(sql("SELECT COUNT(*) FROM metal_factory WHERE factory_name LIKE 'Bulk_%'"),'0','do not seed twice');
const f=await warehouseFixture();fs.writeFileSync(out+'/fixture.json',JSON.stringify({prefix:f.prefix,made:f.made}));
// Deliberate synthetic pagination fixture in a new schema. Not production completion evidence.
sql('CREATE TABLE bulk_numbers(n INT PRIMARY KEY); INSERT INTO bulk_numbers SELECT a.n+10*b.n+100*c.n+1000*d.n+1 FROM (SELECT 0 n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) a CROSS JOIN (SELECT 0 n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9)b CROSS JOIN (SELECT 0 n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9)c CROSS JOIN (SELECT 0 n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9)d');
const selects=[
"INSERT INTO metal_factory(factory_name,area,address) SELECT CONCAT('Bulk_F_',LPAD(n,5,'0')),1,'bulk independent fixture' FROM bulk_numbers",
`INSERT INTO metal_production_line(production_line_code,production_line_name,factory_id) SELECT CONCAT('BULK_L_',n),CONCAT('Bulk_L_',n),${f.factoryA} FROM bulk_numbers`,
"INSERT INTO metal_product(product_code,product_name,product_category,dimensions,unit) SELECT CONCAT('BULK_P_',n),CONCAT('Bulk_P_',n),'铱杆实心',JSON_OBJECT('diameter','1','length','1'),'件' FROM bulk_numbers",
"INSERT INTO metal_material(material_code,material_name,material_type) SELECT CONCAT('BULK_M_',n),CONCAT('Bulk_M_',n),'铱粉' FROM bulk_numbers",
"INSERT INTO metal_process(process_name,process_type) SELECT CONCAT('Bulk_Process_',n),1 FROM bulk_numbers",
`INSERT INTO metal_warehouse(factory_id,name,create_by) SELECT ${f.factoryA},CONCAT('Bulk_Warehouse_',n),'1' FROM bulk_numbers`];for(const q of selects)sql(q);
for(let i=0;i<34;i++){
 const b=f.body();b.items[0].quantity=1;const wo=await f.createOrder(b),w=await detail('/metal/work-order',wo,f.admin),item=w.items[0];
 const batch=String(ok(await api('/metal/process-batch',f.admin,'POST',{requestId:crypto.randomUUID(),workOrderId:wo,itemId:item.id,processId:f.processA,plannedQuantity:1})).id);
 const raw=await f.post('RAW_IN',[{category:'RAW',referenceId:f.mat1,netWeight:'10',quantity:0,organization:'Bulk_Org_'+i}]);const stock=(await detail('/metal/warehouse-order',raw,f.admin)).lines[0].inventoryId;
 await f.post('PRODUCTION_OUT',[{inventoryId:stock,netWeight:'5',quantity:0}],{batchId:batch});await f.post('SEMI_IN',[{category:'SEMI',referenceId:f.productA,netWeight:'1',quantity:1}],{batchId:batch,settle:true,completedQuantity:1});
}
const pages={};for(const k of ['factory','product','production-line','material','process','warehouse','work-order','warehouse-order','warning-record','inventory']){const r=ok(await api('/metal/'+k+'/list?pageSize=16'+(k==='warehouse-order'?'&filter=OUT':k==='inventory'?'&filter=RAW':''),f.admin));pages[k]=r.total;assert(r.total>=32,k+' needs two pages');}
const dashboard=ok(await api('/metal/dashboard',f.admin)).data;fs.writeFileSync(out+'/expected.json',JSON.stringify({schema:process.env.TEST_DB_SCHEMA,pages,dashboardWorkOrders:dashboard.workOrders.total,syntheticPaginationOnly:true},null,2));console.log('isolated bulk fixture seeded; six categories10000+, business32+, manifest recorded');
