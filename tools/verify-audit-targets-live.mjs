import fs from 'node:fs';
import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {randomUUID} from 'node:crypto';
import {workOrderFixture} from './work-order-test-fixture.mjs';
import {api,ok,sql} from './local-test-client.mjs';

const root='.runtime/audit-and-full-frontend-2026-10-23';fs.mkdirSync(root,{recursive:true});
let f;const quotes=[];
const run=type=>execFileSync('powershell.exe',['-NoProfile','-Command',`mvn.cmd -o -pl ruoyi-framework -am test '-Dmaven.test.skip=false' '-DskipTests=false' '-Dtest=${type}' '-Dsurefire.failIfNoSpecifiedTests=false' '-Daudit.live.root=D:/leo/heavyMetalSystem' *> ../${root}/${type}.log; exit $LASTEXITCODE`],{cwd:'backend',stdio:'pipe',timeout:180000});
try{
 f=await workOrderFixture();fs.writeFileSync(root+'/audit-fixture.json',JSON.stringify({prefix:f.prefix,factoryA:f.factoryA,factoryB:f.factoryB,made:f.made}));
 const startedAt=sql("SELECT DATE_FORMAT(NOW(),'%Y-%m-%d %H:%i:%s')"),body=f.body();
 const work=await f.createOrder(body);let eventId='';
 for(let i=0;i<30;i++){eventId=sql("SELECT event_id FROM audit_outbox WHERE create_time>='"+startedAt+"' AND JSON_UNQUOTE(JSON_EXTRACT(event_json,'$.path'))='/metal/work-order' AND JSON_UNQUOTE(JSON_EXTRACT(JSON_UNQUOTE(JSON_EXTRACT(event_json,'$.request')),'$.body.requestId'))='"+body.requestId+"'");if(eventId)break;await new Promise(r=>setTimeout(r,200))}
 assert.match(eventId,/^[a-f0-9-]{36}$/);fs.writeFileSync('.runtime/audit-fixture-live-target.json',JSON.stringify({eventId,factoryId:f.factoryA,prefix:f.prefix,startedAt}));
 run('AuditFixtureDeliveryLiveTest');console.log('PASS fresh isolated work-order audit delivery');
 const quoteStartedAt=sql("SELECT DATE_FORMAT(NOW(),'%Y-%m-%d %H:%i:%s')"),requestId=randomUUID(),quoteBody={requestId,factoryId:f.factoryA,items:[{category:'铱杆实心',quantity:2,dimensions:{diameter:'2',length:'3'}}]};
 const saved=ok(await api('/metal/quotation/save',f.admin,'POST',quoteBody)).data;quotes.push(saved.id);const replay=ok(await api('/metal/quotation/save',f.admin,'POST',quoteBody)).data;assert.equal(saved.id,replay.id);
 fs.writeFileSync('.runtime/audit-quote-live-target.json',JSON.stringify({quoteNo:saved.quoteNo,factoryId:f.factoryA,requestId,startedAt:quoteStartedAt}));
 run('AuditQuoteDeliveryLiveTest');console.log('PASS fresh isolated quote audit first delivery and idempotent replay');
 fs.writeFileSync(root+'/audit-target-result.json',JSON.stringify({workOrderId:work,eventId,quoteNo:saved.quoteNo,checks:2,passed:2},null,2));
}finally{
 if(f){for(const id of quotes){assert.match(String(id),/^\d+$/);sql('DELETE FROM metal_saved_quotation WHERE quote_id='+id+' AND factory_id='+f.factoryA)}await f.cleanup();console.log('isolated audit fixture business data cleaned; audit records retained')}
}
