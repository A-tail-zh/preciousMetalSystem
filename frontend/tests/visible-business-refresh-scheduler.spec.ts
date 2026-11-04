import {test,expect,type Page} from '@playwright/test'

// Removing the periodic timer, context guard or quiet error branch must fail these browser checks.
async function setup(page:Page,probe=false){
 await page.route('**/dev-api/**',r=>r.fulfill({json:{code:200,data:[],rows:[],total:0}}))
 await page.goto('/login')
 await page.addStyleTag({content:'.el-loading-mask{pointer-events:none!important}'})
 await page.clock.install()
 await page.clock.pauseAt(new Date(Date.now()+1000))
 await page.evaluate(async probe=>{
  const vuePath='/node_modules/.vite/deps/vue.js',refreshPath='/src/composables/useInventoryRefresh.ts',userPath='/src/store/modules/user.ts',authPath='/src/utils/auth.ts'
  const {createApp,ref,h}=await import(vuePath),{useInventoryRefresh}=await import(refreshPath),user=(await import(userPath)).default(),auth=await import(authPath)
  auth.setToken('scheduler-test-A');Object.assign(user,{sessionReady:true,token:'scheduler-test-A',id:'refresh-A',permissions:['refresh:read']})
  const state:any={calls:0,probes:0,active:0,maxActive:0,resets:0,applied:0,fail:false,throw:false,hold:false,gates:[],contexts:[],blocked:ref(false),paused:ref(false)}
  const run=async(context:any,probe=false)=>{state.contexts.push(context);probe?state.probes++:state.calls++;state.maxActive=Math.max(state.maxActive,++state.active);try{if(state.hold)await new Promise(resolve=>state.gates.push(resolve));if(state.throw)throw Error('scheduler fixture failure');if(context?.isCurrent?.())state.applied++;return !state.fail}finally{state.active--}}
  const host=document.createElement('div');document.body.append(host)
  const app=createApp({setup(){state.api=useInventoryRefresh((context:any)=>run(context),state.blocked,{allowed:()=>user.sessionReady&&user.permissions.includes('refresh:read'),reset:()=>state.resets++},{paused:state.paused,...(probe?{probe:(context:any)=>run(context,true)}:{})});return()=>h('span','refresh harness')}})
  app.mount(host);Object.assign(state,{unmount:()=>app.unmount(),user,auth});(window as any).refreshTest=state
 },probe)
}
const snapshot=(page:Page)=>page.evaluate(()=>{const s=(window as any).refreshTest;return{calls:s.calls,probes:s.probes,active:s.active,maxActive:s.maxActive,resets:s.resets,applied:s.applied,failed:s.api?.failed?.value}})
async function event(page:Page,name='focus'){await page.evaluate(name=>{(name==='visibilitychange'?document:window).dispatchEvent(new Event(name))},name)}
async function visible(page:Page,value:boolean){await page.evaluate(value=>{Object.defineProperty(document,'visibilityState',{configurable:true,value:value?'visible':'hidden'});document.dispatchEvent(new Event('visibilitychange'))},value)}
async function tick(page:Page,ms:number){await page.clock.runFor(ms);await page.evaluate(()=>Promise.resolve())}

for(const invalidation of ['cookie','epoch','throw'])test('request preflight rejects '+invalidation+' change before attaching credentials or invoking the adapter',async({page})=>{
 await setup(page);await page.evaluate(()=>history.replaceState(null,'','/refresh-preflight-harness'))
 const result=await page.evaluate(async invalidation=>{
  const path='/src/utils/request.ts',request=(await import(path)).default,s=(window as any).refreshTest
  let current=true,calls=0
  const pending=request({url:'/refresh-preflight',method:'put',data:{draft:'原账号草稿'},isContextCurrent:()=>{if(invalidation==='throw'&&!current)throw Error('disposed fixture');return current&&s.user.token===s.auth.getToken()},adapter:async(config:any)=>{calls++;return{config,status:200,statusText:'OK',headers:{},request:{responseType:''},data:{code:401,msg:'expired'}}}})
  // Axios schedules request interceptors in a microtask; invalidate in the same turn before they run.
  if(invalidation==='cookie')s.auth.setToken('scheduler-test-B');else current=false
  try{await pending;return{calls,canceled:false,attached:true,ready:s.user.sessionReady}}catch(error:any){return{calls,canceled:error?.code==='ERR_CANCELED',attached:!!error?.config?.headers?.Authorization||!!error?.config?.sessionToken,ready:s.user.sessionReady}}
 },invalidation)
 expect(result).toEqual({calls:0,canceled:true,attached:false,ready:true});await tick(page,100)
 await expect(page.locator('.el-message,.el-notification')).toHaveCount(0);await expect(page.getByRole('dialog',{name:'系统提示'})).toHaveCount(0)
})

