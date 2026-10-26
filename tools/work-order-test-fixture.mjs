import{assertEnvironment,assertFixtureOwnership,fixtureEnvironment,discoverFixtureResources}from'./test-fixture-scope.mjs'
import {trackFixture,fixtureCleaned} from './test-fixture-registry.mjs'
import {randomUUID,randomBytes} from 'node:crypto'
import {api,login,ok,detail,sql} from './local-test-client.mjs'
export async function workOrderFixture(){
 const admin=await login(),suffix=Date.now()+'_'+randomBytes(3).toString('hex'),prefix='工单验收_'+suffix,orders=new Set(),made={factories:[],lines:[],products:[],processes:[],roles:[],users:[],materials:[]}
 const environment=fixtureEnvironment();trackFixture(prefix,made)
 const create=async(path,body,group)=>{const r=ok(await api(path,admin,'POST',body));const id=String(r.id??r.factoryId);made[group].push(id);return id}
 const menus=ok(await api('/metal/options/menus',admin)).data
 const wanted=['metal:work-order:list','metal:work-order:query','metal:work-order:add','metal:work-order:edit','metal:work-order:remove','metal:work-order:approve','metal:work-order:reject','metal:work-order:resubmit','metal:factory:list','metal:factory:query','metal:factory:edit','metal:production-line:list','metal:production-line:query','metal:production-line:edit','metal:product:list','metal:product:query']
 const menuIds=new Set(menus.filter(m=>wanted.includes(m.perms)).map(m=>m.id));for(const key of [...menuIds]){let parent=menus.find(m=>m.id===key)?.parentId;while(parent&&parent!=='0'){menuIds.add(parent);parent=menus.find(m=>m.id===parent)?.parentId}}
 const factoryA=await create('/metal/factory',{factoryName:prefix+'厂A',area:'1',address:'工单本地测试'},'factories'),factoryB=await create('/metal/factory',{factoryName:prefix+'厂B',area:'1',address:'工单本地测试'},'factories')
 const processA=await create('/metal/process',{name:prefix+'粉料',processType:1,description:'工单测试'},'processes'),processB=await create('/metal/process',{name:prefix+'焊接',processType:8},'processes')
 const productA=await create('/metal/product',{code:'WO_PA_'+suffix,name:prefix+'产品A',category:'铱杆实心',dimensions:{diameter:'10',length:'20'},weight:'1.2',processes:[{processId:processA,loss:'0.1'},{processId:processB,loss:'0.2'}]},'products')
 const productB=await create('/metal/product',{code:'WO_PB_'+suffix,name:prefix+'产品B',category:'铱环',dimensions:{outerDiameter:'12',innerDiameter:'10',height:'2'},weight:'0.8',processes:[{processId:processB,loss:'0.3'}]},'products')
 const noProcess=await create('/metal/product',{code:'WO_PC_'+suffix,name:prefix+'无工序产品',category:'铱杆实心',dimensions:{diameter:'10',length:'20'},processes:[]},'products')
 const lineA=await create('/metal/production-line',{code:'WO_LA_'+suffix,name:prefix+'产线A',factoryId:factoryA,productIds:[productA,productB,noProcess]},'lines'),lineB=await create('/metal/production-line',{code:'WO_LB_'+suffix,name:prefix+'产线B',factoryId:factoryB,productIds:[productA,productB]},'lines')
 const roleA=await create('/system/role',{roleName:prefix+'角色A',factoryId:factoryA,roleSort:1,status:'0',menuIds:[...menuIds]},'roles'),roleB=await create('/system/role',{roleName:prefix+'角色B',factoryId:factoryB,roleSort:1,status:'0',menuIds:[...menuIds]},'roles')
 const phoneA='134'+String(Date.now()).slice(-8),phoneB='133'+String(Date.now()).slice(-8),password='WO'+randomBytes(10).toString('hex')
 const ownerA=await create('/system/user',{phonenumber:phoneA,nickName:prefix+'负责人A',factoryId:factoryA,roleIds:[roleA],status:'0',password},'users'),ownerB=await create('/system/user',{phonenumber:phoneB,nickName:prefix+'负责人B',factoryId:factoryB,roleIds:[roleB],status:'0',password},'users')
 const plansA=()=>[{processId:processA,startTime:'2026-10-14 09:00:00',endTime:'2026-10-14 10:00:00'},{processId:processB,startTime:'2026-10-14 10:00:00',endTime:'2026-10-14 11:00:00'}]
 const body=(b=false)=>({requestId:randomUUID(),customer:prefix,factoryId:b?factoryB:factoryA,productionLineId:b?lineB:lineA,ownerId:b?ownerB:ownerA,orderDate:'2026-10-13',items:[{productId:productA,quantity:5,plans:plansA()}]})
 const createOrder=async(value=body(),token=admin)=>{const r=ok(await api('/metal/work-order',token,'POST',value));orders.add(String(r.id));return String(r.id)}
 const cleanup=()=>cleanupWorkOrderFixture({prefix,made,environment},admin)
 return{admin,prefix,environment,suffix,made,orders,factoryA,factoryB,lineA,lineB,productA,productB,noProcess,processA,processB,ownerA,ownerB,roleA,roleB,phoneA,phoneB,password,body,plansA,createOrder,cleanup}
}

export async function cleanupWorkOrderFixture({prefix,made,environment},admin){
  assertEnvironment(environment,sql);
  assertFixtureOwnership(prefix,made,sql);discoverFixtureResources(prefix,made,sql);assertFixtureOwnership(prefix,made,sql);
  if(!/^工单验收_\d+_[0-9a-f]{6}$/.test(prefix)||Object.values(made).flat().some(id=>!/^\d+$/.test(id)))throw Error("invalid fixture scope");
  // Disposable local fixtures only: verify customer and actor-created IDs before purging locked test orders.
  const found=sql("SELECT work_order_id FROM metal_work_order WHERE LEFT(customer,CHAR_LENGTH('"+prefix+"'))='"+prefix+"'"),keys=found?found.split(/\r?\n/):[]
  if(keys.some(id=>!/^\d+$/.test(id)))throw Error('invalid fixture IDs')
  if(keys.length){const list=keys.join(',');sql('DELETE s FROM metal_work_order_step s JOIN metal_work_order_item i ON i.item_id=s.item_id WHERE i.work_order_id IN ('+list+')');sql('DELETE FROM metal_work_order_review WHERE work_order_id IN ('+list+')');sql('DELETE FROM metal_work_order_item WHERE work_order_id IN ('+list+')');sql('DELETE FROM metal_work_order WHERE work_order_id IN ('+list+')')}
  const existing=async(path,id)=>{const r=await api(path+'/'+id,admin);if(r.code===200)return r.data;if(r.code===404||/不存在|已删除|已归档/.test(r.msg||''))return null;ok(r)};const clean=async(path,ids)=>{for(const id of ids)if(await existing(path,id))ok(await api(path+'/'+id,admin,'DELETE'))}
  await clean('/system/user',made.users);await clean('/system/role',made.roles)
  for(const id of made.lines){const row=await existing('/metal/production-line',id);if(row)ok(await api('/metal/production-line',admin,'PUT',{...row,productIds:[]}))}
  for(const id of made.factories){const row=await existing('/metal/factory',id);if(row)ok(await api('/metal/factory',admin,'PUT',{...row,productionLineIds:[]}))}
  await clean('/metal/production-line',made.lines);await clean('/metal/product',made.products);await clean('/metal/material',made.materials||[]);await clean('/metal/process',made.processes);await clean('/metal/factory',made.factories);fixtureCleaned(prefix);await api('/logout',admin,'POST')
 }
