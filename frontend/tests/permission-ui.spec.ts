import {test,expect,type Page} from '@playwright/test'

async function setup(page:Page,baseURL:string,grants={value:['*:*:*']},ordinary=false){
 await page.context().addCookies([{name:'Admin-Token',value:'permission-ui-test',url:baseURL}])
 await page.route('**/*-api/**',async route=>{
  const path=new URL(route.request().url()).pathname.replace(/^\/(dev|prod)-api/,'')
  let r:any={code:200,data:[],rows:[],total:0}
  if(path==='/getInfo')r={code:200,user:{userId:ordinary?9:1,userName:ordinary?'worker':'admin',factoryId:ordinary?'101':null},roles:[ordinary?'worker':'admin'],permissions:grants.value}
  if(path==='/getRouters')r.data=[{path:'/system',name:'System',component:'Layout',children:grants.value.includes('*:*:*')?[{path:'role',name:'Role',component:'metal/role/index',meta:{title:'角色管理'}}]:[]}]
  if(path==='/metal/options/factories')r.data=[{id:'101',name:'本厂'}]
  if(path==='/metal/options/menus')r.data=[{id:'1',parentId:'0',name:'产品管理'},{id:'2',parentId:'1',name:'产品管理query',perms:'metal:product:query'},{id:'3',parentId:'1',name:'产品管理add',perms:'metal:product:add'},{id:'4',parentId:'1',name:'产品管理/edit',perms:'metal:product:edit'},{id:'5',parentId:'1',name:'产品管理remove',perms:'metal:product:remove'},{id:'6',parentId:'1',name:'产品管理/export',perms:'metal:product:export'}]
  if(path==='/metal/customer/list')r.rows=[{id:'11',factoryId:'101',name:'客户甲'}]
  if(path==='/metal/production-line/list')r.rows=[{id:'21',factoryId:'101',name:'本厂产线',productIds:['31']}]
  if(path==='/system/user/list')r.rows=[{id:'9',factoryId:'101',nickName:'加工人员',status:'0'}]
  if(path==='/metal/product/list')r.rows=[{id:'31',factoryId:'101',name:'关联产品',code:'P31',processes:[{processId:'71',processName:'工序甲'}]},{id:'32',factoryId:'101',name:'其他产线产品',code:'P32',processes:[]}]
  if(path==='/metal/storage-review-tasks/count')r.data={count:0}
  if(path==='/metal/storage-orders/options')r.data={materials:[{id:'41',code:'M41',name:'计重物料',measurementMode:'WEIGHT'},{id:'42',code:'M42',name:'计件物料',measurementMode:'PIECE'},{id:'43',code:'M43',name:'未配置物料'}],warehouses:[{id:'51',name:'原料库'}],people:[{id:'9',name:'仓管员',canAccept:true}]}
  if(r.rows?.length)r.total=r.rows.length
  await route.fulfill({json:r})
 })
}
async function navigate(page:Page,path:string){await page.evaluate(path=>(document.querySelector('#app') as any).__vue_app__.config.globalProperties.$router.push(path),path)}
async function changed(page:Page){await page.evaluate(async()=>{(await import('/src/utils/permission-change.ts')).notifyPermissionChange()})}

