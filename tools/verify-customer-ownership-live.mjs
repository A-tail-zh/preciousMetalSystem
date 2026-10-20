import assert from 'node:assert/strict';
import {warehouseFixture} from './warehouse-test-fixture.mjs';
import {detail} from './local-test-client.mjs';
const f=await warehouseFixture();
try{
 const raw=await f.post('RAW_IN',[{category:'RAW',referenceId:f.mat1,netWeight:'100',quantity:0,organization:'原料供应商'}]);
 const rawStock=(await detail('/metal/warehouse-order',raw,f.admin)).lines[0].inventoryId;
 const first=await f.createBatch(f.processA,5);
 await f.post('PRODUCTION_OUT',[{inventoryId:rawStock,netWeight:'10',quantity:0}],{batchId:first});
 const semi=await f.post('SEMI_IN',[{category:'SEMI',referenceId:f.productA,netWeight:'9',quantity:5,organization:'客户端错误归属',receivingOrganization:'测试指定收货企业'}],{batchId:first,settle:true,completedQuantity:5});
 const semiLine=(await detail('/metal/warehouse-order',semi,f.admin)).lines[0];
 assert.equal(semiLine.organization,f.work.customer);assert.equal(semiLine.receivingOrganization,'测试指定收货企业');
 assert.equal((await detail('/metal/inventory',semiLine.inventoryId,f.admin)).organization,f.work.customer);
 const second=await f.createBatch(f.processB,5);
 const outgoing=await f.post('PRODUCTION_OUT',[{inventoryId:semiLine.inventoryId,netWeight:'9',quantity:5,organization:'错误领料归属'}],{batchId:second});
 assert.equal((await detail('/metal/warehouse-order',outgoing,f.admin)).lines[0].organization,f.work.customer);
 const finished=await f.post('FINISHED_IN',[{category:'FINISHED',referenceId:f.productA,netWeight:'8',quantity:5,organization:'错误成品归属',receivingOrganization:'测试最终收货企业'}],{batchId:second,settle:true,completedQuantity:5});
 const finishedLine=(await detail('/metal/warehouse-order',finished,f.admin)).lines[0];
 assert.equal(finishedLine.organization,f.work.customer);assert.equal(finishedLine.receivingOrganization,'测试最终收货企业');
 assert.equal((await detail('/metal/inventory',finishedLine.inventoryId,f.admin)).organization,f.work.customer);
 console.log('PASS: 两道工序的半成品入库、继续领用、成品入库均保留工单客户，收货单位单独保存。');
}finally{await f.cleanup();}
