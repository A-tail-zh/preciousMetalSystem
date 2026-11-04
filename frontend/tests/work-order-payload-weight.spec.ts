import {test,expect,type Page} from '@playwright/test'
async function setup(page:Page,raw=false){
 const saves:any[]=[]
 const plan={processId:'5',processName:'前处理',loss:'0',theoreticalLoss:'0',startTime:'2026-11-04 09:00:00',endTime:'2026-11-04 10:00:00',outputStage:raw?'RAW':'FINISHED',measurementMode:raw?'WEIGHT':'PIECE'}
 const detail={id:'200',version:1,taskType:raw?'RAW_PROCESSING':'PRODUCTION',customer:'测试客户',customerId:raw?undefined:'10',customerVersion:1,processingType:raw?'DIRECT':'MATERIAL',factoryId:'1',productionLineId:'2',ownerId:'3',orderDate:'2026-11-04',canEdit:true,canDelete:true,orderNo:'WO-TEST',items:[{id:'20',productId:raw?'':'4',materialId:raw?'7':'',plannedInputWeight:raw?'1000.123456':'',quantity:raw?0:10,plans:[plan]}]}
 await page.context().addCookies([{name:'Admin-Token',value:'work-order-payload-test',url:'http://127.0.0.1:5201'}])
 await page.route('**/dev-api/**',async route=>{const req=route.request(),path=new URL(req.url()).pathname.replace('/dev-api','');let out:any={code:200,data:[],rows:[],total:0}
  if(path==='/getInfo')out={code:200,user:{userId:2,userName:'fixture',factoryId:'1'},roles:['staff'],permissions:['*:*:*']}
  else if(path==='/getRouters')out.data=[{path:'/metal',component:'Layout',children:[{path:'work-order',component:'metal/work-order/index'}]}]
  else if(path==='/metal/work-order'&&['POST','PUT'].includes(req.method())){saves.push(req.postDataJSON());out={code:200,id:'200'}}
  else if(path==='/metal/work-order/list')out={code:200,rows:[detail],total:1}
  else if(path==='/metal/work-order/200'||path.endsWith('/edit-context'))out.data=detail
  else if(path.endsWith('/options/factories'))out.data=[{id:'1',name:'测试工厂'}]
  else if(path.endsWith('/options/lines'))out.data=[{id:'2',name:'测试产线'}]
  else if(path.endsWith('/options/owners'))out.data=[{id:'3',name:'测试负责人'}]
  else if(path.endsWith('/options/products'))out.data=[{id:'4',name:'产品A',hasProcesses:true}]
  else if(path.endsWith('/options/materials'))out.data=[{id:'7',name:'原料A',code:'M1'}]
  else if(path.endsWith('/options/raw-processes'))out.data=[{id:'5',name:'前处理'}]
  else if(path.endsWith('/options/customers'))out.data=[{id:'10',name:'测试客户',companyName:'测试公司',version:1}]
  else if(!['GET','HEAD','OPTIONS'].includes(req.method()))throw new Error('Unexpected API mutation '+path)
  await route.fulfill({json:out})
 });await page.goto('/metal/work-order');await expect(page.locator('.order-table')).toContainText('WO-TEST');return{saves,detail}
}
for(const taskType of [undefined,'PRODUCTION'] as const)for(const edit of [false,true])test(`serializer: ${taskType??'legacy production'} ${edit?'edit':'create'} omits raw-only fields`,async({page})=>{
 const s=await setup(page);await page.evaluate(async data=>{const path='/src/api/metal/work-order.ts';await (await import(path)).orderSave(data)},{...s.detail,id:edit?'200':undefined,taskType,requestId:edit?undefined:crypto.randomUUID()})
 expect(s.saves).toHaveLength(1);expect(s.saves[0].items[0]).not.toHaveProperty('plannedInputWeight');expect(s.saves[0].items[0]).not.toHaveProperty('materialId');expect(s.saves[0].items[0].quantity).toBe(10)
})
test('serializer: raw weight is retained exactly and draft is not mutated',async({page})=>{const s=await setup(page,true);const before=JSON.stringify(s.detail);await page.evaluate(async data=>{const path='/src/api/metal/work-order.ts';await (await import(path)).orderSave(data)},s.detail);expect(s.saves[0].items[0]).toMatchObject({materialId:'7',plannedInputWeight:'1000.123456',quantity:0});expect(JSON.stringify(s.detail)).toBe(before)})
for(const value of ['', '0','-1','1e3'])test(`raw form rejects invalid planned net weight ${value||'blank'}`,async({page})=>{
 const s=await setup(page,true);await page.locator('.order-table').getByRole('button',{name:'编辑',exact:true}).click();const d=page.getByRole('dialog',{name:'编辑工单',exact:true});await expect(d).toBeVisible();await d.getByLabel('计划投入净重（g）',{exact:true}).fill(value);await d.getByRole('button',{name:'确认',exact:true}).click();await expect(d.locator('.el-form-item__error')).not.toHaveCount(0);expect(s.saves).toHaveLength(0)
})
test('raw form accepts positive weight with six decimals',async({page})=>{const s=await setup(page,true);await page.locator('.order-table').getByRole('button',{name:'编辑',exact:true}).click();const d=page.getByRole('dialog',{name:'编辑工单',exact:true});await expect(d).toBeVisible();await d.getByRole('button',{name:'确认',exact:true}).click();await expect.poll(()=>s.saves.length).toBe(1);expect(s.saves[0].items[0].plannedInputWeight).toBe('1000.123456')})
test('production form submits while planned weight input is absent',async({page})=>{const s=await setup(page);await page.locator('.order-table').getByRole('button',{name:'编辑',exact:true}).click();const d=page.getByRole('dialog',{name:'编辑工单',exact:true});await expect(d).toBeVisible();await expect(d.getByLabel('计划投入净重（g）',{exact:true})).toHaveCount(0);await d.getByRole('button',{name:'确认',exact:true}).click();await expect.poll(()=>s.saves.length).toBe(1);expect(s.saves[0].items[0]).not.toHaveProperty('plannedInputWeight')})
