import assert from 'node:assert/strict';
import {warehouseFixture} from './warehouse-test-fixture.mjs';
import {detail,api,ok,sql} from './local-test-client.mjs';
const f=await warehouseFixture();
const rejected=async(action,message)=>{const count=sql('SELECT COUNT(*) FROM metal_warehouse_order WHERE factory_id='+f.factoryA);await assert.rejects(action,message);assert.equal(sql('SELECT COUNT(*) FROM metal_warehouse_order WHERE factory_id='+f.factoryA),count,'失败不得遗留单据');};
try{
 const batch=await f.createBatch(f.processA,5);
 await rejected(()=>f.post('SEMI_IN',[{category:'SEMI',referenceId:f.productA,netWeight:'1',quantity:1}],{batchId:batch}),/尚未领料/);
 const incoming=await f.post('RAW_IN',[{category:'RAW',referenceId:f.mat1,netWeight:'20',quantity:0}]);const raw=(await detail('/metal/warehouse-order',incoming,f.admin)).lines[0].inventoryId;
 const issue=await f.post('PRODUCTION_OUT',[{inventoryId:raw,netWeight:'10',quantity:0}],{batchId:batch});const issueLine=(await detail('/metal/warehouse-order',issue,f.admin)).lines[0].id;
 await rejected(()=>f.post('SEMI_IN',[{category:'SEMI',referenceId:f.productA,netWeight:'4',quantity:2},{category:'SEMI',referenceId:f.productA,netWeight:'6.000001',quantity:3}],{batchId:batch}),/累计产出净重/);
 assert.equal(sql('SELECT COUNT(*) FROM metal_inventory WHERE factory_id='+f.factoryA+" AND category='SEMI'"),'0');
 const partial=await f.post('SEMI_IN',[{category:'SEMI',referenceId:f.productA,netWeight:'4',quantity:2}],{batchId:batch});const semi=(await detail('/metal/warehouse-order',partial,f.admin)).lines[0].inventoryId;
 await rejected(()=>f.post('SEMI_IN',[{category:'SEMI',referenceId:f.productA,netWeight:'6.000001',quantity:3}],{batchId:batch}),/累计产出净重/);
 assert.equal((await detail('/metal/inventory',semi,f.admin)).netWeight,'4.000000');
 await rejected(()=>f.post('SEMI_OUT',[{inventoryId:semi,netWeight:'4',quantity:2}]),/尚未结算/);
 assert.equal((await detail('/metal/inventory',semi,f.admin)).netWeight,'4.000000');
 await f.post('SEMI_IN',[{category:'SEMI',referenceId:f.productA,netWeight:'6',quantity:3}],{batchId:batch});
 await rejected(()=>f.post('RAW_RETURN',[{sourceLineId:issueLine,netWeight:'1',quantity:0}],{batchId:batch}),/累计产出净重/);
 ok(await api('/metal/process-batch/'+batch+'/settle',f.admin,'POST',{version:(await detail('/metal/process-batch',batch,f.admin)).version,completedQuantity:5}));
 const second=await f.createBatch(f.processB,5);await f.post('PRODUCTION_OUT',[{inventoryId:semi,netWeight:'10',quantity:5}],{batchId:second});
 await rejected(()=>f.post('FINISHED_IN',[{category:'FINISHED',referenceId:f.productA,netWeight:'11',quantity:5}],{batchId:second}),/累计产出净重/);
 const output=await f.post('FINISHED_IN',[{category:'FINISHED',referenceId:f.productA,netWeight:'9',quantity:5}],{batchId:second});const finished=(await detail('/metal/warehouse-order',output,f.admin)).lines[0].inventoryId;
 await rejected(()=>f.post('PRODUCT_OUT',[{inventoryId:finished,netWeight:'9',quantity:5}]),/尚未结算/);
 assert.equal((await detail('/metal/inventory',finished,f.admin)).netWeight,'9.000000');
 ok(await api('/metal/process-batch/'+second+'/settle',f.admin,'POST',{version:(await detail('/metal/process-batch',second,f.admin)).version,completedQuantity:5}));
 await f.post('PRODUCT_OUT',[{inventoryId:finished,netWeight:'9',quantity:5}]);assert.equal((await detail('/metal/inventory',finished,f.admin)).netWeight,'0.000000');
 console.log('PASS 未领料产出、10g领料产出11g、累计多行超重、超额回料均拒绝且回滚；未结算半成品及成品不能出库；结算后下道领料和产品出库正常。');
}finally{await f.cleanup();}
