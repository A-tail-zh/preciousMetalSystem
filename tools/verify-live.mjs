import { readFileSync, writeFileSync } from 'node:fs'
import { execFileSync } from 'node:child_process'
import { randomBytes } from 'node:crypto'
import assert from 'node:assert/strict'
import bcrypt from '../frontend/node_modules/bcryptjs/index.js'

const env = Object.fromEntries(readFileSync(new URL('../.env',import.meta.url),'utf8').split(/\r?\n/).filter(l => /^[A-Z_]+=/.test(l)).map(l => {
  const split = l.indexOf('='); return [l.slice(0,split),l.slice(split+1).replace(/^'(.*)'$/,'$1')]
}))
const mysql = 'D:/mysql8.4/mysql-8.4.11-winx64/bin/mysql.exe'
const redis = 'D:/javaenv/Redis-x64-3.0.504/redis-cli.exe'
function sql(query) {
  return execFileSync(mysql,['--no-defaults','--no-login-paths','--protocol=TCP','-h','127.0.0.1','-P',env.MYSQL_PORT,'-u',env.MYSQL_USER,'-N','-B','heavy_metal','-e',query],
    {encoding:'utf8',env:{...process.env,MYSQL_PWD:env.MYSQL_PASSWORD}}).trim()
}
const base = 'http://127.0.0.1:8080'
async function api(path,token,method='GET',data) {
  const res = await fetch(base+path,{method,headers:{'Content-Type':'application/json',...(token ? {Authorization:`Bearer ${token}`} : {})},body:data ? JSON.stringify(data) : undefined})
  return res.json()
}
async function login(username,password) {
  const captcha = await api('/captchaImage')
  // Own local test fixture: obtain generated challenge from this application's test Redis.
  // No CAPTCHA configuration is disabled and no third-party service is involved.
  const answer = captcha.captchaEnabled ? JSON.parse(execFileSync(redis,['-h',env.REDIS_HOST,'-p',env.REDIS_PORT,'--raw','get',`captcha_codes:${captcha.uuid}`],{encoding:'utf8'}).trim()) : ''
  const res = await api('/login',null,'POST',{username,password,code:String(answer),uuid:captcha.uuid})
  assert.equal(res.code,200,res.msg); return res.token
}
const results = [], factories = [], lines = []
const suffix = randomBytes(5).toString('hex')
let userId, roleId, admin
try {
  assert.equal(sql('SELECT COUNT(*) FROM flyway_schema_history WHERE success=1'), '3'); results.push('Flyway三项迁移成功')
  assert.equal((await api('/metal/factory/list')).code,401); results.push('未登录接口拒绝')
  admin = await login('admin',env.ADMIN_LOGIN_PASSWORD); results.push('真实验证码与管理员登录成功')
  assert.equal((await api('/getRouters',admin)).code,200)
  const create = async name => {
    const res = await api('/metal/factory',admin,'POST',{factoryName:name,area:'12.5000',address:'联调测试地址',introduction:'自动验收临时数据',productionLineIds:[]})
    assert.equal(res.code,200,res.msg); factories.push(res.factoryId); return res.factoryId
  }
  const a = await create(`验收工厂_${suffix}`), b = await create(`验收工厂_${suffix}`)
  const initial = (await api(`/metal/factory/${a}`,admin)).data
  assert.equal(initial.area,'12.5000'); results.push('新增及详情持久化，重复名称允许')
  let edit = {...initial,factoryName:`验收修改_${suffix}`,area:'20.1234',productionLineIds:[]}
  assert.equal((await api('/metal/factory',admin,'PUT',edit)).code,200)
  assert.equal(sql(`SELECT area FROM metal_factory WHERE factory_id=${a}`),'20.1234')
  assert.notEqual((await api('/metal/factory',admin,'PUT',edit)).code,200); results.push('修改持久化及旧版本冲突拒绝')
  assert.notEqual((await api('/metal/factory',admin,'POST',{factoryName:'invalid',area:'0',address:'x',productionLineIds:[]})).code,200)
  results.push('非法面积拒绝')
  const rollbackName = `回滚验收_${suffix}`
  assert.notEqual((await api('/metal/factory',admin,'POST',{factoryName:rollbackName,area:'1',address:'x',productionLineIds:['9223372036854775807']})).code,200)
  assert.equal(sql(`SELECT COUNT(*) FROM metal_factory WHERE factory_name='${rollbackName}'`),'0')
  results.push('新增无效关联时工厂插入完整回滚')
  for (let i=0;i<16;i++) await create(`分页验收_${suffix}_${i}`)
  const page = await api('/metal/factory/list',admin)
  assert.equal(page.rows.length,16); assert.ok(page.total>=18)
  assert.equal(new Set(page.rows.map(row=>row.factoryId)).size,16)
  results.push('真实数据库默认16条分页且无重复工厂')
  const line = sql(`INSERT INTO metal_production_line(production_line_code,production_line_name) VALUES('test_${suffix}','验收产线'); SELECT LAST_INSERT_ID();`)
  lines.push(line)
  edit = {...(await api(`/metal/factory/${a}`,admin)).data,productionLineIds:[line]}
  assert.equal((await api('/metal/factory',admin,'PUT',edit)).code,200)
  assert.equal(sql(`SELECT factory_id FROM metal_production_line WHERE production_line_id=${line}`),a)
  const other = {...(await api(`/metal/factory/${b}`,admin)).data,factoryName:'不应保存的修改',productionLineIds:[line]}
  assert.notEqual((await api('/metal/factory',admin,'PUT',other)).code,200)
  assert.notEqual((await api(`/metal/factory/${b}`,admin)).data.factoryName,'不应保存的修改')
  assert.notEqual((await api(`/metal/factory/${a},${b}`,admin,'DELETE')).code,200)
  assert.equal(sql(`SELECT COUNT(*) FROM metal_factory WHERE factory_id IN (${a},${b}) AND del_flag='0'`),'2')
  results.push('关联持久化、占用拒绝、失败回滚、批量删除限制')
  edit = {...(await api(`/metal/factory/${a}`,admin)).data,productionLineIds:[]}
  assert.equal((await api('/metal/factory',admin,'PUT',edit)).code,200)
  const contenders = await Promise.all([a,b].map(async id => ({...(await api(`/metal/factory/${id}`,admin)).data,productionLineIds:[line]})))
  const race = await Promise.all(contenders.map(data => api('/metal/factory',admin,'PUT',data)))
  assert.equal(race.filter(res => res.code===200).length,1); results.push('并发关联同一产线仅一方成功')
  const owner = sql(`SELECT factory_id FROM metal_production_line WHERE production_line_id=${line}`)
  assert.equal((await api('/metal/factory',admin,'PUT',{...(await api(`/metal/factory/${owner}`,admin)).data,productionLineIds:[]})).code,200)
  const readPassword = randomBytes(10).toString('hex'), readHash = await bcrypt.hash(readPassword,10)
  roleId = sql(`INSERT INTO sys_role(role_name,role_key,role_sort,status) VALUES('验收只读','test_${suffix}',99,'0'); SELECT LAST_INSERT_ID();`)
  sql(`INSERT INTO sys_role_menu(role_id,menu_id) VALUES (${roleId},3000),(${roleId},3001),(${roleId},3002);`)
  userId = sql(`INSERT INTO sys_user(user_name,nick_name,password,status,pwd_update_date) VALUES('test_${suffix}','验收只读用户','${readHash}','0',NOW()); SELECT LAST_INSERT_ID();`)
  sql(`INSERT INTO sys_user_role(user_id,role_id) VALUES (${userId},${roleId});`)
  const readonly = await login(`test_${suffix}`,readPassword)
  assert.equal((await api('/metal/factory/list',readonly)).code,200)
  assert.equal((await api('/metal/factory',readonly,'POST',{factoryName:'权限测试',area:'1',address:'x',productionLineIds:[]})).code,403)
  await api('/logout',readonly,'POST'); results.push('真实只读角色查询成功、写入接口拒绝')
  assert.equal((await api(`/metal/factory/${a},${b}`,admin,'DELETE')).code,200)
  assert.notEqual((await api(`/metal/factory/${a}`,admin)).code,200)
  assert.equal(sql(`SELECT COUNT(*) FROM metal_factory WHERE factory_id IN (${a},${b}) AND del_flag='2'`),'2')
  results.push('批量逻辑删除及已删除详情拒绝')
  writeFileSync(new URL('../docs/live-verification.json',import.meta.url),JSON.stringify({timestamp:new Date().toISOString(),mysqlVersion:sql('SELECT VERSION()'),passed:results},null,2))
  console.log(JSON.stringify({passed:results},null,2))
} finally {
  if (admin) {
    for (const id of factories) {
      const detail = await api(`/metal/factory/${id}`,admin)
      if (detail.code===200) { await api('/metal/factory',admin,'PUT',{...detail.data,productionLineIds:[]}); await api(`/metal/factory/${id}`,admin,'DELETE') }
    }
    await api('/logout',admin,'POST')
  }
  // Permanently remove only generated test fixture rows; factory audit records remain logically deleted.
  if (lines.length) sql(`DELETE FROM metal_production_line WHERE production_line_id IN (${lines.join(',')}) AND production_line_code='test_${suffix}'`)
  if (userId) sql(`DELETE FROM sys_user_role WHERE user_id=${userId}; DELETE FROM sys_user WHERE user_id=${userId} AND user_name='test_${suffix}';`)
  if (roleId) sql(`DELETE FROM sys_role_menu WHERE role_id=${roleId}; DELETE FROM sys_role WHERE role_id=${roleId} AND role_key='test_${suffix}';`)
}
