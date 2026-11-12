import {test,expect} from '@playwright/test'

test('debt history and repayment preserve an older selected debt and its debtor',async({page})=>{
 const debt={id:'7',workOrderId:'21',orderNumber:'GD21',creditorId:'0',creditorName:'工厂',debtorId:'31',debtorName:'客户甲',debtKind:'ADVANCE',measurementMode:'WEIGHT',originalWeight:'100.00',settledWeight:'40.00',remainingWeight:'60.00',remainingQuantity:0,status:'OPEN',identity:{name:'金属原料',specification:'S'},documentNumber:'OUT7',sourceBatchNo:'ST7'}
 const seen:string[]=[]
 await page.context().addCookies([{name:'Admin-Token',value:'debt-ui-test',url:process.env.STORAGE_TEST_BASE_URL||'http://127.0.0.1:5202'}])
 await page.route('**/*-api/**',async route=>{
  const url=new URL(route.request().url()),path=url.pathname.replace(/^\/(dev|prod)-api/,'');seen.push(path+url.search)
  let body:any={code:200,data:[],rows:[],total:0}
  if(path==='/getInfo')body={code:200,user:{userId:1,userName:'admin'},permissions:['*:*:*'],roles:['admin']}
  if(path==='/metal/options/factories')body={code:200,data:[{id:'101',name:'工厂甲'}]}
  if(path==='/metal/options/customers')body={code:200,data:[{id:'31',name:'客户甲'}]}
  if(path==='/metal/storage-orders/options')body={code:200,data:{warehouses:[{id:'11',name:'原料库'}],customers:[{id:'31',name:'客户甲'}],people:[],stocks:[]}}
  if(path==='/metal/material-debts/list'){
   const settled={...debt,id:'8',settledWeight:'100.00',remainingWeight:'0.00',status:'SETTLED'}
   const rows=url.searchParams.get('pageSize')==='500'?[]:url.searchParams.get('includeSettled')==='true'?[debt,settled]:[debt]
   body={code:200,rows,total:rows.length}
  }
  if(path==='/metal/material-debts/7')body={code:200,data:{...debt,settlements:[]}}
  await route.fulfill({json:body})
 })
 await page.goto('/metal/storage-orders')
 await page.getByRole('combobox',{name:'所属工厂',exact:true}).press('ArrowDown');await page.getByRole('option',{name:'工厂甲',exact:true}).click()
 await page.getByRole('button',{name:'借还料账',exact:true}).click()
 const panel=page.getByRole('dialog',{name:'材料借还账',exact:true})
 await expect(panel).toContainText('100.00 / 40.00 / 60.00 g')
 await panel.getByText('包含已结清',{exact:true}).click();await expect(panel.getByRole('cell',{name:'已结清',exact:true})).toBeVisible()
 await expect(panel.getByRole('button',{name:'办理归还',exact:true})).toHaveCount(1)
 await panel.getByRole('button',{name:'办理归还',exact:true}).click()
 const editor=page.getByRole('dialog',{name:'添加料权归还单',exact:true})
 await editor.getByRole('combobox',{name:'仓库/库房',exact:true}).press('ArrowDown');await page.getByRole('option',{name:'原料库',exact:true}).click();await page.keyboard.press('Escape')
 await editor.getByRole('button',{name:'添加物料',exact:true}).click()
 const line=page.getByRole('dialog',{name:'物料明细',exact:true})
 await expect(line.locator('.owner-control')).toContainText('客户甲')
 await expect(line).toContainText('7 / 客户甲欠工厂 / 金属原料 / 未还60.00g')
 expect(seen.some(url=>url.startsWith('/metal/material-debts/7'))).toBeTruthy()
 expect(seen.some(url=>url.includes('orderId=21')&&url.includes('pageSize=500'))).toBeTruthy()
})

