import { test, expect, type Page } from '@playwright/test'
import { readFile } from 'node:fs/promises'

async function setup(page: Page, ordinary = false, permissions = ['metal:inventory:list', 'metal:inventory:query', 'metal:inventory:adjust', 'metal:inventory:remove', 'metal:inventory:export']) {
  const businessRequests: string[] = []
  await page.context().addCookies([{ name: 'Admin-Token', value: 'storage-inventory-test', url: String(test.info().project.use.baseURL) }])
  await page.route('**/dev-api/**', async route => {
    const path = new URL(route.request().url()).pathname.replace('/dev-api', '')
    if (path === '/getInfo') { await route.fulfill({ json: { code: 200, user: { userId: ordinary ? 2 : 1, userName: 'inventory-preview', nickName: '库存预览人员', factoryId: ordinary ? '7' : null }, roles: ['staff'], permissions } }); return }
    if (path === '/getRouters') { await route.fulfill({ json: { code: 200, data: [] } }); return }
    businessRequests.push(path); await route.abort('failed')
  })
  await page.goto('/metal/storage-inventory')
  await expect(page.getByRole('tab', { name: '原材料', exact: true })).toBeVisible()
  if (!ordinary) {
    await page.getByRole('combobox', { name: '所属工厂', exact: true }).press('ArrowDown')
    await page.getByRole('option', { name: '演示工厂一', exact: true }).click()
  }
  await expect(page.getByRole('cell', { name: 'M001', exact: true })).toBeVisible()
  return businessRequests
}

const firstRaw = (page: Page) => page.getByRole('row').filter({ has: page.getByRole('cell', { name: 'M001', exact: true }) })

test('inventory tabs keep the document columns and raw inventory has no delete', async ({ page }) => {
  const requests = await setup(page)
  await page.screenshot({path: test.info().outputPath('inventory-desktop.png'), fullPage: true, animations: 'disabled'})
  await expect(page.getByRole('columnheader', { name: '物料类型名称', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '删除', exact: true })).toHaveCount(0)
  await firstRaw(page).getByRole('button', { name: '查看详情', exact: true }).click()
  const detail = page.getByRole('dialog', { name: '原材料库存详情', exact: true })
  await expect(detail).toContainText('粉料'); await expect(detail).toContainText('原料库')
  await detail.getByRole('button', { name: '关闭', exact: true }).click()
  await page.getByRole('tab', { name: '成品', exact: true }).click()
  await expect(page.getByRole('columnheader', { name: '数量', exact: true })).toBeVisible()
  await expect(page.getByRole('columnheader', { name: '物料编码', exact: true })).toHaveCount(0)
  await page.getByRole('tab', { name: '半成品', exact: true }).click()
  await expect(page.getByRole('columnheader', { name: '来源工序', exact: true })).toBeVisible()
  await expect(page.getByRole('columnheader', { name: '数量 / 参考', exact: true })).toBeVisible()
  await expect(page.getByRole('cell', { name: '11（参考）', exact: true })).toBeVisible()
  expect(requests).toEqual([])
})

test('inventory adjustment validates two decimals and reason without changing another factory', async ({ page }) => {
  const requests = await setup(page)
  await firstRaw(page).getByRole('button', { name: '库存调整', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '库存调整', exact: true })
  await dialog.getByRole('textbox', { name: '调整后净重（g）', exact: true }).fill('12.345')
  await dialog.getByRole('button', { name: '确认调整', exact: true }).click()
  await expect(dialog.getByText('净重须为非负数，最多14位整数和2位小数', { exact: true })).toBeVisible()
  await expect(dialog.getByText('请输入调整原因', { exact: true })).toBeVisible()
  await dialog.getByRole('textbox', { name: '调整后净重（g）', exact: true }).fill('12.34')
  await dialog.getByRole('textbox', { name: '调整原因', exact: true }).fill('演示复称调整')
  await dialog.getByRole('button', { name: '确认调整', exact: true }).click()
  await expect(dialog).not.toBeVisible(); await expect(firstRaw(page)).toContainText('12.34')
  await page.getByRole('combobox', { name: '所属工厂', exact: true }).press('ArrowDown')
  await page.getByRole('option', { name: '演示工厂二', exact: true }).click()
  await expect(firstRaw(page)).toContainText('100.00')
  await page.reload()
  await page.getByRole('combobox', { name: '所属工厂', exact: true }).press('ArrowDown')
  await page.getByRole('option', { name: '演示工厂一', exact: true }).click()
  await expect(firstRaw(page)).toContainText('100.00')
  expect(requests).toEqual([])
})

