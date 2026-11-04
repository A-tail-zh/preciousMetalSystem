import assert from 'node:assert/strict';
import {readFileSync,writeFileSync,mkdirSync} from 'node:fs';
import {randomBytes,randomUUID} from 'node:crypto';
import {api,sql,ok,detail,login} from './local-test-client.mjs';
import {trackFixture} from './test-fixture-registry.mjs';
import {assertEnvironment,fixtureEnvironment} from './test-fixture-scope.mjs';

const environment=fixtureEnvironment();
assert.deepEqual(environment,{schema:'heavy_metal_factory_20261104',port:'3321',api:'http://127.0.0.1:8093'});
assert.equal(process.env.TEST_REDIS_PORT,'6391');assertEnvironment(environment,sql);
const suffix=Date.now()+'_'+randomBytes(3).toString('hex'),prefix='工单验收_'+suffix;
const directory=new URL(`../.runtime/factory-scope-20261104/live-${suffix}/`,import.meta.url);mkdirSync(directory,{recursive:true});
const made={metalTypes:[],factories:[],lines:[],products:[],processes:[],customers:[],roles:[],users:[],materials:[]};
const extra={types:[],warehouses:[],orders:[]},passed=[];
trackFixture(prefix,made);let admin;
const save=()=>writeFileSync(new URL('fixture.private.json',directory),JSON.stringify({prefix,suffix,environment,made,extra,passed},null,2));
const check=name=>{passed.push(name);save();console.log('PASS '+name)};
const create=async(path,body,group)=>{const r=ok(await api(path,admin,'POST',body)),id=String(r.id??r.factoryId??r.data?.id);assert.match(id,/^\d+$/);if(group)(made[group]??extra[group]).push(id);save();return id;};
const fail=r=>{assert.notEqual(r.code,200,JSON.stringify(r));return r};
const date='2026-11-04',times=ids=>ids.map((processId,i)=>({processId,startTime:`${date} ${String(9+i).padStart(2,'0')}:00:00`,endTime:`${date} ${String(10+i).padStart(2,'0')}:00:00`}));

