import {test,expect,type Page} from '@playwright/test'
import {api,login,ok,detail} from '../../tools/local-test-client.mjs'

const cases = [
  {title:'产品管理',path:'/metal/product',label:'产品名称',codeLabel:'产品编号',select:['产品类别','铱环'],fields:{'外径（cm）':'12','内径（cm）':'10','高度（cm）':'2'}},
  {title:'产线管理',path:'/metal/production-line',label:'产线名称',codeLabel:'产线编号',fields:{}},
  {title:'物料管理',path:'/metal/material',label:'规格型号',codeLabel:'物料编码',select:['物料类型','铱粉'],fields:{}},
  {title:'工序管理',path:'/metal/process',label:'工序名称',select:['工序类型','粉料'],fields:{'工序描述':'UI测试描述'}}
]

async function choose(page:Page,label:string,value:string) {
  await page.getByRole('dialog').locator('.el-form-item').filter({hasText:label}).locator('.el-select__wrapper').click()
  await page.getByRole('option',{name:value,exact:true}).click()
}

for(const [index,c] of cases.entries()) test(`${c.title}：真实页面新增、查询、编辑、删除`,async({page,context})=>{
  test.setTimeout(60000)
  const token=await login(),suffix=`${Date.now()}_${index}`,name=`CRUD页面_${suffix}`,updated=`${name}_修改`
  const errors:string[]=[];page.on('pageerror',e=>errors.push(e.message))
  let id:string|undefined,factory:string|undefined
  try {
    if(c.path==='/metal/production-line') factory=String(ok(await api('/metal/factory',token,'POST',{
      factoryName:`CRUD页面厂_${suffix}`,area:'1',address:'临时页面测试地址',productionLineIds:[]
    })).factoryId)
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
    if(c.select) await choose(page,c.select[0],c.select[1])
    if(factory) await choose(page,'所属工厂',`CRUD页面厂_${suffix}`)
    for(const [label,value]of Object.entries(c.fields)) await dialog.getByLabel(label,{exact:true}).fill(value)
    const saving=page.waitForResponse(r=>new URL(r.url()).pathname.endsWith(c.path)&&r.request().method()==='POST')
    await dialog.getByRole('button',{name:'确定',exact:true}).click()
    const result=await (await saving).json();if(result.id!=null)id=String(result.id)
    expect(result.code,result.msg).toBe(200);expect(id).toBeTruthy()
    await expect(dialog).not.toBeVisible()
    await expect(page.getByRole('row').filter({hasText:name})).toBeVisible()
    await page.reload()
    let row=page.getByRole('row').filter({hasText:name});await expect(row).toBeVisible()
    await row.getByRole('button',{name:'编辑',exact:true}).click()
    await expect(dialog.getByLabel(c.label,{exact:true})).toHaveValue(name)
    for(const [label,value]of Object.entries(c.fields)) await expect(dialog.getByLabel(label,{exact:true})).toHaveValue(value)
    await dialog.getByLabel(c.label,{exact:true}).fill(updated)
    await dialog.getByRole('button',{name:'确定',exact:true}).click();await expect(dialog).not.toBeVisible()
    await page.reload();row=page.getByRole('row').filter({hasText:updated});await expect(row).toBeVisible()
    expect((await detail(c.path,id!,token))[c.path==='/metal/material'?'specification':'name']).toBe(updated)
    // A line must be detached from its factory before deletion under the current business rule.
    if(factory) {
      await row.getByRole('button',{name:'删除',exact:true}).click();await page.getByRole('button',{name:'确定',exact:true}).click()
      await expect(page.getByText('请先解除产线的工厂和产品关联：'+updated,{exact:true})).toBeVisible()
      await expect(row).toBeVisible()
      const f=await detail('/metal/factory',factory,token)
      ok(await api('/metal/factory',token,'PUT',{...f,productionLineIds:[]}))
      await page.reload();row=page.getByRole('row').filter({hasText:updated})
    }
    await row.getByRole('button',{name:'删除',exact:true}).click()
    await page.getByRole('button',{name:'取消',exact:true}).click();await expect(row).toBeVisible()
    await row.getByRole('button',{name:'删除',exact:true}).click()
    await page.getByRole('button',{name:'确定',exact:true}).click();await expect(row).toHaveCount(0)
    expect((await api(`${c.path}/${id}`,token)).code).not.toBe(200);id=undefined
    expect(errors).toEqual([])
  } finally {
    const failures:string[]=[]
    if(factory)try{const f=await detail('/metal/factory',factory,token);ok(await api('/metal/factory',token,'PUT',{...f,productionLineIds:[]}))}catch(e){failures.push(String(e))}
    if(id)try{ok(await api(`${c.path}/${id}`,token,'DELETE'))}catch(e){failures.push(`${c.path}/${id}: ${e}`)}
    if(factory)try{ok(await api(`/metal/factory/${factory}`,token,'DELETE'))}catch(e){failures.push(`/metal/factory/${factory}: ${e}`)}
    await api('/logout',token,'POST');expect(failures,'临时记录清理失败').toEqual([])
  }
})
