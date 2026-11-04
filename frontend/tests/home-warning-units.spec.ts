import {test,expect} from '@playwright/test'

for(const [unit,suffix] of [['PERCENT','%'],['G_PER_PIECE','（历史 g/件）'],[undefined,'（历史 g/件）']] as const){
 test('home warning threshold renders '+(unit??'missing historical unit'),async({page})=>{
  await page.context().addCookies([{name:'Admin-Token',value:'home-unit-mock',url:'http://127.0.0.1:5200'}])
  await page.route('**/dev-api/**',route=>{
   const path=new URL(route.request().url()).pathname.replace('/dev-api','')
   let result:any={code:200,data:[]}
   if(path==='/getInfo')result={code:200,user:{userId:1,userName:'单位测试'},roles:['admin'],permissions:['*:*:*']}
   else if(path==='/getRouters')result.data=[{path:'/',component:'Layout',children:[{path:'index',component:'index',name:'Index',meta:{title:'数据看板'}}]}]
   else if(path==='/metal/dashboard/factories')result.data={canSelectAll:true,factories:[{id:'F',name:'测试工厂'}]}
   else if(path==='/metal/dashboard')result.data={warningCenter:[{triggerTime:'2026-11-03 12:00:00',factoryName:'测试工厂',processName:'单位测试工序',thresholdValue:'1.25',...(unit?{lossUnit:unit}:{}),status:'PENDING'}],trend:[{day:'2026-11-03',loss:'1.25'}],ranking:[],factories:[],distribution:[]}
   return route.fulfill({json:result})
  })
  await page.goto('/index');const table=page.locator('.warning-table');await expect(table).toContainText('单位测试工序')
  await expect(table.locator('.el-table__body tr').first().locator('td').nth(3)).toHaveText('1.25'+suffix)
  await expect(page.locator('.dashboard canvas')).toHaveCount(4)
 })
}
