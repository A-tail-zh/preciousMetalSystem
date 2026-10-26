import assert from 'node:assert/strict'
import {randomUUID} from 'node:crypto'
import {mkdirSync,writeFileSync} from 'node:fs'
import {fileURLToPath} from 'node:url'
import {chromium,expect} from '../frontend/node_modules/@playwright/test/index.mjs'
import {warehouseFixture} from './warehouse-test-fixture.mjs'
import {api,ok,detail,sql} from './local-test-client.mjs'
const root=fileURLToPath(new URL('../.runtime/in-process-semi-adjustment/',import.meta.url));mkdirSync(root,{recursive:true})
const f=await warehouseFixture({completionPolicy:'SINGLE_OUTPUT_RETURN'});let browser;const checks=[],results=[]
const check=(value,message)=>{assert.ok(value,message);checks.push(message);console.log('PASS '+message)}
const context=async b=>ok(await api('/metal/process-batch/'+b+'/completion-context',f.admin)).data
const createBatch=async(w,process)=>String(ok(await api('/metal/process-batch',f.admin,'POST',{requestId:randomUUID(),workOrderId:w.id,itemId:w.items[0].id,processId:process,plannedQuantity:33})).id)
const candidates=async w=>ok(await api(`/metal/warehouse/options/stocks?factoryId=${f.factoryA}&warehouseId=${f.warehouse}&workOrderId=${w.id}&itemId=${w.items[0].id}&processId=${f.processB}`,f.admin))
const flow=async w=>ok(await api('/metal/work-order/'+w.id+'/semi-flow',f.admin)).data
const postIssue=async(b,id,weight)=>api('/metal/warehouse-order',f.admin,'POST',{requestId:randomUUID(),factoryId:f.factoryA,warehouseId:f.warehouse,type:'PRODUCTION_OUT',orderDate:'2026-10-26',operatorIds:[f.ownerA],keeperId:f.ownerA,recipientId:f.ownerA,batchId:b,batchVersion:(await context(b)).version,lines:[{inventoryId:id,netWeight:String(weight)}]})
async function create(){const body=f.body();body.items[0].quantity=33;const w=await detail('/metal/work-order',await f.createOrder(body),f.admin);const b=await createBatch(w,f.processA)
 const raw=await detail('/metal/warehouse-order',await f.post('RAW_IN',[{category:'RAW',referenceId:f.mat1,netWeight:'110',quantity:0,organization:'ADJ_NEXT_'+w.id}]),f.admin)
 await f.post('PRODUCTION_OUT',[{inventoryId:raw.lines[0].inventoryId,netWeight:'110',quantity:0}],{batchId:b})
 const output=await detail('/metal/warehouse-order',await f.post('SEMI_IN',[{category:'SEMI',referenceId:f.productA,netWeight:'100',referenceQuantity:1}],{batchId:b}),f.admin)
 const ctx=await context(b);ok(await api('/metal/process-batch/'+b+'/confirm-return',f.admin,'POST',{requestId:randomUUID(),version:ctx.version,mode:'NONE',documents:[],physicalChecked:true,reason:'工单过程中调整半成品库存隔离验收'}))
 return{w,b,stockId:output.lines[0].inventoryId,next:await createBatch(w,f.processB)}}
async function browserBalance(page,s,grams,key){await page.goto('http://127.0.0.1:5173/metal/warehouse-order');await page.locator('.toolbar').getByRole('button',{name:'添加',exact:true}).click();const d=page.getByRole('dialog',{name:'添加出库工单',exact:true})
 const choose=async(scope,label,name)=>{const field=scope.locator('.el-form-item').filter({has:page.locator('label').filter({hasText:new RegExp('^'+label+'$')})});await field.locator('.el-select__wrapper').first().click();const id=await field.getByRole('combobox').first().getAttribute('aria-controls');await page.locator('[id="'+id+'"]').getByRole('option',{name,exact:true}).click()}
 await choose(d,'工厂',f.prefix+'厂A');await choose(d,'生产工单',s.w.orderNo);await choose(d,'产品明细',f.prefix+'产品A 1');await choose(d,'工序',f.prefix+'焊接');await choose(d,'仓库/库房',f.prefix+'库房');await choose(d,'领用人',new RegExp(f.phoneA));await choose(d,'仓管员',new RegExp(f.phoneA));await d.locator('.line-toolbar').getByRole('button',{name:'添加',exact:true}).click()
 const line=page.getByRole('dialog',{name:/^(取)?物料明细$/,exact:true});await line.getByRole('combobox').first().click();const options=page.getByRole('option').filter({hasText:s.w.orderNo});await expect(options).toHaveCount(1);const label=await options.innerText();check(label.includes(grams+'.000000')&&label.includes('g'),key+' 浏览器下一工序下拉显示最新可领 '+grams+'g');await page.screenshot({path:root+key+'.png',fullPage:true});await options.click();await line.getByLabel('实测取净重(g)',{exact:true}).fill(String(grams));await expect(line.getByLabel('参考件数（选填）',{exact:true})).toHaveValue('');check(true,key+' 浏览器参考件数可留空');await line.getByRole('button',{name:'确认',exact:true}).click();await expect(line).not.toBeVisible();await expect(d.locator('.el-table__body .el-table__row')).toHaveCount(1);check(true,key+' 浏览器按最新净重确认领料明细成功');await page.screenshot({path:root+key+'-draft.png',fullPage:true});const saved=page.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname.endsWith('/metal/warehouse-order'));await d.getByRole('button',{name:'确认',exact:true}).click();const response=ok(await(await saved).json());await expect(d).not.toBeVisible();return String(response.id)}
