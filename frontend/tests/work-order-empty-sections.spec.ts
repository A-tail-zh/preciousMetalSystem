import {test,expect,type Page,type Locator} from '@playwright/test'

const titles:Record<string,string>={progress:'工序数量覆盖',items:'产品完成情况',stocks:'剩余原料／半成品库存',releases:'释放记录',uses:'跨工单来源与去向',closures:'关闭操作记录'}
const row:Record<string,any>={
 progress:{processName:'成型',localQuantity:6,externalQuantity:0,coveredQuantity:6},
 items:{productSnapshot:'{"name":"测试产品"}',plannedQuantity:10,completedQuantity:6,cancelledQuantity:4},
 stocks:{inventoryId:'S',category:'SEMI',name:'测试半成品',quantity:4,netWeight:'40.000000',releasedQuantity:0,unreleasedQuantity:4,processName:'成型'},
 releases:{releaseId:'R',name:'测试半成品',quantity:4,netWeight:'40.000000',availableQuantity:4,availableWeight:'40.000000',status:'ACTIVE'},
 uses:{releaseId:'R',orderNo:'OUT-TEST',targetWorkOrderNo:'WO-TARGET',quantity:2,netWeight:'20.000000',status:'POSTED'},
 closures:{createTime:'2026-10-24 00:00:00',action:'CLOSE',reason:'不再继续加工'}
}
const emptyFlow=()=>({workOrder:{workOrderId:'W',version:1,status:'IN_PROGRESS'},progress:[],items:[],stocks:[],releases:[],uses:[],closures:[]})
async function cell(table:Locator,title:string){const headers=await table.locator('thead th .cell').allTextContents(),index=headers.findIndex(value=>value.trim()===title);expect(index,'missing column '+title).toBeGreaterThanOrEqual(0);return table.locator('.el-table__body .el-table__row').first().locator('td').nth(index)}
test('重量前处理显示克数，不显示件数覆盖额度',async({page})=>{
 const {root}=await setup(page,'SemiFlowPanel',{...emptyFlow(),progress:[{processName:'酸洗',processRole:'ACID_WASH',outputStage:'RAW',measurementMode:'WEIGHT',localQuantity:0,outputWeight:'95.00',externalQuantity:0,skippedQuantity:0,coveredQuantity:0}]})
 const table=root.locator('.el-table')
 await expect(await cell(table,'产出阶段')).toHaveText('前处理原料');await expect(await cell(table,'本单加工')).toHaveText('95.00g');await expect(await cell(table,'计量方式')).toHaveText('按重量')
 for(const title of ['外部半成品接入(件)','许可跳过(件)','数量覆盖合计(件)'])await expect(await cell(table,title)).toHaveText('—')
})
async function setup(page:Page,component='SemiFlowPanel',initial:any=emptyFlow(),initialFailure=false){
 let data=initial,fail=initialFailure,completed=0
 await page.context().addCookies([{name:'Admin-Token',value:'empty-sections-test',url:'http://127.0.0.1:5173'}])
 await page.route('**/dev-api/**',async route=>{
  const path=new URL(route.request().url()).pathname;const target=path.endsWith('/semi-flow')||path.endsWith('/auto-release')
  let result:any={code:200,data:[]}
  if(path.endsWith('/getInfo'))result={code:200,user:{userId:1,userName:'admin'},roles:['admin'],permissions:['*:*:*']}
  else if(target)result=fail?{code:500,msg:'隔离加载失败'}:{code:200,data}
  await route.fulfill({json:result});if(target)completed++
 })
 await page.goto('/index')
 await page.evaluate(async component=>{
  const{createApp}=await import('/node_modules/.vite/deps/vue.js'),{default:ElementPlus}=await import('/node_modules/.vite/deps/element-plus.js'),{default:C}=await import('/src/views/metal/work-order/'+component+'.vue')
  const el=document.createElement('div');el.dataset.testid='empty-sections-host';document.body.append(el)
  ;(window as any).__emptySections=createApp(C,{workOrderId:'W'}).use(ElementPlus).mount(el)
 },component)
 await expect.poll(()=>completed).toBe(1)
 return{root:page.getByTestId('empty-sections-host'),set(value:any){data=value;fail=false},fail(){fail=true},async load(){await page.evaluate(()=>(window as any).__emptySections.$.setupState.load())}}
}
test('空流转表格与标题均隐藏，关闭工单入口保留',async({page})=>{
 const{root}=await setup(page)
 for(const title of Object.values(titles))await expect(root.getByRole('heading',{name:title,exact:true})).toHaveCount(0)
 await expect(root.locator('.el-table')).toHaveCount(0);await expect(root.getByRole('button',{name:'部分完工关闭',exact:true})).toBeVisible()
})
for(const key of Object.keys(titles))test(`${titles[key]}有记录才显示`,async({page})=>{
 const{root}=await setup(page,'SemiFlowPanel',{...emptyFlow(),[key]:[row[key]]})
 await expect(root.getByRole('heading',{name:titles[key],exact:true})).toBeVisible();await expect(root.locator('.el-table')).toHaveCount(1)
 for(const other of Object.keys(titles).filter(k=>k!==key))await expect(root.getByRole('heading',{name:titles[other],exact:true})).toHaveCount(0)
})
test('RAW库存显示净重，不显示件数或半成品释放入口',async({page})=>{
 const {root}=await setup(page,'SemiFlowPanel',{...emptyFlow(),workOrder:{id:'W',version:1,status:'COMPLETED'},stocks:[{...row.stocks,category:'RAW',measurementMode:'WEIGHT',quantity:999,unreleasedQuantity:999}]})
 await expect(root.getByRole('heading',{name:titles.stocks,exact:true})).toBeVisible();const table=root.locator('.el-table');await expect(await cell(table,'实物净重(g)')).toHaveText('40.00')
 for(const title of ['实物库存(件)','已释放未领(件)','尚未释放(件)'])await expect(await cell(table,title)).toHaveText('—')
 await expect(await cell(table,'参考件数（选填）')).toHaveText('未记录')
 await expect(table.getByRole('button',{name:'释放',exact:true})).toHaveCount(0)
})
test('刷新后有记录显示，记录清空后整段隐藏',async({page})=>{
 const f=await setup(page);f.set({...emptyFlow(),releases:[row.releases]})
 await f.root.getByRole('button',{name:'刷新',exact:true}).click();await expect(f.root.getByRole('heading',{name:'释放记录',exact:true})).toBeVisible()
 f.set(emptyFlow());await f.root.getByRole('button',{name:'刷新',exact:true}).click();await expect(f.root.getByRole('heading',{name:'释放记录',exact:true})).toHaveCount(0)
})
test('未触发自动释放且无结果时隐藏整张卡片',async({page})=>{
 const{root}=await setup(page,'AutoSemiReleaseStatus',{autoSemiReleaseEligible:true,event:null,results:[]})
 await expect(root.locator('.auto-status')).toHaveCount(0);await expect(root).not.toContainText('尚未触发')
})
for(const status of ['PENDING','NO_BALANCE','FAILED','CANCELLED'])test(`自动释放已有${status}任务时保留状态`,async({page})=>{
 const{root}=await setup(page,'AutoSemiReleaseStatus',{autoSemiReleaseEligible:true,event:{status,message:'已记录任务'},results:[]})
 await expect(root.locator('.auto-status')).toBeVisible();await expect(root).toContainText('已记录任务')
})
test('自动释放只有结果记录时也显示',async({page})=>{
 const{root}=await setup(page,'AutoSemiReleaseStatus',{event:null,results:[{inventoryId:'S',quantity:2,netWeight:'20.000000',status:'RELEASED'}]})
 await expect(root.locator('.auto-status .el-table')).toBeVisible();await expect(root).toContainText('20.00')
})
test('自动释放从未触发变为有任务后正常显示',async({page})=>{
 const f=await setup(page,'AutoSemiReleaseStatus',{event:null,results:[]});await expect(f.root.locator('.auto-status')).toHaveCount(0)
 f.set({event:{status:'PENDING'},results:[]});await f.load();await expect(f.root.locator('.auto-status')).toBeVisible();await expect(f.root).toContainText('待处理')
})
test('自动释放查询失败保留错误与重试，不伪装成无数据',async({page})=>{
 const f=await setup(page,'AutoSemiReleaseStatus',null,true);await expect(f.root.getByRole('alert')).toContainText('自动释放状态更新失败');await expect(f.root.locator('.auto-status')).toBeVisible();await expect(f.root).not.toContainText('尚未触发')
 f.set({event:{status:'NO_BALANCE'},results:[]});await f.root.getByRole('button',{name:'刷新状态',exact:true}).click();await expect(f.root).toContainText('无需释放');await expect(f.root.getByRole('alert')).toHaveCount(0)
})
