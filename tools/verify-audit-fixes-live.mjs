import assert from 'node:assert/strict';import{execFileSync}from'node:child_process';import fs from 'node:fs';import{randomUUID}from'node:crypto';import{api,ok,detail,login,sql}from'./local-test-client.mjs';import{workOrderFixture}from'./work-order-test-fixture.mjs';
const f=await workOrderFixture(),quotes=[];const results=[];
try{
 const menus=ok(await api('/metal/options/menus',f.admin)).data;const ids=menus.filter(m=>['monitor:operlog:list','monitor:operlog:query','metal:quotation:list'].includes(m.perms)).map(m=>m.id);
 for(const id of [f.roleA,f.roleB]){const role=await detail('/system/role',id,f.admin);ok(await api('/system/role',f.admin,'PUT',{...role,menuIds:[...new Set([...role.menuIds,...ids])]}));}
 const own=await login(f.phoneA,f.password),other=await login(f.phoneB,f.password);
 const startedAt=new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Shanghai',dateStyle:'short',timeStyle:'medium'}).format(new Date());const body={requestId:randomUUID(),factoryId:f.factoryA,items:[{category:'铱杆实心',quantity:2,dimensions:{diameter:'2',length:'3'}}]};
 const saved=ok(await api('/metal/quotation/save',f.admin,'POST',body)).data;quotes.push(saved.id);
 const repeated=ok(await api('/metal/quotation/save',f.admin,'POST',body)).data;assert.equal(repeated.id,saved.id);assert.equal(sql("SELECT COUNT(*) FROM metal_saved_quotation WHERE request_id='"+body.requestId+"'"),'1');
 fs.writeFileSync('.runtime/audit-quote-live-target.json',JSON.stringify({quoteNo:saved.quoteNo,factoryId:f.factoryA,requestId:body.requestId,startedAt}));
 if(process.env.AUDIT_FIXTURE_DIRECT_DELIVERY==='1'){console.log('显式启用新测试事件定向投递');
 execFileSync('powershell.exe',['-NoProfile','-Command',"mvn -o -gs ../build-settings.xml -s ../build-settings.xml -B -ntp -pl ruoyi-framework -am test '-Dtest=AuditQuoteDeliveryLiveTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Daudit.live.root=D:/leo/heavyMetalSystem' *> ../.runtime/audit-quote-delivery-test.log; exit $LASTEXITCODE"],{cwd:'backend',stdio:'pipe',timeout:120000});}
 let rows=[];for(let i=0;i<55;i++){rows=ok(await api('/monitor/operlog/list?businessCode='+saved.quoteNo,f.admin)).rows;if(rows.length>=2)break;await new Promise(r=>setTimeout(r,1000));}assert.equal(rows.length,2,'两次报价操作日志应投递');
 const logs=[];for(const row of rows){const log=ok(await api('/monitor/operlog/'+row.id,own)).data;assert.equal(log.businessFactoryId,f.factoryA);assert.equal(log.businessCode,saved.quoteNo);assert.equal(log.objectType,'metal_saved_quotation');const changes=typeof log.changesJson==='string'?JSON.parse(log.changesJson):log.changesJson;const after=changes.after[saved.id];assert(after,'存在报价快照');assert.equal(String(after.factory_id),f.factoryA);assert.equal(after.quote_no,saved.quoteNo);assert.equal(after.total_price,saved.totalPrice);assert.deepEqual(JSON.parse(after.item_snapshot),saved.items);assert.equal((await api('/monitor/operlog/'+row.id,other)).code,403);logs.push(log);}
 assert(logs.some(l=>l.replayed===true||l.replayed===1));assert(logs.some(l=>l.replayed===false||l.replayed===0));
 assert.equal(ok(await api('/monitor/operlog/list?businessCode='+saved.quoteNo,own)).total,2);assert.equal(ok(await api('/monitor/operlog/list?businessCode='+saved.quoteNo,other)).total,0);
 results.push('超级管理员保存报价：本厂可查、跨厂列表不可见且详情403、工厂/单号/保存快照准确','幂等重复提交不新增报价，两条事件正确标记首次及重放');
 const config=ok(await api('/monitor/log-config',f.admin)).data;assert.equal(config.cleanupEnabled,false,'保持自动清理关闭');
 fs.writeFileSync('outputs/日志修复真实接口验收.json',JSON.stringify({verifiedAt:new Date().toISOString(),checks:results,eventIds:rows.map(r=>r.id),temporaryQuoteNo:saved.quoteNo,historicalLogsChanged:false},null,2));console.log('PASS '+results.join('\nPASS '));
 await api('/logout',own,'POST');await api('/logout',other,'POST');
}finally{for(const id of quotes){assert(/^\d+$/.test(id));sql('DELETE FROM metal_saved_quotation WHERE quote_id='+id+' AND factory_id='+f.factoryA);}await f.cleanup();}
