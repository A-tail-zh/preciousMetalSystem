import {test,expect,type Page} from '@playwright/test'

test('production flow hides manual creation and the automatic batch hint',async({page,baseURL})=>{
 const {writes}=await setup(page,baseURL!,(path)=>{
  if(path==='/metal/production-order/list')return {code:200,rows:[{id:'71',number:'GD71',status:'IN_PROGRESS'}],total:1}
  if(path.endsWith('/flow'))return {code:200,data:{order:{id:'71',status:'IN_PROGRESS'},items:[],executions:[]}}
 })
 await page.goto('/metal/production-order');await factory(page);await page.getByRole('button',{name:'查看',exact:true}).click()
 const parent=page.getByRole('dialog',{name:'生产工单完整流程',exact:true})
 await expect(parent.getByText('加工批次与物料平衡',{exact:true})).toBeVisible()
 await expect(parent.getByRole('button',{name:'创建加工批次',exact:true})).toHaveCount(0)
 await expect(page.getByRole('dialog',{name:'创建加工批次',exact:true})).toHaveCount(0)
 await expect(parent.getByText(/请在仓储「生产领料出库」时/)).toHaveCount(0)
 await parent.getByRole('button',{name:'刷新流程',exact:true}).click();expect(writes).toHaveLength(0)
})

test('production issue keeps automatic batch fields without selecting an existing batch',async({page,baseURL})=>{
 const {writes}=await setup(page,baseURL!,(path)=>path==='/metal/storage-orders/options'?{code:200,data:{productionOrders:[{id:'71',number:'GD71'}],orderItems:[{id:'81',orderId:'71',productName:'产品甲',steps:[{id:'2',sequenceNo:2,processName:'工序002',measurementMode:'WEIGHT'}]}],people:[{id:'9',name:'加工甲',canReceive:true}]}}:undefined)
 await page.goto('/metal/storage-orders');await factory(page);await page.getByRole('button',{name:'添加',exact:true}).click()
 const dialog=page.getByRole('dialog',{name:'添加出库单',exact:true})
 await dialog.getByRole('combobox',{name:'生产工单',exact:true}).press('ArrowDown');await page.getByRole('option',{name:'GD71',exact:true}).click()
 await dialog.getByRole('combobox',{name:'产品组',exact:true}).press('ArrowDown');await page.getByRole('option',{name:'产品甲 / 81',exact:true}).click()
 await dialog.getByRole('combobox',{name:'目标工序',exact:true}).press('ArrowDown');await page.getByRole('option',{name:'2 工序002',exact:true}).click()
 await dialog.getByRole('combobox',{name:'加工人员',exact:true}).press('ArrowDown');await page.getByRole('option',{name:'加工甲',exact:true}).click()
 await expect(dialog.getByRole('combobox',{name:'加工批次',exact:true})).toHaveCount(0)
 await expect(dialog.getByText('加工批次（可空=出库自动建批）',{exact:true})).toHaveCount(0);expect(writes).toHaveLength(0)
})

for(const original of ['8',''])test(`unused batch edits personnel and planned quantity with current version original=${original||'empty'}`,async({page,baseURL})=>{
 const batch:any={id:'91',orderStepId:'2',processorId:original,processorName:'仓管甲',processName:'工序002',status:'OPEN',canEdit:true,canCancel:true,started:false,plannedQuantity:50,measurementMode:'PIECE',version:2}
 const flow=()=>({order:{id:'71',status:'IN_PROGRESS'},items:[],executions:[batch]})
 const {writes}=await setup(page,baseURL!,(path,method,body)=>{
  if(path==='/metal/production-order/list')return {code:200,rows:[{id:'71',number:'GD71'}],total:1}
  if(path.endsWith('/flow'))return {code:200,data:flow()}
  if(path==='/metal/storage-orders/options')return {code:200,data:{people:[{id:'8',name:'仓管甲',canReceive:false},{id:'9',name:'加工甲',canReceive:true}]}}
  if(path==='/metal/production-order/executions/91'&&method==='PUT'){Object.assign(batch,{processorId:body.processorId,plannedQuantity:body.plannedQuantity,processorName:'加工甲',version:3});return {code:200,data:batch}}
 })
 await page.goto('/metal/production-order');await factory(page);await page.getByRole('button',{name:'查看',exact:true}).click();const parent=page.getByRole('dialog',{name:'生产工单完整流程',exact:true});await parent.getByRole('button',{name:'编辑',exact:true}).click();const dialog=page.getByRole('dialog',{name:'编辑加工批次',exact:true});await dialog.getByRole('spinbutton').waitFor();await dialog.getByRole('button',{name:'保存',exact:true}).click();await expect(dialog.getByText(original?'所选加工人员不可用或没有领料复验权限，请重新选择':'请选择加工人员',{exact:true})).toBeVisible();expect(writes).toHaveLength(0);
 await dialog.getByRole('combobox',{name:'加工人员',exact:true}).press('ArrowDown');if(original)await expect(page.getByRole('option',{name:'仓管甲',exact:true})).toHaveAttribute('aria-disabled','true');await page.getByRole('option',{name:'加工甲',exact:true}).click();await dialog.getByRole('spinbutton').fill('25');await dialog.getByRole('textbox',{name:/修改原因/}).fill('   ');await dialog.getByRole('button',{name:'保存',exact:true}).click();await expect(dialog.getByText('请填写修改原因',{exact:true})).toBeVisible();expect(writes).toHaveLength(0);await expect(dialog.getByRole('spinbutton')).toHaveValue('25');
 await dialog.getByRole('textbox',{name:/修改原因/}).fill('指定人员错误');await dialog.getByRole('button',{name:'保存',exact:true}).click();await expect(dialog).toBeHidden();expect(writes).toHaveLength(1);expect(writes[0]).toMatchObject({method:'PUT',path:'/metal/production-order/executions/91',body:{factoryId:'101',version:2,processorId:'9',plannedQuantity:25,reason:'指定人员错误'}});expect(writes[0].body.orderStepId).toBeUndefined();await expect(parent.getByRole('cell',{name:'加工甲',exact:true})).toBeVisible()
})

test('invalid fixed production reviewer displays person name and blocks submission',async({page,baseURL})=>{
 const {writes}=await setup(page,baseURL!,(path)=>path==='/metal/storage-orders/options'?{code:200,data:{productionOrders:[{id:'71',number:'GD71'}],executions:[{id:'91',orderId:'71',processorId:'8',processorName:'仓管甲',processName:'工序002',status:'OPEN'}],people:[{id:'8',name:'仓管甲',canReceive:false}]}}:undefined)
 await page.goto('/metal/storage-orders');await factory(page);await page.getByRole('button',{name:'添加',exact:true}).click();const dialog=page.getByRole('dialog',{name:'添加出库单',exact:true});await dialog.getByRole('combobox',{name:'生产工单',exact:true}).press('ArrowDown');await page.getByRole('option',{name:'GD71',exact:true}).click();await dialog.getByRole('combobox',{name:'加工批次',exact:true}).press('ArrowDown');await page.getByRole('option').filter({hasText:'工序002'}).click();await expect(dialog.getByText('仓管甲',{exact:true})).toBeVisible();await expect(dialog.getByText('批次加工人员没有有效的领料复验权限，请先编辑批次调整人员',{exact:true})).toBeVisible();await dialog.getByRole('button',{name:'保存草稿',exact:true}).click();expect(writes).toHaveLength(0)
})

