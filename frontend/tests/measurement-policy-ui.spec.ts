import {test,expect,type Page,type Locator} from '@playwright/test'

async function setup(page:Page,options:{readOnly?:boolean;saveFails?:boolean;legacyLoss?:boolean}={}){
 await page.context().addCookies([{name:'Admin-Token',value:'measurement-fixture',url:test.info().project.use.baseURL||'http://127.0.0.1:5173'}])
 const processes=[{id:'1',name:'工序甲',processTypeName:'酸洗'},{id:'2',name:'工序乙',processTypeName:'融定'},{id:'3',name:'工序丙',processTypeName:'焊接'},{id:'4',name:'工序丁',processTypeName:'打磨'}]
 const row={id:'10',factoryId:'7',factoryName:'计量工厂',name:'计量测试产品',code:'MP10',version:0,metalTypeId:'1',productType:'杆',calculationVersion:2,dimensions:{innerDiameter:'0.00',outerDiameter:'2.00',length:'1.00'},volume:'6.28',weight:'142.24',processes:processes.slice(0,3).map((p,i)=>({processId:p.id,processName:p.name,measurementMode:i===1?'WEIGHT':'PIECE',loss:options.legacyLoss&&i===0?'0.123456':'1.00',lossUnit:options.legacyLoss&&i===0?'G_PER_PIECE':'PERCENT'}))}
 const saves:any[]=[],state={optionsChanged:false,optionReads:0}
 await page.route('**/dev-api/**',async route=>{
  const req=route.request(),path=new URL(req.url()).pathname.replace('/dev-api','');let out:any={code:200,data:[],rows:[],total:0}
  if(path==='/getInfo')out={code:200,user:{userId:1,userName:'admin'},roles:['admin'],permissions:['*:*:*']}
  else if(path==='/getRouters')out.data=[{path:'/metal',component:'Layout',children:[{path:'product',component:'metal/product/index',name:'Product'}]}]
  else if(path==='/metal/product/list')out={code:200,rows:[row],total:1}
  else if(path==='/metal/options/factories')out.data=[{id:'7',name:'计量工厂'}]
  else if(path==='/metal/options/processes'){state.optionReads++;out.data=processes.map(p=>({...p,name:p.name+(state.optionsChanged?'（更新）':'')}))}
  else if(path==='/metal/metal-types')out.data=[{id:'1',name:'铱'}]
  else if(path==='/metal/product/calculation-rule')out.data=[{metalTypeId:'1',metalTypeName:'铱',density:'22.65',version:0}]
  else if(path==='/metal/product/10'||path==='/metal/product/10/edit-context')out.data=row
  else if(path==='/metal/product'&&['POST','PUT'].includes(req.method())){saves.push(req.postDataJSON());out=options.saveFails?{code:400,msg:'本次修改未保存，请核对工艺'}:{code:200,id:'10'}}
  await route.fulfill({json:out})
 })
 await page.goto('/metal/product')
 await page.locator('.master-table').getByRole('button',{name:options.readOnly?'查看':'编辑',exact:true}).click()
 const dialog=page.getByRole('dialog',{name:options.readOnly?'查看产品':'编辑产品',exact:true});await expect(dialog).toBeVisible()
 return {dialog,saves,state}
}
const mode=(d:Locator,index:number)=>d.getByRole('combobox',{name:`第${index+1}道产出计量方式`,exact:true})
async function choose(page:Page,select:Locator,label:string){await select.press('ArrowDown');await page.getByRole('option',{name:label,exact:true}).click()}
async function modes(d:Locator,labels:string[]){for(const [index,label] of labels.entries()){await expect(mode(d,index)).toBeVisible();await expect(d.locator('.mode-field').nth(index)).toContainText(label)}}

for(const width of [1920,1440,768,390])test(`产品逐项选择计量并按原值保存 ${width}`,async({page})=>{
 await page.setViewportSize({width,height:1000});const {dialog:d,saves}=await setup(page)
 await modes(d,['按件管理（件）','按重量管理（g）','按件管理（件）'])
 await choose(page,mode(d,0),'按重量管理（g）');await choose(page,mode(d,0),'按件管理（件）')
 await d.getByRole('button',{name:'确定',exact:true}).click();await expect.poll(()=>saves.length).toBe(1)
 expect(saves[0].processes.map((s:any)=>s.measurementMode)).toEqual(['PIECE','WEIGHT','PIECE'])
 expect(saves[0]).toMatchObject({id:'10',version:0,factoryId:'7'})
 expect(await page.locator('.master-page').evaluate(e=>e.scrollWidth<=e.clientWidth+1)).toBe(true)
})

