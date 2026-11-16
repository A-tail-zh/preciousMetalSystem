import { readFileSync, writeFileSync } from 'node:fs'
import { execFileSync } from 'node:child_process'
import assert from 'node:assert/strict'
const env = Object.fromEntries(readFileSync('.env','utf8').split(/\r?\n/).filter(l=>/^[A-Z_]+=/.test(l)).map(l=>{const i=l.indexOf('=');return[l.slice(0,i),l.slice(i+1).replace(/^'(.*)'$/,'$1')]}))
async function api(path,token,method='GET',body) {
  return (await fetch('http://127.0.0.1:8080'+path,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:`Bearer ${token}`}:{})},body:body?JSON.stringify(body):undefined})).json()
}
const captcha = await api('/captchaImage')
const answer = captcha.captchaEnabled ? JSON.parse(execFileSync('D:/javaenv/Redis-x64-3.0.504/redis-cli.exe',['-h',env.REDIS_HOST,'-p',env.REDIS_PORT,'--raw','get',`captcha_codes:${captcha.uuid}`],{encoding:'utf8'}).trim()) : ''
const login = await api('/login',null,'POST',{username:'admin',password:env.ADMIN_LOGIN_PASSWORD,code:String(answer),uuid:captcha.uuid})
assert.equal(login.code,200,login.msg)
try {
  if (process.argv[2]==='before') {
    const factory = {factoryName:`重启验收_${Date.now()}`,area:'7.1250',address:'临时重启验收地址',productionLineIds:[]}
    const created = await api('/metal/factory',login.token,'POST',factory); assert.equal(created.code,200,created.msg)
    writeFileSync('.runtime/restart-factory.json',JSON.stringify({...factory,factoryId:created.factoryId}))
    console.log('已创建真实接口重启验收记录')
  } else {
    const expected = JSON.parse(readFileSync('.runtime/restart-factory.json','utf8'))
    const actual = await api(`/metal/factory/${expected.factoryId}`,login.token)
    assert.equal(actual.code,200,actual.msg); assert.equal(actual.data.factoryName,expected.factoryName); assert.equal(actual.data.area,expected.area)
    assert.equal((await api(`/metal/factory/${expected.factoryId}`,login.token,'DELETE')).code,200)
    writeFileSync('docs/restart-verification.json',JSON.stringify({timestamp:new Date().toISOString(),passed:'后端重启后工厂详情及面积保持一致，验收记录已逻辑删除'},null,2))
    console.log('后端重启后的持久化验证通过')
  }
} finally { await api('/logout',login.token,'POST') }
