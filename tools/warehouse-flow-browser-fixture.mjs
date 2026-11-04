import fs from 'node:fs';
import assert from 'node:assert/strict';
import {warehouseFlowFixture} from './warehouse-flow-test-fixture.mjs';
import {cleanupWarehouseFixtures} from './warehouse-test-fixture.mjs';
import {cleanupWorkOrderFixture} from './work-order-test-fixture.mjs';
import {api,ok,detail,sql} from './local-test-client.mjs';
assert.equal(process.env.TEST_MYSQL_PORT,'3321');assert.equal(process.env.TEST_DB_SCHEMA,'heavy_metal_flow');assert.equal(sql('SELECT @@port'),'3321');assert.equal(process.env.TEST_API_BASE_URL,'http://127.0.0.1:8091');
const file='.runtime/warehouse-flow-20261103/browser-private.json';
if(process.argv[2]==='create'){
 const f=await warehouseFlowFixture();try{
  const customerStock=await f.incoming('CUSTOMER','90'),factoryStock=await f.incoming('FACTORY','10'),batch=await detail('/metal/process-batch',f.flowBatchId,f.admin);
  const submitted=await f.submit('PRODUCTION_OUT',[{inventoryId:customerStock,netWeight:'90',quantity:0},{inventoryId:factoryStock,netWeight:'10',quantity:0}],{workOrderId:f.flowWorkOrderId,itemId:f.flowItem.id,processId:f.processB,batchId:f.flowBatchId,batchVersion:batch.version});
  const data=Object.fromEntries(['prefix','made','environment','factoryA','factoryB','ownerA','processorId','admin','keeper','processor','mat1','mat2','warehouse','flowWorkOrderId','flowBatchId','suffix','phoneA'].map(k=>[k,f[k]]));
  data.customerStock=customerStock;data.factoryStock=factoryStock;data.flowId=String(submitted.flowId);data.outOrderId=String(submitted.id);data.outOrderNo=(await f.flow(submitted.flowId)).groups[0].orderId;
  fs.writeFileSync(file,JSON.stringify(data));console.log('REGISTERED ISOLATED BROWSER FIXTURE');
 }catch(error){await f.cleanup();throw error}
}else if(process.argv[2]==='cleanup'){
 const f=JSON.parse(fs.readFileSync(file));cleanupWarehouseFixtures(f);await cleanupWorkOrderFixture(f,f.admin);fs.unlinkSync(file);console.log('BROWSER FIXTURE CLEANED (audit retained)');
}else if(process.argv[2]==='verify'){
 const f=JSON.parse(fs.readFileSync(file));const report=JSON.parse(sql(`SELECT JSON_OBJECT('loss',loss_weight,'input',net_input_weight,'difference',handover_difference,'received',keeper_received_weight) FROM metal_work_report WHERE batch_id=${f.flowBatchId}`));assert.equal(Number(report.loss),1);assert.equal(Number(report.input),97);assert.equal(Number(report.difference),0.2);assert.equal(Number(report.received),98.8);assert.equal(sql(`SELECT COUNT(*) FROM metal_stock_ledger WHERE order_id=${f.outOrderId}`),'2');console.log('BROWSER BUSINESS RESULT VERIFIED');
}else throw Error('expected create, verify or cleanup');
