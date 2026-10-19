import assert from 'node:assert/strict';
import {warehouseFixture} from './warehouse-test-fixture.mjs';
import {api,ok,detail} from './local-test-client.mjs';
const f=await warehouseFixture();
try{
 const incoming=await f.post('RAW_IN',[{category:'RAW',referenceId:f.mat1,netWeight:'400',quantity:0,organization:'Report fixture'}]);const stock=(await detail('/metal/warehouse-order',incoming,f.admin)).lines[0].inventoryId;
 const batch=await f.createBatch(f.processA,5);const issued=await f.post('PRODUCTION_OUT',[{inventoryId:stock,netWeight:'300',quantity:0}],{batchId:batch});const source=(await detail('/metal/warehouse-order',issued,f.admin)).lines[0].id;
 await f.post('RAW_RETURN',[{sourceLineId:source,netWeight:'100',quantity:0}],{batchId:batch});await f.post('SEMI_IN',[{category:'SEMI',referenceId:f.productA,netWeight:'200',quantity:5}],{batchId:batch,settle:true,completedQuantity:5});
 const list=ok(await api('/metal/work-report/list?factoryId='+f.factoryA,f.admin)).rows;const row=list.find(r=>r.batchId===batch);assert(row);
 for(const r of [row,await detail('/metal/work-report',row.id,f.admin)]){assert.equal(r.rawReturnedWeight,'100.000000');assert.equal(r.outputWeight,'200.000000');assert.equal(r.issuedWeight,'300.000000');assert.equal(r.returnedWeight,'300.000000');assert.equal(r.lossWeight,'0.000000');}
 const info=await detail('/metal/process-batch',batch,f.admin);ok(await api('/metal/process-batch/'+batch+'/reverse',f.admin,'POST',{version:info.version,reason:'isolated report verification'}));const voided=await detail('/metal/work-report',row.id,f.admin);assert.equal(voided.rawReturnedWeight,null);assert.equal(voided.outputWeight,null);assert.equal(voided.returnedWeight,'300.000000');assert.equal(voided.lossWeight,'0.000000');
 console.log('PASS: list/detail show issued300, raw return100, output200, loss0; reversed report keeps original totals without inventing a breakdown.');
}finally{await f.cleanup();}
