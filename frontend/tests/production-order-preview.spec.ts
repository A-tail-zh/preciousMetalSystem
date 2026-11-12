import { test, expect, type Page } from '@playwright/test'

async function setup(page: Page, options: { ordinary?: boolean; missing?: boolean; fail?: boolean; paginated?: boolean; unknown?: boolean; listFailure?: boolean; initialOrders?: number; mutations?: boolean; editUnknown?: boolean; deleteUnknown?: boolean; customerAdd?: boolean; customerUnknown?: boolean; customerReadFail?: boolean } = {}) {
  const requests: { path: string; method: string; factory: string | null }[] = []
  let fail = !!options.fail, lookupFailure = !!options.unknown, listFailure = false
  const operations = new Map<string, any>();
  const createdCustomers: any[] = []; let customerReadFail = false
  const saved: any[] = Array.from({ length: options.initialOrders || 0 }, (_, i) => ({ id: 'old-' + i, factoryId: '7', number: '历史测试-' + i, factory: '甲厂', status: 'CREATED' }))
  await page.context().addCookies([{ name: 'Admin-Token', value: 'preview-test', url: String(test.info().project.use.baseURL) }])
  await page.route('**/dev-api/**', async route => {
    const u = new URL(route.request().url()), path = u.pathname.replace('/dev-api', ''), factory = u.searchParams.get('factoryId')
    requests.push({ path, method: route.request().method(), factory })
    let result: any = { code: 200, data: [], rows: [], total: 0 }
    if (path === '/getInfo') result = { code: 200, user: { userId: options.ordinary ? 2 : 1, userName: 'tester', nickName: '测试人员' }, roles: ['staff'], permissions: ['metal:quotation:list', 'metal:work-order:list', 'metal:work-order:query', 'metal:work-order:add', 'metal:customer:list', 'metal:production-line:list', 'metal:product:list', ...(options.missing ? [] : ['system:user:list']), ...(options.customerAdd ? ['metal:customer:add'] : []), ...(options.mutations ? ['metal:work-order:edit','metal:work-order:remove'] : [])] }
    else if (path === '/getRouters') result.data = [{ path: '/metal', component: 'Layout', children: [{ path: 'product', component: 'metal/product/index', meta: { title: '产品管理' } }] }]
    else if (path.endsWith('/factories')) result.data = options.ordinary ? [{ id: '7', name: '甲厂' }] : [{ id: '7', name: '甲厂' }, { id: '8', name: '乙厂' }]
    else if (path === '/metal/customer' && route.request().method() === 'POST') {
      const row = { ...route.request().postDataJSON(), id: '91', version: 0 }; createdCustomers.push(row); customerReadFail = !!options.customerReadFail
      if (options.customerUnknown) { await route.abort('failed'); return }
      result = { code: 200, id: row.id }
    }
    else if (path === '/metal/production-order' && route.request().method() === 'POST') {
      const body = route.request().postDataJSON()
      const existing = saved.find(r => r.requestId === body.requestId)
      const data = existing || { ...body, version: 0, id: String(saved.length + 1), number: 'GD20261109000001', factory: body.factoryId === '7' ? '甲厂' : '乙厂', customer: body.customerId ? '7客户' : '', line: '第一产线', owner: '启用员工', creator: '测试人员', createdAt: '2026-11-09 12:00:00', status: 'CREATED', customerInfo: body.customerId ? { id: 'c', name: '7客户', companyName: '测试公司', address: '测试地址', contactName: '联络人', contactPhone: '123', department: '采购' } : null, products: body.products.map((g: any) => ({ ...g, product: { id: g.productId, name: '关联产品', code: 'P1' }, processes: g.processes.map((p: any) => ({ ...p, processName: p.processId === 'step1' ? '压制' : '烧结' })) })) }
      if (!existing) saved.unshift(data)
      listFailure = !!options.listFailure
      if (options.unknown && !existing) { await route.abort('failed'); return }
      result.data = data
    }
    else if (/^\/metal\/production-order\/\d+$/.test(path) && route.request().method() === 'PUT') {
      const {version, data} = route.request().postDataJSON(), row = saved.find(r => r.id === path.split('/').pop() && r.factoryId === data.factoryId)
      if (operations.has(data.requestId)) result.data = operations.get(data.requestId)
      else if (!row || row.version !== version) result = {code: 409, msg: '工单已被修改，请重新打开核对'}
      else { Object.assign(row, {weight: data.weight, version: version + 1, products: row.products.map((g: any, i: number) => ({...g, quantity: data.products[i].quantity}))}); operations.set(data.requestId, {...row}); result.data = row; if(options.editUnknown){await route.abort('failed'); return} }
    }
    else if (path === '/metal/production-order/batch-delete') {
      const body = route.request().postDataJSON()
      if (!operations.has(body.requestId)) { for (const item of body.orders) { const i = saved.findIndex(r => r.id === item.id && r.factoryId === body.factoryId); if(i >= 0) saved.splice(i,1) }; operations.set(body.requestId,{deleted: true}) }
      result.data = operations.get(body.requestId)
      if(options.deleteUnknown){await route.abort('failed');return}
    }
    else if (path.startsWith('/metal/production-order/operations/')) {
      result.data = operations.get(path.split('/').pop()!)
      if(!result.data) result = {code:404, msg:'未查到操作'}
    }
    else if (path.endsWith('/edit-context')) {
      result.data = saved.find(r => r.id === path.split('/').at(-2) && r.factoryId === factory)
      if(!result.data) result = {code:404, msg:'工单不存在'}
    }
    else if (path === '/metal/production-order/list') {
      if (listFailure) { listFailure = false; await route.fulfill({ status: 500, json: { code: 500, msg: 'list failure' } }); return }
      const rows = saved.filter(r => r.factoryId === factory); result.total = rows.length; const offset = (Number(u.searchParams.get('pageNum') || 1) - 1) * 16; result.rows = rows.slice(offset, offset + 16)
    }
    else if (path.startsWith('/metal/production-order/requests/')) {
      if (lookupFailure) { lookupFailure = false; await route.abort('failed'); return }
      result.data = saved.find(r => r.requestId === path.split('/').pop() && r.factoryId === factory)
      if (!result.data) result = { code: 404, msg: '尚未查到' }
    }
    else if (/^\/metal\/production-order\/\d+$/.test(path)) {
      result.data = saved.find(r => r.id === path.split('/').pop() && r.factoryId === factory)
      if (!result.data) result = { code: 404, msg: '工单不存在' }
    }
    else if (path.endsWith('/list')) {
      if (customerReadFail && path === '/metal/customer/list') { customerReadFail = false; await route.fulfill({ status: 500, json: { code: 500 } }); return }
      if (fail && path === '/metal/product/list') { fail = false; await route.fulfill({ status: 500, json: { code: 500, msg: '加载测试失败' } }); return }
      const rows: Record<string, any[]> = {
        '/metal/customer/list': [{ id: 'c', name: `${factory}客户`, companyName: '测试公司', address: '测试地址', contactName: '联络人', contactPhone: '123', department: '采购' }],
        '/metal/production-line/list': [{ id: 'l1', name: '第一产线', productIds: ['p1'] }, { id: 'l2', name: '第二产线', productIds: ['p2'] }],
        '/system/user/list': [{ id: 'u1', nickName: '启用员工', status: '0' }, { id: 'u2', nickName: '停用员工', status: '1' }],
        '/metal/product/list': [{ id: 'p1', name: '关联产品', code: 'P1', processes: [{ processId: 'step1', processName: '压制' }, { processId: 'step2', processName: '烧结' }] }, { id: 'p2', name: '其他产品', code: 'P2', processes: [] }]
      }
      if (path === '/metal/customer/list') rows[path].push(...createdCustomers.filter(c => c.factoryId === factory))
      result.rows = (rows[path] || []).map(r => ({ ...r, factoryId: factory })); result.total = result.rows.length
      if (options.paginated && path === '/metal/product/list') {
        result.rows = u.searchParams.get('pageNum') === '1' ? Array.from({ length: 100 }, (_, i) => ({ id: `extra-${i}`, name: '额外产品', factoryId: factory })) : result.rows
        result.total = 102
      }
    }
    await route.fulfill({ json: result })
  })
  await page.goto('/metal/production-order')
  return requests
}
async function select(page: Page, name: string, option: string) {
  await page.getByRole('combobox', { name, exact: true }).press('ArrowDown')
  await page.getByRole('option', { name: option, exact: true }).click()
}
async function open(page: Page) {
  await select(page, '所属工厂', '甲厂')
  await page.getByRole('button', { name: '添加', exact: true }).click()
  await expect(page.getByRole('button', { name: '确认', exact: true })).toBeEnabled()
}
async function configure(page: Page) {
  await select(page, '产线', '第一产线')
  await select(page, '负责人', '启用员工')
  await select(page, '选择产品 1', '关联产品（P1）')
}

