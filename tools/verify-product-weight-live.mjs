import assert from 'node:assert/strict';
import {writeFileSync} from 'node:fs';
if(!process.env.TEST_MYSQL_PORT||process.env.TEST_MYSQL_PORT==='3308'||!process.env.TEST_REDIS_PORT||process.env.TEST_REDIS_PORT==='6380'||!process.env.TEST_API_BASE_URL)throw Error('Explicit isolated MySQL, Redis and API required');
const {api,ok,detail,sql}=await import('./local-test-client.mjs');
const {workOrderFixture}=await import('./work-order-test-fixture.mjs');
const checks=[];const pass=name=>{checks.push(name);console.log('PASS '+name)};
let f,cleaned=false;
try{
 f=await workOrderFixture();
 const metal=String(ok(await api('/metal/metal-types',f.admin,'POST',{name:f.prefix+'净重金属'})).id);f.made.metalTypes.push(metal);
 const rule={metalTypeId:metal,version:0,density:'22.65',powderPrice:'0',nonPowderPrice:'0',shortagePrice:'0',lossPrice:'0'};
 ok(await api('/metal/quote-rule',f.admin,'PUT',rule));
 const id=String(ok(await api('/metal/product',f.admin,'POST',{name:f.prefix+'净重产品',code:'PW_'+f.suffix,metalTypeId:metal,productType:'环',dimensions:{outerDiameter:'0.3',innerDiameter:'0.1',thickness:'0.5'},weight:'999',processes:[]})).id);f.made.products.push(id);
 let p=await detail('/metal/product',id,f.admin);assert.equal(Number(p.volume),0.03);assert.equal(Number(p.weight),0.68);pass('create uses rounded volume times selected density and overrides caller weight');
 const raw=sql(`SELECT theoretical_weight FROM metal_product WHERE product_id=${id}`);assert.equal(Number(raw),0.68);pass('derived weight is persisted');
 ok(await api('/metal/product',f.admin,'PUT',{...p,dimensions:{outerDiameter:'0.4',innerDiameter:'0.1',thickness:'0.5'},weight:'777'}));p=await detail('/metal/product',id,f.admin);assert.equal(Number(p.volume),0.06);assert.equal(Number(p.weight),1.36);pass('editing dimensions recalculates persisted weight');
 ok(await api('/metal/quote-rule',f.admin,'PUT',{...rule,version:1,density:'21.45'}));assert.equal(Number((await detail('/metal/product',id,f.admin)).weight),1.36);pass('rule change alone does not rewrite stored products');
 ok(await api('/metal/product',f.admin,'PUT',{...p,name:p.name+'更新'}));p=await detail('/metal/product',id,f.admin);assert.equal(Number(p.weight),1.29);pass('explicit product save uses latest metal density');
 const unknown=String(ok(await api('/metal/metal-types',f.admin,'POST',{name:f.prefix+'无密度金属'})).id);f.made.metalTypes.push(unknown);
 const empty=String(ok(await api('/metal/product',f.admin,'POST',{name:f.prefix+'未配置产品',code:'PW_EMPTY_'+f.suffix,metalTypeId:unknown,productType:'杆',dimensions:{innerDiameter:'0',outerDiameter:'2',length:'1'},weight:'999',processes:[]})).id);f.made.products.push(empty);
 assert.equal((await detail('/metal/product',empty,f.admin)).weight,null);pass('unconfigured metal can save with empty weight');
 const unsupported=String(ok(await api('/metal/product',f.admin,'POST',{name:f.prefix+'无公式产品',code:'PW_SHAPE_'+f.suffix,metalTypeId:metal,productType:'梯形',dimensions:{upperInnerDiameter:'4',upperOuterDiameter:'6',wallThickness:'0.5',longInnerHeight:'3',lowerInnerDiameter:'2',angle:'0',bottomThickness:'1'},weight:'999',processes:[]})).id);f.made.products.push(unsupported);
 assert.equal(Number((await detail('/metal/product',unsupported,f.admin)).volume),52.60);pass('new tapered geometry has a supported volume formula');
 const order=await f.createOrder(),before=await detail('/metal/work-order',order,f.admin),product=await detail('/metal/product',f.productA,f.admin);
 ok(await api('/metal/product',f.admin,'PUT',{...product,dimensions:{innerDiameter:'0',outerDiameter:'10',length:'21'}}));
 const after=await detail('/metal/work-order',order,f.admin);assert.deepEqual(after.items[0].snapshot,before.items[0].snapshot);assert.deepEqual(after.items[0].plans,before.items[0].plans);pass('existing work order product and route snapshots remain unchanged');
}finally{
 if(f){await f.cleanup();cleaned=true;console.log('CLEANED registered fixture; audit retained')}
 writeFileSync(new URL('../.runtime/product-weight-live-result.json',import.meta.url),JSON.stringify({passed:checks.length,checks,cleaned,auditRetained:true},null,2));
}
