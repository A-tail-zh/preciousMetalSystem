import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {warehouseFixture} from './warehouse-test-fixture.mjs';
import {api,ok,detail,sql} from './local-test-client.mjs';
const f=await warehouseFixture(),checks=[];
try{
 for(const category of ['RAW','SEMI','FINISHED']){
  const type={RAW:'RAW_IN',SEMI:'SEMI_IN',FINISHED:'FINISHED_IN'}[category];const incoming=await f.post(type,[{category,referenceId:category==='RAW'?f.mat1:f.productA,netWeight:'10',quantity:category==='RAW'?0:2,remark:'非调整备注'}],{reason:'非调整单据原因'});const stock=(await detail('/metal/warehouse-order',incoming,f.admin)).lines[0].inventoryId;
  const get=()=>detail('/metal/inventory',stock,f.admin);assert.equal((await get()).ledger[0].adjustmentReason,null);
  const adjust=async(reason,delta='1')=>{const s=await get();return String(ok(await api('/metal/inventory/'+stock+'/adjust',f.admin,'POST',{requestId:randomUUID(),version:s.version,operatorId:f.ownerA,keeperId:f.ownerA,deltaWeight:delta,deltaQuantity:0,reason})).id);};
  const reason=('盘点复核差异'.repeat(60))+'\n第二行说明';const first=await adjust(reason),second=await adjust('第二次独立调整','-1');let rows=(await get()).ledger;assert.equal(rows[1].adjustmentReason,reason);assert.equal(rows[2].adjustmentReason,'第二次独立调整');
  let order=await detail('/metal/warehouse-order',second,f.admin);ok(await api('/metal/warehouse-order/'+second+'/reverse',f.admin,'POST',{version:order.version,reason:'撤销第二次调整'}));rows=(await get()).ledger;assert.equal(rows[2].adjustmentReason,'第二次独立调整');assert.equal(rows.at(-1).adjustmentReason,'冲销：撤销第二次调整');assert.equal(rows[1].adjustmentReason,reason);
  const fallback=await adjust('旧单据原因');sql(`UPDATE metal_warehouse_order_line SET remark=NULL WHERE order_id=${fallback} AND inventory_id=${stock}`);assert.equal((await get()).ledger.at(-1).adjustmentReason,'旧单据原因');order=await detail('/metal/warehouse-order',fallback,f.admin);ok(await api('/metal/warehouse-order/'+fallback+'/reverse',f.admin,'POST',{version:order.version,reason:'旧单冲销原因'}));rows=(await get()).ledger;assert.equal(rows.at(-2).adjustmentReason,null);assert.equal(rows.at(-1).adjustmentReason,'冲销：旧单冲销原因');assert.equal((await get()).netWeight,'11.000000');
  checks.push(category+': row matching, long/multiline text, non-adjustment null, original/reversal distinction, legacy fallback, missing original and unchanged balances');
 }
 console.log(JSON.stringify({passed:checks.length,checks},null,2));
}finally{await f.cleanup();}
