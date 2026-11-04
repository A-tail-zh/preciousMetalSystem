import assert from 'node:assert/strict'
import {randomUUID,randomBytes} from 'node:crypto'
import {writeFileSync,mkdirSync} from 'node:fs'
import {fileURLToPath} from 'node:url'
import {chromium,expect} from '../frontend/node_modules/@playwright/test/index.mjs'
import {warehouseFixture} from './warehouse-test-fixture.mjs'
import {api,ok,detail,login} from './local-test-client.mjs'
const root=fileURLToPath(new URL('../.runtime/settled-report-account-review/live/',import.meta.url));mkdirSync(root,{recursive:true})
const f=await warehouseFixture({completionPolicy:'SINGLE_OUTPUT_RETURN'});let browser;const checks=[]
const check=(v,m)=>{assert.ok(v,m);checks.push(m);console.log('PASS '+m)}
try{
 const raw=await detail('/metal/warehouse-order',await f.post('RAW_IN',[{category:'RAW',referenceId:f.mat1,netWeight:'10',quantity:0}]),f.admin),b=await f.createBatch(f.processA,1)
 await f.post('PRODUCTION_OUT',[{inventoryId:raw.lines[0].inventoryId,netWeight:'10',quantity:0}],{batchId:b});await f.post('SEMI_IN',[{category:'SEMI',referenceId:f.productA,netWeight:'9',quantity:1}],{batchId:b})
 const ctx=ok(await api('/metal/process-batch/'+b+'/completion-context',f.admin)).data;ok(await api('/metal/process-batch/'+b+'/confirm-return',f.admin,'POST',{requestId:randomUUID(),version:ctx.version,mode:'NONE',documents:[],completedQuantity:1,physicalChecked:true,reason:'多账号报工按钮只读验收'}))
 const menus=ok(await api('/metal/options/menus',f.admin)).data,accounts=[{name:'超级管理员',token:f.admin,view:true},{name:'无报工查看权限的工厂人员',token:await login(f.phoneA,f.password),view:false}]
 for(const reverse of [false,true]){
  const perms=['metal:work-order:list','metal:work-order:query','metal:work-report:query',...(reverse?['metal:process-batch:reverse']:[])],ids=new Set(menus.filter(m=>perms.includes(m.perms)).map(m=>m.id))
  for(const p of perms)assert(menus.some(m=>m.perms===p),'missing permission '+p)
  for(const id of [...ids]){let parent=menus.find(m=>m.id===id)?.parentId;while(parent&&parent!=='0'){ids.add(parent);parent=menus.find(m=>m.id===parent)?.parentId}}
  const role=String(ok(await api('/system/role',f.admin,'POST',{roleName:f.prefix+(reverse?'可冲销查看员':'报工只读员'),factoryId:f.factoryA,roleSort:1,status:'0',menuIds:[...ids]})).id);f.made.roles.push(role)
  const phone='132'+String(randomBytes(4).readUInt32BE()%100000000).padStart(8,'0'),id=String(ok(await api('/system/user',f.admin,'POST',{phonenumber:phone,nickName:f.prefix+(reverse?'纠错人员':'只读人员'),factoryId:f.factoryA,roleIds:[role],status:'0',password:f.password})).id);f.made.users.push(id)
  const token=await login(phone,f.password),info=ok(await api('/getInfo',token));assert(info.permissions.includes('metal:work-report:query'));assert.equal(info.permissions.includes('metal:process-batch:reverse'),reverse);accounts.push({name:reverse?'具备冲销权限的查看员':'报工只读员',token,view:true})
 }
 browser=await chromium.launch({channel:'chrome'})
 for(const [i,account] of accounts.entries()){
  const context=await browser.newContext({viewport:{width:i===2?390:1440,height:950}});await context.addCookies([{name:'Admin-Token',value:account.token,url:'http://127.0.0.1:5173'}]);const page=await context.newPage();let reads=0;page.on('request',r=>{if(r.url().includes('/consumption/'))reads++})
  await page.goto('http://127.0.0.1:5173/metal/work-order?id='+f.wo);const reports=page.locator('.reports');await expect(reports).toContainText(f.prefix+'粉料');check(await reports.getByRole('button',{name:'编辑',exact:true}).count()===0,account.name+' 已结算报工无编辑按钮')
  const view=reports.getByRole('button',{name:'查看',exact:true});await expect(view).toHaveCount(account.view?1:0);check(await reports.getByRole('columnheader',{name:'操作',exact:true}).count()===(account.view?1:0),account.name+' 查看及操作列按权限显示')
  if(account.view){const response=page.waitForResponse(r=>r.url().includes('/consumption/steps?'));await view.click();const reply=await response;ok(await reply.json());const u=new URL(reply.url());check(u.searchParams.get('workOrderId')===f.wo&&u.searchParams.get('batchScope')==='SETTLED',account.name+' 查看实际查询本工单已结算明细');const dialog=page.getByRole('dialog',{name:'工序消耗明细',exact:true});await expect(dialog).toBeVisible();await expect(dialog.locator('.consumption-steps')).toContainText(f.prefix+'粉料');check(await dialog.getByRole('button',{name:'编辑',exact:true}).count()===0&&await dialog.getByRole('button',{name:'批次纠错（冲销）',exact:true}).count()===0,account.name+' 查看弹窗只读且明细正常');await page.screenshot({path:root+'account-'+i+'.png',fullPage:true})}
  else check(reads===0,account.name+' 不发起明细查询')
  await context.close();if(account.token!==f.admin)await api('/logout',account.token,'POST')
 }
 writeFileSync(root+'result.json',JSON.stringify({passed:checks.length,checks,workOrderNo:f.work.orderNo},null,2));console.log('PASSED '+checks.length)
}finally{if(browser)await browser.close();await f.cleanup();console.log('CLEANED isolated accounts and business fixture; audit retained')}
