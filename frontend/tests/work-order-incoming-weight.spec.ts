import {test,expect,type Page} from '@playwright/test'

async function setup(page:Page,options:{weight?:string|null;status?:string;raw?:boolean;admin?:boolean;permissions?:string[]}={}){
 const saves:any[]=[],corrections:any[]=[]
 const plan={processId:'5',processName:'加工',loss:'0',lossUnit:'PERCENT',theoreticalLoss:'0',startTime:'2026-11-04 09:00:00',endTime:'2026-11-04 10:00:00'}
 const detail:any={id:'200',version:1,taskType:options.raw?'RAW_PROCESSING':'PRODUCTION',customer:'测试客户',customerId:options.raw?undefined:'10',customerVersion:1,processingType:options.raw?'DIRECT':'MATERIAL',customerIncomingWeight:options.weight??null,factoryId:'1',factoryName:'测试工厂',productionLineId:'2',lineName:'测试产线',ownerId:'3',ownerName:'负责人',orderDate:'2026-11-04',status:options.status??'WAITING_MATERIAL',canEdit:!options.status,canDelete:!options.status,orderNo:'WO-INCOMING',reviews:[],items:[{id:'20',productId:options.raw?'':'4',materialId:options.raw?'7':null,plannedInputWeight:options.raw?'100':null,quantity:options.raw?0:10,snapshot:{name:'产品A',code:'P1'},plans:[plan]}]}
 const state={conflict:false,failCorrection:false}
 await page.context().addCookies([{name:'Admin-Token',value:'incoming-test',url:String(test.info().project.use.baseURL)}])
 await page.route('**/dev-api/**',async route=>{
  const req=route.request(),path=new URL(req.url()).pathname.replace('/dev-api','');let out:any={code:200,data:[],rows:[],total:0}
  if(path==='/getInfo')out={code:200,user:{userId:options.admin?1:2,userName:'fixture',factoryId:'1'},roles:['staff'],permissions:options.permissions??['*:*:*']}
  else if(path==='/getRouters')out.data=[{path:'/metal',component:'Layout',children:[{path:'work-order',component:'metal/work-order/index'}]}]
  else if(path==='/metal/work-order/list')out={code:200,rows:[detail],total:1}
  else if(path==='/metal/work-order/200'||path.endsWith('/edit-context'))out.data=detail
  else if(path==='/metal/work-order'&&['PUT','POST'].includes(req.method())){const body=req.postDataJSON();saves.push(body);Object.assign(detail,{customerIncomingWeight:body.customerIncomingWeight});out={code:200,id:'200'}}
  else if(path.endsWith('/customer-incoming-weight')){
   const body=req.postDataJSON();corrections.push(body)
   if(state.failCorrection){await route.abort();return}
   if(state.conflict)out={code:500,msg:'工单已被修改，请刷新后重试'}
   else{const before=detail.customerIncomingWeight;detail.customerIncomingWeight=body.customerIncomingWeight;detail.version++;detail.reviews.push({id:'1',action:'INCOMING_CHANGE',actorName:'测试员',createTime:'2026-11-04 10:00:00',reason:`${before??'未登记'} → ${body.customerIncomingWeight??'未登记'}；原因：${body.reason}`})}
  }
  else if(path.endsWith('/options/factories'))out.data=[{id:'1',name:'测试工厂'},{id:'12',name:'另一工厂'}]
  else if(path.endsWith('/options/lines'))out.data=[{id:'2',name:'测试产线'}]
  else if(path.endsWith('/options/owners'))out.data=[{id:'3',name:'测试负责人'}]
  else if(path.endsWith('/options/products'))out.data=[{id:'4',name:'产品A',hasProcesses:true}]
  else if(path.endsWith('/options/materials'))out.data=[{id:'7',name:'原料A',code:'M1'}]
  else if(path.endsWith('/options/raw-processes'))out.data=[{id:'5',name:'加工'}]
  else if(path.endsWith('/options/customers'))out.data=[{id:'10',name:'测试客户',companyName:'测试公司',version:1},{id:'11',name:'另一客户',companyName:'另一公司',version:1}]
  else if(path.includes('/options/products/'))out.data={snapshot:{name:'产品A',code:'P1'},plans:[plan]}
  else if(path.endsWith('/number/reserve'))out.data='WO-NEW'
  else if(!['GET','HEAD','OPTIONS'].includes(req.method()))throw Error('Unexpected API mutation '+path)
  await route.fulfill({json:out})
 })
 await page.goto('/metal/work-order');await expect(page.locator('.order-table')).toContainText('WO-INCOMING')
 return{saves,corrections,detail,state}
}
const editor=(page:Page)=>page.getByRole('dialog',{name:'编辑工单',exact:true})
async function edit(page:Page){await page.locator('.order-table').getByRole('button',{name:'编辑',exact:true}).click();await expect(editor(page)).toBeVisible();return editor(page)}
async function view(page:Page){await page.locator('.order-table').getByRole('button',{name:'查看',exact:true}).click();await expect(page.locator('.basic-card')).toContainText('WO-INCOMING')}