test('visible page checks every five seconds and coalesces foreground/local notifications',async({page})=>{
 await setup(page);await tick(page,4999);expect((await snapshot(page)).calls).toBe(0);await tick(page,1);expect((await snapshot(page)).calls).toBe(1)
 await event(page);await event(page,'visibilitychange');await event(page,'metal:inventory-changed');await tick(page,100);expect((await snapshot(page)).calls).toBe(2)
 await tick(page,5000);expect((await snapshot(page)).calls).toBe(3)
})
test('hidden page stops and foreground checks once immediately',async({page})=>{
 await setup(page);await visible(page,false);await tick(page,30000);expect((await snapshot(page)).calls).toBe(0)
 await visible(page,true);await event(page);await tick(page,100);expect((await snapshot(page)).calls).toBe(1)
})
test('draft block defers replacement and optionally probes without overlapping',async({page})=>{
 await setup(page,true);await page.evaluate(()=>{const s=(window as any).refreshTest;s.blocked.value=true;s.hold=true});await tick(page,5000)
 expect(await snapshot(page)).toMatchObject({calls:0,probes:1,active:1});await event(page);await tick(page,10000);expect((await snapshot(page)).probes).toBe(1)
 await page.evaluate(()=>{const s=(window as any).refreshTest;s.hold=false;s.gates.shift()();s.blocked.value=false});await tick(page,100)
 expect(await snapshot(page)).toMatchObject({calls:1,maxActive:1})
})
test('pause stops reads and probes and resumes one pending check',async({page})=>{
 await setup(page,true);await page.evaluate(()=>{const s=(window as any).refreshTest;s.blocked.value=true;s.paused.value=true});await tick(page,20000);await event(page);await tick(page,100)
 expect(await snapshot(page)).toMatchObject({calls:0,probes:0});await page.evaluate(()=>{(window as any).refreshTest.paused.value=false});await tick(page,100);expect((await snapshot(page)).probes).toBe(1)
})
test('failures back off, preserve failed status, and success resets cadence',async({page})=>{
 await setup(page);await page.evaluate(()=>{(window as any).refreshTest.fail=true});await tick(page,5000);expect(await snapshot(page)).toMatchObject({calls:1,failed:true})
 await tick(page,9999);expect((await snapshot(page)).calls).toBe(1);await tick(page,1);expect((await snapshot(page)).calls).toBe(2)
 await tick(page,19999);expect((await snapshot(page)).calls).toBe(2);await page.evaluate(()=>{(window as any).refreshTest.fail=false});await tick(page,1);expect(await snapshot(page)).toMatchObject({calls:3,failed:false});await tick(page,5000);expect((await snapshot(page)).calls).toBe(4)
})
test('account ABA invalidates old context and old finally cannot overlap the new read',async({page})=>{
 await setup(page);await page.evaluate(()=>{(window as any).refreshTest.hold=true});await tick(page,5000)
 await page.evaluate(()=>{const s=(window as any).refreshTest;s.user.id='refresh-B';s.user.id='refresh-A'});await tick(page,100)
 expect(await snapshot(page)).toMatchObject({calls:1,active:1,applied:0});expect(await page.evaluate(()=>(window as any).refreshTest.contexts[0]?.isCurrent?.()??true)).toBe(false)
 await page.evaluate(()=>{const s=(window as any).refreshTest;s.hold=false;s.gates.shift()()});await tick(page,100);expect(await snapshot(page)).toMatchObject({calls:2,active:0,applied:1,maxActive:1})
})
test('revocation and cookie mismatch prevent reads and invalidate in-flight results',async({page})=>{
 await setup(page);await page.evaluate(()=>{(window as any).refreshTest.hold=true});await tick(page,5000)
 await page.evaluate(()=>{(window as any).refreshTest.user.permissions=[]});await event(page);await tick(page,100);expect(await page.evaluate(()=>(window as any).refreshTest.contexts[0]?.isCurrent?.()??true)).toBe(false)
 await page.evaluate(()=>{const s=(window as any).refreshTest;s.hold=false;s.gates.shift()();s.auth.setToken('scheduler-test-B');s.user.permissions=['refresh:read']});await tick(page,10000);expect((await snapshot(page)).calls).toBe(1)
})
for(const hidden of [false,true])test('cookie mismatch immediately resets a paused in-flight read on '+(hidden?'hidden':'visible')+' focus',async({page})=>{
 await setup(page);await page.evaluate(()=>{(window as any).refreshTest.hold=true});await tick(page,5000);await page.evaluate(()=>{(window as any).refreshTest.paused.value=true});if(hidden)await visible(page,false)
 const before=await snapshot(page);expect(before).toMatchObject({calls:1,active:1,resets:0})
 await page.evaluate(()=>{(window as any).refreshTest.auth.setToken('scheduler-test-B');window.dispatchEvent(new Event('focus'))})
 expect(await snapshot(page)).toMatchObject({calls:1,active:1,resets:1,applied:0});await event(page);expect((await snapshot(page)).resets).toBe(1)
 await page.evaluate(()=>{const s=(window as any).refreshTest;s.hold=false;s.gates.shift()()});await tick(page,10000);expect(await snapshot(page)).toMatchObject({calls:1,active:0,applied:0,maxActive:1})
 await page.evaluate(()=>{const s=(window as any).refreshTest;s.auth.setToken('scheduler-test-A');s.paused.value=false});if(hidden)await visible(page,true);await event(page);await tick(page,100);expect(await snapshot(page)).toMatchObject({calls:2,applied:1,maxActive:1})
 await expect(page.locator('.el-message,.el-notification')).toHaveCount(0)
})
test('unmount invalidates context and clears every queued check',async({page})=>{
 await setup(page);await page.evaluate(()=>{(window as any).refreshTest.hold=true});await tick(page,5000);await page.evaluate(()=>{(window as any).refreshTest.unmount()});await event(page)
 await page.evaluate(()=>{const s=(window as any).refreshTest;s.hold=false;s.gates.shift()()});await tick(page,60000);expect(await snapshot(page)).toMatchObject({calls:1,applied:0})
})