for(const type of ['PRODUCTION_OUT','PRODUCT_OUT'])test('stock selector category filter '+type,async({page,baseURL})=>{
 await setup(page,baseURL!,(path)=>path==='/metal/storage-orders/options'?{code:200,data:{warehouses:[warehouse],stocks:['RAW','SEMI','PRODUCT'].map((category,i)=>({...stock,id:String(41+i),category,identity:{name:'同名物料'},batchNo:'ST'+i}))}}:undefined)
 await page.goto('/metal/storage-orders');await factory(page);await page.getByRole('button',{name:'添加',exact:true}).click();const order=page.getByRole('dialog',{name:'添加出库单',exact:true});if(type==='PRODUCT_OUT'){await order.getByRole('combobox',{name:'单据类型',exact:true}).press('ArrowDown');await page.getByRole('option',{name:'产品出库',exact:true}).click()}await order.getByRole('combobox',{name:'仓库/库房',exact:true}).press('ArrowDown');await page.getByRole('option',{name:'原料库',exact:true}).click();await page.keyboard.press('Escape');await order.getByRole('button',{name:'添加物料',exact:true}).click();const dialog=page.getByRole('dialog',{name:'物料明细',exact:true});await dialog.getByRole('combobox',{name:'库存物料',exact:true}).press('ArrowDown');for(const label of ['【原料】','【半成品】','【成品】'])await expect(page.getByRole('option').filter({hasText:label})).toHaveCount((type==='PRODUCT_OUT')===(label==='【成品】')?1:0)
})

for(const mode of ['WEIGHT','PIECE','EMPTY','ABSENT'])test(`source table expands on demand and factory zero selection ${mode}`,async({page,baseURL})=>{
 const ownerships=mode==='ABSENT'?[]:mode==='EMPTY'?[{ownerId:0,weight:'10.00',quantity:0}]:[{ownerId:0,weight:'10.00',quantity:10},{ownerId:8,ownerName:'客户甲',weight:'10.00',quantity:10}]
 const {writes}=await setup(page,baseURL!,(path)=>path==='/metal/storage-orders/options'?{code:200,data:{warehouses:[warehouse],stocks:[{...stock,measurementMode:mode==='WEIGHT'?'WEIGHT':'PIECE',ownerships}]}}:undefined)
 await page.goto('/metal/storage-orders');await factory(page);await page.getByRole('button',{name:'添加',exact:true}).click()
 const editor=page.getByRole('dialog',{name:'添加出库单',exact:true});await editor.getByRole('combobox',{name:'仓库/库房',exact:true}).press('ArrowDown');await page.getByRole('option',{name:'原料库',exact:true}).click();await page.keyboard.press('Escape');await editor.getByRole('button',{name:'添加物料',exact:true}).click()
 const line=page.getByRole('dialog',{name:'物料明细',exact:true});await line.getByRole('combobox',{name:'库存物料',exact:true}).press('ArrowDown');await page.getByRole('option').filter({hasText:'10.00g'}).click()
 await expect(line.getByRole('heading',{name:'领料来源分配',exact:true})).toHaveCount(0);await expect(line.getByText(/本客户及工厂料不足/)).toHaveCount(0)
 await line.getByRole('button',{name:'添加来源',exact:true}).click();await expect(line.getByRole('heading',{name:'领料来源分配',exact:true})).toBeVisible()
 await line.getByRole('combobox',{name:'来源料权方',exact:true}).press('ArrowDown')
 if(['EMPTY','ABSENT'].includes(mode)){await expect(page.getByRole('option',{name:'工厂自有（当前批次无可用库存）',exact:true})).toHaveAttribute('aria-disabled','true');await page.keyboard.press('Escape')}
 else{
  await page.getByRole('option',{name:mode==='WEIGHT'?'工厂自有（10.00g）':'工厂自有（10件 / 10.00g）',exact:true}).click()
  await line.getByRole('textbox',{name:'实测净重（g）',exact:true}).fill('1.00');if(mode==='PIECE')await line.getByRole('textbox',{name:'数量（件）',exact:true}).fill('1')
  await line.locator('.allocation-table .el-input__inner').nth(0).fill('1.00');if(mode==='PIECE')await line.locator('.allocation-table .el-input__inner').nth(1).fill('1')
  await line.getByRole('button',{name:'确认',exact:true}).click();await expect(line).toBeHidden();await editor.getByRole('button',{name:'编辑',exact:true}).click();await expect(line.getByRole('heading',{name:'领料来源分配',exact:true})).toBeVisible();await expect(line.locator('.allocation-table').getByText(mode==='WEIGHT'?'工厂自有（10.00g）':'工厂自有（10件 / 10.00g）',{exact:true})).toBeVisible()
 }
 await line.getByRole('button',{name:'添加来源',exact:true}).click();await expect(line.getByRole('combobox',{name:'来源料权方',exact:true})).toHaveCount(2)
 await line.getByRole('button',{name:'移除',exact:true}).first().click();await line.getByRole('button',{name:'移除',exact:true}).click();await expect(line.getByRole('heading',{name:'领料来源分配',exact:true})).toHaveCount(0);await expect(line.getByRole('button',{name:'添加来源',exact:true})).toBeVisible();expect(writes).toHaveLength(0)
})

const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/
const warehouse={id:'11',factoryId:'101',name:'原料库',sortOrder:0,status:'0',version:0}
const material={id:'21',code:'M21',name:'原料',measurementMode:'WEIGHT',materialForm:'METAL'}
const stock={id:'41',factoryId:'101',warehouseId:'11',category:'RAW',netWeight:'10.00',measurementMode:'WEIGHT',identity:{code:'M21',name:'原料'}}
const document={id:'31',factoryId:'101',number:'IN31',type:'RAW_IN',kind:'IN',status:'PENDING_REVIEW',creatorId:'8',reviewerId:'1',version:1,reviewRound:1,lines:[{id:'51',warehouseId:'11',name:'原料',netWeight:'10.00',measurementMode:'WEIGHT'}]}

test.beforeEach(async({page})=>{
  // Keep the regression effective even when run on a secure localhost origin.
  await page.addInitScript(()=>Object.defineProperty(crypto,'randomUUID',{value:undefined,configurable:true}))
})

async function setup(page:Page,baseURL:string,override?:(path:string,method:string,body:any)=>any){
  const writes:{path:string;method:string;body:any}[]=[],errors:string[]=[]
  page.on('pageerror',e=>errors.push(e.message))
  page.on('console',m=>{if(m.type()==='error'&&m.text().includes('randomUUID'))errors.push(m.text())})
  await page.context().addCookies([{name:'Admin-Token',value:'storage-lan-mock',url:baseURL}])
  await page.route('**/*-api/**',async route=>{
    const req=route.request(),path=new URL(req.url()).pathname.replace(/^\/(dev|prod)-api/,''),method=req.method(),data=req.postDataJSON()
    if(method!=='GET'){writes.push({path,method,body:data});if(path!=='/metal/customer')expect(data.requestId).toMatch(uuid)}
    let body:any={code:200,data:[],rows:[],total:0}
    if(path==='/getInfo')body={code:200,user:{userId:1,userName:'admin'},roles:['admin'],permissions:['*:*:*']}
    if(path==='/metal/options/factories')body={code:200,data:[{id:'101',name:'本厂'}]}
    if(path==='/metal/storage-review-tasks/count')body={code:200,data:{count:1}}
    if(path==='/metal/storage-review-tasks/list')body={code:200,rows:[{id:'61',documentId:'31',documentNumber:'IN31',version:1,reviewRound:1}],total:1}
    if(path==='/metal/storage-orders/31')body={code:200,data:document}
    if(path==='/metal/storage-review-tasks/61')body={code:200,data:{id:'61',version:1,reviewRound:1,document,reviewers:[{id:'9',name:'仓管员'}]}}
    if(path==='/metal/storage-warehouses/options')body={code:200,data:[warehouse]}
    if(path==='/metal/storage-inventory/list')body={code:200,rows:[stock],total:1}
    if(path==='/metal/storage-orders/options')body={code:200,data:{warehouses:[warehouse],materials:[material],stocks:[stock],people:[{id:'9',name:'仓管员',canAccept:true,canReceive:true}]}}
    if(method!=='GET')body={code:200,data:{id:'31',version:1}}
    body=override?.(path,method,data)??body
    await route.fulfill({json:body})
  })
  return {writes,errors}
}

async function factory(page:Page){
  await page.getByRole('combobox',{name:'所属工厂',exact:true}).press('ArrowDown')
  await page.getByRole('option',{name:'本厂',exact:true}).click()
}

