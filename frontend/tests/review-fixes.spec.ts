import {test,expect} from '@playwright/test'
import {login,api} from '../../tools/local-test-client.mjs'

test('credential requests and legacy credential cache are never persisted',async({page})=>{
 await page.addInitScript(()=>sessionStorage.setItem('sessionObj',JSON.stringify({url:'/fixture',data:JSON.stringify({oldPassword:'FictionalOld',newPassword:'FictionalNew'})})))
 await page.route('**/dev-api/system/user/profile/updatePwd',r=>r.fulfill({status:200,contentType:'application/json',body:JSON.stringify({code:500,msg:'intercepted fixture'})}))
 await page.goto('/login')
 const result=await page.evaluate(async()=>{const path='/src/utils/request.ts';const request=(await import(path)).default;const oldCacheGone=sessionStorage.getItem('sessionObj')===null;try{await request({url:'/system/user/profile/updatePwd',method:'put',data:{oldPassword:'FictionalOld',newPassword:'FictionalNew'}})}catch{}return{oldCacheGone,after:sessionStorage.getItem('sessionObj')}})
 expect(result.oldCacheGone).toBe(true);expect(result.after).toBeNull()
})

test('clearing audit date range restores default query instead of crashing',async({page,context})=>{
 const token=await login();try{await context.addCookies([{name:'Admin-Token',value:token,url:'http://127.0.0.1:5173'}]);const errors:string[]=[];page.on('pageerror',e=>errors.push(e.message));await page.goto('/audit/operations');await page.getByRole('heading',{name:'操作日志',exact:true}).waitFor();const picker=page.locator('.el-date-editor');await picker.locator('input').nth(0).fill('2026-10-14 00:00:00');await picker.locator('input').nth(1).fill('2026-10-16 00:00:00');await picker.locator('input').nth(1).press('Enter');await page.getByRole('heading',{name:'操作日志',exact:true}).click();await picker.hover();await picker.locator('.el-range__close-icon').click({force:true});const response=page.waitForResponse(r=>r.url().includes('/monitor/operlog/list'));await page.getByRole('button',{name:'查询',exact:true}).click();expect((await response).status()).toBe(200);expect(errors).toEqual([])}finally{await api('/logout',token,'POST')}
})

test('audit trace copy works when native clipboard is unavailable',async({page,context})=>{
 const token=await login();try{await page.addInitScript(()=>Object.defineProperty(navigator,'clipboard',{value:undefined,configurable:true}));await context.addCookies([{name:'Admin-Token',value:token,url:'http://127.0.0.1:5173'}]);const errors:string[]=[];page.on('pageerror',e=>errors.push(e.message));await page.goto('/audit/operations');await page.locator('.el-table__body .el-table__row').first().getByRole('button',{name:'详情',exact:true}).click();await page.getByRole('button',{name:'复制追踪ID',exact:true}).click();await expect(page.getByText('追踪ID已复制',{exact:true})).toBeVisible();expect(errors).toEqual([])}finally{await api('/logout',token,'POST')}
})
