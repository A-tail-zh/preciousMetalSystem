import assert from 'node:assert/strict'
import {randomUUID} from 'node:crypto'
import {execFileSync} from 'node:child_process'
import {warehouseFixture} from './warehouse-test-fixture.mjs'
import {api,ok,detail,sql,login} from './local-test-client.mjs'
let checks=0;const check=(value,message)=>{assert.ok(value,message);checks++;console.log('PASS '+message)}
const f=await warehouseFixture({completionPolicy:'SINGLE_OUTPUT_RETURN'});
const context=async id=>ok(await api(`/metal/process-batch/${id}/completion-context`,f.admin)).data;
const confirm=async(id,mode,documents=[],extra={})=>api(`/metal/process-batch/${id}/confirm-return`,f.admin,'POST',{requestId:randomUUID(),version:(await context(id)).version,mode,physicalChecked:true,reason:'隔离验收全部实物核对',documents,...extra});
const doc=(type,lines)=>({type,warehouseId:f.warehouse,orderDate:'2026-10-23',operatorIds:[f.ownerA],keeperId:f.ownerA,reason:'隔离验收还料',lines});
const preview=async(batch,type,line,warehouseId=f.warehouse)=>{
 const headers=['库存ID','资料ID','原出库明细ID','分类','批次号','净重(g)','数量','毛重(g)','杂质(g)','纯度(%)','单位名称','用途','备注'];
 const bytes=execFileSync('python',['-c',"import json,sys,io,openpyxl;d=json.loads(sys.stdin.buffer.read().decode('utf-8'));w=openpyxl.Workbook();w.active.append(d['headers']);w.active.append(d['row']);b=io.BytesIO();w.save(b);sys.stdout.buffer.write(b.getvalue())"],{input:JSON.stringify({headers,row:[null,line.referenceId??null,line.sourceLineId??null,line.category??null,null,line.netWeight,line.quantity??0,null,null,null,null,null,null]}),maxBuffer:1000000});
 const form=new FormData();form.append('file',new Blob([bytes]),'isolated-return.xlsx');for(const[k,v]of Object.entries({factoryId:f.factoryA,warehouseId,type,workOrderId:f.wo,itemId:f.item.id,processId:f.processB,batchId:batch}))if(v!==null)form.append(k,String(v));return (await fetch('http://127.0.0.1:8080/metal/warehouse-order/import',{method:'POST',headers:{Authorization:'Bearer '+f.admin},body:form})).json();
};
const raw=(await detail('/metal/warehouse-order',await f.post('RAW_IN',[{category:'RAW',referenceId:f.mat1,netWeight:'2000',quantity:0}]),f.admin)).lines[0].inventoryId;
try{
 let b1=await f.createBatch(f.processA,10);check((await context(b1)).completionPolicy==='SINGLE_OUTPUT_RETURN','新批次执行新策略');
 check((await confirm(b1,'NONE')).code!==200,'无产出不能确认结束');
 await f.post('PRODUCTION_OUT',[{inventoryId:raw,netWeight:'1000',quantity:0}],{batchId:b1});
 let output=await f.post('SEMI_IN',[{category:'SEMI',referenceId:f.productA,netWeight:'990',quantity:10}],{batchId:b1});
 const wrong=await detail('/metal/warehouse-order',output,f.admin);
 check((await api('/metal/warehouse-order/'+output+'/reverse',f.admin,'POST',{version:wrong.version,reason:'隔离验收未确认产出纠错'})).code===200,'未确认产出允许单独纠错冲销');
 check((await context(b1)).phase==='AWAITING_OUTPUT','纠错后有效入库次数归零');
 output=await f.post('SEMI_IN',[{category:'SEMI',referenceId:f.productA,netWeight:'990',quantity:10}],{batchId:b1});
 check((await context(b1)).phase==='AWAITING_RETURN','足额入库仍等待还料确认');
 const outputAgain={requestId:randomUUID(),factoryId:f.factoryA,warehouseId:f.warehouse,type:'SEMI_IN',orderDate:'2026-10-23',operatorIds:[f.ownerA],keeperId:f.ownerA,batchId:b1,batchVersion:(await context(b1)).version,lines:[{category:'SEMI',referenceId:f.productA,netWeight:'1',quantity:1}]};
 check((await api('/metal/warehouse-order',f.admin,'POST',outputAgain)).code!==200,'第二次有效入库被拒绝');
 check((await api(`/metal/process-batch/${b1}/settle`,f.admin,'POST',{version:(await context(b1)).version,completedQuantity:10})).code!==200,'独立旧结算不能绕过还料确认');
 check((await confirm(b1,'NONE',[],{physicalChecked:false})).code!==200,'必须核对实物');
 check((await confirm(b1,'NONE',[],{version:999999})).code!==200,'版本冲突不结算');
 const deniedToken=await login(f.phoneA,f.password);check((await api(`/metal/process-batch/${b1}/confirm-return`,deniedToken,'POST',{requestId:randomUUID(),version:(await context(b1)).version,mode:'NONE',physicalChecked:true,documents:[]})).code===403,'普通录入权限不能替代结算权限');await api('/logout',deniedToken,'POST');
 const menus=ok(await api('/metal/options/menus',f.admin)).data,role=await detail('/system/role',f.roleB,f.admin);ok(await api('/system/role',f.admin,'PUT',{...role,menuIds:[...new Set([...role.menuIds,...menus.filter(m=>['metal:warehouse-order:add','metal:process-batch:settle'].includes(m.perms)).map(m=>m.id)])]}));const foreign=await login(f.phoneB,f.password);check((await api(`/metal/process-batch/${b1}/completion-context`,foreign)).code===403,'有入口权限的跨工厂人员仍被拒绝');await api('/logout',foreign,'POST');
 const uuid=randomUUID(),version=(await context(b1)).version,payload={requestId:uuid,version,mode:'NONE',physicalChecked:true,reason:'隔离确认无余料',documents:[]};
 const result=ok(await api(`/metal/process-batch/${b1}/confirm-return`,f.admin,'POST',payload)).data;
 check(result.status==='SETTLED'&&Number(result.reports[0].lossWeight)===10,'无余料正确结算10g损耗');
 check(ok(await api(`/metal/process-batch/${b1}/confirm-return`,f.admin,'POST',payload)).data.status==='SETTLED','相同UUID重放成功');
 check((await api(`/metal/process-batch/${b1}/confirm-return`,f.admin,'POST',{...payload,reason:'changed'})).code!==200,'同UUID不同内容拒绝');
 check(sql('SELECT COUNT(*) FROM metal_work_report WHERE batch_id='+b1)==='1','无重复报工');
 const firstSettled=b1;check((await api(`/metal/process-batch/${b1}/reverse`,f.admin,'POST',{version:(await context(b1)).version,reason:'隔离验收整批冲销'})).code===200,'无下游依赖时整批冲销确认及报工');
 check(sql("SELECT status FROM metal_batch_return_confirmation WHERE batch_id="+b1)==='VOID','整批冲销同步失效确认');
 check(ok(await api(`/metal/process-batch/${firstSettled}/confirm-return`,f.admin,'POST',payload)).data.status==='VOID','已冲销请求重放不重新生效');
 b1=await f.createBatch(f.processA,10);await f.post('PRODUCTION_OUT',[{inventoryId:raw,netWeight:'1000',quantity:0}],{batchId:b1});output=await f.post('SEMI_IN',[{category:'SEMI',referenceId:f.productA,netWeight:'990',quantity:10}],{batchId:b1});ok(await confirm(b1,'NONE'));
 const semi=(await detail('/metal/warehouse-order',output,f.admin)).lines[0].inventoryId,b2=await f.createBatch(f.processB,6),reserved=await f.createBatch(f.processB,4);
 const issued=await f.post('PRODUCTION_OUT',[{inventoryId:semi,netWeight:'990',quantity:10},{inventoryId:raw,netWeight:'5',quantity:0}],{batchId:b2});
 const issueLines=(await detail('/metal/warehouse-order',issued,f.admin)).lines,semiLine=issueLines.find(l=>l.category==='SEMI'),rawLine=issueLines.find(l=>l.category==='RAW');
 const wh2=String(ok(await api('/metal/warehouse',f.admin,'POST',{requestId:randomUUID(),factoryId:f.factoryA,name:f.prefix+'第二库房',sortOrder:0,status:'0'})).id),raw2=(await detail('/metal/warehouse-order',await f.post('RAW_IN',[{category:'RAW',referenceId:f.mat1,netWeight:'4',quantity:0}],{warehouseId:wh2}),f.admin)).lines[0].inventoryId;
 const supplementary=await f.post('PRODUCTION_OUT',[{inventoryId:raw2,netWeight:'4',quantity:0}],{batchId:b2,warehouseId:wh2}),secondRawLine=(await detail('/metal/warehouse-order',supplementary,f.admin)).lines[0];
 await f.post('FINISHED_IN',[{category:'FINISHED',referenceId:f.productA,netWeight:'580',quantity:6}],{batchId:b2});
 const documents=[doc('RAW_RETURN',[{sourceLineId:rawLine.id,netWeight:'4',quantity:0}]),{...doc('RAW_RETURN',[{sourceLineId:secondRawLine.id,netWeight:'4',quantity:0}]),warehouseId:wh2},doc('SEMI_RETURN',[{sourceLineId:semiLine.id,netWeight:'396',quantity:4}]),doc('PROCESS_RAW_RETURN',[{category:'RAW',referenceId:f.mat2,netWeight:'10',quantity:0}])];
 for(const d of documents){const r=await preview(b2,d.type,d.lines[0],d.warehouseId);check(r.code===200,'明确批次的'+d.type+'导入预览支持并行批次和多仓库：'+r.msg)}
 check((await preview(b1,'RAW_RETURN',documents[0].lines[0])).code!==200,'导入指定已结束的其他批次被拒绝');
 const legacyReturn={requestId:randomUUID(),factoryId:f.factoryA,warehouseId:f.warehouse,type:'RAW_RETURN',orderDate:'2026-10-23',operatorIds:[f.ownerA],keeperId:f.ownerA,batchId:b2,batchVersion:(await context(b2)).version,lines:documents[0].lines};
 check((await api('/metal/warehouse-order',f.admin,'POST',legacyReturn)).code!==200,'旧还库入口不能绕过统一确认');
 const before=sql(`SELECT CONCAT((SELECT COUNT(*) FROM metal_warehouse_order WHERE batch_id=${b2}),':',(SELECT net_weight FROM metal_inventory WHERE inventory_id=${semi}))`);
 const bad=structuredClone(documents);bad.at(-1).lines[0].netWeight='10000';
 check((await confirm(b2,'RETURNED',bad)).code!==200,'混合还料最后一类超额整体失败');
 check(before===sql(`SELECT CONCAT((SELECT COUNT(*) FROM metal_warehouse_order WHERE batch_id=${b2}),':',(SELECT net_weight FROM metal_inventory WHERE inventory_id=${semi}))`),'失败无部分单据或库存恢复');
 check(sql('SELECT COUNT(*) FROM metal_batch_return_confirmation WHERE batch_id='+b2)==='0','失败没有残留确认');
 const duplicates=structuredClone(documents);duplicates[0].lines.push({sourceLineId:rawLine.id,netWeight:'4',quantity:0});check((await confirm(b2,'RETURNED',duplicates)).code!==200,'重复来源行累计校验，不能超退同一来源');
 const mixed=ok(await confirm(b2,'RETURNED',documents)).data;
 check(mixed.status==='SETTLED'&&mixed.documents.length===4,'三类多来源还料一次提交，生成四张正确分组的关联单据');
 check(Number(mixed.reports[0].lossWeight)===5&&Number(mixed.completedQuantity)===6,'混合还料后完成6件损耗5g');
 const child=await detail('/metal/warehouse-order',mixed.documents.find(d=>d.type==='PROCESS_RAW_RETURN').id,f.admin);check((await api('/metal/warehouse-order/'+child.id+'/reverse',f.admin,'POST',{version:child.version,reason:'不得拆分确认组'})).code!==200,'已结算确认组不能单独冲销加工余料子单');
 check(sql('SELECT CONCAT(quantity,\':\',net_weight) FROM metal_inventory WHERE inventory_id='+semi)==='4:396.000000','未加工半成品恢复原库存');
 const b3=reserved;check(!!b3,'同一工序多个批次独立确认，剩余4件保持预占');
 await f.post('PRODUCTION_OUT',[{inventoryId:semi,netWeight:'396',quantity:4}],{batchId:b3});
 await f.post('FINISHED_IN',[{category:'FINISHED',referenceId:f.productA,netWeight:'390',quantity:4}],{batchId:b3});
 const v=(await context(b3)).version,responses=await Promise.all([confirm(b3,'NONE',[],{version:v}),confirm(b3,'NONE',[],{version:v})]);
 check(responses.filter(r=>r.code===200).length===1,'并发确认仅一次成功');
 check(sql('SELECT COUNT(*) FROM metal_work_report WHERE batch_id='+b3)==='1','并发无重复报工');
 check((await detail('/metal/work-order',f.wo,f.admin)).status==='COMPLETED','累计足额后正常完成整单');
 check((await api('/metal/process-batch',f.admin,'POST',{requestId:randomUUID(),workOrderId:f.wo,itemId:f.item.id,processId:f.processB,plannedQuantity:1})).code!==200,'足额后不能超额新建批次');
 console.log(JSON.stringify({passed:checks,failed:0,fixture:f.prefix}));
}finally{await f.cleanup();console.log('CLEANED isolated batch fixture')}