test('HTTP warehouse create, edit and delete send valid independent request IDs',async({page,baseURL})=>{
  let rows:any[]=[]
  const {writes,errors}=await setup(page,baseURL!,(path,method,data)=>{
    if(path==='/metal/storage-warehouses/list')return {code:200,rows,total:rows.length}
    if(path==='/metal/storage-warehouses/11'&&method==='GET')return {code:200,data:rows[0]}
    if(method==='POST'&&path==='/metal/storage-warehouses'){const {requestId,...fields}=data;rows=[{...warehouse,...fields,id:'11'}];return {code:200,data:rows[0]}}
    if(method==='PUT'){const {requestId,...fields}=data;rows=[{...rows[0],...fields,version:1}];return {code:200,data:rows[0]}}
    if(path.endsWith('/batch-delete')){rows=[];return {code:200,data:{deleted:true}}}
  })
  await page.goto('/metal/storage-warehouses');await factory(page)
  await page.getByRole('button',{name:'添加',exact:true}).click()
  let dialog=page.getByRole('dialog',{name:'添加仓库'})
  await dialog.getByRole('textbox',{name:'仓库名称',exact:true}).fill('局域网仓库')
  await dialog.getByRole('button',{name:'确认',exact:true}).click()
  await expect(dialog).toBeHidden()
  await page.getByRole('row').filter({hasText:'局域网仓库'}).getByRole('button',{name:'编辑',exact:true}).click()
  dialog=page.getByRole('dialog',{name:'编辑仓库'})
  await dialog.getByRole('textbox',{name:'仓库名称',exact:true}).fill('已修改仓库')
  await dialog.getByRole('button',{name:'确认',exact:true}).click()
  await expect(dialog).toBeHidden()
  await page.getByRole('row').filter({hasText:'已修改仓库'}).getByRole('button',{name:'删除',exact:true}).click()
  await page.getByRole('dialog',{name:'确认删除'}).getByRole('button',{name:'确定',exact:true}).click()
  await expect(page.getByRole('cell',{name:'已修改仓库',exact:true})).toHaveCount(0)
  expect(writes.map(w=>w.method)).toEqual(['POST','PUT','POST'])
  expect(new Set(writes.map(w=>w.body.requestId)).size).toBe(3)
  expect(errors).toEqual([])
})

test('HTTP inventory adjustment opens its existing stock line and saves a draft',async({page,baseURL})=>{
  const {writes,errors}=await setup(page,baseURL!)
  await page.goto('/metal/storage-inventory');await factory(page)
  await page.getByRole('row').filter({hasText:'M21'}).getByRole('button',{name:'库存调整',exact:true}).click()
  const dialog=page.getByRole('dialog',{name:'添加库存调整'})
  await expect(dialog).toBeVisible()
  await dialog.getByRole('button',{name:'保存草稿',exact:true}).click()
  await expect(dialog).toBeHidden()
  expect(writes).toHaveLength(1)
  expect(writes[0].body.type).toBe('ADJUSTMENT')
  expect(writes[0].body.lines[0].localId).toMatch(uuid)
  expect(writes[0].body.lines[0].stockId).toBe('41')
  expect(errors).toEqual([])
})

for(const pass of [true,false])test(`HTTP assigned review ${pass?'posts':'returns'} with a valid request ID`,async({page,baseURL})=>{
  const {writes,errors}=await setup(page,baseURL!)
  await page.goto('/metal/storage-warehouses');await factory(page)
  await page.getByRole('button',{name:/待复验消息/}).click()
  await page.getByRole('button').filter({hasText:'IN31'}).click()
  const dialog=page.getByRole('dialog',{name:'单据复验'})
  await expect(dialog).toBeVisible()
  if(!pass)await dialog.getByRole('textbox').last().fill('重新核对净重')
  await dialog.getByRole('button',{name:pass?'确认过账':'退回',exact:true}).click()
  await expect(dialog).toBeHidden()
  expect(writes).toHaveLength(1)
  expect(writes[0].path).toBe('/metal/storage-orders/31/'+(pass?'review':'return'))
  expect(writes[0].body.reviewRound).toBe(1)
  expect(errors).toEqual([])
})

test('HTTP review reassignment sends the original task round',async({page,baseURL})=>{
  const {writes,errors}=await setup(page,baseURL!)
  await page.goto('/metal/storage-warehouses');await factory(page)
  await page.getByRole('button',{name:/待复验消息/}).click()
  const drawer=page.getByRole('dialog',{name:'待复验消息',exact:true})
  await drawer.getByRole('combobox').last().press('ArrowDown')
  await page.getByRole('option',{name:'本厂待复验管理',exact:true}).click()
  await drawer.getByRole('button',{name:'转派',exact:true}).click()
  const dialog=page.getByRole('dialog',{name:'转派复验任务'})
  await dialog.getByRole('combobox').press('ArrowDown')
  await page.getByRole('option',{name:'仓管员',exact:true}).click()
  await dialog.getByRole('textbox').fill('换班交接')
  await dialog.getByRole('button',{name:'确认转派',exact:true}).click()
  await expect(dialog).toBeHidden()
  expect(writes).toHaveLength(1)
  expect(writes[0].path).toBe('/metal/storage-review-tasks/61/reassign')
  expect(writes[0].body).toMatchObject({reviewerId:'9',reviewRound:1,reason:'换班交接'})
  expect(errors).toEqual([])
})

test('review reassignment uses task-specific candidates without general warehouse options',async({page,baseURL})=>{
 const {writes}=await setup(page,baseURL!,(path)=>path==='/metal/storage-review-tasks/61'?{code:200,data:{id:'61',version:1,reviewRound:1,document,reviewers:[{id:'10',name:'可复验仓管'}]}}:path==='/metal/storage-orders/options'?{code:403,msg:'没有仓储列表权限'}:undefined)
 await page.goto('/metal/storage-warehouses');await factory(page);await page.getByRole('button',{name:/待复验消息/}).click();const drawer=page.getByRole('dialog',{name:'待复验消息',exact:true});await drawer.getByRole('combobox').last().press('ArrowDown');await page.getByRole('option',{name:'本厂待复验管理',exact:true}).click();await drawer.getByRole('button',{name:'转派',exact:true}).click();const dialog=page.getByRole('dialog',{name:'转派复验任务'});await expect(dialog.getByRole('combobox')).toBeEnabled();await dialog.getByRole('combobox').press('ArrowDown');await expect(page.getByRole('option',{name:'可复验仓管',exact:true})).toBeVisible();await expect(page.getByRole('option',{name:'原单发起人',exact:true})).toBeVisible();await expect(page.getByRole('option',{name:'只有领料权限',exact:true})).toHaveCount(0);expect(writes).toHaveLength(0)
})

test('production issue review stays assigned to its batch processor',async({page,baseURL})=>{
 const {writes}=await setup(page,baseURL!,(path)=>path==='/metal/storage-review-tasks/list'?{code:200,rows:[{id:'61',documentId:'31',documentNumber:'OUT31',type:'PRODUCTION_OUT',version:1,reviewRound:1}],total:1}:undefined)
 await page.goto('/metal/storage-warehouses');await factory(page);await page.getByRole('button',{name:/待复验消息/}).click();const drawer=page.getByRole('dialog',{name:'待复验消息',exact:true});await drawer.getByRole('combobox').last().press('ArrowDown');await page.getByRole('option',{name:'本厂待复验管理',exact:true}).click();await expect(drawer.getByText('领料复验',{exact:true})).toBeVisible();await expect(drawer.getByRole('button',{name:'转派',exact:true})).toBeDisabled();expect(writes).toHaveLength(0)
})

