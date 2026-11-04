import {test,expect,type Page} from '@playwright/test'

type Fixture={reports?:number;plans?:number;secondPlans?:number;longNames?:boolean;state?:'loading'|'error'}
const productNames=['产品一','产品二']
async function setup(page:Page,config:Fixture={}){
 let current={...config},release!:()=>void,reportCalls=0
 const gate=new Promise<void>(resolve=>release=resolve)
 const plans=(n:number)=>Array.from({length:n},(_,i)=>({processId:String(i+1),processName:(current.longNames?'这是需要完整换行展示的很长加工工序名称':'加工工序')+(i+1),loss:'1.00',lossUnit:'PERCENT',measurementMode:'PIECE',startTime:'2026-11-03 08:00:00',endTime:'2026-11-03 10:00:00',actualStartTime:'2026-11-03 08:30:00',actualEndTime:null}))
 const reportRows=()=>Array.from({length:current.reports??3},(_,i)=>({id:String(200+i),batchId:String(100+i),batchNo:'批次'+(i+1),itemId:'I'+(i%2+1),processId:'1',productName:productNames[i%2],processName:current.longNames?'这是需要完整换行展示的很长加工工序名称1':'加工工序1',theoreticalLoss:'1.00',lossUnit:'PERCENT',lossWeight:String(i+1),lossRatePercent:'1.00',status:'NORMAL',settledTime:'2026-11-03 12:00:00'}))
 await page.context().addCookies([{name:'Admin-Token',value:'card-height-test',url:'http://127.0.0.1:5173'}])
 await page.route('**/dev-api/**',async route=>{
  const path=new URL(route.request().url()).pathname.replace('/dev-api','');let result:any={code:200,data:[],rows:[],total:0}
  if(path==='/getInfo')result={...result,user:{userId:1,userName:'admin'},roles:['admin'],permissions:['metal:work-order:list','metal:work-order:query','metal:work-report:query']}
  else if(path==='/getRouters')result.data=[{path:'/metal',component:'Layout',children:[{path:'work-order',component:'metal/work-order/index',name:'HeightOrder',meta:{title:'生产工单管理',noCache:true}}]}]
  else if(path==='/metal/work-order/80001')result.data={id:'80001',version:0,orderNo:'WO-CARD-HEIGHT',factoryId:'1',factoryName:'隔离布局工厂',productionLineId:'2',lineName:'布局产线',ownerId:'3',ownerName:'负责人',customerId:'4',customerVersion:0,customer:'布局客户',customerSnapshot:{name:'布局客户',companyName:'布局公司',address:'测试地址',contactName:'联系人',contactPhone:'13812345678',department:null,version:0},processingType:'MATERIAL',orderDate:'2026-11-03',createTime:'2026-11-03 07:00:00',creatorName:'测试人员',status:'IN_PROGRESS',items:productNames.map((name,i)=>({id:'I'+(i+1),productId:String(10+i),quantity:10,snapshot:{code:'P'+(i+1),name,productType:'杆',metalTypeName:'铱',weight:'10.00'},plans:plans(i===0?current.plans??3:current.secondPlans??2)})),reviews:[{action:'CREATE',actorName:'测试人员',createTime:'2026-11-03 07:00:00',reason:'测试记录'}]}
  else if(path==='/metal/work-order/80001/reports'){
   reportCalls++;const state=current.state,rows=reportRows()
   if(state==='loading')await gate
   result=state==='error'?{code:500,msg:'隔离加载失败'}:{code:200,data:rows}
  }
  else if(path==='/metal/work-order/80001/materials')result.data=[]
  else if(path.endsWith('/semi-flow'))result.data={workOrder:{workOrderId:'80001',version:0,status:'IN_PROGRESS'},progress:[],items:[],stocks:[],releases:[],uses:[],closures:[]}
  else if(path.endsWith('/weight-balance'))result.data={workOrderNo:'WO-CARD-HEIGHT',totals:{},status:'BALANCED',items:[],pending:[],anomalies:[],snapshotToken:'layout-fixture'}
  else if(path.endsWith('/auto-release'))result.data={event:null,results:[],autoSemiReleaseEligible:true}
  else if(path.endsWith('/closeout-context/80001'))result.data={factoryId:'1',version:0,status:'IN_PROGRESS',factoryIssuedWeight:'0',factoryReturnedWeight:'0',factoryNetInputWeight:'0',customerRemainingWeight:'0',factoryRemainingWeight:'0',factorySupplement:[],lots:[],offsetOrders:[],flows:[]}
  await route.fulfill({json:result})
 })
 async function ready(){await expect(page.locator('.detail-columns')).toBeVisible();if(!current.state){if(current.reports===0)await expect(page.locator('.reports')).toContainText('暂无报工记录');else await expect(page.locator('.reports')).toContainText('加工工序')}}
 await page.goto('/metal/work-order?id=80001');await ready()
 return{release,async update(next:Fixture){current={...next};const previous=reportCalls;await page.reload();await expect.poll(()=>reportCalls).toBe(previous+1);await ready()}}
}
const tableRows=(page:Page,section:string)=>page.locator('.'+section+' .el-table__body-wrapper tbody tr')
async function expectRecords(page:Page,reports:number,firstPlans:number,secondPlans:number){
 const plans=tableRows(page,'progress'),rows=tableRows(page,'reports')
 await expect(plans).toHaveCount(firstPlans+secondPlans);await expect(rows).toHaveCount(reports)
 for(let i=0;i<firstPlans+secondPlans;i++)await expect(plans.nth(i).locator('td').nth(0)).toHaveText(i<firstPlans?'产品一':'产品二')
 for(let i=0;i<reports;i++){await expect(rows.nth(i).locator('td').nth(0)).toHaveText(productNames[i%2]);await expect(rows.nth(i).locator('td').nth(1)).toHaveText('加工工序1');await expect(rows.nth(i).locator('td').nth(3)).toHaveText((i+1).toFixed(2))}
}
async function geometry(page:Page,selector='.detail-columns > .detail-card'){
 return page.locator(selector).evaluateAll(cards=>cards.map(card=>{
  const box=card.getBoundingClientRect(),style=getComputedStyle(card)
  const children=[...card.children].filter(e=>!e.classList.contains('el-loading-mask')&&e.getBoundingClientRect().height>0)
  const bottom=Math.max(...children.map(e=>e.getBoundingClientRect().bottom))
  return{height:box.height,top:box.top,natural:bottom-box.top+parseFloat(style.paddingBottom)+parseFloat(style.borderBottomWidth)}
 }))
}
async function expectFit(page:Page){
 await expect.poll(async()=>{const cards=await geometry(page);return cards.length===2&&cards.every(c=>Math.abs(c.height-c.natural)<=2)&&cards[1].top>=cards[0].top+cards[0].height}).toBe(true)
 expect(await page.evaluate(()=>document.documentElement.scrollWidth)).toBeLessThanOrEqual(await page.evaluate(()=>innerWidth))
}
for(const width of [1920,1440,768,390])test(`工单详情卡片${width}px跨产品单列按内容定高`,async({page},testInfo)=>{
 await page.setViewportSize({width,height:1000});await setup(page,{reports:3,plans:3,secondPlans:2})
 await expectRecords(page,3,3,2);await expectFit(page)
 const operation=page.locator('.detail-page > .detail-card').filter({has:page.getByText('操作记录',{exact:true})});await expect(operation).toContainText('操作记录')
 expect((await operation.boundingBox())!.height).toBeLessThan(250)
 await expect.poll(async()=>{const[card]=await geometry(page,'.basic-card');return Math.abs(card.height-card.natural)<=2}).toBe(true)
 await page.screenshot({path:testInfo.outputPath('work-order-card-height-'+width+'.png'),fullPage:true,animations:'disabled'})
})
for(const [reports,plans] of [[0,0],[0,3],[10,1],[1,10]])test(`单列${reports}批报工及每产品${plans}道工序分别由内容撑高`,async({page})=>{
 await page.setViewportSize({width:1440,height:1000});await setup(page,{reports,plans,secondPlans:plans})
 await expectRecords(page,reports,plans,plans);await expectFit(page)
})
test('跨产品数据集刷新后记录及卡片高度增加并缩回',async({page})=>{
 await page.setViewportSize({width:1440,height:1000});const initial={reports:3,plans:3,secondPlans:2},f=await setup(page,initial)
 await expectRecords(page,3,3,2);await expectFit(page);const before=await geometry(page)
 await f.update({reports:10,plans:9,secondPlans:8});await expectRecords(page,10,9,8);await expectFit(page)
 const expanded=await geometry(page);for(let i=0;i<2;i++)expect(expanded[i].height).toBeGreaterThan(before[i].height+100)
 await f.update(initial);await expectRecords(page,3,3,2);await expectFit(page)
 const restored=await geometry(page);for(let i=0;i<2;i++)expect(Math.abs(restored[i].height-before[i].height)).toBeLessThanOrEqual(2)
})
test('报工加载中和加载结束均按内容定高',async({page})=>{
 await page.setViewportSize({width:1440,height:1000});const f=await setup(page,{state:'loading',reports:3,plans:1,secondPlans:1})
 try{await expect(page.locator('.reports')).toContainText('正在加载报工记录');await expect(page.locator('.reports')).not.toContainText('暂无报工记录');await expectFit(page)}finally{f.release()}
 await expectRecords(page,3,1,1);await expectFit(page);await expect(page.locator('.report-status')).toHaveCount(0)
})
test('报工失败保留错误和重试且不伪装为空态',async({page})=>{
 await page.setViewportSize({width:1440,height:1000});await setup(page,{state:'error',plans:1,secondPlans:1})
 await expect(page.locator('.report-error')).toBeVisible();await expect(page.locator('.report-error').getByRole('button',{name:'重试',exact:true})).toBeVisible()
 await expect(page.locator('.reports')).not.toContainText('暂无报工记录');await expectFit(page)
})
test('窄屏长名称与不同记录数量的卡片各自自然撑高',async({page})=>{
 await page.setViewportSize({width:390,height:1000});await setup(page,{reports:8,plans:1,secondPlans:1,longNames:true})
 await expect(page.locator('.reports')).toContainText('这是需要完整换行展示的很长加工工序名称1');await expect(tableRows(page,'reports')).toHaveCount(8);await expect(tableRows(page,'progress')).toHaveCount(2);await expectFit(page)
 const cards=await geometry(page);expect(cards[1].height).toBeGreaterThan(cards[0].height+100)
})
