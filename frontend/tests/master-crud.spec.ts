import {selectWarehouseOption} from './helpers/warehouse-select'
import {fixtureEnvironment} from '../../tools/test-fixture-scope.mjs'
import {workOrderFixture} from '../../tools/work-order-test-fixture.mjs'
import {test,expect,type Page} from './helpers/isolated-live-test'
import {api,ok,detail} from '../../tools/local-test-client.mjs'

function assertIsolatedCrudEnvironment(){
 const environment=fixtureEnvironment()
 const current=environment.port==='3323'&&environment.schema==='heavy_metal'&&environment.api==='http://127.0.0.1:8096'
 const previous=environment.port==='3321'&&['heavy_metal_flow','heavy_metal_factory_20261104','heavy_metal_processing_v2'].includes(environment.schema)&&/^http:\/\/127\.0\.0\.1:809[123]$/.test(environment.api)
 if(!current&&!previous)throw Error('真实CRUD测试必须显式指定登记隔离环境，禁止访问主环境')
}

const cases = [
  {title:'产品管理',path:'/metal/product',label:'产品名称',codeLabel:'产品编号',select:['产品类型','环'],fields:{'外径（cm）':'12','内径（cm）':'10','厚（cm）':'2'}},
  {title:'产线管理',path:'/metal/production-line',label:'产线名称',codeLabel:'产线编号',fields:{}},
  {title:'物料管理',path:'/metal/material',label:'规格型号',codeLabel:'物料编码',select:['物料类型','粉料'],fields:{}},
  {title:'工序管理',path:'/metal/process',label:'工序名称',select:['工序类型','融定'],fields:{'工序描述':'UI测试描述'}}
]

async function choose(page:Page,label:string,value:string) {
  const field=page.getByRole('dialog').locator('.el-form-item').filter({has:page.locator('label').filter({hasText:new RegExp('^'+label+'$')})});await selectWarehouseOption(page,field,value)
}

for(const [index,c] of cases.entries()) test(`${c.title}：真实页面新增、查询、编辑、删除`,async({page,context})=>{
  assertIsolatedCrudEnvironment()
  test.setTimeout(60000)
  const productFixture=await workOrderFixture();const token=productFixture.admin,suffix=productFixture.suffix,name=productFixture.prefix+'CRUD',updated=`${name}_修改`
  const filterCurrent=async()=>{if(['/metal/material','/metal/process'].includes(c.path)){await page.locator('.master-toolbar').getByPlaceholder(c.path==='/metal/material'?'物料编码':'工序名称',{exact:true}).fill(c.path==='/metal/material'?'UI'+suffix:name);await page.locator('.master-toolbar').getByRole('button',{name:'查询',exact:true}).click()}};
  const errors:string[]=[];page.on('pageerror',e=>errors.push(e.message))
  let id:string|undefined
  try {
    await context.addCookies([{name:'Admin-Token',value:token,url:'http://127.0.0.1:5173'}])
    await page.goto(c.path)
    await expect(page.locator('.master-table')).toBeVisible()
    await expect(page.locator('.master-toolbar').getByRole('button',{name:'编辑',exact:true})).toBeDisabled()
    await expect(page.locator('.master-toolbar').getByRole('button',{name:'删除',exact:true})).toBeDisabled()
    await page.getByRole('button',{name:'添加',exact:true}).click()
    const dialog=page.getByRole('dialog')
    await dialog.getByRole('button',{name:'确定',exact:true}).click()
    await expect(dialog.locator('.el-form-item__error').first()).toBeVisible()
    await dialog.getByLabel(c.label,{exact:true}).fill(name)
    if(c.codeLabel) await dialog.getByLabel(c.codeLabel,{exact:true}).fill(`UI${suffix}`)
    await choose(page,'所属工厂',productFixture.prefix+'厂A')
    if(c.select) await choose(page,c.select[0],c.select[1])
    if(c.path==='/metal/material'||c.path==='/metal/product') await choose(page,'金属类型','铱')
    for(const [label,value]of Object.entries(c.fields)) await dialog.getByLabel(label,{exact:true}).fill(value)
    const saving=page.waitForResponse(r=>new URL(r.url()).pathname.endsWith(c.path)&&r.request().method()==='POST')
    await dialog.getByRole('button',{name:'确定',exact:true}).click()
    const result=await (await saving).json();if(result.id!=null)id=String(result.id)
    expect(result.code,result.msg).toBe(200);expect(id).toBeTruthy();if(id)productFixture.made[c.path==='/metal/product'?'products':c.path==='/metal/material'?'materials':c.path==='/metal/process'?'processes':'lines'].push(id)
    await expect(dialog).not.toBeVisible();await filterCurrent()
    await expect(page.getByRole('row').filter({hasText:name})).toBeVisible()
    await page.reload();await filterCurrent()
    let row=page.getByRole('row').filter({hasText:name});await expect(row).toBeVisible()
    await row.getByRole('button',{name:'编辑',exact:true}).click()
    await expect(dialog.getByLabel(c.label,{exact:true})).toHaveValue(name)
    for(const [label,value]of Object.entries(c.fields)) await expect(dialog.getByLabel(label,{exact:true})).toHaveValue(c.path==='/metal/product'?Number(value).toFixed(2):value)
    await dialog.getByLabel(c.label,{exact:true}).fill(updated)
    await dialog.getByRole('button',{name:'确定',exact:true}).click();await expect(dialog).not.toBeVisible()
    await page.reload();await filterCurrent();row=page.getByRole('row').filter({hasText:updated});await expect(row).toBeVisible()
    expect((await detail(c.path,id!,token))[c.path==='/metal/material'?'specification':'name']).toBe(updated)
    await row.getByRole('button',{name:'删除',exact:true}).click()
    await page.getByRole('button',{name:'取消',exact:true}).click();await expect(row).toBeVisible()
    await row.getByRole('button',{name:'删除',exact:true}).click()
    await page.getByRole('button',{name:'确定',exact:true}).click();await expect(row).toHaveCount(0)
    expect((await api(`${c.path}/${id}`,token)).code).not.toBe(200);id=undefined
    expect(errors).toEqual([])
  } finally {
    const failures:string[]=[]
    if(id)try{ok(await api(`${c.path}/${id}`,token,'DELETE'))}catch(e){failures.push(`${c.path}/${id}: ${e}`)}
    await productFixture.cleanup();expect(failures,'临时记录清理失败').toEqual([])
  }
})
