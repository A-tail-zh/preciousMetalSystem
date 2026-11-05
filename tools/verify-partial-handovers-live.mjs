import assert from 'node:assert/strict';
import fs from 'node:fs';
import {randomUUID,randomBytes} from 'node:crypto';
import {api,sql,ok,detail,login} from './local-test-client.mjs';
import {trackFixture} from './test-fixture-registry.mjs';
import {assertEnvironment,fixtureEnvironment} from './test-fixture-scope.mjs';

const environment=fixtureEnvironment();
assert.deepEqual(environment,{schema:'heavy_metal',port:'3323',api:'http://127.0.0.1:8096'});
assert.equal(process.env.TEST_REDIS_PORT,'6396');assertEnvironment(environment,sql);
const run=new URL('../.runtime/partial-handovers-20261105/',import.meta.url),date='2026-11-04';
fs.mkdirSync(run,{recursive:true});
const prefix='工单验收_'+Date.now()+'_'+randomBytes(3).toString('hex'),made={factories:[],metalTypes:[],materials:[],processes:[],products:[],lines:[],customers:[],roles:[],users:[]},extra={warehouses:[],workOrders:[],batches:[]},checks=[];
trackFixture(prefix,made);
function save(){fs.writeFileSync(new URL('live-result.json',run),JSON.stringify({prefix,environment,made,extra,checks,complete:false},null,2));}
function pass(name){checks.push(name);save();console.log('PASS '+name);}
const fail=r=>{assert.notEqual(r.code,200,JSON.stringify(r));return r;};
const admin=await login();
const create=async(path,body,group)=>{const r=ok(await api(path,admin,'POST',body)),id=String(r.id??r.factoryId??r.data?.id);assert.match(id,/^\d+$/);(made[group]??(extra[group]??=[])).push(id);save();return id;};
const factory=await create('/metal/factory',{factoryName:prefix+'连续分批厂',area:'1',address:'独立空库验收'},'factories');
const metal=await create('/metal/metal-types',{factoryId:factory,name:'铱'},'metalTypes'),processes=[];
for(const name of ['酸洗','融定','焊接']){const type=await create('/metal/process/types',{factoryId:factory,name},'processTypes');processes.push(await create('/metal/process',{factoryId:factory,name:prefix+name,processType:Number(type)},'processes'));}
const material=await create('/metal/material',{factoryId:factory,code:'OLD_'+prefix,metalTypeId:metal,materialType:'旧料'},'materials');
const cleanMaterial=await create('/metal/material',{factoryId:factory,code:'CLEAN_'+prefix,metalTypeId:metal,materialType:'块料'},'materials');
const product=await create('/metal/product',{factoryId:factory,code:'PRODUCT_'+prefix,name:prefix+'产品',metalTypeId:metal,productType:'环',calculationVersion:2,dimensions:{outerDiameter:'2',innerDiameter:'1',thickness:'1'},processes:processes.map(processId=>({processId,loss:'12',lossUnit:'PERCENT',measurementMode:'PIECE'}))},'products');
const line=await create('/metal/production-line',{factoryId:factory,code:'LINE_'+prefix,name:prefix+'产线',productIds:[product]},'lines');
const customer=await create('/metal/customer',{requestId:randomUUID(),factoryId:factory,name:prefix+'客户',companyName:prefix+'公司',address:'地址',contactName:'联系人',contactPhone:'13812345678'},'customers');
const warehouses=[];for(let i=0;i<2;i++)warehouses.push(await create('/metal/warehouse',{factoryId:factory,requestId:randomUUID(),name:prefix+'仓'+i,sortOrder:i,status:'0'},'warehouses'));
const menus=ok(await api('/metal/options/menus',admin)).data,menuIds=menus.filter(m=>String(m.perms).startsWith('metal:')).map(m=>m.id);
for(const id of [...menuIds]){let parent=menus.find(m=>m.id===id)?.parentId;while(parent&&parent!=='0'){if(!menuIds.includes(parent))menuIds.push(parent);parent=menus.find(m=>m.id===parent)?.parentId;}}
const role=await create('/system/role',{factoryId:factory,roleName:prefix+'角色',roleSort:1,status:'0',menuIds},'roles'),password='Batch'+randomBytes(10).toString('hex'),actors=[];
for(let i=0;i<3;i++){const phone='13'+(5+i)+String(Date.now()+i).slice(-8),id=await create('/system/user',{factoryId:factory,phonenumber:phone,nickName:prefix+'人员'+i,roleIds:[role],status:'0',password},'users');actors.push({id,token:await login(phone,password)});}
const [keeper,p1,p2]=actors;
const flow=id=>detail('/metal/warehouse-flows',id,admin);
async function confirmAll(root,token){for(const group of root.groups){root=ok(await api(`/metal/warehouse-flows/${root.id}/groups/${group.id}/confirm`,token,'POST',{requestId:randomUUID(),version:root.version,lines:group.lines.map(l=>({lineId:l.id,netWeight:l.processorWeight??l.plannedWeight,quantity:l.line.quantity??0}))})).data;}return root;}
async function incoming(warehouse,weight,reference=material){const r=ok(await api('/metal/warehouse-order',admin,'POST',{requestId:randomUUID(),factoryId:factory,warehouseId:warehouse,type:'RAW_IN',orderDate:date,operatorIds:[keeper.id],keeperId:keeper.id,lines:[{category:'RAW',referenceId:reference,netWeight:weight,quantity:0,customerId:customer,customerVersion:0,materialOwnership:'CUSTOMER'}]}));const root=await confirmAll(await flow(r.flowId??r.data?.flowId),keeper.token);return (await detail('/metal/warehouse-order',root.groups[0].orderId,admin)).lines[0].inventoryId;}
const stocks=[await incoming(warehouses[0],'20000'),await incoming(warehouses[1],'2000')];
const work=await create('/metal/work-order',{requestId:randomUUID(),factoryId:factory,productionLineId:line,ownerId:p1.id,customerId:customer,customerVersion:0,processingType:'MATERIAL',orderDate:date,items:[{productId:product,quantity:200,plans:processes.map((processId,i)=>({processId,startTime:date+' '+String(9+i).padStart(2,'0')+':00:00',endTime:date+' '+String(10+i).padStart(2,'0')+':00:00'}))}]},'workOrders');
const item=(await detail('/metal/work-order',work,admin)).items[0];
const plan=async(process=processes[0])=>ok(await api(`/metal/warehouse/batch-planning?factoryId=${factory}&workOrderId=${work}&itemId=${item.id}&processId=${process}`,keeper.token)).data;
const request=(qty,groups,process=processes[0])=>({requestId:randomUUID(),factoryId:factory,type:'PRODUCTION_OUT',orderDate:date,operatorIds:[keeper.id],recipientId:p1.id,keeperId:keeper.id,workOrderId:work,itemId:item.id,processId:process,batchAction:'NEW',plannedQuantity:qty,batchId:null,batchVersion:null,groups});
const group=(i,weight,who=p1,inventory=stocks[i],quantity=0)=>({warehouseId:warehouses[i],peopleOverride:{operatorIds:[keeper.id],recipientId:who.id,keeperId:keeper.id},lines:[{inventoryId:inventory,netWeight:weight,quantity}]});
const submit=async r=>ok(await api('/metal/warehouse-order/outbound-submissions',keeper.token,'POST',r)).data;
async function receive(result){let root=await flow(result.flowId);for(const g of root.groups){const actor=actors.find(a=>a.id===g.recipientId);root=await confirmGroup(root,g,actor.token);}return root;}
async function confirmGroup(root,g,token){return ok(await api(`/metal/warehouse-flows/${root.id}/groups/${g.id}/confirm`,token,'POST',{requestId:randomUUID(),version:root.version,lines:g.lines.map(l=>({lineId:l.id,netWeight:l.processorWeight??l.plannedWeight,quantity:l.line.quantity??0}))})).data;}
async function handover(batch,actor,quantity,weight,sourceWarehouse,remaining='0',returned='0'){
 const ctx=ok(await api('/metal/warehouse-flows/handover-context/'+batch,actor.token)).data;
 const source=ctx.sources.find(s=>s.recipientId===actor.id&&s.warehouseId===sourceWarehouse);
 const category=ctx.outputType==='RAW_PROCESS_IN'?'RAW':ctx.outputType==='SEMI_IN'?'SEMI':'FINISHED';
 const documents=[{type:ctx.outputType,warehouseId:sourceWarehouse,keeperId:keeper.id,lines:[{category,referenceId:category==='RAW'?cleanMaterial:product,netWeight:weight,quantity}]}];
 if(Number(returned)>0)documents.push({type:'RAW_RETURN',warehouseId:sourceWarehouse,keeperId:keeper.id,lines:[{category:'RAW',sourceLineId:source.lineId??source.id,netWeight:returned,quantity:0}]});
 return ok(await api('/metal/warehouse-flows/handover/'+batch,actor.token,'POST',{requestId:randomUUID(),version:ctx.version,orderDate:date,physicalChecked:true,wipBalances:ctx.wipGroups.map(g=>({groupKey:g.groupKey,remainingWeight:remaining})),residualMode:Number(returned)>0?'RETURNED':'NONE',documents})).data;
}

