import{workOrderFixture}from'../../tools/work-order-test-fixture.mjs'
import{registerPageResources}from'./helpers/fixture-registration'
import {test,expect} from '@playwright/test'
// Local integration fixtures; no API interception or disabled captcha.
import {api,login,ok,detail} from '../../tools/local-test-client.mjs'

test('六项真实页面、菜单、报价边界及原型比例',async({page,context})=>{
 test.skip(process.env.LIVE_MASTER!=='1','需要本地真实服务')
 test.setTimeout(90000);page.setDefaultTimeout(10000)
 const f=await workOrderFixture();registerPageResources(page,f);const token=f.admin,suffix=f.suffix,created:Array<[string,string]>=[]
 const create=async(path:string,body:object)=>{const r=ok(await api(path,token,'POST',body));const id=String(r.id??r.factoryId);created.push([path,id]);return id}
 await context.addCookies([{name:'Admin-Token',value:token,url:'http://127.0.0.1:5173'}])
 const choose=async(label:string,value:string)=>{await page.getByRole('dialog').locator('.el-form-item').filter({hasText:label}).locator('.el-select__wrapper').click();await page.getByRole('option',{name:value,exact:true}).click()}
 const save=async()=>{const dialog=page.getByRole('dialog');await dialog.getByRole('button',{name:'确定',exact:true}).click();await expect(dialog).not.toBeVisible()}
 try{
  const factory=await create('/metal/factory',{factoryName:`${f.prefix}厂_${suffix}`,area:'1',address:'验收地址'})
  const role=await create('/system/role',{roleName:`${f.prefix}角色_${suffix}`,factoryId:factory,roleSort:0,status:'0',menuIds:[]})
  const process=await create('/metal/process',{name:`${f.prefix}工序_${suffix}`,processType:1,description:'原描述'})
  const material=await create('/metal/material',{code:`BM${suffix}`,materialType:'铱粉',specification:'旧规格'})
  const product=await create('/metal/product',{name:`${f.prefix}产品_${suffix}`,code:`BP${suffix}`,category:'铱环',dimensions:{outerDiameter:'12',innerDiameter:'10',height:'2'},processes:[{processId:process,loss:'0.123456'}]})
  const line=await create('/metal/production-line',{name:`${f.prefix}线_${suffix}`,code:`BL${suffix}`,factoryId:factory,productIds:[product]})
  const staff=await create('/system/user',{nickName:`${f.prefix}人员_${suffix}`,phonenumber:'137'+suffix.replace(/\D/g,'').slice(-8),factoryId:factory,roleIds:[role],status:'0',password:`Browser${suffix}`})
  await page.setViewportSize({width:1442,height:1026});await page.goto('/metal/factory')
  await expect(page.locator('.prototype-sidebar')).toBeVisible()
  for(const [width,height]of [[1442,1026],[1920,1080]]){
   await page.setViewportSize({width,height})
   await expect.poll(async()=>Math.round((await page.locator('.prototype-sidebar').boundingBox())!.width)).toBe(247)
   expect(Math.round((await page.locator('.prototype-header').boundingBox())!.height)).toBe(82)
   await expect(page.locator('.factory-page')).toHaveCSS('background-color','rgb(248, 251, 255)')
   await page.screenshot({path:`../docs/screenshots/layout-${width}.png`,fullPage:true})
  }
  await page.setViewportSize({width:1442,height:1026})
  await page.getByRole('button',{name:'基础信息管理'}).click();await expect(page.getByRole('link',{name:'产品管理',exact:true})).toHaveCount(0)
  await page.getByRole('button',{name:'基础信息管理'}).click()
  const cases=[['工序管理','/metal/process',`${f.prefix}工序_${suffix}`,'工序描述','页面改描述',process],['物料管理','/metal/material',`BM${suffix}`,'规格型号','页面改规格',material],['产品管理','/metal/product',`${f.prefix}产品_${suffix}`,'理论成品净重（g）','2.123456',product],['产线管理','/metal/production-line',`${f.prefix}线_${suffix}`,'产线名称',`${f.prefix}改线_${suffix}`,line],['人员管理','/system/user',`${f.prefix}人员_${suffix}`,'姓名',`${f.prefix}改人员_${suffix}`,staff],['角色管理','/system/role',`${f.prefix}角色_${suffix}`,'角色名称',`${f.prefix}改角色_${suffix}`,role]]
  for(const [menu,path,name,label,value,id]of cases){
   await page.getByRole('link',{name:menu,exact:true}).click();await expect(page).toHaveURL(new RegExp(path+'$'))
   await expect(page.getByRole('link',{name:menu,exact:true})).toHaveClass(/active/);if(path==='/metal/material'){await page.getByPlaceholder('物料编码',{exact:true}).fill(String(name));await page.getByRole('button',{name:'查询',exact:true}).click()}
   const queryField:Record<string,string>={'/metal/process':'工序名称','/metal/product':'产品名称','/metal/production-line':'产线名称','/system/role':'角色名称'};if(queryField[String(path)]){await page.getByPlaceholder(queryField[String(path)],{exact:true}).fill(String(name));await page.getByRole('button',{name:'查询',exact:true}).click()}
   const row=page.getByRole('row').filter({hasText:name});await expect(row).toBeVisible();await row.getByRole('button',{name:'编辑',exact:true}).click()
   await page.getByRole('dialog').getByLabel(label,{exact:true}).fill(value);await save();await page.reload();if(path==='/metal/material'){await page.getByPlaceholder('物料编码',{exact:true}).fill(String(name));await page.getByRole('button',{name:'查询',exact:true}).click()}
   const d=await detail(path,id,token);const key=label==='工序描述'?'description':label==='规格型号'?'specification':label==='理论成品净重（g）'?'weight':label==='姓名'?'nickName':label==='角色名称'?'roleName':'name';expect(d[key]).toBe(value)
   if(path==='/metal/product'){expect(d.unit).toBeUndefined();await expect(page.getByRole('columnheader',{name:'单位',exact:true})).toHaveCount(0)}
   if(queryField[String(path)]){await page.getByPlaceholder(queryField[String(path)],{exact:true}).fill(String(d.name??d.nickName??d.roleName??d.code));await page.getByRole('button',{name:'查询',exact:true}).click()}
   await expect(page.getByRole('row').filter({hasText:d.name??d.nickName??d.roleName??d.code})).toBeVisible()
   await page.screenshot({path:`../docs/screenshots/master-${path.split('/').pop()}.png`,fullPage:true})
  }
  // Exercise new forms against the real server, including factory/role selections.
  for(const [menu,path,name,fields,selects]of [
   ['物料管理','/metal/material',`UM${suffix}`,{'物料编码':`UM${suffix}`},[['物料类型','铱粉']]],
   ['产品管理','/metal/product',`${f.prefix}新增产品_${suffix}`,{'产品编号':`UP${suffix}`,'产品名称':`${f.prefix}新增产品_${suffix}`,'外径（cm）':'12','内径（cm）':'10','高度（cm）':'2'},[['产品类别','铱环']]],
   ['产线管理','/metal/production-line',`${f.prefix}新增线_${suffix}`,{'产线编号':`UL${suffix}`,'产线名称':`${f.prefix}新增线_${suffix}`},[['所属工厂',`${f.prefix}厂_${suffix}`]]],
   ['角色管理','/system/role',`${f.prefix}新增角色_${suffix}`,{'角色名称':`${f.prefix}新增角色_${suffix}`},[['所属工厂',`${f.prefix}厂_${suffix}`]]],
   ['人员管理','/system/user',`${f.prefix}新增人员_${suffix}`,{'手机号':'135'+suffix.replace(/\D/g,'').slice(-8),'姓名':`${f.prefix}新增人员_${suffix}`,'初始密码':`Browser${suffix}`},[['所属工厂',`${f.prefix}厂_${suffix}`],['角色',`${f.prefix}改角色_${suffix}`]]]
  ] as Array<[string,string,string,Record<string,string>,Array<[string,string]>]>){
   await page.getByRole('link',{name:menu,exact:true}).click();await page.getByRole('button',{name:'添加',exact:true}).click()
   for(const [label,value]of selects){await choose(label,value);if(label==='角色')await page.getByRole('dialog').getByRole('heading').click()}
   for(const [label,value]of Object.entries(fields))await page.getByRole('dialog').getByLabel(label,{exact:true}).fill(value)
   if(path==='/system/role')await page.getByRole('dialog').locator('.el-tree-node__content').filter({has:page.getByText('工序管理',{exact:true})}).locator('.el-checkbox__inner').click()
   await save();if(path==='/metal/material'){await page.getByPlaceholder('物料编码',{exact:true}).fill(String(name));await page.getByRole('button',{name:'查询',exact:true}).click()}const queryField:Record<string,string>={'/metal/process':'工序名称','/metal/product':'产品名称','/metal/production-line':'产线名称','/system/role':'角色名称'};if(queryField[String(path)]){await page.getByPlaceholder(queryField[String(path)],{exact:true}).fill(String(name));await page.getByRole('button',{name:'查询',exact:true}).click()}
   const row=page.getByRole('row').filter({hasText:name});await expect(row).toBeVisible()
   const items=ok(await api(path+'/list?pageSize=100',token)).rows;const item=items.find((r:any)=>[r.name,r.roleName,r.nickName,r.code].includes(name));expect(item).toBeTruthy();created.push([path,item.id])
   if(path==='/system/role')expect((await detail(path,item.id,token)).menuIds.length).toBeGreaterThan(0)
   if(path==='/system/user'){await row.getByRole('button',{name:'重置密码',exact:true}).click();await page.getByPlaceholder('请输入至少8位的新密码').fill(`NewBrowser${suffix}`);await save();const temporary=await login(fields['手机号'],`NewBrowser${suffix}`);await api('/logout',temporary,'POST')}
   if(path==='/system/role'||path==='/system/user'){await row.locator('.el-switch__core').click();await expect.poll(async()=> (await detail(path,item.id,token)).status).toBe('1')}
   await row.getByRole('button',{name:'删除',exact:true}).click();await page.getByRole('button',{name:'确定',exact:true}).click();await expect(row).toHaveCount(0)
   created.splice(created.findIndex(([p,id])=>p===path&&id===item.id),1)
  }
  await page.getByRole('link',{name:'工序管理',exact:true}).click();await page.getByRole('button',{name:'添加',exact:true}).click()
  const dialog=page.getByRole('dialog');const newName=`${f.prefix}新增工序_${suffix}`;await dialog.getByLabel('工序名称',{exact:true}).fill(newName);await choose('工序类型','粉料');await save()
  const newRow=page.getByRole('row').filter({hasText:newName});await expect(newRow).toBeVisible();await newRow.getByRole('button',{name:'删除',exact:true}).click();await page.getByRole('button',{name:'确定',exact:true}).click();await expect(newRow).toHaveCount(0)
  await page.getByRole('link',{name:'报价规则管理',exact:true}).click();await expect(page.getByLabel('基础单价',{exact:true})).toBeVisible()
  await page.getByRole('link',{name:'自动报价单',exact:true}).click();await expect(page.getByRole('button',{name:/计算/})).toBeDisabled()
 }finally{
  // Clear line relations first; delete only this test's temporary records.
  for(const [path,id]of created.filter(([p])=>p==='/metal/production-line')){const d=await detail(path,id,token);ok(await api(path,token,'PUT',{...d,productIds:[]}))}
  for(const [path,id]of created.filter(([p])=>p==='/metal/factory')){const d=await detail(path,id,token);ok(await api(path,token,'PUT',{...d,productionLineIds:[]}))}
  const order=['/system/user','/system/role','/metal/production-line','/metal/product','/metal/material','/metal/process','/metal/factory']
  for(const path of order)for(const [,id]of created.filter(([p])=>p===path))ok(await api(`${path}/${id}`,token,'DELETE'))
  await f.cleanup()
 }
})
