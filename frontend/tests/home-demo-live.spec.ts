import {test,expect} from '@playwright/test'
import {uiIdentityFixture} from '../../tools/ui-identity-fixture.mjs'
import {login,api,ok} from '../../tools/local-test-client.mjs'
test('数据看板默认首页、演示角色登录和后台入口',async({page,context})=>{
 test.skip(process.env.LIVE_DEMO!=='1','需先创建本地演示人员');test.setTimeout(60000)
 const demo=await uiIdentityFixture();try{
 for(const account of [{phone:'admin',role:'超级管理员'},...demo.accounts]){
  const token=await login(account.phone,account.phone==='admin'?undefined:demo.password)
  await context.clearCookies();await context.addCookies([{name:'Admin-Token',value:token,url:'http://127.0.0.1:5173'}]);await page.goto('/index')
  await expect(page.locator('.dashboard-header')).toContainText('贵金属加工系统');await expect(page.locator('.prototype-sidebar')).toHaveCount(0);await expect(page.locator('.summary-card')).toHaveCount(3);await expect(page.locator('.dashboard-chart')).toHaveCount(4)
  await expect(page.locator('.dashboard')).toContainText('近30日金属损耗趋势');await page.reload();await expect(page.locator('.dashboard-header')).toBeVisible()
  if(account.role==='超级管理员'){await page.screenshot({path:'../docs/screenshots/dashboard-home.png',fullPage:true})}
  await page.getByRole('button',{name:'后台管理',exact:true}).click();await expect(page.locator('.prototype-sidebar')).toBeVisible();await expect(page.getByRole('link',{name:'数据看板',exact:true})).toBeVisible();await expect(page.getByText('单据模板配置',{exact:true})).toHaveCount(0)
  if(account.role==='只读查看员'){await page.goto('/metal/work-order');await expect(page.locator('.order-table')).toBeVisible();await expect(page.locator('.order-toolbar').getByRole('button',{name:'添加',exact:true})).toHaveCount(0)}
  if(account.role!=='超级管理员'){const info=ok(await api('/getInfo',token));expect(info.permissions).not.toContain('*:*:*')}
  await api('/logout',token,'POST')
 }
}finally{await demo.cleanup()}
})
