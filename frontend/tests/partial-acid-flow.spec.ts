import {test,expect,type Page,type Locator} from '@playwright/test'

async function choose(page:Page,scope:Page|Locator,label:string,name:string){
 const picker=scope.getByRole('combobox',{name:label,exact:true});await picker.press('ArrowDown')
 await page.locator(`[id="${await picker.getAttribute('aria-controls')}"]`).getByRole('option',{name,exact:true}).click()
}
async function fixture(page:Page,baseURL:string){
 const writes:any[]=[],requests:string[]=[]
 const steps=[{id:'41',sequenceNo:1,processName:'酸洗',semanticCode:'ACID_WASH',outputCategory:'RAW',measurementMode:'WEIGHT'}, {id:'42',sequenceNo:2,processName:'融定',semanticCode:'SMELTING',outputCategory:'SEMI',measurementMode:'WEIGHT'}, {id:'43',sequenceNo:3,processName:'轧板',semanticCode:'NORMAL',outputCategory:'PRODUCT',measurementMode:'PIECE'}]
 const order={id:'7',number:'GD-PARTIAL',orderNo:'GD-PARTIAL',status:'IN_PROGRESS',version:2,processingType:'DIRECT_CUSTOM',customerSnapshot:'{}'}
 const execution={id:'6',executionId:'6',orderId:'7',orderItemId:'31',orderStepId:'41',processorId:'81',processorName:'加工甲',processName:'酸洗',outputCategory:'RAW',measurementMode:'WEIGHT',status:'OPEN',batchId:'1011',batchMapped:true,readOnly:false,inputWeight:'1000.00',outputWeight:'400.00'}
 const raw={id:'501',warehouseId:'11',materialId:'51',category:'RAW',measurementMode:'WEIGHT',netWeight:'600.00',availableWeight:'600.00',identity:{code:'RAW',name:'普通未处理原料'},ownerships:[{ownerId:'0',weight:'600.00',availableWeight:'600.00'}]}
 const acid={...raw,id:'502',originStepId:'41',netWeight:'400.00',availableWeight:'400.00',identity:{code:'ACID',name:'已酸洗金属料',form:'METAL'},ownerships:[{ownerId:'88',ownerName:'客户甲',weight:'400.00',availableWeight:'400.00'}]}
 let delayStep='',releaseDelay:()=>void=()=>{},delayed:Promise<void>=Promise.resolve(),created=false
 await page.context().addCookies([{name:'Admin-Token',value:'partial-acid-only-mocks',url:baseURL}])
 await page.route('**/*-api/**',async route=>{
  const req=route.request(),url=new URL(req.url()),path=url.pathname.replace(/^\/(dev|prod)-api/,'')
  let r:any={code:200,data:[],rows:[],total:0}
  if(req.method()!=='GET')writes.push({path,data:req.postDataJSON()})
  if(path==='/getInfo')r={code:200,user:{userId:1,userName:'admin'},roles:['admin'],permissions:['*:*:*']}
  if(path==='/metal/options/factories')r.data=[{id:'101',name:'本厂'}]
  if(path==='/metal/production-order/list')r={code:200,rows:[{...order,factory:'本厂',customer:'客户甲',line:'产线',owner:'负责人'}],total:1}
  if(path==='/metal/production-order/7/flow')r.data={order,items:[{id:'31',quantity:100,product:{name:'金环'},steps}],executions:[execution],documents:[],events:[]}
  if(path==='/metal/production-order/7/report')r.data={executions:[],orderTotals:{}}
  if(path==='/metal/production-batches/list')r={code:200,rows:[{id:'1011',number:'PB-HISTORY',orderItemId:'31',plannedQuantity:100,lifecycle:'ACTIVE'}],total:1}
  if(path==='/metal/storage-orders/options'){
   const step=url.searchParams.get('orderStepId')||'';requests.push(step)
   if(step===delayStep)await delayed
   r.data={warehouses:[{id:'11',name:'原料库'}],people:[{id:'81',name:'加工甲',canReceive:true}],productionOrders:[{id:'7',number:'GD-PARTIAL'}],orderItems:[{id:'31',orderId:'7',productName:'金环',quantity:100,steps}],sourceOutboundOrders:[{id:'55',number:'OUT-ACID',orderId:'7',orderItemId:'31',orderStepId:'41',processorId:'81',executionId:'6',batchId:'1011',available:true}],executions:[execution,...(created?[{...execution,id:'8',orderStepId:'42',processName:'融定',outputCategory:'SEMI',batchId:'1012'}]:[])],stocks:step==='42'?[acid]:step==='43'?[]:[raw]}
  }
  if(path==='/metal/storage-orders/reservations'){created=true;const data=req.postDataJSON();r.data={sessionId:data.sessionId,status:'TEMP',version:1,batchId:'1012',executionId:'8',orderItemId:'31',orderStepId:'42',processorId:'81',lines:data.lines}}
  await route.fulfill({json:r})
 })
 return {writes,requests,delay(step:string){delayStep=step;delayed=new Promise<void>(resolve=>{releaseDelay=resolve})},release(){delayStep='';releaseDelay()}}
}
async function openFlow(page:Page){
 await page.goto('/metal/production-order');await choose(page,page,'所属工厂','本厂')
 await page.getByRole('row').filter({hasText:'GD-PARTIAL'}).getByRole('button',{name:'查看',exact:true}).click()
 return page.getByRole('dialog',{name:'生产工单完整流程',exact:true})
}
test('work order stage issues acid output independently while the acid record remains open',async({page,baseURL})=>{
 const f=await fixture(page,baseURL!),flow=await openFlow(page)
 for(const name of ['新建生产批次','进入工序','拆分','合并','完成生产批次','编辑','取消空批次'])await expect(flow.getByRole('button',{name,exact:true})).toHaveCount(0)
 await expect(flow.locator('.execution-table').getByRole('button',{name:'补料',exact:true})).toBeVisible()
 await flow.getByRole('row').filter({hasText:'融定'}).getByRole('button',{name:'生产领料',exact:true}).click()
 const d=page.getByRole('dialog',{name:'添加出库单',exact:true})
 await expect(d.getByRole('radio',{name:'生产领料',exact:true})).toBeChecked()
 await expect(d.getByText('2 融定',{exact:true})).toBeVisible()
 await expect(d.getByRole('combobox',{name:'原出库单',exact:true})).toHaveCount(0)
 await choose(page,d,'加工人员','加工甲');await choose(page,d,'仓库/库房','原料库');await page.keyboard.press('Escape');await d.getByRole('heading',{name:'物料清单',exact:true}).click()
 await d.getByRole('button',{name:'添加物料',exact:true}).click();const line=page.getByRole('dialog',{name:'物料明细',exact:true})
 await line.getByRole('combobox',{name:'库存物料',exact:true}).press('ArrowDown');await page.getByRole('option').filter({hasText:'已酸洗金属料'}).click()
 await line.getByRole('textbox',{name:'实测净重（g）',exact:true}).fill('400');await line.getByRole('button',{name:'确认',exact:true}).click();await expect(line).toBeHidden()
 const reserve=f.writes.find(w=>w.path==='/metal/storage-orders/reservations');expect(reserve.data).toMatchObject({executionMode:'NEW',orderId:'7',orderItemId:'31',orderStepId:'42',processorId:'81'})
 expect(reserve.data.executionId).toBeUndefined();expect(reserve.data.batchId).toBeUndefined();expect(reserve.data.lines[0].stockId).toBe('502')
 expect(f.writes.some(w=>w.path.includes('production-batches'))).toBe(false)
})
test('warehouse stage switch refreshes candidates and rejects delayed stale stage responses',async({page,baseURL})=>{
 const f=await fixture(page,baseURL!);await page.goto('/metal/storage-orders');await choose(page,page,'所属工厂','本厂');await page.getByRole('button',{name:'添加',exact:true}).click()
 const d=page.getByRole('dialog',{name:'添加出库单',exact:true});await choose(page,d,'生产工单','GD-PARTIAL');await choose(page,d,'加工人员','加工甲');await choose(page,d,'仓库/库房','原料库');await page.keyboard.press('Escape');await d.getByRole('heading',{name:'物料清单',exact:true}).click()
 f.delay('42');await choose(page,d,'目标工序','2 融定');await expect(d.getByRole('button',{name:'添加物料',exact:true})).toBeDisabled()
 await choose(page,d,'目标工序','3 轧板');f.release();await expect(d.getByRole('button',{name:'添加物料',exact:true})).toBeEnabled()
 await d.getByRole('button',{name:'添加物料',exact:true}).click();const line=page.getByRole('dialog',{name:'物料明细',exact:true})
 await expect(line.getByText('当前仓库暂无可领取库存，请核对上道产出是否已复验、是否选对仓库，以及库存是否已被领用或预占。',{exact:true})).toBeVisible()
 await line.getByRole('combobox',{name:'库存物料',exact:true}).press('ArrowDown');await expect(page.getByRole('option').filter({hasText:'已酸洗金属料'})).toHaveCount(0)
 expect(f.requests).toContain('42');expect(f.requests).toContain('43');expect(f.writes).toEqual([])
})
test('existing acid record opens supplement with its original outbound instead of a new stage',async({page,baseURL})=>{
 const f=await fixture(page,baseURL!),flow=await openFlow(page)
 await flow.locator('.execution-table').getByRole('button',{name:'补料',exact:true}).click();const d=page.getByRole('dialog',{name:'添加出库单',exact:true})
 await expect(d.getByRole('radio',{name:'补料',exact:true})).toBeChecked();await expect(d.getByText('OUT-ACID',{exact:true}).first()).toBeVisible()
 await expect(d.getByRole('combobox',{name:'目标工序',exact:true})).toHaveCount(0);expect(f.writes).toEqual([])
})