const first=await submit(request(null,[group(0,'1000')]));extra.batches.push(first.batchId);await receive(first);
assert.equal(Number(sql(`SELECT planned_quantity FROM metal_process_batch WHERE batch_id=${first.batchId}`)),200);
let h150=await handover(first.batchId,p1,150,'700',warehouses[0],'250');
let h50=await handover(first.batchId,p1,50,'230',warehouses[0],'0');
assert.notEqual(h150.id,h50.id);assert.equal(h150.stage,'KEEPER');assert.equal(h50.stage,'KEEPER');
assert.equal(sql(`SELECT SUM(remaining_weight) FROM metal_batch_wip_balance WHERE batch_id=${first.batchId}`),'0.000000');
assert.equal(sql(`SELECT SUM(l.net_weight) FROM metal_warehouse_order o JOIN metal_warehouse_order_line l ON l.order_id=o.order_id WHERE o.batch_id=${first.batchId} AND o.type='PRODUCTION_OUT' AND o.status='POSTED'`),'1000.000000');
pass('1000g issued once; 150 and 50 independently pending while retained WIP is consumed once');
h50=await confirmAll(h50,keeper.token);
assert.equal((await detail('/metal/process-batch',first.batchId,admin)).status,'OPEN');
const partialProgress=ok(await api('/metal/work-order/'+work+'/batch-progress',keeper.token)).data[0];
assert.equal(Number(partialProgress.completedQuantity),50);assert.equal(Number(partialProgress.pendingAcceptanceQuantity),150);assert.equal(partialProgress.handovers.length,2);
const stockFrom=async root=>(await detail('/metal/warehouse-order',root.groups.find(g=>['RAW_PROCESS_IN','SEMI_IN','FINISHED_IN'].includes(g.type)).orderId,admin)).lines[0].inventoryId;
const stock50=await stockFrom(h50);
const next50=await submit(request(null,[group(0,'230',p1,stock50,50)],processes[1]));extra.batches.push(next50.batchId);await receive(next50);
assert.equal(Number(sql(`SELECT planned_quantity FROM metal_process_batch WHERE batch_id=${next50.batchId}`)),50);
pass('50 accepted before 150, can immediately transfer downstream while parent batch stays OPEN');
h150=await confirmAll(await flow(h150.id),keeper.token);
assert.equal((await detail('/metal/process-batch',first.batchId,admin)).status,'SETTLED');
const stock150=await stockFrom(h150);assert.notEqual(stock150,stock50);
assert.equal(sql(`SELECT SUM(issued_weight),SUM(loss_weight),SUM(completed_quantity) FROM metal_work_report WHERE batch_id=${first.batchId} AND voided=FALSE`),'1000.000000\t70.000000\t200');
const segmentReports=ok(await api(`/metal/work-report/step?itemId=${item.id}&processId=${processes[0]}`,admin)).data;
assert.deepEqual(segmentReports.map(r=>Number(r.outputWeight)).sort((a,b)=>a-b),[230,700]);
for(const report of segmentReports){const detailReport=await detail('/metal/work-report',report.id,admin);assert.equal(detailReport.orders.length,1);assert.equal(Number(detailReport.outputWeight),Number(report.outputWeight));}
const next150=await submit(request(null,[group(0,'700',p1,stock150,150)],processes[1]));extra.batches.push(next150.batchId);await receive(next150);
assert.equal(Number(sql(`SELECT planned_quantity FROM metal_process_batch WHERE batch_id=${next150.batchId}`)),150);
pass('two reports allocate 750g and 250g, loss 50g + 20g; separate downstream batches auto-plan 50 and 150');
for(const [batch,qty,weight] of [[next50.batchId,50,'220'],[next150.batchId,150,'680']])await confirmAll(await handover(batch,p1,qty,weight,warehouses[0]),keeper.token);
const out150=await stockFrom((await api('/metal/warehouse-flows/handover-context/'+next150.batchId,p1.token)).data.handovers?await flow(sql(`SELECT flow_id FROM metal_warehouse_flow WHERE batch_id=${next150.batchId} AND kind='HANDOVER' AND status='POSTED'`)):null);
const out50=await stockFrom(await flow(sql(`SELECT flow_id FROM metal_warehouse_flow WHERE batch_id=${next50.batchId} AND kind='HANDOVER' AND status='POSTED'`)));
const merged=await submit(request(null,[{warehouseId:warehouses[0],lines:[{inventoryId:out150,netWeight:'680',quantity:150},{inventoryId:out50,netWeight:'220',quantity:50}]}],processes[2]));extra.batches.push(merged.batchId);await receive(merged);
assert.equal(Number(sql(`SELECT planned_quantity FROM metal_process_batch WHERE batch_id=${merged.batchId}`)),200);
assert.equal(sql(`SELECT COUNT(DISTINCT l.inventory_id) FROM metal_warehouse_order o JOIN metal_warehouse_order_line l ON l.order_id=o.order_id WHERE o.batch_id=${merged.batchId} AND o.type='PRODUCTION_OUT' AND o.status='POSTED'`),'2');
const finish=await handover(merged.batchId,p1,200,'880',warehouses[0]);await confirmAll(finish,keeper.token);
assert.equal((await detail('/metal/work-order',work,admin)).status,'COMPLETED');
pass('downstream combines two accepted source inventories into one 200-piece batch and finishes the order');

