import {test,expect,type Page,type Locator} from '@playwright/test'

async function choose(page:Page,scope:Page|Locator,label:string,text:string){
 const picker=scope.getByRole('combobox',{name:label,exact:true});await picker.press('ArrowDown');await page.locator(`[id="${await picker.getAttribute('aria-controls')}"]`).getByRole('option',{name:text,exact:true}).click()
}
async function setup(page:Page,baseURL:string,unknown=false,allocated=0,prepareLine=true,longName=false){
 const writes:any[]=[];let created=false,version=0,held:any[]=[]
 await page.context().addCookies([{name:'Admin-Token',value:'auto-batch-ui-test',url:baseURL}])
 await page.route('**/*-api/**',async route=>{
  const req=route.request(),p=new URL(req.url()).pathname.replace(/^\/(dev|prod)-api/,''),data=req.postDataJSON();let r:any={code:200,data:[],rows:[],total:0}
  if(req.method()!=='GET')writes.push({p,data})
  if(p==='/getInfo')r={code:200,user:{userId:1,userName:'admin'},roles:['admin'],permissions:['*:*:*']}
  if(p==='/metal/options/factories')r.data=[{id:'101',name:'工厂甲'}]
  if(p==='/metal/storage-orders/options')r.data={warehouses:[{id:'11',name:'原料库'}],customers:[{id:'31',name:'客户甲'}],people:[{id:'9',name:'加工甲',canReceive:true}],productionOrders:[{id:'71',number:'GD71',status:'IN_PROGRESS'},{id:'72',number:'GD72已完成',status:'COMPLETED'},{id:'73',number:'GD73异常完成',status:'EXCEPTION_COMPLETED'},{id:'74',number:'GD74已结清待完工',status:'IN_PROGRESS',productionIssueAllowed:false}],orderItems:[{id:'81',orderId:'71',productName:'产品甲',quantity:100,allocatedQuantity:allocated,remainingQuantity:100-allocated,steps:[{id:'2',sequenceNo:1,processName:'酸洗',measurementMode:'WEIGHT'},{id:'3',sequenceNo:2,processName:'成型',measurementMode:'PIECE'}]}],executions:created?[{id:'91',orderId:'71',batchId:'1011',batchNumber:'PB-AUTO',orderItemId:'81',orderStepId:'2',processorId:'9',processorName:'加工甲',processName:'酸洗',status:'OPEN'}]:[],stocks:[{id:'41',warehouseId:'11',materialId:'51',category:'RAW',netWeight:'100.00',availableWeight:'100.00',measurementMode:'WEIGHT',identity:{code:'M001',name:longName?'原材料候选 / '+('长规格与工序说明 / '.repeat(20)):'原材料候选'},ownerships:[{ownerId:'31',ownerName:'客户甲',weight:'100.00',availableWeight:'100.00'}]}]}
  if(p==='/metal/storage-orders/reservations'){created=true;held=data.lines;version++;if(unknown&&version===1){await route.abort('timedout');return}r.data={sessionId:data.sessionId,version,status:'TEMP',batchId:'1011',executionId:'91',processorId:'9',orderItemId:'81',orderStepId:'2',lines:held}}
  if(p.includes('/reservations/operations/'))r.data={version,status:'TEMP',batchId:'1011',executionId:'91',processorId:'9',orderItemId:'81',orderStepId:'2',lines:held}
  if(p.endsWith('/release'))r.data={status:'RELEASED',version:++version}
  await route.fulfill({json:r})
 })
 await page.goto('/metal/storage-orders');await choose(page,page,'所属工厂','工厂甲');await page.getByRole('button',{name:'添加',exact:true}).click();const dialog=page.getByRole('dialog',{name:'添加出库单',exact:true})
 await choose(page,dialog,'生产工单','GD71');await expect(dialog.getByRole('radio',{name:'生产领料',exact:true})).toBeChecked();await expect(dialog.getByRole('spinbutton',{name:'本批计划件数',exact:true})).toHaveCount(0)
 if(!prepareLine)return {writes,dialog,line:page.getByRole('dialog',{name:'物料明细',exact:true})}
 await expect(dialog.getByText('1 酸洗',{exact:true})).toBeVisible();await choose(page,dialog,'加工人员','加工甲')
 await choose(page,dialog,'仓库/库房','原料库');await page.keyboard.press('Escape');await dialog.getByRole('heading',{name:'物料清单',exact:true}).click();await dialog.getByRole('button',{name:'添加物料',exact:true}).click();const line=page.getByRole('dialog',{name:'物料明细',exact:true});await line.getByRole('combobox',{name:'库存物料',exact:true}).press('ArrowDown');await page.getByRole('option').filter({hasText:'原材料候选'}).click();await line.getByRole('textbox',{name:'实测净重（g）',exact:true}).fill('10')
 return {writes,dialog,line}
}
test('new production issue excludes completed and exception-completed orders',async({page,baseURL})=>{
 const {dialog}=await setup(page,baseURL!,false,0,false)
 await dialog.getByRole('combobox',{name:'生产工单',exact:true}).press('ArrowDown')
 await expect(page.getByRole('option',{name:'GD71',exact:true})).toBeVisible()
 await expect(page.getByRole('option',{name:'GD72已完成',exact:true})).toHaveCount(0)
 await expect(page.getByRole('option',{name:'GD73异常完成',exact:true})).toHaveCount(0)
 await expect(page.getByRole('option',{name:'GD74已结清待完工',exact:true})).toHaveCount(0)
})

