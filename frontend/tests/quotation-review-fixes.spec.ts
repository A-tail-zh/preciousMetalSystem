import {test,expect,type Page} from '@playwright/test'

function origin(lan=false){const url=new URL(String(test.info().project.use.baseURL));url.hostname=lan?'192.168.1.66':'127.0.0.1';return url.origin}
const png=Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScLbtAAAAABJRU5ErkJggg==','base64')
async function setup(page:Page,address=origin()){
 const state={version:0,contextFailure:false,loseResponse:false,contexts:0},calls:any[]=[],documents:any[]=[],requests=new Map<string,any>(),errors:string[]=[]
 page.on('pageerror',e=>errors.push(e.message))
 await page.context().addCookies([{name:'Admin-Token',value:'quotation-review-mock',url:address}])
 // Every API is intercepted: this suite must never call the business backend.
 await page.route('**/dev-api/**',async route=>{
  const path=new URL(route.request().url()).pathname.replace('/dev-api','');let result:any={code:200,data:[],rows:[],total:0}
  if(path==='/getInfo')result={code:200,user:{userId:1,userName:'review'},roles:['admin'],permissions:['*:*:*']}
  else if(path==='/getRouters')result.data=[{path:'/metal',component:'Layout',children:[{path:'quotation',component:'metal/quotation/index',meta:{noCache:true}}]}]
  else if(path==='/metal/quotation/factories')result.data=[{id:'11',name:'审查工厂'}]
  else if(path==='/metal/quotation/context'){
   state.contexts++
   if(state.contextFailure){await route.abort('failed');return}
   result.data={metalTypes:[{id:'1',name:'铱'}],rules:[{metalTypeId:'1',version:state.version}],volumeRule:{version:0}}
  }else if(path==='/metal/quotation/save'){
   const payload=route.request().postDataJSON();calls.push(payload);let quote=requests.get(payload.requestId);const reused=!!quote
   if(!quote){quote={id:String(documents.length+1),quoteNo:'QT-REVIEW-'+(documents.length+1),factoryName:'审查工厂',createTime:'2026-11-03 10:00:00',snapshotVersion:2,totalPrice:state.version?'84.00':'42.00',items:[]};documents.push(quote);requests.set(payload.requestId,quote)}
   if(state.loseResponse){state.loseResponse=false;await route.abort('timedout');return}
   result.data={...quote,reused}
  }else if(path==='/metal/quotation/orders'){result.rows=documents;result.total=documents.length}
  else if(path.endsWith('/download')){await route.fulfill({contentType:'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',body:Buffer.from('PK mock frozen workbook')});return}
  else if(path.endsWith('/image')){await route.fulfill({contentType:'image/png',body:png});return}
  await route.fulfill({json:result})
 })
 await page.goto(address+'/metal/quotation');await expect(page.locator('.history .el-pagination')).toBeVisible();await expect(page.getByRole('combobox',{name:'所属工厂',exact:true})).toBeEnabled();await page.getByRole('combobox',{name:'所属工厂',exact:true}).press('ArrowDown');await page.getByRole('option',{name:'审查工厂',exact:true}).click()
 return{state,calls,documents,errors}
}
async function fill(page:Page){
 const card=page.locator('.goods-item')
 for(const[label,value]of [['加工类型','来料定制'],['金属类型','铱'],['产品类型','方形盖']]){await card.getByRole('combobox',{name:label,exact:true}).press('ArrowDown');await page.getByRole('option',{name:value,exact:true}).click()}
 for(const[label,value]of [['商品数量','2'],['来料粉料重量（g）','10'],['来料非粉料重量（g）','5'],['理论损失重量（g）','1'],['长（cm）','1'],['宽（cm）','1'],['高（cm）','1']])await card.getByLabel(label,{exact:true}).fill(value)
}
const total=(page:Page)=>page.locator('.quote-results .quote-total')
const calculate=(page:Page)=>page.getByRole('button',{name:'开始计算',exact:true}).click()
async function refresh(page:Page){const response=page.waitForResponse(r=>r.url().includes('/metal/quotation/context'));await page.evaluate(()=>window.dispatchEvent(new Event('focus')));await response;await expect(page.getByRole('button',{name:'开始计算',exact:true})).toBeEnabled()}
const warning=(page:Page)=>page.getByText('规则刷新失败，当前规则状态未知；保留上次已保存报价，正在自动重试。',{exact:true})

