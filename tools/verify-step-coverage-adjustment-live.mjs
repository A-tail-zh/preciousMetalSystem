import assert from 'node:assert/strict'
import {randomUUID} from 'node:crypto'
import {mkdirSync,writeFileSync} from 'node:fs'
import {fileURLToPath} from 'node:url'
import {chromium,expect} from '../frontend/node_modules/@playwright/test/index.mjs'
import {warehouseFixture} from './warehouse-test-fixture.mjs'
import {api,ok,detail,sql} from './local-test-client.mjs'
const root=fileURLToPath(new URL('../.runtime/step-coverage-adjustment/',import.meta.url));mkdirSync(root,{recursive:true})
const f=await warehouseFixture({completionPolicy:'SINGLE_OUTPUT_RETURN'});let browser;const checks=[],results=[]
const check=(v,m)=>{assert.ok(v,m);checks.push(m);console.log('PASS '+m)}
const flow=async w=>ok(await api('/metal/work-order/'+w.id+'/semi-flow',f.admin)).data
const context=async b=>ok(await api('/metal/process-batch/'+b+'/completion-context',f.admin)).data
const processes=[f.processA,f.processB]
try{
 for(const [i,name] of ['碎料','粉料','融定','焊接'].entries()){
  if(i<2){const p=await detail('/metal/process',processes[i],f.admin);ok(await api('/metal/process',f.admin,'PUT',{...p,name:f.prefix+name}))}
  else{const id=String(ok(await api('/metal/process',f.admin,'POST',{name:f.prefix+name,processType:i===3?8:1})).id);processes.push(id);f.made.processes.push(id)}
 }
 browser=await chromium.launch({channel:'chrome'});const session=await browser.newContext({viewport:{width:1600,height:1000}});await session.addCookies([{name:'Admin-Token',value:f.admin,url:'http://127.0.0.1:5173'}]);const page=await session.newPage()
 for(const mode of ['PIECE','WEIGHT']){
  const product=await detail('/metal/product',f.productA,f.admin);product.processes=processes.map((processId,i)=>({processId,loss:'0.1',measurementMode:i===1?mode:'PIECE'}));ok(await api('/metal/product',f.admin,'PUT',product))
  const body=f.body();body.items[0].quantity=33;body.items[0].plans=processes.map((processId,i)=>({processId,startTime:`2026-10-14 ${String(9+i).padStart(2,'0')}:00:00`,endTime:`2026-10-14 ${String(10+i).padStart(2,'0')}:00:00`}))
  const w=await detail('/metal/work-order',await f.createOrder(body),f.admin);check(w.items[0].plans[1].measurementMode===mode,mode+' 粉料工序使用工单快照中的计量模式')
  let input=(await detail('/metal/warehouse-order',await f.post('RAW_IN',[{category:'RAW',referenceId:f.mat1,netWeight:'110',quantity:0,organization:'COVERAGE_'+w.id}]),f.admin)).lines[0].inventoryId
  for(let i=0;i<4;i++){
   const weight=i===1&&mode==='WEIGHT',qty=i===0?1:33
   const b=String(ok(await api('/metal/process-batch',f.admin,'POST',{requestId:randomUUID(),workOrderId:w.id,itemId:w.items[0].id,processId:processes[i],plannedQuantity:qty})).id)
   const issuedWeight=110-i*10
   const previousWeight=i===2&&mode==='WEIGHT'
   await f.post('PRODUCTION_OUT',[{inventoryId:input,netWeight:String(issuedWeight),...(!previousWeight?{quantity:i===0?0:mode==='WEIGHT'&&i===1?1:qty}:{})}],{batchId:b})
   const output=await detail('/metal/warehouse-order',await f.post(i===3?'FINISHED_IN':'SEMI_IN',[{category:i===3?'FINISHED':'SEMI',referenceId:f.productA,netWeight:String(issuedWeight-10),...(weight?{referenceQuantity:1}:{quantity:qty})}],{batchId:b}),f.admin)
   const c=await context(b);ok(await api('/metal/process-batch/'+b+'/confirm-return',f.admin,'POST',{requestId:randomUUID(),version:c.version,mode:'NONE',documents:[],physicalChecked:true,...(!weight?{completedQuantity:qty}:{}),reason:'截图工序覆盖场景隔离验收'}));input=output.lines[0].inventoryId
   if((mode==='PIECE'&&i===0)||(mode==='WEIGHT'&&i===1)){
    const before=await flow(w),stock=await detail('/metal/inventory',input,f.admin)
    ok(await api('/metal/inventory/'+input+'/adjust',f.admin,'POST',{requestId:randomUUID(),version:stock.version,operatorId:f.ownerA,keeperId:f.ownerA,deltaWeight:'0',deltaQuantity:mode==='PIECE'?32:0,...(mode==='WEIGHT'?{referenceQuantity:33}:{}),reason:'复现前道1件与后道33件显示'}))
    const after=await flow(w),adjusted=await detail('/metal/inventory',input,f.admin)
    check(JSON.stringify(before.progress)===JSON.stringify(after.progress),mode+' 库存调整不改写历史各工序报工')
    check(Number(mode==='PIECE'?adjusted.quantity:adjusted.referenceQuantity)===33,mode+' 实时库存件数或参考件数调整到33')
   }
  }
  const final=await flow(w),progress=final.progress
  check(progress.length===4&&progress[0].localQuantity===1,mode+' 第一工序历史加工仍为1件')
  if(mode==='PIECE')check(progress.slice(1).every(x=>x.localQuantity===33)&&progress.every(x=>x.externalQuantity===0&&x.skippedQuantity===0),'PIECE 可复现1、33、33、33，后道33来自真实领料产出及结算')
  else check(progress[1].measurementMode==='WEIGHT'&&progress[1].localQuantity==null&&Number(progress[1].outputWeight)===90&&progress[2].localQuantity===33&&progress[3].localQuantity===33,'WEIGHT 粉料记录90g，不把参考33当作加工33件')
  check(final.workOrder.status==='IN_PROGRESS',mode+' 不会因最后工序33件就错误自动完成整单')
  await page.goto('http://127.0.0.1:5173/metal/work-order?id='+w.id);const table=page.locator('.semi-panel .el-table').first();await expect(table).toContainText(f.prefix+'碎料');await expect(table.locator('.el-table__body tr')).toHaveCount(4);await expect(table.locator('.el-table__body tr').nth(0).locator('td').nth(2)).toHaveText('1件')
  for(let i=1;i<4;i++)await expect(table.locator('.el-table__body tr').nth(i).locator('td').nth(2)).toHaveText(mode==='WEIGHT'&&i===1?'90.000000g':'33件')
  check(true,mode+' 浏览器工单详情显示与实际接口一致')
  await table.scrollIntoViewIfNeeded();await page.screenshot({path:root+mode.toLowerCase()+'.png',fullPage:true});results.push({powderMode:mode,workOrderNo:w.orderNo,status:final.workOrder.status,progress})
 }
 writeFileSync(root+'result.json',JSON.stringify({passed:checks.length,checks,results},null,2));console.log('PASSED '+checks.length)
}finally{if(browser)await browser.close();await f.cleanup();console.log('CLEANED isolated business data; audit retained')}
