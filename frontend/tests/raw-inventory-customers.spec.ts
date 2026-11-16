import {test,expect,type Page,type Locator} from '@playwright/test'
import {selectWarehouseOption} from './helpers/warehouse-select'

async function pick(page:Page,dialog:Locator,label:string,name:string){
 const field=dialog.locator('.el-form-item').filter({has:page.locator('label').filter({hasText:new RegExp('^'+label+'$')})})
 await selectWarehouseOption(page,field,name)
}
async function setup(page:Page){
 const state={posts:[] as any[],fail:false,weight:'100.00',version:2}
 const row=()=>({id:'1',category:'RAW',code:'RAW-001',name:'铱粉料',materialType:'粉料',specification:'原料规格',customerId:'C',customerCompanyName:'客户公司',organization:'历史公司',warehouseId:'W',warehouseName:'原料仓',factoryId:'F',factoryName:'本厂',netWeight:state.weight,quantity:0,version:state.version,ownershipStatus:'NORMAL',batchNo:''})
 await page.context().addCookies([{name:'Admin-Token',value:'raw-inventory-test',url:'http://127.0.0.1:5180'}])
 await page.route('**/dev-api/**',async r=>{
  const url=new URL(r.request().url()),p=url.pathname.replace('/dev-api','');let out:any={code:200,data:[],rows:[],total:0}
  if(p==='/getInfo')out={code:200,user:{userId:1,userName:'admin'},roles:['admin'],permissions:['*:*:*']}
  else if(p==='/getRouters')out.data=[{path:'/metal',component:'Layout',children:[{path:'inventory',component:'metal/inventory/index',name:'RawInventory'},{path:'warehouse-order',component:'metal/warehouse-order/index',name:'RawOrders'}]}]
  else if(p==='/metal/inventory/list')out={code:200,rows:url.searchParams.get('filter')==='RAW'?[row()]:[{...row(),id:'2',category:'SEMI',name:'半成品',quantity:5}],total:1}
  else if(p==='/metal/inventory/1'||p==='/metal/inventory/1/edit-context')out.data={...row(),ledger:[{id:'L',orderNo:'IN001',type:'RAW_IN',deltaWeight:'100',balanceWeight:'100',deltaQuantity:0,balanceQuantity:0,customerCompanySnapshot:'历史公司',createTime:'2026-11-02 10:00:00'}]}
  else if(p==='/metal/inventory/1/adjust'){state.posts.push(r.request().postDataJSON());if(state.fail)out={code:500,msg:'库存不足，不能过账'};else{state.weight='95.00';state.version++;out.id='ADJ1'}}
  else if(p.endsWith('/options/factories'))out.data=[{id:'F',name:'本厂'}]
  else if(p.endsWith('/options/people'))out.data=[{id:'U',name:'人员',phone:'13800000000',roleNames:'仓管员'}]
  else if(p.endsWith('/options/warehouses'))out.data=[{id:'W',name:'原料仓'}]
  else if(p.endsWith('/options/customers'))out.data=[{id:'C',name:'客户',companyName:'客户公司',version:3}]
  else if(p.endsWith('/options/materials'))out.data=[{id:'M',code:'RAW-001',name:'铱粉料',materialType:'粉料',specification:'原料规格'}]
  else if(p.endsWith('/number/reserve'))out.data='IN001'
  else if(p==='/metal/warehouse-order'&&r.request().method()==='POST'){state.posts.push(r.request().postDataJSON());out.id='IN1'}
  await r.fulfill({json:out})
 })
 await page.goto('/metal/inventory');await expect(page.locator('.warehouse-table')).toContainText('RAW-001');return state
}
for(const width of [1920,1440,768,390])test('raw inventory and detail use weight only '+width,async({page})=>{
 await page.setViewportSize({width,height:1000});await setup(page)
 const headers=await page.locator('.warehouse-table thead th .cell').allTextContents()
 expect(headers.filter(h=>h.trim())).toEqual(['物料编码','物料类型','规格型号','客户公司名称','净重（g）','操作'])
 await page.getByRole('button',{name:'查看详情',exact:true}).click();const dialog=page.getByRole('dialog',{name:'库存管理详情',exact:true})
 await expect(dialog).toContainText('剩余总量：100.00 g');await expect(dialog).toContainText('历史公司');await expect(dialog).toContainText('客户公司')
 await expect(dialog).not.toContainText('数量');await expect(dialog).not.toContainText('件');await expect(dialog).toContainText('原料仓')
 await page.screenshot({path:'../.runtime/raw-inventory-20261102/detail-'+width+'.png',fullPage:true,animations:'disabled'})
})
test('raw adjustment has signed net delta, reason and people only',async({page})=>{
 const s=await setup(page);await page.locator('.warehouse-table').getByRole('button',{name:'库存调整',exact:true}).click();const d=page.getByRole('dialog',{name:'库存调整',exact:true})
 await expect(d).toContainText('当前净重：100.00 g');await expect(d).not.toContainText('毛重差额');await expect(d).not.toContainText('杂质差额');await expect(d).not.toContainText('数量')
 await d.getByLabel('净重差额(g)',{exact:true}).fill('0');await d.getByRole('button',{name:'确认',exact:true}).click();await expect(d).toContainText('净重差额不能为0');expect(s.posts).toHaveLength(0)
 await d.getByLabel('净重差额(g)',{exact:true}).fill('-5');await pick(page,d,'经手人','仓管员｜人员｜13800000000');await pick(page,d,'仓管员','仓管员｜人员｜13800000000');await d.getByLabel('调整原因',{exact:true}).fill('现场盘点')
 s.fail=true;await d.getByRole('button',{name:'确认',exact:true}).click();await expect.poll(()=>s.posts.length).toBe(1);await expect(d.getByRole('button',{name:'确认',exact:true})).toBeEnabled();await expect(d).toBeVisible();await expect(d.getByLabel('净重差额(g)',{exact:true})).toHaveValue('-5.00');await expect(d.getByLabel('调整原因',{exact:true})).toHaveValue('现场盘点')
 s.fail=false;await d.getByRole('button',{name:'确认',exact:true}).click();await expect(d).not.toBeVisible();expect(s.posts).toHaveLength(2);expect(s.posts[0].requestId).toBe(s.posts[1].requestId);expect(s.posts[1].deltaQuantity).toBe(0);expect(s.posts[1].deltaGrossWeight).toBeUndefined()
 await expect(page.locator('.warehouse-table')).toContainText('95.00')
})
test('raw entry selects actual customer and submits its version',async({page})=>{
 const s=await setup(page);await page.locator('.toolbar').getByRole('button',{name:'添加',exact:true}).click();const d=page.getByRole('dialog',{name:'添加入库工单',exact:true})
 await pick(page,d,'工厂','本厂');await pick(page,d,'仓库/库房','原料仓');await page.keyboard.press('Escape');await pick(page,d,'经办人','仓管员｜人员｜13800000000');await page.keyboard.press('Escape');await pick(page,d,'仓管员','仓管员｜人员｜13800000000')
 await d.locator('.line-toolbar').getByRole('button',{name:'添加',exact:true}).click();const line=page.getByRole('dialog',{name:'物料明细',exact:true})
 await pick(page,line,'原材料','RAW-001 / 铱粉料 / 原料规格');await line.getByLabel('实测净重(g)',{exact:true}).fill('10');await pick(page,line,'客户公司名称','客户公司 / 客户')
 await expect(line).not.toContainText('数量');await line.getByRole('button',{name:'确认',exact:true}).click();await expect(line).not.toBeVisible();await d.getByRole('button',{name:'确认',exact:true}).click()
 await expect.poll(()=>s.posts.length).toBe(1);expect(s.posts[0].lines[0]).toMatchObject({customerId:'C',customerVersion:3,quantity:0,organization:'客户公司'})
})
test('semi inventory keeps its quantity column',async({page})=>{
 await setup(page);await page.getByRole('tab',{name:'半成品',exact:true}).click();await expect(page.locator('.warehouse-table').getByRole('columnheader',{name:'数量 / 参考',exact:true})).toBeVisible()
})
