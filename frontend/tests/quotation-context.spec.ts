import {test,expect,type Page} from '@playwright/test'

const deferred=()=>{let resolve!:()=>void;const promise=new Promise<void>(r=>resolve=r);return{promise,resolve}}
async function setup(page:Page,{admin=true,save=true,single=false}={}){
 const state={factoryFailure:false,emptyFactories:false,saveFailure:false,lost:false,saveGate:undefined as ReturnType<typeof deferred>|undefined,downloadGate:undefined as ReturnType<typeof deferred>|undefined,saves:[] as any[]}
 await page.context().addCookies([{name:'Admin-Token',value:'quote-context-fixture',url:String(test.info().project.use.baseURL)}])
 await page.route('**/dev-api/**',async route=>{
  const req=route.request(),path=new URL(req.url()).pathname.replace('/dev-api','');let out:any={code:200,data:[],rows:[],total:0}
  if(path==='/getInfo')out={code:200,user:{userId:admin?1:2,userName:'fixture'},roles:[admin?'admin':'staff'],permissions:save?['metal:quotation:list','metal:quotation:add']:['metal:quotation:list']}
  else if(path==='/getRouters')out.data=[{path:'/metal',component:'Layout',children:[{path:'quotation',component:'metal/quotation/index',name:'QuoteContext'}]}]
  else if(path==='/metal/quotation/factories')out=state.factoryFailure?{code:500,msg:'工厂加载失败'}:{code:200,data:state.emptyFactories?[]:single||!admin?[{id:'11',name:'工厂A'}]:[{id:'11',name:'工厂A'},{id:'22',name:'工厂B'}]}
  else if(path==='/metal/quotation/context')out.data={metalTypes:[{id:'1',name:'铱'}],rules:[],volumeRule:{version:0}}
  else if(path==='/metal/quotation/save'){
   const body=req.postDataJSON();state.saves.push(body);if(state.saveGate)await state.saveGate.promise
   if(state.lost){state.lost=false;await route.abort();return}
   out=state.saveFailure?{code:500,msg:'保存失败'}:{code:200,data:{id:String(state.saves.length),quoteNo:'QT'+state.saves.length,factoryName:body.factoryId==='11'?'工厂A':'工厂B',totalPrice:'24.00',snapshotVersion:2,items:body.items.map((item:any)=>({...item,category:'铱杆',processingTypeName:'欠料定制',totalPrice:'24.00',unitWeight:'12.00',totalWeight:'24.00'}))}}
  }else if(path.endsWith('/download')){if(state.downloadGate)await state.downloadGate.promise;await route.fulfill({contentType:'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',body:'mock excel'});return}
  else if(path.endsWith('/image')){await route.fulfill({contentType:'image/png',body:Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScLbtAAAAABJRU5ErkJggg==','base64')});return}
  await route.fulfill({json:out})
 })
 await page.goto('/metal/quotation');return state
}
async function factory(page:Page,name='工厂A'){const select=page.getByRole('combobox',{name:'所属工厂',exact:true});await expect(select).toBeEnabled();await select.press('ArrowDown');await page.getByRole('option',{name,exact:true}).click()}
async function goods(page:Page){
 const row=page.locator('.goods-item').first()
 for(const[label,name]of [['加工类型','欠料定制'],['金属类型','铱'],['产品类型','杆']]){await row.getByRole('combobox',{name:label,exact:true}).press('ArrowDown');await page.getByRole('option',{name,exact:true}).click()}
 for(const[label,value]of [['商品数量','2'],['理论损失重量（g）','0'],['内径（cm）','0'],['外径（cm）','2'],['长度（cm）','3']])await row.getByLabel(label,{exact:true}).fill(value)
}
const calculate=(page:Page)=>page.getByRole('button',{name:'开始计算',exact:true}).click()
const result=(page:Page)=>page.locator('.quote-results')

test('admin explicitly chooses a factory and calculation saves the frozen quote in one request',async({page})=>{
 const state=await setup(page,{single:true});await expect(page.getByRole('button',{name:'开始计算',exact:true})).toBeDisabled();await factory(page);await goods(page);await calculate(page);await expect(result(page)).toContainText('QT1');expect(state.saves[0].factoryId).toBe('11');await expect(page.getByRole('button',{name:'保存报价单',exact:true})).toHaveCount(0)
 const download=page.waitForEvent('download');await result(page).getByRole('button',{name:'下载报价单',exact:true}).click();expect((await download).suggestedFilename()).toBe('QT1.xlsx')
})
test('staff factory is automatically fixed and list-only permission cannot save',async({page})=>{
 await setup(page,{admin:false,save:false});await expect(page.getByRole('textbox',{name:'所属工厂',exact:true})).toHaveValue('工厂A');await expect(page.getByRole('combobox',{name:'所属工厂',exact:true})).toHaveCount(0);await expect(page.getByRole('button',{name:'开始计算',exact:true})).toBeDisabled()
})
test('changing factory clears the saved association and metal selection while retaining quantities',async({page})=>{
 const state=await setup(page);await factory(page);await goods(page);await calculate(page);await expect(result(page)).toBeVisible();await factory(page,'工厂B');await expect(result(page)).toHaveCount(0);await expect(page.getByLabel('商品数量',{exact:true})).toHaveValue('2');await expect(page.getByRole('combobox',{name:'金属类型',exact:true})).toHaveValue('');expect(state.saves).toHaveLength(1)
})
test('saved input changes create a fresh request identity and clear the previous download',async({page})=>{
 const state=await setup(page);await factory(page);await goods(page);await calculate(page);await expect(result(page)).toBeVisible();await page.getByLabel('长度（cm）',{exact:true}).fill('4');await expect(result(page)).toHaveCount(0);await calculate(page);await expect(result(page)).toContainText('QT2');expect(state.saves[1].requestId).not.toBe(state.saves[0].requestId)
})
test('lost response retries the identical payload and locks factory changes until resolved',async({page})=>{
 const state=await setup(page);await factory(page);await goods(page);state.lost=true;await calculate(page);await expect.poll(()=>state.saves.length).toBe(1);await expect(page.getByRole('button',{name:'开始计算',exact:true})).toBeEnabled();await expect(page.getByRole('combobox',{name:'所属工厂',exact:true})).toBeDisabled();await calculate(page);await expect(result(page)).toBeVisible();expect(state.saves[1]).toEqual(state.saves[0])
})
test('save locks the draft and leaving the page discards a delayed response',async({page})=>{
 const state=await setup(page);await factory(page);await goods(page);state.saveGate=deferred();await calculate(page);await expect.poll(()=>state.saves.length).toBe(1);await expect(page.getByLabel('商品数量',{exact:true})).toBeDisabled();await page.evaluate(async()=>{const path='/src/router/index.ts';await (await import(path)).default.push('/401')});const response=page.waitForResponse(r=>r.url().includes('/metal/quotation/save'));state.saveGate.resolve();await response;await expect(result(page)).toHaveCount(0)
})
test('factory load failure automatically retries and an empty list keeps calculation blocked',async({page})=>{
 await page.clock.install();const state=await setup(page);state.factoryFailure=true;await page.reload();await expect(page.getByText('工厂加载失败，正在自动重试。',{exact:true})).toBeVisible();state.factoryFailure=false;state.emptyFactories=true;await page.evaluate(()=>window.dispatchEvent(new Event('focus')));await expect(page.getByText('工厂加载失败，正在自动重试。',{exact:true})).toHaveCount(0);await expect(page.getByRole('button',{name:'开始计算',exact:true})).toBeDisabled()
})
for(const width of [1920,1440,768,390])test('factory and quote draft fit the viewport '+width,async({page})=>{
 await page.setViewportSize({width,height:1000});await setup(page);await factory(page);await goods(page);expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)
})