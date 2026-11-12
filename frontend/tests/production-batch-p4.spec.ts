import {test,expect,type Page} from '@playwright/test'

async function fixture(page:Page,baseURL:string,mode:'NORMAL'|'REVERSED'|'LEGACY'='NORMAL'){
 const writes:any[]=[],traceQueries:any[]=[];const reversed=mode==='REVERSED'
 const batch={id:'1011',number:'PB-A100',orderId:'7',orderItemId:'31',lifecycle:'SPLIT',plannedQuantity:100,version:4}
 const children=[{batchId:'1012',number:'PB-A1-30',lifecycle:'ACTIVE',plannedQuantity:30,version:1},{batchId:'1013',number:'PB-A2-70',lifecycle:'ACTIVE',plannedQuantity:70,version:2}]
 const order={id:'7',orderNo:'GD-P4',number:'GD-P4',status:'IN_PROGRESS',version:4,factoryId:'101',factoryName:'本厂',processingType:'DIRECT_CUSTOM',customerSnapshot:'{}'}
 const operation=()=>({operationId:'8001',action:'SPLIT',reason:'已产30先走，70移交',createdAt:'2026-11-12T10:00:00+08:00',reversedBy:reversed?'8002':null,canReverse:!reversed})
 const nodes=()=>[{...batch,batchId:batch.id,lifecycle:reversed?'ACTIVE':'SPLIT'},...children.map(c=>({...c,lifecycle:reversed?'CANCELLED':'ACTIVE'}))]
 const execution={executionId:'301',batchId:'1011',orderStepId:'41',status:reversed?'OPEN':'SPLIT_TRANSFERRED',version:6,inputWeight:'1000.00',outputWeight:'300.00',carryInWeight:'0.00',carryOutWeight:reversed?'0.00':'700.00'}
 await page.context().addCookies([{name:'Admin-Token',value:'p4-mock-only',url:baseURL}])
 await page.route('**/*-api/**',async route=>{
  const req=route.request(),url=new URL(req.url()),path=url.pathname.replace(/^\/(dev|prod)-api/,''),method=req.method();let response:any={code:200,data:[],rows:[],total:0};if(method!=='GET'){writes.push({method,path});await route.fulfill({json:{code:400,msg:'Read-only test refuses mutation'}});return}
  if(path==='/getInfo')response={code:200,user:{userId:1,userName:'admin'},roles:['admin'],permissions:['*:*:*']}
  else if(path==='/getRouters')response.data=[]
  else if(path==='/metal/options/factories')response.data=[{id:'101',name:'本厂'}]
  else if(path==='/metal/production-order/list')response={code:200,rows:[{...order,factory:'本厂'}],total:1}
  else if(path==='/metal/production-order/7/flow')response.data={order,items:[{id:'31',product:{name:'金环'},quantity:100,steps:[]}],executions:[{...execution,id:'301',processorId:'81'},...(mode==='LEGACY'?[{id:'15',status:'OPEN',legacyAdopted:true,readOnly:true,targetBatchId:'1011',targetExecutionId:'301',writeBlockedReason:'历史已承接，原记录只读'}]:[])],documents:[],events:[]}
  else if(path==='/metal/production-order/7/report')response.data={executions:[{...execution,id:'301'}],orderTotals:{externalInputWeight:'1000.00',inProcessWeight:reversed?'700.00':'700.00',lossWeight:'0.00',balanceDifferenceWeight:'0.00'},notices:['内部移交不重复计为投入和产量']}
  else if(path==='/metal/production-batches/list')response={code:200,rows:[batch],total:1}
  else if(path==='/metal/production-batches/1011')response.data={...batch,executions:[{...execution,...(mode==='LEGACY'?{historicalOutputWeight:'4880.00',historicalOutputQuantity:0,effectiveOutputWeight:'5180.00',effectiveOutputQuantity:0}:{})}]}
  else if(path==='/metal/production-batches/1011/trace'){
   traceQueries.push(Object.fromEntries(url.searchParams));const cursor=url.searchParams.get('cursor');response.data={nodes:cursor?[nodes()[2]]:nodes().slice(0,2),edges:[{id:cursor?'502':'501',operationId:'8001',sourceBatchId:'1011',targetBatchId:cursor?'1013':'1012',action:'SPLIT',weight:cursor?'700.00':'300.00',quantity:cursor?70:30,reversed}],executions:[execution],allocations:[{allocationId:'6001',purpose:'WIP_TRANSFER',fromSourceId:'70',toSourceId:'71',weight:'700.00',quantity:70}],productionEvents:[{id:'2001',executionId:'301',batchId:'1011',documentId:'501',lineId:'502',eventType:'OUTPUT',stockId:'601',weight:'300.00',quantity:30,reversed:0}],documents:[{id:'501',number:'IN-P4-001',type:'SEMI_IN',status:'POSTED',batchId:'1011',executionId:'301'}],stockSlices:[{stockSliceId:'9001',stockId:'601',stockNumber:'ST-P4-601',batchId:'1012',originDocumentId:'501',originLineId:'502',originStepId:'41',weight:'300.00',quantity:30}],materialSources:[{sourceAllocationId:'71',parentSourceAllocationId:'70',rootIssueAllocationId:'49',issueDocumentId:'501',issueLineId:'502',sourceKind:'FACTORY',sourceOwnerId:'0',currentOwnerId:'88',weight:'700.00',quantity:70}],operations:[operation()],nextCursor:cursor?null:'cursor-next',truncated:!cursor}
  }
  else if(path==='/metal/storage-orders/options')response.data={factories:[{id:'101',name:'本厂'}],warehouses:[],people:[{id:'81',name:'加工甲',canReceive:true}],materials:[],products:[],customers:[],productionOrders:[],orderItems:[],executions:[],stocks:[]}
  else if(path==='/metal/storage-warehouses/options')response.data=[{id:'11',name:'原料库'}]
  else if(path==='/metal/storage-inventory/list')response={code:200,rows:[{id:'501',category:'RAW',code:'RAW-1',name:'铱料',batchNo:'ST-501',warehouseId:'11',netWeight:'1000.00',quantity:100}],total:1}
  else if(path==='/metal/storage-inventory/501')response.data={id:'501',code:'RAW-1',name:'铱料',batchNo:'ST-501',warehouseId:'11',netWeight:'1000.00',quantity:100,ownerships:[],stockSlices:[{stockSliceId:'9001',sourceBatchId:'1011',balanceWeight:'300.00',balanceQuantity:30},{stockSliceId:'9002',sourceBatchId:'1012',balanceWeight:'700.00',balanceQuantity:70}]}
  await route.fulfill({json:response})
 })
 async function openFlow(){await page.goto('/metal/production-order');const factory=page.getByRole('combobox',{name:'所属工厂',exact:true});await factory.press('ArrowDown');await page.getByRole('option',{name:'本厂',exact:true}).click();await page.getByRole('row').filter({hasText:'GD-P4'}).getByRole('button',{name:'查看',exact:true}).click();return page.getByRole('dialog',{name:'生产工单完整流程',exact:true})}
 async function openTrace(){const flow=await openFlow();await flow.locator('.production-batches').getByRole('button',{name:'双向追溯',exact:true}).click();const dialog=page.getByRole('dialog',{name:'生产批次双向追溯',exact:true});await expect(dialog.getByRole('heading',{name:'拆合操作历史',exact:true})).toBeVisible();return dialog}
 return {writes,traceQueries,openFlow,openTrace}
}

