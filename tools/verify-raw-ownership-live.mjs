import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {writeFileSync,readFileSync,mkdirSync} from 'node:fs';
import {execFileSync} from 'node:child_process';
import {warehouseFixture} from './warehouse-test-fixture.mjs';
import {api,ok,detail,sql,login} from './local-test-client.mjs';
const f=await warehouseFixture(), checks=[];
const check=(name)=>checks.push(name);
const stockLine=async id=>(await detail('/metal/warehouse-order',id,f.admin)).lines[0];
const incoming=async(org,weight='100',extra={})=>stockLine(await f.post('RAW_IN',[{category:'RAW',referenceId:f.mat1,netWeight:weight,quantity:0,organization:org}],extra));
const reject=async(fn,pattern)=>{await assert.rejects(fn,pattern);};
try{
 const a=await incoming('Supplier A'), b=await incoming('Supplier B');assert.notEqual(a.inventoryId,b.inventoryId);check('Different suppliers remain separate');
 const a2=await incoming(' Supplier A ','20');assert.equal(a.inventoryId,a2.inventoryId);assert.equal(a2.organization,'Supplier A');check('Same supplier and trimmed names merge');
 const unknown=await incoming(undefined,'10'),unknown2=await incoming('  ','10');assert.equal(unknown.inventoryId,unknown2.inventoryId);assert.notEqual(unknown.inventoryId,a.inventoryId);check('Unknown sources stay separate from named suppliers');
 const concurrent=await Promise.all([incoming('Supplier A','3'),incoming('Supplier A','4')]);assert(concurrent.every(x=>x.inventoryId===a.inventoryId));assert.equal(Number((await detail('/metal/inventory',a.inventoryId,f.admin)).netWeight),127);check('Concurrent receipts preserve identity and balance');
 const batch=await f.createBatch(f.processA,5);
 const out=await f.post('PRODUCTION_OUT',[{inventoryId:a.inventoryId,netWeight:'10',quantity:0,organization:'FORGED'}],{batchId:batch});const outLine=await stockLine(out);assert.equal(outLine.organization,'Supplier A');assert.equal(Number((await detail('/metal/inventory',b.inventoryId,f.admin)).netWeight),100);check('Issue derives supplier server-side and debits selected stock only');
 const before=Number((await detail('/metal/inventory',a.inventoryId,f.admin)).netWeight);await reject(()=>f.post('PRODUCTION_OUT',[{inventoryId:a.inventoryId,netWeight:'118',quantity:0}],{batchId:batch}),/库存不足/);assert.equal(Number((await detail('/metal/inventory',a.inventoryId,f.admin)).netWeight),before);check('Insufficient stock rolls back without using other suppliers');
 // Quarantine only this isolated fixture, never change historical business rows.
 sql(`UPDATE metal_inventory SET ownership_status='REVIEW' WHERE inventory_id=${a.inventoryId} AND factory_id=${f.factoryA}`);
 await reject(()=>f.post('PRODUCTION_OUT',[{inventoryId:a.inventoryId,netWeight:'1',quantity:0}],{batchId:batch}),/来源待核对/);
 await reject(()=>f.post('EXCHANGE_OUT',[{inventoryId:a.inventoryId,netWeight:'1',quantity:0}]),/来源待核对/);check('Quarantined stock rejects production and customer-return issues');
 const current=await detail('/metal/inventory',a.inventoryId,f.admin);const adjustment=await api('/metal/inventory/'+a.inventoryId+'/adjust',f.admin,'POST',{requestId:randomUUID(),version:current.version,operatorId:f.ownerA,keeperId:f.ownerA,deltaWeight:'1',deltaQuantity:0,reason:'isolated ownership test'});assert.notEqual(adjustment.code,200);assert.match(adjustment.msg,/来源待核对/);check('Quarantined balance adjustments blocked');
 const returned=await f.post('RAW_RETURN',[{sourceLineId:outLine.id,netWeight:'2',quantity:0,organization:'FORGED'}],{batchId:batch});const ret=await stockLine(returned);assert.equal(ret.organization,'Supplier A');assert.equal(ret.inventoryId,a.inventoryId);check('Returns retain original supplier and original quarantined stock');
 const returnChoice=ok(await api('/metal/warehouse/options/out-lines?factoryId='+f.factoryA,f.admin)).data.find(x=>x.id===outLine.id);assert.equal(Number(returnChoice.remainingWeight),8);assert.equal(returnChoice.organization,'Supplier A');assert.equal(returnChoice.warehouseId,f.warehouse);check('Return options expose supplier, original warehouse and remaining weight');
 const freshA=await incoming('Supplier A','7');assert.notEqual(freshA.inventoryId,a.inventoryId);assert.equal((await detail('/metal/inventory',freshA.inventoryId,f.admin)).ownershipStatus,'NORMAL');check('New receipts do not merge into historical quarantine');
 await reject(()=>f.post('RAW_RETURN',[{sourceLineId:outLine.id,netWeight:'9',quantity:0}],{batchId:batch}),/累计退还/);check('Cumulative return limit remains enforced');
 const exchange=await f.post('EXCHANGE_OUT',[{inventoryId:b.inventoryId,netWeight:'1',quantity:0,organization:'FORGED'}]);assert.equal((await stockLine(exchange)).organization,'Supplier B');check('Customer-return issue also inherits source');
 const xlsx='.runtime/raw-ownership-import.xlsx';execFileSync('python',['-c',`from openpyxl import Workbook
w=Workbook();s=w.active;s.append(['库存ID','资料ID','原出库明细ID','分类','批次号','净重(g)','数量','毛重(g)','杂质(g)','纯度(%)','单位名称','用途','备注']);s.append(['${b.inventoryId}',None,None,None,None,'1',0,None,None,None,'FORGED',None,None]);w.save('${xlsx}')`]);
 const file=new FormData();file.append('file',new Blob([readFileSync(xlsx)]),'test.xlsx');file.append('factoryId',f.factoryA);file.append('warehouseId',f.warehouse);file.append('type','EXCHANGE_OUT');const preview=ok(await(await fetch('http://127.0.0.1:8080/metal/warehouse-order/import',{method:'POST',headers:{Authorization:'Bearer '+f.admin},body:file})).json());assert.equal(preview.data[0].organization,'Supplier B');check('Excel import ignores forged supplier');
 const importAgain=async(type)=>{const form=new FormData();form.append('file',new Blob([readFileSync(xlsx)]),'test.xlsx');form.append('factoryId',f.factoryA);form.append('warehouseId',f.warehouse);form.append('type',type);if(type==='RAW_RETURN'){form.append('workOrderId',f.wo);form.append('itemId',f.item.id);form.append('processId',f.processA);}return (await fetch('http://127.0.0.1:8080/metal/warehouse-order/import',{method:'POST',headers:{Authorization:'Bearer '+f.admin},body:form})).json();};
 execFileSync('python',['-c',`from openpyxl import load_workbook
w=load_workbook('${xlsx}');s=w.active;s.cell(2,1).value=None;s.cell(2,3).value='${outLine.id}';w.save('${xlsx}')`]);
 assert.equal(ok(await importAgain('RAW_RETURN')).data[0].organization,'Supplier A');check('Return import inherits original outgoing supplier');
 execFileSync('python',['-c',`from openpyxl import load_workbook
w=load_workbook('${xlsx}');s=w.active;s.cell(2,1).value='${a.inventoryId}';s.cell(2,3).value=None;w.save('${xlsx}')`]);
 const blockedImport=await importAgain('EXCHANGE_OUT');assert.notEqual(blockedImport.code,200);assert.match(blockedImport.msg,/来源待核对/);check('Import cannot bypass historical quarantine');
 const secondWarehouse=String(ok(await api('/metal/warehouse',f.admin,'POST',{requestId:randomUUID(),factoryId:f.factoryA,name:f.prefix+'return target',sortOrder:1,status:'0'})).id);
 await reject(()=>f.post('RAW_RETURN',[{sourceLineId:outLine.id,netWeight:'1',quantity:0}],{batchId:batch,warehouseId:secondWarehouse}),/原出库库存所在仓库/);check('Raw return cannot silently change source warehouse');
 const exchangeDoc=await detail('/metal/warehouse-order',exchange,f.admin);ok(await api('/metal/warehouse-order/'+exchange+'/reverse',f.admin,'POST',{version:exchangeDoc.version,reason:'isolated reversal test'}));assert.equal(Number((await detail('/metal/inventory',b.inventoryId,f.admin)).netWeight),100);check('Reversal restores original supplier stock');
 const other=await login(f.phoneB,f.password);try{assert.equal((await api('/metal/warehouse/options/stocks?factoryId='+f.factoryA,other)).code,403);}finally{await api('/logout',other,'POST');}check('Cross-factory stock access denied');
 mkdirSync('.runtime',{recursive:true});writeFileSync('.runtime/raw-ownership-verification.json',JSON.stringify({checks,passed:checks.length},null,2));console.log(JSON.stringify({passed:checks.length,checks},null,2));
}finally{await f.cleanup();}
