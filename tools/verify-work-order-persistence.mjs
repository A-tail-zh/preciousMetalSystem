import {readFileSync,writeFileSync} from 'node:fs'
import assert from 'node:assert/strict'
import {api,login,ok,detail} from './local-test-client.mjs'
import {workOrderFixture,cleanupWorkOrderFixture} from './work-order-test-fixture.mjs'
const file='.runtime/work-order-persistence.json'
if(process.argv[2]==='prepare'){
 const f=await workOrderFixture(),id=await f.createOrder();ok(await api('/metal/work-order/'+id+'/reject',f.admin,'POST',{version:0,reason:'重启验收理由'}));ok(await api('/metal/work-order/'+id+'/resubmit',f.admin,'POST',{version:1}));const saved=await detail('/metal/work-order',id,f.admin)
 writeFileSync(file,JSON.stringify({prefix:f.prefix,made:f.made,id,saved,phoneA:f.phoneA,password:f.password,factoryA:f.factoryA}));await api('/logout',f.admin,'POST');console.log('PASS 已保存重启验证工单和审核历史')
}else{
 const f=JSON.parse(readFileSync(file,'utf8')),admin=await login();let ordinary
 try{const current=await detail('/metal/work-order',f.id,admin);assert.deepEqual(current,f.saved);ordinary=await login(f.phoneA,f.password);assert.ok(ok(await api('/metal/work-order/list',ordinary)).rows.every(r=>r.factoryId===f.factoryA));assert.equal((await detail('/metal/work-order',f.id,ordinary)).orderNo,f.saved.orderNo);ok(await api('/metal/work-order/'+f.id+'/approve',admin,'POST',{version:current.version}));assert.equal((await detail('/metal/work-order',f.id,admin)).status,'APPROVED');writeFileSync('docs/work-order-restart-verification.json',JSON.stringify({timestamp:new Date().toISOString(),passed:['重启后工单、产品快照、工序实际时间和审核历史完整保留','普通账号范围及详情权限保持，重启后审核成功']},null,2));console.log('PASS 重启后工单、实际时间、历史及权限保持')}
 finally{if(ordinary)await api('/logout',ordinary,'POST');await cleanupWorkOrderFixture(f,admin);writeFileSync(file,'{}')}
}