for(const mode of ['normal','borrow','clear'] as const)test(`repayment ${mode} saves the actual source owner`,async({page})=>{
 const debt={id:'7',creditorId:mode==='normal'?'0':'31',creditorName:mode==='normal'?'工厂':'客户甲',debtorId:mode==='normal'?'31':'0',debtorName:mode==='normal'?'客户甲':'工厂',debtKind:mode==='normal'?'ADVANCE':'BORROW',measurementMode:'WEIGHT',originalWeight:'100.00',remainingWeight:'60.00',settledWeight:'40.00',remainingQuantity:0,status:'OPEN',identity:{name:'金属原料'}}
 let saved:any
 await page.context().addCookies([{name:'Admin-Token',value:'debt-source-ui',url:process.env.STORAGE_TEST_BASE_URL||'http://127.0.0.1:5202'}])
 await page.route('**/*-api/**',async route=>{
  const path=new URL(route.request().url()).pathname.replace(/^\/(dev|prod)-api/,'');let body:any={code:200,data:[],rows:[],total:0}
  if(path==='/getInfo')body={code:200,user:{userId:1,userName:'admin'},roles:['admin'],permissions:['*:*:*']}
  if(path==='/metal/options/factories')body={code:200,data:[{id:'101',name:'工厂甲'}]}
  if(path==='/metal/storage-orders/options')body={code:200,data:{warehouses:[{id:'11',name:'原料库'}],customers:[{id:'31',name:'客户甲'},{id:'32',name:'客户乙'}],stocks:[{id:'41',warehouseId:'11',category:'RAW',netWeight:'100.00',measurementMode:'WEIGHT',identity:{name:'可归还原料'}}]}}
  if(path==='/metal/material-debts/list')body={code:200,rows:[debt],total:1}
  if(path==='/metal/material-debts/7')body={code:200,data:debt}
  if(path==='/metal/storage-orders'&&route.request().method()==='POST'){saved=route.request().postDataJSON();body={code:200,data:{...saved,id:'91'}}}
  await route.fulfill({json:body})
 })
 await page.goto('/metal/storage-orders');await page.getByRole('combobox',{name:'所属工厂',exact:true}).press('ArrowDown');await page.getByRole('option',{name:'工厂甲',exact:true}).click()
 await page.getByRole('button',{name:'借还料账',exact:true}).click();await page.getByRole('dialog',{name:'材料借还账',exact:true}).getByRole('button',{name:'办理归还',exact:true}).click()
 const editor=page.getByRole('dialog',{name:'添加料权归还单',exact:true});await editor.getByRole('combobox',{name:'仓库/库房',exact:true}).press('ArrowDown');await page.getByRole('option',{name:'原料库',exact:true}).click();await page.keyboard.press('Escape');await editor.getByRole('button',{name:'添加物料',exact:true}).click()
 const line=page.getByRole('dialog',{name:'物料明细',exact:true});await line.getByRole('combobox',{name:'库存物料',exact:true}).press('ArrowDown');await page.getByRole('option').filter({hasText:'可归还原料'}).click()
 await line.getByRole('textbox',{name:'实测净重（g）',exact:true}).fill('10.00');await line.locator('.el-table__body-wrapper .el-input__inner').last().fill('10.00')
 if(mode==='normal'){
  await line.getByRole('textbox',{name:'实测净重（g）',exact:true}).fill('1.00');await line.getByRole('button',{name:'确认',exact:true}).click()
  await expect(line).toContainText('归还核销明细合计必须与单据净重、件数一致');expect(saved).toBeUndefined()
  await line.getByRole('textbox',{name:'实测净重（g）',exact:true}).fill('10.00')
 }
 if(mode!=='normal'){
  const borrow=line.getByRole('combobox',{name:'借用其他客户料权归还（可选）',exact:true});await borrow.press('ArrowDown');await page.getByRole('option',{name:'客户乙',exact:true}).click()
  await expect(line.locator('.owner-control')).toContainText('客户乙')
  if(mode==='clear'){const field=line.locator('.el-form-item').filter({hasText:'借用其他客户料权归还（可选）'});await field.locator('.el-select').hover();await field.locator('.el-select__clear').click();await expect(line.locator('.owner-control')).toContainText('工厂')}
 }
 await line.getByRole('button',{name:'确认',exact:true}).click();await editor.getByRole('button',{name:'保存草稿',exact:true}).click();await expect.poll(()=>saved?.lines?.length).toBe(1)
 expect(saved.lines[0].ownerId).toBe(mode==='normal'?'31':mode==='borrow'?'32':'0')
 expect(saved.lines[0].borrowOwnerId||'').toBe(mode==='borrow'?'32':'')
})