test('HTTP edited draft replaces stale request ID returned in document data',async({page,baseURL})=>{
  const staleId='12345678-1234-4234-8234-123456789abc'
  const draft={...document,status:'DRAFT',creatorId:'1',reviewerId:'9',warehouseIds:['11'],requestId:staleId}
  const {writes,errors}=await setup(page,baseURL!,(path,method)=>{
    if(path==='/metal/storage-orders/list')return {code:200,rows:[draft],total:1}
    if(path==='/metal/storage-orders/31'&&method==='GET')return {code:200,data:draft}
  })
  await page.goto('/metal/storage-orders');await factory(page)
  await page.getByRole('row').filter({hasText:'IN31'}).getByRole('button',{name:'编辑',exact:true}).click()
  const dialog=page.getByRole('dialog',{name:'编辑入库单'})
  await dialog.getByRole('button',{name:'保存草稿',exact:true}).click()
  await expect(dialog).toBeHidden()
  expect(writes).toHaveLength(1)
  expect(writes[0].body.requestId).not.toBe(staleId)
  expect(errors).toEqual([])
})

test('HTTP CSV import creates line UUIDs and draft submission uses separate keys',async({page,baseURL})=>{
  const {writes,errors}=await setup(page,baseURL!)
  await page.goto('/metal/storage-orders');await factory(page)
  await page.getByRole('tab',{name:'入库单',exact:true}).click()
  await page.getByRole('button',{name:'添加',exact:true}).click()
  const dialog=page.getByRole('dialog',{name:'添加入库单'})
  await dialog.getByRole('combobox',{name:'指定复验人员',exact:true}).press('ArrowDown')
  await page.getByRole('option',{name:'仓管员',exact:true}).click()
  await dialog.getByRole('combobox',{name:'仓库/库房',exact:true}).press('ArrowDown')
  await page.getByRole('option',{name:'原料库',exact:true}).click()
  const download=page.waitForEvent('download')
  await dialog.getByRole('button',{name:'下载模板',exact:true}).click()
  const file=await download,stream=await file.createReadStream(),chunks:Buffer[]=[]
  for await(const chunk of stream!)chunks.push(chunk)
  const header=Buffer.concat(chunks).toString('utf8').trimEnd()
  const row=['','21','','','0','12.34','','','','','METAL','','','局域网导入'].join(',')
  await dialog.locator('input[type=file]').setInputFiles({name:'来料.csv',mimeType:'text/csv',buffer:Buffer.from(header+'\r\n'+row+'\r\n')})
  await expect(dialog.getByRole('cell',{name:'12.34',exact:true})).toBeVisible()
  await dialog.getByRole('button',{name:'提交复验',exact:true}).click()
  await expect(page.getByRole('dialog',{name:/入库单/})).toBeHidden()
  expect(writes.map(w=>w.path)).toEqual(['/metal/storage-orders','/metal/storage-orders/31/submit'])
  expect(writes[0].body.lines[0].localId).toMatch(uuid)
  expect(writes[0].body.lines[0]).not.toHaveProperty('externalSource')
  expect(writes[0].body.requestId).not.toBe(writes[1].body.requestId)
  expect(errors).toEqual([])
})

test('HTTP unknown save retries the same frozen request after verification',async({page,baseURL})=>{
  const {errors}=await setup(page,baseURL!)
  const submitted:any[]=[]
  await page.route('**/*-api/metal/storage-warehouses**',async route=>{
    const path=new URL(route.request().url()).pathname
    if(route.request().method()==='POST'){
      submitted.push(route.request().postDataJSON())
      if(submitted.length===1){await route.abort();return}
      await route.fulfill({json:{code:200,data:warehouse}});return
    }
    await route.fulfill({json:path.includes('/operations/')?{code:404,msg:'尚未查到此次操作'}:{code:200,rows:[],total:0}})
  })
  await page.goto('/metal/storage-warehouses');await factory(page)
  await page.getByRole('button',{name:'添加',exact:true}).click()
  const dialog=page.getByRole('dialog',{name:'添加仓库'})
  await dialog.getByRole('textbox',{name:'仓库名称',exact:true}).fill('原请求仓库')
  await dialog.getByRole('button',{name:'确认',exact:true}).click()
  await dialog.getByRole('button',{name:'核实保存结果',exact:true}).click()
  await dialog.getByRole('button',{name:'重试原请求',exact:true}).click()
  await expect(dialog).toBeHidden()
  expect(submitted).toHaveLength(2)
  expect(submitted[0].requestId).toMatch(uuid)
  expect(submitted[1]).toEqual(submitted[0])
  expect(errors).toEqual([])
})


test('inventory physical ledger displays document and deltas without duplicating ownership entries',async({page,baseURL})=>{
  const entries=[
    {id:'1',documentId:'31',documentNumber:'IN31',ledger:'PHYSICAL',action:'INBOUND',weightDelta:'10.00',quantityDelta:0,createTime:'2026-11-10T10:59:10'},
    {id:'2',documentId:'31',documentNumber:'IN31',ledger:'OWNERSHIP',action:'INBOUND',weightDelta:'10.00',quantityDelta:0,createTime:'2026-11-10T10:59:10'},
    {id:'3',documentId:'32',documentNumber:'OUT32',ledger:'PHYSICAL',action:'ISSUE',weightDelta:'-2.50',quantityDelta:0,createTime:'2026-11-10T11:00:00'}
  ]
  const {errors}=await setup(page,baseURL!,(path)=>{
    if(path==='/metal/storage-inventory/41')return {code:200,data:{...stock,ownerships:[]}}
    if(path==='/metal/storage-inventory/41/ledger')return {code:200,data:entries}
  })
  await page.goto('/metal/storage-inventory');await factory(page)
  await page.getByRole('row').filter({hasText:'M21'}).getByRole('button',{name:'查看详情',exact:true}).click()
  const table=page.getByRole('dialog',{name:'库存详情'}).locator('.physical-ledger')
  await expect(table.locator('tbody tr')).toHaveCount(2)
  await expect(table.getByRole('cell',{name:'IN31',exact:true})).toHaveCount(1)
  for(const name of ['入库','生产领料','10.00','-2.50','2026-11-10 10:59:10'])await expect(table.getByRole('cell',{name,exact:true})).toBeVisible()
  expect(errors).toEqual([])
})

test('pending review dot clears when pending tasks reach zero',async({page,baseURL})=>{
  let count=1
  await setup(page,baseURL!,(path)=>path==='/metal/storage-review-tasks/count'?{code:200,data:{count}}:undefined)
  await page.goto('/metal/storage-warehouses')
  const dot=page.locator('.pending-review-badge .is-dot')
  await expect(dot).toBeHidden()
  await factory(page);await expect(dot).toBeVisible()
  count=0
  await page.evaluate(()=>window.dispatchEvent(new Event('storage-documents-changed')))
  await expect(dot).toBeHidden()
})


for(const mode of ['WEIGHT','PIECE'])test(`review source labels and ${mode} quantity fields display correctly`,async({page,baseURL})=>{
 const row={...document,type:'PRODUCTION_OUT',lines:[{id:'51',code:'M21',name:'旧料',measurementMode:mode,netWeight:'10.00',...(mode==='PIECE'?{quantity:2}:{}),allocations:[{ownerId:'8',ownerName:'客户甲',weight:'10.00',...(mode==='PIECE'?{quantity:2}:{})}]}]}
 const {errors}=await setup(page,baseURL!,(path)=>path==='/metal/storage-orders/31'?{code:200,data:row}:undefined)
 await page.goto('/metal/storage-warehouses');await factory(page)
 await page.getByRole('button',{name:/待复验消息/}).click();await page.getByRole('button').filter({hasText:'IN31'}).click()
 const dialog=page.getByRole('dialog',{name:'单据复验'})
 await expect(dialog.getByText('旧料',{exact:true})).toBeVisible()
 await expect(dialog.getByRole('columnheader',{name:'参考完工件数'})).toHaveCount(0)
 await dialog.locator('.el-table__expand-icon').first().click()
 const sources=dialog.locator('.review-sources')
 await expect(sources.getByRole('cell',{name:'客户甲',exact:true})).toBeVisible()
 await expect(sources.locator('input')).toHaveCount(0)
 await expect(sources.getByRole('combobox')).toHaveCount(0)
 await dialog.screenshot({path:test.info().outputPath('review-'+mode+'.png')})
 expect(errors).toEqual([])
})