test('bidirectional trace loads every page and keeps reversed history distinct',async({page,baseURL})=>{
 const f=await fixture(page,baseURL!);const d=await f.openTrace();const nodeTable=d.locator('.el-table').first();await expect(nodeTable.getByRole('cell',{name:'PB-A1-30',exact:true})).toBeVisible();await expect(nodeTable.getByRole('cell',{name:'PB-A2-70',exact:true})).toHaveCount(0);await d.getByRole('button',{name:'加载更多追溯',exact:true}).click();await expect(nodeTable.getByRole('cell',{name:'PB-A2-70',exact:true})).toBeVisible();await expect(d.getByRole('button',{name:'加载更多追溯',exact:true})).toHaveCount(0);expect(f.traceQueries.map(q=>q.cursor)).toEqual([undefined,'cursor-next']);await d.getByRole('tab',{name:'实际库存份额',exact:true}).click();await expect(d.getByText('ST-P4-601',{exact:true})).toBeVisible();await d.getByRole('tab',{name:'逐来源与原领料',exact:true}).click();await expect(d.getByText('厂方供料',{exact:true})).toBeVisible();await expect(d.getByText('客户 #88',{exact:true})).toBeVisible();const picker=d.getByRole('combobox',{name:'追溯方向',exact:true});await picker.press('ArrowDown');const id=await picker.getAttribute('aria-controls');await page.locator(`[id="${id}"]`).getByRole('option',{name:'仅上游来源',exact:true}).click();await expect.poll(()=>f.traceQueries.at(-1)?.direction).toBe('UPSTREAM');expect(f.writes).toEqual([])
})

