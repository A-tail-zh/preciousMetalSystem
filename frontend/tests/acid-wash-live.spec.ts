import {assertRegisteredIsolation} from '../../tools/isolated-test-environment.mjs'
import {test,expect} from './helpers/isolated-live-test'
import {warehouseFixture} from '../../tools/warehouse-test-fixture.mjs'
import {sql} from '../../tools/local-test-client.mjs'

test('legacy acid entry is read-only for new starts at all business widths',async({page})=>{
 test.setTimeout(120000);if(process.env.TEST_ISOLATED_RUN==='1')assertRegisteredIsolation();else{expect(process.env.TEST_MYSQL_PORT).toBe('3320');expect(process.env.TEST_API_BASE_URL).toBe('http://127.0.0.1:8090')}expect(sql('SELECT @@port')).toBe(process.env.TEST_MYSQL_PORT)
 const f=await warehouseFixture()
 try{
  await page.context().addCookies([{name:'Admin-Token',value:f.admin,url:'http://127.0.0.1:5180'}]);await page.goto('/metal/work-order')
  await page.getByRole('button',{name:'历史酸洗记录',exact:true}).click();const dialog=page.getByRole('dialog',{name:'历史酸洗记录',exact:true})
  await expect(dialog).toContainText('新酸洗请在普通生产工单中安排完整工艺路线及酸洗工序');await expect(dialog.getByRole('button',{name:'新增酸洗领料',exact:true})).toHaveCount(0)
  for(const width of [1920,1440,768,390]){await page.setViewportSize({width,height:1000});expect(await dialog.evaluate(el=>el.scrollWidth<=el.clientWidth+1)).toBe(true)}
 }finally{await f.cleanup()}
})
