import {readFileSync,writeFileSync} from 'node:fs'
import assert from 'node:assert/strict'
import {api,login,ok,detail} from './local-test-client.mjs'
const file='.runtime/persistence-fixtures.json',token=await login()
if(process.argv[2]==='prepare'){
 const suffix=Date.now(),made={}
 const create=async(key,path,body)=>{const r=ok(await api(path,token,'POST',body));made[key]=String(r.id??r.factoryId);return made[key]}
 await create('factory','/metal/factory',{factoryName:`重启厂_${suffix}`,area:'12.5000',address:'临时验收',productionLineIds:[]})
 await create('process','/metal/process',{name:`重启工序_${suffix}`,processType:8})
 await create('material','/metal/material',{name:`重启物料_${suffix}`,code:`RM${suffix}`,materialType:'铱粉'})
 await create('product','/metal/product',{name:`重启产品_${suffix}`,code:`RP${suffix}`,category:'铱环',dimensions:{outerDiameter:'12',innerDiameter:'10',height:'2'},weight:'1.123456',processes:[{processId:made.process,loss:'0.123456'}]})
 await create('line','/metal/production-line',{name:`重启线_${suffix}`,code:`RL${suffix}`,factoryId:made.factory,productIds:[made.product]})
 const menus=ok(await api('/metal/options/menus',token)).data
 await create('role','/system/role',{roleName:`重启角色_${suffix}`,factoryId:made.factory,roleSort:1,status:'0',menuIds:menus.filter(m=>['metal:product:list','metal:product:query'].includes(m.perms)).map(m=>m.id)})
 made.phone='136'+String(suffix).slice(-8);made.password=`Restart${suffix}`
 await create('staff','/system/user',{nickName:'重启人员',phonenumber:made.phone,password:made.password,factoryId:made.factory,roleIds:[made.role],status:'0'})
 writeFileSync(file,JSON.stringify(made));console.log('PASS 已创建重启验收夹具')
}else{
 const m=JSON.parse(readFileSync(file,'utf8'))
 try{
  assert.equal((await detail('/metal/factory',m.factory,token)).area,'12.5000')
  const p=await detail('/metal/product',m.product,token);assert.equal(p.weight,'1.123456');assert.deepEqual(p.dimensions,{outerDiameter:'12',innerDiameter:'10',height:'2'});assert.equal(p.processes[0].loss,'0.123456')
  assert.deepEqual((await detail('/metal/production-line',m.line,token)).productIds,[m.product])
  assert.equal((await detail('/metal/material',m.material,token)).materialType,'铱粉')
  assert.deepEqual((await detail('/system/user',m.staff,token)).roleIds,[m.role])
  const ordinary=await login(m.phone,m.password);ok(await api('/metal/product/list',ordinary));assert.notEqual((await api('/metal/product',ordinary,'POST',{})).code,200);await api('/logout',ordinary,'POST')
  writeFileSync('docs/master-restart-verification.json',JSON.stringify({timestamp:new Date().toISOString(),passed:['六项资料、尺寸精度、产线关联、产品工序及人员角色在重启后保留','普通账号重启后登录及已授予/未授予权限保持']},null,2));console.log('PASS 重启后六项资料、关联、登录与权限保持')
 }finally{
  ok(await api(`/system/user/${m.staff}`,token,'DELETE'));ok(await api(`/system/role/${m.role}`,token,'DELETE'))
  const l=await detail('/metal/production-line',m.line,token);ok(await api('/metal/production-line',token,'PUT',{...l,productIds:[]}))
  const f=await detail('/metal/factory',m.factory,token);ok(await api('/metal/factory',token,'PUT',{...f,productionLineIds:[]}))
  for(const [path,key]of [['/metal/production-line','line'],['/metal/product','product'],['/metal/material','material'],['/metal/process','process'],['/metal/factory','factory']])ok(await api(`${path}/${m[key]}`,token,'DELETE'))
  writeFileSync(file,'{}')
 }
}
await api('/logout',token,'POST')
