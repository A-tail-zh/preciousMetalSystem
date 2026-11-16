import { test, expect, type Page } from '@playwright/test'

// Browser interaction tests use controlled API responses, not a substitute for MySQL integration.
async function setup(page: Page, permissions = ['*:*:*'], area = '12.5000') {
  let rows = [{factoryId:'1',factoryName:'西安航空制造厂',area,address:'西安市高新区',introduction:'工厂简介',version:0,
    updateTime:'2026-10-12 12:00:00',productionLines:[],productionLineIds:[]}]
  let nextId = 2, rejectSave = false
  const saves: {method:string;area:string;factoryName:string}[] = []
  await page.context().addCookies([{name:'Admin-Token',value:'browser-test-token',url:'http://127.0.0.1:5173'}])
  await page.route('**/dev-api/**', async route => {
    const request = route.request(), url = new URL(request.url()), path = url.pathname.replace('/dev-api','')
    let response: any = {code:200,msg:'操作成功'}
    if (path === '/getInfo') response = {...response,user:{userId:1,userName:'admin',nickName:'管理员'},roles:['admin'],permissions}
    else if (path === '/getRouters') response.data = [{path:'/metal',component:'Layout',name:'Metal',children:[{path:'factory',component:'metal/factory/index',name:'Factory',meta:{title:'工厂管理',noCache:true}}]}]
    else if (path === '/metal/factory/list') {
      expect(url.searchParams.get('pageSize')).toBe('16')
      response = {...response,rows,total:rows.length}
    }
    else if (path === '/metal/factory/line-options') response.data = [{productionLineId:'10',productionLineName:'未分配测试产线',factoryId:null}]
    else if (path === '/metal/factory' && ['POST','PUT'].includes(request.method())) {
      if (rejectSave) response = {code:500,msg:'数据已被修改，请刷新后重试'}
      else {
        const data = request.postDataJSON()
        expect(typeof data.area).toBe('string')
        expect(data.productionLineIds).toBeUndefined()
        saves.push({method:request.method(),area:data.area,factoryName:data.factoryName})
        const factoryId = data.factoryId || String(nextId++)
        const row = {...data,factoryId,version:data.factoryId ? data.version+1 : 0,productionLines:[],updateTime:'2026-10-12 13:00:00'}
        rows = [row,...rows.filter(f => f.factoryId !== factoryId)]
      }
    }
    else if (path.startsWith('/metal/factory/') && request.method() === 'DELETE') {
      const ids = path.split('/').pop()!.split(','); rows = rows.filter(f => !ids.includes(f.factoryId))
    }
    else if (path.startsWith('/metal/factory/')) response.data = rows.find(f => f.factoryId === path.split('/').pop())
    else response.data = []
    await route.fulfill({json:response})
  })
  await page.goto('/metal/factory')
  await expect(page.getByRole('cell',{name:'西安航空制造厂',exact:true})).toBeVisible()
  return {saves,failSave:() => {rejectSave = true}}
}

test('原型列表字段、按钮和分页',async ({page}) => {
  const errors: string[] = []; page.on('pageerror',e => errors.push(e.message))
  await setup(page)
  await expect(page.locator('.factory-table th .cell')).toHaveText(['','工厂ID','工厂名称','占地面积（万㎡）','产线名称','详细地址','工厂简介','更新时间','操作'])
  await expect(page.locator('.factory-toolbar button')).toHaveText(['查询','重置','添加','编辑','删除'])
  await expect(page.locator('.factory-toolbar').getByRole('button',{name:'编辑',exact:true})).toBeDisabled()
  await expect(page.locator('.factory-toolbar').getByRole('button',{name:'删除',exact:true})).toBeDisabled()
  await expect(page.locator('.factory-page')).not.toContainText('搜索')
  await expect(page.locator('.factory-pagination')).toContainText('共 1 条')
  await page.screenshot({path:test.info().outputPath('factory-list.png'),fullPage:true})
  expect(errors).toEqual([])
})