for(const [value,expected] of [['',null],['0','0.00'],['123.456','123.46']] as const)test('new order records '+(value||'blank'),async({page})=>{
 const s=await setup(page);await page.getByRole('button',{name:'添加',exact:true}).click();const d=page.getByRole('dialog',{name:'添加工单',exact:true});await expect(d).toBeVisible()
 for(const [label,option] of [['客户名称','测试客户 / 测试公司'],['加工类型','来料定制'],['产线','测试产线'],['负责人','未分配角色｜测试负责人｜未填写手机号'],['选择产品','产品A']]){await d.getByLabel(label,{exact:true}).press('Enter');await page.getByRole('option',{name:option,exact:true}).click()}
 await d.getByLabel('客户来料净重（g）',{exact:true}).fill(value);await d.getByRole('button',{name:'确认',exact:true}).click()
 await expect.poll(()=>s.saves.length).toBe(1);expect(s.saves[0].customerIncomingWeight).toBe(expected)
 await expect(page.locator('.order-table')).toContainText(expected??'未登记')
 await view(page);await expect(page.locator('.basic-card')).toContainText('客户来料净重（g）：'+(expected??'未登记'))
})

test('ordinary edit requires a reason only when registered weight changes',async({page})=>{
 const s=await setup(page,{weight:'100.00'}),d=await edit(page)
 await expect(d.getByLabel('客户来料净重（g）',{exact:true})).toHaveValue('100.00')
 await expect(d.getByLabel('来料更正原因')).toHaveCount(0)
 await d.getByLabel('客户来料净重（g）',{exact:true}).fill('150');await d.getByRole('button',{name:'确认',exact:true}).click()
 await expect(d).toContainText('请输入来料更正原因');expect(s.saves).toHaveLength(0)
 await d.getByLabel('来料更正原因').fill('客户补料');await d.getByRole('button',{name:'确认',exact:true}).click()
 await expect.poll(()=>s.saves.length).toBe(1);expect(s.saves[0]).toMatchObject({customerIncomingWeight:'150.00',incomingWeightChangeReason:'客户补料'})
})

for(const value of ['-0.001','100000000000000','1e3'])test('invalid weight cannot be saved: '+value,async({page})=>{
 const s=await setup(page),d=await edit(page);await d.getByLabel('客户来料净重（g）',{exact:true}).fill(value);await d.getByRole('button',{name:'确认',exact:true}).click()
 await expect(d.locator('.el-form-item__error')).not.toHaveCount(0);expect(s.saves).toHaveLength(0)
})

test('customer switch clears the unsaved weight',async({page})=>{
 await setup(page,{weight:'100.00'});const d=await edit(page)
 await d.getByLabel('客户名称',{exact:true}).click();await page.getByRole('option',{name:'另一客户 / 另一公司',exact:true}).click()
 await expect(d.getByLabel('客户来料净重（g）',{exact:true})).toHaveValue('');await expect(d.getByLabel('来料更正原因')).toBeVisible()
})

