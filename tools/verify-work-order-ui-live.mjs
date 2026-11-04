import {execFileSync} from 'node:child_process';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {warehouseFixture} from './warehouse-test-fixture.mjs';
import {api,ok,detail,sql} from './local-test-client.mjs';
assert.equal(sql('SELECT @@port'),'3317');
const f=await warehouseFixture({completionPolicy:'SINGLE_OUTPUT_RETURN'});
try{
 const product=await detail('/metal/product',f.productA,f.admin);product.processes[0].loss='5';ok(await api('/metal/product',f.admin,'PUT',product));
 const done=await detail('/metal/work-order',await f.createOrder(f.body()),f.admin);
 const raw=(await detail('/metal/warehouse-order',await f.post('RAW_IN',[{category:'RAW',referenceId:f.mat1,netWeight:'120',quantity:0}]),f.admin)).lines[0].inventoryId;
 const batch=String(ok(await api('/metal/process-batch',f.admin,'POST',{requestId:randomUUID(),workOrderId:done.id,itemId:done.items[0].id,processId:f.processA,plannedQuantity:5})).id);
 const issued=await f.post('PRODUCTION_OUT',[{inventoryId:raw,netWeight:'120',quantity:0}],{batchId:batch});await f.post('SEMI_IN',[{category:'SEMI',referenceId:f.productA,netWeight:'95',quantity:5}],{batchId:batch});
 const ctx=ok(await api('/metal/process-batch/'+batch+'/completion-context',f.admin)).data;
 const line=(await detail('/metal/warehouse-order',issued,f.admin)).lines[0];
 ok(await api('/metal/process-batch/'+batch+'/confirm-return',f.admin,'POST',{requestId:randomUUID(),version:ctx.version,mode:'RETURNED',physicalChecked:true,reason:'真实页面隔离核对',documents:[{type:'RAW_RETURN',warehouseId:f.warehouse,orderDate:'2026-11-02',operatorIds:[f.ownerA],keeperId:f.ownerA,reason:'还料20g',lines:[{sourceLineId:line.id,netWeight:'20',quantity:0}]}]}));
 execFileSync('python',['tools/verify-work-order-ui.py'],{stdio:'inherit',env:{...process.env,WORK_ORDER_UI_TOKEN:f.admin,WORK_ORDER_UI_INFO:JSON.stringify({doneOrder:done.id,waitingOrder:f.wo,waitingNo:f.work.orderNo,company:f.prefix+'公司A'})}});
 const saved=await detail('/metal/work-order',f.wo,f.admin);assert.equal(saved.processingType,'DIRECT');assert.equal(saved.customerId,f.customerA);console.log('Real browser save verified against backend.');
}finally{await f.cleanup();console.log('Registered UI fixture cleaned; audit retained.');}