async function fresh(weight='1000',groups){
 const id=await create('/metal/work-order',{requestId:randomUUID(),factoryId:factory,productionLineId:line,ownerId:p1.id,customerId:customer,customerVersion:0,processingType:'MATERIAL',orderDate:date,items:[{productId:product,quantity:200,plans:processes.map((processId,i)=>({processId,startTime:date+' '+String(9+i).padStart(2,'0')+':00:00',endTime:date+' '+String(10+i).padStart(2,'0')+':00:00'}))}]},'workOrders');
 const detailItem=(await detail('/metal/work-order',id,admin)).items[0];
 const req={...request(null,groups??[group(0,weight)]),workOrderId:id,itemId:detailItem.id};
 const issued=await submit(req);extra.batches.push(issued.batchId);await receive(issued);
 return {work:id,item:detailItem,batch:issued.batchId,request:req};
}
const change=(root,reason)=>({requestId:randomUUID(),version:root.version,reason});
const guarded=await fresh();
let early=await handover(guarded.batch,p1,150,'700',warehouses[0],'250');
let later=await handover(guarded.batch,p1,50,'230',warehouses[0]);
early=ok(await api('/metal/warehouse-flows/'+early.id+'/reject',keeper.token,'POST',change(early,'先退回早期分段核对'))).data;
assert.equal(sql(`SELECT SUM(remaining_weight) FROM metal_batch_wip_balance WHERE batch_id=${guarded.batch}`),'0.000000');
fail(await api('/metal/warehouse-flows/'+early.id+'/cancel',p1.token,'POST',change(early,'存在后续分段须阻止')));
ok(await api('/metal/warehouse-flows/'+later.id+'/cancel',p1.token,'POST',change(later,'先撤回末段')));
ok(await api('/metal/warehouse-flows/'+early.id+'/cancel',p1.token,'POST',change(early,'再撤回首段')));
assert.equal(sql(`SELECT SUM(remaining_weight) FROM metal_batch_wip_balance WHERE batch_id=${guarded.batch}`),'1000.000000');
pass('reject retains allocation, earlier cancellation is blocked until later segments are cancelled in reverse order');

