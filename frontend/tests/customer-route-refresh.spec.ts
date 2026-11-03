import {test,expect,type Page} from '@playwright/test'

async function setup(page:Page,admin=true){
 const host=test.info().project.use.baseURL as string||'http://127.0.0.1:5173'
 await page.context().addCookies([{name:'Admin-Token',value:'route-refresh-test',url:host}])
 let customerAvailable=false,routerRequests=0,customerRequests=0,hold=false,release=()=>{}
 await page.route('**/dev-api/**',async route=>{
  const path=new URL(route.request().url()).pathname.replace('/dev-api','')
  let result:any={code:200,data:[],rows:[],total:0}
  if(path==='/getInfo')result={code:200,user:{userId:admin?1:2,userName:admin?'admin':'reader'},roles:[admin?'admin':'reader'],permissions:admin?['*:*:*']:['metal:factory:list']}
  else if(path==='/getRouters'){
   routerRequests++;if(hold)await new Promise<void>(resolve=>release=resolve)
   result.data=[{name:'Metal',path:'/metal',component:'Layout',children:[{name:'Factory',path:'factory',component:'metal/factory/index'},...(customerAvailable&&admin?[{name:'Customer',path:'customer',component:'metal/customer/index'}]:[])]}]
  }else if(path==='/metal/customer/list')customerRequests++
  await route.fulfill({json:result})
 })
 await page.goto('/metal/factory');await expect(page.locator('.factory-page')).toBeVisible()
 return {enable:()=>customerAvailable=true,hold:()=>hold=true,release:()=>release(),get routerRequests(){return routerRequests},get customerRequests(){return customerRequests}}
}

test('existing admin tab refreshes authorized routes when customer menu is added',async({page})=>{
 const state=await setup(page);expect(state.routerRequests).toBe(1);state.enable()
 await page.getByRole('link',{name:'客户管理',exact:true}).click()
 await expect(page.locator('.customer-page')).toBeVisible();expect(state.routerRequests).toBe(2);expect(state.customerRequests).toBe(1)
})
test('route refresh preserves query and hash',async({page})=>{
 const state=await setup(page);state.enable()
 await page.evaluate(()=>{const router=(document.querySelector('#app') as any).__vue_app__.config.globalProperties.$router;return router.push('/metal/customer?from=lan#customer')})
 await expect(page.locator('.customer-page')).toBeVisible();await expect(page).toHaveURL(/\/metal\/customer\?from=lan#customer$/);expect(state.routerRequests).toBe(2)
})
test('ordinary account without customer grant remains unauthorized',async({page})=>{
 const state=await setup(page,false);state.enable()
 await expect(page.getByRole('link',{name:'客户管理',exact:true})).toHaveCount(0)
 await page.evaluate(()=>{const router=(document.querySelector('#app') as any).__vue_app__.config.globalProperties.$router;return router.push('/metal/customer')})
 await expect(page.locator('.not-found-page')).toBeVisible();expect(state.routerRequests).toBe(2);expect(state.customerRequests).toBe(0)
})
test('genuinely unknown URL remains 404 without navigation loop',async({page})=>{
 const state=await setup(page);state.enable()
 await page.evaluate(()=>{const router=(document.querySelector('#app') as any).__vue_app__.config.globalProperties.$router;return router.push('/missing-customer-test')})
 await expect(page.locator('.not-found-page')).toBeVisible();expect(state.routerRequests).toBe(2);expect(state.customerRequests).toBe(0)
})
test('late route refresh cannot register routes after account changes',async({page})=>{
 const state=await setup(page);state.enable();state.hold()
 await page.evaluate(()=>{const router=(document.querySelector('#app') as any).__vue_app__.config.globalProperties.$router;(window as any).routeRefreshResult='pending';void router.push('/metal/customer').then(()=>(window as any).routeRefreshResult='done',()=>(window as any).routeRefreshResult='rejected')});await expect.poll(()=>state.routerRequests).toBe(2)
 await page.evaluate(async()=>{const authPath='/src/utils/auth.ts',userPath='/src/store/modules/user.ts';(await import(authPath)).setToken('new-route-session');const user=(await import(userPath)).default();user.token='new-route-session';user.id=22;user.roles=['reader'];user.permissions=['metal:factory:list']})
 state.release()
 await expect.poll(()=>page.evaluate(()=>(window as any).routeRefreshResult)).not.toBe('pending')
 expect(await page.evaluate(()=>{const router=(document.querySelector('#app') as any).__vue_app__.config.globalProperties.$router;return router.hasRoute('Customer')})).toBe(false)
 await expect(page.locator('.customer-page')).toHaveCount(0)
})
test('route refresh failure keeps existing login and does not register customer',async({page})=>{
 await setup(page);await page.route('**/dev-api/getRouters',route=>route.fulfill({json:{code:500,msg:'路由加载失败'}}))
 await page.getByRole('link',{name:'客户管理',exact:true}).click();await expect(page.locator('.not-found-page')).toBeVisible()
 expect((await page.context().cookies()).some(cookie=>cookie.name==='Admin-Token'&&cookie.value==='route-refresh-test')).toBe(true)
 expect(await page.evaluate(()=>{const router=(document.querySelector('#app') as any).__vue_app__.config.globalProperties.$router;return router.hasRoute('Customer')})).toBe(false)
})
