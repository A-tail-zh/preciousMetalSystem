import {test,expect,type Page} from '@playwright/test'
import {allocationRows,stockAllocations,wipAllocations} from '../src/views/metal/production-order/batch-transform'

async function fixture(page:Page,baseURL:string){
 const writes:string[]=[],reads:string[]=[]
 const order={id:'7',orderNo:'GD-P3',number:'GD-P3',version:3,status:'IN_PROGRESS',factoryId:'101',processingType:'DIRECT_CUSTOM',customerSnapshot:'{}'}
 const rows=[{id:'1011',number:'PB-SPLIT',orderItemId:'31',lifecycle:'SPLIT',plannedQuantity:100},{id:'1012',number:'PB-MERGED',orderItemId:'31',lifecycle:'MERGED',plannedQuantity:30},{id:'1013',number:'PB-CURRENT',orderItemId:'31',lifecycle:'ACTIVE',plannedQuantity:null}]
 await page.context().addCookies([{name:'Admin-Token',value:'p3-read-only-mock',url:baseURL}])
 await page.route('**/*-api/**',async route=>{
  const req=route.request(),path=new URL(req.url()).pathname.replace(/^\/(dev|prod)-api/,'');let response:any={code:200,data:[],rows:[],total:0}
  if(req.method()!=='GET'){writes.push(req.method()+' '+path);await route.fulfill({json:{code:400,msg:'Read-only test refuses mutation'}});return}reads.push(path)
  if(path==='/getInfo')response={code:200,user:{userId:1,userName:'admin'},roles:['admin'],permissions:['*:*:*']}
  else if(path==='/metal/options/factories')response.data=[{id:'101',name:'本厂'}]
  else if(path==='/metal/production-order/list')response={code:200,rows:[{...order,factory:'本厂'}],total:1}
  else if(path==='/metal/production-order/7/flow')response.data={order,items:[{id:'31',product:{name:'金环'},quantity:100,steps:[]}],executions:[],documents:[],events:[]}
  else if(path==='/metal/production-order/7/report')response.data={executions:[],orderTotals:{},notices:[]}
  else if(path==='/metal/production-batches/list')response={code:200,rows,total:rows.length}
  else if(path==='/metal/production-batches/1013')response.data={...rows[2],stageSummary:'历史承接与当前加工',executions:[{executionId:'61',processName:'酸洗',processorId:'81',status:'CLOSED',inputWeight:'1000.00',outputWeight:'300.00',outputQuantity:0,historicalOutputWeight:'4880.00',effectiveOutputWeight:'5180.00',carryInWeight:'700.00',carryOutWeight:'0.00'}]}
  else if(path==='/metal/storage-orders/options')response.data={people:[{id:'81',name:'加工甲'}],warehouses:[],materials:[],products:[],customers:[],productionOrders:[],sourceOutboundOrders:[],orderItems:[],executions:[],stocks:[]}
  await route.fulfill({json:response})
 })
 await page.goto('/metal/production-order');await page.getByRole('combobox',{name:'所属工厂',exact:true}).press('ArrowDown');await page.getByRole('option',{name:'本厂',exact:true}).click();await page.getByRole('row').filter({hasText:'GD-P3'}).getByRole('button',{name:'查看',exact:true}).click();const panel=page.getByRole('dialog',{name:'生产工单完整流程',exact:true}).locator('.production-batches')
 return {panel,writes,reads}
}

test('historical split and merged identities remain visible without manual transform controls',async({page,baseURL})=>{
 const f=await fixture(page,baseURL!)
 await expect(f.panel.getByRole('row').filter({hasText:'PB-SPLIT'})).toContainText('已拆分');await expect(f.panel.getByRole('row').filter({hasText:'PB-MERGED'})).toContainText('已合并');await expect(f.panel.getByRole('row').filter({hasText:'PB-CURRENT'})).toContainText('生产中')
 for(const name of ['新建生产批次','进入工序','拆分','合并','完成生产批次','撤销拆合'])await expect(f.panel.getByRole('button',{name,exact:true})).toHaveCount(0)
 await f.panel.getByRole('button',{name:'刷新批次',exact:true}).click();expect(f.writes).toEqual([]);expect(f.reads.some(path=>path.endsWith('/context'))).toBe(false)
})

test('historical opening output stays separate from actual output in read-only stage details',async({page,baseURL})=>{
 const f=await fixture(page,baseURL!)
 await f.panel.getByRole('row').filter({hasText:'PB-CURRENT'}).getByRole('button',{name:'查看加工记录',exact:true}).click();const detail=f.panel.locator('.batch-detail')
 await expect(detail.getByRole('columnheader',{name:'本记录产出（g）',exact:true})).toBeVisible();await expect(detail.getByRole('columnheader',{name:'历史承接产出（g）',exact:true})).toBeVisible();await expect(detail.getByRole('columnheader',{name:'阶段累计产出（g）',exact:true})).toBeVisible()
 await expect(detail.getByRole('cell',{name:'300.00',exact:true})).toHaveCount(1);await expect(detail.getByRole('cell',{name:'4880.00',exact:true})).toHaveCount(1);await expect(detail.getByRole('cell',{name:'5180.00',exact:true})).toHaveCount(1)
 await expect(detail.getByRole('button')).toHaveCount(0);expect(f.writes).toEqual([])
})

test('allocation builder preserves explicit owner and source axes without weight inference',()=>{
 const context={stockSlices:[{stockId:'1',stockVersion:4,stockSliceId:'2',sliceVersion:3,netWeight:'1000.00',quantity:100,measurementMode:'PIECE',owners:[{ownerId:'88',weight:'600.00',quantity:60},{ownerId:'0',weight:'400.00',quantity:40}],sourceAllocations:[{sourceAllocationId:'7',currentOwnerId:'88',weight:'600.00',quantity:60},{sourceAllocationId:'8',currentOwnerId:'0',weight:'400.00',quantity:40}]}]};const rows=allocationRows(context,'STOCK');expect(rows[0].netWeight).toBe('');rows[0].netWeight='300.00';rows[0].quantity='30';expect(()=>stockAllocations(rows)).toThrow(/逐项/);for(const key of ['ownerAllocations','sourceAllocations']){rows[0][key][0].netWeight='200.00';rows[0][key][0].quantity='20';rows[0][key][1].netWeight='100.00';rows[0][key][1].quantity='10'}const a=stockAllocations(rows)[0];expect(a.ownerAllocations.map((x:any)=>x.netWeight)).toEqual(['200.00','100.00']);expect(a.sourceAllocations.map((x:any)=>x.sourceAllocationId)).toEqual(['7','8']);rows[0].netWeight='300.001';expect(()=>stockAllocations(rows)).toThrow(/两位/);expect(wipAllocations([{sourceAllocationId:'9',measurementMode:'WEIGHT',netWeight:'700.00',quantity:''}])[0]).toMatchObject({sourceAllocationId:'9',netWeight:'700.00',quantity:0})
})