for(const type of ['SEMI_IN','EXTERNAL_SEMI_IN'])test(`HTTP ${type} draft keeps only its applicable source fields`,async({page,baseURL})=>{
  const externalSource={sourceReference:'外来凭据',completedProcessIds:['71']}
  const draft={...document,type,status:'DRAFT',creatorId:'1',reviewerId:'9',warehouseIds:['11'],orderId:type==='SEMI_IN'?'81':null,executionId:type==='SEMI_IN'?'91':null,lines:[{...document.lines[0],productId:'21',externalSource}]}
  const {writes,errors}=await setup(page,baseURL!,(path,method)=>{
    if(path==='/metal/storage-orders/list')return {code:200,rows:[draft],total:1}
    if(path==='/metal/storage-orders/31'&&method==='GET')return {code:200,data:draft}
  })
  await page.goto('/metal/storage-orders');await factory(page)
  await page.getByRole('row').filter({hasText:'IN31'}).getByRole('button',{name:'编辑',exact:true}).click()
  const dialog=page.getByRole('dialog',{name:'编辑入库单'})
  await dialog.getByRole('button',{name:'保存草稿',exact:true}).click()
  await expect(dialog).toBeHidden();expect(writes).toHaveLength(1)
  if(type==='SEMI_IN'){
    expect(writes[0].body.lines[0]).not.toHaveProperty('externalSource')
    expect(writes[0].body).toMatchObject({orderId:'81',executionId:'91'})
  }else expect(writes[0].body.lines[0].externalSource).toEqual(externalSource)
  expect(errors).toEqual([])
})

 test('optional purity input accepts blanks and reports the actual invalid field',async({page,baseURL})=>{
  const {writes,errors}=await setup(page,baseURL!)
  await page.goto('/metal/storage-orders');await factory(page)
  await page.getByRole('tab',{name:'入库单',exact:true}).click()
  await page.getByRole('button',{name:'添加',exact:true}).click()
  const dialog=page.getByRole('dialog',{name:'添加入库单'})
  await dialog.getByRole('combobox',{name:'仓库/库房',exact:true}).press('ArrowDown')
  await page.getByRole('option',{name:'原料库',exact:true}).click()
  await dialog.getByRole('button',{name:'添加物料',exact:true}).click()
  const line=page.getByRole('dialog',{name:'物料明细',exact:true})
  await line.getByRole('combobox',{name:'入库物料',exact:true}).press('ArrowDown')
  await page.getByRole('option',{name:'【原料】 M21 / 原料',exact:true}).click()
  await line.getByRole('textbox',{name:'实测净重（g）',exact:true}).fill('10.00')
  for(const value of ['-1','100.01','1.001','abc']){
    await line.getByRole('textbox',{name:'纯度（%）',exact:true}).fill(value)
    await line.getByRole('button',{name:'确认',exact:true}).click()
    await expect(line.getByRole('alert')).toHaveText('纯度须为0至100，最多两位小数')
  }
  await line.getByRole('textbox',{name:'纯度（%）',exact:true}).fill('')
  await line.getByRole('button',{name:'确认',exact:true}).click()
  await expect(line).toBeHidden()
  await dialog.getByRole('button',{name:'保存草稿',exact:true}).click()
  await expect(dialog).toBeHidden();expect(writes[0].body.lines[0].purity).toBe('');expect(errors).toEqual([])
 })

for(const [type,mode] of [['PRODUCTION_OUT','PIECE'],['SEMI_IN','WEIGHT'],['RAW_RETURN','WEIGHT'],['PRODUCT_IN','PIECE'],['PRODUCT_IN','WEIGHT']])test(`reference completion column applies only to weighted finished receipt ${type} ${mode}`,async({page,baseURL})=>{
 const applicable=type==='PRODUCT_IN'&&mode==='WEIGHT',kind=type==='PRODUCTION_OUT'?'OUT':type==='RAW_RETURN'?'RET':'IN'
 const draft={...document,type,kind,status:'DRAFT',creatorId:'1',reviewerId:'9',warehouseIds:['11'],executionId:'91',lines:[{...document.lines[0],measurementMode:mode,referenceQuantity:7,quantity:mode==='PIECE'?7:null}]}
 const {writes}=await setup(page,baseURL!,(path)=>{
  if(path==='/metal/storage-orders/options')return {code:200,data:{warehouses:[warehouse],stocks:[stock],people:[{id:'9',name:'仓管员',canAccept:true}],executions:[{id:'91',measurementMode:'WEIGHT'}]}}
  if(path==='/metal/storage-orders/list')return {code:200,rows:[draft],total:1}
  if(path==='/metal/storage-orders/31')return {code:200,data:draft}
 })
 await page.goto('/metal/storage-orders');await factory(page);await page.getByRole('button',{name:'物料清单',exact:true}).click();const detail=page.getByRole('dialog',{name:'物料清单',exact:true});await expect(detail.getByRole('columnheader',{name:'参考完工件数',exact:true})).toHaveCount(applicable?1:0);await detail.getByRole('button',{name:'关闭此对话框',exact:true}).click()
 await page.getByRole('row').filter({hasText:'IN31'}).getByRole('button',{name:'编辑',exact:true}).click();const editor=page.getByRole('dialog',{name:/编辑.*单/});await expect(editor.getByRole('columnheader',{name:'参考完工件数',exact:true})).toHaveCount(applicable?1:0)
 Object.assign(draft,{status:'PENDING_REVIEW',creatorId:'8',reviewerId:'1'})
 for(const entry of ['list','message']){
  await page.goto(entry==='list'?'/metal/storage-orders?review=31':'/metal/storage-warehouses');await factory(page)
  if(entry==='message'){await page.getByRole('button',{name:/待复验消息/}).click();await page.getByRole('button').filter({hasText:'IN31'}).click()}
  const review=page.getByRole('dialog',{name:'单据复验',exact:true});await expect(review).toBeVisible();await expect(review.getByRole('columnheader',{name:'参考完工件数',exact:true})).toHaveCount(applicable?1:0)
 }
 expect(writes).toHaveLength(0)
})