for(const lan of [false,true])test('AQ-01 valid UUID and same-request recovery at '+(lan?'LAN':'localhost'),async({page})=>{
 const s=await setup(page,origin(lan));await fill(page)
 expect(await page.evaluate(()=>isSecureContext)).toBe(!lan)
 if(lan)expect(await page.evaluate(()=>typeof crypto.randomUUID)).toBe('undefined')
 s.state.loseResponse=true;await calculate(page);await expect(page.getByRole('button',{name:'开始计算',exact:true})).toBeEnabled();await calculate(page)
 await expect(total(page)).toContainText('42.00');expect(s.calls).toHaveLength(2);expect(s.calls[0]).toEqual(s.calls[1]);expect(s.calls[0].requestId).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);expect(s.documents).toHaveLength(1);expect(s.errors).toEqual([])
})

test('QR-02 refresh failure retains saved snapshot and download; unchanged recovery sends no save',async({page})=>{
 await page.clock.install();const s=await setup(page);await fill(page);await calculate(page);await expect(total(page)).toContainText('42.00')
 s.state.contextFailure=true;await page.evaluate(()=>window.dispatchEvent(new Event('focus')));await expect(warning(page)).toBeVisible()
 await expect(total(page)).toContainText('42.00');await expect(page.locator('.quote-results')).toContainText('QT-REVIEW-1');await expect(page.getByRole('button',{name:'开始计算',exact:true})).toBeDisabled()
 const download=page.waitForEvent('download');await page.locator('.quote-results').getByRole('button',{name:'下载报价单',exact:true}).click();expect((await download).suggestedFilename()).toBe('QT-REVIEW-1.xlsx')
 s.state.contextFailure=false;await page.clock.fastForward(15000);await expect(warning(page)).toHaveCount(0);await expect(page.getByRole('button',{name:'开始计算',exact:true})).toBeEnabled();await expect(total(page)).toContainText('42.00')
 await page.clock.fastForward(30000);expect(s.calls).toHaveLength(1);expect(s.documents).toHaveLength(1);expect(s.errors).toEqual([])
})

test('QR-02 changed rules after refresh recovery save once and then remain stable',async({page})=>{
 await page.clock.install();const s=await setup(page);await fill(page);await calculate(page);await expect(total(page)).toContainText('42.00')
 s.state.contextFailure=true;await page.evaluate(()=>window.dispatchEvent(new Event('focus')));await expect(warning(page)).toBeVisible();await expect(total(page)).toContainText('42.00')
 s.state.contextFailure=false;s.state.version++;await page.clock.fastForward(15000);await expect(total(page)).toContainText('84.00');await expect(warning(page)).toHaveCount(0)
 expect(s.calls).toHaveLength(2);expect(s.calls[0].requestId).not.toBe(s.calls[1].requestId);await page.clock.fastForward(30000);expect(s.calls).toHaveLength(2);expect(s.documents).toHaveLength(2)
})

test('QR-02 lost response plus failed refresh recovers original UUID before saving changed rules',async({page})=>{
 const s=await setup(page);await fill(page);s.state.loseResponse=true;await calculate(page);await expect(page.getByRole('button',{name:'开始计算',exact:true})).toBeEnabled()
 s.state.contextFailure=true;await page.evaluate(()=>window.dispatchEvent(new Event('focus')));await expect(page.getByText('金属及规则加载失败，正在自动重试。',{exact:true})).toBeVisible();expect(s.calls).toHaveLength(1)
 s.state.contextFailure=false;s.state.version++;await refresh(page);await expect(total(page)).toContainText('84.00')
 expect(s.calls).toHaveLength(3);expect(s.calls[0]).toEqual(s.calls[1]);expect(s.calls[2].requestId).not.toBe(s.calls[0].requestId);expect(s.documents).toHaveLength(2);expect(s.errors).toEqual([])
})
