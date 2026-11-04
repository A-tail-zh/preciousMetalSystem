import assert from 'node:assert/strict';
import fs from 'node:fs';
import {randomUUID,randomBytes} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {api,sql,ok,detail,login} from './local-test-client.mjs';
import {trackFixture} from './test-fixture-registry.mjs';
import {assertEnvironment,fixtureEnvironment} from './test-fixture-scope.mjs';

const environment=fixtureEnvironment();
assert.deepEqual(environment,{schema:'heavy_metal_continuous_20261104',port:'3321',api:'http://127.0.0.1:8095'});
assert.equal(process.env.TEST_REDIS_PORT,'6395');assertEnvironment(environment,sql);
const run=new URL('../.runtime/continuous-batches-20261104/',import.meta.url),date='2026-11-04';
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
async function incoming(warehouse,weight){const r=ok(await api('/metal/warehouse-order',admin,'POST',{requestId:randomUUID(),factoryId:factory,warehouseId:warehouse,type:'RAW_IN',orderDate:date,operatorIds:[keeper.id],keeperId:keeper.id,lines:[{category:'RAW',referenceId:material,netWeight:weight,quantity:0,customerId:customer,customerVersion:0,materialOwnership:'CUSTOMER'}]}));const root=await confirmAll(await flow(r.flowId??r.data?.flowId),keeper.token);return (await detail('/metal/warehouse-order',root.groups[0].orderId,admin)).lines[0].inventoryId;}
const stocks=[await incoming(warehouses[0],'3000'),await incoming(warehouses[1],'2000')];
const work=await create('/metal/work-order',{requestId:randomUUID(),factoryId:factory,productionLineId:line,ownerId:p1.id,customerId:customer,customerVersion:0,processingType:'MATERIAL',orderDate:date,items:[{productId:product,quantity:200,plans:processes.map((processId,i)=>({processId,startTime:date+' '+String(9+i).padStart(2,'0')+':00:00',endTime:date+' '+String(10+i).padStart(2,'0')+':00:00'}))}]},'workOrders');
const item=(await detail('/metal/work-order',work,admin)).items[0];
const plan=async(process=processes[0])=>ok(await api(`/metal/warehouse/batch-planning?factoryId=${factory}&workOrderId=${work}&itemId=${item.id}&processId=${process}`,keeper.token)).data;
const request=(qty,groups,process=processes[0])=>({requestId:randomUUID(),factoryId:factory,type:'PRODUCTION_OUT',orderDate:date,operatorIds:[keeper.id],recipientId:p1.id,keeperId:keeper.id,workOrderId:work,itemId:item.id,processId:process,batchAction:'NEW',plannedQuantity:qty,batchId:null,batchVersion:null,groups});
const group=(i,weight,who=p1,inventory=stocks[i],quantity=0)=>({warehouseId:warehouses[i],peopleOverride:{operatorIds:[keeper.id],recipientId:who.id,keeperId:keeper.id},lines:[{inventoryId:inventory,netWeight:weight,quantity}]});
const submit=async r=>ok(await api('/metal/warehouse-order/outbound-submissions',keeper.token,'POST',r)).data;
async function receive(result){let root=await flow(result.flowId);for(const g of root.groups){const actor=actors.find(a=>a.id===g.recipientId);root=await confirmGroup(root,g,actor.token);}return root;}
async function confirmGroup(root,g,token){return ok(await api(`/metal/warehouse-flows/${root.id}/groups/${g.id}/confirm`,token,'POST',{requestId:randomUUID(),version:root.version,lines:g.lines.map(l=>({lineId:l.id,netWeight:l.processorWeight??l.plannedWeight,quantity:l.line.quantity??0}))})).data;}
async function handover(batch,actor,quantity,weight,sourceWarehouse,returned='0'){
 const ctx=ok(await api('/metal/warehouse-flows/handover-context/'+batch,actor.token)).data;
 const source=ctx.sources.find(s=>s.recipientId===actor.id&&s.warehouseId===sourceWarehouse);
 const category=ctx.outputType==='RAW_PROCESS_IN'?'RAW':ctx.outputType==='SEMI_IN'?'SEMI':'FINISHED';
 const documents=[{type:ctx.outputType,warehouseId:sourceWarehouse,keeperId:keeper.id,lines:[{category,referenceId:category==='RAW'?cleanMaterial:product,netWeight:weight,quantity}]}];
 if(Number(returned)>0)documents.push({type:'RAW_RETURN',warehouseId:sourceWarehouse,keeperId:keeper.id,lines:[{category:'RAW',sourceLineId:source.lineId??source.id,netWeight:returned,quantity:0}]});
 return ok(await api('/metal/warehouse-flows/handover/'+batch,actor.token,'POST',{requestId:randomUUID(),version:ctx.version,orderDate:date,physicalChecked:true,residualMode:Number(returned)>0?'RETURNED':'NONE',documents})).data;
}
const firstRequest=request(100,[group(0,'600',p1),group(1,'400',p2)]),first=await submit(firstRequest);extra.batches.push(first.batchId);await receive(first);
assert.equal(Number(sql(`SELECT planned_quantity FROM metal_process_batch WHERE batch_id=${first.batchId}`)),100);
fail(await api('/metal/warehouse-order/outbound-submissions',keeper.token,'POST',request(60,[group(0,'600')])));
let firstHandover=await handover(first.batchId,p1,60,'570',warehouses[0],'20');assert.equal(firstHandover.stage,'PROCESSORS');assert.equal((await plan()).canCreate,false);
fail(await api('/metal/warehouse-order/outbound-submissions',keeper.token,'POST',request(60,[group(0,'600')])));
const partialSupplement={...request(null,[group(0,'1')]),batchAction:'APPEND',batchId:first.batchId,batchVersion:(await detail('/metal/process-batch',first.batchId,admin)).version};delete partialSupplement.plannedQuantity;
const partialAdded=await submit(partialSupplement);assert.equal(partialAdded.batchId,first.batchId);await receive(partialAdded);
firstHandover=await handover(first.batchId,p2,40,'380',warehouses[1],'10');assert.equal(firstHandover.stage,'PROCESSORS');assert.equal((await plan()).canCreate,false);assert.equal(firstHandover.processors.find(p=>p.actorId===p1.id).submitted,false);
firstHandover=await handover(first.batchId,p1,60,'570',warehouses[0],'20');assert.equal(firstHandover.stage,'KEEPER');
pass('partial multi-person handover can receive same-batch supplements; actual recipient must resubmit before another batch can open');assert.equal((await plan()).canCreate,true);assert.equal((await plan()).availableQuantity,100);
pass('100-piece first batch waits for both actual recipients to submit; complete handover opens the next part before keeper acceptance');

