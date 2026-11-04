import {test,expect,type Page} from '@playwright/test'

async function setup(page:Page,permissions:string[],withManagement=true){
 const requests:string[]=[],saves:any[]=[]
 const can=permissions.some(p=>p==='*:*:*'||p==='metal:dashboard:list')
 await page.context().addCookies([{name:'Admin-Token',value:'dashboard-permission-test',url:'http://127.0.0.1:5218'}])
 await page.route('**/dev-api/**',async route=>{
  const request=route.request(),path=new URL(request.url()).pathname.replace('/dev-api','');requests.push(path)
  let out:any={code:200,data:[],rows:[],total:0}
  if(path==='/getInfo')out={code:200,user:{userId:permissions.includes('*:*:*')?1:2,userName:'权限测试'},roles:['staff'],permissions}
  else if(path==='/getRouters')out.data=[
   ...(can?[{path:'/',component:'Layout',children:[{path:'index',component:'index',name:'Index',meta:{title:'数据看板',icon:'dashboard'}}]}]:[]),
   ...(withManagement?[{path:'/system',component:'Layout',children:[{path:'role',component:'metal/role/index',name:'Role',meta:{title:'角色管理'}}]}]:[])
  ]
  else if(path==='/metal/dashboard/factories')out.data={canSelectAll:false,factories:[{id:'1',name:'所属工厂'}]}
  else if(path==='/metal/dashboard')out.data={workOrders:{total:3},warnings:{},quotations:{},trend:[],ranking:[],factories:[],distribution:[],warningCenter:[]}
  else if(path==='/metal/options/factories')out.data=[{id:'1',name:'所属工厂'}]
  else if(path==='/metal/options/menus')out.data=[{id:'58',parentId:'0',name:'数据看板',perms:'metal:dashboard:list'}]
  else if(path==='/system/role'&&request.method()==='POST')saves.push(request.postDataJSON())
  await route.fulfill({json:out})
 })
 return {requests,saves}
}

for(const permissions of [['metal:dashboard:list'],['*:*:*']])test('authorized home renders dashboard '+permissions[0],async({page})=>{
 await setup(page,permissions,permissions.includes('*:*:*'));await page.goto('/');await expect(page).toHaveURL(/\/index$/)
 await expect(page.locator('.summary-card').first()).toContainText('3单');await expect(page.locator('.dashboard canvas')).toHaveCount(4)
 await page.reload();await expect(page.locator('.dashboard-header')).toBeVisible()
 if(permissions.includes('*:*:*')){
  await page.getByRole('button',{name:'后台管理',exact:true}).click();await expect(page).toHaveURL(/\/system\/role$/)
  await page.getByRole('link',{name:'数据看板',exact:true}).click();await expect(page.locator('.dashboard-header')).toBeVisible()
 }else await expect(page.getByRole('button',{name:'后台管理',exact:true})).toBeDisabled()
})

for(const path of ['/','/index'])test('ungranted '+path+' falls back without requesting dashboard data',async({page})=>{
 const s=await setup(page,['system:role:list']);await page.goto(path);await expect(page).toHaveURL(/\/system\/role$/)
 await expect(page.locator('.master-table')).toBeVisible();await expect(page.getByRole('link',{name:'数据看板',exact:true})).toHaveCount(0)
 expect(s.requests.some(path=>path.startsWith('/metal/dashboard'))).toBe(false)
 await page.setViewportSize({width:390,height:844});await page.getByRole('button',{name:'打开导航菜单',exact:true}).click()
 await expect(page.getByRole('dialog',{name:'导航菜单'})).toBeVisible();await expect(page.getByRole('link',{name:'数据看板',exact:true})).toHaveCount(0)
})

test('ungranted user with no menu reaches permission page without a redirect loop',async({page})=>{
 const s=await setup(page,[],false);await page.goto('/');await expect(page).toHaveURL(/\/401$/)
 await expect(page.getByText('您没有访问权限！',{exact:true})).toBeVisible();expect(s.requests.filter(p=>p==='/getRouters')).toHaveLength(1)
 expect(s.requests.some(path=>path.startsWith('/metal/dashboard'))).toBe(false)
})

test('role editor saves the selected dashboard menu',async({page})=>{
 const s=await setup(page,['*:*:*']);await page.goto('/system/role');await page.getByRole('button',{name:'添加',exact:true}).click()
 const dialog=page.getByRole('dialog',{name:'添加角色',exact:true});await dialog.getByLabel('角色名称',{exact:true}).fill('看板查看员')
 await dialog.locator('.el-form-item').filter({hasText:'所属工厂'}).locator('.el-select__wrapper').click();await page.getByRole('option',{name:'所属工厂',exact:true}).click()
 await dialog.locator('.role-permission-tree .el-tree-node__content').filter({hasText:'数据看板'}).locator('.el-checkbox').click()
 await dialog.getByRole('button',{name:'确定',exact:true}).click();await expect(dialog).not.toBeVisible();expect(s.saves[0].menuIds).toEqual(['58'])
})
