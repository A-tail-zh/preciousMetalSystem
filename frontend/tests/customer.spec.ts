import {test,expect,type Page} from '@playwright/test'

async function setup(page:Page,permissions=['*:*:*'],admin=true){
 await page.context().addCookies([{name:'Admin-Token',value:'customer-test',url:test.info().project.use.baseURL as string||'http://127.0.0.1:5173'}])
 const row={id:'9',version:0,name:'客户甲',companyName:'客户甲公司',address:'客户地址',contactName:'张三',contactPhone:'13800138000',department:null,factoryId:'7',factoryName:'工厂甲',createTime:'2026-11-02 10:00:00'}
 const posts:any[]=[],queries:any[]=[],deletes:string[]=[]
 let fail=false,holdSave=false,holdEdit=false,releaseSave=()=>{},releaseEdit=()=>{}
 await page.route('**/dev-api/**',async route=>{
  const req=route.request(),url=new URL(req.url()),path=url.pathname.replace('/dev-api','')
  let out:any={code:200,data:[],rows:[],total:0}
  if(path==='/getInfo')out={code:200,user:{userId:admin?1:2,userName:admin?'admin':'customer-user'},roles:[admin?'admin':'staff'],permissions}
  else if(path==='/getRouters')out.data=[{path:'/metal',component:'Layout',children:[{path:'customer',component:'metal/customer/index'}]}]
  else if(path==='/metal/options/factories'||path==='/metal/filter-options/factories')out={code:200,data:[{id:'7',name:'工厂甲'}],rows:[{id:'7',name:'工厂甲'}],hasMore:false}
  else if(path==='/metal/customer/list'){queries.push(Object.fromEntries(url.searchParams));out={code:200,rows:[row],total:1}}
  else if(path==='/metal/customer/9/edit-context'){if(holdEdit)await new Promise<void>(resolve=>releaseEdit=resolve);out.data={...row}}
  else if(path==='/metal/customer'&&['POST','PUT'].includes(req.method())){posts.push(req.postDataJSON());if(holdSave)await new Promise<void>(resolve=>releaseSave=resolve);out=fail?{code:500,msg:'同厂客户名称和公司名称已存在',fieldErrors:[{field:'companyName',code:'Duplicate',message:'同厂客户名称和公司名称已存在'}]}:{code:200,id:'10'}}
  else if(req.method()==='DELETE'){deletes.push(path)}
  await route.fulfill({json:out})
 })
 await page.goto('/metal/customer');await expect(page.locator('.customer-page .el-table')).toBeVisible()
 return {posts,queries,deletes,fail:()=>fail=true,holdSave:()=>holdSave=true,holdEdit:()=>holdEdit=true,releaseSave:()=>releaseSave(),releaseEdit:()=>releaseEdit()}
}
const dialog=(page:Page)=>page.getByRole('dialog')
async function fill(page:Page){for(const[label,value]of [['客户名称',' 客户乙 '],['客户公司名称',' 客户乙公司 '],['客户地址','客户乙地址'],['联络人名称','李四'],['联络人电话','13900139000']])await dialog(page).getByLabel(label,{exact:true}).fill(value)}
async function chooseFactory(page:Page){await dialog(page).getByRole('combobox',{name:'所属工厂',exact:true}).press('ArrowDown');await page.getByRole('option',{name:'工厂甲',exact:true}).click()}

