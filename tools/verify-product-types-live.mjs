import assert from 'node:assert/strict';
import {writeFileSync} from 'node:fs';
if(!process.env.TEST_MYSQL_PORT||process.env.TEST_MYSQL_PORT==='3308'||!process.env.TEST_REDIS_PORT||process.env.TEST_REDIS_PORT==='6380'||!process.env.TEST_API_BASE_URL)throw Error('Explicit isolated MySQL, Redis and API required');
const {api,ok,detail,sql,login}=await import('./local-test-client.mjs');
const {workOrderFixture}=await import('./work-order-test-fixture.mjs');
const flat={innerDiameter:'2',outerDiameter:'4',wallThickness:'0.5',innerHeight:'3',bottomThickness:'1'};
const tapered={upperInnerDiameter:'4',upperOuterDiameter:'6',wallThickness:'0.5',longInnerHeight:'3',lowerInnerDiameter:'2',angle:'0',bottomThickness:'1'};
const samples=[
 ['平底坩埚',flat,'21.20','480.18'],['环',{outerDiameter:'4',innerDiameter:'2',thickness:'1'},'9.42','213.36'],
 ['杆',{innerDiameter:'2',outerDiameter:'4',length:'3'},'56.52','1280.18'],['梯形',tapered,'52.60','1191.39'],
 ['弧底',flat,'21.83','494.45'],['靶',{innerDiameter:'2',wallThickness:'0.5',bottomThickness:'1'},'7.07','160.14'],
 ['方形坩埚',{length:'5',width:'4',height:'3',innerLength:'2',innerWidth:'2',innerHeight:'2'},'52.00','1177.80'],
 ['方形盖',{length:'5',width:'4',height:'3'},'60.00','1359.00']
];
let f,original,reader,cleaned=false;const checks=[];const pass=name=>{checks.push(name);console.log('PASS '+name)};
const quoteBefore=sql('SELECT quote_id,CAST(item_snapshot AS CHAR),request_hash FROM metal_saved_quotation ORDER BY quote_id');
const originalProducts=sql('SELECT product_id,CAST(dimensions AS CHAR),volume,theoretical_weight,product_category,product_type FROM metal_product ORDER BY product_id');
const originalIds=originalProducts.split(/\r?\n/).filter(Boolean).map(x=>x.split('\t')[0]);assert(originalIds.every(x=>/^\d+$/.test(x)));
try{
 assert.equal(sql("SELECT COUNT(*) FROM flyway_schema_history WHERE version IN ('43','44') AND success=1"),'2');pass('V43 and V44 applied on restored isolated database');
 f=await workOrderFixture();original=ok(await api('/metal/volume-rule',f.admin)).data;
 ok(await api('/metal/volume-rule',f.admin,'PUT',{piValue:'3.14',curvedBottomFactor:'1.03',version:original.version}));
 const metal=String(ok(await api('/metal/metal-types',f.admin,'POST',{name:f.prefix+'八类金属'})).id);f.made.metalTypes.push(metal);
 ok(await api('/metal/quote-rule',f.admin,'PUT',{metalTypeId:metal,version:0,density:'22.65',powderPrice:'0',nonPowderPrice:'0',shortagePrice:'0',lossPrice:'0'}));
 const ids=[];
 for(const [productType,dimensions,volume,weight] of samples){
  const before=sql('SELECT COUNT(*) FROM metal_product');const preview=ok(await api('/metal/product/calculation-preview',f.admin,'POST',{metalTypeId:metal,productType,dimensions})).data;
  assert.equal(preview.volume,volume);assert.equal(preview.weight,weight);assert.equal(sql('SELECT COUNT(*) FROM metal_product'),before);
  const id=String(ok(await api('/metal/product',f.admin,'POST',{metalTypeId:metal,productType,dimensions,name:f.prefix+productType,code:'PV2_'+f.suffix+'_'+ids.length,weight:'999',processes:[]})).id);f.made.products.push(id);ids.push(id);
  const p=await detail('/metal/product',id,f.admin);assert.equal(Number(p.volume),Number(volume));assert.equal(Number(p.weight),Number(weight));assert.equal(p.calculationVersion,2);
  const trial=ok(await api('/metal/quotation/calculate',f.admin,'POST',{metalTypeId:metal,productType,dimensions,quantity:2})).data;assert.equal(trial.volume,volume);assert.equal(trial.unitWeight,weight);assert.equal(Number(trial.totalWeight),Number(weight)*2);assert.equal(trial.calculationVersion,2);assert.equal(trial.pricingEnabled,false);
  pass(productType+': independent expected values match preview, persisted product and weight trial');
 }
 const tiny=ok(await api('/metal/product/calculation-preview',f.admin,'POST',{metalTypeId:metal,productType:'环',dimensions:{outerDiameter:'0.3',innerDiameter:'0.1',thickness:'0.5'}})).data;assert.equal(tiny.volume,'0.03');assert.equal(tiny.weight,'0.68');pass('weight uses rounded two-place volume rather than raw volume');
 const solid=ok(await api('/metal/product/calculation-preview',f.admin,'POST',{metalTypeId:metal,productType:'杆',dimensions:{innerDiameter:'0',outerDiameter:'2',length:'1'}})).data;assert.equal(solid.volume,'6.28');pass('zero inner diameter is allowed and uses confirmed rod formula');
 for(const [productType,dimensions] of [['梯形',{...tapered,angle:'89.999'}],['梯形',{...tapered,angle:'-1'}],['梯形',{...tapered,lowerInnerDiameter:'5'}],['环',{outerDiameter:'2',innerDiameter:'2',thickness:'1'}],['杆',{innerDiameter:'0',outerDiameter:'0.004',length:'1'}],['方形盖',{length:'1',width:'1'}],['方形盖',{length:'1',width:'1',height:'1',extra:'1'}],['方形坩埚',{length:'2',width:'2',height:'2',innerLength:'2',innerWidth:'1',innerHeight:'1'}]])assert.notEqual((await api('/metal/product/calculation-preview',f.admin,'POST',{metalTypeId:metal,productType,dimensions})).code,200);
 pass('direct API rejects invalid geometry, missing and extra fields and rounded boundaries');
 for(const angle of ['60','89.99']){const preview=ok(await api('/metal/product/calculation-preview',f.admin,'POST',{metalTypeId:metal,productType:'梯形',dimensions:{...tapered,angle}})).data;assert(Number(preview.volume)>0);if(angle==='60')assert.equal(preview.volume,'62.02')};pass('degree cosine handles exact sixty degrees and near-ninety safely');
 const curves=await detail('/metal/product',ids[4],f.admin);let config=ok(await api('/metal/volume-rule',f.admin)).data;
 ok(await api('/metal/volume-rule',f.admin,'PUT',{piValue:'3.14',curvedBottomFactor:'1.10',version:config.version}));assert.equal(Number((await detail('/metal/product',ids[4],f.admin)).volume),21.83);
 assert.equal(ok(await api('/metal/product/calculation-preview',f.admin,'POST',{metalTypeId:metal,productType:'弧底',dimensions:flat})).data.volume,'23.31');
 config=ok(await api('/metal/volume-rule',f.admin)).data;ok(await api('/metal/volume-rule',f.admin,'PUT',{piValue:'3.14',version:config.version}));assert.equal(Number(ok(await api('/metal/volume-rule',f.admin)).data.curvedBottomFactor),1.10);
 ok(await api('/metal/product',f.admin,'PUT',{...curves,name:curves.name+'名称更新'}));assert.equal(Number((await detail('/metal/product',ids[4],f.admin)).volume),21.83);pass('factor update affects new calculations; legacy pi PUT and unchanged product geometry preserve current values');
 const unknown=String(ok(await api('/metal/metal-types',f.admin,'POST',{name:f.prefix+'无密度'})).id);f.made.metalTypes.push(unknown);
 const empty=String(ok(await api('/metal/product',f.admin,'POST',{metalTypeId:unknown,productType:'方形盖',dimensions:{length:'1',width:'1',height:'1'},name:f.prefix+'无密度产品',code:'PV2_EMPTY_'+f.suffix,processes:[]})).id);f.made.products.push(empty);assert.equal((await detail('/metal/product',empty,f.admin)).weight,null);pass('missing density leaves product weight empty while allowing a save');
 const legacy=String(ok(await api('/metal/product',f.admin,'POST',{metalTypeId:'1',productType:'环',dimensions:{outerDiameter:'4',innerDiameter:'2',thickness:'1'},name:f.prefix+'历史环',code:'PV2_OLD_'+f.suffix,processes:[]})).id);f.made.products.push(legacy);
 sql(`UPDATE metal_product SET calculation_version=1,product_category='铱环',product_type='环',dimensions=JSON_OBJECT('outerDiameter','4','innerDiameter','2','height','1.234567'),volume=11.234567,theoretical_weight=77.123456 WHERE product_id=${legacy}`);
 let old=await detail('/metal/product',legacy,f.admin);ok(await api('/metal/product',f.admin,'PUT',{...old,name:old.name+'改名'}));let saved=await detail('/metal/product',legacy,f.admin);assert.equal(Number(saved.volume),11.234567);assert.equal(Number(saved.weight),77.123456);assert.deepEqual(saved.dimensions,old.dimensions);
 assert.notEqual((await api('/metal/product',f.admin,'PUT',{...saved,dimensions:{...saved.dimensions,height:'2'}})).code,200);
 const png=await fetch(process.env.TEST_API_BASE_URL+'/metal/product/'+legacy+'/specification-image',{headers:{Authorization:'Bearer '+f.admin}});assert.equal(png.status,200);assert.equal(Buffer.from(await png.arrayBuffer()).subarray(0,8).toString('hex'),'89504e470d0a1a0a');pass('legacy name edit keeps six-place numeric values and old image still renders');
 ok(await api('/metal/product',f.admin,'PUT',{...saved,calculationVersion:2,dimensions:{outerDiameter:'4',innerDiameter:'2',thickness:'1'}}));saved=await detail('/metal/product',legacy,f.admin);assert.equal(saved.calculationVersion,2);assert.equal(Number(saved.volume),9.42);
 assert.notEqual((await api('/metal/product',f.admin,'PUT',{...saved,calculationVersion:1})).code,200);pass('same-name ring conversion requires version two; downgrade is rejected');
 const noTemplate=await api('/metal/product/'+legacy+'/specification-image',f.admin);assert.notEqual(noTemplate.code,200);assert.match(noTemplate.msg,/暂无/);pass('new geometry cannot accidentally use a legacy dimension image');
 const order=await f.createOrder(),before=await detail('/metal/work-order',order,f.admin),product=await detail('/metal/product',f.productA,f.admin);assert.equal(before.items[0].snapshot.calculationVersion,2);
 ok(await api('/metal/product',f.admin,'PUT',{...product,dimensions:{innerDiameter:'0',outerDiameter:'10',length:'21'}}));const after=await detail('/metal/work-order',order,f.admin);assert.deepEqual(after.items[0].snapshot,before.items[0].snapshot);assert.deepEqual(after.items[0].plans,before.items[0].plans);pass('new work orders record version two and retain existing product and route snapshots after edits');
 reader=await login(f.phoneA,f.password);assert.equal((await api('/metal/product/calculation-preview',reader,'POST',{metalTypeId:metal,productType:'方形盖',dimensions:{length:'1',width:'1',height:'1'}})).code,200);assert.equal((await api('/metal/product',reader,'POST',{metalTypeId:metal,productType:'方形盖',dimensions:{length:'1',width:'1',height:'1'},code:'NO_PERMISSION',name:'不应新增',processes:[]})).code,403);pass('query permission may preview but cannot create products');
 assert.equal(sql('SELECT quote_id,CAST(item_snapshot AS CHAR),request_hash FROM metal_saved_quotation ORDER BY quote_id'),quoteBefore);
 if(originalIds.length)assert.equal(sql('SELECT product_id,CAST(dimensions AS CHAR),volume,theoretical_weight,product_category,product_type FROM metal_product WHERE product_id IN ('+originalIds.join(',')+') ORDER BY product_id'),originalProducts);pass('original restored products and historical quote evidence remain unchanged');
}finally{
 if(reader)await api('/logout',reader,'POST');
 if(f){try{if(original){const current=ok(await api('/metal/volume-rule',f.admin)).data;ok(await api('/metal/volume-rule',f.admin,'PUT',{piValue:original.piValue,curvedBottomFactor:original.curvedBottomFactor,version:current.version}));}}finally{await f.cleanup();cleaned=true}}
 writeFileSync(new URL('../.runtime/product-v2-live-result.json',import.meta.url),JSON.stringify({passed:checks.length,checks,cleaned,auditRetained:true},null,2));console.log('CLEANED registered test data; audit retained');
}