for(const failure of ['business','http','network'])test('quiet background '+failure+' failures reject without toast; manual failures still notify',async({page})=>{
 await setup(page);await page.route('**/dev-api/refresh-quiet',r=>failure==='network'?r.abort():r.fulfill({status:failure==='http'?503:200,json:{code:500,msg:'后台刷新测试失败',traceId:'refresh-quiet-test'}}))
 const read=async(quiet:boolean)=>page.evaluate(async quiet=>{const path='/src/utils/request.ts',request=(await import(path)).default;try{await request({url:'/refresh-quiet',quiet});return false}catch{return true}},quiet)
 expect(await read(true)).toBe(true);await tick(page,100);await expect(page.locator('.el-message,.el-notification')).toHaveCount(0)
 expect(await read(false)).toBe(true);await expect(page.locator('.el-message').filter({hasText:failure==='network'?'后端接口连接异常':failure==='http'?'系统接口503异常':'后台刷新测试失败'})).toBeVisible()
})
test('quiet current-session 401 still expires while old-session 401 is ignored',async({page})=>{
 await setup(page);await page.evaluate(()=>history.replaceState(null,'','/refresh-harness'));await page.route('**/dev-api/refresh-expired',r=>r.fulfill({json:{code:401,msg:'expired'}}))
 await page.evaluate(async()=>{const path='/src/utils/request.ts',s=(window as any).refreshTest,request=(await import(path)).default;try{await request({url:'/refresh-expired',quiet:true,headers:{Authorization:'Bearer old-scheduler-test'}})}catch{};s.user.sessionReady=true})
 await expect(page.getByRole('dialog',{name:'系统提示'})).toHaveCount(0)
 await page.evaluate(async()=>{const path='/src/utils/request.ts',request=(await import(path)).default;try{await request({url:'/refresh-expired',quiet:true})}catch{}})
 await expect(page.getByRole('dialog',{name:'系统提示'})).toBeVisible();expect(await page.evaluate(()=>(window as any).refreshTest.user.sessionReady)).toBe(false)
})