early=await handover(guarded.batch,p1,150,'700',warehouses[0],'250');
const beforeRace=ok(await api('/metal/warehouse-flows/handover-context/'+guarded.batch,p1.token)).data;
fail(await handoverResponse(guarded.batch,p1,51,'230','0'));
const raceBody={version:beforeRace.version,orderDate:date,physicalChecked:true,residualMode:'NONE',documents:[{type:beforeRace.outputType,warehouseId:warehouses[0],keeperId:keeper.id,lines:[{category:'RAW',referenceId:cleanMaterial,netWeight:'230',quantity:50}]}],wipBalances:beforeRace.wipGroups.map(g=>({groupKey:g.groupKey,remainingWeight:'0'}))};
const attempts=[{...raceBody,requestId:randomUUID()},{...raceBody,requestId:randomUUID()}];
const results=await Promise.all(attempts.map(body=>api('/metal/warehouse-flows/handover/'+guarded.batch,p1.token,'POST',body)));
assert.equal(results.filter(r=>r.code===200).length,1);assert.equal(results.filter(r=>r.code===409).length,1);
const winner=results.findIndex(r=>r.code===200),winning=results[winner].data;
assert.equal(ok(await api('/metal/warehouse-flows/handover/'+guarded.batch,p1.token,'POST',attempts[winner])).data.id,winning.id);
await confirmAll(winning,keeper.token);await confirmAll(early,keeper.token);
assert.equal(ok(await api('/metal/warehouse-flows/handover/'+guarded.batch,p1.token,'POST',attempts[winner])).data.id,winning.id);
assert.equal(sql(`SELECT COUNT(*) FROM metal_work_report WHERE batch_id=${guarded.batch} AND voided=FALSE`),'2');
fail(await api('/metal/warehouse-flows/'+early.id+'/reverse',keeper.token,'POST',change(await flow(early.id),'不得跨越后续分段冲销')));
ok(await api('/metal/warehouse-flows/'+winning.id+'/reverse',keeper.token,'POST',change(await flow(winning.id),'撤回末段报工及产出')));
assert.equal(sql(`SELECT SUM(remaining_weight) FROM metal_batch_wip_balance WHERE batch_id=${guarded.batch}`),'250.000000');
assert.equal((await detail('/metal/process-batch',guarded.batch,admin)).completedQuantity,150);
pass('concurrent 50-piece submissions have one winner; identical UUID replays before and after acceptance; reverse restores only its segment');

