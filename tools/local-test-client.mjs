import {readFileSync} from 'node:fs'
import {execFileSync} from 'node:child_process'
import assert from 'node:assert/strict'
export const env=Object.fromEntries(readFileSync(new URL('../.env',import.meta.url),'utf8').split(/\r?\n/).filter(l=>/^[A-Z_]+=/.test(l)).map(l=>{const i=l.indexOf('=');return[l.slice(0,i),l.slice(i+1).replace(/^'(.*)'$/,'$1')]}))
for(const [key,value]of Object.entries({MYSQL_PORT:process.env.TEST_MYSQL_PORT,MYSQL_USER:process.env.TEST_MYSQL_USER,MYSQL_PASSWORD:process.env.TEST_MYSQL_PASSWORD,REDIS_PORT:process.env.TEST_REDIS_PORT}))if(value!==undefined)env[key]=value;
const schema=process.env.TEST_DB_SCHEMA||'heavy_metal';if(!/^[a-zA-Z0-9_]+$/.test(schema))throw Error('invalid schema');
export async function api(path,token,method='GET',body){const response=await fetch((process.env.TEST_API_BASE_URL||'http://127.0.0.1:8080')+path,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:`Bearer ${token}`}:{})},body:body?JSON.stringify(body):undefined});return response.json()}
export function sql(query){return execFileSync('D:/mysql8.4/mysql-8.4.11-winx64/bin/mysql.exe',['--no-defaults','--no-login-paths','--default-character-set=utf8mb4','--protocol=TCP','-h','127.0.0.1','-P',env.MYSQL_PORT,'-u',env.MYSQL_USER,'-N','-B','--raw',schema,'-e',query],{encoding:'utf8',env:{...process.env,MYSQL_PWD:env.MYSQL_PASSWORD}}).trim()}
export async function loginResponse(username,password){const captcha=await api('/captchaImage');const answer=captcha.captchaEnabled?JSON.parse(execFileSync('D:/javaenv/Redis-x64-3.0.504/redis-cli.exe',['-h',env.REDIS_HOST,'-p',env.REDIS_PORT,'--raw','get',`captcha_codes:${captcha.uuid}`],{encoding:'utf8'}).trim()):'';return api('/login',null,'POST',{username,password,code:String(answer),uuid:captcha.uuid})}
export async function login(username='admin',password=env.ADMIN_LOGIN_PASSWORD){const result=await loginResponse(username,password);assert.equal(result.code,200,result.msg);return result.token}
export function ok(response){assert.equal(response.code,200,response.msg);return response}
export async function detail(path,id,token){return ok(await api(`${path}/${id}`,token)).data}