test('role permissions survive first opening, switching roles and saving',async({page,baseURL})=>{
 await setup(page,baseURL!)
 const roles=[{id:'21',version:1,roleName:'仓管',factoryId:'101',status:'0',menuIds:['1','2']},{id:'22',version:1,roleName:'加工',factoryId:'101',status:'0',menuIds:['3']}]
 const saved:string[][]=[]
 await page.route('**/*-api/system/role/**',async route=>{
  const path=new URL(route.request().url()).pathname
  if(path.endsWith('/list'))await route.fulfill({json:{code:200,rows:roles,total:2}})
  else await route.fulfill({json:{code:200,data:roles.find(r=>path.endsWith('/'+r.id))}})
 })
 await page.route('**/*-api/system/role',async route=>{
  const body=route.request().postDataJSON();saved.push(body.menuIds)
  Object.assign(roles.find(r=>r.id===body.id)!,body)
  await route.fulfill({json:{code:200}})
 })
 await page.goto('/system/role')
 const dialog=page.getByRole('dialog'),tree=dialog.getByRole('region',{name:'菜单和按钮权限列表'})
 const node=(name:string)=>tree.getByRole('treeitem').filter({has:page.locator('.el-tree-node__label',{hasText:new RegExp('^'+name+'$')})}).last().getByRole('checkbox').first()
 await page.getByRole('row').filter({hasText:'仓管'}).getByRole('button',{name:'编辑',exact:true}).click()
 await expect(node('产品管理查看')).toBeChecked()
 await expect(node('产品管理新增')).not.toBeChecked()
 await dialog.getByRole('button',{name:'确定',exact:true}).click()
 await expect(dialog).not.toBeVisible()
 expect(saved[0].sort()).toEqual(['1','2'])
 await page.getByRole('row').filter({hasText:'加工'}).getByRole('button',{name:'编辑',exact:true}).click()
 await expect(node('产品管理新增')).toBeChecked()
 await expect(node('产品管理查看')).not.toBeChecked()
 await tree.getByText('产品管理新增',{exact:true}).click()
 await tree.getByText('产品管理/修改',{exact:true}).click()
 await dialog.getByRole('button',{name:'确定',exact:true}).click()
 await expect(dialog).not.toBeVisible()
 expect(saved[1]).toEqual(['4'])
 await page.getByRole('row').filter({hasText:'加工'}).getByRole('button',{name:'编辑',exact:true}).click()
 await expect(node('产品管理/修改')).toBeChecked()
 await expect(node('产品管理新增')).not.toBeChecked()
})

test('repeated route generation never leaves an empty layout parent',async({page,baseURL})=>{
 await setup(page,baseURL!)
 await page.goto('/metal/production-order')
 await expect(page.getByRole('combobox',{name:'所属工厂',exact:true})).toBeVisible()
 for(let i=0;i<3;i++){
  await navigate(page,'/missing-'+i)
  for(const [path,title]of [['/metal/production-order','生产工单管理'],['/metal/storage-warehouses','仓库管理'],['/metal/storage-inventory','仓储管理'],['/metal/storage-orders','仓储工单管理']]){
   await navigate(page,path);await expect(page.locator('.page-heading')).toHaveText(title)
   await expect(page.getByRole('combobox',{name:'所属工厂',exact:true})).toBeVisible()
  }
 }
 const parents=await page.evaluate(()=>(document.querySelector('#app') as any).__vue_app__.config.globalProperties.$router.getRoutes().filter((r:any)=>r.path==='/metal/production-order'&&r.children.length).length)
 expect(parents).toBe(1)
})

test('visible ordinary session adopts grants and revocations without login',async({page,baseURL})=>{
 const grants={value:['metal:work-order:list','metal:warehouse:list']}
 await setup(page,baseURL!,grants,true);await page.goto('/metal/production-order')
 await expect(page.getByRole('button',{name:'添加',exact:true})).toHaveCount(0)
 grants.value.push('metal:work-order:add')
 await page.waitForTimeout(3500)
 await expect(page.getByRole('button',{name:'添加',exact:true})).toHaveCount(0)
 await changed(page)
 await expect(page.getByRole('button',{name:'添加',exact:true})).toBeVisible()
 grants.value=['metal:warehouse:list']
 await changed(page)
 await expect(page).toHaveURL(/\/metal\/storage-warehouses/)
 await expect(page.getByRole('link',{name:'生产工单管理',exact:true})).toHaveCount(0)
 await expect(page.getByRole('combobox',{name:'所属工厂',exact:true})).toBeDisabled()
 expect((await page.context().cookies()).some(c=>c.name==='Admin-Token')).toBeTruthy()
})

test('permission tree translates actions with and without separators',async({page,baseURL})=>{
 await setup(page,baseURL!);await page.goto('/system/role')
 await page.getByRole('button',{name:'添加',exact:true}).click()
 const tree=page.getByRole('region',{name:'菜单和按钮权限列表'})
 for(const label of ['产品管理查看','产品管理新增','产品管理/修改','产品管理删除','产品管理/导出'])await expect(tree.getByText(label,{exact:true})).toBeVisible()
 expect(await tree.innerText()).not.toMatch(/query|add|edit|remove|export/)
})

