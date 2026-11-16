import{warehouseFixture}from'./warehouse-test-fixture.mjs';import{api,ok,detail,login}from'./local-test-client.mjs';import assert from'node:assert/strict';
const f=await warehouseFixture();let token;let checks=0;
const check=(actual,expected)=>{assert.equal(actual,expected);checks++};
try{
 const incoming=await f.post('RAW_IN',[{category:'RAW',referenceId:f.mat1,netWeight:'100',quantity:0,organization:'候选隔离客户甲'}]);const stock=(await detail('/metal/warehouse-order',incoming,f.admin)).lines[0].inventoryId;await f.post('EXCHANGE_OUT',[{inventoryId:stock,netWeight:'3',quantity:0}]);
 const menus=ok(await api('/metal/options/menus',f.admin)).data;
 async function grant(perms){if(token)await api('/logout',token,'POST');const ids=perms.map(p=>{const m=menus.find(m=>m.perms===p);assert(m,'missing menu '+p);return m.id});const role=await detail('/system/role',f.roleA,f.admin);ok(await api('/system/role',f.admin,'PUT',{...role,menuIds:ids}));token=await login(f.phoneA,f.password);const info=ok(await api('/getInfo',token));for(const p of perms)assert(info.permissions.includes(p));}
 async function option(kind,factory=f.factoryA){return api('/metal/warehouse/options/'+kind+'?factoryId='+factory,token)}
 await grant(['metal:warehouse:list']);check((await api('/metal/inventory/list',token)).code,403);check((await api('/metal/warehouse-order/list',token)).code,403);check((await option('factories')).code,200);for(const k of ['stocks','out-lines','people','products','materials','work-orders','batches','warehouses','processes'])check((await option(k)).code,403);
 await grant(['metal:warehouse-order:list']);check((await option('out-lines')).code,403);
 await grant(['metal:warehouse-order:query']);check((await option('out-lines')).code,200);
 await grant(['metal:inventory:list']);const rows=ok(await option('stocks')).data;assert(rows.some(x=>String(x.id)===String(stock)&&x.netWeight==='97.000000'));checks++;check((await option('out-lines')).code,403);check((await option('stocks',f.factoryB)).code,403);
 await grant(['metal:warehouse-order:add']);for(const k of ['factories','warehouses','people','stocks','out-lines','materials','products','work-orders','batches'])check((await option(k)).code,200);check((await option('stocks',f.factoryB)).code,403);check((await api('/metal/warehouse-order/list',token)).code,403);
 await grant(['metal:inventory:add']);for(const k of ['factories','warehouses','people','materials','products'])check((await option(k)).code,200);check((await option('stocks')).code,403);check((await option('out-lines')).code,403);
 await grant(['metal:inventory:adjust']);for(const k of ['people','stocks'])check((await option(k)).code,200);check((await option('out-lines')).code,403);check((await option('people',f.factoryB)).code,403);
 check((await api('/metal/warehouse/options/stocks?factoryId='+f.factoryA)).code,401);check((await api('/metal/warehouse/options/not-real?factoryId='+f.factoryA,f.admin)).code,500);
 console.log('PASS: '+checks+' isolated candidate permission checks; reader denied stock/source data; add/opening/adjust consumers preserved; anonymous/cross-factory denied.');
}finally{if(token)await api('/logout',token,'POST');await f.cleanup();console.log('CLEANUP: isolated fixtures removed; existing accounts and business data untouched.');}