for(const entry of ['message','list'])for(const mixed of [false,true])test(`review hides empty allocation expanders ${entry} mixed=${mixed}`,async({page,baseURL})=>{
 const detail={...document,lines:[{...document.lines[0],allocations:[]},...(mixed?[{...document.lines[0],id:'52',name:'有分配原料',measurementMode:'PIECE',quantity:60,allocations:[{ownerId:'8',ownerName:'客户甲',weight:'10.00',quantity:60}]}]:[])]}
 await setup(page,baseURL!,(path)=>path==='/metal/storage-orders/31'?{code:200,data:detail}:undefined)
 await page.goto(entry==='list'?'/metal/storage-orders?review=31':'/metal/storage-warehouses');await factory(page)
 if(entry==='message'){await page.getByRole('button',{name:/待复验消息/}).click();await page.getByRole('button').filter({hasText:'IN31'}).click()}
 const dialog=page.getByRole('dialog',{name:'单据复验'})
 await expect(dialog).toBeVisible()
 await expect(dialog.getByText('无来源分配明细')).toHaveCount(0)
 await expect(dialog.locator('.el-table__expand-icon:visible')).toHaveCount(mixed?1:0)
 if(mixed){await dialog.locator('.el-table__expand-icon:visible').click();await expect(dialog.getByText('客户甲',{exact:true})).toBeVisible();const quantity=dialog.locator('.el-table__body-wrapper').first().getByRole('row').filter({hasText:'有分配原料'}).locator('input').nth(1);await quantity.fill('');await quantity.pressSequentially('60',{delay:80});await expect(quantity).toBeVisible();await expect(quantity).toHaveValue('60');await expect(dialog.locator('.el-table__expanded-cell')).toHaveCount(1)}
})
for(const type of ['RAW_RETURN','RECOVERY_RETURN'])test(`return source retry preserves ${type} input and ignores closed dialog response`,async({page,baseURL})=>{
 const draft={...document,type,kind:'RET',status:'DRAFT',creatorId:'1',reviewerId:'9',warehouseIds:['11'],orderId:'81',executionId:'91',lines:[{...document.lines[0],stockId:'41',issueLineId:'71',purity:'',remark:'已有备注'}]}
 const {writes,errors}=await setup(page,baseURL!,(path,method)=>{
   if(path==='/metal/storage-orders/list')return {code:200,rows:[draft],total:1}
   if(path==='/metal/storage-orders/31'&&method==='GET')return {code:200,data:draft}
 })
 await page.goto('/metal/storage-orders');await factory(page)
 await page.getByRole('row').filter({hasText:'IN31'}).getByRole('button',{name:'编辑',exact:true}).click()
 const order=page.getByRole('dialog',{name:'编辑还库单'})
 await expect(order).toBeVisible()
 // Parent options have loaded; only the nested original-issue request is failed.
 await page.waitForTimeout(100)
 let attempts=0,release:(()=>void)|undefined
 await page.route('**/*-api/metal/storage-orders/options?**',async route=>{
  const params=new URL(route.request().url()).searchParams
  expect(params.get('factoryId')).toBe('101');expect(params.get('executionId')).toBe('91')
  attempts++
  if(attempts===1)return route.abort('failed')
  if(attempts===3)await new Promise<void>(resolve=>{release=resolve})
  await route.fulfill({json:{code:200,data:{issueLines:[{id:'71',stockId:'41',documentNumber:'OUT71',name:'原领料',sourceStock:stock,measurementMode:'WEIGHT'}]}}})
 })
 await order.getByRole('button',{name:'编辑',exact:true}).click()
 const line=page.getByRole('dialog',{name:'物料明细',exact:true})
 await expect(line.getByRole('alert')).toContainText('原领料明细加载失败')
 await line.getByRole('textbox',{name:'实测净重（g）',exact:true}).fill('9.50')
 await line.getByRole('textbox',{name:'纯度（%）',exact:true}).fill('99.5')
 await line.getByRole('textbox',{name:'备注 / 原因',exact:true}).fill('重试保留内容')
 await expect(line.getByRole('button',{name:'确认',exact:true})).toBeDisabled()
 await line.getByRole('button',{name:'重试',exact:true}).click()
 await expect(line.getByRole('button',{name:'重试',exact:true})).toHaveCount(0)
 await expect(line.getByRole('textbox',{name:'实测净重（g）',exact:true})).toHaveValue('9.50')
 await expect(line.getByRole('textbox',{name:'纯度（%）',exact:true})).toHaveValue('99.5')
 await expect(line.getByRole('textbox',{name:'备注 / 原因',exact:true})).toHaveValue('重试保留内容')
 await expect(line.locator('.el-select__selected-item').filter({hasText:'OUT71 / 原领料'})).toBeVisible()
 await line.getByRole('button',{name:'确认',exact:true}).click();await expect(line).toBeHidden()
 await expect(order.getByRole('cell',{name:'9.50',exact:true})).toBeVisible()
 await order.getByRole('button',{name:'编辑',exact:true}).click()
 await expect.poll(()=>!!release).toBeTruthy()
 await line.getByRole('button',{name:'取消',exact:true}).click();await expect(line).toBeHidden()
 release!()
 await order.getByRole('button',{name:'编辑',exact:true}).click()
 await expect(line.getByRole('button',{name:'确认',exact:true})).toBeEnabled()
 await expect(line.getByRole('textbox',{name:'实测净重（g）',exact:true})).toHaveValue('9.50')
 expect(writes).toEqual([]);expect(errors).toEqual([])
})

test('storage header names are shown in list detail and CSV',async({page,baseURL})=>{
 const named={...document,orderNumber:'GD001',creatorName:'管理员',reviewerName:'仓管甲'}
 await setup(page,baseURL!,(path)=>{
  if(path==='/metal/storage-orders/list')return {code:200,rows:[named],total:1}
  if(path==='/metal/storage-orders/31')return {code:200,data:named}
  if(path==='/metal/storage-orders/export')return {code:200,data:[named]}
 })
 await page.goto('/metal/storage-orders');await factory(page)
 const row=page.getByRole('row').filter({hasText:'IN31'})
 for(const name of ['GD001','管理员','仓管甲'])await expect(row.getByRole('cell',{name,exact:true})).toBeVisible()
 await row.getByRole('button',{name:'物料清单',exact:true}).click()
 const detail=page.getByRole('dialog',{name:'物料清单',exact:true})
 for(const name of ['GD001','管理员','仓管甲'])await expect(detail.getByText(name,{exact:true})).toBeVisible()
 await detail.locator('.el-dialog__headerbtn').click()
 const wait=page.waitForEvent('download');await page.getByRole('button',{name:'导出',exact:true}).click()
 const download=await wait,stream=await download.createReadStream(),chunks:Buffer[]=[];for await(const part of stream!)chunks.push(part)
 const csv=Buffer.concat(chunks).toString('utf8');for(const value of ['创建人','复验人员','GD001','管理员','仓管甲'])expect(csv).toContain(value)
})
for(const width of [1440,390])test(`material layout and inline customer preserve inputs at ${width}px`,async({page,baseURL})=>{
 await page.setViewportSize({width,height:1000});let customers:any[]=[]
 const {writes,errors}=await setup(page,baseURL!,(path,method)=>{
  if(path==='/metal/customer'&&method==='POST'){customers=[{id:'301',name:'新客户',companyName:'新客户公司'}];return {code:200,id:'301'}}
  if(path==='/metal/storage-orders/options')return {code:200,data:{warehouses:[warehouse],materials:[material],stocks:[stock],customers,people:[{id:'9',name:'仓管员',canAccept:true}]}}
 })
 await page.goto('/metal/storage-orders');await factory(page);await page.getByRole('tab',{name:'入库单',exact:true}).click();await page.getByRole('button',{name:'添加',exact:true}).click()
 const order=page.getByRole('dialog',{name:'添加入库单',exact:true});await order.getByRole('combobox',{name:'仓库/库房',exact:true}).press('ArrowDown');await page.getByRole('option',{name:'原料库',exact:true}).click();await order.getByRole('button',{name:'添加物料',exact:true}).click()
 const line=page.getByRole('dialog',{name:'物料明细',exact:true});await line.getByRole('textbox',{name:'实测净重（g）',exact:true}).fill('12.34');await line.getByRole('textbox',{name:'备注 / 原因',exact:true}).fill('保留输入')
 const group=line.locator('.owner-control');await expect.poll(()=>group.evaluate(node=>Math.abs(node.querySelector('.el-select')!.getBoundingClientRect().y-node.querySelector('button')!.getBoundingClientRect().y))).toBeLessThan(2);const select=await group.locator('.el-select').boundingBox(),button=await group.getByRole('button',{name:'添加客户'}).boundingBox();expect(button!.x).toBeGreaterThan(select!.x)
 const box=await line.boundingBox();expect(box!.x).toBeGreaterThanOrEqual(0);expect(box!.x+box!.width).toBeLessThanOrEqual(width)
 if(width===1440){const grid=line.locator('.storage-grid');const items=grid.locator(':scope > .el-form-item:visible');const labels=await items.locator('.el-form-item__label').allTextContents();expect(labels.slice(0,7)).toEqual(['产出 / 入库物料','料权归属','物料形态','纯度（%）','毛重（g）','杂质（g）','实测净重（g）'])}
 await group.getByRole('button',{name:'添加客户'}).click();let customer=page.getByRole('dialog',{name:'添加客户',exact:true});await customer.getByRole('button',{name:'取消',exact:true}).click();await expect(line.getByRole('textbox',{name:'实测净重（g）',exact:true})).toHaveValue('12.34')
 await group.getByRole('button',{name:'添加客户'}).click();customer=page.getByRole('dialog',{name:'添加客户',exact:true})
 for(const [name,value] of [['客户名称','新客户'],['客户公司名称','新客户公司'],['客户地址','地址'],['联络人名称','联系人'],['联络人电话','13912345678']])await customer.getByRole('textbox',{name,exact:true}).fill(value)
 await customer.getByRole('button',{name:'保存',exact:true}).click();await expect(customer).toBeHidden();await expect(group.locator('.el-select__selected-item').filter({hasText:'新客户公司'})).toBeVisible();await expect(line.getByRole('textbox',{name:'实测净重（g）',exact:true})).toHaveValue('12.34');await expect(line.getByRole('textbox',{name:'备注 / 原因',exact:true})).toHaveValue('保留输入')
 expect(writes).toHaveLength(1);expect(writes[0].path).toBe('/metal/customer');expect(writes[0].body.factoryId).toBe('101');expect(errors).toEqual([]);await line.screenshot({path:test.info().outputPath('material-'+width+'.png')})
})
test('material owner select uses full width when customer add is not granted',async({page,baseURL})=>{
 await setup(page,baseURL!,(path)=>path==='/getInfo'?{code:200,user:{userId:8,userName:'worker',factoryId:'101'},roles:['worker'],permissions:['metal:warehouse-order:list','metal:warehouse-order:query','metal:warehouse-order:add']}:undefined)
 await page.goto('/metal/storage-orders');await page.getByRole('tab',{name:'入库单',exact:true}).click();await page.getByRole('button',{name:'添加',exact:true}).click();const order=page.getByRole('dialog',{name:'添加入库单',exact:true});await order.getByRole('combobox',{name:'仓库/库房',exact:true}).press('ArrowDown');await page.getByRole('option',{name:'原料库',exact:true}).click();await order.getByRole('button',{name:'添加物料',exact:true}).click();const group=page.getByRole('dialog',{name:'物料明细',exact:true}).locator('.owner-control');await expect(group.getByRole('button',{name:'添加客户'})).toHaveCount(0);const outer=await group.boundingBox(),inner=await group.locator('.el-select').boundingBox();expect(Math.abs(outer!.width-inner!.width)).toBeLessThan(2)
})

