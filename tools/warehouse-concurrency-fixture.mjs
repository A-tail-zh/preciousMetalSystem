import fs from 'node:fs';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {warehouseFlowFixture} from './warehouse-flow-test-fixture.mjs';
import {cleanupWarehouseFixtures} from './warehouse-test-fixture.mjs';
import {cleanupWorkOrderFixture} from './work-order-test-fixture.mjs';
import {api,ok,detail,sql} from './local-test-client.mjs';
assert.equal(process.env.TEST_MYSQL_PORT,'3321');assert.equal(process.env.TEST_DB_SCHEMA,'heavy_metal_flow');assert.equal(sql('SELECT @@port'),'3321');assert.equal(process.env.TEST_API_BASE_URL,'http://127.0.0.1:8091');
const file='.runtime/warehouse-flow-20261103/concurrency-private.json';
if(process.argv[2]==='create'){
 assert(!fs.existsSync(file));const f=await warehouseFlowFixture();try{
  const stock=await f.incoming('CUSTOMER','100'),batch=await f.createBatch(f.processA,10);sql(`UPDATE metal_process_batch SET completion_policy='LEGACY' WHERE batch_id=${batch} AND factory_id=${f.factoryA}`);await f.post('PRODUCTION_OUT',[{inventoryId:stock,netWeight:'100',quantity:0}],{batchId:batch});const output=await f.post('SEMI_IN',[{category:'SEMI',referenceId:f.productA,netWeight:'90',quantity:10}],{batchId:batch});ok(await api('/metal/process-batch/'+batch+'/settle',f.admin,'POST',{version:(await detail('/metal/process-batch',batch,f.admin)).version,completedQuantity:10}));const semi=(await detail('/metal/warehouse-order',output,f.admin)).lines[0].inventoryId;await f.post('SEMI_OUT',[{inventoryId:semi,netWeight:'90',quantity:10}]);ok(await api('/metal/work-order/'+f.wo+'/partial-close',f.admin,'POST',{requestId:randomUUID(),version:(await detail('/metal/work-order',f.wo,f.admin)).version,reason:'本轮隔离服务级并发夹具',confirmed:true}));
  fs.writeFileSync(file,JSON.stringify(Object.fromEntries(['prefix','made','environment','factoryA','factoryB','admin'].map(k=>[k,f[k]]))));console.log('REGISTERED OWNED CONCURRENCY FIXTURE');
 }catch(error){await f.cleanup();throw error}
}else if(process.argv[2]==='cleanup'){
 const f=JSON.parse(fs.readFileSync(file));cleanupWarehouseFixtures(f);await cleanupWorkOrderFixture(f,f.admin);fs.unlinkSync(file);console.log('CONCURRENCY FIXTURE CLEANED (audit retained)');
}else throw Error('expected create or cleanup');