async function mountBusiness(page:Page,file:string,props:Record<string,any>){
 await page.evaluate(async({file,props})=>{const s=(window as any).refreshTest;s.unmount();s.user.permissions=['*:*:*'];const vuePath='/node_modules/.vite/deps/vue.js',epPath='/node_modules/.vite/deps/element-plus.js';const {createApp}=await import(vuePath),{default:ElementPlus}=await import(epPath),{default:component}=await import('/src/views/metal/'+file);const host=document.createElement('div');host.style.cssText='position:fixed;inset:0;overflow:auto;background:white;z-index:999';document.body.append(host);const app=createApp(component,props);app.use(ElementPlus);app.mount(host);s.unmount=()=>app.unmount()}, {file,props})
 await tick(page,100)
}
test('semi-flow probe freezes reason and version and blocks stale confirmation',async({page})=>{
 await setup(page);let version=1
 await page.route('**/dev-api/metal/work-order/W/semi-flow',r=>r.fulfill({json:{code:200,data:{workOrder:{id:'W',version,status:'IN_PROGRESS'},items:[],progress:[],stocks:[],releases:[],uses:[]}}}))
 await mountBusiness(page,'work-order/SemiFlowPanel.vue',{workOrderId:'W'});await page.locator('.semi-heading').getByRole('button',{name:'部分完工关闭',exact:true}).click({force:true});await tick(page,100)
 const d=page.getByRole('dialog',{name:'部分完工关闭',exact:true});await d.getByLabel('操作原因',{exact:true}).fill('保留现场原因');version=2;await event(page);await tick(page,100)
 await expect(d).toContainText('资料已变化');await expect(d.getByLabel('操作原因',{exact:true})).toHaveValue('保留现场原因');await expect(d.getByRole('button',{name:'确认',exact:true})).toBeDisabled()
})
test('residual probe preserves entered weight and failed request identity',async({page})=>{
 await setup(page);let version=1;const bodies:any[]=[]
 await page.route('**/dev-api/metal/warehouse-flows/closeout-context/W',r=>r.fulfill({json:{code:200,data:{version,factoryId:'F',status:'COMPLETED',customerRemainingWeight:'100',lots:[],flows:[],offsetOrders:[],factorySupplement:[]}}}))
 await page.route('**/dev-api/metal/warehouse/options/people?**',r=>r.fulfill({json:{code:200,data:[{id:'K',name:'库管',nickName:'库管'}]}}))
 await page.route('**/dev-api/metal/warehouse-flows/closeout/W',r=>{bodies.push(r.request().postDataJSON());return r.fulfill({status:503,json:{msg:'暂时不可用'}})})
 await mountBusiness(page,'work-order/WorkOrderResidualPanel.vue',{workOrderId:'W'})
 await expect(page.getByRole('button',{name:'本人发起客户余料收尾'})).toBeEnabled()
 await page.evaluate(()=>{let vm=(document.querySelector('.residual-panel') as any).__vueParentComponent;while(vm&&vm.type?.__name!=='WorkOrderResidualPanel')vm=vm.parent;Object.assign(vm.setupState,{mode:'OFFSET',keeperId:'K',offsetWorkOrderId:'OLD',weight:'12.345',reason:'保留称重'})})
 await page.getByRole('button',{name:'本人发起客户余料收尾'}).click();await expect.poll(()=>bodies.length).toBe(1);expect(bodies[0].weight).toBe('12.345');await tick(page,100);version=2;await event(page);await tick(page,100)
 expect(await page.evaluate(()=>{let vm=(document.querySelector('.residual-panel') as any).__vueParentComponent;while(vm&&vm.type?.__name!=='WorkOrderResidualPanel')vm=vm.parent;return vm.setupState.weight})).toBe('12.345')
 await page.getByRole('button',{name:'重试原请求'}).click({force:true});await expect.poll(()=>bodies.length).toBe(2);expect(bodies[1]).toEqual(bodies[0])
})
test('ledger background failure keeps last good totals and reports stale data quietly',async({page})=>{
 await setup(page);let fail=false
 await page.route('**/dev-api/metal/work-order/W/weight-balance**',r=>r.fulfill({json:fail?{code:500,msg:'总账暂时不可用'}:{code:200,data:{workOrderNo:'保留总账',totals:{INPUT:'123'},items:[],status:'BALANCED',snapshotToken:'one'}}}))
 await mountBusiness(page,'work-order/WorkOrderWeightBalance.vue',{workOrderId:'W'});await expect(page.locator('.ledger')).toContainText('保留总账');fail=true;await event(page);await tick(page,100)
 await expect(page.locator('.ledger')).toContainText('保留总账');await expect(page.locator('.ledger')).toContainText('总账加载失败');await expect(page.locator('.el-message')).toHaveCount(0)
})
test('master background list retains page and selected current row',async({page})=>{
 await setup(page);let version=1
 await page.route('**/dev-api/metal/material/list?**',r=>r.fulfill({json:{code:200,rows:[{id:'M',version,code:'刷新物料',specification:'规格',materialType:'粉料'}],total:40}}))
 await mountBusiness(page,'MasterDataPage.vue',{kind:'material'});await expect(page.locator('.master-table')).toContainText('刷新物料');await page.locator('.master-table .el-table__body .el-checkbox').click({force:true});await page.locator('.master-pagination .el-pager li').filter({hasText:/^2$/}).click({force:true});await tick(page,100);await page.locator('.master-table .el-table__body .el-checkbox').click({force:true});version=2;await event(page);await tick(page,100)
 expect(await page.evaluate(()=>{let vm=(document.querySelector('.master-page') as any).__vueParentComponent;return vm.setupState.selected[0]?.version})).toBe(2);await expect(page.locator('.master-toolbar .edit-button')).toBeEnabled();await expect(page.locator('.master-pagination .el-pager .is-active')).toHaveText('2')
})
test('master edit probe detects changed version without replacing the draft',async({page})=>{
 await setup(page);let version=1
 await page.route('**/dev-api/metal/material/list?**',r=>r.fulfill({json:{code:200,rows:[{id:'M',version,code:'刷新物料',specification:'原规格',materialType:'粉料'}],total:1}}))
 await page.route('**/dev-api/metal/material/M',r=>r.fulfill({json:{code:200,data:{id:'M',version,code:'刷新物料',specification:'原规格',materialType:'粉料'}}}))
 await mountBusiness(page,'MasterDataPage.vue',{kind:'material'});await page.locator('.master-table .el-table__body').getByRole('button',{name:'编辑',exact:true}).click({force:true});await tick(page,100)
 const d=page.getByRole('dialog');await d.getByLabel('规格型号',{exact:true}).fill('现场草稿');version=2;await event(page);await tick(page,100)
 await expect(d).toContainText('资料已变化');await expect(d.getByLabel('规格型号',{exact:true})).toHaveValue('现场草稿');await expect(d.getByRole('button',{name:'确定',exact:true})).toBeDisabled()
})
test('rejected callbacks back off and foreground recovers without an unhandled rejection',async({page})=>{
 const errors:string[]=[];page.on('pageerror',e=>errors.push(e.message));await setup(page);await page.evaluate(()=>{(window as any).refreshTest.throw=true});await tick(page,5000);expect(await snapshot(page)).toMatchObject({calls:1,failed:true});await tick(page,9999);expect((await snapshot(page)).calls).toBe(1)
 await page.evaluate(()=>{(window as any).refreshTest.throw=false});await event(page);await tick(page,100);expect(await snapshot(page)).toMatchObject({calls:2,failed:false});expect(errors).toEqual([])
})
test('production overview displays RAW stocks as weight without piece release actions',async({page})=>{
 await setup(page)
 await page.route('**/dev-api/metal/work-order/W/semi-flow',r=>r.fulfill({json:{code:200,data:{workOrder:{id:'W',version:1,status:'COMPLETED',taskType:'PRODUCTION'},items:[],progress:[{processName:'前处理',outputStage:'RAW',outputWeight:'25',localQuantity:999}],stocks:[{inventoryId:'R',category:'RAW',name:'前处理料',quantity:999,netWeight:'25',unreleasedQuantity:999},{inventoryId:'S',category:'SEMI',name:'中间品',quantity:4,netWeight:'40',unreleasedQuantity:4}],releases:[],uses:[]}}}))
 await mountBusiness(page,'work-order/SemiFlowPanel.vue',{workOrderId:'W'});await expect(page.locator('.semi-panel')).toContainText('前处理原料');await expect(page.locator('.semi-panel')).toContainText('25.00g')
 const raw=page.locator('.semi-panel .el-table__body tr').filter({hasText:'前处理料'}),semi=page.locator('.semi-panel .el-table__body tr').filter({hasText:'中间品'});await expect(raw).not.toContainText('999');await expect(raw.getByRole('button',{name:'释放',exact:true})).toHaveCount(0);await expect(semi.getByRole('button',{name:'释放',exact:true})).toBeVisible()
})
test('master candidate change marks the draft stale without removing the selected metal',async({page})=>{
 await setup(page);let metals=[{id:'T',name:'原金属'}]
 await page.route('**/dev-api/metal/options/factories',r=>r.fulfill({json:{code:200,data:[{id:'F',name:'本厂'}]}}))
 await page.route('**/dev-api/metal/metal-types**',r=>r.fulfill({json:{code:200,data:metals}}))
 await page.route('**/dev-api/metal/material/list?**',r=>r.fulfill({json:{code:200,rows:[{id:'M',version:1,code:'候选物料',specification:'规格',materialType:'粉料'}],total:1}}))
 await page.route('**/dev-api/metal/material/M',r=>r.fulfill({json:{code:200,data:{id:'M',version:1,factoryId:'F',metalTypeId:'T',code:'候选物料',specification:'规格',materialType:'粉料'}}}))
 await mountBusiness(page,'MasterDataPage.vue',{kind:'material'});await page.locator('.master-table .el-table__body').getByRole('button',{name:'编辑',exact:true}).click({force:true});await tick(page,100);const d=page.getByRole('dialog');await expect(d).toContainText('原金属');metals=[];await event(page);await tick(page,100);await expect(d).toContainText('资料已变化');await expect(d).toContainText('原金属')
})
test('ledger periodic reads retain expanded detail page and selected item',async({page})=>{
 await setup(page);const linePages:string[]=[],items:string[]=[]
 await page.route('**/dev-api/metal/work-order/W/weight-balance**',r=>{const u=new URL(r.request().url());if(u.pathname.endsWith('/lines')){linePages.push(u.searchParams.get('page')||'');return r.fulfill({json:{code:200,data:{rows:[{orderNo:'第'+u.searchParams.get('page')+'页单据',weight:'4'}],total:45}}})}items.push(u.searchParams.get('itemId')||'');return r.fulfill({json:{code:200,data:{workOrderNo:'总账页码',totals:{INPUT:'123'},items:[{itemId:'I',productSnapshot:'{"name":"保留产品"}'}],status:'BALANCED',snapshotToken:'one'}}})})
 await mountBusiness(page,'work-order/WorkOrderWeightBalance.vue',{workOrderId:'W'});await expect(page.locator('.ledger')).toContainText('总账页码')
 await page.evaluate(()=>{let vm=(document.querySelector('.ledger') as any).__vueParentComponent;vm.setupState.item='I';void vm.setupState.load()});await expect.poll(()=>items.at(-1)).toBe('I')
 await page.locator('.ledger .el-table__expand-icon').first().click({force:true});await expect(page.locator('.ledger .lines')).toContainText('第1页单据')
 await page.locator('.ledger .lines .el-pager li').filter({hasText:/^2$/}).click({force:true});await expect(page.locator('.ledger .lines')).toContainText('第2页单据');await event(page);await tick(page,100)
 await expect(page.locator('.ledger .lines')).toContainText('第2页单据');await expect(page.locator('.ledger .lines .el-pager .is-active')).toHaveText('2');expect(items.at(-1)).toBe('I');expect(linePages.at(-1)).toBe('2')
})