test('finished goods in an old production draft stay visible but cannot be saved or imported',async({page,baseURL})=>{
 const draft={...document,number:'OUT31',kind:'OUT',type:'PRODUCTION_OUT',status:'DRAFT',creatorId:'1',reviewerId:'9',warehouseIds:['11'],orderId:'81',executionId:'91',lines:[{...document.lines[0],stockId:'43',name:'旧成品明细',code:'P43',netWeight:'7.00',remark:'保留备注'}]}
 const {writes}=await setup(page,baseURL!,(path)=>{
  if(path==='/metal/storage-orders/list')return {code:200,rows:[draft],total:1}
  if(path==='/metal/storage-orders/31')return {code:200,data:draft}
  if(path==='/metal/storage-orders/options')return {code:200,data:{warehouses:[warehouse],stocks:[stock],productionOrders:[{id:'81',number:'GD81'}],executions:[{id:'91',orderId:'81',processorId:'9',processName:'工序2',status:'OPEN'}],people:[{id:'9',name:'加工甲',canReceive:true}]}}
 })
 await page.goto('/metal/storage-orders');await factory(page);await page.getByRole('row').filter({hasText:'OUT31'}).getByRole('button',{name:'编辑',exact:true}).click();const d=page.getByRole('dialog',{name:'编辑出库单',exact:true})
 await expect(d.getByRole('combobox',{name:'领料方式',exact:true})).toBeDisabled();await expect(d.getByRole('combobox',{name:'加工批次',exact:true})).toBeDisabled();
 await expect(d.getByText('（不可用于生产领料，请重新选择）',{exact:true})).toBeVisible();await expect(d.getByRole('cell',{name:'保留备注',exact:true})).toBeVisible();await d.getByRole('button',{name:'保存草稿',exact:true}).click();await expect(d.getByText('生产领料仅支持本厂原料或半成品，请重新选择失效明细',{exact:true})).toBeVisible();expect(writes).toHaveLength(0)
 const downloaded=page.waitForEvent('download');await d.getByRole('button',{name:'下载模板',exact:true}).click();const stream=await(await downloaded).createReadStream(),chunks:Buffer[]=[];for await(const chunk of stream!)chunks.push(chunk)
 const header=Buffer.concat(chunks).toString('utf8').trimEnd(),row=['43','','','','0','7.00','','','','','','','','导入成品'].join(',');await d.locator('input[type=file]').setInputFiles({name:'成品.csv',mimeType:'text/csv',buffer:Buffer.from(header+'\r\n'+row+'\r\n')});await expect(d.getByText(/第2行：生产领料仅支持本厂原料或半成品，不能选择成品/)).toBeVisible();await expect(d.getByRole('cell',{name:'保留备注',exact:true})).toBeVisible();expect(writes).toHaveLength(0)
})

for(const entry of ['list','message'])test(`shared review mixed sources edit once and keep expansion ${entry}`,async({page,baseURL})=>{
 const detail={...document,type:'PRODUCTION_OUT',lines:[{id:'51',name:'混合物料',netWeight:'10.00',quantity:10,measurementMode:'PIECE',allocations:[{ownerId:'8',ownerName:'客户甲',weight:'6.00',quantity:6},{ownerId:'0',ownerName:'工厂自有',weight:'4.00',quantity:4}]}]}
 const {writes}=await setup(page,baseURL!,(path)=>path==='/metal/storage-orders/31'?{code:200,data:detail}:path==='/metal/storage-orders/31/review'?{code:409,msg:'库存已变化，请核对'}:undefined)
 await page.goto(entry==='list'?'/metal/storage-orders?review=31':'/metal/storage-warehouses');await factory(page);if(entry==='message'){await page.getByRole('button',{name:/待复验消息/}).click();await page.getByRole('button').filter({hasText:'IN31'}).click()}
 const d=page.getByRole('dialog',{name:'单据复验',exact:true});await d.locator('.el-table__expand-icon').click();const sources=d.locator('.review-sources'),a=sources.getByRole('row').filter({hasText:'客户甲'}),b=sources.getByRole('row').filter({hasText:'工厂自有'});
 await a.locator('input').nth(1).fill('');await a.locator('input').nth(1).pressSequentially('6',{delay:100});await expect(sources).toBeVisible();await page.evaluate(()=>window.dispatchEvent(new Event('storage-documents-changed')));await expect(sources).toBeVisible();await a.locator('input').first().fill('');await d.getByRole('button',{name:'确认过账'}).click();await expect(d.getByText('第1行：重量须为非负数，最多两位小数')).toBeVisible();expect(writes).toHaveLength(0)
 await expect(d.getByText('按来源分配',{exact:true})).toHaveCount(0);await a.locator('input').first().fill('6.00');await d.getByRole('combobox').first().press('ArrowDown');await page.getByRole('option',{name:'交接损失',exact:true}).click();await a.locator('input').nth(2).fill('5.00');await a.locator('input').nth(3).fill('5');await a.getByRole('combobox').press('ArrowDown');await page.getByRole('option',{name:'客户',exact:true}).click();await b.locator('input').nth(2).fill('3.00');await b.locator('input').nth(3).fill('3');await b.getByRole('combobox').press('ArrowDown');await page.getByRole('option',{name:'工厂',exact:true}).last().click();await expect(d.getByText('按来源分配',{exact:true})).toBeVisible();await d.getByRole('button',{name:'确认过账'}).click();await expect(d.getByText('第1行：请填写本行差异原因')).toBeVisible();expect(writes).toHaveLength(0)
 await d.locator('.el-table__body-wrapper').first().getByRole('row').filter({hasText:'混合物料'}).locator('input').last().fill('两方分别承担');await d.getByRole('button',{name:'确认过账'}).click();await expect(d.getByText('库存已变化，请核对',{exact:true})).toBeVisible();expect(writes).toHaveLength(1);expect(writes[0].body.lines[0]).toMatchObject({actualNetWeight:'8.00',actualQuantity:'8',issuedNetWeight:'10.00',issuedQuantity:'10',lossBearer:null,allocations:[{lossBearer:'CUSTOMER',receivedWeight:'5.00'},{lossBearer:'FACTORY',receivedWeight:'3.00'}]});await expect(a.locator('input').nth(2)).toHaveValue('5.00');await expect(sources).toBeVisible()
})

