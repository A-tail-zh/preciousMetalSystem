import {test,expect} from '@playwright/test'

test('document lines show codes, categories and warehouses independently of warehouse names',async({page,baseURL})=>{
 const lines=[
  {netWeight:'600.00',name:'产品1',warehouseId:'11',category:'SEMI',identity:{code:'10001',category:'SEMI'}},
  {netWeight:'675.00',name:'产品1',warehouseId:'12',category:'SEMI',identity:{code:'10001',category:'SEMI'}},
  {netWeight:'20.00',name:'原材料甲',warehouseId:'11',category:'RAW',code:'M001'},
  {netWeight:'5.00',warehouseName:'历史仓库',category:'PRODUCT',identity:{code:'P001',name:'成品甲'}},
  {netWeight:'1.00',name:'历史明细',warehouseId:'99'}
 ]
 const doc={id:'38',number:'IN20261111000017',kind:'IN',type:'SEMI_IN',status:'POSTED',warehouseIds:[11,'12'],lines}
 const writes:string[]=[]
 await page.context().addCookies([{name:'Admin-Token',value:'mock-detail-labels',url:baseURL!}])
 await page.route('**/*-api/**',async route=>{
  if(route.request().method()!=='GET'){writes.push(route.request().url());await route.abort();return}
  const path=new URL(route.request().url()).pathname.replace(/^\/(dev|prod)-api/,'')
  let body:any={code:200,data:[],rows:[],total:0}
  if(path==='/getInfo')body={code:200,user:{userId:1,userName:'admin'},roles:['admin'],permissions:['*:*:*']}
  if(path==='/metal/options/factories')body={code:200,data:[{id:'101',name:'本厂'}]}
  if(path==='/metal/storage-orders/list')body={code:200,rows:[doc],total:1}
  if(path==='/metal/storage-orders/38')body={code:200,data:doc}
  if(path==='/metal/storage-orders/options')body={code:200,data:{warehouses:[{id:'11',name:'原料'},{id:'12',name:'半成品'}]}}
  if(path==='/metal/storage-review-tasks/count')body={code:200,data:{count:0}}
  await route.fulfill({json:body})
 })
 await page.goto('/metal/storage-orders')
 await page.getByRole('combobox',{name:'所属工厂',exact:true}).press('ArrowDown');await page.getByRole('option',{name:'本厂',exact:true}).click()
 await page.getByRole('tab',{name:'入库单',exact:true}).click()
 await expect(page.getByRole('row').filter({hasText:doc.number}).getByRole('cell',{name:'原料、半成品',exact:true})).toBeVisible()
 await page.getByRole('row').filter({hasText:doc.number}).getByRole('button',{name:'物料清单',exact:true}).click()
 const dialog=page.getByRole('dialog',{name:'物料清单',exact:true})
 for(const label of ['编码','物料类别','仓库名称'])await expect(dialog.getByRole('columnheader',{name:label,exact:true})).toBeVisible()
 for(const [weight,values] of [['600.00',['10001','产品1','半成品','原料']],['675.00',['10001','产品1','半成品','半成品']],['20.00',['M001','原材料甲','原材料','原料']],['5.00',['P001','成品甲','成品','历史仓库']],['1.00',['历史明细','99']]] as const){
  const row=dialog.getByRole('row').filter({has:page.getByRole('cell',{name:weight,exact:true})})
  for(const value of values)await expect(row).toContainText(value)
 }
 expect(writes).toEqual([])
})