for(const kind of ['customer','factory']){
 const customer=kind==='customer',label=customer?'客户名称':'工厂名称',save=customer?'保存':'确定'
 async function listFixture(page:Page){
  await setup(page);let version=1,fail=false,calls=0
  const row=()=>customer?{id:'X',version,name:'刷新客户',companyName:'客户公司',address:'客户地址',contactName:'联络人',contactPhone:'13800138000',department:null,factoryId:'F',factoryName:'工厂'}:{factoryId:'X',version,factoryName:'刷新工厂',area:'1',address:'工厂地址',introduction:'原简介',productionLines:[]}
  await page.route(`**/dev-api/metal/${kind}/list?**`,r=>{calls++;return r.fulfill({json:fail?{code:500,msg:'资料暂时不可用'}:{code:200,rows:[row()],total:40}})})
  await page.route(`**/dev-api/metal/${kind}/X**`,r=>r.fulfill({json:{code:200,data:row()}}))
  await mountBusiness(page,`${kind}/index.vue`,{})
  await expect(page.locator(`.${kind}-page .el-table`)).toContainText(customer?'刷新客户':'刷新工厂')
  return {version(value:number){version=value},fail(){fail=true},get calls(){return calls}}
 }
 test(kind+' periodic list preserves current page and selected current version quietly',async({page})=>{
  const s=await listFixture(page);await page.locator(`.${kind}-pagination .el-pager li`).filter({hasText:/^2$/}).click();await tick(page,100);await expect(page.locator(`.${kind}-pagination .el-pager .is-active`)).toHaveText('2');await page.locator(`.${kind}-page .el-table__body .el-checkbox`).click({force:true});s.version(2);await event(page);await tick(page,100)
  expect(await page.evaluate(kind=>(document.querySelector(`.${kind}-page`) as any).__vueParentComponent.setupState.selected[0]?.version,kind)).toBe(2);await expect(page.locator(`.${kind}-pagination .el-pager .is-active`)).toHaveText('2')
  s.fail();await event(page);await tick(page,100);await expect(page.locator(`.${kind}-page .el-table`)).toContainText(customer?'刷新客户':'刷新工厂');await expect(page.locator('.el-message,.el-notification')).toHaveCount(0)
 })
 test(kind+' modal probe keeps input and requires checking a changed version',async({page})=>{
  const s=await listFixture(page);await page.locator(`.${kind}-page .el-table__body`).getByRole('button',{name:'编辑',exact:true}).click({force:true});await tick(page,100);const d=page.getByRole('dialog');await d.getByLabel(label,{exact:true}).fill('现场草稿');s.version(2);await event(page);await tick(page,100)
  await expect(d).toContainText('资料已变化');await expect(d.getByLabel(label,{exact:true})).toHaveValue('现场草稿');await expect(d.getByRole('button',{name:save,exact:true})).toBeDisabled()
 })
 test(kind+' unknown save result preserves draft and blocks polling and duplicate create/save',async({page})=>{
  const s=await listFixture(page);let puts=0;await page.route(`**/dev-api/metal/${kind}`,r=>{puts++;return r.fulfill({status:503,json:{msg:'暂时不可用'}})})
  await page.locator(`.${kind}-page .el-table__body`).getByRole('button',{name:'编辑',exact:true}).click({force:true});await tick(page,100);const d=page.getByRole('dialog');await d.getByLabel(label,{exact:true}).fill('保留未知请求草稿');await d.getByRole('button',{name:save,exact:true}).click({force:true});await expect.poll(()=>puts).toBe(1);await expect(d).toContainText('结果待确认');const count=s.calls;await event(page);await tick(page,60000)
  expect(puts).toBe(1);expect(s.calls).toBe(count);await expect(d.getByLabel(label,{exact:true})).toHaveValue('保留未知请求草稿');await expect(d.getByRole('button',{name:save,exact:true})).toBeDisabled()
 })
 test(kind+' old-account save failure and finally cannot alter the new-account pending save',async({page})=>{
  await listFixture(page);const gates:(()=>void)[]=[];let completed=0
  await page.route(`**/dev-api/metal/${kind}`,async r=>{const index=gates.length;await new Promise<void>(resolve=>gates.push(resolve));completed++;await r.fulfill({json:index===0?{code:500,msg:'旧账号保存故障',traceId:'old-save-test'}:{code:200}})})
  const edit=async()=>{await page.locator(`.${kind}-page .el-table__body`).getByRole('button',{name:'编辑',exact:true}).click({force:true});await tick(page,100);return page.getByRole('dialog')}
  let d=await edit();await d.getByLabel(label,{exact:true}).fill('账号甲草稿');await d.getByRole('button',{name:save,exact:true}).click({force:true});await expect.poll(()=>gates.length).toBe(1)
  await page.evaluate(()=>{const s=(window as any).refreshTest;s.auth.setToken('scheduler-test-B');s.user.token='scheduler-test-B';s.user.id='refresh-B'});await tick(page,100);await expect(page.locator(`.${kind}-page .el-table`)).toContainText(customer?'刷新客户':'刷新工厂')
  d=await edit();await d.getByLabel(label,{exact:true}).fill('账号乙草稿');await d.getByRole('button',{name:save,exact:true}).click({force:true});await expect.poll(()=>gates.length).toBe(2);gates[0]();await expect.poll(()=>completed).toBe(1);await tick(page,100)
  await expect(d.getByLabel(label,{exact:true})).toHaveValue('账号乙草稿');await expect(d.getByRole('button',{name:save,exact:true})).toHaveClass(/is-loading/);await expect(page.getByText('旧账号保存故障',{exact:true})).toHaveCount(0)
  gates[1]();await expect.poll(()=>page.evaluate(kind=>(document.querySelector(`.${kind}-page`) as any).__vueParentComponent.setupState.dialogOpen,kind)).toBe(false);await tick(page,500);await expect(d).not.toBeVisible()
 })
}
test('automatic release status polls quietly without clearing the previous successful status',async({page})=>{
 await setup(page);let status='PENDING',fail=false
 await page.route('**/dev-api/metal/work-order/W/auto-release',r=>r.fulfill({json:fail?{code:500,msg:'释放状态暂时不可用'}:{code:200,data:{autoSemiReleaseEligible:true,event:{status},results:[]}}}))
 await mountBusiness(page,'work-order/AutoSemiReleaseStatus.vue',{workOrderId:'W'});await expect(page.locator('.auto-status')).toContainText('待处理');status='RELEASED';await event(page);await tick(page,100);await expect(page.locator('.auto-status')).toContainText('已自动释放');fail=true;await event(page);await tick(page,100);await expect(page.locator('.auto-status')).toContainText('已自动释放');await expect(page.locator('.el-message')).toHaveCount(0)
})
test('route permit modal probes changed version and preserves its reason and frozen request',async({page})=>{
 await setup(page);let version=1
 await page.route('**/dev-api/metal/route-skip-permits?**',r=>r.fulfill({json:{code:200,data:[{permitId:'P',version,sourceSequence:1,targetSequence:3,enabled:true,reason:'原原因'}]}}))
 await mountBusiness(page,'work-order/RouteSkipPermit.vue',{workOrderId:'W',items:[{id:'I',snapshot:{name:'产品'},plans:[{processId:'A',processName:'一'},{processId:'B',processName:'二'},{processId:'C',processName:'三'}]}]});await page.locator('.permits .el-table__body').getByRole('button',{name:'修改',exact:true}).click({force:true});await tick(page,100);const d=page.getByRole('dialog',{name:'工艺跳步许可'});await d.getByLabel('配置原因',{exact:true}).fill('保留跳步原因');version=2;await event(page);await tick(page,100)
 await expect(d).toContainText('资料已变化');await expect(d.getByLabel('配置原因',{exact:true})).toHaveValue('保留跳步原因');await expect(d.getByRole('button',{name:'保存许可'})).toBeDisabled()
})
test('route permit unknown result retries the same frozen request without polling or changing its version',async({page})=>{
 await setup(page);let version=1,reads=0;const bodies:any[]=[]
 await page.route('**/dev-api/metal/route-skip-permits?**',r=>{reads++;return r.fulfill({json:{code:200,data:[{permitId:'P',version,sourceSequence:1,targetSequence:3,enabled:true,reason:'原原因'}]}})})
 await page.route('**/dev-api/metal/route-skip-permits/P',r=>{bodies.push(r.request().postDataJSON());return r.fulfill({status:503,json:{msg:'暂时不可用'}})})
 await mountBusiness(page,'work-order/RouteSkipPermit.vue',{workOrderId:'W',items:[{id:'I',plans:[{processId:'A'},{processId:'B'},{processId:'C'}]}]});await page.locator('.permits .el-table__body').getByRole('button',{name:'修改',exact:true}).click({force:true});await tick(page,100);const d=page.getByRole('dialog',{name:'工艺跳步许可'});await d.getByLabel('配置原因',{exact:true}).fill('冻结请求原因');await d.getByRole('button',{name:'保存许可'}).click({force:true});await expect.poll(()=>bodies.length).toBe(1);await expect(d).toContainText('结果待确认');const count=reads;version=2;await event(page);await tick(page,60000);expect(reads).toBe(count)
 await d.getByRole('button',{name:'重试原请求'}).click({force:true});await expect.poll(()=>bodies.length).toBe(2);expect(bodies[1]).toEqual(bodies[0]);expect(bodies[1].version).toBe(1);await expect(d.getByLabel('配置原因',{exact:true})).toHaveValue('冻结请求原因')
})
