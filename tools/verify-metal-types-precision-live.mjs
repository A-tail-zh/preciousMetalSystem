import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {writeFileSync,readFileSync} from 'node:fs';
import {execFileSync} from 'node:child_process';
// This suite must be explicitly pointed at an isolated database/API/Redis.
if(!process.env.TEST_MYSQL_PORT||process.env.TEST_MYSQL_PORT==='3308'||!process.env.TEST_API_BASE_URL||!process.env.TEST_REDIS_PORT||process.env.TEST_REDIS_PORT==='6380')throw Error('Set isolated TEST_MYSQL_PORT, TEST_API_BASE_URL and TEST_REDIS_PORT before running');
const {api,ok,sql,detail,login}=await import('./local-test-client.mjs');
const {warehouseFixture}=await import('./warehouse-test-fixture.mjs');
const checks=[];const passed=name=>{checks.push(name);console.log('PASS '+name)};
let f,reader;let cleaned=false;
try {
 f=await warehouseFixture({completionPolicy:'SINGLE_OUTPUT_RETURN'});
 const metal=String(ok(await api('/metal/metal-types',f.admin,'POST',{name:f.prefix+'金属'})).id);f.made.metalTypes.push(metal);
 assert.equal((await api('/metal/metal-types',f.admin,'POST',{name:'   '})).code===200,false);
 assert.equal((await api('/metal/metal-types',f.admin,'POST',{name:f.prefix+'金属'})).code===200,false);passed('metal name validation and duplicate protection');
 const body={code:'NEW_METAL_'+f.suffix,name:f.prefix+'其他金属产品',metalTypeId:metal,productType:'杆',dimensions:{innerDiameter:'0',outerDiameter:'2.005',length:'1.234'},weight:'1.235',purity:'99.995',processes:[{processId:f.processA,loss:'0.125'},{processId:f.processB,loss:'0.225'}]};
 const product=String(ok(await api('/metal/product',f.admin,'POST',body)).id);f.made.products.push(product);
 const p=await detail('/metal/product',product,f.admin);assert.equal(Number(p.volume),7.80);assert.equal(p.dimensions.outerDiameter,'2.01');assert.equal(p.weight,null);assert.equal(Number(p.processes[0].loss),0.13);passed('unconfigured metal product saves rounded fields using shared pi with empty weight when density is missing');
 const r={metalTypeId:metal,density:'21.455',powderPrice:'1.235',nonPowderPrice:'2.345',shortagePrice:'0',lossPrice:'4.555',version:0};
 ok(await api('/metal/quote-rule',f.admin,'PUT',r));const rule=ok(await api('/metal/quote-rule?metalTypeId='+metal,f.admin)).data;
 assert.equal(rule.density,'21.46');assert.equal(rule.piValue,'3.14');assert.equal(rule.powderPrice,'1.24');assert.equal(rule.shortagePrice,'0.00');assert.equal(rule.version,1);
 assert.equal((await api('/metal/quote-rule',f.admin,'PUT',r)).code===200,false);passed('per-metal rule rounds, allows explicit zero and rejects stale version');
 ok(await api('/metal/quote-rule',f.admin,'PUT',{...r,powderPrice:'2.455',version:1}));
 const events=sql(`SELECT event_json FROM audit_outbox WHERE JSON_UNQUOTE(JSON_EXTRACT(event_json,'$.objectType'))='metal_type_quote_rule' AND JSON_UNQUOTE(JSON_EXTRACT(event_json,'$.businessCode'))='${metal}' AND JSON_UNQUOTE(JSON_EXTRACT(event_json,'$.code'))='SUCCESS'`).split(/\r?\n/).filter(Boolean).map(JSON.parse);
 const audit=events.find(e=>Number(e.changes?.after?.[metal]?.version)===2);assert(audit,'latest configuration audit has an after snapshot');assert.equal(Number(audit.changes.before[metal].powder_price),1.24);assert.equal(Number(audit.changes.after[metal].powder_price),2.46);assert.equal(JSON.parse(audit.request).body.powderPrice,'2.455');passed('configuration audit preserves original input and both before/after snapshots');
 const trial=ok(await api('/metal/quotation/calculate',f.admin,'POST',{metalTypeId:metal,productType:'杆',dimensions:{innerDiameter:'0',outerDiameter:'2',length:'1'},quantity:2})).data;
 assert.equal(trial.pricingEnabled,false);assert.equal(trial.unitWeight,'134.77');assert.equal(trial.totalWeight,'269.54');assert.equal(trial.totalPrice,undefined);passed('metal-specific weight trial with no currency');
 sql(`UPDATE metal_product SET calculation_version=1,product_type='杆实心',product_category=CONCAT((SELECT name FROM metal_type WHERE metal_type_id=${metal}),'杆实心'),dimensions=JSON_OBJECT('diameter','2.123456','length','1.234567'),theoretical_weight=1.234567,purity=99.123456,volume=4.567891 WHERE product_id=${product}`);sql(`UPDATE metal_product_process SET theoretical_loss=0.123456 WHERE product_id=${product}`);
 const untouched=sql(`SELECT CAST(dimensions AS CHAR),purity,volume FROM metal_product WHERE product_id=${product}`),oldLoss=sql(`SELECT theoretical_loss FROM metal_product_process WHERE product_id=${product} ORDER BY process_id`);
 const legacy=await detail('/metal/product',product,f.admin);ok(await api('/metal/product',f.admin,'PUT',{...legacy,name:legacy.name+'历史兼容'}));assert.equal(sql(`SELECT CAST(dimensions AS CHAR),purity,volume FROM metal_product WHERE product_id=${product}`),untouched);assert.equal(Number((await detail('/metal/product',product,f.admin)).weight),1.234567);assert.equal(sql(`SELECT theoretical_loss FROM metal_product_process WHERE product_id=${product} ORDER BY process_id`),oldLoss);passed('editing preserves untouched dimensions, purity and volume and legacy version-one weight');
 const line=await detail('/metal/production-line',f.lineA,f.admin);ok(await api('/metal/production-line',f.admin,'PUT',{...line,productIds:[...line.productIds,product]}));
 const candidates=ok(await api('/metal/work-order/options/products?lineId='+f.lineA,f.admin)).data;assert(!candidates.some(x=>x.id===product));
 const order=f.body();order.items[0].productId=product;assert.equal((await api('/metal/work-order',f.admin,'POST',order)).code===200,false);passed('non-iridium product excluded from production options and direct create');
 const count=sql('SELECT COUNT(*) FROM metal_saved_quotation');assert.equal((await api('/metal/quotation/save',f.admin,'POST',{requestId:randomUUID(),factoryId:f.factoryA,items:[{category:'铱杆实心',dimensions:{diameter:'2',length:'1'},quantity:2}]})).code===200,false);assert.equal(sql('SELECT COUNT(*) FROM metal_saved_quotation'),count);passed('new quotation saves are disabled without a record');
 reader=await login(f.phoneA,f.password);assert.equal((await api('/metal/quote-rule',reader,'PUT',{...r,version:1})).code===200,false);assert.equal((await api('/metal/metal-types',reader,'POST',{name:f.prefix+'越权'})).code===200,false);passed('read-only product account cannot create metal or alter prices');
 const base=process.env.TEST_API_BASE_URL,headers={Authorization:'Bearer '+f.admin};
 const template=await fetch(base+'/metal/warehouse-order/template',{headers});assert.equal(template.status,200);writeFileSync('.runtime/metal-precision-template.xlsx',Buffer.from(await template.arrayBuffer()));
 for(const [net,gross,impurity,expected] of [['12.345678','','',12.35],['','12.345','2.344',10.01]]){
  execFileSync('python',['tools/build-warehouse-import-fixture.py','.runtime/metal-precision-template.xlsx','.runtime/metal-precision-import.xlsx',f.mat1,net,gross,impurity]);
  const form=new FormData();for(const [k,v] of Object.entries({factoryId:f.factoryA,warehouseId:f.warehouse,type:'RAW_IN'}))form.append(k,v);form.append('file',new Blob([readFileSync('.runtime/metal-precision-import.xlsx')]),'precision.xlsx');
  const preview=ok(await(await fetch(base+'/metal/warehouse-order/import',{method:'POST',headers,body:form})).json()).data;assert.equal(Number(preview[0].netWeight),expected);
  const imported=await f.post('RAW_IN',preview);assert.equal(Number((await detail('/metal/warehouse-order',imported,f.admin)).lines[0].netWeight),expected);
 }passed('Excel imports and actual postings use the same rounded net and component rules');
 const exported=await fetch(base+'/metal/warehouse-order/export?factoryId='+f.factoryA,{method:'POST',headers});assert.equal(exported.status,200);writeFileSync('.runtime/metal-warehouse-export.xlsx',Buffer.from(await exported.arrayBuffer()));
 execFileSync('python',['-c',`import zipfile,xml.etree.ElementTree as E,re,sys
z=zipfile.ZipFile(sys.argv[1]); values=[]
for name in z.namelist():
 if name.startswith('xl/') and name.endswith('.xml'): values.extend(e.text or '' for e in E.fromstring(z.read(name)).iter() if e.tag.endswith('}t'))
assert '12.35' in values and '10.01' in values
assert not [v for v in values if re.fullmatch(r'[+-]?\\d+\\.\\d{3,}',v)]
`,'.runtime/metal-warehouse-export.xlsx']);passed('real warehouse XLSX exports format business decimals to two places');
 const quoteId=sql('SELECT MIN(quote_id) FROM metal_saved_quotation');
 if(quoteId&&quoteId!=='NULL'){
  const before=sql('SELECT CAST(item_snapshot AS CHAR),CAST(total_price AS CHAR),request_hash FROM metal_saved_quotation WHERE quote_id='+quoteId);
  const historical=ok(await api('/metal/quotation/orders/'+quoteId,f.admin)).data;
  assert.equal(historical.totalPrice,sql('SELECT CAST(total_price AS CHAR) FROM metal_saved_quotation WHERE quote_id='+quoteId));
  assert.deepEqual(historical.items,JSON.parse(sql('SELECT CAST(item_snapshot AS CHAR) FROM metal_saved_quotation WHERE quote_id='+quoteId)));
  const download=await fetch(base+'/metal/quotation/orders/'+quoteId+'/download',{headers});assert.equal(download.status,200);assert(download.headers.get('content-type').includes('spreadsheetml'));writeFileSync('.runtime/metal-historical-quote.xlsx',Buffer.from(await download.arrayBuffer()));
  assert.equal(sql('SELECT CAST(item_snapshot AS CHAR),CAST(total_price AS CHAR),request_hash FROM metal_saved_quotation WHERE quote_id='+quoteId),before);passed('historical quotation view and XLSX download preserve raw amounts snapshots and fingerprints');
 }
 const inbound={requestId:randomUUID(),factoryId:f.factoryA,warehouseId:f.warehouse,type:'RAW_IN',orderDate:'2026-11-02',operatorIds:[f.ownerA],keeperId:f.ownerA,lines:[{category:'RAW',referenceId:f.mat1,netWeight:'10.005',grossWeight:'12.345',impurity:'2.344',quantity:0,organization:f.prefix}]};
 const id=String(ok(await api('/metal/warehouse-order',f.admin,'POST',inbound)).id);const doc=await detail('/metal/warehouse-order',id,f.admin),stock=doc.lines[0].inventoryId;
 assert.equal(Number(doc.lines[0].netWeight),10.01);assert.equal(Number(doc.lines[0].grossWeight),12.35);assert.equal(Number(doc.lines[0].impurity),2.34);
 assert.equal(String(ok(await api('/metal/warehouse-order',f.admin,'POST',inbound)).id),id);
 assert.equal((await api('/metal/warehouse-order',f.admin,'POST',{...inbound,lines:[{...inbound.lines[0],netWeight:'10.004'}]})).code===200,false);passed('single warehouse rounding and original-payload replay/conflict checks');
 for(const value of ['0.004','-0.000001'])assert.equal((await api('/metal/warehouse-order',f.admin,'POST',{...inbound,requestId:randomUUID(),lines:[{category:'RAW',referenceId:f.mat1,netWeight:value,quantity:0}]})).code===200,false);passed('tiny positive and negative weights cannot bypass validation');
 const second=String(ok(await api('/metal/warehouse',f.admin,'POST',{requestId:randomUUID(),factoryId:f.factoryA,name:f.prefix+'第二库',sortOrder:1,status:'0'})).id);
 const submission={requestId:randomUUID(),factoryId:f.factoryA,type:'RAW_IN',orderDate:'2026-11-02',operatorIds:[f.ownerA],keeperId:f.ownerA,groups:[{warehouseId:f.warehouse,lines:[{category:'RAW',referenceId:f.mat1,netWeight:'0.125',quantity:0}]},{warehouseId:second,lines:[{category:'RAW',referenceId:f.mat1,netWeight:'0.225',quantity:0}]}]};
 const multi=ok(await api('/metal/warehouse-order/inbound-submissions',f.admin,'POST',submission)).data;assert.equal(multi.orders.length,2);
 const weights=[];for(const o of multi.orders)weights.push(Number((await detail('/metal/warehouse-order',o.id,f.admin)).lines[0].netWeight));assert.deepEqual(weights.sort(),[0.13,0.23]);
 assert.equal(ok(await api('/metal/warehouse-order/inbound-submissions',f.admin,'POST',submission)).data.submissionId,multi.submissionId);passed('multi-warehouse rounded children replay without duplicate stock');
 const before=sql('SELECT COUNT(*) FROM metal_warehouse_order'),ledgers=sql('SELECT COUNT(*) FROM metal_stock_ledger');
 const bad={...submission,requestId:randomUUID(),groups:[submission.groups[0],{warehouseId:second,lines:[{category:'RAW',referenceId:f.mat1,netWeight:'0.004',quantity:0}]}]};assert.equal((await api('/metal/warehouse-order/inbound-submissions',f.admin,'POST',bad)).code===200,false);assert.equal(sql('SELECT COUNT(*) FROM metal_warehouse_order'),before);assert.equal(sql('SELECT COUNT(*) FROM metal_stock_ledger'),ledgers);passed('second-warehouse rounding failure rolls back all writes');
 const batch=await f.createBatch();await f.post('PRODUCTION_OUT',[{inventoryId:stock,netWeight:'5.555',quantity:0}],{workOrderId:f.wo,itemId:f.item.id,processId:f.processA,batchId:batch});
 await f.post('SEMI_IN',[{category:'SEMI',referenceId:f.productA,netWeight:'5.001',quantity:5}],{workOrderId:f.wo,itemId:f.item.id,processId:f.processA,batchId:batch});
 const context=ok(await api('/metal/process-batch/'+batch+'/completion-context',f.admin)).data;
 const confirmation={requestId:randomUUID(),version:context.version,mode:'NONE',physicalChecked:true,reason:'隔离测试无余料确认',documents:[]};ok(await api('/metal/process-batch/'+batch+'/confirm-return',f.admin,'POST',confirmation));ok(await api('/metal/process-batch/'+batch+'/confirm-return',f.admin,'POST',confirmation));
 assert.equal(sql('SELECT COUNT(*) FROM metal_work_report WHERE batch_id='+batch),'1');assert.equal(Number(sql('SELECT loss_weight FROM metal_work_report WHERE batch_id='+batch)),0.56);assert.equal(Number(sql('SELECT average_loss FROM metal_work_report WHERE batch_id='+batch)),0.11);passed('rounded production balances settle once and store two-place loss metrics');
 const consumption=ok(await api('/metal/work-report/consumption/batches?workOrderId='+f.wo+'&itemId='+f.item.id+'&processId='+f.processA,f.admin)).data.rows.find(x=>String(x.batchId)===String(batch));assert.equal(consumption.state,'SETTLED');assert.equal(consumption.reportPrecisionVersion,2);passed('new two-place report remains a consistent settled consumption batch');
 const settled=await detail('/metal/process-batch',batch,f.admin);ok(await api('/metal/process-batch/'+batch+'/reverse',f.admin,'POST',{version:settled.version,reason:'隔离测试冲销'}));assert.equal((await detail('/metal/process-batch',batch,f.admin)).status,'VOID');passed('rounded production batch reversal restores balances');
 assert.equal(sql('SELECT COUNT(*) FROM metal_stock_ledger WHERE inventory_id IN (SELECT inventory_id FROM metal_inventory WHERE factory_id='+f.factoryA+') AND (delta_weight<>ROUND(delta_weight,2) OR balance_weight<>ROUND(balance_weight,2))'),'0');passed('all fixture stock ledger values have at most two decimal places');
} finally {
 if(reader)await api('/logout',reader,'POST');
 if(f){await f.cleanup();cleaned=true;console.log('CLEANED registered fixture; audit retained');}
 writeFileSync(new URL('../.runtime/metal-types-precision-live-result.json',import.meta.url),JSON.stringify({passed:checks.length,checks,cleaned,auditRetained:true},null,2));
}
