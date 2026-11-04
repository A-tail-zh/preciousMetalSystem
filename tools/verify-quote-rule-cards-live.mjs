import assert from 'node:assert/strict';
import {writeFileSync,readFileSync} from 'node:fs';
import {createHash} from 'node:crypto';
if(process.env.TEST_MYSQL_PORT!=='3311'||process.env.TEST_API_BASE_URL!=='http://127.0.0.1:8081'||process.env.TEST_REDIS_PORT!=='6381')throw Error('Use owned isolated services only');
const {api,ok,sql,login}=await import('./local-test-client.mjs');
const {workOrderFixture}=await import('./work-order-test-fixture.mjs');
const checks=[],pass=name=>{checks.push(name);console.log('PASS '+name)},digest=q=>createHash('sha256').update(sql(q)).digest('hex');
const before=JSON.parse(readFileSync('.runtime/quote-rule-cards/isolated-rule-before.json','utf8'));assert.equal(digest(before.query),before.sha256);pass('migration preserves all original rule columns');
let f,reader;
try{
 f=await workOrderFixture();
 const metal=String(ok(await api('/metal/metal-types',f.admin,'POST',{name:f.prefix+'报价金属'})).id);f.made.metalTypes.push(metal);
 const rule={metalTypeId:metal,density:'21.455',powderPrice:'0',nonPowderPrice:'2',shortagePrice:'3',lossPrice:'4',version:0},get=()=>api('/metal/quote-rule?metalTypeId='+metal,f.admin).then(ok).then(r=>r.data),del=version=>api('/metal/quote-rule/'+metal,f.admin,'DELETE',{version});
 ok(await api('/metal/quote-rule',f.admin,'PUT',rule));let current=await get();assert.equal(current.density,'21.46');assert.equal(Number(current.configured),1);pass('new rule stores two decimals and appears configured');
 const product=String(ok(await api('/metal/product',f.admin,'POST',{code:'RULE_'+f.suffix,name:f.prefix+'报价产品',metalTypeId:metal,productType:'杆',calculationVersion:2,dimensions:{innerDiameter:'0',outerDiameter:'2',length:'1'},processes:[]})).id);f.made.products.push(product);
 const productQuery='SELECT product_id,product_name,volume,theoretical_weight,CAST(dimensions AS CHAR),calculation_version FROM metal_product WHERE product_id='+product,productBefore=digest(productQuery),historyQuery='SELECT quote_id,total_price,request_hash,CAST(item_snapshot AS CHAR) FROM metal_saved_quotation ORDER BY quote_id',historyBefore=digest(historyQuery),sharedBefore=digest('SELECT * FROM metal_volume_rule');
 reader=await login(f.phoneA,f.password);assert.equal((await api('/metal/quote-rule/'+metal,reader,'DELETE',{version:current.version})).code,403);pass('caller without quote edit permission cannot delete');
 assert.equal((await del(0)).code,409);for(const body of [{},{version:-1},{version:'bad'}])assert.notEqual((await api('/metal/quote-rule/'+metal,f.admin,'DELETE',body)).code,200);assert.equal((await get()).version,current.version);pass('stale and malformed deletion requests do not write');
 const raced=await Promise.all([api('/metal/quote-rule',f.admin,'PUT',{...rule,version:current.version,density:'22'}),del(current.version)]);assert.equal(raced.filter(r=>r.code===200).length,1);assert.equal(raced.filter(r=>r.code===409).length,1);pass('concurrent edit and delete have exactly one winner');
 current=await get();if(!Number(current.configured)){ok(await api('/metal/quote-rule',f.admin,'PUT',{...rule,version:current.version}));current=await get();}
 const liveVersion=current.version;ok(await del(liveVersion));let removed=await get();assert.equal(removed.version,liveVersion+1);assert.equal(Number(removed.configured),0);for(const key of ['density','powderPrice','nonPowderPrice','shortagePrice','lossPrice'])assert.equal(removed[key],null);assert.equal(sql('SELECT del_flag FROM metal_type_quote_rule WHERE metal_type_id='+metal),'2');assert.notEqual(sql('SELECT density FROM metal_type_quote_rule WHERE metal_type_id='+metal),'');pass('logical deletion masks all active rule parameters and preserves tombstone');
 assert.notEqual((await api('/metal/quotation/calculate',f.admin,'POST',{metalTypeId:metal,productType:'杆',dimensions:{innerDiameter:'0',outerDiameter:'2',length:'1'},quantity:1})).code,200);pass('deleted rule cannot supply density to new calculations');
 assert.equal((await api('/metal/quote-rule',f.admin,'PUT',{...rule,version:liveVersion})).code,409);assert.equal((await del(liveVersion)).code,409);pass('old windows cannot overwrite or delete the tombstone');
 ok(await api('/metal/quote-rule',f.admin,'PUT',{...rule,version:removed.version}));current=await get();assert.equal(current.version,removed.version+1);assert.equal(Number(current.configured),1);assert.equal((await del(liveVersion)).code,409);pass('recreation uses increasing version and blocks pre-delete requests');
 assert.equal(digest(productQuery),productBefore);assert.equal(digest(historyQuery),historyBefore);assert.equal(digest('SELECT * FROM metal_volume_rule'),sharedBefore);assert.equal(sql('SELECT COUNT(*) FROM metal_type WHERE metal_type_id='+metal),'1');pass('metal directory, products, saved quotations and shared parameters are untouched');
 let audit;for(let i=0;i<20;i++){const raw=sql("SELECT event_json FROM audit_outbox WHERE JSON_UNQUOTE(JSON_EXTRACT(event_json,'$.path'))='/metal/quote-rule/"+metal+"' AND JSON_UNQUOTE(JSON_EXTRACT(event_json,'$.code'))='SUCCESS'");audit=raw.split(/\r?\n/).filter(Boolean).map(JSON.parse).find(e=>e.changes?.before?.[metal]?.del_flag==='0'&&e.changes?.after?.[metal]?.del_flag==='2');if(audit)break;await new Promise(r=>setTimeout(r,250));}assert(audit);assert.equal(JSON.parse(audit.request).body.version,liveVersion);pass('delete audit retains original request and before/after rule states');
}finally{
 if(reader)await api('/logout',reader,'POST');if(f)await f.cleanup();
 writeFileSync('.runtime/quote-rule-cards/api-result.json',JSON.stringify({passed:checks.length,checks,cleaned:!!f,auditRetained:true},null,2));console.log('Owned fixtures cleaned; audit retained');
}