test('required fields and mobile validation prevent submission',async({page})=>{
 const state=await setup(page);await page.getByRole('button',{name:'添加',exact:true}).click();await dialog(page).getByRole('button',{name:'保存',exact:true}).click();await expect(dialog(page)).toContainText('请输入客户名称');await expect(dialog(page)).toContainText('请选择所属工厂');expect(state.posts).toHaveLength(0)
 await chooseFactory(page);await fill(page);await dialog(page).getByLabel('联络人电话',{exact:true}).fill('02812345678');await dialog(page).getByRole('button',{name:'保存',exact:true}).click();await expect(dialog(page)).toContainText('联络人电话须为11位大陆手机号');expect(state.posts).toHaveLength(0)
})
test('admin selects factory and saves trimmed fields with optional department',async({page})=>{
 const state=await setup(page);await page.getByRole('button',{name:'添加',exact:true}).click();await chooseFactory(page);await fill(page);await dialog(page).getByRole('button',{name:'保存',exact:true}).click();await expect(dialog(page)).not.toBeVisible();expect(state.posts).toEqual([{factoryId:'7',name:'客户乙',companyName:'客户乙公司',address:'客户乙地址',contactName:'李四',contactPhone:'13900139000',department:null}])
})
test('ordinary account has automatic readonly factory',async({page})=>{
 const state=await setup(page,['metal:customer:list','metal:customer:add'],false);await page.getByRole('button',{name:'添加',exact:true}).click();await expect(dialog(page).getByRole('textbox',{name:'所属工厂',exact:true})).toHaveValue('工厂甲');await expect(dialog(page).getByRole('textbox',{name:'所属工厂',exact:true})).toHaveAttribute('readonly','');await expect(dialog(page).getByRole('combobox')).toHaveCount(0);await fill(page);await dialog(page).getByRole('button',{name:'保存',exact:true}).click();await expect(dialog(page)).not.toBeVisible();expect(state.posts[0].factoryId).toBe('7')
})
test('editing retains factory and version and can set department',async({page})=>{
 const state=await setup(page);await page.getByRole('row').filter({hasText:'客户甲公司'}).getByRole('button',{name:'编辑',exact:true}).click();await expect(dialog(page).getByLabel('客户名称',{exact:true})).toHaveValue('客户甲');await expect(dialog(page).getByRole('textbox',{name:'所属工厂',exact:true})).toHaveAttribute('readonly','');await dialog(page).getByLabel('部门',{exact:true}).fill('技术部');await dialog(page).getByRole('button',{name:'保存',exact:true}).click();await expect(dialog(page)).not.toBeVisible();expect(state.posts[0]).toMatchObject({id:'9',version:0,factoryId:'7',department:'技术部'});expect(state.posts[0]).not.toHaveProperty('createTime')
})
test('duplicate error stays inline and keeps draft',async({page})=>{
 const state=await setup(page);state.fail();await page.getByRole('button',{name:'添加',exact:true}).click();await chooseFactory(page);await fill(page);await dialog(page).getByRole('button',{name:'保存',exact:true}).click();await expect(dialog(page)).toContainText('同厂客户名称和公司名称已存在');await expect(dialog(page).getByLabel('客户名称',{exact:true})).toHaveValue('客户乙')
})
test('read-only role has no mutation buttons',async({page})=>{
 await setup(page,['metal:customer:list','metal:customer:query'],false);for(const name of ['添加','编辑','删除'])await expect(page.getByRole('button',{name,exact:true})).toHaveCount(0)
})
test('filters are forwarded and reset',async({page})=>{
 const state=await setup(page);await page.getByRole('textbox',{name:'客户公司名称',exact:true}).fill('公司过滤');await page.getByRole('textbox',{name:'联络人电话',exact:true}).fill('138');await page.getByRole('button',{name:'查询',exact:true}).click();await expect.poll(()=>state.queries.at(-1)?.companyName).toBe('公司过滤');expect(state.queries.at(-1)?.contactPhone).toBe('138');await page.getByRole('button',{name:'重置',exact:true}).click();await expect.poll(()=>state.queries.at(-1)?.companyName).toBeUndefined()
})
test('delete requires confirmation and calls once',async({page})=>{
 const state=await setup(page);await page.getByRole('row').filter({hasText:'客户甲公司'}).getByRole('button',{name:'删除',exact:true}).click();expect(state.deletes).toHaveLength(0);await page.getByRole('button',{name:'确定',exact:true}).click();await expect.poll(()=>state.deletes.length).toBe(1);expect(state.deletes[0]).toBe('/metal/customer/9')
})
test('late edit response cannot replace reopened add draft',async({page})=>{
 const state=await setup(page);state.holdEdit();await page.getByRole('row').filter({hasText:'客户甲公司'}).getByRole('button',{name:'编辑',exact:true}).click();await dialog(page).getByRole('button',{name:'取消',exact:true}).click();await page.getByRole('button',{name:'添加',exact:true}).click();await fill(page);state.releaseEdit();await expect(dialog(page).getByLabel('客户名称',{exact:true})).toHaveValue(' 客户乙 ')
})
test('session change closes form and late save cannot publish success',async({page})=>{
 const state=await setup(page);state.holdSave();await page.getByRole('button',{name:'添加',exact:true}).click();await chooseFactory(page);await fill(page);await dialog(page).getByRole('button',{name:'保存',exact:true}).click();await expect.poll(()=>state.posts.length).toBe(1)
 await page.evaluate(async()=>{const path='/src/store/modules/user.ts';(await import(path)).default().sessionReady=false});await expect(dialog(page)).not.toBeVisible();state.releaseSave();await expect(page.getByText('客户保存成功',{exact:true})).toHaveCount(0)
})
for(const width of [1920,1440,768,390])test('customer layout '+width,async({page})=>{
 await page.setViewportSize({width,height:1000});await setup(page)
 if(width===1920){const bounds=await page.locator('.business-filter-form .filter-field .el-input__wrapper,.business-filter-form .filter-field .el-select__wrapper,.business-filter-form .filter-date .el-date-editor,.business-filter-form .filter-action-group>.el-button').evaluateAll(elements=>elements.map(element=>{const box=element.getBoundingClientRect();return {top:box.top,height:box.height}}));expect(bounds.length).toBe(11);expect(Math.max(...bounds.map(box=>box.top))-Math.min(...bounds.map(box=>box.top))).toBeLessThan(1);expect(Math.max(...bounds.map(box=>box.height))-Math.min(...bounds.map(box=>box.height))).toBeLessThan(1)}
 await page.getByRole('button',{name:'添加',exact:true}).click();await expect(dialog(page)).toBeVisible();const box=await dialog(page).boundingBox();expect(box!.x).toBeGreaterThanOrEqual(0);expect(box!.x+box!.width).toBeLessThanOrEqual(width);await page.screenshot({path:'.runtime/customer/dialog-'+width+'.png',fullPage:true,animations:'disabled'})
})