// Use a real workbook and the same submission intent as the pending outbound draft.
const sheet=new URL('new-batch-import.xlsx',run);execFileSync('python',['-c',"import openpyxl,sys;w=openpyxl.Workbook();s=w.active;s.append(['库存ID','资料ID','原出库明细ID','分类','批次号','净重(g)','数量','毛重(g)','杂质(g)','纯度(%)','单位名称','用途','备注']);s.append([sys.argv[2],'','','RAW','','600','0','','','','','','']);w.save(sys.argv[1])",sheet.pathname.replace(/^\/([A-Za-z]:)/,'$1'),stocks[0]]);
const draft=request(60,[group(0,'1')]);draft.groups[0].lines=[];
const form=new FormData();form.append('file',new Blob([fs.readFileSync(sheet)]),'batch.xlsx');for(const [key,value]of Object.entries({factoryId:factory,warehouseId:warehouses[0],type:'PRODUCTION_OUT',workOrderId:work,itemId:item.id,processId:processes[0]}))form.append(key,String(value));form.append('submissionDraft',JSON.stringify(draft));
const imported=await fetch(environment.api+'/metal/warehouse-order/import',{method:'POST',headers:{Authorization:'Bearer '+keeper.token},body:form}).then(r=>r.json());ok(imported);assert.equal(imported.data.length,1);
pass('real XLSX import preserves NEW intent while the first batch remains unaccepted');

