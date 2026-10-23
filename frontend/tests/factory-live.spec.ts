import{workOrderFixture}from'../../tools/work-order-test-fixture.mjs'
import{registerPageResources}from'./helpers/fixture-registration'
import {api,ok,detail,login} from '../../tools/local-test-client.mjs'
import { test, expect } from '@playwright/test'
import { readFileSync } from 'node:fs'
import { execFileSync } from 'node:child_process'

test('真实登录和工厂页面CRUD', async ({page}) => {
  test.skip(process.env.LIVE_FACTORY !== '1', '需要已配置并运行的MySQL、Redis和后端')
  const env = Object.fromEntries(readFileSync('../.env','utf8').split(/\r?\n/).filter(l => /^[A-Z_]+=/.test(l)).map(l => {
    const i = l.indexOf('='); return [l.slice(0,i),l.slice(i+1).replace(/^'(.*)'$/,'$1')]
  }))
  const captchaResponse = page.waitForResponse(res => res.url().includes('/captchaImage') && res.ok())
  await page.goto('/metal/factory')
  const captcha = await (await captchaResponse).json()
  await page.getByPlaceholder('账号',{exact:true}).fill('admin')
  await page.getByPlaceholder('密码',{exact:true}).fill(env.ADMIN_LOGIN_PASSWORD)
  if (captcha.captchaEnabled) {
    const answer = JSON.parse(execFileSync('D:/javaenv/Redis-x64-3.0.504/redis-cli.exe',
      ['-h',env.REDIS_HOST,'-p',env.REDIS_PORT,'--raw','get',`captcha_codes:${captcha.uuid}`],{encoding:'utf8'}).trim())
    await page.getByPlaceholder('验证码',{exact:true}).fill(String(answer))
  }
  await page.getByRole('button',{name:'登 录',exact:true}).click()
  await expect(page).toHaveURL(/\/metal\/factory$/)
  const fixture=await workOrderFixture();registerPageResources(page,fixture);const name = fixture.prefix+'页面厂', updated = `${name}_修改`
  let created = false; const apiToken=await login()
  try {
    await page.getByRole('button',{name:'添加',exact:true}).click()
    const dialog = page.getByRole('dialog')
    await dialog.getByLabel('工厂名称',{exact:true}).fill(name)
    await dialog.getByLabel('占地面积（万㎡）',{exact:true}).fill('12.5000')
    await dialog.getByLabel('详细地址',{exact:true}).fill('真实页面验收地址')
    await dialog.getByRole('button',{name:'确定',exact:true}).click()
    await expect(dialog).not.toBeVisible(); created = true
    await page.reload()
    // Other isolated fixtures may be created concurrently; locate this factory through its filter.
    await page.getByPlaceholder('工厂名称',{exact:true}).fill(name)
    await page.getByRole('button',{name:'查询',exact:true}).click()
    let row = page.getByRole('row').filter({hasText:name})
    await expect(row).toBeVisible()
    await row.getByRole('button',{name:'编辑',exact:true}).click()
    await expect(dialog.getByLabel('占地面积（万㎡）',{exact:true})).toHaveValue('12.5000')
    await dialog.getByLabel('工厂名称',{exact:true}).fill(updated)
    await dialog.getByRole('button',{name:'确定',exact:true}).click()
    row = page.getByRole('row').filter({hasText:updated})
    await expect(row).toBeVisible()
    await page.screenshot({path:'../docs/screenshots/factory-live.png',fullPage:true,animations:'disabled'})
    const current=ok(await api('/metal/factory/list?pageSize=100&factoryName='+encodeURIComponent(updated),apiToken)).rows.find((x:any)=>x.factoryName===updated);ok(await api('/metal/factory',apiToken,'PUT',{...(await detail('/metal/factory',current.factoryId??current.id,apiToken)),productionLineIds:[]}));
    await row.getByRole('button',{name:'删除',exact:true}).click()
    await page.getByRole('button',{name:'确定',exact:true}).click()
    await expect(row).toHaveCount(0); created = false
  } finally {
    if (created) {
      const row = page.getByRole('row').filter({hasText:name})
      if (await row.count()) { await row.getByRole('button',{name:'删除',exact:true}).click(); await page.getByRole('button',{name:'确定',exact:true}).click() }
    }
    await api('/logout',apiToken,'POST');await fixture.cleanup()
  }
})
