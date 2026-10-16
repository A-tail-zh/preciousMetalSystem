import {test,expect} from '@playwright/test'
import {login,api} from '../../tools/local-test-client.mjs'
test('read-only smoke across main modules and work report details',async({page,context})=>{
 test.setTimeout(90000)
 const token=await login();const errors:string[]=[];page.on('pageerror',e=>errors.push(e.message))
 try{
  await context.addCookies([{name:'Admin-Token',value:token,url:'http://127.0.0.1:5173'}])
  for(const path of ['/index','/metal/factory','/metal/product','/metal/production-line','/metal/material','/metal/process','/metal/quotation','/metal/work-order','/metal/inventory','/metal/warehouse-order','/system/user','/system/role','/metal/warning-record','/audit/operations','/audit/runtime']){
   await page.goto(path);await page.waitForLoadState('networkidle');expect(await page.locator('body').innerText()).not.toContain('Internal Server Error')
  }
  const orders=(await api('/metal/work-order/list?pageNum=1&pageSize=16',token)).rows
  let target:any
  for(const order of orders){const summaries=await api('/metal/work-report/summary?workOrderId='+order.id,token);if(summaries.data?.length){target=order;break}}
  expect(target,'existing report fixture required').toBeTruthy()
  await page.goto('/metal/work-order?id='+target.id)
  const view=page.locator('.reports .el-table__body .el-table__row').first().getByRole('button',{name:'查看',exact:true});await view.click()
  const dialog=page.getByRole('dialog',{name:'查看报工',exact:true});await expect(dialog).toBeVisible();await expect(dialog.locator('.material-report .el-table__row').first()).toBeVisible()
  await dialog.getByRole('button',{name:'确认',exact:true}).click();await expect(dialog).not.toBeVisible();expect(errors).toEqual([])
 }finally{await api('/logout',token,'POST')}
})
