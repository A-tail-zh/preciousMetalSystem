import {test,expect,type Page} from '@playwright/test'

type Fixture={reports?:number;plans?:number;secondPlans?:number;longNames?:boolean;state?:'loading'|'error'}
async function setup(page:Page,config:Fixture={}){
 let release!:()=>void;const gate=new Promise<void>(resolve=>release=resolve)
 const plans=(n:number)=>Array.from({length:n},(_,i)=>({processId:String(i+1),processName:config.longNames?'这是需要换行展示的很长工序名称用于核对卡片高度'+(i+1):'加工工序'+(i+1),startTime:'2026-10-23 08:00:00',endTime:'2026-10-23 10:00:00',loss:'0'}))
 const rows=Array.from({length:config.reports??3},(_,i)=>({itemId:'I1',processId:String(i+1),processName:config.longNames?'报工工序名称较长需要完整换行展示'+(i+1):'报工工序'+(i+1),lossWeight:'1.000000',averageLoss:'0.100000',status:'NORMAL'}))
 await page.context().addCookies([{name:'Admin-Token',value:'card-height-test',url:'http://127.0.0.1:5173'}])
 await page.route('**/dev-api/**',async route=>{
  const path=new URL(route.request().url()).pathname.replace('/dev-api','');let result:any={code:200,data:null,rows:[],total:0}
  if(path==='/getInfo')result={...result,user:{userId:1,userName:'admin'},roles:['admin'],permissions:['*:*:*']}
  else if(path==='/getRouters')result.data=[{path:'/metal',component:'Layout',children:[{path:'work-order',component:'metal/work-order/index',name:'HeightOrder',meta:{title:'生产工单管理',noCache:true}}]}]
  else if(path==='/metal/work-order/80001')result.data={id:'80001',orderNo:'WO-CARD-HEIGHT',factoryName:'隔离布局工厂',status:'IN_PROGRESS',items:[{id:'I1',quantity:10,snapshot:{name:'产品一'},plans:plans(config.plans??3)},{id:'I2',quantity:10,snapshot:{name:'产品二'},plans:plans(config.secondPlans??8)}],reviews:[{action:'CREATE',actorName:'测试人员',createTime:'2026-10-23 08:00:00',reason:'测试记录'}]}
  else if(path==='/metal/work-report/summary'){
   if(config.state==='loading')await gate
   result=config.state==='error'?{code:500,msg:'隔离加载失败'}:{code:200,data:rows}
  }
  await route.fulfill({json:result})
 })
 await page.goto('/metal/work-order?id=80001');await expect(page.locator('.detail-columns')).toBeVisible()
 if(!config.state){if(rows.length)await expect(page.locator('.reports')).toContainText('报工工序');else await expect(page.locator('.reports')).toContainText('暂无报工记录')}
 return{release}
}

async function geometry(page:Page){
 return page.locator('.detail-columns > .detail-card').evaluateAll(cards=>cards.map(card=>{
  const box=card.getBoundingClientRect(),style=getComputedStyle(card)
  const children=[...card.children].filter(e=>!e.classList.contains('el-loading-mask')&&e.getBoundingClientRect().height>0)
  const bottom=Math.max(...children.map(e=>e.getBoundingClientRect().bottom))
  return{height:box.height,top:box.top,natural:bottom-box.top+parseFloat(style.paddingBottom)+parseFloat(style.borderBottomWidth)}
 }))
}
async function expectFit(page:Page,stacked=false){
 await expect.poll(async()=>{
  const cards=await geometry(page);if(cards.length!==2)return false
  if(stacked)return cards.every(c=>Math.abs(c.height-c.natural)<=2)&&cards[1].top>=cards[0].top+cards[0].height
  return Math.abs(cards[0].height-cards[1].height)<=1&&Math.abs(cards[0].height-Math.max(...cards.map(c=>c.natural)))<=2
 }).toBe(true)
}

for(const width of [1920,1440,768,390])test(`工单详情卡片${width}px按内容定高`,async({page})=>{
 await page.setViewportSize({width,height:1000});await setup(page,{reports:3,plans:3})
 await expectFit(page,width<=1100)
 const operation=page.locator('.detail-page > .detail-card');await expect(operation).toContainText('操作记录')
 expect((await operation.boundingBox())!.height).toBeLessThan(250)
 expect((await page.locator('.basic-card').boundingBox())!.height).toBeGreaterThanOrEqual(270)
 await page.screenshot({path:`../.runtime/work-order-card-height-${width}.png`,fullPage:true,animations:'disabled'})
})
for(const [reports,plans] of [[0,0],[0,3],[10,1],[1,10]])test(`双列${reports}条报工及${plans}道工序由较长内容撑高`,async({page})=>{
 await page.setViewportSize({width:1440,height:1000});await setup(page,{reports,plans});await expectFit(page)
})
test('切换产品后卡片随内容增加和减少',async({page})=>{
 await page.setViewportSize({width:1440,height:1000});await setup(page,{reports:3,plans:3,secondPlans:10});await expectFit(page)
 const initial=(await geometry(page))[0].height
 await page.getByRole('tab',{name:/产品二/}).click();await expect(page.locator('.reports')).toContainText('暂无报工记录');await expectFit(page)
 expect((await geometry(page))[0].height).toBeGreaterThan(initial+100)
 await page.getByRole('tab',{name:/产品一/}).click();await expectFit(page);expect((await geometry(page))[0].height).toBeCloseTo(initial,0)
})
test('报工加载中和加载结束均按内容定高',async({page})=>{
 await page.setViewportSize({width:1440,height:1000});const f=await setup(page,{state:'loading',reports:3,plans:1})
 try{await expect(page.locator('.reports')).toContainText('正在加载报工记录');await expectFit(page)}finally{f.release()}
 await expect(page.locator('.reports')).toContainText('报工工序');await expectFit(page)
})
test('报工失败提示不撑出大片留白',async({page})=>{
 await page.setViewportSize({width:1440,height:1000});await setup(page,{state:'error',plans:1});await expect(page.locator('.report-error')).toBeVisible();await expectFit(page)
})
test('窄屏长名称与左右不同记录数量各自撑高',async({page})=>{
 await page.setViewportSize({width:390,height:1000});await setup(page,{reports:8,plans:1,longNames:true});await expect(page.locator('.reports')).toContainText('报工工序名称较长');await expectFit(page,true)
 const cards=await geometry(page);expect(cards[0].height).toBeGreaterThan(cards[1].height+100)
})
