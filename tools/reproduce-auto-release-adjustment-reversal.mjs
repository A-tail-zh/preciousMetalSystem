import fs from'node:fs';import assert from'node:assert/strict';import{randomUUID}from'node:crypto';import{warehouseFixture}from'./warehouse-test-fixture.mjs';import{api,ok,detail,login}from'./local-test-client.mjs';
const root=process.env.TEST_OUTPUT_DIR||'.runtime/auto-release-positive-e2e-2026-10-23';fs.mkdirSync(root,{recursive:true});let f,browser,page;
try{f=await warehouseFixture();fs.writeFileSync(root+'/reversal-fixture.json',JSON.stringify({prefix:f.prefix,factoryA:f.factoryA,factoryB:f.factoryB,made:f.made}));
 const stockOf=async id=>(await detail('/metal/warehouse-order',id,f.admin)).lines[0].inventoryId;
 const post=async(type,lines,b,extra={})=>f.post(type,lines,{batchId:b,...extra});
 const raw=await stockOf(await f.post('RAW_IN',[{category:'RAW',referenceId:f.mat1,quantity:0,netWeight:'105',organization:f.prefix}]));
 const first=await f.createBatch(f.processA,10);await post('PRODUCTION_OUT',[{inventoryId:raw,quantity:0,netWeight:'105'}],first);
 const stock=await stockOf(await post('SEMI_IN',[{category:'SEMI',referenceId:f.productA,quantity:10,netWeight:'100'}],first,{settle:true,completedQuantity:10}));
 const last=await f.createBatch(f.processB,10);await post('PRODUCTION_OUT',[{inventoryId:stock,quantity:10,netWeight:'100'}],last);
 const empty=await detail('/metal/inventory',stock,f.admin);assert.equal(Number(empty.quantity),0);
 const adjustment=String(ok(await api('/metal/inventory/'+stock+'/adjust',f.admin,'POST',{requestId:randomUUID(),version:empty.version,operatorId:f.ownerA,keeperId:f.ownerA,deltaQuantity:4,deltaWeight:'40',reason:'隔离复现：末次领料后确认盘盈4件40g，核对自动释放依赖'})).id);
 await post('FINISHED_IN',[{category:'FINISHED',referenceId:f.productA,quantity:10,netWeight:'98'}],last,{settle:true,completedQuantity:10});
 let automatic;for(let n=0;n<30;n++){automatic=ok(await api('/metal/work-order/'+f.wo+'/auto-release',f.admin)).data;if(automatic.event?.status==='RELEASED')break;await new Promise(r=>setTimeout(r,250))}assert.equal(automatic.event.status,'RELEASED');
 const before=await detail('/metal/inventory',stock,f.admin);const ad=await detail('/metal/warehouse-order',adjustment,f.admin);
 const denied=await api('/metal/warehouse-order/'+adjustment+'/reverse',null,'POST',{version:ad.version,reason:'隔离验收：未登录不得冲销'});assert.equal(denied.code,401);
 const ordinary=await login(f.phoneA,f.password);assert.equal((await api('/metal/warehouse-order/'+adjustment+'/reverse',ordinary,'POST',{version:ad.version,reason:'隔离验收：无冲销权限'})).code,403);await api('/logout',ordinary,'POST');
 const menus=ok(await api('/metal/options/menus',f.admin)).data,reverseMenu=menus.find(m=>m.perms==='metal:warehouse-order:reverse');assert(reverseMenu);
 const foreignRole=await detail('/system/role',f.roleB,f.admin);ok(await api('/system/role',f.admin,'PUT',{...foreignRole,menuIds:[reverseMenu.id]}));
 const foreign=await login(f.phoneB,f.password);const foreignRejected=await api('/metal/warehouse-order/'+adjustment+'/reverse',foreign,'POST',{version:ad.version,reason:'隔离验收：有冲销权限但跨工厂'});assert.notEqual(foreignRejected.code,200);assert(!foreignRejected.msg.includes('释放#'));await api('/logout',foreign,'POST');
 const stale=await api('/metal/warehouse-order/'+adjustment+'/reverse',f.admin,'POST',{version:ad.version+1,reason:'隔离验收：过期版本'});assert.notEqual(stale.code,200);assert.match(stale.msg,/修改|版本/);
 let response;
 const browserReverse=async()=>{await page.getByRole('dialog',{name:'仓储工单管理详情',exact:true}).getByRole('button',{name:'冲销',exact:true}).click();const prompt=page.getByRole('dialog',{name:'冲销确认',exact:true});
 // The isolated component host has its own ElementPlus overlay counter; align the test prompt above it.
 await prompt.evaluate(el=>{const overlay=el.closest('.el-overlay');if(overlay)overlay.style.zIndex='10000'});
 await prompt.locator('input').fill('隔离验收：核对调整单冲销释放依赖');await page.screenshot({path:root+'/reversal-prompt.png',fullPage:true});const [result]=await Promise.all([page.waitForResponse(r=>r.url().endsWith('/metal/warehouse-order/'+adjustment+'/reverse')&&r.request().method()==='POST'),prompt.getByRole('button',{name:'确定',exact:true}).click()]);return result.json()};
 if(process.env.VERIFY_REVERSAL_BROWSER==='1'){
  const {chromium}=await import('../frontend/node_modules/playwright/index.mjs');browser=await chromium.launch({channel:'chrome',headless:true});page=await browser.newPage({viewport:{width:1440,height:1000}});await page.context().addCookies([{name:'Admin-Token',value:f.admin,url:'http://127.0.0.1:5173'}]);const info=page.waitForResponse(r=>r.url().includes('/getInfo')&&r.status()===200);await page.goto('http://127.0.0.1:5173/index');await info;
  page.on('request',r=>{if(r.method()==='POST')console.log('BROWSER POST '+new URL(r.url()).pathname)});page.on('pageerror',e=>console.log('BROWSER ERROR '+e.message));
  await page.evaluate(async id=>{const {createApp}=await import('/node_modules/.vite/deps/vue.js'),{default:ElementPlus}=await import('/node_modules/.vite/deps/element-plus.js'),{default:C}=await import('/src/views/metal/warehouse-common/WarehousePage.vue');document.querySelector('#app').style.display='none';const el=document.createElement('div');document.body.append(el);const vm=createApp(C,{kind:'warehouse-order'}).use(ElementPlus).mount(el);await vm.$.setupState.view({id})},adjustment);
  response=await browserReverse();await page.getByText(/存在有效释放，不能冲销调整单/).first().waitFor();assert(await page.getByRole('dialog',{name:'仓储工单管理详情',exact:true}).isVisible());assert.equal(await page.getByText('冲销成功',{exact:true}).count(),0);await page.screenshot({path:root+'/reversal-blocked-1440.png',fullPage:true});
 }else response=await api('/metal/warehouse-order/'+adjustment+'/reverse',f.admin,'POST',{version:ad.version,reason:'隔离复现：有效自动释放尚存，冲销盘盈应被阻止'});
 const after=await detail('/metal/inventory',stock,f.admin),flow=ok(await api('/metal/work-order/'+f.wo+'/semi-flow',f.admin)).data;const release=flow.releases.find(r=>r.releaseMode==='AUTO'&&r.status==='ACTIVE');
 const evidence={expected:'reject reversal while active release depends on adjustment',workOrderNo:(await detail('/metal/work-order',f.wo,f.admin)).orderNo,adjustment:ad.orderNo,reverseResponse:response,before,after,activeRelease:release};fs.writeFileSync(root+'/adjustment-reversal-evidence.json',JSON.stringify(evidence,null,2));
 assert.notEqual(response.code,200,'BUG: positive inventory adjustment reversed despite active AUTO release');
 assert.match(response.msg,/有效释放/);assert.match(response.msg,/撤销释放/);
 assert.deepEqual(after,before,'rejected reversal must not change inventory, version, balances or ledger');
 assert.equal(release.availableQuantity,4);assert.equal(release.availableWeight,'40.000000');
 const unchanged=await detail('/metal/warehouse-order',adjustment,f.admin);assert.equal(unchanged.status,'POSTED');assert.equal(unchanged.version,ad.version);
 const cancel=await api('/metal/warehouse/semi-release/'+release.releaseId+'/cancel',f.admin,'POST',{requestId:randomUUID(),version:release.version,reason:'隔离验收：撤销未使用释放后允许合法冲销'});ok(cancel);
 // The same protection must apply to manually released stock, regardless of AUTO provenance.
 const manualStock=await detail('/metal/inventory',stock,f.admin);const manualId=String(ok(await api('/metal/warehouse/inventory/'+stock+'/semi-release',f.admin,'POST',{requestId:randomUUID(),version:manualStock.version,quantity:4,netWeight:'40',reason:'隔离验收：手工释放亦须阻止调整单冲销'})).id);
 const beforeManualRejected=await detail('/metal/inventory',stock,f.admin);
 const manualDocBefore=await detail('/metal/warehouse-order',adjustment,f.admin),manualReleaseBefore=ok(await api('/metal/work-order/'+f.wo+'/semi-flow',f.admin)).data.releases.find(r=>r.releaseId===manualId);
 const manualRejected=await api('/metal/warehouse-order/'+adjustment+'/reverse',f.admin,'POST',{version:ad.version,reason:'隔离验收：验证手工释放保护'});assert.notEqual(manualRejected.code,200);assert.match(manualRejected.msg,/手工释放/);assert.deepEqual(await detail('/metal/inventory',stock,f.admin),beforeManualRejected);
 const manualFlow=ok(await api('/metal/work-order/'+f.wo+'/semi-flow',f.admin)).data.releases.find(r=>r.releaseId===manualId);ok(await api('/metal/warehouse/semi-release/'+manualId+'/cancel',f.admin,'POST',{requestId:randomUUID(),version:manualFlow.version,reason:'隔离验收：撤销手工释放'}));
 assert.deepEqual(manualFlow,manualReleaseBefore);assert.deepEqual(await detail('/metal/warehouse-order',adjustment,f.admin),manualDocBefore);
 if(page)await page.waitForTimeout(1100); // Respect the existing one-second duplicate-submit guard.
 const allowed=page?await browserReverse():await api('/metal/warehouse-order/'+adjustment+'/reverse',f.admin,'POST',{version:ad.version,reason:'隔离验收：释放依赖解除后合法冲销'});ok(allowed);
 if(page){await page.getByRole('dialog',{name:'仓储工单管理详情',exact:true}).waitFor({state:'hidden'});await page.getByText('冲销成功',{exact:true}).waitFor();await page.screenshot({path:root+'/reversal-allowed-1440.png',fullPage:true})}
 const zero=await detail('/metal/inventory',stock,f.admin);assert.equal(Number(zero.quantity),0);assert.equal(zero.netWeight,'0.000000');assert.equal(zero.releasedQuantity,0);assert.equal(zero.releasedWeight,'0.000000');
 assert.equal((await detail('/metal/warehouse-order',adjustment,f.admin)).status,'VOID');
 assert.notEqual((await api('/metal/warehouse-order/'+adjustment+'/reverse',f.admin,'POST',{version:(await detail('/metal/warehouse-order',adjustment,f.admin)).version,reason:'隔离验收：重复冲销'})).code,200);
 await new Promise(r=>setTimeout(r,2300));assert.equal((await detail('/metal/inventory',stock,f.admin)).releasedQuantity,0);
 fs.writeFileSync(root+'/adjustment-reversal-green.json',JSON.stringify({rejection:response,unchanged,afterCancelAndReverse:zero},null,2));
 console.log('PASS released stock adjustment reversal rejected without side effects; cancellation allows legitimate reversal and AUTO does not reappear');
}finally{if(browser)await browser.close();if(f){await f.cleanup();console.log('isolated reproduction data cleaned')}}
