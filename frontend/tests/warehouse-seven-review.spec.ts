import {test,expect,type Page} from '@playwright/test'
async function setup(page:Page,kind='STORAGE',permissions=['metal:inventory:list','metal:warehouse-order:handover'],creator='7'){
 const baseURL=String(test.info().project.use.baseURL||'http://127.0.0.1:5173')
 await page.context().addCookies([{name:'Admin-Token',value:'warehouse-seven-mock',url:baseURL}])
 await page.route('**/dev-api/**',async route=>{const p=new URL(route.request().url()).pathname.replace('/dev-api','');let response:any={code:200,data:[],rows:[],total:0};
  if(p==='/getInfo')response={code:200,user:{userId:7,userName:'actor'},roles:['demo'],permissions}
  else if(p==='/getRouters')response.data=[{path:'/metal',component:'Layout',children:[{path:'inventory',component:'metal/inventory/index',name:'SevenInventory'},{path:'work-order',component:'metal/work-order/index',name:'SevenOrders'}]}]
  else if(p.endsWith('/options/factories'))response.data=[{id:'2',name:'本厂'}]
  else if(p.endsWith('/options/warehouses'))response.data=[{id:'3',name:'原料仓'}]
  else if(p==='/metal/warehouse-flows/5')response.data={id:'5',kind,requestActor:creator,closeoutId:kind==='RAW_INBOUND'?null:'55',status:'WAITING',stage:'KEEPER',factoryId:'2',version:0,groups:[],events:[]}
  else if(p==='/metal/acid-wash/list')response.data={rows:[],total:0}
  else if(p==='/metal/acid-wash/options')response.data={stocks:[{id:'1',warehouseId:'3',warehouseName:'原料仓',code:'OLD',name:'旧料',customerCompanyName:'客户公司',netWeight:'100',reservedWeight:'80',availableWeight:'20'}],processes:[{id:'4',name:'酸洗',version:0}],materials:[]}
  await route.fulfill({json:response})
 })
 await page.goto(baseURL+'/metal/inventory');await expect(page.locator('.warehouse-page')).toBeVisible()
}
for(const kind of ['CUSTOMER_RETURN','STORAGE','OFFSET'])test('handover-only creator can cancel '+kind,async({page})=>{await setup(page,kind);await page.evaluate(async()=>{await (document.querySelector('.warehouse-page') as any).__vueParentComponent.refs.flowDialog.open('5')});await expect(page.getByRole('dialog',{name:'仓储交接确认',exact:true}).getByRole('button',{name:'取消申请',exact:true})).toBeVisible()})
test('closeout cancellation remains hidden from add-only roles and other actors',async({page})=>{await setup(page,'STORAGE',['metal:inventory:list','metal:warehouse-order:add']);await page.evaluate(async()=>{await (document.querySelector('.warehouse-page') as any).__vueParentComponent.refs.flowDialog.open('5')});await expect(page.getByRole('dialog',{name:'仓储交接确认',exact:true}).getByRole('button',{name:'取消申请',exact:true})).toHaveCount(0)})
test('ordinary add-only creator keeps its cancel action',async({page})=>{await setup(page,'RAW_INBOUND',['metal:inventory:list','metal:warehouse-order:add']);await page.evaluate(async()=>{await (document.querySelector('.warehouse-page') as any).__vueParentComponent.refs.flowDialog.open('5')});await expect(page.getByRole('dialog',{name:'原料入库验收',exact:true}).getByRole('button',{name:'取消申请',exact:true})).toBeVisible()})
test('legacy acid entry keeps historical records and disables new starts',async({page})=>{await setup(page,'STORAGE',['*:*:*']);await page.goto('/metal/work-order');await page.getByRole('button',{name:'历史酸洗记录',exact:true}).click();const d=page.getByRole('dialog',{name:'历史酸洗记录',exact:true});await expect(d).toContainText('新酸洗请在普通生产工单中安排完整工艺路线及酸洗工序');await expect(d.getByRole('button',{name:'新增酸洗领料',exact:true})).toHaveCount(0)})
