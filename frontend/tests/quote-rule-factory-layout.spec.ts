import {test,expect,type Page} from '@playwright/test'
const deferred=()=>{let resolve!:()=>void;const promise=new Promise<void>(r=>resolve=r);return{promise,resolve}}
async function setup(page:Page,staff=false){
 const state={staff,factoryFailure:false,factoryRequests:0,optionGate:undefined as ReturnType<typeof deferred>|undefined,saveGate:undefined as ReturnType<typeof deferred>|undefined},reads:string[]=[],writes:any[]=[]
 await page.context().addCookies([{name:'Admin-Token',value:'quote-factory-layout-mock',url:String(test.info().project.use.baseURL)}])
 await page.route('**/dev-api/**',async route=>{
  const req=route.request(),url=new URL(req.url()),path=url.pathname.replace('/dev-api','');let r:any={code:200,data:[],rows:[],total:0}
  if(path==='/getInfo')r={code:200,user:{userId:staff?9:1,userName:'layout-review'},roles:[staff?'staff':'admin'],permissions:['metal:quote-rule:list','metal:quote-rule:edit']}
  else if(path==='/getRouters')r.data=[{path:'/metal',component:'Layout',children:[{path:'quote-rule',component:'metal/quote-rule/index',meta:{noCache:true}}]}]
  else if(path==='/metal/options/factories'){
   state.factoryRequests++;const gate=state.optionGate;state.optionGate=undefined
   r=state.factoryFailure?{code:500,msg:'工厂加载失败'}:{code:200,data:state.staff?[{id:'22',name:'本人工厂'}]:[{id:'11',name:'工厂甲'},{id:'22',name:'工厂乙'}]};if(gate)await gate.promise
  }else if(path==='/metal/volume-rule'){
   if(req.method()==='PUT'){writes.push({path,factoryId:url.searchParams.get('factoryId'),body:req.postDataJSON()});const gate=state.saveGate;state.saveGate=undefined;if(gate)await gate.promise}
   else reads.push(path+'?factoryId='+url.searchParams.get('factoryId'))
   r.data={piValue:url.searchParams.get('factoryId')==='22'?'3.20':'3.14',curvedBottomFactor:'1.03',version:0}
  }else if(path==='/metal/quote-rule/list')reads.push(path+'?factoryId='+url.searchParams.get('factoryId'))
  await route.fulfill({json:r})
 })
 await page.goto('/metal/quote-rule');return{state,reads,writes}
}
async function choose(page:Page,name:string){await page.getByRole('combobox',{name:'所属工厂',exact:true}).press('ArrowDown');await page.getByRole('option',{name,exact:true}).click()}
for(const width of [1920,1440,768,390])for(const staff of [false,true])test('factory layout '+(staff?'staff':'admin')+' '+width,async({page})=>{
 await page.setViewportSize({width,height:1000});const s=await setup(page,staff);const control=page.getByRole(staff?'textbox':'combobox',{name:'所属工厂',exact:true});await expect(control).toBeVisible()
 const label=page.locator('.factory-form .el-form-item__label'),heading=page.getByRole('heading',{name:'本厂体积参数',exact:true});const a=await label.boundingBox(),b=await heading.boundingBox();expect(Math.abs(a!.x-b!.x)).toBeLessThanOrEqual(1)
 const wrapper=page.locator('.factory-control');expect((await wrapper.boundingBox())!.width).toBeCloseTo(240,0);expect(await page.locator('.quote-page').evaluate(e=>e.scrollWidth<=e.clientWidth+1)).toBeTruthy()
 if(staff){await expect(control).toHaveValue('本人工厂');await expect(control).toHaveAttribute('readonly','');await expect(page.getByRole('combobox',{name:'所属工厂',exact:true})).toHaveCount(0);await expect.poll(()=>s.reads.length).toBe(2);expect(s.reads.every(r=>r.endsWith('factoryId=22'))).toBe(true)}
 else{await expect(page.locator('.factory-form')).toContainText('请选择工厂');expect(s.reads).toHaveLength(0);await choose(page,'工厂甲');await expect(page.getByLabel('π值',{exact:true})).toHaveValue('3.14')}
})
test('admin switching factories loads the selected configuration',async({page})=>{const s=await setup(page);await choose(page,'工厂甲');await expect(page.getByLabel('π值',{exact:true})).toHaveValue('3.14');await choose(page,'工厂乙');await expect(page.getByLabel('π值',{exact:true})).toHaveValue('3.20');expect(s.reads.filter(r=>r.endsWith('factoryId=22'))).toHaveLength(2)})
test('factory load failure can retry for staff',async({page})=>{const s=await setup(page,true);s.state.factoryFailure=true;await page.reload();await expect(page.locator('.factory-status[role=alert]')).toContainText('工厂加载失败');s.state.factoryFailure=false;await page.getByRole('button',{name:'重新加载工厂',exact:true}).click();await expect(page.getByRole('textbox',{name:'所属工厂',exact:true})).toHaveValue('本人工厂');await expect(page.getByLabel('π值',{exact:true})).toHaveValue('3.20');await expect(page.getByRole('button',{name:'重新加载工厂',exact:true})).toHaveCount(0)})
test('saving public parameters prevents changing factory',async({page})=>{const s=await setup(page);await choose(page,'工厂甲');await expect(page.getByLabel('π值',{exact:true})).toHaveValue('3.14');const gate=deferred();s.state.saveGate=gate;await page.getByRole('button',{name:'保存参数',exact:true}).click();await expect.poll(()=>s.writes.length).toBe(1);await expect(page.getByRole('combobox',{name:'所属工厂',exact:true})).toBeDisabled();gate.resolve();await expect(page.getByRole('combobox',{name:'所属工厂',exact:true})).toBeEnabled();expect(s.writes[0].factoryId).toBe('11')})
test('account change discards old factory options and selects the staff factory',async({page})=>{const s=await setup(page);await choose(page,'工厂甲');await expect(page.getByLabel('π值',{exact:true})).toHaveValue('3.14');s.state.staff=true;await page.evaluate(async()=>{const path='/src/store/modules/user.ts';const {default:useUser}=await import(path);useUser().id='9'});await expect(page.getByRole('textbox',{name:'所属工厂',exact:true})).toHaveValue('本人工厂');await expect(page.getByLabel('π值',{exact:true})).toHaveValue('3.20');await expect(page.getByRole('combobox',{name:'所属工厂',exact:true})).toHaveCount(0)})

test('late admin factory options cannot overwrite a new staff account',async({page})=>{
 const s=await setup(page),gate=deferred();s.state.optionGate=gate;const before=s.state.factoryRequests;await page.reload();await expect.poll(()=>s.state.factoryRequests).toBeGreaterThan(before);await expect(page.getByRole('combobox',{name:'所属工厂',exact:true})).toBeDisabled();s.state.staff=true
 await page.evaluate(async()=>{const path='/src/store/modules/user.ts';const {default:useUser}=await import(path);useUser().id='9'});await expect(page.getByRole('textbox',{name:'所属工厂',exact:true})).toHaveValue('本人工厂');gate.resolve();await page.evaluate(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(()=>r(null)))));await expect(page.getByRole('textbox',{name:'所属工厂',exact:true})).toHaveValue('本人工厂');await expect(page.getByLabel('π值',{exact:true})).toHaveValue('3.20');expect(s.reads.every(r=>r.endsWith('factoryId=22'))).toBe(true)
})
