import assert from 'node:assert/strict';
import fs from 'node:fs';
import {randomUUID} from 'node:crypto';
import {api,ok,detail,sql} from './local-test-client.mjs';
import {warehouseFlowFixture} from './warehouse-flow-test-fixture.mjs';
assert.equal(process.env.TEST_MYSQL_PORT,'3321');assert.equal(process.env.TEST_DB_SCHEMA,'heavy_metal_flow');assert.equal(sql('SELECT @@port'),'3321');assert.equal(process.env.TEST_API_BASE_URL,'http://127.0.0.1:8091');
const checks=[],pass=name=>{checks.push(name);console.log('PASS '+name)};let f;
try{
 f=await warehouseFlowFixture();const stock=await f.incoming('CUSTOMER','300');
 for(const recovery of [false,true]){
  const body=f.body();body.items=[{productId:f.productB,quantity:10,plans:[{processId:f.processB,startTime:'2026-11-03 09:00:00',endTime:'2026-11-03 10:00:00'}]}];const work=await f.createOrder(body),item=(await detail('/metal/work-order',work,f.admin)).items[0],batch=String(ok(await api('/metal/process-batch',f.admin,'POST',{requestId:randomUUID(),workOrderId:work,itemId:item.id,processId:f.processB,plannedQuantity:10})).id);
  const issue=await f.submit('PRODUCTION_OUT',[{inventoryId:stock,netWeight:'100',quantity:0}],{workOrderId:work,itemId:item.id,processId:f.processB,batchId:batch,batchVersion:0});let root=await f.flow(issue.flowId);ok(await f.confirm(root,root.groups[0],f.processor));const source=(await detail('/metal/warehouse-order',issue.id,f.admin)).lines[0];
  const request={requestId:randomUUID(),version:(await detail('/metal/process-batch',batch,f.admin)).version,orderDate:'2026-11-03',physicalChecked:true,residualMode:'RETURNED',reason:'逐条交接溢余不改变加工损耗',documents:[{type:'FINISHED_IN',warehouseId:f.warehouse,keeperId:f.ownerA,lines:[{category:'FINISHED',referenceId:f.productB,netWeight:'96',quantity:10}]},{type:recovery?'PROCESS_RAW_RETURN':'RAW_RETURN',warehouseId:f.warehouse,keeperId:f.ownerA,lines:[recovery?{category:'RAW',referenceId:f.mat1,materialOwnership:'CUSTOMER',netWeight:'4',quantity:0}:{category:'RAW',sourceLineId:source.id,netWeight:'4',quantity:0}]}]};
  assert.notEqual((await api('/metal/warehouse-flows/handover/'+batch,f.processor,'POST',{...request,requestId:randomUUID(),documents:[{...request.documents[0],lines:[{...request.documents[0].lines[0],netWeight:'97'}]},request.documents[1]]})).code,200);pass((recovery?'回收':'原料还库')+'加工人负损耗拒绝，不允许用交接原因绕过');
  root=ok(await api('/metal/warehouse-flows/handover/'+batch,f.processor,'POST',request)).data;for(const group of root.groups)root=ok(await f.confirm(root,group,f.keeper,group.type==='FINISHED_IN'?undefined:['4.1'])).data;
  const report=JSON.parse(sql(`SELECT JSON_OBJECT('loss',loss_weight,'custody',handover_difference,'received',keeper_received_weight) FROM metal_work_report WHERE batch_id=${batch}`));assert.equal(Number(report.loss),0);assert.equal(Number(report.custody),-0.1);assert.equal(Number(report.received),100.1);pass((recovery?'加工回收':'未用原料')+'库管多收0.1g独立记录交接溢余，加工损耗仍为0');
  const balance=ok(await api('/metal/work-order/'+work+'/weight-balance',f.admin)).data;assert.equal(Number(balance.totals.RECONCILIATION_DIFFERENCE),0);pass((recovery?'回收':'原料还库')+'交接溢余在工单物理总账正确抵销');
 }
 fs.writeFileSync('.runtime/warehouse-flow-20261103/custody-live-result.json',JSON.stringify({passed:checks.length,checks},null,2));
}finally{if(f){await f.cleanup();console.log('FIXTURE CLEANED (audit retained)')}}
