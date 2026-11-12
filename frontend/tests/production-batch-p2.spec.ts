import {test,expect,type Page} from '@playwright/test'
import {stockSliceChoices,stockGroups,splitStockGroup} from '../src/views/metal/storage-orders/stock-groups'

async function fixture(page:Page,baseURL:string,count=1,failFirst=false){
 const writes:string[]=[],pages:number[]=[];let failed=false
 const order={id:'7',number:'GD-P2',orderNo:'GD-P2',version:0,status:'IN_PROGRESS',factoryId:'101',factoryName:'本厂',processingType:'DIRECT_CUSTOM',customerSnapshot:'{}'}
 const rows=Array.from({length:count},(_,index)=>({id:String(1011+index),number:`PB-${String(index+1).padStart(2,'0')}`,orderId:'7',orderItemId:'31',plannedQuantity:null,lifecycle:'ACTIVE',version:0}))
 await page.context().addCookies([{name:'Admin-Token',value:'p2-read-only-mock',url:baseURL}])
 await page.route('**/*-api/**',async route=>{
  const req=route.request(),url=new URL(req.url()),path=url.pathname.replace(/^\/(dev|prod)-api/,'');let response:any={code:200,data:[],rows:[],total:0}
  if(req.method()!=='GET'){writes.push(req.method()+' '+path);await route.fulfill({json:{code:400,msg:'Read-only test refuses mutation'}});return}
  if(path==='/getInfo')response={code:200,user:{userId:1,userName:'admin'},roles:['admin'],permissions:['*:*:*']}
  else if(path==='/metal/options/factories')response.data=[{id:'101',name:'本厂'}]
  else if(path==='/metal/production-order/list')response={code:200,rows:[{...order,factory:'本厂'}],total:1}
  else if(path==='/metal/production-order/7/flow')response.data={order,items:[{id:'31',product:{name:'金环'},quantity:100,steps:[]}],executions:[],documents:[],events:[]}
  else if(path==='/metal/production-order/7/report')response.data={executions:[],orderTotals:{},notices:[]}
  else if(path==='/metal/production-batches/list'){
   if(failFirst&&!failed){failed=true;await route.fulfill({json:{code:500,msg:'批次读取失败'}});return}
   const number=Number(url.searchParams.get('pageNum')||1);pages.push(number);response={code:200,rows:rows.slice((number-1)*16,number*16),total:rows.length}
  }
  else if(path==='/metal/production-batches/1011')response.data={...rows[0],stageSummary:'酸洗部分产出',nextStep:{processName:'融定'},executions:[{executionId:'61',processName:'酸洗',processorId:'81',status:'OPEN',inputWeight:'1000.00',outputWeight:'400.00',outputQuantity:0,carryInWeight:'0.00',carryOutWeight:'0.00'}]}
  else if(path==='/metal/storage-orders/options')response.data={people:[{id:'81',name:'加工甲'}],warehouses:[],materials:[],products:[],customers:[],productionOrders:[],sourceOutboundOrders:[],orderItems:[],executions:[],stocks:[]}
  await route.fulfill({json:response})
 })
 await page.goto('/metal/production-order');await page.getByRole('combobox',{name:'所属工厂',exact:true}).press('ArrowDown');await page.getByRole('option',{name:'本厂',exact:true}).click();await page.getByRole('row').filter({hasText:'GD-P2'}).getByRole('button',{name:'查看',exact:true}).click()
 const flow=page.getByRole('dialog',{name:'生产工单完整流程',exact:true}),panel=flow.locator('.production-batches');await expect(panel.getByRole('heading',{name:'生产批次',exact:true})).toBeVisible()
 return {panel,flow,writes,pages}
}

test('automatic batch panel shows processing facts without any manual batch operation',async({page,baseURL})=>{
 const f=await fixture(page,baseURL!)
 await f.panel.getByRole('row').filter({hasText:'PB-01'}).getByRole('button',{name:'查看加工记录',exact:true}).click()
 const detail=f.panel.locator('.batch-detail');await expect(detail.getByRole('cell',{name:'400.00',exact:true})).toBeVisible();await expect(detail.getByRole('cell',{name:'加工中',exact:true})).toBeVisible()
 for(const name of ['新建生产批次','进入工序','拆分','合并','完成生产批次','领料 / 补料','产出入库','退料','结束本工序'])await expect(f.panel.getByRole('button',{name,exact:true})).toHaveCount(0)
 await expect(page.getByRole('dialog',{name:'新建生产批次',exact:true})).toHaveCount(0);expect(f.writes).toEqual([])
})

