import {test,expect} from './helpers/isolated-live-test'
import {customerFixture} from '../../tools/customer-test-fixture.mjs'
import {api,ok,detail} from '../../tools/local-test-client.mjs'

test('registered live customer CRUD and layouts',async({page,context})=>{
 test.skip(!process.env.CUSTOMER_LIVE,'需显式启用客户隔离环境测试');test.setTimeout(120000)
 const f=await customerFixture()
 try{
  const base=test.info().project.use.baseURL as string
  await context.addCookies([{name:'Admin-Token',value:f.admin,url:base}]);await page.goto('/metal/customer');await expect(page.locator('.customer-page .el-table')).toBeVisible();await expect(page.getByRole('link',{name:'客户管理',exact:true})).toBeVisible()
  await page.getByRole('button',{name:'添加',exact:true}).click();const dialog=page.getByRole('dialog',{name:'添加客户',exact:true})
  await dialog.getByRole('combobox',{name:'所属工厂',exact:true}).press('ArrowDown');await page.getByRole('option',{name:f.prefix+'厂A',exact:true}).click()
  for(const[label,value]of [['客户名称',f.prefix+'页面客户'],['客户公司名称',f.prefix+'页面公司'],['客户地址','客户地址'],['联络人名称','张三'],['联络人电话','13800138000']])await dialog.getByLabel(label,{exact:true}).fill(value)
  const saved=page.waitForResponse(response=>response.url().endsWith('/metal/customer')&&response.request().method()==='POST');await dialog.getByRole('button',{name:'保存',exact:true}).click();const result=await(await saved).json();expect(result.code).toBe(200);f.made.customers.push(String(result.id));await expect(dialog).not.toBeVisible()
  const row=page.getByRole('row').filter({hasText:f.prefix+'页面公司'});await expect(row).toBeVisible();await row.getByRole('button',{name:'编辑',exact:true}).click();const edit=page.getByRole('dialog',{name:'编辑客户',exact:true});await expect(edit.getByLabel('客户名称',{exact:true})).toHaveValue(f.prefix+'页面客户');await edit.getByLabel('部门',{exact:true}).fill('技术部');await expect(edit.locator('.el-form-item__error')).toHaveCount(0)
  for(const width of [1920,1440,768,390]){await page.setViewportSize({width,height:1000});await expect(edit).toBeVisible();const box=await edit.boundingBox();expect(box!.x).toBeGreaterThanOrEqual(0);expect(box!.x+box!.width).toBeLessThanOrEqual(width);await page.screenshot({path:'.runtime/customer/live-'+width+'.png',fullPage:true,animations:'disabled'})}
  await edit.getByRole('button',{name:'保存',exact:true}).click();await expect(edit).not.toBeVisible();expect((await detail('/metal/customer',result.id,f.admin)).department).toBe('技术部')
  await row.getByRole('button',{name:'删除',exact:true}).click();await page.getByRole('button',{name:'确定',exact:true}).click();await expect(row).toHaveCount(0);expect((await api('/metal/customer/'+result.id,f.admin)).code).not.toBe(200)
  const source=ok(await api('/getRouters',f.userA));expect(JSON.stringify(source.data)).toContain('metal/customer/index')
 }finally{await f.cleanup()}
})
