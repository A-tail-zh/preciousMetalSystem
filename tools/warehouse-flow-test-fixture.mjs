import {randomUUID} from 'node:crypto';
import {warehouseFixture} from './warehouse-test-fixture.mjs';
import {api,ok,detail,sql,login} from './local-test-client.mjs';
import {assertEnvironment,assertFixtureOwnership} from './test-fixture-scope.mjs';

export async function warehouseFlowFixture(){
 const f=await warehouseFixture({completionPolicy:'DUAL_WEIGH_HANDOVER'});
 try{
  assertEnvironment(f.environment,sql);assertFixtureOwnership(f.prefix,{roles:[f.roleA]},sql);
  const permissions=['metal:warehouse-order:list','metal:warehouse-order:query','metal:warehouse-order:add','metal:warehouse-order:receive','metal:warehouse-order:accept','metal:warehouse-order:handover','metal:process-batch:settle','metal:process-batch:query','metal:inventory:list','metal:inventory:query'];
  sql(`INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT ${f.roleA},menu_id FROM sys_menu WHERE perms IN (${permissions.map(p=>"'"+p+"'").join(',')})`);
  const phone='135'+String(Date.now()).slice(-8);
  const processorId=String(ok(await api('/system/user',f.admin,'POST',{phonenumber:phone,nickName:f.prefix+'加工人',factoryId:f.factoryA,roleIds:[f.roleA],status:'0',password:f.password})).id);f.made.users.push(processorId);
  const keeper=await login(f.phoneA,f.password),processor=await login(phone,f.password);
  const body=f.body();body.items=[{productId:f.productB,quantity:10,plans:[{processId:f.processB,startTime:'2026-11-03 09:00:00',endTime:'2026-11-03 10:00:00'}]}];
  const workOrderId=await f.createOrder(body),work=await detail('/metal/work-order',workOrderId,f.admin),item=work.items[0];
  const batchId=String(ok(await api('/metal/process-batch',f.admin,'POST',{requestId:randomUUID(),workOrderId,itemId:item.id,processId:f.processB,plannedQuantity:10})).id);
  const submit=async(type,lines,extra={},token=keeper)=>ok(await api('/metal/warehouse-order',token,'POST',{requestId:randomUUID(),factoryId:f.factoryA,warehouseId:f.warehouse,type,orderDate:'2026-11-03',operatorIds:[f.ownerA],keeperId:f.ownerA,recipientId:processorId,lines,...extra}));
  const flow=id=>detail('/metal/warehouse-flows',id,f.admin);
  const confirm=async(root,group,token,weights)=>api(`/metal/warehouse-flows/${root.id}/groups/${group.id}/confirm`,token,'POST',{requestId:randomUUID(),version:root.version,lines:group.lines.map((line,i)=>({lineId:line.id,quantity:line.line.quantity??0,netWeight:weights?.[i]??line.plannedWeight,...(weights?.[i]!==undefined&&Number(weights[i])!==Number(line.plannedWeight)?{differenceReason:'隔离测试逐条复称差异'}:{})}))});
  const incoming=async(owner,weight)=>{
   const submitted=await submit('RAW_IN',[{category:'RAW',referenceId:f.mat1,netWeight:weight,quantity:0,powderOrigin:'NATIVE',materialOwnership:owner,...(owner==='CUSTOMER'?{customerId:f.customerA,customerVersion:0}:{})}]);
   let root=await flow(submitted.flowId);root=ok(await confirm(root,root.groups[0],keeper)).data;
   const document=await detail('/metal/warehouse-order',submitted.id,f.admin);return document.lines[0].inventoryId;
  };
  return {...f,keeper,processor,processorId,flow,confirm,submit,incoming,flowWorkOrderId:workOrderId,flowWork:work,flowItem:item,flowBatchId:batchId};
 }catch(error){await f.cleanup();throw error}
}
