import fs from 'node:fs'
import {randomBytes} from 'node:crypto'
import {api,login,ok,detail} from './local-test-client.mjs'
const token=await login(),factories=ok(await api('/metal/options/factories',token)).data
const factory=factories.find(f=>f.name==='西安航空精密制造厂');if(!factory)throw Error('未找到演示账号所属工厂')
const menus=ok(await api('/metal/options/menus',token)).data
const read=['metal:factory:list','metal:factory:query','metal:production-line:list','metal:production-line:query','metal:product:list','metal:product:query','metal:material:list','metal:material:query','metal:process:list','metal:process:query','metal:work-order:list','metal:work-order:query','metal:quotation:list']
const groups=[
 {role:'工厂管理员',person:'演示工厂管理员',phone:'19900001001',permissions:menus.map(m=>m.perms).filter(p=>p&&(p.startsWith('metal:')||p.startsWith('system:user:')||p.startsWith('system:role:'))&&!['metal:factory:add','metal:factory:remove'].includes(p))},
 {role:'工单审核员',person:'演示工单审核员',phone:'19900001002',permissions:[...read,'metal:work-order:approve','metal:work-order:reject']},
 {role:'生产操作员',person:'演示生产操作员',phone:'19900001003',permissions:[...read,'metal:work-order:add','metal:work-order:edit','metal:work-order:remove','metal:work-order:resubmit']},
 {role:'只读查看员',person:'演示只读查看员',phone:'19900001004',permissions:read}
]
let roles=ok(await api('/system/role/list?pageSize=100',token)).rows,users=ok(await api('/system/user/list?pageSize=100',token)).rows
let previous={};const file='.runtime/demo-identities.json';if(fs.existsSync(file))previous=JSON.parse(fs.readFileSync(file,'utf8'))
const password=previous.password||('Demo'+randomBytes(6).toString('hex')+'!'),results=[]
fs.mkdirSync('.runtime',{recursive:true});fs.writeFileSync(file,JSON.stringify({...previous,factory:factory.name,factoryId:factory.id,password},null,2))
for(const group of groups){
 const ids=new Set(menus.filter(m=>group.permissions.includes(m.perms)).map(m=>m.id));for(const key of [...ids]){let parent=menus.find(m=>m.id===key)?.parentId;while(parent&&parent!=='0'){ids.add(parent);parent=menus.find(m=>m.id===parent)?.parentId}}
 let role=roles.find(r=>r.roleName===group.role&&r.factoryId===factory.id)
 if(!role){const created=ok(await api('/system/role',token,'POST',{roleName:group.role,factoryId:factory.id,roleSort:results.length+1,status:'0',menuIds:[...ids],remark:'本地演示角色'}));role=await detail('/system/role',created.id,token)}
 let person=users.find(u=>u.phonenumber===group.phone)
 if(person){if(person.nickName!==group.person||person.factoryId!==factory.id)throw Error('演示手机号已被其他人员占用：'+group.phone)}
 else{const created=ok(await api('/system/user',token,'POST',{phonenumber:group.phone,nickName:group.person,factoryId:factory.id,roleIds:[role.id],status:'0',password}));person=await detail('/system/user',created.id,token)}
 const loginToken=await login(group.phone,password),info=ok(await api('/getInfo',loginToken));if(group.role==='只读查看员'&&info.permissions.some(p=>p.endsWith(':add')||p.endsWith(':edit')||p.endsWith(':approve')))throw Error('只读账号权限错误');await api('/logout',loginToken,'POST')
 results.push({role:group.role,name:group.person,phone:group.phone,userId:person.id,roleId:role.id})
 console.log('PASS '+group.role+' / '+group.phone+' 创建及登录验证')
}
fs.mkdirSync('.runtime',{recursive:true});fs.writeFileSync(file,JSON.stringify({factory:factory.name,factoryId:factory.id,password,accounts:results},null,2));console.log('账号信息保存在忽略提交的.runtime/demo-identities.json')