const upgraded=await fresh();
sql(`UPDATE metal_process_batch SET handover_policy_version=1,completion_policy='SINGLE_OUTPUT_RETURN' WHERE batch_id=${upgraded.batch} AND factory_id=${factory}`);
sql(`UPDATE metal_work_order SET handover_policy_version=1 WHERE work_order_id=${upgraded.work} AND factory_id=${factory}`);
const upgradeContext=ok(await api('/metal/warehouse-flows/handover-context/'+upgraded.batch,p1.token)).data;
assert.equal(upgradeContext.canConvert,true);
const beforeStock=sql(`SELECT net_weight FROM metal_inventory WHERE inventory_id=${stocks[0]}`);
await handover(upgraded.batch,p1,150,'700',warehouses[0],'250');
assert.equal(sql(`SELECT handover_policy_version FROM metal_process_batch WHERE batch_id=${upgraded.batch}`),'2');
assert.equal(sql(`SELECT net_weight FROM metal_inventory WHERE inventory_id=${stocks[0]}`),beforeStock);
assert.equal(sql(`SELECT COUNT(*) FROM metal_batch_wip_balance WHERE batch_id=${upgraded.batch}`),'1');
assert.equal((await submit(upgraded.request)).batchId,upgraded.batch);
pass('eligible old unfinished batch upgrades once without changing original issues or warehouse balances');

const legacy=await fresh();
sql(`UPDATE metal_process_batch SET handover_policy_version=1 WHERE batch_id=${legacy.batch} AND factory_id=${factory}`);
const legacyContext=ok(await api('/metal/warehouse-flows/handover-context/'+legacy.batch,p1.token)).data;
const legacyBody={requestId:randomUUID(),version:legacyContext.version,orderDate:date,physicalChecked:true,residualMode:'NONE',documents:[{type:legacyContext.outputType,warehouseId:warehouses[0],keeperId:keeper.id,lines:[{category:'RAW',referenceId:cleanMaterial,netWeight:'930',quantity:200}]}]};
let legacyFlow=ok(await api('/metal/warehouse-flows/handover/'+legacy.batch,p1.token,'POST',legacyBody)).data;
assert.equal(ok(await api('/metal/warehouse-flows/handover-context/'+legacy.batch,p1.token)).data.partialHandover,false);
fail(await api('/metal/warehouse-flows/handover/'+legacy.batch,p1.token,'POST',{...legacyBody,requestId:randomUUID(),wipBalances:legacyContext.wipGroups.map(g=>({groupKey:g.groupKey,remainingWeight:'0'}))}));
legacyFlow=await confirmAll(legacyFlow,keeper.token);
assert.equal(ok(await api('/metal/warehouse-flows/handover/'+legacy.batch,p1.token,'POST',legacyBody)).data.id,legacyFlow.id);
assert.equal(sql(`SELECT COUNT(*) FROM metal_work_report WHERE batch_id=${legacy.batch} AND handover_flow_id IS NULL`),'1');
pass('old pending handover stays on legacy strategy, refuses unsafe conversion and preserves successful old request replay');

const multi=await fresh('1000',[group(0,'500',p1),group(1,'500',p2)]);
fail(await handoverResponse(multi.batch,p1,150,'700','0'));
await confirmAll(await handover(multi.batch,p1,150,'450',warehouses[0]),keeper.token);
assert.equal((await detail('/metal/process-batch',multi.batch,admin)).status,'OPEN');
await confirmAll(await handover(multi.batch,p2,50,'450',warehouses[1]),keeper.token);
assert.equal((await detail('/metal/process-batch',multi.batch,admin)).status,'SETTLED');
pass('two recipients in different warehouses may hand over independently, cannot borrow each other’s WIP, and close only after both balances are handled');

const supplemented=await fresh();
let supplementedFirst=await handover(supplemented.batch,p1,150,'700',warehouses[0],'250');
const append={...supplemented.request,requestId:randomUUID(),batchAction:'APPEND',batchId:supplemented.batch,batchVersion:(await detail('/metal/process-batch',supplemented.batch,admin)).version,groups:[group(0,'10')]};
await receive(await submit(append));
assert.equal((await flow(supplementedFirst.id)).stage,'KEEPER');
const supplementedLast=await handover(supplemented.batch,p1,50,'240',warehouses[0]);
supplementedFirst=await confirmAll(await flow(supplementedFirst.id),keeper.token);await confirmAll(supplementedLast,keeper.token);
assert.equal(sql(`SELECT SUM(issued_weight),SUM(loss_weight) FROM metal_work_report WHERE batch_id=${supplemented.batch} AND voided=FALSE`),'1010.000000\t70.000000');
pass('10g extra actual material joins WIP once without invalidating already submitted segment or duplicating the original input');

