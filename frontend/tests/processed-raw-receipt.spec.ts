import {test,expect,type Page,type Locator} from '@playwright/test'
import {receiptTypesFor} from '../src/views/metal/storage-orders/model'

async function select(page:Page,scope:Page|Locator,label:string,name:string){await scope.getByRole('combobox',{name:label,exact:true}).press('ArrowDown');await page.getByRole('option',{name,exact:true}).click();await scope.getByRole('heading').first().click()}
test('receipt types retain defaults and add only the raw alternative for semi batches',()=>{
 expect(receiptTypesFor('RAW')).toEqual(['PROCESS_RAW_IN'])
 expect(receiptTypesFor('SEMI')).toEqual(['SEMI_IN','PROCESS_RAW_IN'])
 expect(receiptTypesFor('PRODUCT')).toEqual(['PRODUCT_IN'])
 expect(receiptTypesFor(null)).toEqual([])
})
test('semi batch can switch to processed raw and select only matching materials',async({page,baseURL})=>{
 await page.context().addCookies([{name:'Admin-Token',value:'processed-raw-ui',url:baseURL!}])
 await page.route('**/*-api/**',async route=>{
  const path=new URL(route.request().url()).pathname.replace(/^\/(prod|dev)-api/,'')
  let body:any={code:200,data:[],rows:[],total:0}
  if(path==='/getInfo')body={code:200,user:{userId:1,userName:'admin'},roles:['admin'],permissions:['*:*:*']}
  if(path==='/metal/options/factories')body={code:200,data:[{id:'101',name:'本厂'}]}
  if(path==='/metal/storage-review-tasks/count')body={code:200,data:{count:0}}
  if(path==='/metal/storage-orders/options')body={code:200,data:{warehouses:[{id:'11',name:'原料'},{id:'12',name:'半成品'}],productionOrders:[{id:'10',number:'GD10'}],executions:[{id:'14',orderId:'10',orderItemId:'13',orderStepId:'30',processorId:'9',processorName:'加工员',processName:'融定',status:'OPEN',outputCategory:'SEMI',measurementMode:'WEIGHT',metalTypeId:'19',expectedProductId:'1',productName:'产品1'}],products:[{id:'1',name:'产品1'}],materials:[{id:'1',code:'20003',name:'加工后金属料',metalTypeId:'19',materialForm:'METAL',measurementMode:'WEIGHT'},{id:'2',code:'WRONG-METAL',name:'其他金属',metalTypeId:'20',materialForm:'METAL',measurementMode:'WEIGHT'},{id:'3',code:'WRONG-MODE',name:'按件材料',metalTypeId:'19',materialForm:'METAL',measurementMode:'PIECE'}]}}
  await route.fulfill({json:body})
 })
 await page.goto('/metal/storage-orders');await page.getByRole('combobox',{name:'所属工厂',exact:true}).press('ArrowDown');await page.getByRole('option',{name:'本厂',exact:true}).click()
 await page.getByRole('tab',{name:'入库单',exact:true}).click();await page.getByRole('button',{name:'添加',exact:true}).click()
 const dialog=page.getByRole('dialog',{name:'添加入库单',exact:true})
 await select(page,dialog,'单据类型','半成品入库');await select(page,dialog,'生产工单','GD10');await select(page,dialog,'加工批次','14 / 产品1 / 融定')
 await expect(dialog.locator('.el-form-item').filter({hasText:'单据类型'})).toContainText('半成品入库')
 await select(page,dialog,'单据类型','加工原材料入库')
 await expect(dialog.locator('.el-form-item').filter({hasText:'单据类型'})).toContainText('加工原材料入库')
 await select(page,dialog,'仓库/库房','原料');await dialog.getByRole('button',{name:'添加物料',exact:true}).click()
 const line=page.getByRole('dialog',{name:'物料明细',exact:true})
 await line.getByRole('combobox',{name:'入库物料',exact:true}).press('ArrowDown')
 await expect(page.getByRole('option').filter({hasText:'WRONG-'})).toHaveCount(0)
 await page.getByRole('option',{name:'【原料】 20003 / 加工后金属料',exact:true}).click()
 await expect(line.getByRole('textbox',{name:'物料形态',exact:true})).toHaveValue('金属料')
 await expect(line.getByRole('textbox',{name:'物料形态',exact:true})).not.toBeEditable()
 await line.getByRole('textbox',{name:'实测净重（g）',exact:true}).fill('10')
 await line.getByRole('button',{name:'确认',exact:true}).click()
 await expect(line).not.toBeVisible();await expect(dialog.getByRole('cell',{name:'20003 加工后金属料',exact:true})).toBeVisible()
})
