import assert from 'node:assert/strict'
import {randomUUID} from 'node:crypto'
import {warehouseFixture} from './warehouse-test-fixture.mjs'
import {api,ok,detail,sql} from './local-test-client.mjs'
const f=await warehouseFixture({completionPolicy:'SINGLE_OUTPUT_RETURN'})
let checks=0
const check=(value,message)=>{assert.ok(value,message);checks++;console.log('PASS '+message)}
try{
 const product=await detail('/metal/product',f.productA,f.admin)
 product.processes[0].measurementMode='WEIGHT'
 ok(await api('/metal/product',f.admin,'PUT',product))
 const work=await detail('/metal/work-order',await f.createOrder(f.body()),f.admin)
 const batch=String(ok(await api('/metal/process-batch',f.admin,'POST',{requestId:randomUUID(),workOrderId:work.id,itemId:work.items[0].id,processId:f.processA,plannedQuantity:1})).id)
 const raw=await detail('/metal/warehouse-order',await f.post('RAW_IN',[{category:'RAW',referenceId:f.mat1,netWeight:'110',quantity:0}]),f.admin)
 await f.post('PRODUCTION_OUT',[{inventoryId:raw.lines[0].inventoryId,netWeight:'110',quantity:0}],{batchId:batch})
 const output=await detail('/metal/warehouse-order',await f.post('SEMI_IN',[{category:'SEMI',referenceId:f.productA,netWeight:'100',referenceQuantity:1}],{batchId:batch}),f.admin)
 const inventoryId=output.lines[0].inventoryId
 const completion=ok(await api('/metal/process-batch/'+batch+'/completion-context',f.admin)).data
 ok(await api('/metal/process-batch/'+batch+'/confirm-return',f.admin,'POST',{requestId:randomUUID(),version:completion.version,mode:'NONE',documents:[],physicalChecked:true,reason:'参考件数组合校验隔离验收'}))
 for(const components of [{deltaImpurity:'2'},{deltaGrossWeight:'2'},{deltaGrossWeight:'2',deltaImpurity:'2'}]){
  const before=await detail('/metal/inventory',inventoryId,f.admin)
  const count=sql('SELECT COUNT(*) FROM metal_stock_ledger WHERE inventory_id='+inventoryId)
  const requestId=randomUUID()
  const result=await api('/metal/inventory/'+inventoryId+'/adjust',f.admin,'POST',{requestId,version:before.version,operatorId:f.ownerA,keeperId:f.ownerA,deltaWeight:'0',deltaQuantity:0,referenceQuantity:33,reason:'拒绝半成品毛重杂质差额',...components})
  const expected=components.deltaGrossWeight&&components.deltaImpurity?'仅原材料支持毛重和杂质差额':'毛重差额和杂质差额须同时填写'
  check(result.code===500&&result.msg?.includes(expected),'参考件数不能绕过毛重杂质组合校验 '+JSON.stringify(components))
  const after=await detail('/metal/inventory',inventoryId,f.admin)
  check(after.version===before.version&&after.referenceQuantity===before.referenceQuantity&&after.netWeight===before.netWeight&&sql('SELECT COUNT(*) FROM metal_stock_ledger WHERE inventory_id='+inventoryId)===count&&sql("SELECT COUNT(*) FROM metal_warehouse_order WHERE request_id='"+requestId+"'")==='0','非法请求不生成单据、流水或改变库存')
 }
 const before=await detail('/metal/inventory',inventoryId,f.admin)
 ok(await api('/metal/inventory/'+inventoryId+'/adjust',f.admin,'POST',{requestId:randomUUID(),version:before.version,operatorId:f.ownerA,keeperId:f.ownerA,deltaWeight:'0',deltaQuantity:0,referenceQuantity:33,reason:'合法只调整参考件数'}))
 const after=await detail('/metal/inventory',inventoryId,f.admin)
 check(Number(after.referenceQuantity)===33&&after.netWeight===before.netWeight,'合法参考件数调整保留零净重差额')
 console.log('PASSED '+checks)
}finally{await f.cleanup();console.log('CLEANED isolated validation fixture; audit retained')}
