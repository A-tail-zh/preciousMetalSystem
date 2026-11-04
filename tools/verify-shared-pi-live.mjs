import assert from 'node:assert/strict';
import {writeFileSync} from 'node:fs';
if(process.env.TEST_MYSQL_PORT!=='3311'||!process.env.TEST_API_BASE_URL?.endsWith(':8081')||process.env.TEST_REDIS_PORT!=='6381')throw Error('Use the registered isolated services only');
const {api,ok,sql,login}=await import('./local-test-client.mjs');
const {workOrderFixture}=await import('./work-order-test-fixture.mjs');
let f,reader,original;const checks=[];const pass=name=>{checks.push(name);console.log('PASS '+name)};
try{
 f=await workOrderFixture();original=ok(await api('/metal/volume-rule',f.admin)).data;
 const historical=sql('SELECT CAST(item_snapshot AS CHAR),total_price,request_hash FROM metal_saved_quotation ORDER BY quote_id');
 const productBefore=sql(`SELECT volume,theoretical_weight,CAST(dimensions AS CHAR) FROM metal_product WHERE product_id IN (${f.productA},${f.productB}) ORDER BY product_id`);
 ok(await api('/metal/volume-rule',f.admin,'PUT',{piValue:'3.204',version:original.version}));const shared=ok(await api('/metal/volume-rule',f.admin)).data;assert.equal(shared.piValue,'3.20');pass('independent pi saves with HALF_UP to two places');
 const metal=String(ok(await api('/metal/metal-types',f.admin,'POST',{name:f.prefix+'金属'})).id);f.made.metalTypes.push(metal);
 const product=String(ok(await api('/metal/product',f.admin,'POST',{code:'PI_'+f.suffix,name:f.prefix+'产品',metalTypeId:metal,productType:'杆',dimensions:{innerDiameter:'0',outerDiameter:'2',length:'1'},processes:[]})).id);f.made.products.push(product);
 const newProduct=ok(await api('/metal/product/'+product,f.admin)).data;assert.equal(Number(newProduct.volume),6.40);assert.equal(newProduct.weight,null);pass('unconfigured metal shares pi and can calculate volume without density');
 const rates={metalTypeId:metal,density:'10',powderPrice:'0',nonPowderPrice:'0',shortagePrice:'0',lossPrice:'0',version:0};ok(await api('/metal/quote-rule',f.admin,'PUT',rates));
 sql(`UPDATE metal_type_quote_rule SET pi_value=9.99 WHERE metal_type_id=${metal}`);
 for(const metalTypeId of ['1',metal]){
  const detail=ok(await api('/metal/quote-rule?metalTypeId='+metalTypeId,f.admin)).data;assert.equal(detail.piValue,'3.20');assert.equal(detail.piVersion,shared.version);
  const trial=ok(await api('/metal/quotation/calculate',f.admin,'POST',{metalTypeId,productType:'杆',dimensions:{innerDiameter:'0',outerDiameter:'2',length:'1'},quantity:2})).data;assert.equal(trial.volume,'6.40');assert.equal(trial.volumeRule.piValue,'3.20');assert.equal(trial.volumeRule.version,shared.version);assert.equal(trial.unitWeight,metalTypeId==='1'?'144.96':'64.00');
 }pass('different metals and stale legacy pi columns all use the shared pi and snapshot version');
 const shapes=[['杆',{outerDiameter:'3',innerDiameter:'2',length:'1'},'8.00'],['环',{outerDiameter:'3',innerDiameter:'2',thickness:'1'},'4.00'],['平底坩埚',{innerDiameter:'2',outerDiameter:'2.4',innerHeight:'1',bottomThickness:'0.2',wallThickness:'0.2'},'2.33']];
 for(const[productType,dimensions,volume]of shapes)assert.equal(ok(await api('/metal/quotation/calculate',f.admin,'POST',{metalTypeId:metal,productType,dimensions,quantity:1})).data.volume,volume);pass('all supported shape formulas use shared pi without a coefficient');
 ok(await api('/metal/quote-rule',f.admin,'PUT',{...rates,density:'11',version:1}));assert.equal(sql('SELECT CAST(pi_value AS CHAR) FROM metal_type_quote_rule WHERE metal_type_id='+metal),'9.99');assert.equal(ok(await api('/metal/volume-rule',f.admin)).data.version,shared.version);pass('metal saves do not change shared pi, its version, or old per-metal pi');
 assert.equal(sql(`SELECT volume,theoretical_weight,CAST(dimensions AS CHAR) FROM metal_product WHERE product_id IN (${f.productA},${f.productB}) ORDER BY product_id`),productBefore);assert.equal(sql('SELECT CAST(item_snapshot AS CHAR),total_price,request_hash FROM metal_saved_quotation ORDER BY quote_id'),historical);pass('pi changes leave existing product values and historical quotation evidence untouched');
 const raced=await Promise.all(['3.30','3.40'].map(piValue=>api('/metal/volume-rule',f.admin,'PUT',{piValue,version:shared.version})));assert.equal(raced.filter(x=>x.code===200).length,1);assert.equal(raced.filter(x=>x.code===409).length,1);pass('concurrent global pi saves cannot overwrite the winning version');
 const current=ok(await api('/metal/volume-rule',f.admin)).data;
 for(const piValue of ['0.004','0','-1','99999.995',null])assert.notEqual((await api('/metal/volume-rule',f.admin,'PUT',{piValue,version:current.version})).code,200);assert.equal(ok(await api('/metal/volume-rule',f.admin)).data.version,current.version);pass('zero tiny negative null and rounded-overflow pi values are rejected without a write');
 reader=await login(f.phoneA,f.password);assert.equal((await api('/metal/volume-rule',reader)).code,200);assert.equal((await api('/metal/volume-rule',reader,'PUT',{piValue:'3.14',version:current.version})).code,403);pass('product-query account reads common pi but cannot modify it');
 const events=sql("SELECT event_json FROM audit_outbox WHERE JSON_UNQUOTE(JSON_EXTRACT(event_json,'$.path'))='/metal/volume-rule' AND JSON_UNQUOTE(JSON_EXTRACT(event_json,'$.code'))='SUCCESS'").split(/\r?\n/).filter(Boolean).map(JSON.parse);const event=events.find(e=>Number(e.changes?.after?.['1']?.pi_value)===3.2);assert(event);assert.equal(Number(event.changes.before['1'].pi_value),Number(original.piValue));assert.equal(JSON.parse(event.request).body.piValue,'3.204');pass('global pi audit retains original request and before/after snapshots');
}finally{
 if(reader)await api('/logout',reader,'POST');
 if(f){try{if(original){const current=ok(await api('/metal/volume-rule',f.admin)).data;ok(await api('/metal/volume-rule',f.admin,'PUT',{piValue:original.piValue,version:current.version}));}}finally{await f.cleanup();}}
 writeFileSync('.runtime/global-pi-20261102/live-result.json',JSON.stringify({passed:checks.length,checks,cleaned:!!f,auditRetained:true},null,2));console.log('Fixture cleaned, shared pi restored, audit retained');
}