test('first issue automatically creates processing without plan, and switching releases it',async({page,baseURL})=>{
 const {writes,dialog,line}=await setup(page,baseURL!);await line.getByRole('button',{name:'确认',exact:true}).click();await expect(line).toBeHidden();const reserve=writes.find(w=>w.p==='/metal/storage-orders/reservations');expect(reserve.data).toMatchObject({executionMode:'NEW',orderId:'71',orderItemId:'81',orderStepId:'2',processorId:'9'});expect(reserve.data.executionId).toBeUndefined()
 expect(reserve.data.plannedQuantity).toBeUndefined();await expect(dialog.getByText('本次领料已建立加工记录，继续添加物料沿用本次加工。',{exact:true})).toHaveCount(0)
 await test.step('switch the visible issue mode',async()=>{await dialog.getByText('补料',{exact:true}).click()});await test.step('confirm clearing the reserved line',async()=>{const confirm=page.getByRole('dialog',{name:'确认变更',exact:true});await expect(confirm).toBeVisible();await confirm.getByRole('button',{name:'确定',exact:true}).click()});await expect(dialog.getByRole('combobox',{name:'原出库单',exact:true})).toBeVisible();expect(writes.filter(w=>w.p.endsWith('/release'))).toHaveLength(1);expect(writes.some(w=>w.p==='/metal/production-batches')).toBe(false)
})
test('unknown reservation recovers original batch without another create request',async({page,baseURL})=>{
 const {writes,dialog,line}=await setup(page,baseURL!,true);await line.getByRole('button',{name:'确认',exact:true}).click();await expect(line.getByRole('button',{name:'确认',exact:true})).toBeDisabled();await line.getByRole('button',{name:'核实预占结果',exact:true}).click();await expect(dialog.getByRole('spinbutton',{name:'本批计划件数',exact:true})).toHaveCount(0);expect(writes.filter(w=>w.p==='/metal/storage-orders/reservations')).toHaveLength(1)
})

test('fully allocated legacy plans do not block automatic production issues',async({page,baseURL})=>{
 const {dialog,line,writes}=await setup(page,baseURL!,false,100,true)
 await expect(dialog.getByRole('spinbutton',{name:'本批计划件数',exact:true})).toHaveCount(0)
 await expect(dialog.getByText('计划份额已分配完',{exact:false})).toHaveCount(0)
 await line.getByRole('button',{name:'确认',exact:true}).click()
 await expect(line).toBeHidden()
 expect(writes.find(w=>w.p==='/metal/storage-orders/reservations').data.plannedQuantity).toBeUndefined()
})

test('receipt selects original outbound and explicitly confirms final material settlement',async({page,baseURL})=>{
 const {dialog:out,writes}=await setup(page,baseURL!,false,0,false)
 await out.getByRole('button',{name:'取消',exact:true}).click()
 await page.getByRole('dialog',{name:'确认关闭',exact:true}).getByRole('button',{name:'放弃',exact:true}).click()
 await page.route('**/metal/storage-orders/options?**',async route=>route.fulfill({json:{code:200,data:{warehouses:[{id:'11',name:'原料库'}],people:[{id:'9',name:'加工甲',canReceive:true,canAccept:true}],productionOrders:[{id:'71',number:'GD71'}],orderItems:[{id:'81',orderId:'71',quantity:100,acceptedProductQuantity:95}],sourceOutboundOrders:[{id:'55',number:'OUT55',available:true,orderId:'71',orderItemId:'81',orderStepId:'3',processorId:'9',executionId:'92',batchId:'1012'}],executions:[{id:'92',orderId:'71',orderItemId:'81',orderStepId:'3',processorId:'9',processorName:'加工甲',processName:'成型',outputCategory:'PRODUCT',measurementMode:'PIECE',status:'OPEN',inputWeight:'100.00'}]}}}))
 await page.getByRole('tab',{name:'入库单',exact:true}).click();await page.getByRole('button',{name:'添加',exact:true}).click()
 const dialog=page.getByRole('dialog',{name:'添加入库单',exact:true})
 await choose(page,dialog,'单据类型','成品入库');await choose(page,dialog,'原出库单','OUT55')
 await expect(dialog.getByText('GD71',{exact:true}).first()).toBeVisible()
 await expect(dialog.getByRole('combobox',{name:'工序加工记录',exact:true})).toHaveCount(0)
 await expect(dialog.locator('.el-descriptions')).toHaveCount(0)
 await expect(dialog.getByRole('textbox',{name:'批次加工人员',exact:true})).toHaveValue('加工甲')
 await expect(dialog.getByRole('combobox',{name:'指定复验人员',exact:true})).toBeVisible()
 await expect(dialog.getByRole('checkbox',{name:'本次加工已结束',exact:true})).not.toBeChecked()
 await dialog.getByText('本次加工已结束',{exact:true}).click()
 await expect(dialog.getByText('本次入库后待核对余额 100.00 g；余料请先还库，实际损耗须明确填写并确认。',{exact:true})).toBeVisible()
 await dialog.getByRole('button',{name:'保存草稿',exact:true}).click()
 await expect(dialog.getByText('请先处理余料，再核对损耗数量、说明及实物确认',{exact:true})).toBeVisible()
 expect(writes).toEqual([])
})

test('long stock option text wraps inside its dropdown',async({page,baseURL})=>{
 const {line}=await setup(page,baseURL!,false,0,true,true)
 await line.getByRole('combobox',{name:'库存物料',exact:true}).press('ArrowDown')
 const option=page.getByRole('option').filter({hasText:'原材料候选'})
 await expect(option).toBeVisible()
 const layout=await option.evaluate((el:HTMLElement)=>({whiteSpace:getComputedStyle(el).whiteSpace,height:el.clientHeight,fits:el.scrollWidth<=el.clientWidth}))
 expect(layout.whiteSpace).toBe('normal');expect(layout.height).toBeGreaterThan(40);expect(layout.fits).toBe(true)
})
