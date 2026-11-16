import test from 'node:test'
import assert from 'node:assert/strict'
import {randomInt,randomBytes} from 'node:crypto'
import {api,login,loginResponse,ok,detail} from '../../tools/local-test-client.mjs'

test('角色权限与工作人员真实服务验证',async t=>{
  const admin=await login(),sessions=new Set([admin]),suffix=Date.now().toString(),records=[]
  const password='Test'+randomBytes(10).toString('hex')
  const create=async(path,body)=>{const r=ok(await api(path,admin,'POST',body)),id=String(r.id??r.factoryId);records.push({path,id});return id}
  const denied=async(path,token,method='GET',body,expected=403)=>{const r=await api(path,token,method,body);assert.equal(r.code,expected,`${method} ${path}: ${r.msg}`);return r}
  const invalid=async(path,method,body)=>{const r=await api(path,admin,method,body);if(method==='POST'&&r.code===200&&r.id!=null)records.push({path,id:String(r.id)});assert.notEqual(r.code,200,`${method} ${path} unexpectedly succeeded`);return r}
  const session=async(phone,pw=password)=>{const token=await login(phone,pw);sessions.add(token);return token}
  const menus=ok(await api('/metal/options/menus',admin)).data
  const menuIds=permissions=>{
    const ids=new Set()
    for(const permission of permissions){const m=menus.find(m=>m.perms===permission);assert.ok(m,`Missing menu: ${permission}`);ids.add(m.id)}
    for(const id of [...ids]){let parent=menus.find(m=>m.id===id)?.parentId;while(parent&&parent!=='0'){ids.add(parent);parent=menus.find(m=>m.id===parent)?.parentId}}
    return [...ids]
  }
  const managerPermissions=[...['user','role'].flatMap(k=>['list','query','add','edit','remove'].map(a=>`system:${k}:${a}`)),'system:user:resetPwd','metal:material:list','metal:material:query']
  const readerPermissions=['system:user:list','system:user:query','system:role:list','system:role:query','metal:material:list','metal:material:query']
  const roleBody=(name,factoryId,permissions)=>({roleName:`权限测试_${name}_${suffix}`,factoryId,roleSort:1,status:'0',menuIds:menuIds(permissions)})
  const phone=()=>String(randomInt(13000000000,13999999999))
  let userA,userB,reader,roleA,roleB,roleReader,roleHigher,factoryA,factoryB,tokenA,tokenB,tokenReader
  const phoneA=phone(),phoneB=phone(),phoneReader=phone()
  try {
    factoryA=await create('/metal/factory',{factoryName:`权限厂A_${suffix}`,area:'1',address:'临时权限测试地址',productionLineIds:[]})
    factoryB=await create('/metal/factory',{factoryName:`权限厂B_${suffix}`,area:'1',address:'临时权限测试地址',productionLineIds:[]})
    roleA=await create('/system/role',roleBody('管理员A',factoryA,managerPermissions))
    roleB=await create('/system/role',roleBody('管理员B',factoryB,managerPermissions))
    roleReader=await create('/system/role',roleBody('只读A',factoryA,readerPermissions))
    roleHigher=await create('/system/role',roleBody('高级A',factoryA,['metal:material:list','metal:material:add','metal:material:edit']))
    const staffBody=(name,phonenumber,factoryId,roleIds)=>({nickName:`权限人员_${name}_${suffix}`,phonenumber,factoryId,roleIds,status:'0',password})
    userA=await create('/system/user',staffBody('管理员A',phoneA,factoryA,[roleA]))
    userB=await create('/system/user',staffBody('管理员B',phoneB,factoryB,[roleB]))
    reader=await create('/system/user',staffBody('只读A',phoneReader,factoryA,[roleReader]))
    tokenA=await session(phoneA);tokenB=await session(phoneB);tokenReader=await session(phoneReader)

    await t.test('角色和工作人员 CRUD、回显、重复数据与版本冲突',async()=>{
      const role=await detail('/system/role',roleReader,admin);assert.deepEqual(new Set(role.menuIds),new Set(menuIds(readerPermissions)))
      ok(await api('/system/role',admin,'PUT',{...role,roleName:role.roleName+'_修改'}))
      await invalid('/system/role','PUT',role)
      assert.equal((await detail('/system/role',roleReader,admin)).roleName,role.roleName+'_修改')
      await invalid('/system/role','POST',{...await detail('/system/role',roleReader,admin),id:undefined})
      const person=await detail('/system/user',reader,admin)
      assert.equal(person.password,undefined);assert.equal(person.userName,phoneReader);assert.deepEqual(person.roleIds,[roleReader])
      ok(await api('/system/user',admin,'PUT',{...person,nickName:person.nickName+'_修改'}))
      await invalid('/system/user','PUT',person)
      assert.equal((await detail('/system/user',reader,admin)).nickName,person.nickName+'_修改')
      await invalid('/system/user','POST',staffBody('重复号码',phoneReader,factoryA,[roleReader]))
      assert.ok(ok(await api('/system/user/list?pageSize=100',admin)).rows.some(r=>r.id===reader))
      tokenReader=await session(phoneReader)
    })
    await t.test('只读角色可查询，直接调用写接口全部被拒绝',async()=>{
      for(const path of ['/system/user/list','/system/role/list','/metal/material/list'])ok(await api(path,tokenReader))
      const r=await detail('/system/role',roleReader,admin),u=await detail('/system/user',reader,admin)
      for(const [path,method,body]of [
        ['/system/role','POST',roleBody('只读越权',factoryA,readerPermissions)],['/system/role','PUT',r],[`/system/role/${roleReader}`,'DELETE'],
        ['/system/user','POST',staffBody('只读越权',phone(),factoryA,[roleReader])],['/system/user','PUT',u],[`/system/user/${reader}`,'DELETE'],
        ['/system/user/changeStatus','PUT',{id:reader,version:u.version,status:'1'}],['/system/user/resetPwd','PUT',{id:reader,version:u.version,password:'NotSaved123'}]
      ])await denied(path,tokenReader,method,body)
      const info=ok(await api('/getInfo',tokenReader));assert.ok(info.permissions.includes('system:user:list'));assert.ok(!info.permissions.includes('system:user:add'))
    })
    await t.test('不同工厂列表、详情、选项和批量操作隔离',async()=>{
      assert.deepEqual(new Set(ok(await api('/system/user/list',tokenA)).rows.map(r=>r.id)),new Set([userA,reader]))
      assert.deepEqual(new Set(ok(await api('/system/role/list',tokenA)).rows.map(r=>r.id)),new Set([roleA,roleReader,roleHigher]))
      assert.deepEqual(ok(await api('/system/user/list',tokenB)).rows.map(r=>r.id),[userB])
      assert.deepEqual(ok(await api('/metal/options/factories',tokenA)).data.map(r=>r.id),[factoryA])
      for(const [path,id]of [['/system/user',userB],['/system/role',roleB]]){
        const before=await detail(path,id,admin);await denied(`${path}/${id}`,tokenA);await denied(path,tokenA,'PUT',before);await denied(`${path}/${id}`,tokenA,'DELETE')
        assert.equal((await detail(path,id,admin)).version,before.version)
      }
      await denied(`/metal/options/roles?factoryId=${factoryB}`,tokenA)
      await denied(`/system/user/${reader},${userB}`,tokenA,'DELETE');await detail('/system/user',reader,admin)
      for(const path of ['/legacy/system/user/list','/legacy/system/role/list','/system/menu/list'])await denied(path,tokenA)
    })
    await t.test('不能授予自身没有的权限，不能分配跨厂或超级管理员角色',async()=>{
      const higherMenu=menus.find(m=>m.perms==='metal:material:edit').id
      await denied('/system/role',tokenA,'POST',{...roleBody('越权授权',factoryA,[]),menuIds:[higherMenu]})
      const r=await detail('/system/role',roleReader,admin)
      await denied('/system/role',tokenA,'PUT',{...r,menuIds:[...r.menuIds,higherMenu]})
      const u=await detail('/system/user',reader,admin)
      for(const roles of [[roleHigher],[roleB],['1']])await denied('/system/user',tokenA,'PUT',{...u,roleIds:roles})
      await denied('/system/user',admin,'PUT',{...u,roleIds:['1']})
      const available=ok(await api('/metal/options/menus',tokenA)).data
      assert.ok(!available.some(m=>m.perms==='metal:material:edit'))
      await invalid('/system/role','PUT',{...r,menuIds:[r.menuIds[0],r.menuIds[0]]})
      await invalid('/system/role','PUT',{...r,menuIds:['9223372036854775807']})
      assert.deepEqual((await detail('/system/role',roleReader,admin)).menuIds,r.menuIds)
    })
    await t.test('工作人员创建和编辑参数校验、角色保护、密码不回传',async()=>{
      for(const overrides of [{phonenumber:'123'},{password:'short'},{password:'密'.repeat(25)},{roleIds:[]},{roleIds:[roleB]},{roleIds:[roleReader,roleReader]}])
        await invalid('/system/user','POST',{...staffBody('非法参数',phone(),factoryA,[roleReader]),...overrides})
      const u=await detail('/system/user',reader,admin)
      await invalid('/system/user','PUT',{...u,password:'CannotEdit123'})
      await denied('/system/user',tokenA,'PUT',{...u,factoryId:factoryB,roleIds:[roleB]})
      await invalid('/system/role','PUT',{...await detail('/system/role',roleReader,admin),factoryId:factoryB})
      await invalid(`/system/role/${roleReader}`,'DELETE')
      assert.equal((await detail('/system/user',reader,admin)).password,undefined)
    })
    await t.test('超级管理员和当前账号禁止停用或删除',async()=>{
      const root=await detail('/system/user','1',admin),own=await detail('/system/user',userA,admin)
      await invalid('/system/user/changeStatus','PUT',{id:'1',version:root.version,status:'1'})
      await invalid('/system/user/1','DELETE');await invalid('/system/role/1','DELETE')
      await invalid('/system/role','PUT',{...await detail('/system/role','1',admin),status:'1'})
      assert.notEqual((await api('/system/user/changeStatus',tokenA,'PUT',{id:userA,version:own.version,status:'1'})).code,200)
      assert.notEqual((await api(`/system/user/${userA}`,tokenA,'DELETE')).code,200)
      ok(await api('/getInfo',admin));ok(await api('/getInfo',tokenA))
    })
    await t.test('多角色权限合并，角色变更使旧登录失效',async()=>{
      const u=await detail('/system/user',reader,admin)
      ok(await api('/system/user',admin,'PUT',{...u,roleIds:[roleReader,roleHigher]}))
      await denied('/getInfo',tokenReader,'GET',undefined,401)
      tokenReader=await session(phoneReader);const info=ok(await api('/getInfo',tokenReader))
      assert.ok(info.permissions.includes('system:user:list'));assert.ok(info.permissions.includes('metal:material:edit'))
      const current=await detail('/system/user',reader,admin)
      ok(await api('/system/user',admin,'PUT',{...current,roleIds:[roleReader]}))
      await denied('/getInfo',tokenReader,'GET',undefined,401);tokenReader=await session(phoneReader)
      assert.ok(!ok(await api('/getInfo',tokenReader)).permissions.includes('metal:material:edit'))
    })
    await t.test('角色权限撤销实时注销旧登录，新登录权限减少',async()=>{
      const r=await detail('/system/role',roleA,admin),editId=menus.find(m=>m.perms==='system:user:edit').id
      ok(await api('/system/role',admin,'PUT',{...r,menuIds:r.menuIds.filter(id=>id!==editId)}))
      await denied('/getInfo',tokenA,'GET',undefined,401);tokenA=await session(phoneA)
      const info=ok(await api('/getInfo',tokenA));assert.ok(!info.permissions.includes('system:user:edit'))
      await denied('/system/user',tokenA,'PUT',await detail('/system/user',reader,admin))
    })
    await t.test('密码重置：旧密码和旧登录失效，新密码可登录，旧版本被拒绝',async()=>{
      const u=await detail('/system/user',reader,admin),newPassword='Reset'+randomBytes(10).toString('hex')
      await invalid('/system/user/resetPwd','PUT',{id:reader,version:u.version,password:'short'})
      ok(await api('/system/user/resetPwd',admin,'PUT',{id:reader,version:u.version,password:newPassword}))
      await denied('/getInfo',tokenReader,'GET',undefined,401)
      assert.notEqual((await loginResponse(phoneReader,password)).code,200)
      tokenReader=await session(phoneReader,newPassword)
      await invalid('/system/user/resetPwd','PUT',{id:reader,version:u.version,password:'StalePassword123'})
      ok(await api('/getInfo',tokenReader))
      // Keep the new password only in memory for later account-state checks.
      passwordByUser.set(reader,newPassword)
    })
    await t.test('工作人员停用拒绝登录，重新启用后恢复',async()=>{
      const u=await detail('/system/user',reader,admin),pw=passwordByUser.get(reader)
      ok(await api('/system/user/changeStatus',admin,'PUT',{id:reader,version:u.version,status:'1'}))
      await denied('/getInfo',tokenReader,'GET',undefined,401);assert.notEqual((await loginResponse(phoneReader,pw)).code,200)
      const disabled=await detail('/system/user',reader,admin)
      ok(await api('/system/user/changeStatus',admin,'PUT',{id:reader,version:disabled.version,status:'0'}));tokenReader=await session(phoneReader,pw)
    })
    await t.test('角色停用移除权限并使旧登录失效，启用后恢复',async()=>{
      const r=await detail('/system/role',roleReader,admin)
      ok(await api('/system/role/changeStatus',admin,'PUT',{id:roleReader,version:r.version,status:'1'}))
      await denied('/getInfo',tokenReader,'GET',undefined,401);tokenReader=await session(phoneReader,passwordByUser.get(reader))
      assert.ok(!ok(await api('/getInfo',tokenReader)).permissions.includes('system:user:list'));await denied('/system/user/list',tokenReader)
      const disabled=await detail('/system/role',roleReader,admin)
      ok(await api('/system/role/changeStatus',admin,'PUT',{id:roleReader,version:disabled.version,status:'0'}))
      tokenReader=await session(phoneReader,passwordByUser.get(reader));ok(await api('/system/user/list',tokenReader))
    })
    await t.test('删除工作人员后旧登录及再次登录被拒绝，解除引用后角色可删除',async()=>{
      ok(await api(`/system/user/${reader}`,admin,'DELETE'));await denied('/getInfo',tokenReader,'GET',undefined,401)
      assert.notEqual((await loginResponse(phoneReader,passwordByUser.get(reader))).code,200)
      ok(await api(`/system/role/${roleReader}`,admin,'DELETE'))
      assert.notEqual((await api(`/system/role/${roleReader}`,admin)).code,200)
    })
  } finally {
    const errors=[]
    for(const path of ['/system/user','/system/role','/metal/factory'])for(const r of records.filter(r=>r.path===path)){
      try{const d=await api(`${path}/${r.id}`,admin);if(d.code===200)ok(await api(`${path}/${r.id}`,admin,'DELETE'));else assert.equal(d.msg,'记录不存在或已删除')}catch(e){errors.push(`${path}/${r.id}: ${e.message}`)}
    }
    for(const token of sessions)await api('/logout',token,'POST')
    assert.deepEqual(errors,[],'临时角色、工作人员、工厂清理失败')
  }
})
const passwordByUser=new Map()