try{
 admin=await login();
 const factoryA=await create('/metal/factory',{factoryName:prefix+'厂A',area:'1',address:'厂隔离验收'},'factories');
 const factoryB=await create('/metal/factory',{factoryName:prefix+'厂B',area:'1',address:'厂隔离验收'},'factories');
 const factories=[];
 for(const factoryId of [factoryA,factoryB]){
  const metal=await create('/metal/metal-types',{factoryId,name:'铱'},'metalTypes');assert.notEqual(metal,'1');
  const types=[],processes=[];
  for(const name of ['酸洗','融定','焊接']){const type=await create('/metal/process/types',{factoryId,name},'types');types.push(type);processes.push(await create('/metal/process',{factoryId,name:prefix+name,processType:Number(type)},'processes'));}
  const material=await create('/metal/material',{factoryId,code:'FACTORY_SAME_'+suffix,metalTypeId:metal,materialType:'旧料',specification:'同名目录'},'materials');
  const powder=await create('/metal/material',{factoryId,code:'POWDER_'+suffix,metalTypeId:metal,materialType:'粉料'},'materials');
  const product=await create('/metal/product',{factoryId,code:'FACTORY_SAME_'+suffix,name:prefix+'产品',metalTypeId:metal,productType:'环',calculationVersion:2,dimensions:{outerDiameter:'2',innerDiameter:'1',thickness:'1'},processes:processes.map(processId=>({processId,loss:'12',lossUnit:'PERCENT',measurementMode:'PIECE'}))},'products');
  const line=await create('/metal/production-line',{factoryId,code:'LINE_'+factoryId+'_'+suffix,name:prefix+'产线',productIds:[product]},'lines');
  const customer=await create('/metal/customer',{factoryId,name:prefix+'客户',companyName:prefix+'公司',address:'原地址',contactName:'联络人',contactPhone:'13812345678',department:null},'customers');
  const warehouse=await create('/metal/warehouse',{factoryId,requestId:randomUUID(),name:prefix+'仓库',sortOrder:0,status:'0'},'warehouses');
  const rule={metalTypeId:metal,density:'22.65',powderPrice:'2',nonPowderPrice:'3',shortagePrice:'4',lossPrice:'7',version:0};ok(await api('/metal/quote-rule',admin,'PUT',rule));
  factories.push({factoryId,metal,types,processes,material,powder,product,line,customer,warehouse});
 }
 const [a,b]=factories;
 const spare=await create('/metal/material',{factoryId:factoryA,code:'WH_M2_'+suffix,metalTypeId:a.metal,materialType:'块料'},'materials');
 check('A/B factories independently create identical metal names, process types, material/product codes');
 fail(await api('/metal/metal-types',admin,'POST',{factoryId:factoryA,name:'铱'}));
 fail(await api('/metal/material',admin,'POST',{factoryId:factoryA,code:'FACTORY_SAME_'+suffix,metalTypeId:a.metal,materialType:'粉料'}));
 fail(await api('/metal/process',admin,'POST',{factoryId:factoryA,name:'跨厂',processType:Number(b.types[0])}));
 fail(await api('/metal/product',admin,'POST',{factoryId:factoryA,code:'CROSS_'+suffix,name:'跨厂',metalTypeId:b.metal,productType:'环',dimensions:{outerDiameter:'2',innerDiameter:'1',thickness:'1'},processes:[]}));
 fail(await api('/metal/production-line',admin,'POST',{factoryId:factoryA,code:'CROSS_LINE_'+suffix,name:'跨厂',productIds:[b.product]}));
 const menus=ok(await api('/metal/options/menus',admin)).data,menuIds=menus.filter(m=>String(m.perms).startsWith('metal:')).map(m=>m.id);
 for(const id of [...menuIds]){let parent=menus.find(m=>m.id===id)?.parentId;while(parent&&parent!=='0'){if(!menuIds.includes(parent))menuIds.push(parent);parent=menus.find(m=>m.id===parent)?.parentId;}}
 const password='Factory'+randomBytes(10).toString('hex'),accounts=[];
 for(const [index,factoryId] of [factoryA,factoryB,factoryA].entries()){
  const role=await create('/system/role',{factoryId,roleName:prefix+'角色'+index,roleSort:1,status:'0',menuIds},'roles');
  const phone='13'+(5+index)+String(Date.now()+index).slice(-8);
  const id=await create('/system/user',{factoryId,phonenumber:phone,nickName:prefix+'人员'+index,roleIds:[role],status:'0',password},'users');accounts.push({id,phone,token:await login(phone,password)});
 }
 const [keeper,other,processor]=accounts;
 const rows=ok(await api('/metal/material/list?pageNum=1&pageSize=100',keeper.token)).rows;assert(rows.some(r=>r.id===a.material));assert(!rows.some(r=>r.id===b.material));
 fail(await api('/metal/material/'+b.material,keeper.token));
 fail(await api('/metal/material',keeper.token,'POST',{factoryId:factoryB,code:'FORGED_'+suffix,materialType:'粉料',metalTypeId:b.metal}));
 fail(await api('/metal/material/'+a.material+','+b.material,keeper.token,'DELETE'));assert.equal((await detail('/metal/material',a.material,admin)).id,a.material);
 const suggestions=ok(await api('/metal/filter-options/suggestions?module=material&field=code&keyword='+encodeURIComponent('FACTORY_SAME_'+suffix),keeper.token)).rows;assert.equal(suggestions.length,1);
 const legacy=sql("SELECT material_id FROM metal_material WHERE factory_id IS NULL AND del_flag='0' LIMIT 1");
 assert(legacy);const candidates=ok(await api('/metal/warehouse/options/materials?factoryId='+factoryA,admin));
 assert(!JSON.stringify(candidates).includes('"id":"'+legacy+'"'));
 check('same-factory duplicate rejected; cross-factory reads, writes, links and atomic mixed batch denied');
 const input={metalTypeId:a.metal,productType:'杆',quantity:2,dimensions:{innerDiameter:'0',outerDiameter:'2',length:'1'},processingType:'MATERIAL_CUSTOM',powderWeight:'10',nonPowderWeight:'5',lossWeight:'1'};
 const quote=ok(await api('/metal/quotation/save',admin,'POST',{requestId:randomUUID(),factoryId:factoryA,items:[input]})).data;assert.equal(quote.totalPrice,'42.00');
 const volumeB=ok(await api('/metal/volume-rule?factoryId='+factoryB,admin)).data,volumeA=ok(await api('/metal/volume-rule?factoryId='+factoryA,admin)).data;
 ok(await api('/metal/volume-rule?factoryId='+factoryA,admin,'PUT',{...volumeA,piValue:'3.20',curvedBottomFactor:'1.04'}));
 assert.deepEqual(ok(await api('/metal/volume-rule?factoryId='+factoryB,admin)).data,volumeB);
 assert.equal((await detail('/metal/quotation/orders',quote.id,admin)).totalPrice,'42.00');
 fail(await api('/metal/quotation/calculate?factoryId='+factoryA,admin,'POST',{...input,metalTypeId:b.metal}));
 for(const processingType of ['MATERIAL_CUSTOM','DIRECT_CUSTOM','SHORTAGE_CUSTOM']){const result=ok(await api('/metal/quotation/calculate?factoryId='+factoryA,admin,'POST',{...input,processingType,...(processingType==='SHORTAGE_CUSTOM'?{powderWeight:null,nonPowderWeight:null}:{})})).data;assert.equal(result.totalPrice,processingType==='SHORTAGE_CUSTOM'?'1163.68':'42.00');}
 const aRule=ok(await api('/metal/quote-rule?metalTypeId='+a.metal,admin)).data,bRule=ok(await api('/metal/quote-rule?metalTypeId='+b.metal,admin)).data;
 fail(await api('/metal/quote-rule?metalTypeId='+b.metal,keeper.token));
 fail(await api('/metal/quote-rule',keeper.token,'PUT',{...bRule,density:'23'}));
 ok(await api('/metal/quote-rule',admin,'PUT',{...aRule,density:'23',powderPrice:'20',lossPrice:'70'}));
 assert.deepEqual(ok(await api('/metal/quote-rule?metalTypeId='+b.metal,admin)).data,bRule);
 assert.equal(ok(await api('/metal/quotation/calculate?factoryId='+factoryA,admin,'POST',input)).data.totalPrice,'285.00');
 assert.equal(ok(await api('/metal/quotation/calculate?factoryId='+factoryB,admin,'POST',{...input,metalTypeId:b.metal})).data.totalPrice,'42.00');
 assert.equal((await detail('/metal/quotation/orders',quote.id,admin)).totalPrice,'42.00');
 ok(await api('/metal/quote-rule',admin,'PUT',{...aRule,version:ok(await api('/metal/quote-rule?metalTypeId='+a.metal,admin)).data.version}));
 check('metal quotation rules isolated: A density/prices change only A; B and saved quotes unchanged; A account cannot view or edit B rules');
 check('factory geometry parameters isolated; saved quote frozen; all three pricing algorithms preserved');
 const order=(type,lines,extra={})=>({requestId:randomUUID(),factoryId:factoryA,warehouseId:a.warehouse,type,orderDate:date,operatorIds:[keeper.id],keeperId:keeper.id,recipientId:processor.id,lines,...extra});
 const flow=id=>detail('/metal/warehouse-flows',id,admin);
 const measurement=(root,group,weights,reason=true)=>({requestId:randomUUID(),version:root.version,lines:group.lines.map((l,i)=>({lineId:l.id,netWeight:weights?.[i]??l.plannedWeight,quantity:l.line.quantity??0,...(reason&&weights?.[i]!=null&&Number(weights[i])!==Number(l.plannedWeight)?{differenceReason:'实测交接差异'}:{})}))});
 const confirm=async(root,token,weights)=>ok(await api(`/metal/warehouse-flows/${root.id}/groups/${root.groups[0].id}/confirm`,token,'POST',measurement(root,root.groups[0],weights))).data;
 const receipt=order('RAW_IN',[{category:'RAW',referenceId:a.material,netWeight:'120',quantity:0,materialOwnership:'CUSTOMER',customerId:a.customer,customerVersion:0,customerSnapshot:{address:'伪造地址'}}]);
 fail(await api('/metal/warehouse-order',admin,'POST',order('RAW_IN',[{category:'RAW',referenceId:legacy,netWeight:'1',quantity:0,materialOwnership:'FACTORY'}])));
 const pending=ok(await api('/metal/warehouse-order',admin,'POST',receipt));let pendingFlow=await flow(pending.flowId);
 assert.equal(pendingFlow.groups[0].lines[0].line.customerSnapshot.address,'原地址');assert.equal(pendingFlow.groups[0].lines[0].line.customerSnapshot.department,null);
 const customer=await detail('/metal/customer',a.customer,admin);ok(await api('/metal/customer',admin,'PUT',{...customer,address:'新地址'}));
 fail(await api(`/metal/warehouse-flows/${pendingFlow.id}/groups/${pendingFlow.groups[0].id}/confirm`,keeper.token,'POST',measurement(pendingFlow,pendingFlow.groups[0])));
 assert.equal((await flow(pendingFlow.id)).groups[0].lines[0].line.customerSnapshot.address,'原地址');
 pendingFlow=ok(await api(`/metal/warehouse-flows/${pendingFlow.id}/revise`,admin,'POST',{requestId:randomUUID(),version:pendingFlow.version,reason:'客户版本更新后修订',documents:[{...receipt,lines:[{...receipt.lines[0],customerVersion:1}]}]})).data;
 assert.equal(pendingFlow.groups[0].lines[0].line.customerSnapshot.address,'新地址');pendingFlow=await confirm(pendingFlow,keeper.token);
 const rawDoc=await detail('/metal/warehouse-order',pendingFlow.groups[0].orderId,admin);assert.equal(rawDoc.lines[0].customerSnapshot.address,'新地址');const rawStock=rawDoc.lines[0].inventoryId;
 ok(await api('/metal/customer',admin,'PUT',{...await detail('/metal/customer',a.customer,admin),address:'再变更地址'}));assert.equal((await detail('/metal/warehouse-order',rawDoc.id,admin)).lines[0].customerSnapshot.address,'新地址');
 check('authoritative six-field receipt snapshot; client spoof ignored; stale customer revision blocked; accepted history frozen');
 const workId=await create('/metal/work-order',{requestId:randomUUID(),factoryId:factoryA,productionLineId:a.line,ownerId:processor.id,customerId:a.customer,customerVersion:2,processingType:'MATERIAL',orderDate:date,items:[{productId:a.product,quantity:1,plans:times(a.processes)}]},'orders');
 let work=await detail('/metal/work-order',workId,admin);assert.equal(work.items[0].plans[0].outputStage,'RAW');assert.equal(work.items[0].plans[1].outputStage,'SEMI');assert.equal(work.items[0].plans[2].outputStage,'FINISHED');
 const item=work.items[0],reports=[];let source=rawStock;
 for(let step=0;step<3;step++){
  const weight=['100','70.20','60'][step],output=['70','60','55'][step];
  const submitted=ok(await api('/metal/warehouse-order',admin,'POST',order('PRODUCTION_OUT',[{inventoryId:source,netWeight:weight,quantity:step===2?1:0}],{workOrderId:workId,itemId:item.id,processId:a.processes[step]})));
  let issued=await flow(submitted.flowId);
  fail(await api(`/metal/warehouse-flows/${issued.id}/groups/${issued.groups[0].id}/confirm`,keeper.token,'POST',measurement(issued,issued.groups[0])));
  if(step===0)fail(await api(`/metal/warehouse-flows/${issued.id}/groups/${issued.groups[0].id}/confirm`,processor.token,'POST',measurement(issued,issued.groups[0],['99'],false)));
  issued=await confirm(issued,processor.token);assert.equal(issued.status,'POSTED');
  const batch=issued.batchId,context=ok(await api('/metal/warehouse-flows/handover-context/'+batch,processor.token)).data;
  const outDoc=await detail('/metal/warehouse-order',submitted.id,admin);
  const documents=[{type:context.outputType,warehouseId:a.warehouse,keeperId:keeper.id,lines:[{category:step===0?'RAW':step===1?'SEMI':'FINISHED',referenceId:step===0?a.powder:a.product,netWeight:output,quantity:step===0?0:1}]}];
  if(step===0)documents.push({type:'RAW_RETURN',warehouseId:a.warehouse,keeperId:keeper.id,lines:[{sourceLineId:outDoc.lines[0].id,category:'RAW',netWeight:'20',quantity:0}]});
  const handover={requestId:randomUUID(),version:context.version,orderDate:date,physicalChecked:true,residualMode:step===0?'RETURNED':'NONE',documents};
  let hand=ok(await api('/metal/warehouse-flows/handover/'+batch,processor.token,'POST',handover)).data;
  for(const group of hand.groups){hand=ok(await api(`/metal/warehouse-flows/${hand.id}/groups/${group.id}/confirm`,keeper.token,'POST',measurement(hand,group,step===0&&group.type==='RAW_PROCESS_IN'?['70.20']:undefined))).data;}
  assert.equal(hand.status,'POSTED');assert.equal(ok(await api('/metal/warehouse-flows/handover/'+batch,processor.token,'POST',handover)).data.id,hand.id);
  const report=(ok(await api(`/metal/work-order/${workId}/reports`,admin)).data).find(r=>r.batchId===batch);assert(report);reports.push(report);
  assert.equal(report.lossRateBasis,'OUTBOUND_TOTAL');assert.equal(Number(report.lossRateDenominatorWeight),Number(weight));
  if(step===0){assert.equal(Number(report.lossWeight),10);assert.equal(Number(report.lossRatePercent),10);assert.equal(report.status,'NORMAL');assert.equal(Number(report.handoverDifference),-.2);}
  const stock=sql(`SELECT l.inventory_id FROM metal_warehouse_order_line l JOIN metal_warehouse_order o ON o.order_id=l.order_id WHERE o.warehouse_flow_id=${hand.id} AND o.type='${context.outputType}' LIMIT 1`);source=stock;
  if(step===0){work=await detail('/metal/work-order',workId,admin);ok(await api(`/metal/work-order/${workId}/weight-stage/${item.id}/${a.processes[0]}/close`,admin,'POST',{requestId:randomUUID(),version:work.version,reason:'已完成前处理',confirmed:true}));}
 }
 assert.equal((await detail('/metal/work-order',workId,admin)).status,'COMPLETED');assert(reports.some(r=>r.status==='ABNORMAL'));
 check('single work order traces raw → semi → finished, dual weighing, outbound denominator, warnings and idempotent handover');
 const native=ok(await api('/metal/warehouse-order',admin,'POST',order('RAW_IN',[{category:'RAW',referenceId:a.powder,netWeight:'5',quantity:0,powderOrigin:'NATIVE',materialOwnership:'FACTORY'}])));let nativeFlow=await flow(native.flowId);nativeFlow=await confirm(nativeFlow,keeper.token);assert.equal((await detail('/metal/warehouse-order',native.id,admin)).lines[0].washingStatus,'EXEMPT');
 check('factory iridium identity works when metal ID is not 1');
 writeFileSync(new URL('browser.private.json',directory),JSON.stringify({environment,prefix,a,b,accounts,password,workId,rawDocId:rawDoc.id,quoteId:quote.id},null,2));
 writeFileSync(new URL('../last-live.json',directory),JSON.stringify({directory:directory.href,prefix,factoryA,factoryB,spare,complete:true},null,2));
 save();console.log(JSON.stringify({complete:true,directory:directory.href,checks:passed.length}));
}catch(error){save();writeFileSync(new URL('failure.json',directory),JSON.stringify({message:error.message,stack:error.stack,passed},null,2));throw error;}

