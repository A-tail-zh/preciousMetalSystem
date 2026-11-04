import assert from 'node:assert/strict';
import {randomBytes} from 'node:crypto';
import {writeFileSync} from 'node:fs';
if(!process.env.TEST_MYSQL_PORT||process.env.TEST_MYSQL_PORT==='3308'||!process.env.TEST_REDIS_PORT||process.env.TEST_REDIS_PORT==='6380'||!process.env.TEST_API_BASE_URL)throw Error('Explicit isolated MySQL, Redis and API required');
const {api,ok,sql,login}=await import('./local-test-client.mjs');
const {trackFixture}=await import('./test-fixture-registry.mjs');
const {fixtureEnvironment}=await import('./test-fixture-scope.mjs');
const {cleanupWorkOrderFixture}=await import('./work-order-test-fixture.mjs');
const prefix='工单验收_'+Date.now()+'_'+randomBytes(3).toString('hex'),made={metalTypes:[],factories:[],lines:[],products:[],processes:[],roles:[],users:[],materials:[]},environment=fixtureEnvironment();
trackFixture(prefix,made);const admin=await login(),checks=[];let cleaned=false;
const pass=name=>{checks.push(name);console.log('PASS '+name)};
const protectedRows=()=>sql('SELECT quote_id,CAST(item_snapshot AS CHAR),total_price,request_hash,request_actor FROM metal_saved_quotation ORDER BY quote_id')+'\n'+sql('SELECT product_id,CAST(dimensions AS CHAR),volume,theoretical_weight,product_type,calculation_version FROM metal_product ORDER BY product_id');
const original=protectedRows();
try{
 const createMetal=async(name,prices)=>{const id=String(ok(await api('/metal/metal-types',admin,'POST',{name:prefix+name})).id);made.metalTypes.push(id);if(prices)ok(await api('/metal/quote-rule',admin,'PUT',{metalTypeId:id,version:0,density:'22.65',powderPrice:'2',nonPowderPrice:'3',shortagePrice:'4',lossPrice:'7',...prices}));return id};
 const metal=await createMetal('加工报价',{}),other=await createMetal('另一金属',{density:'21.45',shortagePrice:'5'}),unconfigured=await createMetal('未配报价');
 const base={metalTypeId:metal,productType:'杆',dimensions:{innerDiameter:'0',outerDiameter:'2',length:'1'},quantity:2};
 const request=body=>api('/metal/quotation/calculate',admin,'POST',body);
 const context=ok(await api('/metal/quotation/context',admin)).data;assert.equal(context.pricingEnabled,true);assert.deepEqual(context.processingTypes.map(x=>x.value),['MATERIAL_CUSTOM','DIRECT_CUSTOM','SHORTAGE_CUSTOM']);pass('context advertises three processing types and price trial support');
 for(const processingType of ['MATERIAL_CUSTOM','DIRECT_CUSTOM'])for(const quantity of [1,2,99]){
  const result=ok(await request({...base,processingType,powderWeight:'10',nonPowderWeight:'5',lossWeight:'1',quantity,totalPrice:'0.01',unitPrice:'0.01'})).data;
  assert.equal(result.totalPrice,'42.00');assert.equal(result.powderAmount,'20.00');assert.equal(result.nonPowderAmount,'15.00');assert.equal(result.lossAmount,'7.00');assert.equal(result.pricingEnabled,true);assert(!('unitPrice'in result));
 }pass('material/direct fixed sample is 42.00, aggregate weights are never multiplied again, client amounts ignored');
 const shortage={...base,processingType:'SHORTAGE_CUSTOM',lossWeight:'1'},result=ok(await request(shortage)).data;
 assert.equal(result.totalWeight,'284.48');assert.equal(result.productAmount,'1137.92');assert.equal(result.lossAmount,'4.00');assert.equal(result.totalPrice,'1141.92');assert(!('powderWeight'in result));pass('shortage uses total theoretical weight and shortage price for both terms');
 const second=ok(await request({...shortage,metalTypeId:other})).data;assert.equal(second.totalWeight,'269.42');assert.equal(second.totalPrice,'1352.10');pass('selected metal supplies its own density and prices');
 const legacy=ok(await request(base)).data;assert.equal(legacy.pricingEnabled,false);assert.equal(legacy.totalWeight,'284.48');assert(!('totalPrice'in legacy));pass('old weight-only request remains compatible');
 for(const body of [{...base,lossWeight:'0'},{...shortage,processingType:'UNKNOWN'},{...shortage,lossWeight:null},{...shortage,lossWeight:''},{...shortage,lossWeight:'-0.004'},{...shortage,lossWeight:'1e2'},{...shortage,lossWeight:'NaN'},{...shortage,lossWeight:'1.1234567'},{...shortage,lossWeight:'100000000000000'},{...shortage,powderWeight:'0'},{...shortage,nonPowderWeight:'0'},{...shortage,metalTypeId:unconfigured},{...shortage,quantity:0},{...shortage,quantity:1.5}])assert.notEqual((await request(body)).code,200);
 pass('direct API rejects missing/invalid type, missing and malformed weights, hidden materials, invalid quantity and missing config');
 const bad=await request({...shortage,lossWeight:'1e2'});assert(bad.fieldErrors.some(x=>x.field==='lossWeight'&&x.message.includes('损失重量')));pass('DTO validation returns Chinese field paths');
 assert.equal((await api('/metal/quotation/calculate',undefined,'POST',shortage)).code,401);pass('unauthenticated pricing is denied');
 let rule=ok(await api('/metal/quote-rule?metalTypeId='+metal,admin)).data;
 ok(await api('/metal/quote-rule',admin,'PUT',{...rule,powderPrice:'0.5',nonPowderPrice:'0.5',lossPrice:'0.5'}));
 const rounded=ok(await request({...base,processingType:'MATERIAL_CUSTOM',powderWeight:'0.005',nonPowderWeight:'0.005',lossWeight:'0.005'})).data;assert.equal(rounded.powderWeight,'0.01');assert.equal(rounded.totalPrice,'0.03');pass('weights round before multiplying, components round before summing');
 rule=ok(await api('/metal/quote-rule?metalTypeId='+metal,admin)).data;ok(await api('/metal/quote-rule',admin,'PUT',{...rule,powderPrice:'0',nonPowderPrice:'0',lossPrice:'0',shortagePrice:'0'}));
 assert.equal(ok(await request({...base,processingType:'MATERIAL_CUSTOM',powderWeight:'0',nonPowderWeight:'0',lossWeight:'0'})).data.totalPrice,'0.00');assert.equal(ok(await request(shortage)).data.totalPrice,'0.00');pass('explicit zero weights and zero prices are valid');
 rule=ok(await api('/metal/quote-rule?metalTypeId='+metal,admin)).data;ok(await api('/metal/quote-rule',admin,'PUT',{...rule,powderPrice:'99999999999999.99'}));
 assert.notEqual((await request({...base,processingType:'MATERIAL_CUSTOM',powderWeight:'2',nonPowderWeight:'0',lossWeight:'0'})).code,200);pass('amount overflow is rejected');
 assert.equal(protectedRows(),original);pass('original product values and historical quotation snapshots/fingerprints remain unchanged');
 const save=await api('/metal/quotation/save',admin,'POST',{requestId:crypto.randomUUID(),factoryId:1,items:[base]});assert.notEqual(save.code,200);assert.equal(protectedRows(),original);pass('new saved quotations remain disabled');
}finally{
 await cleanupWorkOrderFixture({prefix,made,environment},admin);cleaned=true;
 assert.equal(sql("SELECT COUNT(*) FROM metal_type WHERE LEFT(name,CHAR_LENGTH('"+prefix+"'))='"+prefix+"'"),'0');
 writeFileSync(new URL('../.runtime/quotation-pricing/live-result.json',import.meta.url),JSON.stringify({passed:checks.length,checks,cleaned,auditRetained:true},null,2));console.log('CLEANED registered fixture; audit retained');
}
