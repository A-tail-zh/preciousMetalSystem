import {test,expect,type Page} from '@playwright/test'

async function factory(page:Page,permissions=['*:*:*']){
 await page.context().addCookies([{name:'Admin-Token',value:'list-style-fixture',url:'http://127.0.0.1:5173'}])
 await page.route('**/dev-api/**',async route=>{
  const path=new URL(route.request().url()).pathname.replace('/dev-api','')
  let response:any={code:200,data:[],rows:[],total:0}
  if(path==='/getInfo')response={code:200,user:{userId:permissions.includes('*:*:*')?1:2,userName:'fixture',nickName:'测试人员'},roles:['factory'],permissions}
  else if(path==='/getRouters')response.data=[{path:'/metal',component:'Layout',children:[{path:'factory',component:'metal/factory/index',name:'Factory'}]}]
  else if(path==='/metal/factory/list')response={code:200,rows:[{factoryId:'10',factoryName:'按钮测试工厂',area:'1.00',address:'测试地址',version:0}],total:1}
  await route.fulfill({json:response})
 })
 await page.goto('/metal/factory')
 await expect(page.locator('.factory-table .el-table__row')).toHaveCount(1)
}

for(const width of [1920,1440,768,390])test(`列表按钮颜色、禁用态及布局 ${width}px`,async({page})=>{
 await page.setViewportSize({width,height:1000});await factory(page)
 const toolbar=page.locator('.factory-toolbar')
 await expect(toolbar.getByRole('button',{name:'添加',exact:true})).toHaveCSS('background-color','rgb(103, 194, 58)')
 await expect(toolbar.getByRole('button',{name:'查询',exact:true})).toHaveCSS('background-color','rgb(64, 158, 255)')
 await expect(toolbar.getByRole('button',{name:'重置',exact:true})).toHaveCSS('background-color','rgb(255, 255, 255)')
 for(const name of ['编辑','删除'])await expect(toolbar.getByRole('button',{name,exact:true})).toBeDisabled()
 await expect(toolbar.getByRole('button',{name:'编辑',exact:true})).toHaveCSS('opacity','0.6')
 await expect(page.locator('.factory-table').getByRole('button',{name:'编辑',exact:true})).toHaveCSS('background-color','rgb(238, 155, 88)')
 await expect(page.locator('.factory-table').getByRole('button',{name:'删除',exact:true})).toHaveCSS('background-color','rgb(245, 108, 108)')
 await expect(page.locator('.factory-table .list-action--row').first()).toHaveCSS('height',width<=768?'44px':'25px')
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)
 await page.screenshot({path:test.info().outputPath(`list-${width}.png`),fullPage:true})
})

test('选中、悬停、焦点和重置后仍保留原有可操作条件',async({page})=>{
 await factory(page)
 const toolbar=page.locator('.factory-toolbar'),edit=toolbar.getByRole('button',{name:'编辑',exact:true})
 await page.locator('.factory-table .el-table__row .el-checkbox').click()
 await expect(edit).toBeEnabled();await expect(edit).toHaveCSS('background-color','rgb(238, 155, 88)')
 await edit.hover();await expect(edit).not.toHaveCSS('background-color','rgb(238, 155, 88)')
 await page.mouse.move(0,0);await toolbar.getByRole('button',{name:'添加',exact:true}).focus();await page.keyboard.press('Tab')
 await expect(edit).toBeFocused();await expect(edit).toHaveCSS('outline-style','solid')
 await page.locator('.factory-table .el-table__row .el-checkbox').click();await expect(edit).toBeDisabled()
 await toolbar.getByRole('button',{name:'重置',exact:true}).click();await expect(edit).toBeDisabled()
})

test('列表样式不进入新增弹窗',async({page})=>{
 await factory(page);await page.locator('.factory-toolbar').getByRole('button',{name:'添加',exact:true}).click()
 const dialog=page.getByRole('dialog');await expect(dialog).toBeVisible()
 await expect(dialog.locator('.list-action')).toHaveCount(0)
 await expect(dialog.getByRole('button',{name:'确定',exact:true})).toHaveCSS('background-color','rgb(64, 158, 255)')
 await dialog.getByRole('button',{name:'取消',exact:true}).click();await expect(dialog).not.toBeVisible()
})

test('没有新增编辑删除权限时不显示按钮',async({page})=>{
 await factory(page,['metal:factory:list','metal:factory:query'])
 for(const name of ['添加','编辑','删除'])await expect(page.getByRole('button',{name,exact:true})).toHaveCount(0)
 await expect(page.locator('.factory-toolbar').getByRole('button',{name:'查询',exact:true})).toBeVisible()
})

