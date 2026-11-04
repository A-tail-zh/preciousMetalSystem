import {test,expect,type Page,type Locator} from '@playwright/test'
import {warehouseFixture} from '../../tools/warehouse-test-fixture.mjs'
import {api,ok,detail,sql} from '../../tools/local-test-client.mjs'
import {selectWarehouseOption} from './helpers/warehouse-select'

test('real raw detail and lost adjustment response across four widths',async({page})=>{
 test.setTimeout(120000)
 expect(process.env.TEST_MYSQL_PORT).toBe('3320');expect(sql('SELECT @@port')).toBe('3320')
 const f=await warehouseFixture()
 try{
  const incoming=await f.post('RAW_IN',[{category:'RAW',referenceId:f.mat1,netWeight:'100',quantity:0}])
  const id=(await detail('/metal/warehouse-order',incoming,f.admin)).lines[0].inventoryId
  const code='WH_M1_'+f.suffix,company=f.prefix+'公司A'
  await page.context().addCookies([{name:'Admin-Token',value:f.admin,url:'http://127.0.0.1:5180'}]);await page.goto('/metal/inventory')
  const row=page.locator('.warehouse-table .el-table__body .el-table__row').filter({hasText:code})
  for(const width of [1920,1440,768,390]){
   await page.setViewportSize({width,height:1000});await expect(row).toContainText(company)
   await row.getByRole('button',{name:'查看详情',exact:true}).click();const d=page.getByRole('dialog',{name:'库存管理详情',exact:true})
   await expect(d).toContainText('剩余总量：100.00 g');await expect(d).not.toContainText('数量');await expect(d).not.toContainText('件');await expect(d).toContainText(incoming?code:'')
   await page.screenshot({path:'../.runtime/raw-inventory-20261102/real-detail-'+width+'.png',fullPage:true,animations:'disabled'});await d.getByRole('button',{name:'关闭',exact:true}).click()
  }
  await page.setViewportSize({width:1440,height:1000});await row.getByRole('button',{name:'库存调整',exact:true}).click();const d=page.getByRole('dialog',{name:'库存调整',exact:true})
  await d.getByLabel('净重差额(g)',{exact:true}).fill('5');const person=f.prefix+'角色A｜'+f.prefix+'负责人A｜'+f.phoneA
  for(const label of ['经手人','仓管员']){
   const field=d.locator('.el-form-item').filter({has:page.locator('label').filter({hasText:new RegExp('^'+label+'$')})});await selectWarehouseOption(page,field,person)
  }
  await d.getByLabel('调整原因',{exact:true}).fill('隔离浏览器实测调整');let posted=false,calls=0
  await page.route('**/dev-api/metal/inventory/'+id+'/adjust',async r=>{calls++;if(calls===1){const response=await r.fetch();expect((await response.json()).code).toBe(200);posted=true;await r.abort('failed')}else await r.continue()})
  await d.getByRole('button',{name:'确认',exact:true}).click();await expect.poll(()=>posted).toBe(true);await expect(d.getByRole('button',{name:'确认',exact:true})).toBeEnabled();await expect(d.getByLabel('调整原因',{exact:true})).toHaveValue('隔离浏览器实测调整')
  await d.getByRole('button',{name:'确认',exact:true}).click();await expect(d).not.toBeVisible();await expect(row).toContainText('105.00');expect(calls).toBe(2)
  const current=await detail('/metal/inventory',id,f.admin);expect(Number(current.netWeight)).toBe(105);expect(current.ledger.filter((l:any)=>Number(l.deltaWeight)===5)).toHaveLength(1)
 }finally{await f.cleanup()}
})
