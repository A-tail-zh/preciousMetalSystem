import {test,expect,type Page} from '@playwright/test'
const reader=['metal:work-order:list','metal:inventory:list','metal:inventory:query','metal:warehouse-order:query']
async function setup(page:Page,permissions=reader,path='/metal/work-order',factories=[{id:'2',name:'本厂'}]){
 const calls:{path:string;method:string;factory:string|null}[]=[]
 await page.context().addCookies([{name:'Admin-Token',value:'legacy-acid-test',url:'http://127.0.0.1:5195'}])
 await page.route('**/dev-api/**',async route=>{
  const req=route.request(),url=new URL(req.url()),p=url.pathname.replace('/dev-api','');calls.push({path:p,method:req.method(),factory:url.searchParams.get('factoryId')})
  let out:any={code:200,data:[],rows:[],total:0}
  if(p==='/getInfo')out={code:200,user:{userId:7,userName:'只读用户'},roles:['reader'],permissions}
  else if(p==='/getRouters')out.data=[{path:'/metal',component:'Layout',children:[...(permissions.includes('metal:work-order:list')?[{path:'work-order',component:'metal/work-order/index',name:'LegacyOrders'}]:[]),{path:'inventory',component:'metal/inventory/index',name:'LegacyInventory'}]}]
  else if(p.endsWith('/options/factories'))out.data=factories
  else if(p==='/metal/acid-wash/list')out.data={rows:[{id:'12',batchNo:'历史独立酸洗-'+url.searchParams.get('factoryId'),status:'OPEN',issuedWeight:'100',outputWeight:'0',processName:'酸洗'}],total:1}
  else if(p==='/metal/acid-wash/12')out.data={id:'12',factoryId:'2',batchNo:'历史独立酸洗-2',process:{process_name:'酸洗'},status:'OPEN',version:0,issuedWeight:'100',outputWeight:'0',returnedWeight:'0',orders:[{id:'21',orderNo:'旧领料单21',type:'ACID_OUT',warehouseName:'原料仓',status:'POSTED'}],sources:[{id:'22',inventoryId:'31',warehouseId:'3',warehouseName:'原料仓',code:'旧粉料',netWeight:'100'}]}
  else if(p==='/metal/acid-wash/options')out.data={stocks:[],processes:[],materials:[{id:'10',code:'IR',name:'金属料'}]}
  else if(p==='/metal/inventory/list')out={code:200,rows:[{id:'31',category:'RAW',code:'旧酸洗料',netWeight:'98',washingStatus:'PASSED'}],total:1}
  else if(p==='/metal/inventory/31')out.data={id:'31',factoryId:'2',category:'RAW',originAcidWashId:'12',code:'旧酸洗料',netWeight:'98',washingStatus:'PASSED',ledger:[{orderNo:'旧产出单23',type:'ACID_IN',deltaWeight:'98',balanceWeight:'98'}]}
  await route.fulfill({json:out})
 })
 await page.goto(path);await expect(page.locator(path.includes('inventory')?'.warehouse-page':path.includes('history')?'.acid-history-page':'.work-order-page')).toBeVisible();return calls
}
test('history moves to work orders and remains available without work-order creation',async({page})=>{
 const calls=await setup(page);await expect(page.locator('.order-toolbar').getByRole('button',{name:'添加',exact:true})).toHaveCount(0)
 await page.getByRole('button',{name:'历史酸洗记录',exact:true}).click();const d=page.getByRole('dialog',{name:'历史酸洗记录',exact:true})
 await expect(d).toContainText('完整工艺路线');await expect(d).toContainText('历史独立酸洗-2');await d.getByRole('button',{name:'查看详情',exact:true}).click();await expect(d).toContainText('旧领料单21')
 await expect(d.getByRole('button',{name:'确认产出及全部还料并结算',exact:true})).toHaveCount(0);await expect(d.getByRole('button',{name:'整体冲销酸洗批次',exact:true})).toHaveCount(0);expect(calls.some(c=>c.path==='/metal/acid-wash/options')).toBe(false);expect(calls.some(c=>c.path==='/metal/acid-wash'&&c.method==='POST')).toBe(false)
 await d.getByRole('button',{name:'关闭',exact:true}).click();await page.goto('/metal/inventory');await expect(page.locator('.toolbar').getByRole('button',{name:'历史酸洗记录',exact:true})).toHaveCount(0)
})
test('inventory ledger and explicit legacy source trace remain accessible',async({page})=>{
 await setup(page,reader,'/metal/inventory');await page.getByRole('button',{name:'查看详情',exact:true}).click();await expect(page.getByRole('dialog',{name:'库存管理详情',exact:true})).toContainText('旧产出单23')
 await page.getByRole('button',{name:'查看历史酸洗来源',exact:true}).click();await expect(page.getByRole('dialog',{name:'历史酸洗记录',exact:true})).toContainText('旧领料单21')
})
test('history is hidden without original warehouse read permission',async({page})=>{const calls=await setup(page,['metal:work-order:list']);await expect(page.getByRole('button',{name:'历史酸洗记录',exact:true})).toHaveCount(0);expect(calls.some(c=>c.path.startsWith('/metal/acid-wash'))).toBe(false)})
test('legacy finish and controlled reverse retain their original permissions',async({page})=>{
 await setup(page,[...reader,'metal:warehouse-order:add','metal:process-batch:settle','metal:warehouse-order:reverse']);await page.getByRole('button',{name:'历史酸洗记录',exact:true}).click();const d=page.getByRole('dialog',{name:'历史酸洗记录',exact:true});await d.getByRole('button',{name:'登记产出',exact:true}).click();await expect(d.getByRole('button',{name:'确认产出及全部还料并结算',exact:true})).toBeVisible();await expect(d.getByRole('button',{name:'整体冲销酸洗批次',exact:true})).toBeVisible();await expect(d.getByRole('button',{name:'新增酸洗领料',exact:true})).toHaveCount(0)
})
test('factory change discards previous history and uses the chosen factory',async({page})=>{
 const calls=await setup(page,reader,'/metal/work-order',[{id:'2',name:'本厂'},{id:'3',name:'另一授权工厂'}]);await page.getByRole('button',{name:'历史酸洗记录',exact:true}).click();const d=page.getByRole('dialog',{name:'历史酸洗记录',exact:true})
 await d.locator('.el-select__wrapper').first().click();await page.getByRole('option',{name:'本厂',exact:true}).click();await expect(d).toContainText('历史独立酸洗-2');await d.locator('.el-select__wrapper').first().click();await page.getByRole('option',{name:'另一授权工厂',exact:true}).click();await expect(d).toContainText('历史独立酸洗-3');await expect(d).not.toContainText('历史独立酸洗-2');expect(calls.filter(c=>c.path==='/metal/acid-wash/list').map(c=>c.factory)).toEqual(['2','3'])
})
test('toolbar and dialog fit business widths',async({page})=>{await setup(page);for(const width of [1920,1440,768,390]){await page.setViewportSize({width,height:1000});const button=page.getByRole('button',{name:'历史酸洗记录',exact:true});await expect(button).toBeVisible();expect(await button.evaluate(el=>el.getBoundingClientRect().right<=innerWidth)).toBe(true);await button.click();const d=page.getByRole('dialog',{name:'历史酸洗记录',exact:true});expect(await d.evaluate(el=>el.scrollWidth<=el.clientWidth+1)).toBe(true);await d.getByRole('button',{name:'关闭',exact:true}).click()}})
test('warehouse-only history reader has dedicated navigation without work-order route or permission',async({page})=>{
 const calls=await setup(page,['metal:warehouse-order:query'],'/metal/acid-wash-history');await expect(page.getByRole('dialog',{name:'历史酸洗记录',exact:true})).toContainText('历史独立酸洗-2');await expect(page.getByRole('link',{name:'历史酸洗记录',exact:true})).toHaveAttribute('href','/metal/acid-wash-history');expect(calls.some(c=>c.path.startsWith('/metal/work-order'))).toBe(false)
 await page.evaluate(async()=>{const path='/src/store/modules/user.ts';(await import(path)).default().permissions=[]});await expect(page.getByRole('dialog',{name:'历史酸洗记录',exact:true})).toHaveCount(0);await expect(page.getByRole('link',{name:'历史酸洗记录',exact:true})).toHaveCount(0);await expect(page.locator('.acid-history-page')).toContainText('没有历史酸洗查询权限')
})
test('work-order list refresh preserves selection and defers while legacy dialog is open',async({page})=>{
 await page.clock.install();await setup(page,[...reader,'metal:work-order:edit']);let revision=1,count=0
 await page.route('**/dev-api/metal/work-order/list?*',async route=>{count++;await route.fulfill({json:{code:200,rows:[{id:'41',orderNo:'工单修订'+revision,status:'IN_PROGRESS',canEdit:true}],total:1}})})
 await page.evaluate(()=>window.dispatchEvent(new Event('metal:inventory-changed')));await page.clock.runFor(200);await expect(page.locator('.order-table')).toContainText('工单修订1');await page.locator('.order-table .el-table__body .el-checkbox').click();revision=2
 await page.clock.runFor(5100);await expect(page.locator('.order-table')).toContainText('工单修订2');await expect(page.locator('.order-table .el-table__body .el-checkbox input')).toBeChecked()
 await page.getByRole('button',{name:'历史酸洗记录',exact:true}).click();const before=count;revision=3;await page.clock.runFor(10100);expect(count).toBe(before);await page.getByRole('dialog',{name:'历史酸洗记录',exact:true}).getByRole('button',{name:'关闭',exact:true}).click();await page.clock.runFor(200);await expect(page.locator('.order-table')).toContainText('工单修订3')
})
test('inventory refresh preserves rows on background failure without a global error',async({page})=>{
 await page.clock.install();await setup(page,reader,'/metal/inventory');await expect(page.locator('.warehouse-table')).toContainText('旧酸洗料');await page.route('**/dev-api/metal/inventory/list?*',route=>route.fulfill({json:{code:500,msg:'周期读取失败'}}));await page.clock.runFor(5100);await expect(page.locator('.warehouse-table')).toContainText('旧酸洗料');await expect(page.getByRole('status').filter({hasText:'更新失败'})).toBeVisible();await expect(page.locator('.el-message')).toHaveCount(0)
})
test('inventory legacy source link rejects an unauthorized factory before reading a batch',async({page})=>{
 const calls=await setup(page,reader,'/metal/inventory',[{id:'3',name:'仅授权工厂'}]);await page.getByRole('button',{name:'查看详情',exact:true}).click();await page.getByRole('button',{name:'查看历史酸洗来源',exact:true}).click();await expect(page.getByRole('dialog',{name:'历史酸洗记录',exact:true})).toContainText('没有此工厂的酸洗查询权限');expect(calls.some(c=>c.path==='/metal/acid-wash/12')).toBe(false)
})
test('inventory refresh keeps valid selection and pauses while its detail is open',async({page})=>{
 await page.clock.install();await setup(page,reader,'/metal/inventory');await expect(page.locator('.warehouse-table')).toContainText('旧酸洗料');await page.locator('.warehouse-table .el-table__body .el-checkbox').click();let calls=0
 await page.route('**/dev-api/metal/inventory/list?*',async route=>{calls++;await route.fulfill({json:{code:200,rows:[{id:'31',category:'RAW',code:'更新酸洗料',netWeight:'97'}],total:1}})});await page.clock.runFor(5100);await expect(page.locator('.warehouse-table')).toContainText('更新酸洗料');await expect(page.locator('.warehouse-table .el-table__body .el-checkbox input')).toBeChecked();await page.getByRole('button',{name:'查看详情',exact:true}).click();const before=calls;await page.clock.runFor(10100);expect(calls).toBe(before);await expect(page.getByRole('dialog',{name:'库存管理详情',exact:true})).toContainText('旧产出单23')
})
