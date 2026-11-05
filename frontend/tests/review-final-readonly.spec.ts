import {warehouseFixture} from '../../tools/warehouse-test-fixture.mjs'
import {test,expect} from './helpers/isolated-live-test'
import {login,api,detail,ok} from '../../tools/local-test-client.mjs'
test('read-only smoke across main modules and work report details',async({page,context})=>{
 test.setTimeout(90000)
 const f=await warehouseFixture(),token=f.admin;const rawDoc=await f.post('RAW_IN',[{category:'RAW',referenceId:f.mat1,netWeight:'100',quantity:0}]),raw=(await detail('/metal/warehouse-order',rawDoc,token)).lines[0].inventoryId,batch=await f.createBatch(f.processA,5);await f.post('PRODUCTION_OUT',[{inventoryId:raw,netWeight:'100',quantity:0}],{batchId:batch});await f.post('SEMI_IN',[{category:'SEMI',referenceId:f.productA,netWeight:'95',quantity:5}],{batchId:batch});ok(await api('/metal/process-batch/'+batch+'/settle',token,'POST',{version:(await detail('/metal/process-batch',batch,token)).version,completedQuantity:5}));const errors:string[]=[];page.on('pageerror',e=>errors.push(e.message))
 try{
  await context.addCookies([{name:'Admin-Token',value:token,url:'http://127.0.0.1:5173'}])
  for(const path of ['/index','/metal/factory','/metal/product','/metal/production-line','/metal/material','/metal/process','/metal/quotation','/metal/work-order','/metal/inventory','/metal/warehouse-order','/system/user','/system/role','/metal/warning-record','/audit/operations','/audit/runtime']){
   await page.goto(path);await page.waitForLoadState('networkidle');expect(await page.locator('body').innerText()).not.toContain('Internal Server Error')
  }
  const target={id:f.wo};
  await page.goto('/metal/work-order?id='+target.id)
  const view=page.locator('.reports .el-table__body .el-table__row').first().getByRole('button',{name:'查看',exact:true});await view.click()
  const dialog=page.getByRole('dialog',{name:'查看报工',exact:true});await expect(dialog).toBeVisible();await expect(dialog.locator('.report-summary')).toContainText('实际损耗：5.00 g');await expect(dialog).toContainText('本批完成：5 件');await expect(dialog.locator('.material-report .el-table__row').first()).toContainText('WH_M1_'+f.suffix)
  await dialog.getByRole('button',{name:'确认',exact:true}).click();await expect(dialog).not.toBeVisible();expect(errors).toEqual([])
 }finally{await f.cleanup()}
})
