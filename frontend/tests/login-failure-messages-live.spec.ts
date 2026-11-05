import {test,expect,type Page} from './helpers/isolated-live-test'
import {execFileSync} from 'node:child_process'
import {api,env,sql} from '../../tools/local-test-client.mjs'

test.skip(process.env.LIVE_LOGIN!=='1','需要显式隔离登录环境')

test.beforeAll(()=>{
 expect(process.env.TEST_API_BASE_URL).toBe('http://127.0.0.1:8096')
 expect(process.env.TEST_MYSQL_PORT).toBe('3323')
 expect(process.env.TEST_REDIS_PORT).toBe('6396')
 expect(sql('SELECT @@port')).toBe('3323')
})

async function captcha(page:Page){
 const response=page.waitForResponse(r=>new URL(r.url()).pathname.endsWith('/captchaImage'))
 await page.goto('/login')
 const data=await(await response).json();expect(data.captchaEnabled).not.toBe(false)
 const key='captcha_codes:'+data.uuid
 const code=JSON.parse(execFileSync('D:/javaenv/Redis-x64-3.0.504/redis-cli.exe',['-h','127.0.0.1','-p','6396','--raw','get',key],{encoding:'utf8'}).trim())
 return{key,code:String(code)}
}

for(const kind of ['password','unknown-account','captcha','expired'] as const)test('real login explains '+kind,async({page})=>{
 const challenge=await captcha(page)
 await page.getByPlaceholder('账号',{exact:true}).fill(kind==='unknown-account'?'19900000001':'admin')
 await page.getByPlaceholder('密码',{exact:true}).fill('FictionalWrong123!')
 if(kind==='expired')execFileSync('D:/javaenv/Redis-x64-3.0.504/redis-cli.exe',['-h','127.0.0.1','-p','6396','del',challenge.key])
 await page.getByPlaceholder('验证码',{exact:true}).fill(kind==='captcha'?'wrong':challenge.code)
 await page.getByRole('button',{name:/登.*录/}).click()
 const message=kind==='captcha'?'验证码错误':kind==='expired'?'验证码已失效':'账号或密码错误'
 await expect(page.locator('.el-message').filter({hasText:message})).toBeVisible()
 await expect(page.getByText('系统处理失败，请稍后重试或联系管理员',{exact:true})).toHaveCount(0)
 await expect(page).toHaveURL(/\/login$/)
 expect((await page.context().cookies()).some(c=>c.name==='Admin-Token')).toBe(false)
})

test('valid credentials and captcha still log in',async({page})=>{
 const challenge=await captcha(page)
 await page.getByPlaceholder('账号',{exact:true}).fill('admin')
 await page.getByPlaceholder('密码',{exact:true}).fill(env.ADMIN_LOGIN_PASSWORD)
 await page.getByPlaceholder('验证码',{exact:true}).fill(challenge.code)
 await page.getByRole('button',{name:/登.*录/}).click()
 await expect(page).not.toHaveURL(/\/login$/)
 const token=(await page.context().cookies()).find(c=>c.name==='Admin-Token')?.value
 expect(token).toBeTruthy()
 try{expect((await api('/getInfo',token)).code).toBe(200)}finally{await api('/logout',token,'POST')}
})