const lists=[
 ['metal','factory','metal/factory/index'],['metal','product','metal/product/index'],
 ['metal','production-line','metal/production-line/index'],['metal','material','metal/material/index'],
 ['metal','process','metal/process/index'],['metal','customer','metal/customer/index'],
 ['system','user','metal/staff/index'],['system','role','metal/role/index'],
 ['metal','work-order','metal/work-order/index'],['metal','inventory','metal/inventory/index'],
 ['metal','warehouse-order','metal/warehouse-order/index'],['metal','warehouse','metal/warehouse/index'],
 ['metal','warning-rule','metal/warning-rule/index'],['metal','warning-record','metal/warning-record/index'],
 ['metal','process-batch','metal/process-batch/index'],['metal','quote-rule','metal/quote-rule/index'],
 ['metal','quotation','metal/quotation/index'],['audit','operations','audit/operations'],
 ['audit','logins','audit/logins'],['audit','runtime','audit/runtime']
]
for(const width of [1920,1440,768,390])test(`各业务列表按钮范围与配色 ${width}px`,async({page})=>{
 test.setTimeout(120000)
 await page.setViewportSize({width,height:1000})
 await page.context().addCookies([{name:'Admin-Token',value:'all-list-style-fixture',url:'http://127.0.0.1:5173'}])
 const errors:string[]=[];page.on('pageerror',e=>errors.push(e.message))
 let releaseExport:(()=>void)|undefined
 await page.route('**/dev-api/**',async route=>{
  const path=new URL(route.request().url()).pathname.replace('/dev-api','')
  if(path.endsWith('/export')){await new Promise<void>(resolve=>{releaseExport=resolve});await route.fulfill({contentType:'application/octet-stream',body:Buffer.from('fixture export')});return}
  let response:any={code:200,data:[],rows:[],total:0}
  if(path==='/getInfo')response={code:200,user:{userId:1,userName:'admin',nickName:'测试管理员'},roles:['admin'],permissions:['*:*:*']}
  else if(path==='/getRouters')response.data=['metal','system','audit'].map(group=>({path:'/'+group,component:'Layout',children:lists.filter(([g])=>g===group).map(([,path,component])=>({path,component,name:group+'-'+path}))}))
  else if(path==='/metal/options/factories')response.data=[{id:'10',name:'测试工厂'}]
  else if(path==='/metal/volume-rule')response.data={piValue:'3.14',curvedBottomFactor:'1.03',version:0}
  else if(path==='/metal/quotation/rules')response.data={metals:[],volumeRule:{piValue:'3.14',curvedBottomFactor:'1.03',version:0}}
  else if(path==='/metal/quote-rule/list')response.data=[{metalTypeId:'10',metalTypeName:'铱',density:'22.65',powderPrice:'1.00',nonPowderPrice:'2.00',shortagePrice:'3.00',lossPrice:'4.00',version:0}]
  else if(path.endsWith('/list'))response={code:200,rows:[{id:'10',factoryId:'10',factoryName:'测试工厂',name:'测试资料',code:'TEST-10',userId:'10',roleId:'10',nickName:'测试人员',roleName:'测试角色',customerName:'测试客户',customerId:'10',status:'0',version:0,area:'1.00',address:'测试地址',measurementMode:'WEIGHT',referenceQuantity:33,netWeight:'100.00',quantity:0,canDelete:true}],total:1}
  else if(path==='/metal/quotation/orders')response={code:200,rows:[{id:'10',quoteNo:'QT-TEST',factoryName:'测试工厂',categories:'铱杆',totalPrice:'100.00'}],total:1}
  await route.fulfill({json:response})
 })
 for(const [group,path] of lists){
  await page.goto(`/${group}/${path}`)
  if(path==='quote-rule'){const factory=page.getByRole('combobox',{name:'所属工厂',exact:true});await expect(factory).toBeEnabled();await factory.press('ArrowDown');await page.getByRole('option',{name:'测试工厂',exact:true}).click()}
  const buttons=page.locator('main .list-action')
  await expect(buttons.first(),`${path} 应有列表按钮`).toBeVisible()
  const colors=await buttons.evaluateAll(nodes=>nodes.map(node=>({classes:node.className,text:node.textContent?.trim(),color:getComputedStyle(node).backgroundColor})))
  for(const button of colors){
   const expected=(['添加','新增','添加工序类型','增加报价'].includes(button.text??'')||(path==='quotation'&&button.text==='下载'))?'rgb(103, 194, 58)':['编辑','修改','库存调整'].includes(button.text??'')?'rgb(238, 155, 88)':button.text==='删除'?'rgb(245, 108, 108)':['重置','返回'].includes(button.text??'')?'rgb(255, 255, 255)':'rgb(64, 158, 255)'
   expect(button.color,`${path}: ${button.classes}`).toBe(expected)
  }
  for(const button of await page.locator('main .el-table .el-button').all())await expect(button).toHaveClass(/list-action/)
  if(path==='user'&&width>768){
   const row=page.locator('main .el-table__row').first().locator('.list-action--row')
   await expect(row).toHaveCount(3)
   const tops=await row.evaluateAll(nodes=>nodes.map(n=>n.getBoundingClientRect().top))
   expect(Math.max(...tops)-Math.min(...tops),'人员列表三个行内按钮应保持同一行').toBeLessThanOrEqual(1)
  }
  await expect(page.locator('.el-dialog .list-action')).toHaveCount(0)
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),`${path} 不应撑出页面`).toBe(true)
  if(path==='operations'&&width===1920){
   const button=page.locator('.log-toolbar').getByRole('button',{name:'导出',exact:true})
   await expect(button).toBeEnabled();const clicking=button.click()
   try{await expect(button).toHaveClass(/is-loading/);await expect.poll(()=>!!releaseExport).toBe(true)}finally{releaseExport?.()}
   await clicking;await expect(button).not.toHaveClass(/is-loading/)
  }
  if(['warehouse-order','work-order','quotation','operations'].includes(path))await page.screenshot({path:test.info().outputPath(`${path}-${width}.png`),fullPage:true})
 }
 expect(errors).toEqual([])
})
