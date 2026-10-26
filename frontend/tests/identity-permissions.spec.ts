import {test,expect,type Page} from '@playwright/test'
import {randomInt,randomBytes} from 'node:crypto'
import {api,login,loginResponse,ok,detail} from '../../tools/local-test-client.mjs'

async function fixture() {
 const admin=await login(),suffix=Date.now().toString(),records:Array<[string,string]>=[],sessions=new Set<string>([admin])
 const create=async(path:string,body:object)=>{const r=ok(await api(path,admin,'POST',body));const id=String(r.id??r.factoryId);records.push([path,id]);return id}
 const factoryName=`权限页面厂_${suffix}`
 const factory=await create('/metal/factory',{factoryName,area:'1',address:'临时权限测试地址'})
 const menus=ok(await api('/metal/options/menus',admin)).data
 const permissions=['system:user:list','system:user:query','system:role:list','system:role:query']
 const selected=new Set<string>(menus.filter((m:any)=>permissions.includes(m.perms)).map((m:any)=>m.id))
 for(const id of [...selected]){let parent=menus.find((m:any)=>m.id===id)?.parentId;while(parent&&parent!=='0'){selected.add(parent);parent=menus.find((m:any)=>m.id===parent)?.parentId}}
 const track=(path:string,id:string)=>records.push([path,id])
 const cleanup=async()=>{
  const failures:string[]=[]
  const existing=await api('/metal/factory/'+factory,admin);if(existing.code===200)ok(await api('/metal/factory',admin,'PUT',{...existing.data,productionLineIds:[]}))
  for(const path of ['/system/user','/system/role','/metal/production-line','/metal/factory'])for(const [p,id]of records.filter(([p])=>p===path))try{
   const r=await api(`${p}/${id}`,admin);if(r.code===200)ok(await api(`${p}/${id}`,admin,'DELETE'))
  }catch(e){failures.push(`${p}/${id}: ${e}`)}
  for(const token of sessions)await api('/logout',token,'POST')
  expect(failures,'临时人员、角色和工厂清理失败').toEqual([])
 }
 return {admin,suffix,factory,factoryName,menus,selected,create,track,sessions,cleanup}
}
async function select(page:Page,label:string,name:string){
 await page.getByRole('dialog').locator('.el-form-item').filter({hasText:label}).locator('.el-select__wrapper').click()
 await page.getByRole('option',{name,exact:true}).click()
 await page.getByRole('dialog').getByRole('heading').click()
}
async function authenticated(page:Page,token:string,path:string){
 await page.context().addCookies([{name:'Admin-Token',value:token,url:'http://127.0.0.1:5173'}]);await page.goto(path)
 await expect(page.locator('.master-table')).toBeVisible()
}

test('角色页面：授权树新增、权限回显、修改和删除',async({page})=>{
 test.setTimeout(60000);const f=await fixture()
 try{
  const name=`权限页面角色_${f.suffix}`
  await authenticated(page,f.admin,'/system/role');await page.getByRole('button',{name:'添加',exact:true}).click()
  const dialog=page.getByRole('dialog');await dialog.getByLabel('角色名称',{exact:true}).fill(name);await select(page,'所属工厂',f.factoryName)
  const depth=(m:any)=>{let n=0,parent=m.parentId;const visited=new Set();while(parent&&parent!=='0'&&!visited.has(parent)){visited.add(parent);n++;parent=f.menus.find((x:any)=>x.id===parent)?.parentId}return n};
  for(const m of [...f.menus].sort((a:any,b:any)=>depth(a)-depth(b))){const node=dialog.locator('.el-tree-node[data-key="'+m.id+'"] > .el-tree-node__content');if(await node.locator('input[type="checkbox"]').isChecked()!==f.selected.has(m.id))await node.locator('.el-checkbox__inner').click()}

  const saved=page.waitForResponse(r=>r.url().endsWith('/system/role')&&r.request().method()==='POST')
  await dialog.getByRole('button',{name:'确定',exact:true}).click();const response=await(await saved).json()
  if(response.id)f.track('/system/role',String(response.id));expect(response.code,response.msg).toBe(200);const id=String(response.id)
  await expect(dialog).not.toBeVisible();await page.reload()
  let row=page.getByRole('row').filter({hasText:name});await row.getByRole('button',{name:'编辑',exact:true}).click()
  for(const menu of f.selected){await expect(dialog.locator('.el-tree-node[data-key="'+menu+'"] > .el-tree-node__content .el-checkbox')).toHaveClass(/is-checked/)}
  await dialog.getByLabel('角色名称',{exact:true}).fill(name+'_修改');await dialog.getByRole('button',{name:'确定',exact:true}).click();await expect(dialog).not.toBeVisible()
  const d=await detail('/system/role',id,f.admin);expect(new Set(d.menuIds)).toEqual(f.selected);expect(d.roleName).toBe(name+'_修改')
  row=page.getByRole('row').filter({hasText:name+'_修改'});await row.getByRole('button',{name:'删除',exact:true}).click();await page.getByRole('button',{name:'确定',exact:true}).click();await expect(row).toHaveCount(0)
 }finally{await f.cleanup()}
})