const weightProduct=await create('/metal/product',{factoryId:factory,code:'WEIGHT_'+prefix,name:prefix+'重量产品',metalTypeId:metal,productType:'环',calculationVersion:2,dimensions:{outerDiameter:'2',innerDiameter:'1',thickness:'1'},processes:processes.map((processId,i)=>({processId,loss:'12',lossUnit:'PERCENT',measurementMode:i===0?'WEIGHT':'PIECE'}))},'products');
const weightLine=await create('/metal/production-line',{factoryId:factory,code:'WEIGHT_LINE_'+prefix,name:prefix+'重量产线',productIds:[weightProduct]},'lines');
const weightWork=await create('/metal/work-order',{requestId:randomUUID(),factoryId:factory,productionLineId:weightLine,ownerId:p1.id,customerId:customer,customerVersion:0,processingType:'MATERIAL',orderDate:date,items:[{productId:weightProduct,quantity:200,plans:processes.map((processId,i)=>({processId,startTime:date+' '+String(9+i).padStart(2,'0')+':00:00',endTime:date+' '+String(10+i).padStart(2,'0')+':00:00'}))}]},'workOrders');
const weightItem=(await detail('/metal/work-order',weightWork,admin)).items[0];
const weightIssued=await submit({...request(null,[group(0,'1000')]),workOrderId:weightWork,itemId:weightItem.id});extra.batches.push(weightIssued.batchId);await receive(weightIssued);
async function weighted(weight,remaining,reference){const ctx=ok(await api('/metal/warehouse-flows/handover-context/'+weightIssued.batchId,p1.token)).data;return ok(await api('/metal/warehouse-flows/handover/'+weightIssued.batchId,p1.token,'POST',{requestId:randomUUID(),version:ctx.version,orderDate:date,physicalChecked:true,residualMode:'NONE',wipBalances:ctx.wipGroups.map(g=>({groupKey:g.groupKey,remainingWeight:remaining})),documents:[{type:ctx.outputType,warehouseId:warehouses[0],keeperId:keeper.id,lines:[{category:'RAW',referenceId:cleanMaterial,netWeight:weight,quantity:0,referenceQuantity:reference}]}]})).data;}
const weight150=await weighted('700','250',150),weight50=await weighted('230','0',50);await confirmAll(weight50,keeper.token);await confirmAll(weight150,keeper.token);
assert.equal(sql(`SELECT planned_quantity,completed_quantity FROM metal_process_batch WHERE batch_id=${weightIssued.batchId}`),'0\t0');
assert.equal(sql(`SELECT SUM(completed_quantity),SUM(loss_weight) FROM metal_work_report WHERE batch_id=${weightIssued.batchId} AND voided=FALSE`),'0\t70.000000');
assert.equal((await detail('/metal/work-order',weightWork,admin)).status,'IN_PROGRESS');
pass('weight-only segments retain 150/50 reference counts without producing effective piece credits or falsely completing the order');

const differences=await fresh();
const differenceContext=ok(await api('/metal/warehouse-flows/handover-context/'+differences.batch,p1.token)).data;
const differenceSource=differenceContext.sources.find(s=>s.recipientId===p1.id);
let differenceFlow=ok(await api('/metal/warehouse-flows/handover/'+differences.batch,p1.token,'POST',{requestId:randomUUID(),version:differenceContext.version,orderDate:date,physicalChecked:true,residualMode:'RETURNED',wipBalances:differenceContext.wipGroups.map(g=>({groupKey:g.groupKey,remainingWeight:'230'})),documents:[{type:differenceContext.outputType,warehouseId:warehouses[0],keeperId:keeper.id,lines:[{category:'RAW',referenceId:cleanMaterial,netWeight:'700',quantity:150}]},{type:'RAW_RETURN',warehouseId:warehouses[0],keeperId:keeper.id,lines:[{category:'RAW',sourceLineId:differenceSource.lineId,netWeight:'20',quantity:0}]}]})).data;
for(const g of differenceFlow.groups){differenceFlow=ok(await api(`/metal/warehouse-flows/${differenceFlow.id}/groups/${g.id}/confirm`,keeper.token,'POST',{requestId:randomUUID(),version:differenceFlow.version,lines:g.lines.map(l=>({lineId:l.id,netWeight:g.type==='RAW_RETURN'?'19':'698',quantity:l.line.quantity??0,differenceReason:'隔离实物复称差异验证'}))})).data;}
await confirmAll(await handover(differences.batch,p1,50,'210',warehouses[0]),keeper.token);
assert.equal(sql(`SELECT SUM(loss_weight),SUM(handover_difference) FROM metal_work_report WHERE batch_id=${differences.batch} AND voided=FALSE`),'70.000000\t3.000000');
pass('actual returns and keeper short receipts stay separate: 70g process loss plus 3g handover difference, retained 230g never counted twice');