test('reversed transform history remains visible without any reversal action',async({page,baseURL})=>{
 const f=await fixture(page,baseURL!,'REVERSED'),d=await f.openTrace()
 await expect(d.getByText('已撤销（历史）',{exact:true})).toBeVisible();await expect(d.getByText('已撤销 / 操作8002',{exact:true})).toBeVisible()
 await expect(d.getByRole('button',{name:'撤销拆合',exact:true})).toHaveCount(0);await expect(page.getByRole('dialog',{name:'撤销批次拆合',exact:true})).toHaveCount(0)
 await d.getByRole('button',{name:'刷新追溯',exact:true}).click();await expect(d.getByText('已撤销 / 操作8002',{exact:true})).toBeVisible();expect(f.writes).toEqual([])
})

test('trace dialog keeps all mutation controls absent even with administrator permissions',async({page,baseURL})=>{
 const f=await fixture(page,baseURL!),d=await f.openTrace()
 for(const name of ['新建生产批次','进入工序','拆分','合并','完成生产批次','撤销拆合','确认撤销拆合'])await expect(d.getByRole('button',{name,exact:true})).toHaveCount(0)
 await d.getByRole('button',{name:'加载更多追溯',exact:true}).click();await d.getByRole('button',{name:'关闭',exact:true}).click();await expect(d).not.toBeVisible();expect(f.writes).toEqual([])
})

test('flow shows carried WIP without adding it to external input or output',async({page,baseURL})=>{
 const f=await fixture(page,baseURL!);const flow=await f.openFlow();await expect(flow.getByRole('columnheader',{name:'在制移交（g）',exact:true})).toBeVisible();await expect(flow.getByText('已移交',{exact:true})).toBeVisible();await flow.getByRole('tab',{name:'报工与损耗',exact:true}).click();await expect(flow.getByText('内部移交不重复计为投入和产量',{exact:true})).toBeVisible();const report=flow.getByRole('tabpanel',{name:'报工与损耗',exact:true});await expect(report.getByText('1000.00',{exact:true}).first()).toBeVisible();await expect(report.getByText('700.00',{exact:true}).first()).toBeVisible();expect(f.writes).toEqual([])
})

test('inventory separates physical ST number from production batch slices',async({page,baseURL})=>{
 const f=await fixture(page,baseURL!);await page.goto('/metal/storage-inventory');await page.getByRole('combobox',{name:'所属工厂',exact:true}).press('ArrowDown');await page.getByRole('option',{name:'本厂',exact:true}).click();await expect(page.getByRole('columnheader',{name:'实物库存号',exact:true})).toBeVisible();await page.getByRole('row').filter({hasText:'ST-501'}).getByRole('button',{name:'查看详情',exact:true}).click();const detail=page.getByRole('dialog',{name:'库存详情',exact:true});await expect(detail.getByText('ST-501',{exact:true})).toBeVisible();await expect(detail.getByText('PB身份 #1011',{exact:true})).toBeVisible();await expect(detail.getByRole('button',{name:'追溯生产批次',exact:true})).toHaveCount(2);expect(f.writes).toEqual([])
})

 test('adopted historical records stay read only and historical output is shown once',async({page,baseURL})=>{
 const f=await fixture(page,baseURL!,'LEGACY');const flow=await f.openFlow()
 const old=flow.locator('.execution-table').getByRole('row').filter({hasText:'已承接 → PB #1011'})
 await expect(old).toContainText('历史已承接')
 await expect(old.getByRole('button',{name:'补料',exact:true})).toBeDisabled()
 await expect(old.getByRole('button',{name:'结束本工序',exact:true})).toBeDisabled()
 await flow.locator('.production-batches').getByRole('button',{name:'查看加工记录',exact:true}).click()
 const detail=flow.locator('.batch-detail')
 await expect(detail.getByRole('columnheader',{name:'本记录产出（g）',exact:true})).toBeVisible()
 await expect(detail.getByRole('columnheader',{name:'历史承接产出（g）',exact:true})).toBeVisible()
 await expect(detail.getByRole('columnheader',{name:'阶段累计产出（g）',exact:true})).toBeVisible()
 await expect(detail.getByRole('cell',{name:'4880.00',exact:true})).toHaveCount(1)
 await expect(detail.getByRole('cell',{name:'5180.00',exact:true})).toHaveCount(1)
 expect(f.writes).toEqual([])
 })