test('dirty adjustment asks before closing and product deletion is preview only', async ({ page }) => {
  const requests = await setup(page)
  await firstRaw(page).getByRole('button', { name: '库存调整', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '库存调整', exact: true })
  await dialog.getByRole('textbox', { name: '调整后净重（g）', exact: true }).fill('1')
  await dialog.getByRole('button', { name: '取消', exact: true }).click()
  await page.getByRole('button', { name: '继续填写', exact: true }).click()
  await expect(dialog.getByRole('textbox', { name: '调整后净重（g）', exact: true })).toHaveValue('1')
  await dialog.getByRole('button', { name: '取消', exact: true }).click(); await page.getByRole('button', { name: '放弃', exact: true }).click()
  await page.getByRole('tab', { name: '成品', exact: true }).click()
  const rows = page.locator('.storage-inventory-page .el-table__body-wrapper tbody tr')
  await expect(rows).toHaveCount(3)
  await rows.first().getByRole('button', { name: '删除', exact: true }).click()
  await page.getByRole('dialog', { name: '删除库存', exact: true }).getByRole('button', { name: '取消', exact: true }).click()
  await expect(rows).toHaveCount(3)
  await rows.first().getByRole('button', { name: '删除', exact: true }).click()
  await page.getByRole('dialog', { name: '删除库存', exact: true }).getByRole('button', { name: '删除', exact: true }).click()
  await expect(rows).toHaveCount(2)
  expect(requests).toEqual([])
})

test('inventory filters export all matching rows and reset', async ({ page }) => {
  const requests = await setup(page)
  await page.getByRole('textbox', { name: '物料编码筛选', exact: true }).fill('M001')
  await page.getByRole('button', { name: '查询', exact: true }).click()
  const rows = page.locator('.storage-inventory-page .el-table__body-wrapper tbody tr')
  await expect(rows).toHaveCount(1)
  const download = page.waitForEvent('download'); await page.getByRole('button', { name: '导出', exact: true }).click()
  const file = await (await download).path(); const csv = await readFile(file!, 'utf8')
  expect(csv).toContain('物料类型名称'); expect(csv).toContain('M001'); expect(csv).not.toContain('M002')
  await page.getByRole('button', { name: '重置', exact: true }).click()
  await expect(page.getByRole('textbox', { name: '物料编码筛选', exact: true })).toHaveValue('')
  await expect(rows).toHaveCount(3)
  expect(requests).toEqual([])
})

test('ordinary account is fixed to its factory and missing mutation permissions hide buttons', async ({ page }) => {
  const requests = await setup(page, true, ['metal:inventory:list', 'metal:inventory:query'])
  await expect(page.getByRole('combobox', { name: '所属工厂', exact: true })).toBeDisabled()
  await expect(page.getByRole('button', { name: '库存调整', exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '导出', exact: true })).toHaveCount(0)
  await page.getByRole('tab', { name: '成品', exact: true }).click()
  await expect(page.getByRole('button', { name: '删除', exact: true })).toHaveCount(0)
  expect(requests).toEqual([])
})

