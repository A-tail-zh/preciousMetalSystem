import {test,expect,type Page} from '@playwright/test'
import {resolve} from 'node:path'

const order=(id:string,flowVersion=1)=>({id,orderNo:'GD-'+id,factoryName:'甲厂',status:'IN_PROGRESS',flowVersion,products:[
 {id:id+'a',productName:'银杆',specification:'直径10mm',plannedQuantity:'100',completedQuantity:'10',steps:[{id:'s1',processName:'工序一',measurementMode:'PIECE',completedQuantity:'30'},{id:'s2',processName:'精密加工与表面处理工序二（长名称）',measurementMode:'PIECE',completedQuantity:'10'}]},
 {id:id+'b',productName:'铂环',specification:null,plannedQuantity:'200',completedQuantity:'0',steps:[{id:'s3',processName:'熔炼',measurementMode:'WEIGHT',completedQuantity:'0'}]}
]})
async function setup(page:Page){
 const state={fail:false,empty:false,requests:[] as string[],held:false,release:()=>{}}
 await page.context().addCookies([{name:'Admin-Token',value:'progress-test',url:'http://127.0.0.1:5218'}])
 await page.route('**/dev-api/**',async route=>{
  const url=new URL(route.request().url()),path=url.pathname.replace('/dev-api','')
  let result:any={code:200,data:[]}
  if(path==='/getInfo')result={code:200,user:{userId:1,userName:'admin',nickName:'看板测试'},roles:['admin'],permissions:['*:*:*']}
  else if(path==='/getRouters')result.data=[{path:'/',component:'Layout',children:[{path:'index',component:'index',name:'Index',meta:{title:'数据看板'}}]}]
  else if(path==='/metal/dashboard/factories')result.data={canSelectAll:true,factories:[{id:'7',name:'甲厂'},{id:'8',name:'乙厂'}]}
  else if(path==='/metal/dashboard')result.data={workOrders:{total:12,inProgress:8,completed:1},warnings:{},quotations:{},trend:[],ranking:[],factories:[],distribution:[],warningCenter:[]}
  else if(path==='/metal/dashboard/production-progress'){
   state.requests.push(url.search)
   const factory=url.searchParams.get('factoryId'),pageNum=url.searchParams.get('pageNum')
   if(state.held&&factory==='7')await new Promise<void>(done=>{state.release=done})
   result=state.fail?{code:500,msg:'模拟加载失败'}:{code:200,data:state.empty?{rows:[],total:0}:{rows:pageNum==='2'?[order('page2')]:[order(factory==='8'?'factory8':factory==='7'?'factory7':'001'),order('legacy',0)],total:11}}
  }
  await route.fulfill({json:result})
 })
 return state
}

test('product progress distinguishes counts, weight and legacy; desktop layouts',async({page})=>{
 await setup(page);await page.goto('/index');await page.waitForLoadState('networkidle')
 const board=page.getByRole('region',{name:'生产工单进度'})
 await expect(board).toContainText('10 / 100 件');await expect(board).toContainText('暂无执行数据')
 await board.getByRole('button',{name:'查看工单 GD-001',exact:true}).click()
 const dialog=page.getByRole('dialog',{name:'工单进度详情'})
 await expect(dialog).toContainText('30/100')
 await dialog.getByRole('button',{name:'下一产品'}).click();await expect(dialog).toContainText('按重量计量')
 await page.keyboard.press('Escape')
 await expect(dialog).toBeHidden()

 for(const width of [1920,1440,1366,900]){
  await page.setViewportSize({width,height:width===1920?1080:768})
  await expect(board.getByRole('button',{name:'刷新进度'})).toHaveCount(0)
  await expect(board).not.toContainText('30秒自动刷新')
  const plot=await page.locator('.distribution-plot').boundingBox(),label=await page.locator('.distribution-plot .empty-overlay').boundingBox()
  expect(Math.abs(label!.x+label!.width/2-plot!.x-plot!.width/2)).toBeLessThan(1)
  expect(Math.abs(label!.y+label!.height/2-plot!.y-plot!.height/2)).toBeLessThan(1)
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth && document.documentElement.scrollHeight<=innerHeight)).toBe(true)
  await expect(page.locator('.chart-card')).toHaveCount(5)
  for(const panel of await page.locator('.chart-card,.production-progress').all()){const box=await panel.boundingBox();expect(box!.y+box!.height).toBeLessThanOrEqual(width===1920?1080:768)}
  await page.screenshot({path:resolve('../output/dashboard-progress-'+width+'.png'),fullPage:true})
 }
})

test('pagination, failure recovery and empty result',async({page})=>{
 await page.clock.install()
 const state=await setup(page);await page.goto('/index')
 const board=page.getByRole('region',{name:'生产工单进度'})
 await expect(board).toContainText('GD-001')
 await board.getByRole('button',{name:'下一页工单',exact:true}).click();await expect(board).toContainText('GD-page2')
 expect(state.requests.at(-1)).toContain('pageNum=2')
 state.fail=true;await page.clock.fastForward(30001);await expect(board.getByRole('alert')).toBeVisible();await expect(board).not.toContainText('GD-page2')
 await expect(board.getByRole('alert')).toHaveText('生产进度加载失败，将自动重试')
 state.fail=false;state.empty=true;await page.clock.fastForward(30001);await expect(board).toContainText('暂无未开工或生产中的工单')
})