try{
 const product=await detail('/metal/product',f.productA,f.admin);product.processes[0].measurementMode='WEIGHT';ok(await api('/metal/product',f.admin,'PUT',product))
 browser=await chromium.launch({channel:'chrome'});const session=await browser.newContext({viewport:{width:1440,height:1000}});await session.addCookies([{name:'Admin-Token',value:f.admin,url:'http://127.0.0.1:5173'}]);const page=await session.newPage()
 for(const scenario of [{key:'increase',delta:'30',latest:130},{key:'decrease',delta:'-30',latest:70},{key:'reference',delta:'0',latest:100,referenceQuantity:33}]){
  const s=await create(),initial=await detail('/metal/inventory',s.stockId,f.admin),beforeFlow=await flow(s.w),beforeCandidates=await candidates(s.w)
  const currentWork=await detail('/metal/work-order',s.w.id,f.admin);console.log('STATE '+JSON.stringify({scenario:scenario.key,status:currentWork.status,weight:initial.netWeight,reference:initial.referenceQuantity}))
  check(currentWork.status==='IN_PROGRESS'&&Number(initial.netWeight)===100&&Number(initial.referenceQuantity)===1,scenario.key+' 工单进行中，初始半成品100g、参考1')
  const initialCandidate=beforeCandidates.data.find(x=>String(x.id)===String(s.stockId));check(initialCandidate?.measurementMode==='WEIGHT'&&Number(initialCandidate.availableWeight)===100,scenario.key+' 调整前下一工序可领100g')
  ok(await api('/metal/inventory/'+s.stockId+'/adjust',f.admin,'POST',{requestId:randomUUID(),version:initial.version,operatorId:f.ownerA,keeperId:f.ownerA,deltaWeight:scenario.delta,deltaQuantity:0,reason:'隔离验证下一工序按调整后余额领用',...(scenario.referenceQuantity?{referenceQuantity:scenario.referenceQuantity}:{})}))
  const adjusted=await detail('/metal/inventory',s.stockId,f.admin),current=await candidates(s.w),candidate=current.data.find(x=>String(x.id)===String(s.stockId)),afterFlow=await flow(s.w)
  check(Number(adjusted.netWeight)===scenario.latest&&Number(candidate?.availableWeight)===scenario.latest,scenario.key+' 下一工序接口按最新 '+scenario.latest+'g 余额返回')
  check(beforeCandidates.entryContext.externalEntryCapacity===current.entryContext.externalEntryCapacity,scenario.key+' 库存调整不伪增外部接入额度')
  check(JSON.stringify(beforeFlow.progress)===JSON.stringify(afterFlow.progress),scenario.key+' 历史加工和工序覆盖保持原记录')
  const report=ok(await api('/metal/work-report/summary?workOrderId='+s.w.id,f.admin)).data.find(x=>String(x.itemId)===String(s.w.items[0].id)&&String(x.processId)===String(f.processA));check(Number(report?.lossWeight)===10,scenario.key+' 原工序历史损耗仍为10g')
  if(scenario.referenceQuantity)check(Number(adjusted.referenceQuantity)===33&&Number(candidate.referenceQuantity)===33&&candidate.quantity==null,scenario.key+' 参考数33独立显示，不转换为有效件数')
  const beforeCount=sql('SELECT COUNT(*) FROM metal_stock_ledger WHERE inventory_id='+s.stockId),bad=await postIssue(s.next,s.stockId,scenario.latest+1)
  check(bad.code!==200&&/库存|可领|余额/.test(bad.msg),scenario.key+' 超过最新余额1g被后端拒绝')
  const unchanged=await detail('/metal/inventory',s.stockId,f.admin);check(unchanged.version===adjusted.version&&unchanged.netWeight===adjusted.netWeight&&sql('SELECT COUNT(*) FROM metal_stock_ledger WHERE inventory_id='+s.stockId)===beforeCount,scenario.key+' 超领失败不扣库存、不增加流水')
  const issue=await detail('/metal/warehouse-order',await browserBalance(page,s,scenario.latest,scenario.key),f.admin)
  check(Number(issue.lines[0].netWeight)===scenario.latest&&String(issue.batchId)===String(s.next)&&Number(issue.lines[0].quantity??0)===0,scenario.key+' 浏览器整单保存后下一工序实际成功领用 '+scenario.latest+'g，不要求件数')
  check(Number((await detail('/metal/inventory',s.stockId,f.admin)).netWeight)===0,scenario.key+' 成功领用后库存准确归零')
  results.push({scenario:scenario.key,workOrderNo:s.w.orderNo,sourceBatch:s.b,targetBatch:s.next,inventoryId:s.stockId,adjustedWeight:scenario.latest,referenceQuantity:adjusted.referenceQuantity,issuedOrderNo:issue.orderNo,issuedWeight:issue.lines[0].netWeight})
 }
 writeFileSync(root+'result.json',JSON.stringify({passed:checks.length,checks,results},null,2));console.log('PASSED '+checks.length)
}finally{if(browser)await browser.close();await f.cleanup();console.log('CLEANED isolated business fixture; audit retained')}
