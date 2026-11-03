import assert from 'node:assert/strict';
import fs from 'node:fs';
import {randomUUID} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {api,sql,ok,detail,login} from './local-test-client.mjs';
import {workOrderFixture} from './work-order-test-fixture.mjs';
import {cleanupWarehouseFixtures} from './warehouse-test-fixture.mjs';
import {assertEnvironment,assertWarehouseOwnership} from './test-fixture-scope.mjs';

if(process.env.TEST_DB_SCHEMA!=='heavy_metal_processing_v2'||process.env.TEST_MYSQL_PORT!=='3321'||process.env.TEST_API_BASE_URL!=='http://127.0.0.1:8091')throw Error('only processing V2 isolated environment permitted');
const artifact=new URL('../.runtime/measurement-policy-20261103/',import.meta.url),passed=[];
const phase=process.argv[2]||'verify';
const stored=phase==='prepare'?null:JSON.parse(fs.readFileSync(new URL('fixture-private.json',artifact)));
const f=stored?.f??await workOrderFixture({legacy:false});f.admin=await login();if(stored?.passed)passed.push(...stored.passed);if(stored){f.body=()=>({...structuredClone(stored.body),requestId:randomUUID()});f.createOrder=async body=>String(ok(await api('/metal/work-order',f.admin,'POST',body)).id);}

