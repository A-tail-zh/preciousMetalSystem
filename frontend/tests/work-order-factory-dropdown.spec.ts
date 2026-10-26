import {test,expect,type Page} from '@playwright/test'

async function openFactoryOptions(page:Page){
 await page.context().addCookies([{name:'Admin-Token',value:'factory-dropdown-test',url:'http://127.0.0.1:5173'}])
 await page.route('**/dev-api/**',async route=>{
  const path=new URL(route.request().url()).pathname.replace('/dev-api','')
  let result:any={code:200,data:[],rows:[],total:0}
  if(path==='/getInfo')result={...result,user:{userId:1,userName:'admin'},roles:['admin'],permissions:['*:*:*']}
  else if(path==='/getRouters')result.data=[{path:'/metal',component:'Layout',children:[{path:'work-order',component:'metal/work-order/index',name:'WorkOrder',meta:{title:'生产工单管理',noCache:true}}]}]
  else if(path==='/metal/work-order/number/reserve')result.data='WO-DROPDOWN-TEST'
  else if(path==='/metal/work-order/options/factories')result.data=Array.from({length:20},(_,i)=>({id:String(7000+i),name:'下拉验收工厂'+(i+1)}))
  else if(path==='/metal/work-order/options/lines')result.data=[{id:'8001',name:'所选工厂产线'}]
  await route.fulfill({json:result})
 })
 await page.goto('/metal/work-order');await page.getByRole('button',{name:'添加',exact:true}).click()
 const dialog=page.getByRole('dialog',{name:'添加工单',exact:true})
 await dialog.locator('.el-form-item').filter({has:page.locator('label').getByText('工厂',{exact:true})}).locator('.el-select__wrapper').click()
 const panel=page.locator('.el-select-dropdown__wrap:visible').first();await expect(panel).toBeVisible()
 return{dialog,panel}
}
for(const width of [1440,768,390])test(`工单工厂列表在${width}px可直接显示约十项`,async({page})=>{
 await page.setViewportSize({width,height:900});const {dialog,panel}=await openFactoryOptions(page)
 const visible=await panel.evaluate(e=>{const bounds=e.getBoundingClientRect();return [...e.querySelectorAll('.el-select-dropdown__item')].filter(item=>{const r=item.getBoundingClientRect();return r.top>=bounds.top&&r.bottom<=bounds.bottom&&r.bottom<=innerHeight}).length})
 expect(visible).toBeGreaterThanOrEqual(9);expect(visible).toBeLessThanOrEqual(11)
 expect(await panel.evaluate(e=>e.scrollHeight>e.clientHeight)).toBeTruthy()
 const box=await panel.boundingBox();expect(box!.x).toBeGreaterThanOrEqual(0);expect(box!.x+box!.width).toBeLessThanOrEqual(width+1)
 await page.screenshot({path:`../.runtime/work-order-factory-options-${width}.png`,fullPage:true,animations:'disabled'})
 await panel.getByRole('option',{name:'下拉验收工厂8',exact:true}).click()
 await expect(dialog.locator('.work-order-factory-select')).toContainText('下拉验收工厂8')
})
test('低高度视口中的工厂列表不超出可视区域',async({page})=>{
 await page.setViewportSize({width:768,height:480});const{panel}=await openFactoryOptions(page)
 const box=await panel.boundingBox();expect(box!.y).toBeGreaterThanOrEqual(0);expect(box!.y+box!.height).toBeLessThanOrEqual(481)
 expect(await panel.evaluate(e=>e.scrollHeight>e.clientHeight)).toBeTruthy()
})
