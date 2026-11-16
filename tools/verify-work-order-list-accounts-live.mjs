import assert from 'node:assert/strict'
import {mkdirSync,writeFileSync} from 'node:fs'
import {fileURLToPath} from 'node:url'
import {chromium,expect} from '../frontend/node_modules/@playwright/test/index.mjs'
import {workOrderFixture} from './work-order-test-fixture.mjs'
import {api,ok,detail,login} from './local-test-client.mjs'
const root=fileURLToPath(new URL('../.runtime/work-order-list-session/live/',import.meta.url));mkdirSync(root,{recursive:true})
const f=await workOrderFixture(),checks=[],tokens=[];let browser
const check=(value,message)=>{assert.ok(value,message);checks.push(message);console.log('PASS '+message)}
try{
 const aId=await f.createOrder(f.body()),bId=await f.createOrder(f.body(true)),aOrder=await detail('/metal/work-order',aId,f.admin),bOrder=await detail('/metal/work-order',bId,f.admin)
 const a=await login(f.phoneA,f.password),b=await login(f.phoneB,f.password);tokens.push(a,b)
 const aInfo=ok(await api('/getInfo',a)),bInfo=ok(await api('/getInfo',b))
 check((await api('/metal/work-order/'+aId,b)).code!==200,'B cannot directly query A factory order')
 browser=await chromium.launch({channel:'chrome'});const context=await browser.newContext({viewport:{width:1440,height:950}});await context.addCookies([{name:'Admin-Token',value:a,url:'http://127.0.0.1:5173'}]);const page=await context.newPage(),reads=[]
 page.on('request',r=>{if(r.url().includes('/metal/work-order/list?'))reads.push({authorizationMatches:r.headers().authorization==='Bearer '+a?'A':r.headers().authorization==='Bearer '+b?'B':'other',url:r.url()})})
 await page.goto('http://127.0.0.1:5173/metal/work-order');const table=page.locator('.order-table');await expect(table).toContainText(aOrder.orderNo);await expect(table).not.toContainText(bOrder.orderNo);check(true,'A only sees own factory order')
 await page.locator('.el-table__body .el-checkbox').click()
 await table.getByRole('button',{name:'编辑',exact:true}).click();const editor=page.getByRole('dialog',{name:'编辑工单',exact:true});await expect(editor).toBeVisible();await page.evaluate(async token=>{const auth='/src/utils/auth.ts';(await import(auth)).setToken(token);window.dispatchEvent(new Event('focus'))},b);await expect(editor).not.toBeVisible();await expect(table).not.toContainText(aOrder.orderNo);check(true,'real Cookie-only account switch closes old form and clears rows')
 const change=async(token,info)=>page.evaluate(async({token,info})=>{const auth='/src/utils/auth.ts',store='/src/store/modules/user.ts';(await import(auth)).setToken(token);const u=(await import(store)).default();u.sessionReady=false;u.token=token;u.id=info.user.userId;u.roles=info.roles;u.permissions=info.permissions;u.sessionReady=true},{token,info})
 await change(b,bInfo);await expect(table).toContainText(bOrder.orderNo);await expect(table).not.toContainText(aOrder.orderNo);check(true,'real A to B switch removes old rows and reads B order');await expect(page.locator('.order-toolbar').getByRole('button',{name:'删除',exact:true})).toBeDisabled();check(true,'old selection cleared')
 for(const width of [1920,1440,768,390]){await page.setViewportSize({width,height:950});await expect(table).toContainText(bOrder.orderNo);await expect(page.locator('.order-pagination')).toBeVisible();await page.screenshot({path:root+'list-'+width+'.png',fullPage:true});check(true,'real list layout '+width)}
 const newA=await login(f.phoneA,f.password);tokens.push(newA);await change(a,aInfo);await expect(table).toContainText(aOrder.orderNo);const before=reads.length;await change(newA,aInfo);await expect.poll(()=>reads.length).toBeGreaterThan(before);await expect(table).toContainText(aOrder.orderNo);check(true,'same account with replacement real token reloads')
 const role=await detail('/system/role',f.roleA,f.admin);ok(await api('/system/role',f.admin,'PUT',{...role,menuIds:[]}));const denied=await login(f.phoneA,f.password);tokens.push(denied);const deniedInfo=ok(await api('/getInfo',denied));check(!deniedInfo.permissions.includes('metal:work-order:list'),'same factory account actually has no list permission');const previous=reads.length;await change(denied,deniedInfo);await expect(table).not.toContainText(aOrder.orderNo);await page.waitForTimeout(150);check(reads.length===previous,'no-list account clears data without list request')
 await change(b,bInfo);await expect(table).toContainText(bOrder.orderNo);check(true,'restored permitted identity reads its own order')
 const other=await context.newPage();await other.goto('http://127.0.0.1:5173/login');await other.evaluate(async()=>{const auth='/src/utils/auth.ts';(await import(auth)).removeToken()});const beforeFocus=reads.length;await page.evaluate(()=>window.dispatchEvent(new Event('focus')));await expect(table).not.toContainText(bOrder.orderNo);check(reads.length===beforeFocus,'other tab cookie removal clears old rows without requests');await context.close()
 writeFileSync(root+'result.json',JSON.stringify({passed:checks.length,checks,requests:reads.map(r=>({account:r.authorizationMatches,url:new URL(r.url).pathname+new URL(r.url).search}))},null,2));console.log('PASSED '+checks.length)
}finally{if(browser)await browser.close();for(const token of tokens)await api('/logout',token,'POST');await f.cleanup();console.log('CLEANED isolated data; audit retained')}
