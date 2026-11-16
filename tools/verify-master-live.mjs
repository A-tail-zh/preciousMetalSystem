import {writeFileSync} from 'node:fs'
import {randomBytes} from 'node:crypto'
import assert from 'node:assert/strict'
import {api,sql,login,loginResponse,ok,detail} from './local-test-client.mjs'
const suffix=randomBytes(5).toString('hex'),results=[],created={factories:[],lines:[],products:[],materials:[],processes:[],users:[],roles:[]}
const admin=await login(),sessions=new Set([admin])
const create=async(path,body,group)=>{const response=ok(await api(path,admin,'POST',body));const id=String(response.id??response.factoryId);created[group].push(id);return id}
const reject=async(path,token,method='GET',body,code)=>{const response=await api(path,token,method,body);assert.notEqual(response.code,200,'request should be rejected');if(code)assert.equal(response.code,code,response.msg);return response}
const pass=name=>{results.push(name);console.log(`PASS ${name}`)}
try{
 assert.equal(sql('SELECT COUNT(*) FROM flyway_schema_history WHERE success=1'),'14');pass('V1～V14迁移成功')
 const menus=ok(await api('/metal/options/menus',admin)).data
 const permissionIds=permissions=>{const ids=new Set(menus.filter(m=>permissions.includes(m.perms)).map(m=>m.id));for(const id of [...ids]){let parent=menus.find(m=>m.id===id)?.parentId;while(parent&&parent!=='0'){ids.add(parent);parent=menus.find(m=>m.id===parent)?.parentId}}return[...ids]}
 const permissions=['metal:factory:list','metal:factory:query','metal:factory:edit',...['production-line','product','material','process'].flatMap(k=>['list','query','add','edit','remove'].map(a=>`metal:${k}:${a}`)),...['user','role'].flatMap(k=>['list','query','add','edit','remove'].map(a=>`system:${k}:${a}`)),'system:user:resetPwd']
 const factoryBody=name=>({factoryName:name,area:'12.5000',address:'本次自动验收临时工厂',introduction:'临时验收',productionLineIds:[]})
 const fa=await create('/metal/factory',factoryBody(`验收A_${suffix}`),'factories'),fb=await create('/metal/factory',factoryBody(`验收B_${suffix}`),'factories')
 const process1=await create('/metal/process',{name:`粉料_${suffix}`,description:'粉料描述',processType:1},'processes')
 const process2=await create('/metal/process',{name:`焊接_${suffix}`,description:'焊接描述',processType:8},'processes')
 let proc=await detail('/metal/process',process1,admin);assert.equal(proc.processType,1);assert.equal(typeof proc.id,'string')
 ok(await api('/metal/process',admin,'PUT',{...proc,name:`更新粉料_${suffix}`}));await reject('/metal/process',admin,'PUT',proc);pass('工序真实CRUD与版本冲突')
 const mat=await create('/metal/material',{code:`material_${suffix}`,name:'铱粉样本',materialType:'铱粉',specification:'规格A'},'materials')
 let material=await detail('/metal/material',mat,admin);ok(await api('/metal/material',admin,'PUT',{...material,specification:'规格B'}));await reject('/metal/material',admin,'POST',{code:`bad_${suffix}`,name:'bad',materialType:'非法类型'});pass('物料类型和持久化校验')
 const dims={铱坩埚弧底:{innerDiameter:'10',innerHeight:'20',wallThickness:'1',bottomThickness:'2',rHeight:'3'},铱坩埚平底:{innerDiameter:'10',innerHeight:'20',wallThickness:'1',bottomThickness:'2'},铱坩埚梯形:{innerDiameter:'10',innerHeight:'20',wallThickness:'1',bottomThickness:'2'},铱环:{outerDiameter:'12',innerDiameter:'10',height:'2'},铱杆实心:{diameter:'10',length:'20'},铱杆空心:{outerDiameter:'12',innerDiameter:'10',length:'20'}}
 for(const [category,dimensions]of Object.entries(dims)){
  const id=await create('/metal/product',{code:`product_${suffix}_${created.products.length}`,name:category,category,dimensions,volume:null,purity:'99.999999',weight:'1.123456',processes:[{processId:process2,loss:'0.123456'},{processId:process1,loss:'0'}]},'products')
  const p=await detail('/metal/product',id,admin);assert.deepEqual(p.dimensions,dimensions);if(['铱坩埚平底','铱环','铱杆实心','铱杆空心'].includes(category))assert.ok(p.volume);else assert.equal(p.volume,null);assert.equal(p.weight,'1.123456');assert.deepEqual(p.processes.map(s=>s.processId),[process2,process1]);assert.equal(p.processes[0].loss,'0.123456')
 }
 const first=await detail('/metal/product',created.products[0],admin)
 await reject('/metal/product',admin,'PUT',{...first,processes:[{processId:process1,loss:'1'},{processId:process1,loss:'2'}]})
 await reject('/metal/product',admin,'PUT',{...first,category:'铱环',dimensions:{outerDiameter:'10',innerDiameter:'12',height:'1'}})
 assert.deepEqual((await detail('/metal/product',first.id,admin)).processes,first.processes)
 await reject(`/metal/process/${process1}`,admin,'DELETE');pass('六种产品尺寸、精度、工序顺序损耗、失败回滚与引用删除')
 const line=await create('/metal/production-line',{code:`line_${suffix}`,name:'产线A',factoryId:fa,productIds:created.products.slice(0,2)},'lines')
 const lineB=await create('/metal/production-line',{code:`lineB_${suffix}`,name:'产线B',factoryId:fb,productIds:[]},'lines')
 const actualLine=await detail('/metal/production-line',line,admin);assert.deepEqual(actualLine.productIds,created.products.slice(0,2));assert.equal(typeof actualLine.productIds[0],'string')
 assert.equal((await detail('/metal/factory',fa,admin)).productionLines[0].productionLineId,line)
 await reject(`/metal/product/${created.products[0]},${created.products[2]}`,admin,'DELETE');assert.equal((await detail('/metal/product',created.products[2],admin)).id,created.products[2])
 await reject(`/metal/production-line/${line}`,admin,'DELETE');pass('产线多产品关联、工厂回显、批量引用删除原子性')
 const roleA=await create('/system/role',{roleName:`经理_${suffix}`,factoryId:fa,roleSort:1,status:'0',menuIds:permissionIds(permissions)},'roles')
 const roleB=await create('/system/role',{roleName:`经理_${suffix}`,factoryId:fb,roleSort:2,status:'0',menuIds:permissionIds(permissions)},'roles')
 await reject('/system/role',admin,'POST',{roleName:`经理_${suffix}`,factoryId:fa,roleSort:1,status:'0',menuIds:[]})
 const phoneA='139'+String(Date.now()).slice(-8),phoneB='138'+String(Date.now()).slice(-8),password='T'+randomBytes(9).toString('hex')
 const userA=await create('/system/user',{phonenumber:phoneA,nickName:'经理A',factoryId:fa,roleIds:[roleA],status:'0',password},'users')
 const userB=await create('/system/user',{phonenumber:phoneB,nickName:'经理B',factoryId:fb,roleIds:[roleB],status:'0',password},'users')
 assert.equal((await detail('/system/user',userA,admin)).password,undefined)
 let tokenA=await login(phoneA,password);sessions.add(tokenA)
 let tokenB=await login(phoneB,password);sessions.add(tokenB)
 assert.deepEqual(ok(await api('/metal/factory/list',tokenA)).rows.map(f=>f.factoryId),[fa])
 assert.deepEqual(ok(await api('/metal/production-line/list',tokenA)).rows.map(l=>l.id),[line])
 assert.deepEqual(ok(await api('/system/user/list',tokenA)).rows.map(u=>u.id),[userA])
 assert.deepEqual(ok(await api('/system/role/list',tokenA)).rows.map(r=>r.id),[roleA])
 for(const [path,id]of [['/metal/factory',fb],['/metal/production-line',lineB],['/system/user',userB],['/system/role',roleB]])await reject(`${path}/${id}`,tokenA,'GET',undefined,403)
 await reject(`/metal/options/roles?factoryId=${fb}`,tokenA,'GET',undefined,403)
 await reject('/metal/production-line',tokenA,'PUT',{...actualLine,factoryId:fb},403)
 await reject('/legacy/system/user/list',tokenA,'GET',undefined,403)
 await reject('/legacy/system/role/list',tokenA,'GET',undefined,403)
 await reject('/system/menu/list',tokenA,'GET',undefined,403)
 assert.equal(ok(await api('/metal/product/list',tokenB)).rows.some(p=>p.id===first.id),true)
 pass('两个工厂列表、详情、选项、直接写入及旧接口隔离，共享资料访问')
 const staffA=await detail('/system/user',userA,admin)
 await reject('/system/user',tokenA,'PUT',{...staffA,roleIds:[roleB]},403)
 await reject('/system/user',admin,'PUT',{...staffA,roleIds:['1']},403)
 const unowned=menus.find(m=>m.perms==='system:menu:edit').id
 await reject('/system/role',tokenA,'POST',{roleName:'越权角色',factoryId:fa,roleSort:9,status:'0',menuIds:[unowned]},403)
 await reject(`/system/user/${userA},${userB}`,tokenA,'DELETE',undefined,403)
 await reject(`/system/role/${roleA}`,admin,'DELETE');await reject(`/metal/factory/${fa}`,admin,'DELETE')
 pass('跨厂角色、超级角色、越权授权、人员引用和批量删除拒绝')
 let role=await detail('/system/role',roleA,admin)
 const reduced=role.menuIds.filter(id=>id!==menus.find(m=>m.perms==='metal:material:edit').id)
 ok(await api('/system/role',admin,'PUT',{...role,menuIds:reduced}));await reject('/metal/product/list',tokenA,'GET',undefined,401)
 tokenA=await login(phoneA,password);sessions.add(tokenA)
 await reject('/metal/material',tokenA,'PUT',await detail('/metal/material',mat,admin),403)
 pass('角色授权变更失效旧令牌，新登录权限生效')
 const newPassword='N'+randomBytes(9).toString('hex')
 ok(await api('/system/user/resetPwd',admin,'PUT',{id:userA,version:staffA.version,password:newPassword}));await reject('/metal/product/list',tokenA,'GET',undefined,401)
 assert.notEqual((await loginResponse(phoneA,password)).code,200)
 tokenA=await login(phoneA,newPassword);sessions.add(tokenA)
 let staff=await detail('/system/user',userA,admin)
 ok(await api('/system/user/changeStatus',admin,'PUT',{id:userA,version:staff.version,status:'1'}));await reject('/metal/product/list',tokenA,'GET',undefined,401)
 assert.notEqual((await loginResponse(phoneA,newPassword)).code,200)
 staff=await detail('/system/user',userA,admin);ok(await api('/system/user/changeStatus',admin,'PUT',{id:userA,version:staff.version,status:'0'}))
 tokenA=await login(phoneA,newPassword);sessions.add(tokenA)
 role=await detail('/system/role',roleA,admin);ok(await api('/system/role/changeStatus',admin,'PUT',{id:roleA,version:role.version,status:'1'}));await reject('/metal/product/list',tokenA,'GET',undefined,401)
 role=await detail('/system/role',roleA,admin);ok(await api('/system/role/changeStatus',admin,'PUT',{id:roleA,version:role.version,status:'0'}))
 pass('密码重置、人员启停、角色启停令牌失效与重新登录')
 const rootStaff=await detail('/system/user','1',admin)
 await reject('/system/user/changeStatus',admin,'PUT',{id:'1',version:rootStaff.version,status:'1'})
 await reject('/system/user/1',admin,'DELETE');await reject('/system/role/1',admin,'DELETE')
 const config=ok(await api('/metal/quote-rule',admin)).data
 ok(await api('/metal/quote-rule',admin,'PUT',{...config,basePrice:'25.123456',piValue:'3.141592653589793'}));await reject('/metal/quote-rule',admin,'PUT',config)
 const restored=ok(await api('/metal/quote-rule',admin)).data
 ok(await api('/metal/quote-rule',admin,'PUT',{...restored,basePrice:config.basePrice,piValue:config.piValue}))
 const quotation=ok(await api('/metal/quotation/context',admin)).data;assert.equal(quotation.calculationEnabled,true);assert.ok(quotation.products.some(p=>p.id===first.id))
 pass('系统账号保护、报价规则独立保存、报价计算功能已启用')
 writeFileSync('docs/master-live-verification.json',JSON.stringify({timestamp:new Date().toISOString(),passed:results},null,2))
}finally{
 for(const token of sessions)await api('/logout',token,'POST').catch(()=>{})
 const root=await login()
 const clean=async(path,ids)=>{for(const id of ids){const r=await api(`${path}/${id}`,root);if(r.code===200){const response=await api(`${path}/${id}`,root,'DELETE');if(response.code!==200)console.error(`cleanup incomplete: ${path}/${id} ${response.msg}`)}}}
 await clean('/system/user',created.users);await clean('/system/role',created.roles)
 for(const id of created.lines){const r=await api(`/metal/production-line/${id}`,root);if(r.code===200)await api('/metal/production-line',root,'PUT',{...r.data,productIds:[]})}
 for(const id of created.factories){const r=await api(`/metal/factory/${id}`,root);if(r.code===200)await api('/metal/factory',root,'PUT',{...r.data,productionLineIds:[]})}
 await clean('/metal/production-line',created.lines);await clean('/metal/product',created.products);await clean('/metal/material',created.materials);await clean('/metal/process',created.processes);await clean('/metal/factory',created.factories)
 await api('/logout',root,'POST')
}
