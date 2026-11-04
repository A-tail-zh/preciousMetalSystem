import {randomBytes} from 'node:crypto'
import {api,login,ok} from './local-test-client.mjs'
import {trackFixture} from './test-fixture-registry.mjs'
import {fixtureEnvironment} from './test-fixture-scope.mjs'
import {cleanupWorkOrderFixture} from './work-order-test-fixture.mjs'

export async function customerFixture(){
 const admin=await login(),prefix='工单验收_'+Date.now()+'_'+randomBytes(3).toString('hex')
 const made=trackFixture(prefix,{factories:[],users:[],roles:[],customers:[]}),environment=fixtureEnvironment()
 const cleanup=()=>cleanupWorkOrderFixture({prefix,made,environment},admin)
 const create=async(path,body,group)=>{const result=ok(await api(path,admin,'POST',body));const id=String(result.id??result.factoryId);made[group].push(id);return id}
 const tokens=[]
 try{
  const factoryA=await create('/metal/factory',{factoryName:prefix+'厂A',area:'1',address:'客户隔离测试'},'factories')
  const factoryB=await create('/metal/factory',{factoryName:prefix+'厂B',area:'1',address:'客户隔离测试'},'factories')
  const menus=ok(await api('/metal/options/menus',admin)).data
  const password='C'+randomBytes(12).toString('hex')
  const account=async(label,factoryId,actions,index)=>{
   const menuIds=new Set(menus.filter(menu=>actions.includes(menu.perms?.replace('metal:customer:',''))&&menu.perms?.startsWith('metal:customer:')).map(menu=>menu.id))
   for(const key of [...menuIds]){let parent=menus.find(menu=>menu.id===key)?.parentId;while(parent&&parent!=='0'){menuIds.add(parent);parent=menus.find(menu=>menu.id===parent)?.parentId}}
   if(!actions.includes('list'))for(const menu of menus)if(menu.perms==='metal:customer:list')menuIds.delete(menu.id)
   const role=await create('/system/role',{roleName:prefix+label,factoryId,roleSort:1,status:'0',menuIds:[...menuIds]},'roles')
   const phone='139'+String(Date.now()).slice(-6)+String(index).padStart(2,'0')
   await create('/system/user',{phonenumber:phone,nickName:prefix+label,factoryId,roleIds:[role],status:'0',password},'users')
   const token=await login(phone,password);tokens.push(token);return token
  }
  const full=['list','query','add','edit','remove']
  const userA=await account('本厂',factoryA,full,1),userB=await account('他厂',factoryB,full,2)
  const reader=await account('只读',factoryA,['list','query'],3),adder=await account('仅新增',factoryA,['add'],4),editor=await account('仅编辑',factoryA,['edit'],5),remover=await account('仅删除',factoryA,['remove'],6)
  const body=(extra={})=>({name:prefix+'客户',companyName:prefix+'公司',address:'客户测试地址',contactName:'联络人',contactPhone:'13800138000',department:null,...extra})
  const createCustomer=async(extra={},token=admin)=>createWithToken(body(extra),token)
  const createWithToken=async(value,token)=>{const result=ok(await api('/metal/customer',token,'POST',value));made.customers.push(String(result.id));return String(result.id)}
  return {admin,prefix,made,environment,factoryA,factoryB,userA,userB,reader,adder,editor,remover,body,createCustomer,
   cleanup:async()=>{for(const token of tokens)await api('/logout',token,'POST');await cleanup()}}
 }catch(error){for(const token of tokens)await api('/logout',token,'POST');await cleanup();throw error}
}
