import assert from 'node:assert/strict';
import fs from 'node:fs';
import {dirname,resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {randomUUID} from 'node:crypto';
import {performance} from 'node:perf_hooks';
import {chromium,expect} from '../frontend/node_modules/@playwright/test/index.mjs';

// Run only after the parent has started its new isolated JAR and frontend and kept the registered fixture.
const required={TEST_API_BASE_URL:'http://127.0.0.1:8091',TEST_MYSQL_PORT:'3321',TEST_DB_SCHEMA:'heavy_metal_processing_v2',TEST_FRONTEND_BASE_URL:'http://127.0.0.1:5197',TEST_REDIS_PORT:'6391'};
for(const [name,value] of Object.entries(required))assert.equal(process.env[name],value,'isolated environment required: '+name);
const input=process.env.CROSS_USER_BROWSER_MANIFEST||process.argv[2]||(process.env.PROCESSING_ARTIFACT_DIR&&resolve(process.env.PROCESSING_ARTIFACT_DIR,'browser-fixture.private.json'));
assert(input,'pass the private browser manifest path or PROCESSING_ARTIFACT_DIR');
const manifestPath=resolve(input),fixtureDir=dirname(manifestPath),manifest=JSON.parse(fs.readFileSync(manifestPath,'utf8'));
assert.equal(manifest.schemaVersion,1);
assert.deepEqual(manifest.environment,{schema:required.TEST_DB_SCHEMA,port:required.TEST_MYSQL_PORT,api:required.TEST_API_BASE_URL});
if(manifest.frontendBaseUrl)assert.equal(manifest.frontendBaseUrl,required.TEST_FRONTEND_BASE_URL);
const fullResult=JSON.parse(fs.readFileSync(resolve(fixtureDir,'live-result.json'),'utf8'));
assert.equal(fullResult.complete,true,'the parent full-chain run must complete before browser work');
assert.equal(fullResult.prefix,manifest.prefix);
const registryPath=fileURLToPath(new URL('../.runtime/test-fixtures/'+encodeURIComponent(manifest.prefix)+'.json',import.meta.url));
const registry=JSON.parse(fs.readFileSync(registryPath,'utf8'));
assert.equal(registry.prefix,manifest.prefix);assert.equal(registry.runId,manifest.cleanup?.runId);assert.equal(registry.cleaned,false);
assert.deepEqual(registry.environment,manifest.environment);
if(process.env.TEST_RUN_ID)assert.equal(process.env.TEST_RUN_ID,registry.runId);
const id=value=>{assert.match(String(value),/^\d+$/,'fixture ID must be numeric');return String(value)};
const keeper=manifest.users?.keeper,processor=manifest.users?.processor;
for(const actor of [keeper,processor]){assert(actor?.token,'private fixture token required');id(actor.id);assert.notEqual(String(actor.id),'1','use the two fixture users, not admin')}
assert.notEqual(String(keeper.id),String(processor.id));
const factory=id(manifest.factoryId??manifest.factoryA),warehouse=id(manifest.warehouseIds?.[0]),material=id(manifest.blockMaterialId??manifest.materialId);
const secrets=[manifest.adminToken,...Object.values(manifest.users??{}).flatMap(u=>[u.token,u.password])].filter(v=>typeof v==='string'&&v.length);
const redact=value=>secrets.reduce((text,secret)=>text.split(secret).join('[redacted]'),String(value));
const outDir=resolve(process.env.CROSS_USER_ARTIFACT_DIR||resolve(fixtureDir,'cross-user-refresh-'+Date.now()));
fs.mkdirSync(resolve(outDir,'screenshots'),{recursive:true});
const report={runId:registry.runId,prefix:manifest.prefix,environment:manifest.environment,frontend:required.TEST_FRONTEND_BASE_URL,startedAt:new Date().toISOString(),complete:false,checks:[],skips:[],api:[],requests:[],created:[],screenshots:[],faultInjections:[]};
const reportPath=resolve(outDir,'result.json');
const save=()=>fs.writeFileSync(reportPath,redact(JSON.stringify(report,null,2)));
const pass=(name,evidence={})=>{report.checks.push({name,at:new Date().toISOString(),...evidence});save();console.log('PASS '+name)};
const skip=(name,reason)=>{report.skips.push({name,reason});save();console.log('SKIP '+name+': '+reason)};
save();
const {sql}=await import('./local-test-client.mjs');
const {assertEnvironment,assertFixtureOwnership}=await import('./test-fixture-scope.mjs');
const readSql=query=>{assert.match(query,/^SELECT\b/i);assert(!/;|\bFOR\s+UPDATE\b|\bINTO\b/i.test(query),'SQL evidence must be read-only');return sql(query)};
assertEnvironment(manifest.environment,readSql);
assertFixtureOwnership(manifest.prefix,{factories:[factory],users:[id(keeper.id),id(processor.id)],roles:[id(manifest.roleA)],materials:[material]},readSql);
const materialCode=readSql('SELECT material_code FROM metal_material WHERE material_id='+material);
const warehouseName=readSql('SELECT name FROM metal_warehouse WHERE warehouse_id='+warehouse+' AND factory_id='+factory);
assert(materialCode&&warehouseName,'fixture material and warehouse must exist');
assert(warehouseName.startsWith(manifest.prefix),'warehouse must belong to this fixture');
const front=required.TEST_FRONTEND_BASE_URL,apiBase=required.TEST_API_BASE_URL,headless=process.env.LIVE_BROWSER_HEADED!=='1';
const timeout=15000;
let browser;
const contexts=[];
const units=value=>{const text=String(value).replace(/,/g,'').trim();assert.match(text,/^-?\d+(\.\d{1,6})?$/);const negative=text.startsWith('-'),[whole,fraction='']=(negative?text.slice(1):text).split('.');const n=BigInt(whole)*1000000n+BigInt(fraction.padEnd(6,'0'));return negative?-n:n};
const weight=value=>{assert(value>=0n);return (value/1000000n)+'.'+String(value%1000000n).padStart(6,'0')};
async function call(path,actor,method='GET',body){
 assert(path.startsWith('/'),'API path must be relative');
 const started=performance.now(),response=await fetch(apiBase+path,{method,headers:{'Content-Type':'application/json',Authorization:'Bearer '+actor.token},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(20000)});
 const value=await response.json();
 report.api.push({actorId:String(actor.id),method,path,http:response.status,code:value.code,durationMs:Math.round(performance.now()-started)});
 assert.equal(response.status,200,'HTTP failure: '+method+' '+path);
 assert.equal(value.code,200,'business failure: '+method+' '+path+' code='+value.code+' '+redact(value.msg??''));
 return value;
}
async function rejected(path,actor){
 const response=await fetch(apiBase+path,{headers:{Authorization:'Bearer '+actor.token},signal:AbortSignal.timeout(20000)}),value=await response.json();
 report.api.push({actorId:String(actor.id),method:'GET',path,http:response.status,code:value.code,expectedRejection:true});
 assert([401,403].includes(response.status)||[401,403].includes(value.code),'revoked fixture session must be rejected by the server');
 return {http:response.status,code:value.code};
}
async function until(check,message,limit=timeout){
 const began=performance.now();
 while(performance.now()-began<limit){const value=await check();if(value)return {at:new Date().toISOString(),elapsedMs:Math.round(performance.now()-began),value};await new Promise(resolve=>setTimeout(resolve,200))}
 throw Error('timed out: '+message);
}
const requestCount=(label,path)=>report.requests.filter(r=>r.context===label&&r.method==='GET'&&r.path===path).length;
async function session(label,actor,path){
 const context=await browser.newContext({viewport:{width:1440,height:1000},serviceWorkers:'block'});
 contexts.push(context);
 await context.addInitScript(()=>{window.__liveRefreshEvents=[];for(const name of ['focus','blur','visibilitychange'])((name==='visibilitychange')?document:window).addEventListener(name,()=>window.__liveRefreshEvents.push({name,at:Date.now(),visibility:document.visibilityState}))});
 await context.addCookies([{name:'Admin-Token',value:actor.token,url:front}]);
 context.on('request',request=>{
  const url=new URL(request.url());if(url.origin!==front||!url.pathname.startsWith('/dev-api/'))return;
  report.requests.push({context:label,method:request.method(),path:url.pathname.slice('/dev-api'.length),query:url.search,at:new Date().toISOString()});
 });
 context.on('requestfailed',request=>{const row=report.requests.findLast(r=>r.context===label&&r.path===new URL(request.url()).pathname.slice('/dev-api'.length)&&!r.failure);if(row)row.failure=request.failure()?.errorText});
 const page=await context.newPage();
 const identityResponse=page.waitForResponse(r=>new URL(r.url()).pathname==='/dev-api/getInfo');
 await page.goto(front+path);
 const identity=await (await identityResponse).json();
 assert.equal(identity.code,200);assert.equal(String(identity.user.userId),String(actor.id));
 assert(new URL(page.url()).origin===front);
 pass('real browser identity '+label,{userId:String(actor.id),permissions:identity.permissions?.length??0,independentContext:true});
 return {page,context,label};
}
async function inbound(keeperId,netWeight,batchNo){
 const payload={requestId:randomUUID(),factoryId:factory,warehouseId:warehouse,type:'RAW_IN',orderDate:new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Shanghai'}).format(new Date()),operatorIds:[id(processor.id)],keeperId:id(keeperId),reason:manifest.prefix+'跨用户刷新验收',lines:[{category:'RAW',referenceId:material,quantity:0,netWeight,materialOwnership:'FACTORY',batchNo}]};
 const result=await call('/metal/warehouse-order',processor,'POST',payload);
 const orderId=id(result.id),flowId=id(result.flowId);
 const flow=(await call('/metal/warehouse-flows/'+flowId,processor)).data;
 assert.equal(String(flow.factoryId),factory);assert.equal(flow.kind,'RAW_INBOUND');assert.equal(flow.groups.length,1);
 assert.equal(String(flow.groups[0].keeperId),String(keeperId));
 const order=(await call('/metal/warehouse-order/'+orderId,processor)).data;
 report.created.push({orderId,flowId,keeperId:String(keeperId),batchNo,requestId:payload.requestId});
 save();return {payload,orderId,flowId,flow,orderNo:order.orderNo};
}
const ledgerCount=orderId=>Number(readSql('SELECT COUNT(*) FROM metal_stock_ledger l JOIN metal_warehouse_order o ON o.order_id=l.order_id WHERE o.factory_id='+factory+' AND o.order_id='+id(orderId)));
const stockBatch=manifest.prefix+'_refresh_'+randomUUID().slice(0,8);
let stockId,stockWeight=0n;
async function addStock(tag,netWeight){
 const created=await inbound(processor.id,netWeight,stockBatch),group=created.flow.groups[0];
 const body={requestId:randomUUID(),version:created.flow.version,lines:group.lines.map(line=>({lineId:line.id,netWeight:line.plannedWeight,quantity:line.line.quantity??0}))};
 const suffix='/metal/warehouse-flows/'+created.flowId+'/groups/'+id(group.id)+'/confirm';
 const confirmed=await Promise.all([call(suffix,processor,'POST',body),call(suffix,processor,'POST',body)]);
 for(const value of confirmed){assert.equal(String(value.data.id),created.flowId);assert.equal(value.data.status,'POSTED')}
 assert.equal(ledgerCount(created.orderId),1,'concurrent confirmation must post exactly one ledger line');
 const replay=await call('/metal/warehouse-order',processor,'POST',created.payload);
 assert.equal(String(replay.id),created.orderId);assert.equal(String(replay.flowId),created.flowId);assert.equal(ledgerCount(created.orderId),1);
 const order=(await call('/metal/warehouse-order/'+created.orderId,processor)).data;
 const currentId=id(order.lines[0].inventoryId);if(stockId)assert.equal(currentId,stockId,'same fixture batch must accumulate in the same stock');stockId=currentId;
 stockWeight+=units(netWeight);
 const inventory=(await call('/metal/inventory/'+stockId,processor)).data;
 assert.equal(String(inventory.factoryId),factory);assert.equal(units(inventory.netWeight),stockWeight);
 pass('real idempotent stock change '+tag,{orderId:created.orderId,flowId:created.flowId,inventoryId:stockId,weight:weight(stockWeight),ledgerLines:1,createReplaySameId:true,concurrentConfirmSameRequestId:true});
 return created;
}
async function inventoryView(page,target){
 // IDs are not displayed in the table; use the real bound row only to locate its rendered weight cell.
 return page.evaluate(target=>{
  const root=document.querySelector('.warehouse-page'),vm=root?.__vueParentComponent,rows=vm?.setupState?.rows??[],index=rows.findIndex(row=>String(row.id)===String(target));
  if(index<0)return {present:false};
  const headers=[...root.querySelectorAll('.warehouse-table .el-table__header-wrapper th')].map(cell=>cell.textContent.replace(/\s/g,''));
  const weightIndex=headers.findIndex(label=>/^净重/.test(label)),tr=root.querySelectorAll('.warehouse-table .el-table__body-wrapper tbody tr')[index];
  return {present:!!tr,id:String(rows[index].id),boundWeight:String(rows[index].netWeight),renderedWeight:tr?.children[weightIndex]?.textContent.trim(),text:tr?.textContent??''};
 },target);
}
async function observedStock(page,expected=stockWeight,limit=timeout){
 const observed=await until(async()=>{const row=await inventoryView(page,stockId);return row.present&&/^\d+(\.\d{1,6})?$/.test(String(row.renderedWeight??'').replace(/,/g,''))&&units(row.boundWeight)===expected&&units(row.renderedWeight)===expected&&row.text.includes(materialCode)?row:false},'actual inventory row balance',limit);
 return {inventoryId:stockId,expectedWeight:weight(expected),firstObservedAt:observed.at,latencyMs:observed.elapsedMs,renderedWeight:observed.value.renderedWeight,observation:'real bound row ID plus rendered table weight cell'};
}
async function fillReceipt(page,orderNo,value,reason){
 const list=page.getByRole('dialog',{name:'仓储交接确认',exact:true});
 await list.getByRole('row').filter({hasText:orderNo}).getByRole('button',{name:'查看',exact:true}).click();
 const dialog=page.getByRole('dialog',{name:'原料入库验收',exact:true});
 const input=dialog.locator('.flow-group .el-table__body input').first(),difference=dialog.getByPlaceholder('重量不同必须填写',{exact:true});
 await input.fill(value);await difference.fill(reason);
 return {dialog,input,difference};
}
try{
 const backendA=(await call('/getInfo',keeper)).user,backendB=(await call('/getInfo',processor)).user;
 assert.equal(String(backendA.userId),String(keeper.id));assert.equal(String(backendB.userId),String(processor.id));
 browser=await chromium.launch({channel:'chrome',headless});
 report.browser={headless,channel:'chrome',noMockResponses:true};save();
 const a=await session('A-flow',keeper,'/metal/warehouse-order'),b=await session('B-processor',processor,'/index');
 await a.page.bringToFront();
 const firstList=a.page.waitForResponse(r=>new URL(r.url()).pathname==='/dev-api/metal/warehouse-flows/list');
 await a.page.getByRole('button',{name:'仓储交接待办',exact:true}).click();
 assert.equal((await (await firstList).json()).code,200);
 const before=requestCount(a.label,'/metal/warehouse-flows/list'),createdAt=performance.now();
 const pending=await inbound(keeper.id,'10.00',manifest.prefix+'_draft_'+randomUUID().slice(0,8));
 const replay=await call('/metal/warehouse-order',processor,'POST',pending.payload);
 assert.equal(String(replay.id),pending.orderId);assert.equal(String(replay.flowId),pending.flowId);assert.equal(ledgerCount(pending.orderId),0);
 const appeared=await until(async()=>await a.page.getByRole('dialog',{name:'仓储交接确认',exact:true}).getByRole('row').filter({hasText:pending.orderNo}).count()>0,'A idle flow list observes B submission');
 assert(requestCount(a.label,'/metal/warehouse-flows/list')>before);
 pass('A idle flow list observes B independent API submission',{flowId:pending.flowId,firstObservedAt:appeared.at,latencyMs:Math.round(performance.now()-createdAt),noManualReload:true,noStorageSignal:true});
 const draft=await fillReceipt(a.page,pending.orderNo,'9.25',manifest.prefix+'实收差异草稿');
 const snapshot={weight:await draft.input.inputValue(),reason:await draft.difference.inputValue()};
 for(const width of [1920,1440,768,390]){
  await a.page.setViewportSize({width,height:1000});
  const alignment=await draft.dialog.evaluate(element=>({dialog:{left:element.querySelector('.el-dialog')?.getBoundingClientRect().left,right:element.querySelector('.el-dialog')?.getBoundingClientRect().right},viewport:innerWidth,headers:[...element.querySelectorAll('.flow-group .el-table__header-wrapper th:not(.el-table-column--selection):not(.el-table__expand-column) .cell')].map(cell=>({text:cell.textContent.trim(),align:getComputedStyle(cell).textAlign})),cells:[...element.querySelectorAll('.flow-group .el-table__body td:not(.el-table-column--selection):not(.el-table__expand-column) .cell')].map(cell=>getComputedStyle(cell).textAlign),inputs:[...element.querySelectorAll('.flow-group .el-table input')].map(input=>({value:input.value,align:getComputedStyle(input).textAlign})),tables:[...element.querySelectorAll('.flow-group .el-table__body-wrapper')].map(table=>({clientWidth:table.clientWidth,scrollWidth:table.scrollWidth}))}));
  assert(alignment.dialog.left>=-1&&alignment.dialog.right<=width+1,'dialog must fit viewport');
  assert(alignment.headers.length&&alignment.inputs.length);
  assert(alignment.headers.every(cell=>cell.align==='center'),'real table headers must be centered');
  assert(alignment.cells.every(align=>align==='center'),'real table data must be centered');
  assert(alignment.inputs.every(input=>input.align==='center'),'real table inputs must be centered');
  const screenshot=resolve(outDir,'screenshots','receipt-'+width+'.png');await a.page.screenshot({path:screenshot,fullPage:true});
  report.screenshots.push({width,path:screenshot,alignment});save();
 }
 await a.page.setViewportSize({width:1440,height:1000});
 const cancelAt=performance.now();
 await call('/metal/warehouse-flows/'+pending.flowId+'/cancel',processor,'POST',{requestId:randomUUID(),version:pending.flow.version,reason:manifest.prefix+'本人取消以验证另一用户草稿探测'});
 await expect(draft.dialog).toContainText('资料已更新',{timeout});
 assert.equal(await draft.input.inputValue(),snapshot.weight);assert.equal(await draft.difference.inputValue(),snapshot.reason);
 await expect(draft.dialog.getByRole('button',{name:'本人库管验收',exact:true})).toBeDisabled();
 assert.equal(ledgerCount(pending.orderId),0);
 pass('A receipt draft remains unchanged after B cancellation',{flowId:pending.flowId,latencyMs:Math.round(performance.now()-cancelAt),weight:snapshot.weight,reasonPreserved:true,staleNotice:true,noLedgerPosted:true});
 const inventory=await session('A-inventory',keeper,'/metal/inventory');
 assert.notEqual(inventory.context,a.context);assert.notEqual(inventory.context,b.context);
 await inventory.page.bringToFront();await expect(inventory.page.locator('.warehouse-table')).toBeVisible();
 const filters=inventory.page.getByRole('search',{name:'筛选条件',exact:true});
 await filters.getByRole('textbox',{name:'物料编码',exact:true}).fill(materialCode);
 await filters.getByRole('textbox',{name:'客户公司 / 工厂自有',exact:true}).fill('工厂自有');
 const warehouseField=filters.locator('.el-form-item').filter({has:inventory.page.getByRole('combobox',{name:'仓库',exact:true})});
 await warehouseField.locator('.el-select__wrapper').click();await inventory.page.getByRole('option',{name:warehouseName,exact:true}).click();
 await filters.getByRole('button',{name:'查询',exact:true}).click();
 await inventory.page.locator('.el-pagination__sizes .el-select__wrapper').click();await inventory.page.getByRole('option',{name:'64条/页',exact:true}).click();
 await until(()=>report.requests.some(r=>r.context===inventory.label&&r.path==='/metal/inventory/list'&&r.query.includes('pageSize=64')),'real inventory page-size request');
 const stockStarted=performance.now();await addStock('new row','17.13');
 pass('A independent inventory context observes B confirmed new stock',{...await observedStock(inventory.page),actionToFirstObservedMs:Math.round(performance.now()-stockStarted),noStorageSignal:true,noManualReload:true});
 const visibilityBefore=await inventory.page.evaluate(()=>document.visibilityState);
 const cover=await inventory.context.newPage();await cover.goto('about:blank');await cover.bringToFront();await inventory.page.waitForTimeout(300);
 const hidden=await inventory.page.evaluate(()=>document.visibilityState==='hidden');
 if(hidden){
  const stoppedAt=requestCount(inventory.label,'/metal/inventory/list');
  await inventory.page.waitForTimeout(6100);assert.equal(requestCount(inventory.label,'/metal/inventory/list'),stoppedAt,'hidden tab must stop inventory queries');
  await addStock('while A is hidden','2.11');await inventory.page.waitForTimeout(6100);
  assert.equal(requestCount(inventory.label,'/metal/inventory/list'),stoppedAt,'hidden tab must remain stopped after independent change');
  const returned=performance.now();await inventory.page.bringToFront();
  pass('real hidden tab pauses reads and checks on return',{before:visibilityBefore,hidden:true,...await observedStock(inventory.page),returnToObservedMs:Math.round(performance.now()-returned),hiddenRequestCount:0});
 }else{
  skip('real hidden-tab query suspension','Chrome '+(headless?'headless':'headed')+' kept document.visibilityState='+await inventory.page.evaluate(()=>document.visibilityState)+' after another real tab came to the front; no synthetic visibility event was used');
  await inventory.page.bringToFront();
 }
 await cover.close();
 const beforeOffline=stockWeight,beforeToasts=await inventory.page.locator('.el-message:visible,.el-notification:visible').count(),offlineBegan=new Date().toISOString();
 await inventory.context.setOffline(true);
 await expect(inventory.page.getByRole('status').filter({hasText:'列表更新失败'})).toBeVisible({timeout:20000});
 await addStock('while A is offline','3.17');
 const offlineRow=await inventoryView(inventory.page,stockId);assert.equal(units(offlineRow.boundWeight),beforeOffline);assert.equal(units(offlineRow.renderedWeight),beforeOffline);
 await inventory.page.waitForTimeout(6100);
 assert.equal(await inventory.page.locator('.el-message:visible,.el-notification:visible').count(),beforeToasts,'background failures must not produce repeated global toasts');
 const failedReads=report.requests.filter(r=>r.context===inventory.label&&r.path==='/metal/inventory/list'&&r.at>=offlineBegan&&r.failure);
 assert(failedReads.length>0,'real offline request failures must be recorded');
 pass('real offline A preserves old stock and reports inline failure',{oldWeight:weight(beforeOffline),serverWeight:weight(stockWeight),failedRequests:failedReads.length,globalToastIncrease:0,fault:'BrowserContext.setOffline(true)'});
 await inventory.context.setOffline(false);
 const foregroundCover=await inventory.context.newPage();await foregroundCover.goto('about:blank');await foregroundCover.bringToFront();
 const resumedAt=performance.now();await inventory.page.bringToFront();
 pass('A recovers fresh stock after network restoration',{...await observedStock(inventory.page,stockWeight,25000),restorationToObservedMs:Math.round(performance.now()-resumedAt),events:await inventory.page.evaluate(()=>window.__liveRefreshEvents.slice(-12))});
 await foregroundCover.close();
 const latePending=await inbound(keeper.id,'10.00',manifest.prefix+'_late_'+randomUUID().slice(0,8));
 const late=await session('A-delayed-real-response',keeper,'/metal/warehouse-order');
 await late.page.bringToFront();await late.page.getByRole('button',{name:'仓储交接待办',exact:true}).click();
 await expect(late.page.getByRole('dialog',{name:'仓储交接确认',exact:true}).getByRole('row').filter({hasText:latePending.orderNo})).toBeVisible({timeout});
 let release,captured=false,finished=false;
 const hold=new Promise(resolve=>release=resolve);
 await late.page.route('**/dev-api/metal/warehouse-flows/list?*',async route=>{
  if(captured){await route.continue();return}
  const response=await route.fetch();assert.equal(response.status(),200);assert.equal((await response.json()).code,200);captured=true;
  const safety=setTimeout(release,25000);
  try{await hold;await route.fulfill({response})}finally{clearTimeout(safety);finished=true}
 });
 await until(()=>captured,'hold an actual scheduled GET response',10000);
 report.faultInjections.push({kind:'delay real HTTP response',path:'/metal/warehouse-flows/list',bodyUnchanged:true,method:'route.fetch() then hold then route.fulfill({response})'});
 const lateDraft=await fillReceipt(late.page,latePending.orderNo,'8.75',manifest.prefix+'迟到响应草稿');
 const lateWeight=await lateDraft.input.inputValue();release();
 await until(()=>finished,'release unchanged upstream response');await late.page.waitForTimeout(500);
 await expect(lateDraft.dialog).toContainText(latePending.orderNo);assert.equal(await lateDraft.input.inputValue(),lateWeight);
 pass('delayed real list response cannot replace a newly opened receipt draft',{flowId:latePending.flowId,weightPreserved:true,upstreamBodyUnchanged:true});
 const latestQuery=report.requests.findLast(r=>r.context===inventory.label&&r.path==='/metal/inventory/list'&&!r.failure)?.query??'';
 const query=new URLSearchParams(latestQuery);
 assert.equal(query.get('code'),materialCode);assert.equal(query.get('warehouseId'),warehouse);assert.equal(query.get('pageSize'),'64');assert.equal(query.get('filter'),'RAW');
 pass('automatic inventory refresh preserves real filters and page size',{code:materialCode,warehouseId:warehouse,pageSize:64,category:'RAW'});
 // Last mutation: revoke this registered fixture role only. Both fixture users losing their old sessions is intentional.
 assert(manifest.adminToken,'private fixture admin token required for scoped role revocation');
 const admin={id:'1',token:manifest.adminToken},roleId=id(manifest.roleA);
 assert.notEqual(roleId,'1');assert(registry.made.roles.map(String).includes(roleId),'role must be registered in this run');
 const assigned=readSql('SELECT user_id FROM sys_user_role WHERE role_id='+roleId+' ORDER BY user_id').split(/\r?\n/).filter(Boolean).map(id);
 assert(assigned.length&&assigned.includes(String(keeper.id))&&assigned.includes(String(processor.id)));
 assert(assigned.every(userId=>userId!=='1'&&registry.made.users.map(String).includes(userId)),'cannot revoke a role held by a user outside this registered fixture');
 assertFixtureOwnership(manifest.prefix,{roles:[roleId],users:assigned},readSql);
 const role=(await call('/system/role/'+roleId,admin)).data;
 assert.equal(String(role.id),roleId);assert.equal(String(role.factoryId),factory);assert(role.roleName.startsWith(manifest.prefix));assert(Array.isArray(role.menuIds)&&role.menuIds.length);
 const authVersions=()=>Object.fromEntries(readSql('SELECT user_id,auth_version FROM sys_user WHERE user_id IN ('+id(keeper.id)+','+id(processor.id)+') ORDER BY user_id').split(/\r?\n/).filter(Boolean).map(row=>{const [key,value]=row.split('\t');return [key,Number(value)]}));
 const versionsBefore=authVersions();
 let releaseRevoked,capturedRevoked=false,finishedRevoked=false;
 const revokedHold=new Promise(resolve=>releaseRevoked=resolve);
 await inventory.page.route('**/dev-api/metal/inventory/list?*',async route=>{
  if(capturedRevoked){await route.continue();return}
  const response=await route.fetch();assert.equal(response.status(),200);assert.equal((await response.json()).code,200);capturedRevoked=true;
  const safety=setTimeout(releaseRevoked,25000);
  try{await revokedHold;await route.fulfill({response})}finally{clearTimeout(safety);finishedRevoked=true}
 });
 await inventory.page.bringToFront();
 await until(()=>capturedRevoked,'hold a real successful inventory read before role revocation',10000);
 report.faultInjections.push({kind:'delay real pre-revocation inventory response',path:'/metal/inventory/list',bodyUnchanged:true});
 report.permissionMutation={roleId,registeredUserIds:assigned,menuCountBefore:role.menuIds.length,menuCountAfter:0,state:'prepared'};save();
 await call('/system/role',admin,'PUT',{id:roleId,version:role.version,roleName:role.roleName,factoryId:role.factoryId,roleSort:role.roleSort,status:role.status,menuIds:[]});
 report.permissionMutation.state='applied';save();
 const versionsAfter=authVersions();
 for(const actor of [keeper,processor])assert(versionsAfter[String(actor.id)]>versionsBefore[String(actor.id)],'server auth_version must advance');
 const deniedA=await rejected('/metal/inventory/list?filter=RAW&pageNum=1&pageSize=16',keeper),deniedB=await rejected('/metal/warehouse-flows/list?factoryId='+factory,processor);
 // This explicit UI query receives the actual 401 while an older real 200 remains held.
 await filters.getByRole('button',{name:'查询',exact:true}).click();
 await until(()=>inventory.page.evaluate(()=>{const vm=document.querySelector('.warehouse-page')?.__vueParentComponent;return vm&&vm.setupState.user.sessionReady===false&&vm.setupState.rows.length===0}),'frontend invalidates revoked session and clears old stock',15000);
 releaseRevoked();await until(()=>finishedRevoked,'release pre-revocation real success');await inventory.page.waitForTimeout(500);
 assert.equal((await inventoryView(inventory.page,stockId)).present,false,'late pre-revocation data must not repopulate the cleared list');
 pass('real fixture role revocation invalidates both old sessions and rejects late inventory data',{roleId,affectedRegisteredUserIds:assigned,versionsBefore,versionsAfter,keeperRejection:deniedA,processorRejection:deniedB,oldSuccessBodyUnchanged:true,frontendSessionReady:false,oldRowsRemainCleared:true});
 report.requestCounts=Object.fromEntries([...new Set(report.requests.map(r=>r.context))].map(label=>[label,Object.fromEntries([...new Set(report.requests.filter(r=>r.context===label).map(r=>r.path))].map(path=>[path,report.requests.filter(r=>r.context===label&&r.path===path).length]))]));
 report.complete=true;report.finishedAt=new Date().toISOString();save();
 console.log('RESULT '+reportPath);
}catch(error){
 report.failure={name:error?.name??'Error',message:redact(error?.message??error)};report.finishedAt=new Date().toISOString();save();
 console.error('FAILED '+report.failure.message);console.error('RESULT '+reportPath);process.exitCode=1;
}finally{
 await Promise.allSettled(contexts.map(context=>context.close()));if(browser)await browser.close();
 // The parent owns cleanup for this registered run; keep business evidence and audit records for review.
}