test('persistent order, precision, factory query, read-only snapshots and left aligned weight', async ({ page }) => {
  const requests = await setup(page, { paginated: true })
  await expect(page.getByRole('button', { name: '添加', exact: true })).toBeDisabled()
  const nav = page.locator('.nav-group')
  expect((await nav.allTextContents()).join('|')).toMatch(/报价管理.*工单管理/)
  await open(page)
  await expect(page.getByText('客户公司名称', { exact: true })).toHaveCount(0)
  await select(page, '客户名称', '7客户')
  await expect(page.getByText('客户公司名称', { exact: true })).toBeVisible()
  await page.getByRole('textbox', { name: '客户来料净重（g）', exact: true }).fill('25')
  await select(page, '加工类型', '直接定制')
  await expect(page.getByRole('textbox', { name: '客户来料净重（g）', exact: true })).toHaveCount(0)
  await select(page, '加工类型', '来料定制')
  await expect(page.getByRole('textbox', { name: '客户来料净重（g）', exact: true })).toHaveValue('')
  await page.getByRole('button', { name: '确认', exact: true }).click()
  await expect(page.getByText('请选择产线、开单日期和负责人')).toBeVisible()
  await configure(page)
  await expect(page.getByRole('cell', { name: '压制', exact: true })).toBeVisible()
  await expect(page.getByRole('cell', { name: '烧结', exact: true })).toBeVisible()
  await page.getByRole('spinbutton', { name: '产品数量 1', exact: true }).fill('1.5')
  await page.getByRole('button', { name: '确认', exact: true }).click()
  await expect(page.getByText('产品数量须为1至2147483647的整数')).toBeVisible()
  await page.getByRole('spinbutton', { name: '产品数量 1', exact: true }).fill('2')
  await page.getByPlaceholder('选择开始时间', { exact: true }).first().fill('2026-11-10 10:00:00')
  await page.getByPlaceholder('选择开始时间', { exact: true }).first().press('Enter')
  await page.getByPlaceholder('选择结束时间', { exact: true }).first().fill('2026-11-09 10:00:00')
  await page.getByPlaceholder('选择结束时间', { exact: true }).first().press('Enter')
  await page.getByRole('button', { name: '确认', exact: true }).click()
  await expect(page.getByText('工序结束时间不得早于开始时间')).toBeVisible()
  await page.getByPlaceholder('选择结束时间', { exact: true }).first().fill('2026-11-11 10:00:00')
  await page.getByPlaceholder('选择结束时间', { exact: true }).first().press('Enter')
  await expect(page.locator('.el-message')).toHaveCount(0)
  await page.getByRole('heading', { name: '基础信息', exact: true }).scrollIntoViewIfNeeded()
  await page.screenshot({ path: '.runtime/production-order-customer-20261109/dialog-desktop.png', fullPage: true })
  await page.getByRole('textbox', { name: '客户来料净重（g）', exact: true }).fill('99999999999999.12')
  await expect(page.locator('.incoming-weight input')).toHaveCSS('text-align', 'left')
  await page.getByRole('button', { name: '确认', exact: true }).click()
  await expect(page.getByRole('cell', { name: 'GD20261109000001', exact: true })).toBeVisible()
  await expect(page.getByRole('dialog', { name: '添加工单', exact: true })).toHaveCount(0)
  await page.reload()
  await expect(page).toHaveURL(/factoryId=7/)
  await expect(page.getByRole('cell', { name: 'GD20261109000001', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '查看', exact: true }).click()
  await expect(page.getByRole('dialog', { name: '查看工单', exact: true })).toBeVisible()
  await expect(page.getByRole('textbox', { name: '客户来料净重（g）', exact: true })).toHaveValue('99999999999999.12')
  await expect(page.getByRole('textbox', { name: '客户来料净重（g）', exact: true })).toBeDisabled()
  await expect(page.getByRole('textbox', { name: '客户公司名称', exact: true })).toHaveValue('测试公司')
  await expect(page.getByRole('button', { name: '确认', exact: true })).toHaveCount(0)
  await page.getByRole('button', { name: '关闭', exact: true }).click()
  await select(page, '所属工厂', '乙厂')
  await expect(page.getByRole('cell', { name: 'GD20261109000001', exact: true })).toHaveCount(0)
  expect(requests.filter(r => r.method === 'POST').map(r => r.path)).toEqual(['/metal/production-order'])
  expect(requests.some(r => /work-order|warehouse/.test(r.path))).toBe(false)
})

test('line confirmation, minimum groups, cancel confirmation and mobile layout', async ({ page }) => {
  await setup(page); await open(page); await configure(page)
  await page.getByRole('button', { name: '＋ 添加产品', exact: true }).click()
  await expect(page.getByRole('combobox', { name: '选择产品 2', exact: true })).toBeVisible()
  await select(page, '产线', '第二产线')
  await page.getByRole('button', { name: '更换', exact: true }).click()
  await expect(page.getByRole('dialog', { name: '更换产线', exact: true })).toHaveCount(0)
  await expect(page.getByRole('combobox', { name: '选择产品 2', exact: true })).toHaveCount(0)
  await expect(page.getByRole('cell', { name: '压制', exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '移除', exact: true })).toBeDisabled()
  await page.setViewportSize({ width: 390, height: 844 })
  const fields = page.locator('.form-grid').first().locator('.el-form-item')
  const a = await fields.nth(0).boundingBox(), b = await fields.nth(1).boundingBox()
  expect(b!.y).toBeGreaterThan(a!.y + a!.height)
  await page.screenshot({ path: '.runtime/production-order-customer-20261109/dialog-mobile.png', fullPage: true })
  await page.getByRole('dialog', { name: '添加工单', exact: true }).getByRole('button', { name: '取消', exact: true }).click()
  await expect(page.getByText('放弃尚未保存的工单内容？')).toBeVisible()
  await page.getByRole('button', { name: '继续填写', exact: true }).click()
  await expect(page.getByRole('dialog', { name: '添加工单', exact: true })).toBeVisible()
  await page.getByRole('dialog', { name: '添加工单', exact: true }).getByRole('button', { name: '取消', exact: true }).click()
  await page.getByRole('button', { name: '放弃', exact: true }).click()
  await expect(page.getByRole('dialog', { name: '添加工单', exact: true })).toHaveCount(0)
})

test('ordinary factory fixed, failure retry and only active staff', async ({ page }) => {
  const requests = await setup(page, { ordinary: true, fail: true })
  await expect(page.getByRole('combobox', { name: '所属工厂', exact: true })).toBeDisabled()
  await page.getByRole('button', { name: '添加', exact: true }).click()
  await expect(page.getByText(/资料加载失败/)).toBeVisible()
  await expect(page.getByRole('button', { name: '确认', exact: true })).toBeDisabled()
  await page.getByRole('button', { name: '重试', exact: true }).click()
  await expect(page.getByRole('button', { name: '确认', exact: true })).toBeEnabled()
  await page.getByRole('combobox', { name: '负责人', exact: true }).press('ArrowDown')
  await expect(page.getByRole('option', { name: '停用员工', exact: true })).toHaveCount(0)
  await expect(page.getByRole('option', { name: '启用员工', exact: true })).toBeVisible()
  expect(requests.filter(r => r.path.endsWith('/list')).every(r => r.factory === '7')).toBe(true)
})

test('missing catalog permission does not auto-grant or use mock fallback', async ({ page }) => {
  const requests = await setup(page, { missing: true }); await select(page, '所属工厂', '甲厂')
  await page.getByRole('button', { name: '添加', exact: true }).click()
  await expect(page.getByText(/缺少人员查看权限/)).toBeVisible()
  await expect(page.getByRole('button', { name: '确认', exact: true })).toBeDisabled()
  expect(requests.some(r => r.path === '/system/user/list')).toBe(false)
})

test('late factory response cannot replace current catalogs and old route stays retired', async ({ page }) => {
  await setup(page)
  let release!: () => void
  const delayed = new Promise<void>(resolve => { release = resolve })
  await page.route('**/dev-api/metal/customer/list?**', async route => {
    if (new URL(route.request().url()).searchParams.get('factoryId') !== '7') { await route.fallback(); return }
    await delayed
    await route.fulfill({ json: { code: 200, total: 1, rows: [{ id: 'old', factoryId: '7', name: '迟到的甲厂客户' }] } })
  })
  await select(page, '所属工厂', '甲厂')
  await page.getByRole('button', { name: '添加', exact: true }).click()
  await expect(page.getByRole('button', { name: '确认', exact: true })).toBeDisabled()
  await page.getByRole('dialog', { name: '添加工单', exact: true }).getByRole('button', { name: '取消', exact: true }).click()
  await select(page, '所属工厂', '乙厂')
  await page.getByRole('button', { name: '添加', exact: true }).click()
  await expect(page.getByRole('button', { name: '确认', exact: true })).toBeEnabled()
  release()
  await select(page, '客户名称', '8客户')
  await expect(page.getByRole('option', { name: '迟到的甲厂客户', exact: true })).toHaveCount(0)
  await page.goto('/metal/work-order')
  await expect(page.getByText('404', { exact: true })).toBeVisible()
})




test('unknown save freezes original request and verifies without another write', async ({ page }) => {
  const requests = await setup(page, { unknown: true }); await open(page); await configure(page)
  await page.getByRole('button', { name: '确认', exact: true }).click()
  await expect(page.getByText('暂时无法核实保存结果，请稍后点击“核实保存结果”')).toBeVisible()
  await expect(page.getByRole('combobox', { name: '负责人', exact: true })).toBeDisabled()
  await page.getByRole('button', { name: '核实保存结果', exact: true }).click()
  await expect(page.getByRole('cell', { name: 'GD20261109000001', exact: true })).toBeVisible()
  expect(requests.filter(r => r.method === 'POST')).toHaveLength(1)
})

test('saved order with failed list refresh is not submitted again', async ({ page }) => {
  const requests = await setup(page, { listFailure: true, initialOrders: 17 }); await select(page, '所属工厂', '甲厂'); await page.getByRole('button', { name: '下一页', exact: true }).click(); await expect(page.getByRole('cell', { name: '历史测试-16', exact: true })).toBeVisible(); await page.getByRole('button', { name: '添加', exact: true }).click(); await expect(page.getByRole('button', { name: '确认', exact: true })).toBeEnabled(); await configure(page)
  await page.getByRole('button', { name: '确认', exact: true }).click()
  await expect(page.getByText('已保存，列表刷新失败')).toBeVisible()
  await expect(page.getByRole('dialog', { name: '添加工单', exact: true })).toHaveCount(0)
  await page.getByRole('button', { name: '重试', exact: true }).click()
  await expect(page.getByRole('cell', { name: 'GD20261109000001', exact: true })).toBeVisible()
  expect(requests.filter(r => r.method === 'POST')).toHaveLength(1)
})


async function createCustomer(page: Page) {
  await page.getByRole('button', { name: '添加客户', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '添加客户', exact: true })
  await expect(dialog.getByRole('textbox', { name: '所属工厂', exact: true })).toHaveValue('甲厂')
  for (const [label, value] of [['客户名称','新增客户'],['客户公司名称','新增公司'],['客户地址','新增地址'],['联络人名称','新联系人'],['联络人电话','13900000000'],['部门','采购部']])
    await dialog.getByRole('textbox', { name: label, exact: true }).fill(value)
  await dialog.getByRole('button', { name: '保存', exact: true }).click()
  return dialog
}

test('inline customer is independently saved and selected without losing order inputs', async ({ page }) => {
  const requests = await setup(page, { customerAdd: true }); await open(page); await configure(page)
  await expect(page.getByRole('button', { name: '刷新配置', exact: true })).toHaveCount(0)
  await page.getByRole('textbox', { name: '客户来料净重（g）', exact: true }).fill('123.456')
  await createCustomer(page)
  await expect(page.getByRole('dialog', { name: '添加客户', exact: true })).toHaveCount(0)
  await expect(page.getByRole('dialog', { name: '添加工单', exact: true }).getByRole('textbox', { name: '客户公司名称', exact: true })).toHaveValue('新增公司')
  await expect(page.getByRole('textbox', { name: '客户来料净重（g）', exact: true })).toHaveValue('123.456')
  await expect(page.getByRole('cell', { name: '压制', exact: true })).toBeVisible()
  await page.getByRole('dialog', { name: '添加工单', exact: true }).getByRole('button', { name: '取消', exact: true }).click()
  await page.getByRole('button', { name: '放弃', exact: true }).click()
  await page.getByRole('button', { name: '添加', exact: true }).click()
  await expect(page.getByRole('button', { name: '确认', exact: true })).toBeEnabled()
  await select(page, '客户名称', '新增客户')
  expect(requests.filter(r => r.method === 'POST').map(r => r.path)).toEqual(['/metal/customer'])
})

test('created customer read failure retries only reads', async ({ page }) => {
  const requests = await setup(page, { customerAdd: true, customerReadFail: true }); await open(page)
  await createCustomer(page)
  await expect(page.getByText(/客户已创建，资料加载失败/)).toBeVisible()
  await expect(page.getByRole('button', { name: '添加客户', exact: true })).toBeDisabled()
  await page.getByRole('button', { name: '重试', exact: true }).click()
  await expect(page.getByRole('dialog', { name: '添加工单', exact: true }).getByRole('textbox', { name: '客户公司名称', exact: true })).toHaveValue('新增公司')
  expect(requests.filter(r => r.method === 'POST')).toHaveLength(1)
})

test('unknown customer creation never retries or selects a same-name match automatically', async ({ page }) => {
  const requests = await setup(page, { customerAdd: true, customerUnknown: true }); await open(page)
  const dialog = await createCustomer(page)
  await expect(dialog.getByText(/客户创建结果待核实/)).toBeVisible()
  await expect(dialog.getByRole('button', { name: '保存', exact: true })).toBeDisabled()
  await dialog.getByRole('button', { name: '查询本厂客户', exact: true }).click()
  await expect(dialog.getByRole('button', { name: '使用此客户', exact: true })).toBeVisible()
  await expect(dialog).toBeVisible()
  await dialog.getByRole('button', { name: '使用此客户', exact: true }).click()
  await page.getByRole('button', { name: '使用', exact: true }).click()
  await expect(page.getByRole('dialog', { name: '添加工单', exact: true }).getByRole('textbox', { name: '客户公司名称', exact: true })).toHaveValue('新增公司')
  expect(requests.filter(r => r.method === 'POST')).toHaveLength(1)
})

test('customer cancel confirms dirty input and add permission is separate', async ({ page }) => {
  await setup(page); await open(page)
  await expect(page.getByRole('button', { name: '添加客户', exact: true })).toHaveCount(0)
  await page.unrouteAll(); await setup(page, { customerAdd: true }); await open(page)
  await page.getByRole('button', { name: '添加客户', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '添加客户', exact: true })
  await dialog.getByRole('button', { name: '保存', exact: true }).click()
  await expect(dialog.getByText('请输入客户名称', { exact: true })).toBeVisible()
  await dialog.getByRole('textbox', { name: '客户名称', exact: true }).fill('未保存')
  await dialog.getByRole('button', { name: '取消', exact: true }).click()
  await page.getByRole('button', { name: '继续填写', exact: true }).click()
  await expect(dialog.getByRole('textbox', { name: '客户名称', exact: true })).toHaveValue('未保存')
})

test('save preflight updates changed selection without submitting, and retains process times', async ({ page }) => {
  const requests = await setup(page); await open(page); await configure(page)
  await page.getByPlaceholder('选择开始时间', { exact: true }).first().fill('2026-11-10 10:00:00')
  await page.getByPlaceholder('选择开始时间', { exact: true }).first().press('Enter')
  await page.route('**/dev-api/metal/product/list?**', route => route.fulfill({ json: { code: 200, total: 1, rows: [{ id: 'p1', factoryId: '7', code: 'P1', name: '更新产品', processes: [{ processId: 'step2', processName: '烧结新名称' }, { processId: 'step1', processName: '压制' }] }] } }))
  await page.getByRole('button', { name: '确认', exact: true }).click()
  await expect(page.getByText('所选资料已更新，请核对后再次确认保存')).toHaveCount(0)
  await expect(page.getByPlaceholder('选择开始时间', { exact: true }).nth(1)).toHaveValue('2026-11-10 10:00:00')
  expect(requests.filter(r => r.method === 'POST')).toHaveLength(0)
  await page.getByRole('button', { name: '确认', exact: true }).click()
  await expect(page.getByRole('cell', { name: 'GD20261109000001', exact: true })).toBeVisible()
})

test('focus refresh invalidates owner and protects removed process times until confirmed', async ({ page }) => {
  await setup(page); await open(page); await configure(page)
  await page.getByPlaceholder('选择开始时间', { exact: true }).first().fill('2026-11-10 10:00:00')
  await page.getByPlaceholder('选择开始时间', { exact: true }).first().press('Enter')
  await page.route('**/dev-api/metal/product/list?**', route => route.fulfill({ json: { code: 200, total: 1, rows: [{ id: 'p1', factoryId: '7', code: 'P1', name: '关联产品', processes: [{ processId: 'step2', processName: '烧结' }] }] } }))
  await page.evaluate(() => window.dispatchEvent(new Event('focus')))
  await expect(page.getByText(/最新配置移除了压制/)).toBeVisible()
  await page.getByRole('button', { name: '保留并核对', exact: true }).click()
  await expect(page.getByPlaceholder('选择开始时间', { exact: true }).first()).toHaveValue('2026-11-10 10:00:00')
  await expect(page.getByRole('button', { name: '确认', exact: true })).toBeDisabled()
  await page.getByRole('combobox', { name: '选择产品 1', exact: true }).press('ArrowDown')
  await page.getByRole('button', { name: '应用新配置', exact: true }).click()
  await expect(page.getByRole('cell', { name: '压制', exact: true })).toHaveCount(0)
  await page.route('**/dev-api/system/user/list?**', route => route.fulfill({ json: { code: 200, rows: [], total: 0 } }))
  await page.evaluate(() => window.dispatchEvent(new Event('focus')))
  await expect(page.getByText(/负责人已失效，请重新选择/)).toBeVisible()
  await expect(page.getByRole('button', { name: '确认', exact: true })).toBeDisabled()
})

test('return events coalesce and pause while nested customer dialog is open', async ({ page }) => {
  const requests = await setup(page, { customerAdd: true }); await open(page)
  const count = () => requests.filter(r => r.path === '/metal/customer/list').length
  const before = count()
  await page.evaluate(() => { window.dispatchEvent(new Event('focus')); document.dispatchEvent(new Event('visibilitychange')); window.dispatchEvent(new Event('focus')) })
  await expect.poll(count).toBe(before + 1)
  await page.getByRole('button', { name: '添加客户', exact: true }).click()
  const paused = count()
  await page.evaluate(() => { window.dispatchEvent(new Event('focus')); document.dispatchEvent(new Event('visibilitychange')) })
  await page.getByRole('dialog', { name: '添加客户', exact: true }).getByRole('textbox', { name: '客户名称', exact: true }).fill('保留草稿')
  expect(count()).toBe(paused)
  await expect(page.getByRole('button', { name: '刷新配置', exact: true })).toHaveCount(0)
})

test('server configuration conflict refreshes catalogs without resubmission', async ({ page }) => {
  const requests = await setup(page); await open(page); await configure(page)
  let posts = 0
  await page.route('**/dev-api/metal/production-order', async route => {
    posts++
    await page.route('**/dev-api/metal/product/list?**', r => r.fulfill({ json: { code: 200, total: 1, rows: [{ id: 'p1', factoryId: '7', code: 'P1', name: '服务端更新产品', processes: [{ processId: 'step1', processName: '压制新名称' }] }] } }))
    await route.fulfill({ json: { code: 409, msg: '产品工序配置已变化' } })
  })
  const before = requests.filter(r => r.path === '/metal/customer/list').length
  await page.getByRole('button', { name: '确认', exact: true }).click()
  await expect(page.getByRole('cell', { name: '压制新名称', exact: true })).toBeVisible()
  expect(posts).toBe(1)
  expect(requests.filter(r => r.path === '/metal/customer/list').length).toBe(before + 2)
  await expect(page.getByRole('dialog', { name: '添加工单', exact: true })).toBeVisible()
})

async function savedOrder(page: Page) {
  await open(page); await configure(page)
  await page.getByRole('button', {name:'确认',exact:true}).click()
  await expect(page.getByRole('cell',{name:'GD20261109000001',exact:true})).toBeVisible()
}
test('edit and delete persist with factory and version, and edit keeps the order number', async ({page}) => {
  const requests = await setup(page,{mutations:true}); await savedOrder(page)
  const row = page.getByRole('row').filter({hasText:'GD20261109000001'})
  await row.getByRole('button',{name:'编辑',exact:true}).click()
  const dialog = page.getByRole('dialog',{name:'编辑工单',exact:true})
  await expect(dialog.getByRole('button',{name:'确认',exact:true})).toBeEnabled()
  await dialog.getByRole('textbox',{name:'客户来料净重（g）',exact:true}).fill('987.12')
  await dialog.getByRole('spinbutton',{name:'产品数量 1',exact:true}).fill('3')
  await dialog.getByRole('button',{name:'确认',exact:true}).click()
  await expect(dialog).not.toBeVisible(); await page.reload()
  await expect(row).toContainText('987.12')
  await row.getByRole('button',{name:'删除',exact:true}).click()
  await page.getByRole('button',{name:'取消',exact:true}).click()
  expect(requests.filter(r=>r.path.endsWith('batch-delete'))).toHaveLength(0)
  await row.getByRole('button',{name:'删除',exact:true}).click()
  await page.getByRole('dialog',{name:'删除工单',exact:true}).getByRole('button',{name:'删除',exact:true}).click()
  await expect(row).toHaveCount(0)
  expect(requests.filter(r=>r.method==='PUT')).toHaveLength(1)
})
test('unknown edit and delete results are verified without duplicate writes', async ({page}) => {
  const requests = await setup(page,{mutations:true,editUnknown:true,deleteUnknown:true}); await savedOrder(page)
  const row = page.getByRole('row').filter({hasText:'GD20261109000001'})
  await row.getByRole('button',{name:'编辑',exact:true}).click()
  const dialog = page.getByRole('dialog',{name:'编辑工单',exact:true})
  await expect(dialog.getByRole('button',{name:'确认',exact:true})).toBeEnabled()
  await dialog.getByRole('textbox',{name:'客户来料净重（g）',exact:true}).fill('2')
  await dialog.getByRole('button',{name:'确认',exact:true}).click(); await expect(dialog).not.toBeVisible()
  await row.getByRole('button',{name:'删除',exact:true}).click()
  await page.getByRole('dialog',{name:'删除工单',exact:true}).getByRole('button',{name:'删除',exact:true}).click()
  await expect(row).toHaveCount(0)
  expect(requests.filter(r=>r.method==='PUT')).toHaveLength(1)
  expect(requests.filter(r=>r.path.endsWith('batch-delete'))).toHaveLength(1)
})
test('view permission does not expose editing or deleting', async ({page}) => {
  await setup(page); await savedOrder(page)
  await expect(page.getByRole('button',{name:'编辑',exact:true})).toHaveCount(0)
  await expect(page.getByRole('button',{name:'删除',exact:true})).toHaveCount(0)
})
test('stale edit keeps inputs and asks to reopen without a second write', async ({page}) => {
  await setup(page,{mutations:true}); await savedOrder(page)
  await page.getByRole('row').filter({hasText:'GD20261109000001'}).getByRole('button',{name:'编辑',exact:true}).click()
  const dialog = page.getByRole('dialog',{name:'编辑工单',exact:true})
  await expect(dialog.getByRole('button',{name:'确认',exact:true})).toBeEnabled()
  let writes=0
  await page.route('**/dev-api/metal/production-order/1', async route => {writes++;await route.fulfill({json:{code:409,msg:'工单已被修改，请重新打开核对'}})})
  await dialog.getByRole('textbox',{name:'客户来料净重（g）',exact:true}).fill('123')
  await dialog.getByRole('button',{name:'确认',exact:true}).click()
  await expect(dialog.getByText('工单已被修改，请重新打开核对',{exact:true})).toBeVisible()
  await expect(dialog.getByRole('textbox',{name:'客户来料净重（g）',exact:true})).toHaveValue('123.00')
  expect(writes).toBe(1)
})


test('incoming weight uses two decimals and row actions use solid buttons', async ({ page }) => {
  await setup(page, { mutations: true }); await open(page); await configure(page)
  const input = page.getByRole('textbox', { name: '客户来料净重（g）', exact: true })
  await input.fill('1.234')
  await page.getByRole('button', { name: '确认', exact: true }).click()
  await expect(page.getByText('客户来料净重须为非负数，最多14位整数和2位小数')).toBeVisible()
  await input.fill('1.2'); await input.blur()
  await expect(input).toHaveValue('1.20')
  await page.getByRole('button', { name: '确认', exact: true }).click()
  const row = page.getByRole('row').filter({ hasText: 'GD20261109000001' })
  await expect(row).toContainText('1.20')
  await expect(row.getByRole('button', { name: '查看', exact: true })).toHaveCSS('background-color', 'rgb(64, 158, 255)')
  await expect(row.getByRole('button', { name: '编辑', exact: true })).toHaveCSS('background-color', 'rgb(238, 155, 88)')
  await expect(row.getByRole('button', { name: '删除', exact: true })).toHaveCSS('background-color', 'rgb(245, 108, 108)')
  await expect(page.getByRole('dialog', { name: '添加工单', exact: true })).toHaveCount(0)
  await row.getByRole('button', { name: '查看', exact: true }).scrollIntoViewIfNeeded()
  await page.screenshot({ path: '.runtime/order-polish-20261109/order-buttons.png', fullPage: true })
})