const multiKeeper=await fresh();
const keeperContext=ok(await api('/metal/warehouse-flows/handover-context/'+multiKeeper.batch,p1.token)).data;
let keeperFlow=ok(await api('/metal/warehouse-flows/handover/'+multiKeeper.batch,p1.token,'POST',{requestId:randomUUID(),version:keeperContext.version,orderDate:date,physicalChecked:true,residualMode:'NONE',wipBalances:keeperContext.wipGroups.map(g=>({groupKey:g.groupKey,remainingWeight:'250'})),documents:[0,1].map(i=>({type:keeperContext.outputType,warehouseId:warehouses[i],keeperId:keeper.id,lines:[{category:'RAW',referenceId:cleanMaterial,netWeight:'350',quantity:75}]}))})).data;
keeperFlow=await confirmGroup(keeperFlow,keeperFlow.groups[0],keeper.token);
assert.equal(sql(`SELECT COUNT(*) FROM metal_inventory WHERE origin_batch_id=${multiKeeper.batch}`),'0');
keeperFlow=await confirmGroup(keeperFlow,keeperFlow.groups[1],keeper.token);
assert.equal(sql(`SELECT COUNT(*) FROM metal_inventory WHERE origin_batch_id=${multiKeeper.batch}`),'2');
pass('a multi-warehouse segment posts neither output until every designated warehouse has accepted, then preserves distinct source inventories');

const partialFinish=await fresh();
await confirmAll(await handover(partialFinish.batch,p1,150,'700',warehouses[0],'250'),keeper.token);
const returnContext=ok(await api('/metal/warehouse-flows/handover-context/'+partialFinish.batch,p1.token)).data;
const returnFlow=ok(await api('/metal/warehouse-flows/handover/'+partialFinish.batch,p1.token,'POST',{requestId:randomUUID(),version:returnContext.version,orderDate:date,physicalChecked:true,residualMode:'RETURNED',wipBalances:returnContext.wipGroups.map(g=>({groupKey:g.groupKey,remainingWeight:'0'})),documents:[{type:'RAW_RETURN',warehouseId:warehouses[0],keeperId:keeper.id,lines:[{category:'RAW',sourceLineId:returnContext.sources[0].lineId,netWeight:'250',quantity:0}]}]})).data;
await confirmAll(returnFlow,keeper.token);
const partialBatch=await detail('/metal/process-batch',partialFinish.batch,admin);
assert.equal(partialBatch.status,'SETTLED');assert.equal(partialBatch.completedQuantity,150);assert.equal((await detail('/metal/work-order',partialFinish.work,admin)).status,'IN_PROGRESS');
assert.equal(sql(`SELECT SUM(completed_quantity),SUM(loss_weight) FROM metal_work_report WHERE batch_id=${partialFinish.batch} AND voided=FALSE`),'150\t50.000000');
pass('partial completion may return the remaining WIP with zero new pieces, frees only the unfinished 50 and never invents production');

const secondMaterial=await create('/metal/material',{factoryId:factory,code:'SECOND_'+prefix,metalTypeId:metal,materialType:'旧料',specification:'分段来源分摊'},'materials');
const secondStock=await incoming(warehouses[1],'1000',secondMaterial);
const mixedSources=await fresh('1000',[group(0,'500'),group(1,'500',p1,secondStock)]);
const mixedFirst=await handover(mixedSources.batch,p1,150,'700',warehouses[0]);
await receive(await submit({...mixedSources.request,requestId:randomUUID(),batchAction:'APPEND',batchId:mixedSources.batch,batchVersion:(await detail('/metal/process-batch',mixedSources.batch,admin)).version,groups:[group(1,'250',p1,secondStock)]}));
const mixedLast=await handover(mixedSources.batch,p1,50,'230',warehouses[0]);
await confirmAll(mixedFirst,keeper.token);await confirmAll(mixedLast,keeper.token);
assert.equal(sql(`SELECT COUNT(*),SUM(m.issued_weight) FROM metal_work_report r JOIN metal_report_material m ON m.report_id=r.report_id WHERE r.handover_flow_id=${mixedLast.id}`),'1\t250.000000');
pass('a later segment using only new material does not reallocate depleted earlier material or create zero-weight report sources');

const revised=await fresh();
let revisionFlow=await handover(revised.batch,p1,150,'700',warehouses[0],'250');
revisionFlow=ok(await api('/metal/warehouse-flows/'+revisionFlow.id+'/reject',keeper.token,'POST',change(revisionFlow,'重新称量本段产出'))).data;
const revisionContext=ok(await api('/metal/warehouse-flows/handover-context/'+revised.batch,p1.token)).data;
revisionFlow=ok(await api('/metal/warehouse-flows/handover/'+revised.batch,p1.token,'POST',{requestId:randomUUID(),flowId:revisionFlow.id,version:revisionFlow.version,orderDate:date,physicalChecked:true,residualMode:'NONE',documents:[{type:revisionContext.outputType,warehouseId:warehouses[0],keeperId:keeper.id,lines:[{category:'RAW',referenceId:cleanMaterial,netWeight:'710',quantity:150}]}],wipBalances:revisionContext.wipGroups.map(g=>({groupKey:g.groupKey,remainingWeight:'240'}))})).data;
assert.deepEqual(sql(`SELECT allocation_version,voided,allocated_weight FROM metal_handover_allocation WHERE flow_id=${revisionFlow.id} ORDER BY allocation_version`).split(/\r?\n/),['1\t1\t750.000000','2\t0\t760.000000']);
await confirmAll(revisionFlow,keeper.token);
assert.equal(sql(`SELECT issued_weight,returned_weight,loss_weight FROM metal_work_report WHERE handover_flow_id=${revisionFlow.id} AND voided=FALSE`),'760.000000\t710.000000\t50.000000');
assert.equal(sql(`SELECT SUM(remaining_weight) FROM metal_batch_wip_balance WHERE batch_id=${revised.batch}`),'240.000000');
pass('revising a rejected segment preserves its first allocation and creates a new allocation version without repeating the original issue');