test('factory switch clears the unsaved weight',async({page})=>{
 await setup(page,{admin:true});await page.getByRole('button',{name:'添加',exact:true}).click();const d=page.getByRole('dialog',{name:'添加工单',exact:true})
 await d.getByLabel('工厂名称',{exact:true}).press('Enter');await page.getByRole('option',{name:'测试工厂',exact:true}).click()
 await d.getByLabel('客户来料净重（g）',{exact:true}).fill('100')
 await d.getByLabel('工厂名称',{exact:true}).press('Enter');await page.getByRole('option',{name:'另一工厂',exact:true}).click()
 await expect(d.getByLabel('客户来料净重（g）',{exact:true})).toHaveValue('')
})

test('factory-owned raw task has no customer incoming input or correction',async({page})=>{
 await setup(page,{raw:true});const d=await edit(page);await expect(d.getByLabel('客户来料净重（g）',{exact:true})).toHaveCount(0)
 await d.getByRole('button',{name:'取消',exact:true}).click();await view(page);await expect(page.getByRole('button',{name:'更正来料重量'})).toHaveCount(0)
})

for(const status of ['IN_PROGRESS','COMPLETED'])test('correct '+status+' order and retain its history',async({page})=>{
 const s=await setup(page,{weight:'100.00',status});await view(page);await page.getByRole('button',{name:'更正来料重量'}).click()
 const d=page.getByRole('dialog',{name:'更正来料重量',exact:true});await expect(d.getByLabel('客户来料净重（g）',{exact:true})).toHaveValue('100.00')
 await d.getByLabel('客户来料净重（g）',{exact:true}).fill('0');await d.getByRole('button',{name:'保存更正'}).click();await expect(d).toContainText('请输入来料更正原因');expect(s.corrections).toHaveLength(0)
 await d.getByLabel('来料更正原因').fill('更正误填');await d.getByRole('button',{name:'保存更正'}).click();await expect(d).not.toBeVisible()
 expect(s.corrections).toEqual([{version:1,customerIncomingWeight:'0.00',reason:'更正误填'}]);expect(s.detail.status).toBe(status)
 await expect(page.locator('.basic-card')).toContainText('客户来料净重（g）：0.00');await expect(page.getByText('100.00 → 0.00；原因：更正误填')).toBeVisible()
 await page.reload();await expect(page.locator('.basic-card')).toContainText('客户来料净重（g）：0.00')
})

test('viewer cannot correct incoming weight',async({page})=>{
 await setup(page,{permissions:['metal:work-order:list','metal:work-order:query']});await view(page);await expect(page.getByRole('button',{name:'更正来料重量'})).toHaveCount(0)
})

for(const failure of ['conflict','failCorrection'] as const)test('correction '+failure+' preserves draft and requires reread',async({page})=>{
 const s=await setup(page,{weight:'100.00'});await view(page);await page.getByRole('button',{name:'更正来料重量'}).click();const d=page.getByRole('dialog',{name:'更正来料重量',exact:true})
 await expect(d.getByLabel('客户来料净重（g）',{exact:true})).toHaveValue('100.00');await d.getByLabel('客户来料净重（g）',{exact:true}).fill('120');await d.getByLabel('来料更正原因').fill('补料');s.state[failure]=true
 await d.getByRole('button',{name:'保存更正'}).click();await expect(d).toContainText('请关闭后重新打开核对');await expect(d.getByLabel('客户来料净重（g）',{exact:true})).toHaveValue('120.00');await expect(d.getByRole('button',{name:'保存更正'})).toBeDisabled();expect(s.corrections).toHaveLength(1)
 await d.getByRole('button',{name:'取消',exact:true}).click();s.state[failure]=false;await page.getByRole('button',{name:'更正来料重量'}).click();await expect(d.getByLabel('客户来料净重（g）',{exact:true})).toHaveValue('100.00')
})
