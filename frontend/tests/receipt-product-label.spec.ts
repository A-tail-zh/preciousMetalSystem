import {test,expect,type Page,type Locator} from '@playwright/test'
async function choose(page:Page,picker:Locator,name:string){await picker.press('ArrowDown');const id=await picker.getAttribute('aria-controls');await page.locator(`[id="${id}"]`).getByRole('option',{name,exact:true}).click()}
async function fixture(page:Page,baseURL:string,legacy=false,historicalName=''){
 const saved:any[]=[]
 const product={id:'51',code:'P051',name:'金环',processes:[{id:'21',name:'工序甲',measurementMode:'WEIGHT'}]}
 const executions=[{id:'7',orderId:'9',orderItemId:'31',orderStepId:'41',processorId:'81',processorName:'加工甲',expectedProductId:'51',productName:'金环',processName:'融定',outputCategory:'SEMI',measurementMode:'WEIGHT',status:'OPEN'},{id:'8',orderId:'9',orderItemId:'31',orderStepId:'42',processorId:'81',processorName:'加工甲',expectedProductId:'51',productName:'金环',processName:'成型',outputCategory:'PRODUCT',measurementMode:'PIECE',status:'OPEN'}]
 const draft={id:'24',creatorId:'1',number:'IN_LEGACY',factoryId:'101',type:'SEMI_IN',kind:'IN',status:'DRAFT',version:0,reviewRound:0,orderId:'9',executionId:'7',reviewerId:'2',date:'2026-11-11',warehouseIds:['11'],lines:[{localId:'old',productId:'51',name:historicalName,warehouseId:'11',netWeight:'5.00',measurementMode:'WEIGHT'}]}
 await page.context().addCookies([{name:'Admin-Token',value:'label-test',url:baseURL}])
 await page.route('**/*-api/**',async route=>{
  const path=new URL(route.request().url()).pathname.replace(/^\/(dev|prod)-api/,'');let response:any={code:200,data:[],rows:[],total:0}
  if(path==='/getInfo')response={code:200,user:{userId:1,userName:'admin'},roles:['admin'],permissions:['*:*:*']}
  if(path==='/getRouters')response.data=[{path:'/metal',component:'Layout',children:[{path:'storage-orders',component:'metal/storage-orders/index'}]}]
  if(path==='/metal/options/factories')response.data=[{id:'101',name:'本厂'}]
  if(path==='/metal/storage-orders/list')response={code:200,rows:legacy?[draft]:[],total:legacy?1:0}
  if(path==='/metal/storage-orders/24')response.data=draft
  if(path==='/metal/storage-orders/options')response.data={products:[product],executions,productionOrders:[{id:'9',number:'GD9'}],warehouses:[{id:'11',name:'仓库一'}],people:[{id:'2',name:'仓管',canAccept:true,canReceive:true}],materials:[],stocks:[],customers:[]}
  if(path==='/metal/storage-orders'&&route.request().method()==='POST'){const body=route.request().postDataJSON();saved.push(body);response.data={...body,id:'25',version:1}}
  await route.fulfill({json:response})
 })
 await page.goto('/metal/storage-orders');await choose(page,page.getByRole('combobox',{name:'所属工厂',exact:true}),'本厂');await page.getByRole('tab',{name:'入库单',exact:true}).click()
 if(legacy)await page.getByRole('row').filter({hasText:'IN_LEGACY'}).getByRole('button',{name:'编辑',exact:true}).click();else await page.getByRole('button',{name:'添加',exact:true}).click()
 const dialog=page.getByRole('dialog',{name:legacy?'编辑入库单':'添加入库单',exact:true});await expect(dialog).toBeVisible();return {dialog,saved}
}
for(const [type,batch] of [['半成品入库','7 / 金环 / 融定'],['成品入库','8 / 金环 / 成型'],['外来半成品入库','']])test(`${type} echoes product code/name before saving`,async({page,baseURL})=>{
 const {dialog,saved}=await fixture(page,baseURL!);await choose(page,dialog.getByRole('combobox',{name:'单据类型',exact:true}),type)
 if(batch){await choose(page,dialog.getByRole('combobox',{name:'生产工单',exact:true}),'GD9');await choose(page,dialog.getByRole('combobox',{name:'加工批次',exact:true}),batch)}
 await choose(page,dialog.locator('.el-form-item').filter({has:page.locator('label').filter({hasText:/^指定复验人员$/})}).getByRole('combobox'),'仓管')
 await choose(page,dialog.locator('.el-form-item').filter({has:page.locator('label').filter({hasText:/^仓库\/库房$/})}).getByRole('combobox'),'仓库一');await dialog.getByRole('button',{name:'添加物料',exact:true}).click()
 const line=page.getByRole('dialog',{name:'物料明细',exact:true});await expect(line).toBeVisible()
 if(!batch){await choose(page,line.getByRole('combobox',{name:'产品',exact:true}),'【半成品】 P051 / 金环');await line.locator('.el-form-item').filter({has:page.locator('label').filter({hasText:/^外部来源凭证$/})}).getByRole('textbox').fill('TEST');await choose(page,line.locator('.el-form-item').filter({has:page.locator('label').filter({hasText:/^已完成至工序$/})}).getByRole('combobox'),'1. 工序甲')}
 await line.getByRole('textbox',{name:'实测净重（g）',exact:true}).fill('5.00');if(type==='成品入库')await line.getByRole('textbox',{name:'数量（件）',exact:true}).fill('2');await line.getByRole('button',{name:'确认',exact:true}).click();await expect(line).not.toBeVisible()
 const row=dialog.locator('.material-list .el-table__body tr');await expect(row).toContainText('P051 金环');await dialog.getByRole('button',{name:'保存草稿',exact:true}).click();await expect(dialog).not.toBeVisible();expect(saved[0].lines[0]).toMatchObject({productId:'51',code:'P051',name:'金环',netWeight:'5.00'})
})
for(const name of ['', '历史金环'])test(`legacy product ID line displays ${name||'catalog fallback'} without changing quantity`,async({page,baseURL})=>{
 const {dialog}=await fixture(page,baseURL!,true,name);const row=dialog.locator('.material-list .el-table__body tr');await expect(row).toContainText('P051');await expect(row).toContainText(name||'金环');await expect(row).toContainText('5.00')
})