test('polls every 30 seconds and ignores late response from previous factory',async({page})=>{
 await page.clock.install();const state=await setup(page);await page.goto('/index')
 const board=page.getByRole('region',{name:'生产工单进度'})
 await expect(board).toContainText('GD-001');const count=state.requests.length
 await page.clock.fastForward(30001);await expect.poll(()=>state.requests.length).toBe(count+1)
 await expect(board).toHaveAttribute('aria-busy','false')
 state.held=true
 await page.locator('.dashboard-factory .el-select__wrapper').click();await page.getByRole('option',{name:'甲厂',exact:true}).click()
 await expect.poll(()=>state.requests.some(q=>q.includes('factoryId=7'))).toBe(true)
 const heldCount=state.requests.length;await page.clock.fastForward(30001);expect(state.requests.length).toBe(heldCount)
 await page.locator('.dashboard-factory .el-select__wrapper').click();await page.getByRole('option',{name:'乙厂',exact:true}).click()
 await expect(board).toContainText('GD-factory8');state.release()
 await page.waitForLoadState('networkidle');await expect(board).not.toContainText('GD-factory7')
})

test('search and status use server pagination; details close on factory changes',async({page})=>{
 const state=await setup(page);await page.goto('/index')
 const board=page.getByRole('region',{name:'生产工单进度'})
 await expect(board).toContainText('GD-001')
 await board.getByRole('textbox',{name:'搜索工单号'}).fill('GD-00')
 await expect.poll(()=>state.requests.at(-1)).toContain('orderNo=GD-00')
 await board.locator('.el-select__wrapper').click();await page.getByRole('option',{name:'未开工',exact:true}).click()
 await expect.poll(()=>state.requests.at(-1)).toContain('status=CREATED')
 expect(state.requests.at(-1)).toContain('pageNum=1')
 await board.getByRole('button',{name:'查看工单 GD-001',exact:true}).click()
 await expect(page.getByRole('dialog',{name:'工单进度详情'})).toBeVisible()
 await page.keyboard.press('Escape')
 await board.getByRole('spinbutton',{name:'跳转页码'}).fill('2')
 await board.getByRole('button',{name:'跳转',exact:true}).click()
 await expect(board).toContainText('GD-page2')
})

test('many warnings and process details fit without panel scrollbars',async({page})=>{
 await setup(page)
 await page.route('**/dev-api/metal/dashboard',route=>route.fulfill({json:{code:200,data:{workOrders:{},warnings:{},quotations:{categories:Array.from({length:20},(_,i)=>'产品'+i),categoryCounts:{}},trend:[],ranking:[],factories:[],distribution:[],warningCenter:Array.from({length:6},(_,i)=>({id:String(i),triggerTime:'2026-11-10 10:00:00',factoryName:'工厂'+i,processName:'工序'+i,thresholdValue:'1.25',lossUnit:'PERCENT',status:'PENDING'}))}}}))
 const large=order('large');large.products[0].steps=Array.from({length:13},(_,i)=>({id:String(i),processName:'工序'+i,measurementMode:'PIECE',completedQuantity:'10'}))
 await page.route('**/dev-api/metal/dashboard/production-progress?*',route=>route.fulfill({json:{code:200,data:{rows:[large],total:1}}}))
 await page.goto('/index')
 for(const viewport of [{width:1366,height:768},{width:900,height:888}]){
  await page.setViewportSize(viewport)
  await expect(page.getByRole('button',{name:'查看工单 GD-large'})).toBeVisible()
  await page.waitForTimeout(150)
  for(const el of await page.locator('.warning-table .el-scrollbar__wrap,.production-progress,.order-list').all()){const size=await el.evaluate(e=>({class:e.className,w:e.clientWidth,sw:e.scrollWidth,h:e.clientHeight,sh:e.scrollHeight}));expect(size.sw<=size.w+1&&size.sh<=size.h+1,JSON.stringify({viewport,size})).toBe(true)}
 }
 await page.getByRole('button',{name:'查看工单 GD-large'}).click()
 const dialog=page.getByRole('dialog',{name:'工单进度详情'})
 await expect(dialog.locator('.process-chain li')).toHaveCount(4)
 await dialog.getByRole('button',{name:'下一组工序'}).click()
 await expect(dialog).toContainText('工序4')
 await page.keyboard.press('Escape')
 await page.getByRole('button',{name:'查看报价覆盖产品'}).click()
 await expect(page.getByRole('dialog',{name:'报价覆盖产品'})).toBeVisible()
})

 test('hidden pages skip automatic progress requests',async({page})=>{
 await page.clock.install();const state=await setup(page);await page.goto('/index')
 const board=page.getByRole('region',{name:'生产工单进度'});await expect(board).toContainText('GD-001');await expect(board).toHaveAttribute('aria-busy','false')
 await page.evaluate(()=>Object.defineProperty(document,'hidden',{configurable:true,get:()=>true}))
 const count=state.requests.length;await page.clock.fastForward(30001);expect(state.requests.length).toBe(count)
 await page.evaluate(()=>Object.defineProperty(document,'hidden',{configurable:true,get:()=>false}))
 await page.clock.fastForward(30001);await expect.poll(()=>state.requests.length).toBe(count+1)
})