test('切工序追加移除和上下移动不覆盖计量',async({page})=>{
 const {dialog:d}=await setup(page)
 await choose(page,d.locator('.process-step').nth(2).locator('.process-choice').getByRole('combobox'),'工序丁')
 await modes(d,['按件管理（件）','按重量管理（g）','按件管理（件）'])
 await d.locator('.process-step').nth(1).getByRole('button',{name:'下移',exact:true}).click()
 await modes(d,['按件管理（件）','按件管理（件）','按重量管理（g）'])
 await d.locator('.process-step').nth(2).getByRole('button',{name:'上移',exact:true}).click()
 await d.getByRole('button',{name:'添加工序',exact:true}).click()
 await modes(d,['按件管理（件）','按重量管理（g）','按件管理（件）','按件管理（件）'])
 await d.locator('.process-step').nth(3).getByRole('button',{name:'移除',exact:true}).click()
 await modes(d,['按件管理（件）','按重量管理（g）','按件管理（件）'])
})

test('重量工序移到末道行内报错且不自动改值',async({page})=>{
 const {dialog:d,saves}=await setup(page)
 await d.locator('.process-step').nth(1).getByRole('button',{name:'下移',exact:true}).click()
 await d.getByRole('button',{name:'确定',exact:true}).click()
 const error=d.locator('.process-step').nth(2).locator('.mode-field .el-form-item__error')
 await expect(error).toHaveText('最后一道工序产出成品，须按件管理');await expect(error).toBeInViewport()
 await modes(d,['按件管理（件）','按件管理（件）','按重量管理（g）']);expect(saves).toEqual([])
 await choose(page,mode(d,2),'按件管理（件）');await expect(error).toBeHidden()
})

test('保存失败保留选择和输入',async({page})=>{
 const {dialog:d,saves}=await setup(page,{saveFails:true});await choose(page,mode(d,0),'按重量管理（g）')
 await d.getByLabel('产品名称',{exact:true}).fill('保留本轮草稿');await d.getByRole('button',{name:'确定',exact:true}).click()
 await expect.poll(()=>saves.length).toBe(1);await expect(d).toBeVisible()
 await modes(d,['按重量管理（g）','按重量管理（g）','按件管理（件）']);await expect(d.getByLabel('产品名称',{exact:true})).toHaveValue('保留本轮草稿')
})

test('候选资料刷新提示核对但不改草稿计量',async({page})=>{
 await page.clock.install();const {dialog:d,state,saves}=await setup(page);await choose(page,mode(d,0),'按重量管理（g）')
 const reads=state.optionReads;state.optionsChanged=true;await page.clock.runFor(5500)
 await expect.poll(()=>state.optionReads).toBeGreaterThan(reads);await expect(d).toContainText('当前输入已保留');await expect(d.getByRole('button',{name:'确定',exact:true})).toBeDisabled()
 await modes(d,['按重量管理（g）','按重量管理（g）','按件管理（件）']);expect(saves).toEqual([])
})

test('查看产品保留已存计量并禁止修改',async({page})=>{
 const {dialog:d,saves}=await setup(page,{readOnly:true});await modes(d,['按件管理（件）','按重量管理（g）','按件管理（件）'])
 for(let i=0;i<3;i++)await expect(mode(d,i)).toBeDisabled();expect(saves).toEqual([])
})

test('取消编辑不保存且重开读取服务器原计量',async({page})=>{
 const {dialog:d,saves}=await setup(page);await choose(page,mode(d,0),'按重量管理（g）')
 await d.getByRole('button',{name:'取消',exact:true}).click();await page.locator('.master-table').getByRole('button',{name:'编辑',exact:true}).click()
 await modes(d,['按件管理（件）','按重量管理（g）','按件管理（件）']);expect(saves).toEqual([])
})

test('历史g每件选择重量须先手工配置百分比',async({page})=>{
 const {dialog:d,saves}=await setup(page,{legacyLoss:true});await choose(page,mode(d,0),'按重量管理（g）')
 await d.getByRole('button',{name:'确定',exact:true}).click()
 await expect(d.locator('.process-step').nth(0).locator('.mode-field .el-form-item__error')).toHaveText('按重量管理须先重新配置损耗百分比');expect(saves).toEqual([])
 await d.getByRole('button',{name:'重新配置百分比',exact:true}).click()
 await d.getByRole('textbox',{name:'第1道工序理论损耗（%）',exact:true}).fill('2')
 await d.getByRole('button',{name:'确定',exact:true}).click();await expect.poll(()=>saves.length).toBe(1)
 expect(saves[0].processes[0]).toMatchObject({measurementMode:'WEIGHT',lossUnit:'PERCENT',loss:'2.00'})
})

test('历史g每件保持按件时不换算旧值',async({page})=>{
 const {dialog:d,saves}=await setup(page,{legacyLoss:true});await d.getByRole('button',{name:'确定',exact:true}).click();await expect.poll(()=>saves.length).toBe(1)
 expect(saves[0].processes[0]).toMatchObject({measurementMode:'PIECE',lossUnit:'G_PER_PIECE',loss:'0.123456'})
})