test('新增、刷新、编辑回显和删除',async ({page}) => {
  const state = await setup(page)
  await page.getByRole('button',{name:'添加',exact:true}).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByRole('button',{name:'确定',exact:true}).click()
  await expect(dialog.locator('.el-form-item__error')).toHaveText(['请输入工厂名称','请输入占地面积','请输入详细地址'])
  await dialog.getByLabel('工厂名称',{exact:true}).fill('第二工厂')
  await dialog.getByLabel('占地面积（万㎡）',{exact:true}).fill('20.1234')
  await dialog.getByLabel('详细地址',{exact:true}).fill('第二工厂地址')
  await dialog.getByRole('button',{name:'确定',exact:true}).click()
  await expect(dialog).not.toBeVisible()
  expect(state.saves).toEqual([{method:'POST',area:'20.12',factoryName:'第二工厂'}])
  let row = page.getByRole('row').filter({hasText:'第二工厂'})
  await expect(row).toBeVisible()
  await page.reload()
  row = page.getByRole('row').filter({hasText:'第二工厂'})
  await row.getByRole('button',{name:'编辑',exact:true}).click()
  await expect(dialog.getByLabel('占地面积（万㎡）',{exact:true})).toHaveValue('20.12')
  await dialog.getByLabel('工厂名称',{exact:true}).fill('更新后的工厂')
  await page.screenshot({path:test.info().outputPath('factory-edit.png'),fullPage:true,animations:'disabled'})
  await dialog.getByRole('button',{name:'确定',exact:true}).click()
  row = page.getByRole('row').filter({hasText:'更新后的工厂'})
  await expect(row).toBeVisible()
  await row.getByRole('button',{name:'删除',exact:true}).click()
  await page.getByRole('button',{name:'确定',exact:true}).click()
  await expect(row).toHaveCount(0)
})

for (const focusArea of [false,true]) test('历史面积20.1234在'+(focusArea?'仅聚焦失焦':'只改名称')+'后仍原精度提交',async ({page}) => {
  const state = await setup(page,['*:*:*'],'20.1234')
  await page.getByRole('row').filter({hasText:'西安航空制造厂'}).getByRole('button',{name:'编辑',exact:true}).click()
  const dialog = page.getByRole('dialog'), area = dialog.getByLabel('占地面积（万㎡）',{exact:true})
  await expect(area).toHaveValue('20.12')
  if (focusArea) { await area.focus(); await area.press('Tab') }
  else await dialog.getByLabel('工厂名称',{exact:true}).fill('仅改名称的工厂')
  await dialog.getByRole('button',{name:'确定',exact:true}).click()
  await expect(dialog).not.toBeVisible()
  expect(state.saves).toEqual([{method:'PUT',area:'20.1234',factoryName:focusArea?'西安航空制造厂':'仅改名称的工厂'}])
})

test('历史面积实际修改后按两位提交',async ({page}) => {
  const state = await setup(page,['*:*:*'],'20.1234')
  await page.getByRole('row').filter({hasText:'西安航空制造厂'}).getByRole('button',{name:'编辑',exact:true}).click()
  const dialog = page.getByRole('dialog'), area = dialog.getByLabel('占地面积（万㎡）',{exact:true})
  await expect(area).toHaveValue('20.12')
  await area.fill('21.2356'); await area.press('Tab')
  await expect(area).toHaveValue('21.24')
  await dialog.getByRole('button',{name:'确定',exact:true}).click()
  await expect(dialog).not.toBeVisible()
  expect(state.saves).toEqual([{method:'PUT',area:'21.24',factoryName:'西安航空制造厂'}])
})

test('非法面积及保存失败保留输入',async ({page}) => {
  const mock = await setup(page)
  await page.getByRole('row').filter({hasText:'西安航空制造厂'}).getByRole('button',{name:'编辑',exact:true}).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByLabel('占地面积（万㎡）',{exact:true}).fill('0')
  await dialog.getByRole('button',{name:'确定',exact:true}).click()
  await expect(dialog).toContainText('面积须大于0')
  await dialog.getByLabel('占地面积（万㎡）',{exact:true}).fill('12.5')
  await dialog.getByLabel('工厂名称',{exact:true}).fill('未保存的工厂')
  mock.failSave()
  await dialog.getByRole('button',{name:'确定',exact:true}).click()
  await expect(dialog).toBeVisible()
  await expect(dialog.getByLabel('工厂名称',{exact:true})).toHaveValue('未保存的工厂')
  await expect(page.getByText('数据已被修改，请刷新后重试',{exact:true})).toBeVisible()
})

test('只读权限隐藏写入按钮',async ({page}) => {
  await setup(page,['metal:factory:list','metal:factory:query'])
  await expect(page.locator('.factory-toolbar button')).toHaveText(['查询','重置'])
  await expect(page.locator('.factory-table button')).toHaveCount(0)
})