fs.writeFileSync(new URL('live-fixture.json',artifact),JSON.stringify({prefix:f.prefix,made:f.made,environment:f.environment},null,2));
const check=(name)=>{if(!passed.includes(name))passed.push(name);console.log('PASS '+name);fs.writeFileSync(new URL('live-result.json',artifact),JSON.stringify({prefix:f.prefix,passed,complete:false},null,2));};
const create=async(path,body,group)=>{const r=ok(await api(path,f.admin,'POST',body));const id=String(r.id);if(group)f.made[group].push(id);return id;};
try{
 assertEnvironment(f.environment,sql);assertWarehouseOwnership(f,sql);
 sql(`INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT ${f.roleA},menu_id FROM sys_menu WHERE perms IN ('metal:warehouse-order:list','metal:warehouse-order:query','metal:warehouse-order:add','metal:warehouse-order:receive','metal:warehouse-order:accept','metal:warehouse-order:handover','metal:process-batch:settle','metal:process-batch:query','metal:inventory:list','metal:inventory:query')`);
 const phone=stored?sql(`SELECT phonenumber FROM sys_user WHERE user_id=${stored.processorId} AND factory_id=${f.factoryA}`):'136'+String(Date.now()).slice(-8);
 const processorId=stored?.processorId??await create('/system/user',{phonenumber:phone,nickName:f.prefix+'加工员',factoryId:f.factoryA,roleIds:[f.roleA],status:'0',password:f.password},'users');
 const keeper=await login(f.phoneA,f.password),processor=await login(phone,f.password);
 const warehouses=stored?.warehouses??[];for(let i=warehouses.length;i<2;i++)warehouses.push(await create('/metal/warehouse',{requestId:randomUUID(),factoryId:f.factoryA,name:f.prefix+'库'+i,sortOrder:i,status:'0'}));
 const acidType=sql("SELECT type_id FROM metal_process_type WHERE type_name='酸洗'");
 const acid=stored?.acid??await create('/metal/process',{name:f.prefix+'酸洗',processType:acidType},'processes');
 const melt=stored?.melt??await create('/metal/process',{name:f.prefix+'融定',processType:4},'processes');
 const productProcesses=ok(await api('/metal/options/processes',f.admin)).data;assert(productProcesses.some(p=>p.id===acid&&p.processTypeName==='酸洗'));check('real product route candidates include acid type and type identity');
 const fragment=stored?.fragment??await create('/metal/process',{name:f.prefix+'碎料',processType:3},'processes');
 const plans=ids=>ids.map((processId,i)=>({processId,theoreticalLoss:'5.00',startTime:`2026-11-03 ${String(9+i).padStart(2,'0')}:00:00`,endTime:`2026-11-03 ${String(10+i).padStart(2,'0')}:00:00`}));
 const root=async id=>detail('/metal/warehouse-flows',id,f.admin);
 const confirm=async(flow,group,token,weights)=>api(`/metal/warehouse-flows/${flow.id}/groups/${group.id}/confirm`,token,'POST',{requestId:randomUUID(),version:flow.version,lines:group.lines.map((row,i)=>({lineId:row.id,netWeight:weights?.[i]??row.plannedWeight,quantity:row.line.quantity??0,...(weights?.[i]&&Number(weights[i])!==Number(row.plannedWeight)?{differenceReason:'本轮隔离双称差异'}:{})}))});
 const orderBody=(type,warehouseId,lines,extra={})=>({requestId:randomUUID(),factoryId:f.factoryA,warehouseId,type,orderDate:'2026-11-03',operatorIds:[f.ownerA],keeperId:f.ownerA,recipientId:processorId,lines,...extra});
 const incoming=async(material,weight,{warehouse=warehouses[0],origin,owner='CUSTOMER',batchNo=''}={})=>{
  const line={category:'RAW',referenceId:material,quantity:0,netWeight:weight,materialOwnership:owner,...(owner==='CUSTOMER'?{customerId:f.customerA,customerVersion:0}:{}),...(origin?{powderOrigin:origin}:{}),batchNo};
  const body=orderBody('RAW_IN',warehouse,[line]),submitted=ok(await api('/metal/warehouse-order',f.admin,'POST',body));let flow=await root(submitted.flowId);flow=ok(await confirm(flow,flow.groups[0],keeper)).data;assert.equal(flow.status,'POSTED');
  const doc=await detail('/metal/warehouse-order',submitted.id,f.admin);return {stock:doc.lines[0].inventoryId,line:doc.lines[0],body,submitted};
 };
 const rawOrder=async(material,weight,ids=[fragment,acid],own=false)=>{
  const body={...f.body(),taskType:'RAW_PROCESSING',processingType:own?'DIRECT':'MATERIAL',customerId:own?null:f.customerA,customerVersion:own?null:0,items:[{materialId:material,plannedInputWeight:weight,plans:plans(ids)}]};
  const id=await f.createOrder(body);return {id,body,...await detail('/metal/work-order',id,f.admin)};
 };
 const bind=async(work,stock,weight,processId,{warehouse=warehouses[0],confirmIt=true,extraLine={}}={})=>{
  const item=work.items[0];const body=orderBody('PRODUCTION_OUT',warehouse,[{inventoryId:stock,netWeight:weight,quantity:0,...extraLine}],{workOrderId:work.id,itemId:item.id,processId});
  const response=await api('/metal/warehouse-order',f.admin,'POST',body);if(response.code!==200)return {response,body};let flow=await root(response.flowId);if(confirmIt)flow=ok(await confirm(flow,flow.groups[0],processor)).data;return {response,body,flow,batch:flow.batchId};
 };
 const handover=async(batch,target,weight,{returns=[],warehouse=warehouses[0],pieces=0}={})=>{
  const context=ok(await api(`/metal/warehouse-flows/handover-context/${batch}`,processor)).data;
  const output={type:context.outputType,warehouseId:warehouse,keeperId:f.ownerA,lines:[{category:context.outputType==='RAW_PROCESS_IN'?'RAW':context.outputType==='SEMI_IN'?'SEMI':'FINISHED',referenceId:target,netWeight:weight,quantity:pieces}]};
  const body={requestId:randomUUID(),version:context.version,orderDate:'2026-11-03',physicalChecked:true,residualMode:returns.length?'RETURNED':'NONE',documents:[output,...returns]};
  let flow=ok(await api(`/metal/warehouse-flows/handover/${batch}`,processor,'POST',body)).data;
  for(const group of flow.groups)flow=ok(await confirm(flow,group,keeper)).data;
  assert.equal(flow.status,'POSTED');const docId=sql(`SELECT order_id FROM metal_warehouse_order WHERE warehouse_flow_id=${flow.id} AND type='${context.outputType}' AND status='POSTED' LIMIT 1`);
  const doc=await detail('/metal/warehouse-order',docId,f.admin);return {context,flow,stock:doc.lines[0].inventoryId,document:doc,body};
 };
 const closeStage=async(work,processId)=>{const latest=await detail('/metal/work-order',work.id,f.admin);ok(await api(`/metal/work-order/${work.id}/weight-stage/${work.items[0].id}/${processId}/close`,f.admin,'POST',{requestId:randomUUID(),version:latest.version,reason:'本轮实际承接重量已闭环',confirmed:true}));};

 if(phase==='prepare'){
  const powder=await create('/metal/material',{code:'MEASURE_RAW_'+f.suffix,materialType:'粉料',metalTypeId:'1',specification:'计量升级隔离'},'materials');
  const productId=await create('/metal/product',{code:'MEASURE_OLD_'+f.suffix,name:f.prefix+'历史重量型',metalTypeId:'1',productType:'杆',dimensions:{innerDiameter:'0',outerDiameter:'1',length:'2'},processes:[{processId:melt,loss:'5.00',lossUnit:'PERCENT',measurementMode:'WEIGHT'},{processId:f.processB,loss:'5.00',lossUnit:'PERCENT',measurementMode:'PIECE'}]},'products');
  const productionLineId=await create('/metal/production-line',{code:'MEASURE_LINE_'+f.suffix,name:f.prefix+'计量线',factoryId:f.factoryA,productIds:[productId]},'lines');
  const body={...f.body(),productionLineId,items:[{productId,quantity:5,plans:plans([melt,f.processB])}]};
  const oldId=String(ok(await api('/metal/work-order',f.admin,'POST',body)).id),old=await detail('/metal/work-order',oldId,f.admin);
  assert.equal(old.items[0].plans[0].measurementMode,'WEIGHT');assert.equal(old.items[0].snapshot.measurementPolicyVersion,undefined);
  const raw=await incoming(powder,'1000.00',{origin:'NATIVE'}),issued=await bind(old,raw.stock,'100.00',melt);ok(issued.response);
  const output=await handover(issued.batch,productId,'95.00');assert.equal((await detail('/metal/inventory',output.stock,f.admin)).measurementMode,'WEIGHT');
  const rawTask=await rawOrder(powder,'100.00',[acid]);
  const state={f,processorId,warehouses,acid,melt,fragment,powder,productId,productionLineId,body,old,raw,oldOutput:output,rawTask};
  fs.writeFileSync(new URL('fixture-private.json',artifact),JSON.stringify(state,null,2));
  check('real pre-upgrade V2 WEIGHT order, settled inventory and raw-task snapshots created');

 }else if(phase==='verify'){
  const savedSteps=w=>w.items[0].plans.map(p=>Object.fromEntries(['processId','processName','measurementMode','loss','lossUnit','processRole','outputStage'].map(k=>[k,p[k]])));
  const newWork=async(quantity=5,ids=[melt,f.processB])=>{
   const body={...f.body(),productionLineId:stored.productionLineId,items:[{productId:stored.productId,quantity,plans:plans(ids)}]};
   const id=await f.createOrder(body);return {id,...await detail('/metal/work-order',id,f.admin)};
  };
  const beforePost=async()=>{
  const old=await detail('/metal/work-order',stored.old.id,f.admin);
  assert.deepEqual(old.items[0].snapshot,stored.old.items[0].snapshot);assert.deepEqual(savedSteps(old),savedSteps(stored.old));
  assert.equal(String(ok(await api('/metal/work-order',f.admin,'POST',stored.body)).id),old.id);
  check('old V2 WEIGHT snapshot and lost-response order replay are unchanged after upgrade');
  let raw=await detail('/metal/work-order',stored.rawTask.id,f.admin);
  const edit={...structuredClone(stored.rawTask.body),id:raw.id,version:raw.version,items:[{...stored.rawTask.body.items[0],id:raw.items[0].id}]};
  ok(await api('/metal/work-order',f.admin,'PUT',edit));raw=await detail('/metal/work-order',raw.id,f.admin);
  assert.deepEqual(raw.items[0].snapshot,stored.rawTask.items[0].snapshot);assert.deepEqual(savedSteps(raw),savedSteps(stored.rawTask));
  const changed=await api('/metal/work-order',f.admin,'PUT',{...edit,version:raw.version,items:[{...edit.items[0],id:raw.items[0].id,plans:edit.items[0].plans.map(p=>({...p,theoreticalLoss:'6.00'}))}]});
  assert.notEqual(changed.code,200);assert.match(changed.msg,/快照|理论损耗/);check('saved raw task metadata retains snapshots and cannot rewrite theoretical loss');
  let product=await detail('/metal/product',stored.productId,f.admin);product.processes[0].loss='6.00';
  ok(await api('/metal/product',f.admin,'PUT',product));product=await detail('/metal/product',stored.productId,f.admin);assert.equal(product.processes[0].measurementMode,'PIECE');
  assert.deepEqual(savedSteps(await detail('/metal/work-order',old.id,f.admin)),savedSteps(old));
  const plansPreview=ok(await api(`/metal/work-order/options/products/${stored.productId}/processes?lineId=${stored.productionLineId}`,f.admin)).data;
  assert.equal(plansPreview.snapshot.measurementPolicyVersion,2);assert(plansPreview.plans.every(p=>p.measurementMode==='PIECE'));check('product save and new-order preview derive PIECE from actual process types without changing old orders');
  const work=await newWork();assert.equal(work.items[0].snapshot.measurementPolicyVersion,2);assert(work.items[0].plans.every(p=>p.measurementMode==='PIECE'));assert.equal(Number(work.items[0].plans[0].loss),6);
  const beforeAdjust=await detail('/metal/inventory',stored.oldOutput.stock,f.admin);ok(await api(`/metal/inventory/${stored.oldOutput.stock}/adjust`,f.admin,'POST',{requestId:randomUUID(),version:beforeAdjust.version,operatorId:f.ownerA,keeperId:f.ownerA,deltaWeight:'0',deltaQuantity:0,referenceQuantity:33,reason:'隔离补录参考数，不改变历史加工量'}));
  const denied=await bind(work,stored.oldOutput.stock,'10.00',f.processB,{extraLine:{referenceQuantity:33}});
  assert.notEqual(denied.response.code,200);assert.match(denied.response.msg,/按件|参考件数/);check('new post-process refuses historical WEIGHT semi instead of treating reference counts as pieces');
  const legacy=await bind(old,stored.oldOutput.stock,'95.00',f.processB);ok(legacy.response);
  const legacyDone=await handover(legacy.batch,stored.productId,'90.00',{pieces:5});assert.equal(legacyDone.flow.status,'POSTED');
  await closeStage(old,melt);assert.deepEqual(savedSteps(await detail('/metal/work-order',old.id,f.admin)),savedSteps(old));check('historical WEIGHT smelting continues into its old piece conversion and settles successfully');
  const issue=await bind(work,stored.raw.stock,'100.00',melt);ok(issue.response);
  const output=await handover(issue.batch,stored.productId,'95.00',{pieces:5});const stock=await detail('/metal/inventory',output.stock,f.admin);
  assert.equal(stock.measurementMode,'PIECE');assert.equal(stock.quantity,5);assert.equal(Number(stock.netWeight),95);check('new smelting creates five real piece-managed blanks with independent weight');
  const current=await detail('/metal/work-order',work.id,f.admin);ok(await api(`/metal/work-order/${work.id}/partial-close`,f.admin,'POST',{requestId:randomUUID(),version:current.version,reason:'隔离保留坯件供纯后加工',confirmed:true}));
  const release=ok(await api(`/metal/warehouse/inventory/${output.stock}/semi-release`,f.admin,'POST',{requestId:randomUUID(),version:stock.version,quantity:5,netWeight:'95.00',reason:'计量隔离释放'}));
  product=await detail('/metal/product',stored.productId,f.admin);product.processes=[product.processes[1]];ok(await api('/metal/product',f.admin,'PUT',product));
  const post=await newWork(10,[f.processB]),postIssue=await bind(post,output.stock,'95.00',f.processB,{extraLine:{quantity:5,releaseId:release.id}});ok(postIssue.response);
  const context=ok(await api(`/metal/warehouse-flows/handover-context/${postIssue.batch}`,processor)).data;
  return {post,postIssue,context,output};
  };
  const checkpoint=stored.checkpoint??await beforePost();stored.checkpoint=checkpoint;stored.f=f;stored.passed=passed;fs.writeFileSync(new URL('fixture-private.json',artifact),JSON.stringify(stored,null,2));
  const {post,postIssue,context,output}=checkpoint;
  if(!stored.postDone){
  const outputDoc=pieces=>({type:'FINISHED_IN',warehouseId:warehouses[0],keeperId:f.ownerA,lines:[{category:'FINISHED',referenceId:stored.productId,netWeight:'76.00',quantity:pieces}]});
  const submit=documents=>({requestId:randomUUID(),version:context.version,orderDate:'2026-11-03',physicalChecked:true,reason:'隔离实物交接及全部余料核对',residualMode:documents.length>1?'RETURNED':'NONE',documents});
  const invalid=await api(`/metal/warehouse-flows/handover/${postIssue.batch}`,processor,'POST',submit([outputDoc(6)]));assert.notEqual(invalid.code,200);assert.match(invalid.msg,/实际领用件数/);check('first post-process planned ten but issued five cannot submit six pieces');
  const templateResponse=await fetch(process.env.TEST_API_BASE_URL+`/metal/warehouse-order/template?type=FINISHED_IN&factoryId=${f.factoryA}`,{headers:{Authorization:'Bearer '+f.admin}});assert.equal(templateResponse.status,200);
  const template=Buffer.from(await templateResponse.arrayBuffer());
  const preview=async pieces=>{
   const bytes=execFileSync('python',['-c',"import sys,io,openpyxl;w=openpyxl.load_workbook(io.BytesIO(sys.stdin.buffer.read()));s=w.worksheets[0];v={'物料／产品ID':sys.argv[1],'实测净重(g)':'76.00','数量(件)':int(sys.argv[2])};s.append([v.get(c.value) for c in s[1]]);b=io.BytesIO();w.save(b);sys.stdout.buffer.write(b.getvalue())",stored.productId,String(pieces)],{input:template,maxBuffer:3000000});
   const form=new FormData();form.append('file',new Blob([bytes]),'measurement.xlsx');for(const[k,v]of Object.entries({factoryId:f.factoryA,warehouseId:warehouses[0],type:'FINISHED_IN',workOrderId:post.id,itemId:post.items[0].id,processId:f.processB,batchId:postIssue.batch}))form.append(k,String(v));
   return (await fetch(process.env.TEST_API_BASE_URL+'/metal/warehouse-order/import',{method:'POST',headers:{Authorization:'Bearer '+f.admin},body:form})).json();
  };
  assert.notEqual((await preview(6)).code,200);ok(await preview(5));check('actual Excel import rejects six and accepts five for first post-process');
  const source=sql(`SELECT l.line_id FROM metal_warehouse_order_line l JOIN metal_warehouse_order o ON o.order_id=l.order_id WHERE o.batch_id=${postIssue.batch} AND o.type='PRODUCTION_OUT' AND o.status='POSTED' LIMIT 1`);
  const returned={type:'SEMI_RETURN',warehouseId:warehouses[0],keeperId:f.ownerA,reason:'退回一件完整坯件',lines:[{category:'SEMI',sourceLineId:source,netWeight:'19.00',quantity:1}]};
  const badReturn=await api(`/metal/warehouse-flows/handover/${postIssue.batch}`,processor,'POST',submit([outputDoc(5),returned]));assert.notEqual(badReturn.code,200);check('piece output plus returned whole blanks cannot exceed actual issued pieces');
  const good=submit([outputDoc(4),returned]);let done=ok(await api(`/metal/warehouse-flows/handover/${postIssue.batch}`,processor,'POST',good)).data;
  for(const group of done.groups)done=ok(await confirm(done,group,keeper)).data;
  assert.equal(done.status,'POSTED');assert.equal(Number(sql(`SELECT completed_quantity FROM metal_process_batch WHERE batch_id=${postIssue.batch}`)),4);assert.equal(Number((await detail('/metal/inventory',output.stock,f.admin)).quantity),1);check('four finished pieces and one returned blank settle once and restore one physical source piece');
  stored.postDone=true;stored.f=f;fs.writeFileSync(new URL('fixture-private.json',artifact),JSON.stringify(stored,null,2));
  }
  if(!stored.rawDone){
  const rawNew=await rawOrder(stored.powder,'20.00',[acid]);assert.equal(rawNew.items[0].snapshot.measurementPolicyVersion,2);assert.equal(rawNew.items[0].plans[0].measurementMode,'WEIGHT');
  const rawIssue=await bind(rawNew,stored.raw.stock,'20.00',acid);ok(rawIssue.response);const rawOut=await handover(rawIssue.batch,stored.powder,'18.00');await closeStage(rawNew,acid);assert.equal((await detail('/metal/work-order',rawNew.id,f.admin)).status,'COMPLETED');assert.equal((await detail('/metal/inventory',rawOut.stock,f.admin)).quantity,0);check('new raw-only task stays weight-managed without fabricated piece progress');
  stored.rawDone=true;stored.f=f;fs.writeFileSync(new URL('fixture-private.json',artifact),JSON.stringify(stored,null,2));
  }
  let product=await detail('/metal/product',stored.productId,f.admin);product.processes=[{processId:melt,loss:'6.00',lossUnit:'PERCENT',measurementMode:'PIECE'},{processId:f.processB,loss:'5.00',lossUnit:'PERCENT',measurementMode:'PIECE'}];ok(await api('/metal/product',f.admin,'PUT',product));
  const rawSecond=await incoming(stored.powder,'200.00',{warehouse:warehouses[1],origin:'NATIVE'}),multiWork=await newWork(4);
  const docs=[stored.raw.stock,rawSecond.stock].map((inventoryId,i)=>orderBody('PRODUCTION_OUT',warehouses[i],[{inventoryId,netWeight:i===0?'40.00':'60.00',quantity:0}],{workOrderId:multiWork.id,itemId:multiWork.items[0].id,processId:melt}));
  let multi=ok(await api('/metal/warehouse-flows',f.admin,'POST',{requestId:randomUUID(),factoryId:f.factoryA,documents:docs})).data;
  for(const group of multi.groups)multi=ok(await confirm(multi,group,processor)).data;
  const mc=ok(await api(`/metal/warehouse-flows/handover-context/${multi.batchId}`,processor)).data;
  const sourceRows=sql(`SELECT l.line_id,o.warehouse_id FROM metal_warehouse_order_line l JOIN metal_warehouse_order o ON o.order_id=l.order_id WHERE o.batch_id=${multi.batchId} AND o.type='PRODUCTION_OUT' AND o.status='POSTED' ORDER BY o.warehouse_id`).split(/\r?\n/).map(r=>r.split('\t'));
  const outputs=warehouses.map(warehouseId=>({type:'SEMI_IN',warehouseId,keeperId:f.ownerA,lines:[{category:'SEMI',referenceId:stored.productId,netWeight:'44.00',quantity:2}]}));
  const returns=sourceRows.map(([sourceLineId,warehouseId])=>({type:'RAW_RETURN',warehouseId,keeperId:f.ownerA,reason:'本仓原料余量',lines:[{category:'RAW',sourceLineId,netWeight:'6.00',quantity:0}]}));
  const multiBody={requestId:randomUUID(),version:mc.version,orderDate:'2026-11-03',physicalChecked:true,reason:'多仓实际全部还料核对',residualMode:'RETURNED',documents:[...outputs,...returns]};
  const rejected=structuredClone(multiBody);rejected.requestId=randomUUID();rejected.documents[0].lines[0].quantity=3;assert.notEqual((await api(`/metal/warehouse-flows/handover/${multi.batchId}`,processor,'POST',rejected)).code,200);
  const submitted=await Promise.all([api(`/metal/warehouse-flows/handover/${multi.batchId}`,processor,'POST',multiBody),api(`/metal/warehouse-flows/handover/${multi.batchId}`,processor,'POST',multiBody)]);submitted.forEach(ok);assert.equal(submitted[0].data.id,submitted[1].data.id);let finish=submitted[0].data;
  for(const group of finish.groups)finish=ok(await confirm(finish,group,keeper)).data;
  const replay=ok(await api(`/metal/warehouse-flows/handover/${multi.batchId}`,processor,'POST',multiBody)).data;assert.equal(replay.id,finish.id);assert.equal(replay.status,'POSTED');
  assert.equal(Number(sql(`SELECT completed_quantity FROM metal_process_batch WHERE batch_id=${multi.batchId}`)),4);assert.equal(sql(`SELECT COUNT(*) FROM metal_work_report WHERE batch_id=${multi.batchId} AND voided=FALSE`),'1');check('two warehouses aggregate four blanks, return raw per source, and concurrent/lost-response retry settles once');
  stored.f=f;stored.multiBatch=multi.batchId;fs.writeFileSync(new URL('fixture-private.json',artifact),JSON.stringify(stored,null,2));
  fs.writeFileSync(new URL('live-result.json',artifact),JSON.stringify({passed,complete:true},null,2));

 }else if(phase==='finish'){
  const own=stored.multiBatch;if(!own)throw Error('no registered multi batch');
  const initialBatch=await detail('/metal/process-batch',own,f.admin);
  if(initialBatch.status!=='VOID'){
  const sources=sql(`SELECT l.inventory_id,l.net_weight-COALESCE((SELECT SUM(r.net_weight) FROM metal_warehouse_order_line r JOIN metal_warehouse_order ro ON ro.order_id=r.order_id WHERE ro.type='RAW_RETURN' AND ro.status='POSTED' AND r.source_line_id=l.line_id),0) FROM metal_warehouse_order_line l JOIN metal_warehouse_order o ON o.order_id=l.order_id WHERE o.batch_id=${own} AND o.type='PRODUCTION_OUT' AND o.status='POSTED'`).split(String.fromCharCode(10)).map(row=>row.trim().split(String.fromCharCode(9)));
  const before=sources.map(([inventoryId,spent])=>({inventoryId,expected:Number(sql(`SELECT net_weight FROM metal_inventory WHERE inventory_id=${inventoryId}`))+Number(spent)}));
  const batch=await detail('/metal/process-batch',own,f.admin);ok(await api(`/metal/process-batch/${own}/reverse`,f.admin,'POST',{version:batch.version,reason:'隔离多仓整批冲销验证'}));
  assert.equal((await detail('/metal/process-batch',own,f.admin)).status,'VOID');for(const source of before)assert.equal(Number(sql(`SELECT net_weight FROM metal_inventory WHERE inventory_id=${source.inventoryId}`)),source.expected);
  assert.equal(sql(`SELECT COUNT(*) FROM metal_work_report WHERE batch_id=${own} AND voided=FALSE`),'0');check('whole multi-warehouse batch reversal restores raw balances and removes effective completed pieces');
  }
  const origin=sql(`SELECT origin_batch_id FROM metal_inventory WHERE inventory_id=${stored.checkpoint.output.stock}`),source=await detail('/metal/process-batch',origin,f.admin);
  const denied=await api(`/metal/process-batch/${origin}/reverse`,f.admin,'POST',{version:source.version,reason:'验证有下游引用时禁止冲销'});assert.notEqual(denied.code,200);check('downstream-consumed blanks retain reversal protection');
  const flow=sql(`SELECT flow_id FROM metal_warehouse_flow WHERE batch_id=${own} AND kind='HANDOVER' ORDER BY flow_id DESC LIMIT 1`),body=JSON.parse(sql(`SELECT CAST(content AS CHAR) FROM metal_warehouse_flow_event WHERE flow_id=${flow} AND action='HANDOVER' ORDER BY event_id DESC LIMIT 1`));
  const ledger=sql(`SELECT COUNT(*) FROM metal_stock_ledger WHERE order_id IN (SELECT order_id FROM metal_warehouse_order WHERE batch_id=${own})`);
  const replay=ok(await api(`/metal/warehouse-flows/handover/${own}`,processor,'POST',body)).data;assert.equal(replay.id,flow);assert.equal(sql(`SELECT COUNT(*) FROM metal_stock_ledger WHERE order_id IN (SELECT order_id FROM metal_warehouse_order WHERE batch_id=${own})`),ledger);check('replaying reversed handover returns its original flow without new stock or ledger');
  stored.passed=passed;fs.writeFileSync(new URL('fixture-private.json',artifact),JSON.stringify(stored,null,2));fs.writeFileSync(new URL('live-result.json',artifact),JSON.stringify({passed,complete:true},null,2));
 }else if(phase==='cleanup'){
  assertEnvironment(f.environment,sql);assertWarehouseOwnership(f,sql);cleanupWarehouseFixtures(f);
  const factories=`${f.factoryA},${f.factoryB}`;sql(`START TRANSACTION;DELETE s FROM metal_work_order_step s JOIN metal_work_order_item i ON i.item_id=s.item_id JOIN metal_work_order w ON w.work_order_id=i.work_order_id WHERE w.factory_id IN (${factories});DELETE r FROM metal_work_order_review r JOIN metal_work_order w ON w.work_order_id=r.work_order_id WHERE w.factory_id IN (${factories});DELETE i FROM metal_work_order_item i JOIN metal_work_order w ON w.work_order_id=i.work_order_id WHERE w.factory_id IN (${factories});DELETE FROM metal_work_order WHERE factory_id IN (${factories});COMMIT;`);
  const {cleanupWorkOrderFixture}=await import('./work-order-test-fixture.mjs');await cleanupWorkOrderFixture(f,f.admin);check('registered measurement fixtures cleaned while preserving audits');
 }else throw Error('unknown phase');
 console.log(JSON.stringify({phase,passed:passed.length,isolated:true}));
}catch(error){if(stored){stored.f=f;stored.passed=passed;fs.writeFileSync(new URL('fixture-private.json',artifact),JSON.stringify(stored,null,2));}fs.writeFileSync(new URL('live-result.json',artifact),JSON.stringify({passed,complete:false,error:error.message},null,2));console.error(error);process.exitCode=1;}