const secondRequest=request(60,[group(0,'600')]),second=await submit(secondRequest);extra.batches.push(second.batchId);assert.notEqual(second.batchId,first.batchId);assert.equal((await plan()).availableQuantity,40);assert.equal((await plan()).batches.length,2);
const appendDraft={...request(null,[group(0,'1')]),batchAction:'APPEND',batchId:second.batchId,batchVersion:0};delete appendDraft.plannedQuantity;appendDraft.groups[0].lines=[];
const appendForm=new FormData();appendForm.append('file',new Blob([fs.readFileSync(sheet)]),'append.xlsx');for(const [key,value]of Object.entries({factoryId:factory,warehouseId:warehouses[0],type:'PRODUCTION_OUT',workOrderId:work,itemId:item.id,processId:processes[0],batchId:second.batchId}))appendForm.append(key,String(value));appendForm.append('submissionDraft',JSON.stringify(appendDraft));ok(await fetch(environment.api+'/metal/warehouse-order/import',{method:'POST',headers:{Authorization:'Bearer '+keeper.token},body:appendForm}).then(r=>r.json()));
pass('APPEND XLSX import uses the selected second batch and version even while the first batch awaits acceptance');
const replay=await submit(secondRequest);assert.equal(replay.flowId,second.flowId);assert.equal(sql(`SELECT COUNT(*) FROM metal_process_batch WHERE item_id=${item.id} AND process_id=${processes[0]}`),'2');
const oldSingle={requestId:secondRequest.requestId,factoryId:factory,warehouseId:warehouses[0],type:'PRODUCTION_OUT',orderDate:date,operatorIds:[keeper.id],recipientId:p1.id,keeperId:keeper.id,workOrderId:work,itemId:item.id,processId:processes[0],lines:secondRequest.groups[0].lines};fail(await api('/metal/warehouse-order',keeper.token,'POST',oldSingle));
assert.equal(sql(`SELECT COUNT(*) FROM metal_process_batch WHERE item_id=${item.id} AND process_id=${processes[0]}`),'2');await receive(second);
const ambiguous=request(1,[group(0,'1')]);delete ambiguous.batchAction;delete ambiguous.plannedQuantity;fail(await api('/metal/warehouse-order/outbound-submissions',keeper.token,'POST',ambiguous));
pass('independent 100/60 plans leave 40; identical retries reuse the batch, cross-entry retries and ambiguous legacy requests cannot duplicate it');

const secondInfo=await detail('/metal/process-batch',second.batchId,admin),supplement={...request(null,[group(0,'1')]),batchAction:'APPEND',batchId:second.batchId,batchVersion:secondInfo.version};delete supplement.plannedQuantity;
fail(await api('/metal/warehouse-order/outbound-submissions',keeper.token,'POST',{...supplement,batchVersion:secondInfo.version+1}));const added=await submit(supplement);assert.equal(added.batchId,second.batchId);await receive(added);
fail(await api('/metal/warehouse-order/outbound-submissions',keeper.token,'POST',{...supplement,requestId:randomUUID(),batchId:first.batchId,batchVersion:(await detail('/metal/process-batch',first.batchId,admin)).version}));
assert.equal(sql(`SELECT COUNT(*) FROM metal_inventory WHERE origin_batch_id=${first.batchId}`),'0');
fail(await api('/metal/warehouse-order/outbound-submissions',keeper.token,'POST',request(100,[group(0,'100',p1,stocks[0])],processes[1])));
pass('supplementary material requires the explicit active batch/version; pending handover outputs have no usable stock and cannot feed smelting');

firstHandover=ok(await api('/metal/warehouse-flows/'+firstHandover.id+'/reject',keeper.token,'POST',{requestId:randomUUID(),version:firstHandover.version,reason:'独立核对第一批'})).data;
assert.equal((await plan()).availableQuantity,40);assert.equal((await detail('/metal/process-batch',second.batchId,admin)).status,'OPEN');
firstHandover=await handover(first.batchId,p1,60,'570',warehouses[0],'20');firstHandover=await confirmAll(firstHandover,keeper.token);assert.equal(firstHandover.status,'POSTED');assert.equal((await detail('/metal/process-batch',first.batchId,admin)).status,'SETTLED');
pass('returning first handover for revision preserves its allocation and leaves second batch unchanged; first acceptance closes only that batch');

