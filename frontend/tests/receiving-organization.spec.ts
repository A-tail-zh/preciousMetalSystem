import {test,expect} from '@playwright/test'
test('收货单位未填写时显示行客户，保留明确填写值且不修改原始数据',async({page})=>{
 await page.goto('/index')
 const result=await page.evaluate(async()=>{
  const {receivingOrganizationText}=await import('/src/utils/receiving-organization.ts')
  const rows=[{receivingOrganization:null,organization:'客户甲'},{receivingOrganization:'',organization:'客户乙'},{receivingOrganization:'  ',organization:'客户丙'},{receivingOrganization:'指定收货企业',organization:'客户甲'},{receivingOrganization:null,organization:null}]
  const before=JSON.stringify(rows)
  return {values:rows.map(receivingOrganizationText),unchanged:before===JSON.stringify(rows)}
 })
 expect(result.values).toEqual(['客户甲','客户乙','客户丙','指定收货企业',''])
 expect(result.unchanged).toBe(true)
})

import {warehouseFixture} from '../../tools/warehouse-test-fixture.mjs'
import {detail} from '../../tools/local-test-client.mjs'
test('真实单据详情的收货单位按各行客户兜底',async({page,context})=>{
 test.skip(process.env.LIVE_WAREHOUSE!=='1','需要本地后端');test.setTimeout(90000)
 const f=await warehouseFixture()
 try{
  const incoming=await f.post('RAW_IN',[{category:'RAW',referenceId:f.mat1,netWeight:'100',quantity:0}])
  const stock=(await detail('/metal/warehouse-order',incoming,f.admin)).lines[0].inventoryId
  const batch=await f.createBatch(f.processA,5)
  await f.post('PRODUCTION_OUT',[{inventoryId:stock,netWeight:'10',quantity:0}],{batchId:batch})
  const id=await f.post('SEMI_IN',[{category:'SEMI',referenceId:f.productA,netWeight:'2',quantity:1},{category:'SEMI',referenceId:f.productA,netWeight:'3',quantity:1,receivingOrganization:'指定收货企业'}],{batchId:batch})
  const doc=await detail('/metal/warehouse-order',id,f.admin)
  await context.addCookies([{name:'Admin-Token',value:f.admin,url:'http://127.0.0.1:5173'}])
  await page.goto('/metal/warehouse-order');await page.getByRole('tab',{name:'入库单',exact:true}).click();await page.getByRole('textbox',{name:'单据编号',exact:true}).fill(doc.orderNo);await page.getByRole('button',{name:'查询',exact:true}).click()
  const row=page.locator('.warehouse-table .el-table__body tr').filter({hasText:doc.orderNo})
  await row.getByRole('button',{name:'物料清单',exact:true}).click()
  const dialog=page.getByRole('dialog',{name:'仓储工单管理详情',exact:true})
  const lines=dialog.locator('.semi-order-detail .el-table__body tr')
  await expect(lines.nth(0).locator('td').nth(2)).toHaveText(f.work.customer)
  await expect(lines.nth(1).locator('td').nth(2)).toHaveText('指定收货企业')
 }finally{await f.cleanup()}
})
