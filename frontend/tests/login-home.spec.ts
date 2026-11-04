import {test,expect,type Page} from '@playwright/test'

async function setup(page:Page,permissions:string[],available=['inventory','product'],id=2){
 const requests:string[]=[]
 await page.route('**/dev-api/**',async route=>{
  const path=new URL(route.request().url()).pathname.replace('/dev-api','');requests.push(path)
  let out:any={code:200,data:[],rows:[],total:0}
  if(path==='/captchaImage')out={code:200,captchaEnabled:false}
  else if(path==='/login')out={code:200,token:'login-home-fixture'}
  else if(path==='/getInfo')out={code:200,user:{userId:id,userName:'首页测试'},roles:['staff'],permissions}
  else if(path==='/getRouters')out.data=[
   {path:'/system',component:'Layout',children:[{path:'role',component:'metal/role/index',name:'HomeRole',meta:{title:'角色管理'}}]},
   {path:'/metal',component:'Layout',children:available.map(path=>({path,component:`metal/${path}/index`,name:'Home'+path,meta:{title:path}}))},
   ...(permissions.includes('metal:dashboard:list')?[{path:'/',component:'Layout',children:[{path:'index',component:'index',name:'Index',meta:{title:'数据看板'}}]}]:[])
  ]
  else if(path==='/metal/dashboard/factories')out.data={canSelectAll:false,factories:[]}
  else if(path==='/metal/dashboard')out.data={workOrders:{},warnings:{},quotations:{},trend:[],ranking:[],factories:[],distribution:[],warningCenter:[]}
  await route.fulfill({json:out})
 })
 return requests
}
async function login(page:Page,path='/login'){
 await page.goto(path);await submitLogin(page)
}
async function submitLogin(page:Page){
 await page.getByPlaceholder('账号',{exact:true}).fill('13812345678');await page.getByPlaceholder('密码',{exact:true}).fill('Test123456')
 await page.getByRole('button',{name:'登 录',exact:true}).click()
}

for(const redirect of ['', '?redirect=%2Fsystem%2Frole&unrelated=old'])test('login chooses first sidebar page regardless of backend order '+redirect,async({page})=>{
 const requests=await setup(page,['metal:inventory:list','metal:product:list','system:role:list'])
 await login(page,'/login'+redirect);await expect(page).toHaveURL(/\/metal\/product$/);await expect(page.locator('.master-table')).toBeVisible()
 await expect(page.locator('.prototype-sidebar nav a').first()).toHaveAttribute('href','/metal/product')
 expect(requests.some(p=>p.startsWith('/metal/dashboard'))).toBe(false)
})
test('dashboard login and backend button share the sidebar order',async({page})=>{
 await setup(page,['metal:dashboard:list','metal:inventory:list','metal:product:list','system:role:list'])
 await login(page,'/login?redirect=%2Fmetal%2Finventory');await expect(page).toHaveURL(/\/index$/);await expect(page.locator('.dashboard-header')).toBeVisible()
 await page.getByRole('button',{name:'后台管理',exact:true}).click();await expect(page).toHaveURL(/\/metal\/product$/)
})
test('first permitted menu must also be provided and registered',async({page})=>{
 await setup(page,['metal:factory:list','metal:product:list','metal:inventory:list'],['inventory'])
 await login(page);await expect(page).toHaveURL(/\/metal\/inventory$/)
})
test('acid history alone is a valid management landing page',async({page})=>{
 await setup(page,['metal:warehouse-order:query'],[]);await login(page);await expect(page).toHaveURL(/\/metal\/acid-wash-history$/)
 await expect(page.getByRole('link',{name:'历史酸洗记录',exact:true})).toBeVisible()
})
test('user without any visible management permission reaches 401',async({page})=>{
 await setup(page,[],[]);await login(page);await expect(page).toHaveURL(/\/401$/)
})
test('non-admin cannot land on administrator-only logs despite having their permission',async({page})=>{
 await setup(page,['monitor:runtime-log:list'],[])
 await page.route('**/dev-api/getRouters',r=>r.fulfill({json:{code:200,data:[{path:'/audit',component:'Layout',children:[{path:'runtime',component:'audit/runtime',name:'Runtime',meta:{title:'系统运行日志'}}]}]}}))
 await login(page);await expect(page).toHaveURL(/\/401$/)
})
test('new login reloads identity even if previous roles remain in memory',async({page})=>{
 const requests=await setup(page,['metal:product:list'],['product']);await page.goto('/login')
 await page.evaluate(async()=>{const path='/src/store/modules/user.ts',user=(await import(path)).default();user.roles=['previous-account'];user.permissions=['metal:inventory:list'];user.sessionReady=false})
 await submitLogin(page);await expect(page).toHaveURL(/\/metal\/product$/);expect(requests.filter(p=>p==='/getInfo')).toHaveLength(1)
})