const cleanStocks=sql(`SELECT inventory_id,warehouse_id,quantity,net_weight FROM metal_inventory WHERE origin_batch_id=${first.batchId} AND category='RAW' ORDER BY warehouse_id`).split(/\r?\n/).map(r=>r.split('\t'));
assert.equal(cleanStocks.reduce((s,r)=>s+Number(r[2]),0),100);
const melted=await submit(request(100,cleanStocks.map(r=>group(warehouses.indexOf(r[1]),r[3],p1,r[0],Number(r[2]))),processes[1]));await receive(melted);let meltedHandover=await handover(melted.batchId,p1,100,'900',warehouses[0]);await confirmAll(meltedHandover,keeper.token);
const semi=sql(`SELECT inventory_id,quantity,net_weight FROM metal_inventory WHERE origin_batch_id=${melted.batchId} AND category='SEMI'`).split('\t');
const finished=await submit(request(100,[group(0,semi[2],p1,semi[0],Number(semi[1]))],processes[2]));await receive(finished);await confirmAll(await handover(finished.batchId,p1,100,'850',warehouses[0]),keeper.token);
assert.equal((await detail('/metal/process-batch',second.batchId,admin)).status,'OPEN');
pass('accepted first 100 pieces run through smelting and final production while second acid-wash batch is still processing on the same work order');

let secondHandover=await handover(second.batchId,p1,50,'550',warehouses[0],'20');assert.equal((await plan()).availableQuantity,40);
const races=await Promise.all([request(40,[group(0,'400')]),request(40,[group(0,'400')])].map(r=>api('/metal/warehouse-order/outbound-submissions',keeper.token,'POST',r)));assert.equal(races.filter(r=>r.code===200).length,1);const third=races.find(r=>r.code===200).data;
assert.equal((await plan()).availableQuantity,0);await confirmAll(secondHandover,keeper.token);assert.equal((await plan()).availableQuantity,10);
let thirdFlow=await flow(third.flowId);ok(await api('/metal/warehouse-flows/'+thirdFlow.id+'/cancel',keeper.token,'POST',{requestId:randomUUID(),version:thirdFlow.version,reason:'释放本次未实领计划'}));assert.equal((await plan()).availableQuantity,50);
assert.equal(sql(`SELECT COUNT(*) FROM metal_work_report WHERE batch_id=${second.batchId} AND voided=FALSE`),'1');
pass('concurrent next-part requests serialize; 60 planned/50 accepted releases 10 only after acceptance; cancelling unreceived 40 releases it once');

