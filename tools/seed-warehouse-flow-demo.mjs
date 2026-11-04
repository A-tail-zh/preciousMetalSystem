import fs from 'node:fs';
import assert from 'node:assert/strict';
import {randomUUID,randomBytes} from 'node:crypto';
import {api,login,ok,detail,sql} from './local-test-client.mjs';

// Persistent user-requested demo data; never use the disposable fixture cleanup here.
assert(!process.env.TEST_API_BASE_URL&&!process.env.TEST_MYSQL_PORT&&!process.env.TEST_DB_SCHEMA,'演示数据仅针对当前本地环境');
assert.equal(sql('SELECT @@port'),'3308');
const file='outputs/warehouse-flow-demo-20261103.json',privateFile='.runtime/warehouse-flow-demo-accounts.json';
fs.mkdirSync('outputs',{recursive:true});fs.mkdirSync('.runtime',{recursive:true});
const state=fs.existsSync(file)?JSON.parse(fs.readFileSync(file,'utf8')):{prefix:'演示测试1103',steps:{},createdAt:new Date().toISOString()};
const record=()=>fs.writeFileSync(file,JSON.stringify(state,null,2));
const once=async(key,fn)=>{if(state.steps[key]===undefined){state.steps[key]=await fn();record();console.log('已生成 '+key);}return state.steps[key];};
if(state.completed){console.log('已有演示数据，未重复生成：'+file);process.exit(0);}
const accounts=fs.existsSync(privateFile)?JSON.parse(fs.readFileSync(privateFile,'utf8')):{password:'Demo!'+randomBytes(8).toString('hex'),keeperPhone:'136'+String(Date.now()).slice(-8),processorPhone:'137'+String(Date.now()).slice(-8)};
fs.writeFileSync(privateFile,JSON.stringify(accounts,null,2));
const admin=await login(),sessions=[admin],day='2026-11-03';
const create=(key,path,body)=>once(key,async()=>{const response=ok(await api(path,admin,'POST',{requestId:randomUUID(),...body}));const id=String(response.id??response.factoryId);assert.match(id,/^\d+$/,'新增响应必须包含记录ID');return id;});
try{
 const factory=await create('factory','/metal/factory',{factoryName:'演示测试1103—贵金属加工厂',area:'1',address:'本地演示数据，非真实业务'});
 const menus=ok(await api('/metal/options/menus',admin)).data;
 const menuIds=new Set(menus.filter(m=>m.perms?.startsWith('metal:')).map(m=>m.id));
 for(const id of [...menuIds]){let parent=menus.find(m=>m.id===id)?.parentId;while(parent&&parent!=='0'){menuIds.add(parent);parent=menus.find(m=>m.id===parent)?.parentId;}}
 const role=await create('role','/system/role',{roleName:'演示测试1103—仓储业务角色',factoryId:factory,roleSort:99,status:'0',menuIds:[...menuIds]});
 const keeperId=await create('keeper','/system/user',{phonenumber:accounts.keeperPhone,nickName:'演示测试—仓管员',factoryId:factory,roleIds:[role],status:'0',password:accounts.password});
 const processorId=await create('processor','/system/user',{phonenumber:accounts.processorPhone,nickName:'演示测试—加工人员',factoryId:factory,roleIds:[role],status:'0',password:accounts.password});
 const keeper=await login(accounts.keeperPhone,accounts.password),processor=await login(accounts.processorPhone,accounts.password);sessions.push(keeper,processor);
 const customerA=await create('customerA','/metal/customer',{factoryId:factory,name:'演示测试—华鑫客户',companyName:'演示测试—华鑫精密有限公司',address:'演示地址A',contactName:'演示联系人A',contactPhone:'13800000001'});
 const customerB=await create('customerB','/metal/customer',{factoryId:factory,name:'演示测试—宏远客户',companyName:'演示测试—宏远材料有限公司',address:'演示地址B',contactName:'演示联系人B',contactPhone:'13800000002'});
 const warehouse=await create('warehouse','/metal/warehouse',{factoryId:factory,name:'演示测试—原料及产出仓',sortOrder:0,status:'0'});
 const warehouse2=await create('warehouse2','/metal/warehouse',{factoryId:factory,name:'演示测试—原料二仓',sortOrder:1,status:'0'});
 const powder=await create('powder','/metal/material',{code:'DEMO1103-IR-POWDER',materialType:'粉料',metalTypeId:'1',specification:'铱粉 99.95%，演示测试'});
 const old=await create('old','/metal/material',{code:'DEMO1103-IR-OLD',materialType:'旧料',metalTypeId:'1',specification:'旧铱杆回收料，演示测试'});
 const block=await create('block','/metal/material',{code:'DEMO1103-IR-BLOCK',materialType:'块料',metalTypeId:'1',specification:'铱块 5–20mm，演示测试'});
 const metal=await create('metal','/metal/material',{code:'DEMO1103-IR-METAL',materialType:'块料',metalTypeId:'1',specification:'酸洗后金属料，演示测试'});
 const process=await create('process','/metal/process',{name:'演示测试—精加工',processType:8,description:'演示交接称重'});
 const process2=await create('process2','/metal/process',{name:'演示测试—终检',processType:3});
 const acidType=sql("SELECT type_id FROM metal_process_type WHERE type_name='酸洗'");assert(acidType);
 const acid=await create('acidProcess','/metal/process',{name:'演示测试—酸洗',processType:Number(acidType)});
 const product=await create('product','/metal/product',{code:'DEMO1103-IR-RING',name:'演示测试—铱环成品',metalTypeId:'1',productType:'环',dimensions:{outerDiameter:'12',innerDiameter:'10',thickness:'2'},weight:'9.6',processes:[{processId:process,loss:'2'}]});
 const semiProduct=await create('semiProduct','/metal/product',{code:'DEMO1103-IR-ROD',name:'演示测试—铱杆半成品',metalTypeId:'1',productType:'杆',dimensions:{outerDiameter:'10',innerDiameter:'0',length:'20'},weight:'9.6',processes:[{processId:process,loss:'2'},{processId:process2,loss:'1'}]});
 const line=await create('line','/metal/production-line',{code:'DEMO1103-LINE',name:'演示测试—加工产线',factoryId:factory,productIds:[product,semiProduct]});
 const flow=id=>detail('/metal/warehouse-flows',id,admin);
 const confirm=async(root,token,adjustFinished=false)=>{for(const g of root.groups.filter(g=>g.status==='WAITING'))root=ok(await api(`/metal/warehouse-flows/${root.id}/groups/${g.id}/confirm`,token,'POST',{requestId:randomUUID(),version:root.version,lines:g.lines.map(l=>({lineId:l.id,quantity:l.line.quantity??0,netWeight:adjustFinished&&g.type==='FINISHED_IN'?'95.8':l.plannedWeight,...(adjustFinished&&g.type==='FINISHED_IN'?{differenceReason:'演示测试：两人复称短少0.20g'}:{})}))})).data;return root;};
 const post=async(key,type,lines,extra={},accept=true)=>once(key,async()=>{const response=ok(await api('/metal/warehouse-order',keeper,'POST',{requestId:randomUUID(),factoryId:factory,warehouseId:warehouse,type,orderDate:day,operatorIds:[keeperId],keeperId,recipientId:processorId,reason:'演示测试数据，非真实业务',lines,...extra}));if(accept&&response.flowId)await confirm(await flow(response.flowId),type.endsWith('_OUT')?processor:keeper);return {id:String(response.id),flowId:response.flowId?String(response.flowId):null};});
 const incoming=async(key,referenceId,weight,customerId=customerA,wh=warehouse)=>{const order=await post(key,'RAW_IN',[{category:'RAW',referenceId,quantity:0,netWeight:weight,materialOwnership:customerId?'CUSTOMER':'FACTORY',...(customerId?{customerId,customerVersion:0}:{}),remark:'演示测试入库'}],{warehouseId:wh});return (await detail('/metal/warehouse-order',order.id,admin)).lines[0].inventoryId;};
 const stock=await incoming('powderA',powder,'1000'),factoryStock=await incoming('powderFactory',powder,'200',null);
 await incoming('powderB',powder,'600',customerB);const oldStock=await incoming('oldStock',old,'500');await incoming('blockStock',block,'300',customerB);await incoming('secondWarehouseStock',powder,'250',customerA,warehouse2);
 await once('adjustPlus',async()=>ok(await api(`/metal/inventory/${stock}/adjust`,admin,'POST',{requestId:randomUUID(),version:(await detail('/metal/inventory',stock,admin)).version,operatorId:keeperId,keeperId,deltaWeight:'5',deltaQuantity:0,reason:'演示测试：盘点增加5.00g'})).id);
 await once('adjustMinus',async()=>ok(await api(`/metal/inventory/${stock}/adjust`,admin,'POST',{requestId:randomUUID(),version:(await detail('/metal/inventory',stock,admin)).version,operatorId:keeperId,keeperId,deltaWeight:'-2',deltaQuantity:0,reason:'演示测试：盘点减少2.00g'})).id);
 const acidId=await create('acidBatch','/metal/acid-wash',{factoryId:factory,processId:acid,processVersion:0,orderDate:day,operatorIds:[processorId],keeperId,groups:[{warehouseId:warehouse,lines:[{inventoryId:oldStock,category:'RAW',netWeight:'100',quantity:0}]}],reason:'演示测试：未处理料酸洗'});
 await once('acidFinish',async()=>{const b=await detail('/metal/acid-wash',acidId,admin);ok(await api(`/metal/acid-wash/${acidId}/finish`,admin,'POST',{requestId:randomUUID(),version:b.version,orderDate:day,operatorIds:[processorId],keeperId,physicalChecked:true,outputs:[{warehouseId:warehouse,lines:[{category:'RAW',referenceId:metal,netWeight:'95',quantity:0}]}],returns:[]}));return true;});
 const makeWork=async(key,semi=false)=>{const workId=await create(key,'/metal/work-order',{customerId:customerA,customerVersion:0,processingType:'MATERIAL',factoryId:factory,productionLineId:line,ownerId:processorId,orderDate:day,items:[{productId:semi?semiProduct:product,quantity:10,plans:(semi?[process,process2]:[process]).map((processId,i)=>({processId,startTime:`${day} ${String(9+i).padStart(2,'0')}:00:00`,endTime:`${day} ${String(10+i).padStart(2,'0')}:00:00`}))}]});const work=await detail('/metal/work-order',workId,admin);const batchId=await create(key+'Batch','/metal/process-batch',{workOrderId:workId,itemId:work.items[0].id,processId:process,plannedQuantity:10});return {workOrderId:workId,itemId:work.items[0].id,processId:process,batchId,batchVersion:(await detail('/metal/process-batch',batchId,admin)).version};};
 const completed=await makeWork('completedWork');
 const issued=await post('completedIssue','PRODUCTION_OUT',[{inventoryId:stock,netWeight:'90',quantity:0},{inventoryId:factoryStock,netWeight:'10',quantity:0}],completed);
 const sources=(await detail('/metal/warehouse-order',issued.id,admin)).lines;
 const handover=async(key,context,outputType,outputWeight,returns,accept)=>once(key,async()=>{const root=ok(await api(`/metal/warehouse-flows/handover/${context.batchId}`,processor,'POST',{requestId:randomUUID(),version:(await detail('/metal/process-batch',context.batchId,admin)).version,orderDate:day,physicalChecked:true,residualMode:'RETURNED',reason:'演示测试：加工人申报，仓管独立复称',documents:[{type:outputType,warehouseId:warehouse,keeperId,lines:[{category:outputType==='FINISHED_IN'?'FINISHED':'SEMI',referenceId:outputType==='FINISHED_IN'?product:semiProduct,netWeight:outputWeight,quantity:10}]},{type:'RAW_RETURN',warehouseId:warehouse,keeperId,lines:returns}]})).data;if(accept)await confirm(root,keeper,outputType==='FINISHED_IN');return String(root.id);});
 await handover('completedHandover',completed,'FINISHED_IN','96',sources.map(l=>({category:'RAW',sourceLineId:l.id,quantity:0,netWeight:l.inventoryId===stock?'2':'1'})),true);
 const pending=await makeWork('pendingIssueWork');await post('pendingIssue','PRODUCTION_OUT',[{inventoryId:stock,netWeight:'40',quantity:0}],pending,false);
 const waiting=await makeWork('waitingHandoverWork',true);const waitingIssued=await post('waitingHandoverIssue','PRODUCTION_OUT',[{inventoryId:stock,netWeight:'50',quantity:0}],waiting);const ws=(await detail('/metal/warehouse-order',waitingIssued.id,admin)).lines[0];await handover('waitingHandover',waiting,'SEMI_IN','48',[{category:'RAW',sourceLineId:ws.id,quantity:0,netWeight:'1.5'}],false);
 const semi=await makeWork('semiWork',true);const semiIssue=await post('semiIssue','PRODUCTION_OUT',[{inventoryId:stock,netWeight:'100',quantity:0}],semi);const ss=(await detail('/metal/warehouse-order',semiIssue.id,admin)).lines[0];await handover('semiHandover',semi,'SEMI_IN','96',[{category:'RAW',sourceLineId:ss.id,quantity:0,netWeight:'3'}],true);
 const inventories=ok(await api(`/metal/inventory/list?factoryId=${factory}&pageSize=100`,admin)).rows;
 assert(inventories.some(x=>x.materialType==='粉料'));assert(inventories.some(x=>x.materialType==='未处理料'));assert(inventories.some(x=>x.materialType==='金属料'));assert(inventories.some(x=>x.category==='SEMI'));assert(inventories.some(x=>x.category==='FINISHED'));
 assert(inventories.filter(x=>x.category==='RAW').every(x=>Number(x.quantity)===0));assert.equal((await flow(state.steps.pendingIssue.flowId)).status,'WAITING');assert.equal((await flow(state.steps.waitingHandover)).stage,'KEEPER');
 state.inventory= inventories.map(x=>({id:x.id,category:x.category,code:x.code,materialType:x.materialType,customerCompanyName:x.customerCompanyName,netWeight:x.netWeight,quantity:x.quantity,warehouseName:x.warehouseName}));state.completed=true;state.completedAt=new Date().toISOString();record();
 fs.writeFileSync('outputs/仓储演示数据说明-2026-11-03.md',`# 本地仓储演示数据\n\n所有数据标识“演示测试”，非真实业务，保留供页面查看；没有清理既有数据。\n\n- 登录后在工厂筛选选择：演示测试1103—贵金属加工厂。\n- 原材料仓库：粉料、未处理料、酸洗金属料；华鑫/宏远客户、厂料及二仓库存。华鑫粉料含+5g、-2g调整流水。\n- 半成品仓库：已验收铱杆96g、10件。成品仓库：铱环95.8g、10件。\n- 待实领：工单ID ${pending.workOrderId}，申请40g，实际加工人确认前仅占用。\n- 待库管验收：工单ID ${waiting.workOrderId}，加工产出48g及余料1.5g，待仓管确认，尚未入库。\n- 已结算示例：工单ID ${completed.workOrderId}，客户90g+厂10g，加工产出96g、余料3g；库管收95.8g，损耗1g、交接差异0.2g。\n- 酸洗：批次ID ${acidId}，未处理料100g产出金属料95g。\n\n演示仓管登录：${accounts.keeperPhone}\n\n演示加工人员登录：${accounts.processorPhone}\n\n两账号临时演示密码：${accounts.password}\n\n这些新账号只有演示工厂仓储/生产业务角色，没有系统管理权限。账号口令不要用于真实账号。\n\n逐项记录ID见 warehouse-flow-demo-20261103.json；脚本重复运行不再生成。数据不会自动删除。\n`);
 console.log('演示数据已生成并核对：'+inventories.length+'条库存；说明：outputs/仓储演示数据说明-2026-11-03.md（含新建演示账号口令，不在终端输出）');
}finally{for(const token of sessions)await api('/logout',token,'POST');}
