import assert from 'node:assert/strict';
import {createHash,randomUUID} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {writeFileSync} from 'node:fs';
if(!process.env.TEST_MYSQL_PORT||process.env.TEST_MYSQL_PORT==='3308'||!process.env.TEST_REDIS_PORT||process.env.TEST_REDIS_PORT==='6380'||!process.env.TEST_API_BASE_URL)throw Error('Explicit isolated services required');
const {api,ok,detail,sql,login}=await import('./local-test-client.mjs');
const {workOrderFixture}=await import('./work-order-test-fixture.mjs');
let f,quoteId,other,cleaned=false;const requestId=randomUUID(),checks=[];const pass=name=>{checks.push(name);console.log('PASS '+name)};
try{
 f=await workOrderFixture();
 const dimensions={outerDiameter:'4',innerDiameter:'2',height:'1.234567'},snapshot=[{category:'铱环',quantity:2,dimensions,volume:'11.234567',unitPrice:'1.234567',totalPrice:'2.469134',rule:{piValue:'3.141593',basePrice:'22.65',version:6}}];
 const canonical=f.factoryA+':[铱环:2:{height=1.234567, innerDiameter=2, outerDiameter=4}]',hash=createHash('sha256').update(canonical).digest('hex');
 sql(`INSERT INTO metal_saved_quotation(quote_no,request_id,request_hash,request_actor,factory_id,category_labels,item_snapshot,total_price,rule_version) VALUES('PV2H${Date.now()}','${requestId}','${hash}',1,${f.factoryA},'铱环',CONVERT(0x${Buffer.from(JSON.stringify(snapshot)).toString('hex')} USING utf8mb4),2.469134,6)`);
 quoteId=sql(`SELECT quote_id FROM metal_saved_quotation WHERE request_id='${requestId}'`);assert(/^\d+$/.test(quoteId));
 const original=sql(`SELECT CAST(item_snapshot AS CHAR),total_price,request_hash FROM metal_saved_quotation WHERE quote_id=${quoteId}`);
 const saved=await detail('/metal/quotation/orders',quoteId,f.admin);assert.deepEqual(saved.items,snapshot);assert.equal(saved.totalPrice,'2.469134');pass('historical detail retains six-place values and version-one dimensions');
 const replay=ok(await api('/metal/quotation/save',f.admin,'POST',{requestId,factoryId:f.factoryA,items:[{category:'铱环',quantity:2,dimensions}]})).data;assert.equal(replay.id,quoteId);assert.deepEqual(replay.items,snapshot);pass('original request replays without new geometry or repricing');
 assert.notEqual((await api('/metal/quotation/save',f.admin,'POST',{requestId,factoryId:f.factoryA,items:[{category:'铱环',quantity:2,dimensions:{...dimensions,height:'1.23'}}]})).code,200);pass('changed historical request fingerprint is rejected');
 const response=await fetch(process.env.TEST_API_BASE_URL+'/metal/quotation/orders/'+quoteId+'/download',{headers:{Authorization:'Bearer '+f.admin}});assert.equal(response.status,200);const book=Buffer.from(await response.arrayBuffer());assert.equal(book.subarray(0,2).toString(),'PK');
 const parsed=JSON.parse(execFileSync('python',['-c',`import sys,io,zipfile,xml.etree.ElementTree as E,json
z=zipfile.ZipFile(io.BytesIO(sys.stdin.buffer.read()));n={'m':'http://schemas.openxmlformats.org/spreadsheetml/2006/main'}
s=E.fromstring(z.read('xl/sharedStrings.xml'));strings=[''.join(t.text or '' for t in x.findall('.//m:t',n)) for x in s.findall('m:si',n)]
print(json.dumps({'strings':strings,'media':len([x for x in z.namelist() if x.startswith('xl/media/')])},ensure_ascii=True))`],{input:book,encoding:'utf8'}));
 assert(parsed.strings.includes('铱环'));assert(parsed.strings.includes('2.47'));assert(parsed.strings.includes('1.23'));assert(parsed.media>0);pass('historical XLSX still downloads stored prices and embeds legacy dimension image');
 const image=await fetch(process.env.TEST_API_BASE_URL+'/metal/quotation/orders/'+quoteId+'/items/1/image',{headers:{Authorization:'Bearer '+f.admin}});assert.equal(image.status,200);assert.equal(Buffer.from(await image.arrayBuffer()).subarray(0,8).toString('hex'),'89504e470d0a1a0a');pass('historical image endpoint uses the legacy template');
 const menus=ok(await api('/metal/options/menus',f.admin)).data,role=await detail('/system/role',f.roleB,f.admin);const ids=new Set(role.menuIds);for(const item of menus.filter(x=>x.perms==='metal:quotation:list')){ids.add(item.id);let parent=item.parentId;while(parent&&parent!=='0'){ids.add(parent);parent=menus.find(x=>x.id===parent)?.parentId}}
 ok(await api('/system/role',f.admin,'PUT',{...role,menuIds:[...ids]}));other=await login(f.phoneB,f.password);assert.equal((await api('/metal/quotation/orders/'+quoteId,other)).code,403);pass('another factory cannot read the historical quote');
 assert.equal(sql(`SELECT CAST(item_snapshot AS CHAR),total_price,request_hash FROM metal_saved_quotation WHERE quote_id=${quoteId}`),original);pass('view, replay and download leave historical evidence unchanged');
}finally{
 if(other)await api('/logout',other,'POST');
 if(f){try{if(quoteId){assert.equal(sql(`SELECT CONCAT(factory_id,':',request_id) FROM metal_saved_quotation WHERE quote_id=${quoteId}`),f.factoryA+':'+requestId);sql(`DELETE FROM metal_saved_quotation WHERE quote_id=${quoteId} AND factory_id=${f.factoryA} AND request_id='${requestId}'`)}}finally{await f.cleanup();cleaned=true}}
 writeFileSync(new URL('../.runtime/product-v2-history-result.json',import.meta.url),JSON.stringify({passed:checks.length,checks,cleaned,auditRetained:true},null,2));console.log('CLEANED owned historical fixture; audit retained');
}
