import {test,expect,type Page} from '@playwright/test'
async function fixture(page:Page,options:{saveFails?:boolean;raw?:boolean;rawPlanFails?:boolean}={}){
 let version=0,saveFails=options.saveFails,rawPlanFails=options.rawPlanFails;const calls:string[]=[],mutations:{path:string;body:any}[]=[]
 const work={id:'61',orderNo:'FULL-61',version:0,taskType:'PRODUCTION',processingPolicyVersion:3,customer:'客户甲',customerId:'2',customerVersion:0,customerSnapshot:{name:'客户甲',companyName:'公司甲'},factoryId:'1',productionLineId:'3',ownerId:'7',processingType:'MATERIAL',orderDate:'2026-11-03',status:'WAITING_MATERIAL',canEdit:true,items:[{id:'62',productId:'4',quantity:5,snapshot:{code:'P4',name:'最终产品',metalTypeId:'1'},plans:[{processId:'21',processName:'酸洗',processRole:'ACID_WASH',outputStage:'RAW',measurementMode:'WEIGHT',loss:'1',startTime:'2026-11-04 08:00:00',endTime:'2026-11-04 09:00:00'},{processId:'22',processName:'融定',processRole:'SMELTING',outputStage:'SEMI',measurementMode:'PIECE',loss:'1',startTime:'2026-11-04 09:00:00',endTime:'2026-11-04 10:00:00'},{processId:'23',processName:'闸板',processRole:'POST_PROCESS',outputStage:'FINISHED',measurementMode:'PIECE',loss:'1',startTime:'2026-11-04 10:00:00',endTime:'2026-11-04 11:00:00'}]}]}
 if(options.raw)Object.assign(work,{taskType:'RAW_PROCESSING',items:[{...work.items[0],productId:'',materialId:'11',plannedInputWeight:'100',materialSnapshot:{code:'M11',name:'旧料',category:'RAW'},quantity:0}]})
 await page.context().addCookies([{name:'Admin-Token',value:'unified-draft-mock',url:'http://127.0.0.1:5196'}])
 await page.route('**/dev-api/**',async route=>{const req=route.request(),p=new URL(req.url()).pathname.replace('/dev-api','');calls.push(p);let out:any={code:200,data:[],rows:[],total:0}
  if(req.method()==='POST'||req.method()==='PUT'){
   mutations.push({path:p,body:req.postDataJSON()});if(p==='/metal/work-order'&&saveFails){saveFails=false;await route.abort('connectionfailed');return}if(p.endsWith('/raw-plan')&&rawPlanFails){rawPlanFails=false;await route.abort('connectionfailed');return}
  }
  if(p==='/getInfo')out={code:200,user:{userId:7,userName:'keeper',nickName:'仓管甲'},roles:['demo'],permissions:['*:*:*']}
  else if(p==='/getRouters')out.data=[{path:'/metal',component:'Layout',children:[{path:'work-order',component:'metal/work-order/index',name:'UnifiedOrders'},{path:'inventory',component:'metal/inventory/index',name:'UnifiedInventory'}]}]
  else if(p==='/metal/work-order/list')out={code:200,rows:[work],total:1}
  else if(p==='/metal/work-order/61'||p==='/metal/work-order/61/edit-context')out.data={...work,version}
  else if(p==='/metal/work-order/61/semi-flow')out.data={workOrder:{workOrderId:'61',version,taskType:work.taskType,status:'COMPLETED'},items:[{itemId:'62',productSnapshot:JSON.stringify(work.items[0].snapshot),materialSnapshot:options.raw?JSON.stringify(work.items[0].materialSnapshot):null}],progress:[],stocks:[],releases:[],uses:[],closures:[]}
  else if(p==='/metal/work-order/61/weight-balance')out.data={workOrderNo:'FULL-61',status:'BALANCED',items:[],totals:{},anomalies:[]}
  else if(p==='/metal/warehouse-flows/closeout-context/61')out.data={version,factoryId:'1',status:'COMPLETED',customerRemainingWeight:'10',factoryIssuedWeight:'0',factoryReturnedWeight:'0',factoryNetInputWeight:'0',lots:[],flows:[],offsetOrders:[],factorySupplement:[]}
  else if(p.endsWith('/number/reserve'))out.data='NEW-FULL'
  else if(p.endsWith('/options/factories'))out.data=[{id:'1',name:'本厂'}]
  else if(p.endsWith('/options/lines'))out.data=[{id:'3',name:'本产线'}]
  else if(p.endsWith('/options/owners'))out.data=[{id:'7',name:'仓管甲'}]
  else if(p.endsWith('/options/customers'))out.data=[{id:'2',name:'客户甲',companyName:'公司甲',version:0}]
  else if(p.endsWith('/options/products'))out.data=[{id:'4',name:'最终产品',code:'P4'}]
  await route.fulfill({json:out})
 })
 await page.goto('/metal/work-order');await expect(page.locator('.work-order-page')).toBeVisible()
 return{calls,mutations,changeVersion:()=>{version++}}
}
test('ordinary creation starts with final product without mutually exclusive task selector',async({page})=>{
 await fixture(page);await page.getByRole('button',{name:'添加',exact:true}).click();const d=page.getByRole('dialog',{name:'添加工单',exact:true});await expect(d).toBeVisible();await expect(d.getByRole('combobox',{name:'任务类型',exact:true})).toHaveCount(0);await expect(d).toContainText('最终产品');await expect(d).not.toContainText('待处理原料')
})
test('editing draft detects changed work version without replacing quantity',async({page})=>{
 await page.clock.install();const f=await fixture(page);await page.locator('.order-table').getByRole('button',{name:'编辑',exact:true}).click();const d=page.getByRole('dialog',{name:'编辑工单',exact:true});await expect(d).toBeVisible();await d.getByPlaceholder('请输入产品数量').fill('9');f.changeVersion();await page.clock.runFor(5500);await expect(d).toContainText('数据已更新，请重新核对');await expect(d.getByPlaceholder('请输入产品数量')).toHaveValue('9');await expect(d.getByRole('button',{name:'确认',exact:true})).toBeDisabled()
})
test('unknown work edit freezes its original body and prevents polling from replacing it',async({page})=>{
 await page.clock.install();const f=await fixture(page,{saveFails:true});await page.locator('.order-table').getByRole('button',{name:'编辑',exact:true}).click();const d=page.getByRole('dialog',{name:'编辑工单',exact:true});await d.getByPlaceholder('请输入产品数量').fill('9');await d.getByRole('button',{name:'确认',exact:true}).click();await expect(d).toContainText('提交结果尚未确认');await expect(d.getByPlaceholder('请输入产品数量')).toBeDisabled();const reads=f.calls.filter(p=>p.includes('/61')).length;f.changeVersion();await page.clock.runFor(6000);expect(f.calls.filter(p=>p.includes('/61')).length).toBe(reads);await d.getByRole('button',{name:'重试原请求',exact:true}).click();await expect.poll(()=>f.mutations.filter(m=>m.path==='/metal/work-order').length).toBe(2);const bodies=f.mutations.filter(m=>m.path==='/metal/work-order').map(m=>m.body);expect(bodies[1]).toEqual(bodies[0]);expect(bodies[0]).toMatchObject({id:'61',version:0,items:[{quantity:9}]})
})
test('a child success refresh does not unmount another residual draft',async({page})=>{
 const f=await fixture(page);await page.goto('/metal/work-order?id=61');const panel=page.locator('.residual-panel');await expect(panel.getByLabel('收尾原因',{exact:true})).toBeVisible();await panel.getByLabel('收尾原因',{exact:true}).fill('保留这份收尾草稿');await panel.evaluate(e=>e.setAttribute('data-mount-marker','original'));const reads=f.calls.filter(p=>p==='/metal/work-order/61').length
 await page.evaluate(()=>{let vm=(document.querySelector('.semi-panel') as any).__vueParentComponent;while(vm&&vm.type?.__name!=='SemiFlowPanel')vm=vm.parent;vm.emit('changed')});await expect.poll(()=>f.calls.filter(p=>p==='/metal/work-order/61').length).toBeGreaterThan(reads);await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));await expect(panel.getByLabel('收尾原因',{exact:true})).toHaveValue('保留这份收尾草稿');await expect(panel).toHaveAttribute('data-mount-marker','original')
})
test('raw plan unknown outcome retains input and replays the original request id',async({page})=>{
 const f=await fixture(page,{raw:true,rawPlanFails:true});await page.goto('/metal/work-order?id=61');await page.getByRole('button',{name:'变更计划',exact:true}).click();const d=page.getByRole('dialog',{name:'变更原料投入计划',exact:true});await d.getByLabel('计划投入净重（g）',{exact:true}).fill('120');await d.getByLabel('变更原因',{exact:true}).fill('本轮计划');await d.getByRole('button',{name:'保存计划变更',exact:true}).click();await expect(d).toContainText('提交结果尚未确认');await expect(d.getByLabel('计划投入净重（g）',{exact:true})).toHaveValue('120.00');await d.getByRole('button',{name:'重试原提交',exact:true}).click();await expect.poll(()=>f.mutations.filter(m=>m.path.endsWith('/raw-plan')).length).toBe(2);const bodies=f.mutations.filter(m=>m.path.endsWith('/raw-plan')).map(m=>m.body);expect(bodies[0].requestId).toBeTruthy();expect(bodies[1]).toEqual(bodies[0]);await expect(d).toBeHidden()
})
test('late work dialog close from an old account does not hide a new pending submission',async({page})=>{
 await fixture(page,{saveFails:true});await page.locator('.order-table').getByRole('button',{name:'编辑',exact:true}).click();let d=page.getByRole('dialog',{name:'编辑工单',exact:true});await expect(d).toBeVisible();await d.getByRole('button',{name:'取消',exact:true}).click()
 await page.evaluate(async()=>{const {default:useUserStore}=await import('/src/store/modules/user.ts');const user=useUserStore();document.cookie='Admin-Token=second-account; path=/';user.token='second-account';user.id='8';user.sessionReady=true;await (document.querySelector('.work-order-page') as any).__vueParentComponent.refs.formRef.open({id:'61'})})
 d=page.getByRole('dialog',{name:'编辑工单',exact:true});await expect(d).toBeVisible();await d.getByRole('button',{name:'确认',exact:true}).click();await expect(d).toContainText('提交结果尚未确认');await page.waitForTimeout(350);await expect(d).toBeVisible();await expect(d.getByRole('button',{name:'重试原请求',exact:true})).toBeEnabled()
})
