import {assertRegisteredIsolation} from '../../tools/isolated-test-environment.mjs'
import {test,expect,type Page} from './helpers/isolated-live-test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import {execFileSync} from 'node:child_process'
import {randomUUID} from 'node:crypto'
import {warehouseFlowFixture} from '../../tools/warehouse-flow-test-fixture.mjs'
import {api,ok,detail,sql} from '../../tools/local-test-client.mjs'
import {assertEnvironment,assertFixtureOwnership} from '../../tools/test-fixture-scope.mjs'

test('real warning handler persists across reopen, refresh and account switch',async({page,baseURL})=>{
 test.skip(process.env.LIVE_WARNING_HANDLER!=='1','需独立预警验收环境')
 test.setTimeout(180000)
 if(process.env.TEST_ISOLATED_RUN==='1')assertRegisteredIsolation();else{
 assert.equal(process.env.TEST_MYSQL_PORT,'3321')
 assert.match(process.env.TEST_DB_SCHEMA??'',/^heavy_metal_warning_20261103_[a-f0-9]{8}$/)
 assert.equal(process.env.TEST_API_BASE_URL,'http://127.0.0.1:8097')
 assert.equal(process.env.TEST_REDIS_PORT,'6397')
 assert.equal(baseURL,'http://127.0.0.1:5187')
 }
 const evidence=path.resolve('../.runtime/warning-handler-live-20261103')
 fs.mkdirSync(evidence,{recursive:true})
 const checks:string[]=[],errors:string[]=[]
 page.on('pageerror',error=>errors.push(error.message))
 let f:any
 try{
  f=await warehouseFlowFixture()
  fs.writeFileSync(path.join(evidence,'fixture.json'),JSON.stringify({prefix:f.prefix,made:f.made,environment:f.environment},null,2))
  assertEnvironment(f.environment,sql)
  assertFixtureOwnership(f.prefix,{roles:[f.roleA]},sql)
  sql(`INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT ${f.roleA},menu_id FROM sys_menu WHERE perms IN ('metal:warning-record:list','metal:warning-record:query','metal:warning-record:handle')`)
  const customerStock=await f.incoming('CUSTOMER','90'),factoryStock=await f.incoming('FACTORY','10')
  const batch=await detail('/metal/process-batch',f.flowBatchId,f.admin)
  const outbound=await f.submit('PRODUCTION_OUT',[{inventoryId:customerStock,netWeight:'90',quantity:0},{inventoryId:factoryStock,netWeight:'10',quantity:0}],{workOrderId:f.flowWorkOrderId,itemId:f.flowItem.id,processId:f.processB,batchId:f.flowBatchId,batchVersion:batch.version})
  let flow=await f.flow(outbound.flowId)
  flow=ok(await f.confirm(flow,flow.groups[0],f.processor)).data
  const lines=(await detail('/metal/warehouse-order',outbound.id,f.admin)).lines
  const currentBatch=await detail('/metal/process-batch',f.flowBatchId,f.admin)
  const wipContext=ok(await api('/metal/warehouse-flows/handover-context/'+f.flowBatchId,f.processor)).data
  flow=ok(await api('/metal/warehouse-flows/handover/'+f.flowBatchId,f.processor,'POST',{
   requestId:randomUUID(),version:currentBatch.version,wipBalances:wipContext.wipGroups.map((g:any)=>({groupKey:g.groupKey,remainingWeight:'0'})),orderDate:'2026-11-03',physicalChecked:true,residualMode:'RETURNED',reason:'预警处理人隔离验收',documents:[
    {type:'FINISHED_IN',warehouseId:f.warehouse,keeperId:f.ownerA,lines:[{category:'FINISHED',referenceId:f.productB,netWeight:'96',quantity:10}]},
    {type:'RAW_RETURN',warehouseId:f.warehouse,keeperId:f.ownerA,lines:[{category:'RAW',sourceLineId:lines.find((line:any)=>line.inventoryId===customerStock).id,netWeight:'2',quantity:0},{category:'RAW',sourceLineId:lines.find((line:any)=>line.inventoryId===factoryStock).id,netWeight:'1',quantity:0}]}
   ]
  })).data
  for(const group of flow.groups)flow=ok(await f.confirm(flow,group,f.keeper)).data
  assert.equal(flow.status,'POSTED')
  const warningId=sql(`SELECT w.warning_id FROM metal_warning_record w JOIN metal_work_report r ON r.report_id=w.report_id WHERE r.batch_id=${f.flowBatchId} AND w.factory_id=${f.factoryA}`)
  assert.match(warningId,/^\d+$/)
  checks.push('真实领料、加工交接及库管验收结算生成待处理预警')
  const loginPage=async(phone:string)=>{
   const captchaResponse=page.waitForResponse(response=>response.url().includes('/captchaImage'))
   await page.goto('/login?redirect=/metal/warning-record')
   const captcha=await (await captchaResponse).json()
   await page.getByPlaceholder('账号',{exact:true}).fill(phone)
   await page.getByPlaceholder('密码',{exact:true}).fill(f.password)
   if(captcha.captchaEnabled){
    const code=JSON.parse(execFileSync('D:/javaenv/Redis-x64-3.0.504/redis-cli.exe',['-h','127.0.0.1','-p',process.env.TEST_REDIS_PORT||'6397','--raw','get','captcha_codes:'+captcha.uuid],{encoding:'utf8'}).trim())
    await page.getByPlaceholder('验证码',{exact:true}).fill(String(code))
   }
   const loginResponse=page.waitForResponse(response=>new URL(response.url()).pathname==='/dev-api/login')
   await page.getByRole('button',{name:/^登\s*录$/}).click()
   const result=ok(await (await loginResponse).json())
   await expect(page.getByRole('link',{name:'预警记录',exact:true})).toBeVisible();await page.getByRole('link',{name:'预警记录',exact:true}).click();await expect(page.getByRole('heading',{name:'预警记录',exact:true,level:1})).toBeVisible()
   return result.token as string
  }
  const open=async()=>{
   await page.getByRole('row').filter({hasText:f.flowWork.orderNo}).getByRole('button',{name:/^(去处理|查看)$/}).click()
   return page.getByRole('dialog',{name:'预警记录详情',exact:true})
  }
  await page.setViewportSize({width:1442,height:1026})
  const handlerToken=await loginPage(f.phoneA)
  const handler=ok(await api('/getInfo',handlerToken)).user
  assert.equal(String(handler.userId),String(f.ownerA))
  assert.notEqual(handler.nickName,handler.userName)
  const dialog=await open()
  await expect(dialog.getByText('处理人：'+handler.nickName,{exact:true})).toBeVisible()
  await expect(dialog.getByText('处理人：'+handler.nickName,{exact:true}).locator('input,textarea,[contenteditable=true]')).toHaveCount(0)
  await page.screenshot({path:path.join(evidence,'pending-handler.png'),fullPage:true,animations:'disabled'})
  checks.push('账号A通过真实登录页登录，待处理页面显示其姓名且只读')
  const reason='处理人保存及跨账号查看隔离验收'
  await dialog.getByPlaceholder('填写处理结果').fill(reason)
  const submission=page.waitForResponse(response=>response.url().endsWith('/warning-record/'+warningId+'/handle'))
  await dialog.getByRole('button',{name:'确认处理',exact:true}).click()
  const response=await submission
  expect(response.request().postDataJSON()).toEqual({version:0,reason})
  ok(await response.json())
  await expect(dialog).not.toBeVisible()
  const saved=JSON.parse(sql(`SELECT JSON_OBJECT('handlerId',CAST(handler_id AS CHAR),'handlerName',handler_name,'result',result,'status',status,'handledTime',CAST(handled_time AS CHAR),'version',version) FROM metal_warning_record WHERE warning_id=${warningId} AND factory_id=${f.factoryA}`))
  assert.equal(saved.handlerId,String(handler.userId));assert.equal(saved.handlerName,handler.nickName)
  assert.equal(saved.result,reason);assert.equal(saved.status,'HANDLED');assert.ok(saved.handledTime)
  checks.push('真实处理接口成功，数据库保存账号A的ID、姓名、结果和处理时间')
  const verifyView=async(token:string)=>{
   const data=await detail('/metal/warning-record',warningId,token)
   assert.equal(data.handlerName,saved.handlerName);assert.equal(String(data.handlerId),saved.handlerId);assert.equal(data.result,reason)
   await open()
   await expect(dialog.getByText('处理结果：'+reason+'；处理人：'+saved.handlerName+'；时间：'+data.handledTime,{exact:true})).toBeVisible()
   await expect(dialog.getByPlaceholder('填写处理结果')).toHaveCount(0)
   await expect(dialog.getByRole('button',{name:'确认处理',exact:true})).toHaveCount(0)
  }
  await verifyView(handlerToken)
  await page.reload();await verifyView(handlerToken)
  checks.push('账号A重新打开及刷新后，接口和页面均回显原处理人')
  ok(await api('/logout',handlerToken,'POST'))
  await page.context().clearCookies()
  const viewerPhone=sql(`SELECT phonenumber FROM sys_user WHERE user_id=${f.processorId}`)
  const viewerToken=await loginPage(viewerPhone)
  const viewer=ok(await api('/getInfo',viewerToken)).user
  assert.notEqual(String(viewer.userId),String(handler.userId));assert.notEqual(viewer.nickName,handler.nickName)
  await verifyView(viewerToken)
  await expect(dialog.getByText('处理人：'+viewer.nickName,{exact:true})).toHaveCount(0)
  await page.screenshot({path:path.join(evidence,'other-account-saved-handler.png'),fullPage:true,animations:'disabled'})
  await page.reload();await verifyView(viewerToken)
  assert.equal(sql(`SELECT COUNT(*) FROM metal_warning_history WHERE warning_id=${warningId} AND action='HANDLED'`),'1')
  assert.equal(sql(`SELECT handler_name FROM metal_warning_record WHERE warning_id=${warningId}`),saved.handlerName)
  checks.push('同一浏览器切换到真实账号B并刷新，仍显示账号A；处理历史只有一次')
  ok(await api('/logout',viewerToken,'POST'))
  expect(errors).toEqual([])
  checks.push('浏览器无页面脚本异常')
  fs.writeFileSync(path.join(evidence,'result.json'),JSON.stringify({passed:checks.length,checks,environment:f.environment,warningId,handlerId:saved.handlerId,handlerName:saved.handlerName,viewerId:String(viewer.userId),viewerName:viewer.nickName,fixtureCleaned:false},null,2))
 }finally{
  if(f){
   for(const token of [f.keeper,f.processor])await api('/logout',token,'POST')
   await f.cleanup()
   assert.equal(sql(`SELECT COUNT(*) FROM metal_warning_record WHERE factory_id IN (${f.factoryA},${f.factoryB})`),'0')
   assert.equal(sql(`SELECT COUNT(*) FROM metal_work_order WHERE factory_id IN (${f.factoryA},${f.factoryB})`),'0')
   const resultFile=path.join(evidence,'result.json')
   if(fs.existsSync(resultFile)){const result=JSON.parse(fs.readFileSync(resultFile,'utf8'));result.fixtureCleaned=true;fs.writeFileSync(resultFile,JSON.stringify(result,null,2))}
  }
 }
})
