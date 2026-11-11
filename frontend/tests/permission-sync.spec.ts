import {test,expect} from '@playwright/test'

test('existing customer page adopts grants and revocations without logging in again',async({page,baseURL})=>{
 const grants=['metal:customer:list']
 await page.context().addCookies([{name:'Admin-Token',value:'permission-sync-test',url:baseURL!}])
 await page.route('**/*-api/**',async route=>{
  const path=new URL(route.request().url()).pathname.replace(/^\/(dev|prod)-api/,'')
  let result:any={code:200,data:[],rows:[],total:0}
  if(path==='/getInfo')result={code:200,user:{userId:9,userName:'worker',factoryId:'7'},roles:['worker'],permissions:[...grants]}
  if(path==='/getRouters')result.data=[{path:'/metal',component:'Layout',children:[{path:'customer',name:'Customer',component:'metal/customer/index',meta:{title:'客户管理'}}]}]
  if(path==='/metal/options/factories')result.data=[{id:'7',name:'本厂'}]
  await route.fulfill({json:result})
 })
 await page.goto('/metal/customer')
 await expect(page.locator('.page-heading')).toHaveText('客户管理')
 await expect(page.getByRole('button',{name:'添加',exact:true})).toHaveCount(0)
 grants.push('metal:customer:add')
 await page.evaluate(()=>window.dispatchEvent(new Event('permissions-changed')))
 await expect(page.getByRole('button',{name:'添加',exact:true})).toBeVisible({timeout:10000})
 grants.pop()
 await page.evaluate(()=>window.dispatchEvent(new Event('permissions-changed')))
 await expect(page.getByRole('button',{name:'添加',exact:true})).toHaveCount(0,{timeout:10000})
 expect((await page.context().cookies()).some(cookie=>cookie.name==='Admin-Token')).toBeTruthy()
})
