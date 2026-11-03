import {test,expect,type Page} from '@playwright/test'
const line={id:'81',plannedWeight:'22',actualWeight:null,line:{category:'RAW',code:'RAW-01',name:'旧料',organization:'演示客户',netWeight:'22',quantity:0,grossWeight:'22',impurity:null}}
async function setup(page:Page,patch:any={},permissions=['metal:inventory:list','metal:warehouse-order:accept'],postErrors:string[]=[]){
 const group={id:'8',status:'WAITING',keeperId:'7',keeperName:'仓管甲',warehouseName:'原料仓',orderNo:'IN20261103000021',type:'RAW_IN',order:{warehouseId:'3',keeperId:'7'},lines:[structuredClone(line)],...patch.group}
 const flow={id:'5',kind:'RAW_INBOUND',requestActor:'1',status:'WAITING',stage:'KEEPER',factoryId:'2',version:0,groups:[group],events:[{actorId:'1',actorName:'管理员',action:'CREATE',version:0,createTime:'2026-11-03 17:23:18',content:'{}'},{actorId:'99',action:'REVISE',version:1,content:'{"reason":"补充称重"}'}],...patch,group:undefined}
 const posts:any[]=[]
 await page.context().addCookies([{name:'Admin-Token',value:'raw-accept-mock',url:'http://127.0.0.1:5192'}])
 await page.route('**/dev-api/**',async route=>{const req=route.request(),p=new URL(req.url()).pathname.replace('/dev-api','');let response:any={code:200,data:[],rows:[],total:0}
  if(req.method()==='POST'){posts.push({path:p,body:req.postDataJSON()});const message=postErrors.shift();response=message?{code:500,msg:message}:{code:200,data:flow}}
  else if(p==='/getInfo')response={code:200,user:{userId:7,userName:'keeper',nickName:'当前昵称'},roles:['demo'],permissions}
  else if(p==='/getRouters')response.data=[{path:'/metal',component:'Layout',children:[{path:'inventory',component:'metal/inventory/index',name:'RawAcceptInventory'}]}]
  else if(p.endsWith('/options/factories'))response.data=[{id:'2',name:'本厂'}]
  else if(p==='/metal/warehouse-flows/5')response.data=flow
  await route.fulfill({json:response})
 })
 await page.goto('/metal/inventory');await expect(page.locator('.warehouse-page')).toBeVisible()
 await page.evaluate(async()=>{await (document.querySelector('.warehouse-page') as any).__vueParentComponent.refs.flowDialog.open('5')})
 return {dialog:page.getByRole('dialog',{name:flow.kind==='RAW_INBOUND'?'原料入库验收':'仓储交接确认',exact:true}),posts}
}
test('assigned keeper sees receipt fields, human history and required per-line differences',async({page})=>{
 const {dialog:d,posts}=await setup(page);await expect(d).toBeVisible();await expect(d).toContainText('待库管验收');await expect(d).toContainText('指定验收人：仓管甲');await expect(d).toContainText('当前账号可验收');await expect(d).not.toContainText('待指定库管');await expect(d).not.toContainText('领用人');await expect(d).not.toContainText('数量');await expect(d).not.toContainText('件数');await expect(d).toContainText('RAW-01');await expect(d).toContainText('旧料');await expect(d).toContainText('演示客户')
 await expect(d.getByRole('columnheader',{name:'申报净重（g）',exact:true})).toBeVisible()
 await d.getByText('原始申报称重依据',{exact:true}).click();await expect(d).toContainText('杂质 未填写');await expect(d).not.toContainText('— g')
 await d.getByText('验收及修订历史',{exact:true}).click();await expect(d).toContainText('管理员');await expect(d).toContainText('创建申请');await expect(d).toContainText('修订申请');await expect(d).toContainText('历史未记录姓名');await expect(d.getByRole('columnheader',{name:'版本',exact:true})).toHaveCount(0)
 await d.locator('.flow-group .el-input input').first().fill('21');await d.getByRole('button',{name:'本人库管验收',exact:true}).click();await expect(d).toContainText('每一条重量差异均须填写原因');expect(posts).toHaveLength(0)
 await d.getByPlaceholder('重量不同必须填写').fill('复称差异');await d.getByRole('button',{name:'本人库管验收',exact:true}).click();await expect.poll(()=>posts.length).toBe(1);expect(posts[0].body.lines[0]).toMatchObject({lineId:'81',netWeight:'21.00',quantity:0,differenceReason:'复称差异'})
})
for(const [title,patch,permissions,message] of [
 ['other keeper',{group:{keeperId:'9'}},undefined,'请由仓管员仓管甲登录验收'],
 ['missing permission',{},['metal:inventory:list'],'当前账号缺少库管验收权限'],
 ['returned',{status:'RETURNED'},undefined,'申请已退回，需修订后再验收'],
 ['confirmed partial',{group:{status:'CONFIRMED',confirmedBy:'7',confirmedByName:'已验收人'}},undefined,'本仓已验收，等待其他仓库完成'],
 ['cancelled',{status:'CANCELLED'},undefined,'申请已取消，无需验收'],
 ['posted',{status:'POSTED',stage:'DONE',group:{status:'CONFIRMED'}},undefined,'已验收入库'],
 ['missing assignee',{group:{keeperId:null,keeperName:null,order:{warehouseId:'3'}}},undefined,'尚未指定仓管员，请先修订申请']
] as const)test(title+' explains why acceptance is unavailable',async({page})=>{const {dialog:d}=await setup(page,patch,permissions as string[]|undefined);await expect(d).toContainText(message);await expect(d.getByRole('button',{name:'本人库管验收',exact:true})).toHaveCount(0);if(title==='confirmed partial')await expect(d).toContainText('实际验收人：已验收人')})
test('raw actions request the selected reason and preserve version and request key',async({page})=>{
 const {dialog:d,posts}=await setup(page,{},['metal:inventory:list','metal:warehouse-order:accept','metal:warehouse-order:add']);await expect(d.getByText('退回、修订、取消或冲销原因',{exact:true})).toHaveCount(0)
 await d.getByRole('button',{name:'退回修订',exact:true}).click();await expect(d.getByText('退回原因',{exact:true})).toBeVisible();await d.getByPlaceholder('请填写退回原因').fill('申报需更正');await d.getByRole('button',{name:'确认退回修订',exact:true}).click();await expect.poll(()=>posts.length).toBe(1);expect(posts[0].body).toMatchObject({version:0,reason:'申报需更正'});expect(posts[0].body.requestId).toBeTruthy()
})
for(const width of [1920,1440,768,390])test('raw receipt at '+width,async({page})=>{await page.setViewportSize({width,height:1000});const {dialog:d}=await setup(page);await expect(d).toContainText('指定验收人：仓管甲');await expect(d.getByRole('button',{name:'本人库管验收',exact:true})).toBeVisible();expect(await d.evaluate(e=>e.scrollWidth<=e.clientWidth+1)).toBeTruthy();await d.screenshot({path:'../.runtime/raw-inbound-acceptance-20261103/raw-'+width+'.png'})})
test('receipt retry keeps input and request id, version conflict requires reload',async({page})=>{
 const {dialog:d,posts}=await setup(page,{},undefined,['响应暂不可用','申请版本已变化，请重新加载']);await d.getByPlaceholder('重量不同必须填写').fill('保留输入');const button=d.getByRole('button',{name:'本人库管验收',exact:true});await button.click();await expect(d).toContainText('响应暂不可用');await expect(d.getByPlaceholder('重量不同必须填写')).toHaveValue('保留输入');await button.click();await expect(d).toContainText('申请版本已变化，请重新加载');await expect(button).toBeDisabled();expect(posts).toHaveLength(2);expect(posts[0].body.requestId).toBe(posts[1].body.requestId);await d.getByRole('button',{name:'重新加载',exact:true}).click();await expect(button).toBeEnabled()
})
test('deleted and missing historical actors never become bare account ids',async({page})=>{
 const {dialog:d}=await setup(page,{group:{status:'CONFIRMED',confirmedBy:'99',confirmedByDeleted:'2'},events:[{actorId:'99',actorDeleted:'2',action:'CONFIRM',content:'{"lines":[{"lineId":"81","netWeight":"22","differenceReason":"复称一致"}]}'},{actorId:'100',action:'REVERSE',content:'{}'}]});await expect(d).toContainText('实际验收人：账号已删除');await d.getByText('验收及修订历史',{exact:true}).click();await expect(d).toContainText('账号已删除');await expect(d).toContainText('历史未记录姓名');await expect(d).toContainText('冲销');await d.locator('.el-table__expand-icon').first().click();await expect(d).toContainText('复称一致');await expect(d.getByRole('cell',{name:'RAW-01',exact:true})).toBeVisible()
})
for(const [kind,type,stage] of [['OUTBOUND','PRODUCTION_OUT','RECIPIENT'],['OUTBOUND','SEMI_OUT','RECIPIENT'],['OUTBOUND','PRODUCT_OUT','RECIPIENT'],['HANDOVER','SEMI_IN','KEEPER'],['PRODUCT_RETURN','PRODUCT_RETURN','KEEPER']] as const)test(type+' preserves recipient, processing context and pieces',async({page})=>{
 const category=type==='PRODUCT_OUT'||type==='PRODUCT_RETURN'?'FINISHED':'SEMI'
 const {dialog:d,posts}=await setup(page,{kind,stage,processors:kind==='HANDOVER'?[{actorId:'9',name:'加工乙',submitted:true}]:[],group:{type,recipientId:'7',recipientName:'领料乙',lines:[{...structuredClone(line),line:{category,code:'PRODUCT-01',quantity:10},stock:{category,measurementMode:'PIECE'}}]}},['metal:inventory:list','metal:warehouse-order:accept','metal:warehouse-order:receive']);await expect(d).toContainText('领用人：领料乙');await expect(d.getByRole('columnheader',{name:'数量 / 参考',exact:true})).toBeVisible();if(kind==='HANDOVER'){await expect(d).toContainText('加工乙');await expect(d.getByRole('columnheader',{name:'加工人员净重（g）',exact:true})).toBeVisible()}await d.getByRole('button',{name:stage==='RECIPIENT'?'本人实领确认':'本人库管验收',exact:true}).click();await expect.poll(()=>posts.length).toBe(1);expect(posts[0].body.lines[0].quantity).toBe(10)
})
for(const [name,status,label,confirmLabel] of [['cancel','WAITING','取消原因','确认取消申请'],['reverse','POSTED','冲销原因','确认整体冲销']] as const)test(name+' only submits its own reason',async({page})=>{
 const {dialog:d,posts}=await setup(page,{status,requestActor:'7'},['metal:inventory:list','metal:warehouse-order:add','metal:warehouse-order:reverse']);await d.getByRole('button',{name:name==='cancel'?'取消申请':'整体冲销',exact:true}).click();await expect(d.getByText(label,{exact:true})).toBeVisible();await d.getByRole('button',{name:confirmLabel,exact:true}).click();await expect(d).toContainText('请填写'+label);expect(posts).toHaveLength(0);await d.getByPlaceholder('请填写'+label).fill('本次'+label);await d.getByRole('button',{name:confirmLabel,exact:true}).click();await expect.poll(()=>posts.length).toBe(1);expect(posts[0]).toMatchObject({path:'/metal/warehouse-flows/5/'+name,body:{version:0,reason:'本次'+label}})
})
test('returned raw receipt revises declared weight with its own reason',async({page})=>{
 const {dialog:d,posts}=await setup(page,{status:'RETURNED',requestActor:'7'},['metal:inventory:list','metal:warehouse-order:add']);await expect(d.getByRole('button',{name:'退回修订',exact:true})).toHaveCount(0);await d.getByRole('button',{name:'修订申报明细',exact:true}).click();await expect(d.getByText('修订原因',{exact:true})).toBeVisible();await d.locator('.flow-group .el-input input').first().fill('23');await d.getByPlaceholder('请填写修订原因').fill('重核申报');await d.getByRole('button',{name:'保存修订',exact:true}).click();await expect.poll(()=>posts.length).toBe(1);expect(posts[0]).toMatchObject({path:'/metal/warehouse-flows/5/revise',body:{reason:'重核申报',documents:[{lines:[{netWeight:'23.00',quantity:0}]}]}})
})
test('historical raw revision missing category never displays pieces',async({page})=>{
 const {dialog:d}=await setup(page,{revisions:[{id:'80',revision:0,orderNo:'OLD-01',warehouseName:'原料仓',lines:[{...structuredClone(line),line:{code:'OLD-RAW',quantity:0}}]}]});await d.getByText('验收及修订历史',{exact:true}).click();await expect(d).toContainText('OLD-RAW');await expect(d).not.toContainText('件')
})
test('administrator permissions still cannot accept for a different keeper',async({page})=>{const {dialog:d}=await setup(page,{group:{keeperId:'9'}},['*:*:*']);await expect(d).toContainText('请由仓管员仓管甲登录验收');await expect(d.getByRole('button',{name:'本人库管验收',exact:true})).toHaveCount(0)})
test('multi-warehouse receipt only accepts its waiting warehouse',async({page})=>{
 const groups=[{id:'8',keeperId:'7',keeperName:'仓管甲',confirmedBy:'7',confirmedByName:'仓管甲',status:'CONFIRMED',warehouseName:'一仓',orderNo:'IN-1',type:'RAW_IN',order:{},lines:[structuredClone(line)]},{id:'9',keeperId:'7',keeperName:'仓管甲',status:'WAITING',warehouseName:'二仓',orderNo:'IN-2',type:'RAW_IN',order:{},lines:[{...structuredClone(line),id:'82'}]}]
 const {dialog:d,posts}=await setup(page,{status:'COLLECTING',groups});await expect(d).toContainText('部分仓库已验收');await expect(d).toContainText('本仓已验收，等待其他仓库完成');await expect(d.getByRole('button',{name:'本人库管验收',exact:true})).toHaveCount(1);await d.getByRole('button',{name:'本人库管验收',exact:true}).click();await expect.poll(()=>posts.length).toBe(1);expect(posts[0].path).toBe('/metal/warehouse-flows/5/groups/9/confirm')
})