test('failed menu refresh retries the changed permissions on the next sync',async({page,baseURL})=>{
 const grants={value:['metal:warehouse:list']}
 await setup(page,baseURL!,grants,true);await page.goto('/metal/storage-warehouses')
 await expect(page.locator('.page-heading')).toHaveText('仓库管理')
 let failures=0
 await page.route('**/getRouters',async route=>{
  if(failures++===0)await route.fulfill({json:{code:500,msg:'暂时失败'}})
  else await route.fallback()
 })
 grants.value.push('metal:work-order:list')
 await changed(page)
 await expect(page.getByRole('link',{name:'生产工单管理',exact:true})).toBeVisible({timeout:10000})
 await page.getByRole('link',{name:'生产工单管理',exact:true}).click()
 await expect(page.locator('.page-heading')).toHaveText('生产工单管理')
 expect(failures).toBeGreaterThanOrEqual(2)
})

test('arrival uses the order form style, material-specific counts and no explanatory text',async({page,baseURL})=>{
 await setup(page,baseURL!);await page.goto('/metal/production-order')
 await page.getByRole('combobox',{name:'所属工厂',exact:true}).press('ArrowDown');await page.getByRole('option',{name:'本厂',exact:true}).click()
 await page.getByRole('button',{name:'添加',exact:true}).click()
 const d=page.getByRole('dialog',{name:'添加工单'}),arrival=d.locator('.arrival-details')
 await expect(d.getByRole('textbox',{name:'客户来料净重（g）',exact:true})).toHaveCount(0)
 await expect(d.getByRole('combobox',{name:'选择产品 1',exact:true})).toBeDisabled()
 await expect(d.getByRole('combobox',{name:'产线',exact:true})).toBeEnabled()
 await d.getByRole('combobox',{name:'产线',exact:true}).press('ArrowDown');await page.getByRole('option',{name:'本厂产线',exact:true}).click()
 await d.getByRole('combobox',{name:'选择产品 1',exact:true}).press('ArrowDown')
 await expect(page.getByRole('option',{name:'关联产品（P31）',exact:true})).toBeVisible()
 await expect(page.getByRole('option',{name:'其他产线产品（P32）',exact:true})).toHaveCount(0)
 await page.getByRole('option',{name:'关联产品（P31）',exact:true}).click()
 await expect(d.getByRole('cell',{name:'工序甲',exact:true})).toBeVisible()
 await arrival.getByRole('button',{name:'＋ 添加来料',exact:true}).click()
 const row=arrival.locator('.arrival-group').first()
 await row.getByRole('combobox').first().press('ArrowDown');await page.getByRole('option').filter({hasText:'计重物料'}).click()
 await expect(row.getByRole('textbox',{name:'来料件数 1',exact:true})).toHaveValue('按重量')
 await row.getByRole('textbox',{name:'来料净重 1',exact:true}).fill('12.34')
 await expect(arrival.getByText(/来料合计|复验前不增加库存|保存工单后生成/)).toHaveCount(0)
 await row.getByRole('combobox').first().press('ArrowDown');await page.getByRole('option').filter({hasText:'计件物料'}).click()
 await expect(row.getByRole('textbox',{name:'来料件数 1',exact:true})).toBeEditable()
 await expect(arrival.getByRole('button',{name:'＋ 添加来料',exact:true})).toHaveClass(/el-button--primary.*is-plain/)
 const style=(element:HTMLElement)=>{const s=getComputedStyle(element);return {fontSize:s.fontSize,fontWeight:s.fontWeight,color:s.color}}
 const titleStyle=await arrival.locator('.el-form-item__label').first().evaluate(style)
 const ownerStyle=await d.locator('.el-form-item').filter({has:page.getByRole('combobox',{name:'负责人',exact:true})}).locator('.el-form-item__label').evaluate(style)
 expect(titleStyle).toEqual(ownerStyle)
})
