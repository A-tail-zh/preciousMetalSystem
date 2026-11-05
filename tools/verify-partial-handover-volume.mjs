import assert from 'node:assert/strict';
import fs from 'node:fs';
import {randomUUID} from 'node:crypto';
import {performance} from 'node:perf_hooks';
import {api,ok,detail,sql,login} from './local-test-client.mjs';
import {assertEnvironment,fixtureEnvironment,assertFixtureOwnership} from './test-fixture-scope.mjs';

assertEnvironment(fixtureEnvironment(),sql);
assert.equal(process.env.TEST_API_BASE_URL,'http://127.0.0.1:8096');
assert.equal(process.env.TEST_MYSQL_PORT,'3323');
const run=new URL('../.runtime/partial-handovers-20261105/',import.meta.url);
const f=JSON.parse(fs.readFileSync(new URL('browser.private.json',run),'utf8'));
assertFixtureOwnership(f.prefix,{factories:[f.factory]},sql);
const admin=await login(),processor=f.actors[1].token,keeper=f.actors[0].token;
const original=await detail('/metal/work-order',f.work,admin),plans=original.items[0].plans;
assert.equal(String(original.factoryId),f.factory);
const count=1000,date='2026-11-05';
const request={requestId:randomUUID(),factoryId:f.factory,productionLineId:original.productionLineId,ownerId:f.processor,customerId:original.customerId,customerVersion:0,processingType:'MATERIAL',orderDate:date,items:[{productId:f.product,quantity:count,plans:plans.map((p,i)=>({processId:p.processId,startTime:date+' '+String(9+i).padStart(2,'0')+':00:00',endTime:date+' '+String(10+i).padStart(2,'0')+':00:00'}))}]};
let work,item,issued;
const resume=process.env.TEST_VOLUME_BATCH_ID;
if(resume){
 assert.match(resume,/^\d+$/);const batch=await detail('/metal/process-batch',resume,admin);
 assert.equal(String(batch.factoryId),f.factory);assert.equal(batch.status,'OPEN');assert.equal(Number(batch.plannedQuantity),count);assert.equal(String(batch.processId),String(plans[0].processId));
 work=String(batch.workOrderId);item=(await detail('/metal/work-order',work,admin)).items.find(i=>String(i.id)===String(batch.itemId));assert(item);issued={batchId:resume};
 assert.equal(sql(`SELECT SUM(l.net_weight) FROM metal_warehouse_order o JOIN metal_warehouse_order_line l ON l.order_id=o.order_id WHERE o.batch_id=${resume} AND o.type='PRODUCTION_OUT' AND o.status='POSTED'`),'1000.000000');
}else{
work=String(ok(await api('/metal/work-order',admin,'POST',request)).id);
item=(await detail('/metal/work-order',work,admin)).items[0];
const stock=sql(`SELECT i.inventory_id FROM metal_inventory i WHERE i.factory_id=${f.factory} AND i.warehouse_id=${f.warehouse} AND i.category='RAW' AND i.code='OLD_${f.prefix}' AND i.net_weight>=1000 AND i.del_flag='0'`);
assert.match(stock,/^\d+$/);
issued=ok(await api('/metal/warehouse-order/outbound-submissions',keeper,'POST',{requestId:randomUUID(),factoryId:f.factory,type:'PRODUCTION_OUT',orderDate:date,operatorIds:[f.keeper],recipientId:f.processor,keeperId:f.keeper,workOrderId:work,itemId:item.id,processId:plans[0].processId,batchAction:'NEW',plannedQuantity:null,groups:[{warehouseId:f.warehouse,lines:[{inventoryId:stock,netWeight:'1000',quantity:0}]}]})).data;
let issue=await detail('/metal/warehouse-flows',issued.flowId,admin);
for(const g of issue.groups)issue=ok(await api(`/metal/warehouse-flows/${issue.id}/groups/${g.id}/confirm`,processor,'POST',{requestId:randomUUID(),version:issue.version,lines:g.lines.map(l=>({lineId:l.id,netWeight:l.plannedWeight,quantity:0}))})).data;
}
const ctx=ok(await api('/metal/warehouse-flows/handover-context/'+issued.batchId,processor)).data;
assert.equal(ctx.wipGroups.length,1);
const segments=resume?sql(`SELECT f.flow_id,f.version,f.status,g.group_id,l.flow_line_id,l.processor_weight,JSON_UNQUOTE(JSON_EXTRACT(l.line_json,'$.quantity')) FROM metal_warehouse_flow f JOIN metal_warehouse_flow_group g ON g.flow_id=f.flow_id AND g.status<>'SUPERSEDED' JOIN metal_warehouse_flow_line l ON l.group_id=g.group_id WHERE f.batch_id=${issued.batchId} AND f.kind='HANDOVER' AND f.status NOT IN ('VOID','CANCELLED') ORDER BY f.segment_no`).split(/\r?\n/).filter(Boolean).map(row=>{const[id,version,status,groupId,lineId,weight,quantity]=row.split('\t');assert.equal(Number(weight),0.9);assert.equal(Number(quantity),1);return {id,version:Number(version),status,groups:[{id:groupId,lines:[{id:lineId,processorWeight:weight}]}]}}):[];
assert.equal(new Set(segments.map(f=>f.id)).size,segments.length);
const resumedPendingCount=segments.length,start=performance.now();
fs.writeFileSync(new URL('volume-progress.json',run),JSON.stringify({factoryId:f.factory,workOrderId:work,batchId:issued.batchId,count,resumedPendingCount}));
if(resume)console.log('Resuming '+resumedPendingCount+' durable segments without issuing material again');
for(let i=resumedPendingCount;i<count;i++){
 const flow=ok(await api('/metal/warehouse-flows/handover/'+issued.batchId,processor,'POST',{requestId:randomUUID(),version:Number(ctx.version)+i-resumedPendingCount,orderDate:date,physicalChecked:true,residualMode:'NONE',wipBalances:[{groupKey:ctx.wipGroups[0].groupKey,remainingWeight:String(count-i-1)}],documents:[{type:ctx.outputType,warehouseId:f.warehouse,keeperId:f.keeper,lines:[{category:'RAW',referenceId:f.cleanMaterial,netWeight:'0.90',quantity:1}]}]})).data;
 segments.push(flow);
 if((i+1)%100===0)console.log('Created '+(i+1)+' independent pending segments');
}
for(let i=0;i<count-1;i++){
 const flow=segments[i],g=flow.groups[0];
 if(flow.status==='POSTED')continue;
 ok(await api(`/metal/warehouse-flows/${flow.id}/groups/${g.id}/confirm`,keeper,'POST',{requestId:randomUUID(),version:flow.version,lines:g.lines.map(l=>({lineId:l.id,netWeight:l.processorWeight,quantity:1}))}));
 if((i+1)%100===0)console.log('Accepted '+(i+1)+' segments while final segment remains pending');
}
const elapsed=performance.now()-start;
assert.equal((await detail('/metal/process-batch',issued.batchId,admin)).status,'OPEN');
const measured=[];
for(let i=0;i<3;i++){
 let t=performance.now();
 const candidates=ok(await api(`/metal/warehouse/options/stocks?factoryId=${f.factory}&warehouseId=${f.warehouse}&workOrderId=${work}&itemId=${item.id}&processId=${plans[1].processId}`,admin)).data;
 const candidateMs=performance.now()-t;
 const ownCandidates=candidates.filter(s=>String(s.originBatchId)===String(issued.batchId));assert.equal(ownCandidates.length,count-1);assert(ownCandidates.every(s=>s.sourceReady===true));
 t=performance.now();const progress=ok(await api('/metal/work-order/'+work+'/batch-progress',admin)).data;
 const progressMs=performance.now()-t;
 assert.equal(progress[0].handovers.length,count);assert.equal(Number(progress[0].completedQuantity),count-1);assert.equal(Number(progress[0].pendingAcceptanceQuantity),1);
 measured.push({candidateMs,progressMs,candidateCount:ownCandidates.length,totalCandidateCount:candidates.length,segmentCount:count});
}
const last=segments.at(-1),g=last.groups[0];
ok(await api(`/metal/warehouse-flows/${last.id}/groups/${g.id}/confirm`,keeper,'POST',{requestId:randomUUID(),version:last.version,lines:g.lines.map(l=>({lineId:l.id,netWeight:l.processorWeight,quantity:1}))}));
assert.equal((await detail('/metal/process-batch',issued.batchId,admin)).status,'SETTLED');
assert.equal(sql(`SELECT SUM(issued_weight),SUM(loss_weight),SUM(completed_quantity) FROM metal_work_report WHERE batch_id=${issued.batchId}`),'1000.000000\t100.000000\t1000');
fs.writeFileSync(new URL('volume-result.json',run),JSON.stringify({factoryId:f.factory,workOrderId:work,batchId:issued.batchId,count,resumedPendingCount,continuationDurationMs:elapsed,measured},null,2));
console.log(JSON.stringify({count,resumedPendingCount,continuationDurationMs:Math.round(elapsed),measured}));
assert(measured.every(x=>x.candidateMs<5000&&x.progressMs<5000),'Segment queries exceeded 5 seconds');
