import {test,expect,type Page} from '@playwright/test'

async function openFactoryOptions(page:Page,userId=1){
 const requests:string[]=[],saves:unknown[]=[]
 await page.context().addCookies([{name:'Admin-Token',value:'factory-dropdown-test',url:'http://127.0.0.1:5173'}])
 await page.route('**/dev-api/**',async route=>{
  const url=new URL(route.request().url()),path=url.pathname.replace('/dev-api','');requests.push(path+url.search)
  let result:any={code:200,data:[],rows:[],total:0}
  if(path==='/getInfo')result={...result,user:{userId,userName:userId===1?'admin':'scoped-user'},roles:userId===1?['admin']:['staff'],permissions:userId===1?['*:*:*']:['metal:work-order:list','metal:work-order:add']}
  else if(path==='/getRouters')result.data=[{path:'/metal',component:'Layout',children:[{path:'work-order',component:'metal/work-order/index',name:'WorkOrder',meta:{title:'生产工单管理',noCache:true}}]}]
  else if(path==='/metal/work-order/number/reserve')result.data='WO-DROPDOWN-TEST'
  else if(path==='/metal/work-order/options/factories')result.data=Array.from({length:userId===1?20:1},(_,i)=>({id:String(7000+i),name:'下拉验收工厂'+(i+1)}))
  else if(path==='/metal/work-order/options/lines')result.data=[{id:'8001',name:'所选工厂产线'}]
  else if(path==='/metal/work-order/options/owners')result.data=[{id:'9001',name:'本厂负责人'}]
  else if(path==='/metal/work-order/options/customers')result.data=[{id:'9101',version:0,name:'本厂客户',companyName:'本厂客户公司',address:'测试地址',contactName:'测试联系人',contactPhone:'13812345678',department:null}]
  else if(path==='/metal/work-order'&&['POST','PUT'].includes(route.request().method())){saves.push(route.request().postDataJSON());result={code:500,msg:'本测试禁止保存业务'}}
  await route.fulfill({json:result})
 })
 await page.goto('/metal/work-order');await page.getByRole('button',{name:'添加',exact:true}).click()
 const dialog=page.getByRole('dialog',{name:'添加工单',exact:true})
 if(userId===1)await dialog.locator('.work-order-factory-select .el-select__wrapper').click()
 const panel=page.locator('.el-select-dropdown__wrap:visible').first();if(userId===1)await expect(panel).toBeVisible()
 return{dialog,panel,requests,saves}
}
for(const width of [1440,768,390])test(`工单工厂列表在${width}px可直接显示约十项`,async({page},testInfo)=>{
 await page.setViewportSize({width,height:900});const {dialog,panel}=await openFactoryOptions(page)
 const visible=await panel.evaluate(e=>{const bounds=e.getBoundingClientRect();return [...e.querySelectorAll('.el-select-dropdown__item')].filter(item=>{const r=item.getBoundingClientRect();return r.top>=bounds.top&&r.bottom<=bounds.bottom&&r.bottom<=innerHeight}).length})
 expect(visible).toBeGreaterThanOrEqual(9);expect(visible).toBeLessThanOrEqual(11)
 expect(await panel.evaluate(e=>e.scrollHeight>e.clientHeight)).toBeTruthy()
 const box=await panel.boundingBox();expect(box!.x).toBeGreaterThanOrEqual(0);expect(box!.x+box!.width).toBeLessThanOrEqual(width+1)
 await page.screenshot({path:testInfo.outputPath('work-order-factory-options-'+width+'.png'),fullPage:true,animations:'disabled'})
 await panel.getByRole('option',{name:'下拉验收工厂8',exact:true}).click()
 await expect(dialog.locator('.work-order-factory-select')).toContainText('下拉验收工厂8')
})
test('低高度视口中的工厂列表不超出可视区域',async({page})=>{
 await page.setViewportSize({width:768,height:480});const{panel}=await openFactoryOptions(page)
 const box=await panel.boundingBox();expect(box!.y).toBeGreaterThanOrEqual(0);expect(box!.y+box!.height).toBeLessThanOrEqual(481)
 expect(await panel.evaluate(e=>e.scrollHeight>e.clientHeight)).toBeTruthy()
})

test('普通工单新增账号仅加载本厂候选且不能切换工厂',async({page})=>{
 const{dialog,requests,saves}=await openFactoryOptions(page,9)
 await expect(dialog.getByLabel('工厂名称',{exact:true})).toBeDisabled()
 await expect(dialog.locator('.work-order-factory-select')).toContainText('下拉验收工厂1')
 await expect.poll(()=>requests.filter(p=>p.includes('/options/customers')).length).toBe(1)
 for(const path of requests.filter(p=>/\/options\/(lines|owners|customers)/.test(p)))expect(new URL('http://fixture'+path).searchParams.get('factoryId')).toBe('7000')
 expect(requests.filter(p=>p.includes('factoryId=')&&!p.includes('factoryId=7000'))).toEqual([])
 expect(saves).toEqual([])
})

test('工单新增权限撤销后关闭工厂下拉并禁止重新打开',async({page})=>{
 const{dialog,requests,saves}=await openFactoryOptions(page),before=requests.length
 await page.evaluate(async()=>{const path='/src/store/modules/user.ts';(await import(path)).default().permissions=['metal:work-order:list']})
 await expect(dialog).not.toBeVisible();await expect(page.locator('.work-order-factory-options:visible')).toHaveCount(0)
 await expect(page.getByRole('button',{name:'添加',exact:true})).toHaveCount(0)
 expect(requests.slice(before).filter(p=>p.includes('/options/'))).toEqual([]);expect(saves).toEqual([])
})