for(const scenario of ['success','rejected','unknown','refresh-failed'])test(`batch close visible result ${scenario}`,async({page,baseURL})=>{
 let submitted=0,flowReads=0
 const batch={id:'3',orderStepId:'4',status:'OPEN',version:3,inputWeight:'294.00',outputWeight:'290.00',returnedWeight:'0.00',recoveredWeight:'0.00',lossWeight:null},result={...batch,status:'CLOSED',version:4,lossWeight:'4.00'}
 const {writes}=await setup(page,baseURL!,(path,method)=>{
  if(path==='/metal/production-order/list')return {code:200,rows:[{id:'2',number:'GD2',status:'IN_PROGRESS'}],total:1}
  if(path==='/metal/production-order/2/flow'){flowReads++;return submitted&&scenario==='refresh-failed'?{code:500,msg:'流程暂不可用'}:{code:200,data:{order:{id:'2',status:'IN_PROGRESS',factoryName:'本厂'},items:[],executions:[submitted&&scenario!=='rejected'?result:batch]}}}
  if(path.startsWith('/metal/production-order/executions/operations/'))return {code:200,data:result}
  if(path==='/metal/production-order/executions/3/close'&&method==='POST'){submitted++;return scenario==='rejected'?{code:409,msg:'本批仍有未处理仓储单据'}:scenario==='unknown'?{code:500,msg:'结果未知'}:{code:200,data:result}}
 })
 await page.goto('/metal/production-order');await factory(page);await page.getByRole('button',{name:'查看',exact:true}).click();const parent=page.getByRole('dialog',{name:'生产工单完整流程',exact:true});await parent.getByRole('button',{name:'结清批次',exact:true}).click();const d=page.getByRole('dialog',{name:'结清加工批次',exact:true});await d.getByRole('button',{name:'已核对，结清',exact:true}).click()
 if(scenario==='rejected'){await expect(d.getByText('本批仍有未处理仓储单据',{exact:true})).toBeVisible();await d.getByRole('button',{name:'查看关联仓储单据'}).click();await expect(parent.getByRole('tab',{name:'仓储单据'})).toHaveAttribute('aria-selected','true')}
 else {if(scenario==='unknown'){await expect(d.getByText('结清结果待核实',{exact:true})).toBeVisible();await expect(d.getByRole('button',{name:'已核对，结清'})).toHaveCount(0);await d.getByRole('button',{name:'核实结果',exact:true}).click()}await expect(d.getByText('批次已结清',{exact:true})).toBeVisible();await expect(d.getByText('4.00',{exact:true})).toBeVisible();if(scenario==='refresh-failed')await expect(d.getByText('批次已结清，流程刷新失败，请重试',{exact:true})).toBeVisible()}
 expect(submitted).toBe(1);expect(writes.filter(w=>w.path.endsWith('/close'))).toHaveLength(1)
})

test('batch close disables repeated confirmation and ignores response after page leave',async({page,baseURL})=>{
 let requests=0,release!:()=>void;const gate=new Promise<void>(r=>release=r)
 await setup(page,baseURL!,(path)=>{
  if(path==='/metal/production-order/list')return {code:200,rows:[{id:'2',number:'GD2',status:'IN_PROGRESS'}],total:1}
  if(path==='/metal/production-order/2/flow')return {code:200,data:{order:{id:'2',status:'IN_PROGRESS'},items:[],executions:[{id:'3',status:'OPEN',version:3,inputWeight:'294.00',outputWeight:'290.00'}]}}
 })
 await page.route('**/*-api/metal/production-order/executions/3/close',async route=>{requests++;await gate;await route.fulfill({json:{code:200,data:{id:'3',status:'CLOSED',lossWeight:'4.00'}}}).catch(()=>{})})
 await page.goto('/metal/production-order');await factory(page);await page.getByRole('button',{name:'查看',exact:true}).click();await page.getByRole('button',{name:'结清批次',exact:true}).click();const d=page.getByRole('dialog',{name:'结清加工批次',exact:true});await d.getByRole('button',{name:'已核对，结清'}).click();const busy=d.getByRole('button',{name:'正在结清',exact:true});await expect(busy).toBeDisabled();await expect(d.getByRole('button',{name:'取消',exact:true})).toBeDisabled();expect(requests).toBe(1);await page.goto('/metal/storage-warehouses');release();await expect(page.getByRole('dialog',{name:'结清加工批次'})).toHaveCount(0);await expect(page.getByText('批次已结清',{exact:true})).toHaveCount(0)
})

test('new issue hides reserved batch and switching to supplement releases the auto batch',async({page,baseURL})=>{
 let reserved=false
 const {writes}=await setup(page,baseURL!,(path,method,body)=>{
  if(path==='/metal/storage-orders/options')return {code:200,data:{warehouses:[warehouse],stocks:[stock],productionOrders:[{id:'71',number:'GD71'}],orderItems:[{id:'81',orderId:'71',productName:'产品甲',steps:[{id:'2',sequenceNo:1,processName:'酸洗',measurementMode:'WEIGHT'}]}],executions:reserved?[{id:'91',orderId:'71',status:'OPEN',processorId:'9',processName:'酸洗'}]:[],people:[{id:'9',name:'加工甲',canReceive:true}]}}
  if(path==='/metal/storage-orders/reservations'&&method==='POST'){reserved=true;return {code:200,data:{status:'TEMP',version:1,executionId:'91',processorId:'9',orderItemId:'81',orderStepId:'2',lines:body.lines}}}
  if(path.endsWith('/release'))return {code:200,data:{status:'RELEASED',version:2}}
 })
 await page.goto('/metal/storage-orders');await factory(page);await page.getByRole('button',{name:'添加',exact:true}).click()
 const d=page.getByRole('dialog',{name:'添加出库单',exact:true})
 async function choose(label:string,option:string){await d.getByRole('combobox',{name:label,exact:true}).press('ArrowDown');await page.getByRole('option',{name:option,exact:true}).click();if(label==='仓库/库房')await page.keyboard.press('Escape')}
 await choose('生产工单','GD71');await choose('产品组','产品甲 / 81');await choose('目标工序','1 酸洗');await choose('加工人员','加工甲');await choose('仓库/库房','原料库')
 await d.getByRole('button',{name:'添加物料',exact:true}).click();const line=page.getByRole('dialog',{name:'物料明细',exact:true});await line.getByRole('combobox',{name:'库存物料',exact:true}).press('ArrowDown');await page.getByRole('option').filter({hasText:'M21'}).click();await line.getByRole('textbox',{name:'实测净重（g）',exact:true}).fill('1.00');await line.getByRole('button',{name:'确认',exact:true}).click();await expect(line).toBeHidden()
 expect(writes.find(w=>w.path==='/metal/storage-orders/reservations')?.body.executionMode).toBe('NEW');await expect(d.getByRole('combobox',{name:'加工批次',exact:true})).toHaveCount(0)
 await d.getByRole('combobox',{name:'指定复验人员',exact:true}).press('ArrowDown');await expect(page.getByRole('option',{name:'加工甲',exact:true})).toBeVisible();await page.keyboard.press('Escape')
 await choose('领料方式','已有批次补领料');await page.getByRole('button',{name:'确定',exact:true}).click();await expect(d.getByRole('combobox',{name:'加工批次',exact:true})).toBeVisible();expect(writes.filter(w=>w.path.endsWith('/release'))).toHaveLength(1)
 await d.getByRole('button',{name:'保存草稿',exact:true}).click();await expect(d.getByRole('alert').getByText('请选择加工批次',{exact:true})).toBeVisible();expect(writes.filter(w=>w.path==='/metal/storage-orders')).toHaveLength(0)
 await d.getByRole('combobox',{name:'加工批次',exact:true}).press('ArrowDown');await page.getByRole('option').filter({hasText:'91 /'}).click();await expect(d.getByRole('combobox',{name:'产品组',exact:true})).toHaveCount(0)
})