const browserCase=await fresh();
await confirmAll(await handover(browserCase.batch,p1,150,'700',warehouses[0],'250'),keeper.token);
const otherWorkStock=await stockFrom(await flow(revisionFlow.id));
const externalResponse=await api('/metal/warehouse-order/outbound-submissions',keeper.token,'POST',{...request(null,[group(0,'710',p1,otherWorkStock,150)],processes[1]),workOrderId:browserCase.work,itemId:browserCase.item.id});
fail(externalResponse);assert.match(externalResponse.msg,/跨工单原料仍须等来源批次整体结束/);
assert.equal(Number((await detail('/metal/inventory',otherWorkStock,admin)).quantity),150);
pass('accepted output from an OPEN source advances within its work item but cannot bypass existing cross-work release rules');
const continued=await submit({...request(null,[group(0,'710',p1,otherWorkStock,150)],processes[1]),workOrderId:revised.work,itemId:revised.item.id});
await receive(continued);
const partialSemi=await confirmAll(await handover(continued.batchId,p1,100,'500',warehouses[0],'150'),keeper.token),semiStock=await stockFrom(partialSemi);
const sale=await api('/metal/warehouse-order',keeper.token,'POST',{requestId:randomUUID(),factoryId:factory,warehouseId:warehouses[0],type:'SEMI_OUT',orderDate:date,operatorIds:[keeper.id],keeperId:keeper.id,recipientId:p1.id,lines:[{inventoryId:semiStock,netWeight:'500',quantity:100}]});
if(sale.code===200){
 const pendingSale=await flow(sale.flowId??sale.data?.flowId),saleGroup=pendingSale.groups[0];
 const acceptedSale=await api(`/metal/warehouse-flows/${pendingSale.id}/groups/${saleGroup.id}/confirm`,p1.token,'POST',{requestId:randomUUID(),version:pendingSale.version,lines:saleGroup.lines.map(l=>({lineId:l.id,netWeight:l.plannedWeight,quantity:100}))});
 fail(acceptedSale);assert.match(acceptedSale.msg,/尚未结算/);
 ok(await api('/metal/warehouse-flows/'+pendingSale.id+'/cancel',keeper.token,'POST',change(await flow(pendingSale.id),'撤销未完成批次的非生产出库验证申请')));
}else assert.match(sale.msg,/尚未结算/);
assert.equal(Number((await detail('/metal/inventory',semiStock,admin)).quantity),100);
pass('acceptance of a partial source opens production transfer while ordinary semi outbound still waits for the parent batch to settle');
const partialConsumption=ok(await api(`/metal/work-report/consumption/steps?workOrderId=${browserCase.work}&itemId=${browserCase.item.id}`,admin)).data.rows[0];
assert.equal(partialConsumption.state,'PARTIAL');assert.equal(Number(partialConsumption.settledLossWeight),50);assert.equal(Number(partialConsumption.provisionalDifferenceWeight),250);assert.equal(Number(partialConsumption.settledNetInputWeight),750);assert.equal(partialConsumption.lossRatePercent,'6.67');
fs.writeFileSync(new URL('browser.private.json',run),JSON.stringify({factory,work:browserCase.work,item:browserCase.item.id,batch:browserCase.batch,warehouse:warehouses[0],keeper:keeper.id,processor:p1.id,password,actors,environment,prefix,product,cleanMaterial},null,2));

async function handoverResponse(batch,actor,quantity,weight,remaining){
 const ctx=ok(await api('/metal/warehouse-flows/handover-context/'+batch,actor.token)).data;
 return api('/metal/warehouse-flows/handover/'+batch,actor.token,'POST',{requestId:randomUUID(),version:ctx.version,orderDate:date,physicalChecked:true,residualMode:'NONE',documents:[{type:ctx.outputType,warehouseId:warehouses[0],keeperId:keeper.id,lines:[{category:'RAW',referenceId:cleanMaterial,netWeight:weight,quantity}]}],wipBalances:ctx.wipGroups.map(g=>({groupKey:g.groupKey,remainingWeight:remaining}))});
}

fs.writeFileSync(new URL('live-result.json',run),JSON.stringify({complete:true,prefix,environment,made,extra,checks,workOrderId:work},null,2));
console.log('COMPLETE '+checks.length+' partial handover business scenarios');