test('工作人员页面：新增、角色回显、编辑、重置密码、启停和删除',async({page})=>{
 test.setTimeout(60000);const f=await fixture()
 try{
  const roleName=`权限人员角色_${f.suffix}`
  const role=await f.create('/system/role',{roleName,factoryId:f.factory,roleSort:0,status:'0',menuIds:[...f.selected]})
  const phone=String(randomInt(13000000000,13999999999)),password='UI'+randomBytes(10).toString('hex'),newPassword='Reset'+randomBytes(10).toString('hex'),name=`权限页面人员_${f.suffix}`
  await authenticated(page,f.admin,'/system/user');await page.getByRole('button',{name:'添加',exact:true}).click()
  const dialog=page.getByRole('dialog');await select(page,'所属工厂',f.factoryName);await select(page,'角色',roleName)
  await dialog.getByLabel('手机号',{exact:true}).fill(phone);await dialog.getByLabel('姓名',{exact:true}).fill(name);await dialog.getByLabel('初始密码',{exact:true}).fill(password)
  const saved=page.waitForResponse(r=>r.url().endsWith('/system/user')&&r.request().method()==='POST')
  await dialog.getByRole('button',{name:'确定',exact:true}).click();const r=await(await saved).json();if(r.id)f.track('/system/user',String(r.id));expect(r.code,r.msg).toBe(200);const id=String(r.id)
  await expect(dialog).not.toBeVisible();await page.reload();let row=page.getByRole('row').filter({hasText:name})
  await row.getByRole('button',{name:'编辑',exact:true}).click();await expect(dialog.getByLabel('姓名',{exact:true})).toHaveValue(name);await expect(dialog).toContainText(roleName)
  await expect(dialog.getByLabel('初始密码',{exact:true})).toHaveCount(0)
  await dialog.getByLabel('姓名',{exact:true}).fill(name+'_修改');await dialog.getByRole('button',{name:'确定',exact:true}).click();await expect(dialog).not.toBeVisible()
  expect((await detail('/system/user',id,f.admin)).roleIds).toEqual([role]);row=page.getByRole('row').filter({hasText:name+'_修改'})
  await row.getByRole('button',{name:'重置密码',exact:true}).click();await page.getByPlaceholder('请输入至少8位的新密码').fill(newPassword);await dialog.getByRole('button',{name:'确定',exact:true}).click();await expect(dialog).not.toBeVisible()
  expect((await loginResponse(phone,password)).code).not.toBe(200);const token=await login(phone,newPassword);f.sessions.add(token)
  await row.locator('.el-switch__core').click();await expect.poll(async()=> (await detail('/system/user',id,f.admin)).status).toBe('1')
  expect((await api('/getInfo',token)).code).toBe(401);expect((await loginResponse(phone,newPassword)).code).not.toBe(200)
  await row.locator('.el-switch__core').click();await expect.poll(async()=> (await detail('/system/user',id,f.admin)).status).toBe('0')
  f.sessions.add(await login(phone,newPassword));await row.getByRole('button',{name:'删除',exact:true}).click();await page.getByRole('button',{name:'确定',exact:true}).click();await expect(row).toHaveCount(0)
 }finally{await f.cleanup()}
})

test('只读工作人员：角色和人员页面隐藏写入按钮，账号状态只读显示',async({page})=>{
 test.setTimeout(60000);const f=await fixture()
 try{
  const role=await f.create('/system/role',{roleName:`只读页面角色_${f.suffix}`,factoryId:f.factory,roleSort:0,status:'0',menuIds:[...f.selected]})
  const phone=String(randomInt(13000000000,13999999999)),password='View'+randomBytes(10).toString('hex')
  const user=await f.create('/system/user',{nickName:`只读页面人员_${f.suffix}`,phonenumber:phone,factoryId:f.factory,roleIds:[role],status:'0',password})
  const token=await login(phone,password);f.sessions.add(token)
  for(const path of ['/system/user','/system/role']){
   await authenticated(page,token,path);await expect(page.locator('.master-table .el-table__body-wrapper tbody tr')).toHaveCount(1)
   for(const name of ['添加','编辑','删除','导出'])await expect(page.locator('.master-toolbar').getByRole('button',{name,exact:true})).toHaveCount(0);await expect(page.locator('.master-toolbar').getByRole('button',{name:'查询',exact:true})).toBeVisible();await expect(page.locator('.master-table button')).toHaveCount(0)
   await expect(page.locator('.master-table .el-switch')).toHaveCount(0)
  }
  expect((await api(`/system/user/${user}`,token,'DELETE')).code).toBe(403)
 }finally{await f.cleanup()}
})