test('read-only batch list paginates using GET and keeps all automatic identities visible',async({page,baseURL})=>{
 const f=await fixture(page,baseURL!,17)
 await expect(f.panel.getByRole('cell',{name:'PB-01',exact:true})).toBeVisible();await expect(f.panel.getByRole('cell',{name:'PB-17',exact:true})).toHaveCount(0)
 await f.panel.locator('.el-pagination .btn-next').click();await expect(f.panel.getByRole('cell',{name:'PB-17',exact:true})).toBeVisible();await expect(f.panel.getByRole('cell',{name:'PB-01',exact:true})).toHaveCount(0)
 expect(f.pages).toContain(2);expect(f.writes).toEqual([])
})

test('read-only empty batch list and failed query remain recoverable without creation',async({page,baseURL})=>{
 const f=await fixture(page,baseURL!,0,true)
 await expect(f.panel.getByText('批次读取失败',{exact:true})).toBeVisible();await f.panel.getByRole('button',{name:'刷新批次',exact:true}).click();await expect(f.panel.getByText('暂无数据',{exact:true})).toBeVisible()
 await expect(f.panel.getByRole('button',{name:'新建生产批次',exact:true})).toHaveCount(0);expect(f.writes).toEqual([])
})

test('slice choices respect owner limits and split group preserves each source identity',()=>{
 const stock={id:'501',factoryId:'1',warehouseId:'11',version:9,category:'SEMI',productId:'51',measurementMode:'PIECE',netWeight:'100.00',quantity:100,availableWeight:'80.00',availableQuantity:80,identity:{name:'半成品',code:'P1',metalTypeId:'1',specification:'Φ1',purity:'99',completedProcessIds:['5'],originItemId:'31'},ownerships:[{ownerId:'88',weight:'60.00',quantity:60,availableWeight:'50.00',availableQuantity:50},{ownerId:'0',weight:'40.00',quantity:40,availableWeight:'30.00',availableQuantity:30}],stockSlices:[{stockSliceId:'9001',sourceBatchId:'101',sliceVersion:3,weight:'30.00',quantity:30,availableWeight:'25.00',availableQuantity:25,owners:[{ownerId:'88',weight:'20.00',quantity:20,availableWeight:'15.00',availableQuantity:15},{ownerId:'0',weight:'10.00',quantity:10}]},{stockSliceId:'9002',sourceBatchId:'102',sliceVersion:4,weight:'70.00',quantity:70,availableWeight:'55.00',availableQuantity:55,owners:[{ownerId:'88',weight:'40.00',quantity:40,availableWeight:'35.00',availableQuantity:35},{ownerId:'0',weight:'30.00',quantity:30,availableWeight:'20.00',availableQuantity:20}]}]}
 const choices=stockSliceChoices([stock]);expect(choices.map(s=>s.stockSliceId)).toEqual(['9001','9002']);expect(choices.map(s=>s.availableWeight)).toEqual(['25.00','55.00'])
 const separated=stockGroups(choices,'PRODUCTION_OUT');expect(separated.filter(g=>g.ownerId==='88').map(g=>g.weight)).toEqual([1500n,3500n]);const groups=stockGroups([choices[0],{...choices[1],id:'502',sourceBatchId:'101'}],'PRODUCTION_OUT');const owner=groups.find(g=>g.ownerId==='88')!;expect(owner.weight).toBe(5000n);expect(owner.quantity).toBe(50n)
 let seq=0;const parts=splitStockGroup(owner,{localId:'selection',netWeight:'40.00',quantity:'40'},()=>String(++seq))
 expect(parts.map(p=>[p.stockId,p.stockSliceId,p.sliceVersion,p.stockVersion,p.sourceBatchId,p.netWeight,p.quantity])).toEqual([['501','9001',3,9,'101','15.00','15'],['502','9002',4,9,'101','25.00','25']])
 expect(parts.every(p=>p.allocations[0].ownerId==='88')).toBe(true);expect(()=>splitStockGroup(owner,{netWeight:'51.00',quantity:'51'},()=>String(++seq))).toThrow(/可用库存/)
 expect(groups.find(g=>g.ownerId==='0')?.weight).toBe(3000n)
})