test('export includes all filtered pages, not only sixteen visible records', async ({ page }) => {
  const requests = await setup(page)
  await page.evaluate(async () => {
    const modulePath = performance.getEntriesByType('resource').map(entry => entry.name).filter(name => /\/storage-preview\/state\.ts(?:\?|$)/.test(name)).at(-1)!
    const { preview } = await import(modulePath)
    if (!preview.factoryId) throw new Error('Preview fixture not initialized')
    const row = preview.inventory.find((item: any) => item.factoryId === preview.factoryId && item.category === 'RAW')
    for (let i = 0; i < 20; i++) preview.inventory.push({ ...row, id: `export-row-${i}`, code: `EXPORT-${i}` })
  })
  await page.getByRole('textbox', { name: '物料编码筛选', exact: true }).fill('EXPORT-')
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.locator('.storage-inventory-page .el-table__body-wrapper tbody tr')).toHaveCount(16)
  const downloading = page.waitForEvent('download'); await page.getByRole('button', { name: '导出', exact: true }).click()
  const csv = await readFile((await (await downloading).path())!, 'utf8')
  expect(csv.match(/EXPORT-/g)).toHaveLength(20)
  expect(requests).toEqual([])
})


test('mobile adjustment keeps labels and input left aligned and validates quantity', async ({ page }) => {
  await page.setViewportSize({width:390,height:844})
  const requests = await setup(page, true)
  await page.getByRole('tab', {name:'成品',exact:true}).click()
  await page.getByRole('button', {name:'库存调整',exact:true}).first().click()
  const dialog = page.getByRole('dialog', {name:'库存调整',exact:true})
  const bounds = await dialog.boundingBox(); expect(bounds!.x).toBeGreaterThanOrEqual(0); expect(bounds!.x + bounds!.width).toBeLessThanOrEqual(390)
  const labels = await dialog.locator('.el-form-item__label').evaluateAll(nodes => nodes.map(node => ({left:node.getBoundingClientRect().left,align:getComputedStyle(node).textAlign})))
  expect(labels.every(label => label.align === 'left')).toBe(true)
  expect(Math.max(...labels.map(label=>label.left))-Math.min(...labels.map(label=>label.left))).toBeLessThan(1)
  await page.screenshot({path:test.info().outputPath('inventory-adjust-mobile.png'),fullPage:true,animations:'disabled'})
  await dialog.getByRole('textbox',{name:'调整后数量',exact:true}).fill('1.5')
  await dialog.getByRole('textbox',{name:'调整原因',exact:true}).fill('预览盘点')
  await dialog.getByRole('button',{name:'确认调整',exact:true}).click()
  await expect(dialog.getByText('数量须为0至2147483647的整数',{exact:true})).toBeVisible()
  await dialog.getByRole('textbox',{name:'调整后数量',exact:true}).fill('0')
  await dialog.getByRole('button',{name:'确认调整',exact:true}).click()
  await expect(dialog).not.toBeVisible()
  expect(requests).toEqual([])
})

test('a delete confirmation from the previous session cannot delete newly seeded rows', async ({ page }) => {
  const requests = await setup(page, true)
  await page.getByRole('tab', {name:'成品',exact:true}).click()
  await page.locator('.storage-inventory-page .el-table__body-wrapper tbody tr').first().getByRole('button',{name:'删除',exact:true}).click()
  const confirmation = page.getByRole('dialog',{name:'删除库存',exact:true})
  await expect(confirmation).toBeVisible()
  await page.evaluate(async () => {
    const path = performance.getEntriesByType('resource').map(entry=>entry.name).filter(name=>/\/store\/modules\/user\.ts(?:\?|$)/.test(name)).at(-1)!
    const useUserStore = (await import(path)).default
    useUserStore().sessionEpoch++
  })
  await confirmation.getByRole('button',{name:'删除',exact:true}).click()
  await expect(page.locator('.storage-inventory-page .el-table__body-wrapper tbody tr')).toHaveCount(3)
  expect(requests).toEqual([])
})
