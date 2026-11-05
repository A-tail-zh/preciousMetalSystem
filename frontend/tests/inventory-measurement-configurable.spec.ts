import {test,expect,type Page,type Locator} from '@playwright/test'
import {effectiveMeasurementMode,managesPieces} from '../src/utils/inventory-measurement'
import {stockAvailabilityError} from '../src/utils/stock-availability'
import {stockOptionDetail} from '../src/utils/stock-option-label'
const raw=(piece=true)=>({id:'R',category:'RAW',measurementMode:'PIECE',...(piece?{effectiveMeasurementMode:'PIECE'}:{}),code:'RAW-P',name:'制备原料',referenceId:'M',netWeight:'100.00',availableWeight:'100.00',quantity:10,availableQuantity:10,reservedQuantity:0,warehouseId:'W',factoryId:'F',version:0,ownershipStatus:'NORMAL',materialType:'粉料',organization:'客户',customerId:'C',customerCompanyName:'客户',factoryName:'本厂',warehouseName:'本仓'})
test('RAW effective mode trusts only derived contract; historical mode stays weight',()=>{
 expect(effectiveMeasurementMode(raw())).toBe('PIECE');expect(managesPieces(raw())).toBe(true)
 expect(effectiveMeasurementMode(raw(false))).toBe('WEIGHT');expect(managesPieces({...raw(false),measurementMode:'PIECE'})).toBe(false)
 expect(effectiveMeasurementMode({category:'SEMI',measurementMode:'WEIGHT'})).toBe('WEIGHT');expect(managesPieces({category:'FINISHED'})).toBe(true)
})
test('RAW PIECE checks weight and reserved effective pieces without reference credit',()=>{
 const stock={...raw(),reservedQuantity:2}
 expect(stockAvailabilityError(stock,{inventoryId:'R',netWeight:'1',quantity:9},[])).toContain('数量')
 expect(stockAvailabilityError(stock,{inventoryId:'R',netWeight:'101',quantity:1},[])).toContain('净重')
 expect(stockAvailabilityError(stock,{inventoryId:'R',netWeight:'1',quantity:5},[{inventoryId:'R',netWeight:'1',quantity:4}])).toContain('数量')
 expect(stockAvailabilityError(stock,{inventoryId:'R',netWeight:'1',quantity:8},[])).toBe('')
 expect(stockAvailabilityError({...raw(false),quantity:0},{inventoryId:'R',netWeight:'1',quantity:0,referenceQuantity:1000},[])).toBe('')
})
test('RAW stock label distinguishes effective pieces from historical no-piece stock',()=>{
 expect(stockOptionDetail(raw())).toContain('可领：10件');expect(stockOptionDetail(raw(false))).not.toContain('件')
})
async function setup(page:Page,piece=true,extra:{returning?:boolean;imported?:any[]}={}){
 const posts:any[]=[];await page.context().addCookies([{name:'Admin-Token',value:'measurement-mock',url:'http://127.0.0.1:5197'}])
 await page.route('**/dev-api/**',async route=>{const u=new URL(route.request().url()),p=u.pathname.replace('/dev-api','');let out:any={code:200,data:[],rows:[],total:0}
  if(p==='/getInfo')out={code:200,user:{userId:2,userName:'测量员'},roles:['warehouse'],permissions:['metal:inventory:list','metal:inventory:query','metal:inventory:adjust','metal:warehouse-order:list','metal:warehouse-order:add','metal:warehouse-order:import']}
  else if(p==='/getRouters')out.data=[{path:'/metal',component:'Layout',children:[{path:'inventory',component:'metal/inventory/index',name:'MeasuredRawInventory'},{path:'warehouse-order',component:'metal/warehouse-order/index',name:'MeasuredRawOrder'}]}]
  else if(p==='/metal/inventory/list')out={code:200,rows:[raw(piece)],total:1}
  else if(p==='/metal/inventory/R'||p==='/metal/inventory/R/edit-context')out.data={...raw(piece),ledger:[{id:'L',orderNo:'IN-R',deltaWeight:'100',balanceWeight:'100',deltaQuantity:10,balanceQuantity:10,createTime:'2026-11-04 10:00:00'}]}
  else if(p==='/metal/inventory/R/adjust'||p==='/metal/warehouse-order'||p==='/metal/warehouse-order/outbound-submissions'){if(route.request().method()==='POST'){posts.push(route.request().postDataJSON());out.id='OK'}}
  else if(p==='/metal/warehouse/batch-planning')out.data={factoryId:'F',workOrderId:'O',itemId:'I',processId:'S',measurementMode:'PIECE',availableQuantity:10,canCreate:true,batches:[]}
  else if(p.endsWith('/number/reserve'))out.data='OUT-MOCK'
  else if(p.endsWith('/options/factories'))out.data=[{id:'F',name:'本厂'}]
  else if(p.endsWith('/options/warehouses'))out.data=[{id:'W',name:'本仓'}]
  else if(p.endsWith('/options/people'))out.data=[{id:'U',name:'人员',phone:'13800000000',roleNames:'库管'}]
  else if(p.endsWith('/options/work-orders'))out.data=[{id:'O',name:'订单'}]
  else if(p==='/metal/warehouse/work-order-context')out.data={workOrderId:'O',factoryId:'F',customer:'客户',items:[{id:'I',productId:'P',snapshot:{name:'产品'},plans:[{processId:'S',processName:'下道',sequenceNo:2}]}]}
  else if(p.endsWith('/options/stocks'))out.data=[raw(piece)]
  else if(p.endsWith('/options/batches')&&extra.returning)out.data=[{id:'B',workOrderId:'O',itemId:'I',processId:'S',version:0,completionPolicy:'LEGACY',effectiveMeasurementMode:'PIECE'}]
  else if(p.endsWith('/options/out-lines')&&extra.returning)out.data=[{...raw(piece),id:'L',name:'原出库原料',type:'PRODUCTION_OUT',orderId:'D',orderNo:'OUT-ORIGIN',batchId:'B',batchStatus:'OPEN',remainingQuantity:4,batchReturnableQuantity:4,remainingWeight:'40',batchRemainingWeight:'40',returnableWeight:'40'}]
  else if(p==='/metal/warehouse-order/import')out.data=extra.imported??[]
  await route.fulfill({json:out})
 });return posts
}
async function pick(page:Page,dialog:Locator,label:string,name:string){const f=dialog.locator('.el-form-item').filter({has:page.locator('label').filter({hasText:new RegExp('^'+label+'$')})});await f.locator('.el-select__wrapper').click();const id=await f.getByRole('combobox').getAttribute('aria-controls');await page.locator('[id="'+id+'"]').getByRole('option',{name,exact:true}).click();if(await f.getByRole('combobox').getAttribute('aria-expanded')==='true')await page.keyboard.press('Escape')}
for(const piece of [true,false])test('RAW '+(piece?'PIECE':'legacy')+' inventory list detail ledger and adjustment',async({page})=>{
 const posts=await setup(page,piece);await page.goto('/metal/inventory');const table=page.locator('.warehouse-table')
 await expect(table.locator('thead')).toContainText('净重');if(piece)await expect(table.locator('thead')).toContainText('数量');else await expect(table.locator('thead th')).toHaveCount(7)
 await table.getByRole('button',{name:'查看详情',exact:true}).click();const d=page.getByRole('dialog',{name:'库存管理详情',exact:true})
 if(piece){await expect(d).toContainText('10 件');await expect(d.locator('.stock-ledger-table thead')).toContainText('数量变化')}else await expect(d.locator('.stock-ledger-table thead')).not.toContainText('数量变化')
 await d.locator('.el-dialog__headerbtn').click();await table.getByRole('button',{name:'库存调整',exact:true}).click();const a=page.getByRole('dialog',{name:'库存调整',exact:true})
 if(piece)await expect(a.getByLabel('数量差额',{exact:true})).toBeVisible();else await expect(a.getByLabel('数量差额',{exact:true})).toHaveCount(0)
 await expect(a.getByLabel('参考件数（选填）',{exact:true})).toHaveCount(0)
 await a.getByLabel('净重差额(g)',{exact:true}).fill('-10');if(piece)await a.getByLabel('数量差额',{exact:true}).fill('-1')
 await pick(page,a,'经手人','库管｜人员｜13800000000');await pick(page,a,'仓管员','库管｜人员｜13800000000');await a.getByLabel('调整原因',{exact:true}).fill('实物盘点');await a.getByRole('button',{name:'确认',exact:true}).click()
 await expect.poll(()=>posts.length).toBe(1);expect(posts[0].deltaQuantity).toBe(piece?-1:0);expect(posts[0].referenceQuantity).toBeNull()
})
test('RAW PIECE returns enforce source pieces as well as measured weight',async({page})=>{
 await setup(page,true,{returning:true});await page.goto('/metal/warehouse-order');await page.getByRole('tab',{name:'还库单',exact:true}).click();await page.locator('.toolbar').getByRole('button',{name:'添加',exact:true}).click();const d=page.getByRole('dialog',{name:'添加还库工单',exact:true})
 await pick(page,d,'仓库/库房','本仓');await pick(page,d,'对应出库工单','OUT-ORIGIN');await d.locator('.line-toolbar').getByRole('button',{name:'添加',exact:true}).click();const l=page.getByRole('dialog',{name:'物料明细',exact:true});await pick(page,l,'原出库明细','原出库原料')
 await l.getByLabel('数量(件)',{exact:true}).fill('5');await l.getByLabel('实测净重(g)',{exact:true}).fill('10');await l.getByRole('button',{name:'确认',exact:true}).click();await expect(page.getByText('可还件数不足，请核对来源余额、已产出件数和本单其他明细',{exact:true})).toBeVisible();await expect(l).toBeVisible()
 await l.getByLabel('数量(件)',{exact:true}).fill('3');await l.getByRole('button',{name:'确认',exact:true}).click();await expect(l).toBeHidden();await expect(d.locator('.el-table thead').first()).toContainText('数量')
})
for(const quantity of [3,11])test('imported RAW PIECE quantity '+quantity+' uses actual source balance',async({page})=>{
 const imported={...raw(),inventoryId:'R',netWeight:'30',quantity};await setup(page,true,{imported:[imported]});await page.goto('/metal/warehouse-order');await page.locator('.toolbar').getByRole('button',{name:'添加',exact:true}).click();const d=page.getByRole('dialog',{name:'添加出库工单',exact:true})
 await pick(page,d,'生产工单','订单');await pick(page,d,'产品明细','产品 1');await pick(page,d,'工序','下道');await pick(page,d,'仓库/库房','本仓')
 await d.locator('input[type=file]').setInputFiles({name:'raw-piece.xlsx',mimeType:'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',buffer:Buffer.from('mock parsed workbook')})
 if(quantity===3){await expect(d.locator('.el-table__body').first()).toContainText('3');await expect(d.locator('.el-table thead').first()).toContainText('数量')}else{await expect(page.getByText(/库存不足：数量最多可用/)).toBeVisible();await expect(d.locator('.el-table__body').first().locator('tr')).toHaveCount(0)}
})
for(const piece of [true,false])test('production RAW '+(piece?'PIECE':'legacy')+' line retains actual measurement contract',async({page})=>{
 const posts=await setup(page,piece);await page.goto('/metal/warehouse-order');await page.locator('.toolbar').getByRole('button',{name:'添加',exact:true}).click();const d=page.getByRole('dialog',{name:'添加出库工单',exact:true})
 await pick(page,d,'生产工单','订单');await pick(page,d,'产品明细','产品 1');await pick(page,d,'工序','下道');await pick(page,d,'仓库/库房','本仓')
 await d.locator('.line-toolbar').getByRole('button',{name:'添加',exact:true}).click();const l=page.getByRole('dialog',{name:'取物料明细',exact:true});await l.getByRole('combobox').first().click();const id=await l.getByRole('combobox').first().getAttribute('aria-controls');await page.locator('[id="'+id+'"]').getByRole('option').first().click()
 if(piece){await expect(l.getByLabel('数量(件)',{exact:true})).toBeVisible();await l.getByLabel('数量(件)',{exact:true}).fill('3')}else await expect(l.getByLabel('数量(件)',{exact:true})).toHaveCount(0)
 await expect(l.getByLabel('参考件数（选填）',{exact:true})).toHaveCount(0)
 await l.getByLabel('实测取净重(g)',{exact:true}).fill('30');await l.getByRole('button',{name:'确认',exact:true}).click();await expect(l).toBeHidden()
 if(piece){await expect(d.locator('.el-table thead').first()).toContainText('数量');await expect(d.locator('.el-table__body').first()).toContainText('3')}
 await pick(page,d,'领用人','库管｜人员｜13800000000');await pick(page,d,'仓管员','库管｜人员｜13800000000');await d.getByRole('button',{name:'确认',exact:true}).click();await expect.poll(()=>posts.length).toBe(1)
 expect(posts[0].groups[0].lines[0].quantity).toBe(piece?3:0);expect(posts[0].groups[0].lines[0].referenceQuantity).toBeNull()
})
test('RAW PIECE return import rejects reference counts',async({page})=>{
 await setup(page,true,{returning:true,imported:[{...raw(),sourceLineId:'L',netWeight:'10',quantity:3,referenceQuantity:7}]});await page.goto('/metal/warehouse-order');await page.getByRole('tab',{name:'还库单',exact:true}).click();await page.locator('.toolbar').getByRole('button',{name:'添加',exact:true}).click();const d=page.getByRole('dialog',{name:'添加还库工单',exact:true})
 await pick(page,d,'仓库/库房','本仓');await pick(page,d,'对应出库工单','OUT-ORIGIN');await d.locator('input[type=file]').setInputFiles({name:'return.xlsx',mimeType:'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',buffer:Buffer.from('mock parsed workbook')})
 await expect(page.getByText('按件原料须填写实际件数，不接受参考件数',{exact:true})).toBeVisible();await expect(d.locator('.el-table__body').first().locator('tr')).toHaveCount(0)
})
for(const mode of ['PIECE','WEIGHT'])test('RAW_PROCESS_IN template carries selected '+mode+' batch identity without client mode',async({page})=>{
 await setup(page);let query:URLSearchParams|undefined
 await page.route('**/dev-api/metal/warehouse-order/template**',async route=>{query=new URL(route.request().url()).searchParams;await route.fulfill({status:200,contentType:'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',body:Buffer.from('mock server batch template')})})
 await page.goto('/metal/inventory');await page.locator('.toolbar').getByRole('button',{name:'添加',exact:true}).click();const dialog=page.getByRole('dialog',{name:'添加入库工单',exact:true});await expect(dialog).toBeVisible()
 const batchId='SERVER-BATCH-'+mode
 await page.evaluate(({batchId,mode})=>{
  let component=(document.querySelector('.warehouse-dialog') as any).__vueParentComponent
  while(component&&!String(component.type.__file??'').endsWith('/WarehouseOrderForm.vue'))component=component.parent
  if(!component)throw Error('Existing WarehouseOrderForm fixture did not mount')
  // Seed the server-selected batch context; a client mode must never become a template query input.
  Object.assign(component.setupState.form,{type:'RAW_PROCESS_IN',batchId,taskType:'PRODUCTION',measurementPolicyVersion:3,effectiveMeasurementMode:mode==='PIECE'?'WEIGHT':'PIECE'})
 },{batchId,mode})
 const downloaded=page.waitForEvent('download');await dialog.getByRole('button',{name:'模板下载',exact:true}).click();await downloaded
 expect(query?.get('type')).toBe('RAW_PROCESS_IN');expect(query?.get('factoryId')).toBe('F');expect(query?.get('batchBound')).toBe('true');expect(query?.get('batchId')).toBe(batchId)
 for(const key of ['mode','measurementMode','effectiveMeasurementMode'])expect(query?.has(key)).toBe(false)
})
