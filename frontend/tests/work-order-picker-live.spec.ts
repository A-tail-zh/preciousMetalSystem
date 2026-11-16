import{test,expect}from'@playwright/test'
import{workOrderFixture}from'../../tools/work-order-test-fixture.mjs'
test('工单底部时间面板不被表格或弹窗裁剪',async({page,context})=>{
 test.skip(process.env.LIVE_WORK_ORDER!=='1','需要本地工单服务');test.setTimeout(60000)
 const f=await workOrderFixture()
 try{
  const body=f.body();body.items=[body.items[0],structuredClone(body.items[0]),structuredClone(body.items[0])];await f.createOrder(body)
  await context.addCookies([{name:'Admin-Token',value:f.admin,url:'http://127.0.0.1:5173'}])
  for(const width of [1442,1920]){
   await page.setViewportSize({width,height:width===1442?1026:1080});await page.goto('/metal/work-order');await page.getByRole('row').filter({hasText:f.prefix}).getByRole('button',{name:'编辑',exact:true}).click()
   const dialog=page.getByRole('dialog',{name:'编辑工单',exact:true});const input=dialog.getByPlaceholder('请选择结束时间').last();await input.scrollIntoViewIfNeeded();await input.click()
   const popper=page.locator('.el-picker__popper:visible');await expect(popper).toBeVisible();expect(await popper.evaluate(el=>!el.closest('.order-dialog'))).toBe(true)
   const bounds=await popper.boundingBox();expect(bounds!.y).toBeGreaterThanOrEqual(0);expect(bounds!.y+bounds!.height).toBeLessThanOrEqual(width===1442?1026:1080)
   const cell=popper.locator('.el-date-table td.available').filter({hasText:/^15$/}).first();await expect(cell).toBeVisible();expect(await cell.evaluate(el=>{const r=el.getBoundingClientRect();return el.contains(document.elementFromPoint(r.x+r.width/2,r.y+r.height/2))})).toBe(true);await cell.click()
   await page.screenshot({path:'../docs/screenshots/work-order-picker-'+width+'.png',fullPage:true});await dialog.getByRole('button',{name:'取消',exact:true}).click()
  }
 }finally{await f.cleanup()}
})