// Weight and independent raw-processing routes keep net-weight accounting while allowing the next handover part.
async function weightedHandover(batch,output,referenceQuantity=null){
 const ctx=ok(await api('/metal/warehouse-flows/handover-context/'+batch,p1.token)).data;
 return ok(await api('/metal/warehouse-flows/handover/'+batch,p1.token,'POST',{requestId:randomUUID(),version:ctx.version,orderDate:date,physicalChecked:true,residualMode:'NONE',documents:[{type:ctx.outputType,warehouseId:warehouses[0],keeperId:keeper.id,lines:[{category:'RAW',referenceId:cleanMaterial,netWeight:output,quantity:0,...(referenceQuantity?{referenceQuantity}:{})}]}]})).data;
}
const weightProduct=await create('/metal/product',{factoryId:factory,code:'WEIGHT_'+prefix,name:prefix+'重量产品',metalTypeId:metal,productType:'环',calculationVersion:2,dimensions:{outerDiameter:'2',innerDiameter:'1',thickness:'1'},processes:processes.map((processId,i)=>({processId,loss:'12',lossUnit:'PERCENT',measurementMode:i===2?'PIECE':'WEIGHT'}))},'products');
const weightLine=await create('/metal/production-line',{factoryId:factory,code:'WEIGHT_LINE_'+prefix,name:prefix+'重量产线',productIds:[weightProduct]},'lines');
const weightWork=await create('/metal/work-order',{requestId:randomUUID(),factoryId:factory,productionLineId:weightLine,ownerId:p1.id,customerId:customer,customerVersion:0,processingType:'MATERIAL',orderDate:date,items:[{productId:weightProduct,quantity:10,plans:processes.map((processId,i)=>({processId,startTime:date+' '+String(9+i).padStart(2,'0')+':00:00',endTime:date+' '+String(10+i).padStart(2,'0')+':00:00'}))}]},'workOrders');
const weightItem=(await detail('/metal/work-order',weightWork,admin)).items[0];
const weightRequest=()=>({...request(null,[group(0,'20')]),workOrderId:weightWork,itemId:weightItem.id});
const weightFirst=await submit(weightRequest());await receive(weightFirst);const weightHandover=await weightedHandover(weightFirst.batchId,'19',777);const weightSecond=await submit(weightRequest());await receive(weightSecond);
assert.notEqual(weightFirst.batchId,weightSecond.batchId);assert.equal(Number(sql(`SELECT planned_quantity FROM metal_process_batch WHERE batch_id=${weightSecond.batchId}`)),0);
const weightPlan=ok(await api(`/metal/warehouse/batch-planning?factoryId=${factory}&workOrderId=${weightWork}&itemId=${weightItem.id}&processId=${processes[0]}`,keeper.token)).data;assert.equal(weightPlan.measurementMode,'WEIGHT');assert.equal(weightPlan.availableQuantity,null);await confirmAll(weightHandover,keeper.token);await confirmAll(await weightedHandover(weightSecond.batchId,'19'),keeper.token);
pass('weight-mode batches open independently after complete handover; optional 777 reference pieces never become planned or effective pieces');
const rawWork=await create('/metal/work-order',{requestId:randomUUID(),factoryId:factory,productionLineId:line,ownerId:p1.id,customerId:customer,customerVersion:0,processingType:'MATERIAL',taskType:'RAW_PROCESSING',orderDate:date,items:[{materialId:material,quantity:0,plannedInputWeight:50,plans:[{processId:processes[0],startTime:date+' 09:00:00',endTime:date+' 10:00:00',theoreticalLoss:12}]}]},'workOrders');
const rawItem=(await detail('/metal/work-order',rawWork,admin)).items[0],rawRequest=()=>({...request(null,[group(0,'20')]),workOrderId:rawWork,itemId:rawItem.id});
const rawFirst=await submit(rawRequest());await receive(rawFirst);await weightedHandover(rawFirst.batchId,'19');const rawSecond=await submit(rawRequest());await receive(rawSecond);await weightedHandover(rawSecond.batchId,'19');fail(await api('/metal/warehouse-order/outbound-submissions',keeper.token,'POST',rawRequest()));assert.equal(sql(`SELECT COUNT(*) FROM metal_process_batch WHERE item_id=${rawItem.id}`),'2');
pass('independent RAW_PROCESSING remains weight-only; two 20g parts may overlap acceptance but cannot exceed the 50g input plan');
const progress=ok(await api('/metal/work-order/'+work+'/batch-progress',keeper.token)).data;assert.equal(progress[0].availableQuantity,50);assert(progress[0].batches.some(b=>b.phase==='已冲销'));assert(progress[0].batches.some(b=>b.phase==='已验收／已结算'));
const reportRows=ok(await api('/metal/work-order/'+work+'/reports',keeper.token)).data;assert(reportRows.every(r=>r.lossRateBasis==='OUTBOUND_TOTAL'));assert.equal(reportRows.filter(r=>r.processId===processes[0]).length,2);
const legacyRequest={...oldSingle,requestId:randomUUID()};const legacy=ok(await api('/metal/warehouse-order',keeper.token,'POST',legacyRequest)),legacyAgain=ok(await api('/metal/warehouse-order',keeper.token,'POST',legacyRequest));assert.equal(legacyAgain.flowId,legacy.flowId);const legacyRoot=await flow(legacy.flowId);assert.equal(Number(sql(`SELECT planned_quantity FROM metal_process_batch WHERE batch_id=${legacyRoot.batchId}`)),50);fail(await api('/metal/warehouse-order/outbound-submissions',keeper.token,'POST',{...request(1,[group(0,'1')]),requestId:legacyRequest.requestId}));ok(await api('/metal/warehouse-flows/'+legacyRoot.id+'/cancel',keeper.token,'POST',{requestId:randomUUID(),version:legacyRoot.version,reason:'兼容入口重放验证后取消未实领批次'}));assert.equal((await plan()).availableQuantity,50);
pass('old single-warehouse HTTP entry retains allocation and replay compatibility; switching that UUID to the new entry is rejected without another batch');
const otherFactory=await create('/metal/factory',{factoryName:prefix+'另厂',area:'1',address:'跨厂验证'},'factories');fail(await api(`/metal/warehouse/batch-planning?factoryId=${otherFactory}&workOrderId=${work}&itemId=${item.id}&processId=${processes[0]}`,keeper.token));
pass('work-order batch summary and per-batch loss reports remain traceable; cross-factory planning is denied');
fs.writeFileSync(new URL('live-result.json',run),JSON.stringify({complete:true,prefix,environment,made,extra,checks,workOrderId:work},null,2));
console.log('COMPLETE '+checks.length+' real-database scenarios; dedicated fixture retained for review.');
