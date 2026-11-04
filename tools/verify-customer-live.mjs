import assert from 'node:assert/strict'
import {writeFileSync} from 'node:fs'
if(!process.env.TEST_MYSQL_PORT||process.env.TEST_MYSQL_PORT==='3308'||!process.env.TEST_REDIS_PORT||process.env.TEST_REDIS_PORT==='6380'||!process.env.TEST_API_BASE_URL)throw Error('Explicit isolated MySQL, Redis and API required')
const {api,ok,detail,sql}=await import('./local-test-client.mjs')
const {customerFixture}=await import('./customer-test-fixture.mjs')
const checks=[],pass=name=>{checks.push(name);console.log('PASS '+name)}
const denied=async(path,token,method='GET',body)=>{const result=await api(path,token,method,body);assert.notEqual(result.code,200,path);return result}
let f,cleaned=false
try{
 f=await customerFixture()
 const root='/metal/customer'
 await denied(root,f.admin,'POST',f.body());pass('admin must select factory')
 const first=await f.createCustomer({factoryId:f.factoryA,name:' '+f.prefix+'客户 ',companyName:' '+f.prefix+'公司 '})
 const initial=await detail(root,first,f.admin);assert.equal(initial.name,f.prefix+'客户');assert.equal(initial.factoryId,f.factoryA);assert.equal(initial.department,null);pass('admin create, text trimming and empty department')
 const own=await f.createCustomer({name:f.prefix+'自动绑定'},f.userA);assert.equal((await detail(root,own,f.userA)).factoryId,f.factoryA);pass('ordinary create binds own factory without client factory')
 await denied(root,f.userA,'POST',f.body({factoryId:f.factoryB,name:f.prefix+'伪造'}));pass('forged factory rejected')
 const other=await f.createCustomer({factoryId:f.factoryB});pass('same name and company allowed across factories')
 const duplicate=await denied(root,f.admin,'POST',f.body({factoryId:f.factoryA}));assert.equal(duplicate.fieldErrors[0].field,'companyName');pass('same factory duplicate rejected with inline field location')
 await f.createCustomer({factoryId:f.factoryA,companyName:f.prefix+'其他公司'});pass('same name with another company allowed')
 for(const key of ['name','companyName','address','contactName','contactPhone'])await denied(root,f.admin,'POST',f.body({factoryId:f.factoryA,[key]:'   '}))
 for(const [key,length]of [['name',101],['companyName',101],['address',501],['contactName',101],['department',101]])await denied(root,f.admin,'POST',f.body({factoryId:f.factoryA,[key]:'字'.repeat(length)}))
 for(const phone of ['02812345678','12800138000','1380013800','138001380000','+8613800138000'])await denied(root,f.admin,'POST',f.body({factoryId:f.factoryA,contactPhone:phone}))
 pass('all required fields, length boundaries and mobile validation enforced directly')
 const boundary=await f.createCustomer({factoryId:f.factoryA,name:f.prefix+'客'.repeat(100-f.prefix.length),companyName:'司'.repeat(100),address:'址'.repeat(500),contactName:'人'.repeat(100),department:'部'.repeat(100)});assert.equal((await detail(root,boundary,f.admin)).address.length,500);pass('maximum valid lengths persist')
 const results=await Promise.all([1,2].map(()=>api(root,f.admin,'POST',f.body({factoryId:f.factoryA,name:f.prefix+'并发'}))))
 assert.equal(results.filter(r=>r.code===200).length,1);for(const result of results)if(result.code===200)f.made.customers.push(String(result.id));pass('concurrent duplicate create yields one record')
 for(const token of [f.userA,f.reader,f.editor])await denied(root+'/'+other+'/edit-context',token)
 await denied(root+'/'+other,f.userA);await denied(root,f.userA,'PUT',{...(await detail(root,other,f.admin)),name:f.prefix+'越权编辑'});await denied(root+'/'+other,f.userA,'DELETE');pass('foreign detail, edit context, edit and delete denied')
 const list=ok(await api(root+'/list?pageNum=1&pageSize=16',f.userA));assert(list.rows.every(row=>row.factoryId===f.factoryA));await denied(root+'/list?factoryId='+f.factoryB,f.userA);pass('ordinary list isolated and foreign filter denied')
 const filtered=ok(await api(root+'/list?'+new URLSearchParams({companyName:f.prefix+'公司',contactName:'联络人',contactPhone:'138001',factoryId:f.factoryA}),f.admin));assert(filtered.rows.some(row=>row.id===first));assert(filtered.rows.every(row=>row.companyName===f.prefix+'公司'))
 const literal=ok(await api(root+'/list?name=%25',f.admin));assert.equal(literal.total,0)
 const today=new Date().toLocaleDateString('sv-SE',{timeZone:'Asia/Shanghai'});const dated=ok(await api(root+'/list?startDate='+today+'&endDate='+today,f.userA));assert(dated.rows.some(row=>row.id===first));await denied(root+'/list?startDate=2026-11-03&endDate=2026-11-02',f.admin);pass('combined, literal wildcard and inclusive date filters')
 const page=ok(await api(root+'/list?pageNum=1&pageSize=1',f.userA));assert.equal(page.rows.length,1);assert(page.total>1);await denied(root+'/list?pageSize=101',f.admin);pass('pagination and limits')
 ok(await api(root,f.admin,'PUT',{...initial,department:'研发部'}));const changed=await detail(root,first,f.admin);assert.equal(changed.version,initial.version+1);assert.equal(changed.department,'研发部')
 await denied(root,f.admin,'PUT',{...initial,department:'旧版本'});await denied(root,f.admin,'PUT',{...changed,factoryId:f.factoryB});pass('edit, stale version and immutable factory')
 const duplicateEdit=await detail(root,own,f.admin);await denied(root,f.admin,'PUT',{...duplicateEdit,name:initial.name,companyName:initial.companyName});assert.equal((await detail(root,own,f.admin)).name,duplicateEdit.name);pass('duplicate edit is rejected without changing original record')
 const concurrentRow=await detail(root,first,f.admin),edits=await Promise.all(['并发A','并发B'].map(department=>api(root,f.admin,'PUT',{...concurrentRow,department})));assert.equal(edits.filter(result=>result.code===200).length,1);pass('concurrent edits accept one version only')
 await denied(root+'/'+first+','+other,f.userA,'DELETE');await detail(root,first,f.admin);await denied(root+'/'+first+',9223372036854775806',f.admin,'DELETE');await detail(root,first,f.admin);pass('mixed-factory and missing-ID batch delete is atomic')
 await denied('/metal/factory/'+f.factoryA,f.admin,'DELETE');pass('active customers block factory deletion')
 await denied(root,f.reader,'POST',f.body({factoryId:f.factoryA}));await denied(root,f.reader,'PUT',changed);await denied(root+'/'+first,f.reader,'DELETE')
 await denied(root+'/list',f.adder);await denied(root+'/'+first,f.adder);assert(ok(await api('/metal/options/factories',f.adder)).data.every(row=>row.id===f.factoryA))
 const editOnly=ok(await api(root+'/'+first+'/edit-context',f.editor)).data;ok(await api(root,f.editor,'PUT',{...editOnly,department:'仅编辑'}));await denied(root+'/'+first,f.editor,'DELETE');await denied(root+'/'+first,f.remover);pass('list, query, add, edit and remove grants enforced separately')
 ok(await api(root+'/'+own,f.remover,'DELETE'));assert.equal(sql('SELECT del_flag FROM metal_customer WHERE customer_id='+own),'2');await denied(root+'/'+own,f.admin)
 const rebuilt=await f.createCustomer({name:f.prefix+'自动绑定'},f.userA);assert.notEqual(rebuilt,own);pass('logical deletion and same-name recreation')
 const disabled=f.made.users[0];sql("UPDATE sys_user SET factory_id=NULL WHERE user_id="+disabled);await denied(root,f.userA,'POST',f.body({name:f.prefix+'无厂'}));sql('UPDATE sys_user SET factory_id='+f.factoryA+' WHERE user_id='+disabled);pass('account without valid factory cannot create')
 assert.equal(sql('SELECT COUNT(*) FROM metal_customer WHERE factory_id='+f.factoryB), '1');pass('failed operations leave foreign customer unchanged')
}finally{
 if(f){await f.cleanup();cleaned=true;console.log('CLEANED registered customer fixture; audit retained')}
 writeFileSync(new URL('../.runtime/customer-live-result.json',import.meta.url),JSON.stringify({passed:checks.length,checks,cleaned,auditRetained:true},null,2))
}
