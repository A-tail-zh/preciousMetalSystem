import {test,expect} from '@playwright/test'
import {stockGroups,splitStockGroup,groupedLines,weightText} from '../src/views/metal/storage-orders/stock-groups'
const stock=(id:string,ownerId='15',overrides:any={})=>({id,factoryId:'8',warehouseId:'9',materialId:'7',category:'RAW',netWeight:'100.00',availableWeight:'100.00',measurementMode:'WEIGHT',createTime:'2026-11-11 10:00:00',identity:{code:'W001',name:'粉料',metalTypeId:'1',specification:'规格',purity:'99.00',form:'POWDER',unit:'g'},ownerships:[{ownerId,ownerName:'客户甲',companyName:'甲公司',weight:'100.00',availableWeight:'100.00'}],...overrides})
let counter=0;const id=()=>String(++counter),line=(weight:string,quantity?:string)=>({localId:'logical',netWeight:weight,quantity,remark:'测试',grossWeight:'',impurity:'',allocations:[]})
test('same customer and material combines batches; customer and factory ownership stay separate',()=>{
 const groups=stockGroups([stock('2'),stock('1'),stock('3','16'),stock('4','0')],'PRODUCTION_OUT')
 expect(groups).toHaveLength(3);expect(weightText(groups[0].weight)).toBe('200.00');expect(groups[0].ownerName).toBe('客户甲');expect(groups[0].companyName).toBe('甲公司');expect(groups[0].members.map(m=>m.stock.id)).toEqual(['1','2'])
 const dispatch=stockGroups([stock('1'),stock('2','16')],'RAW_OUT','15');expect(dispatch).toHaveLength(1);expect(dispatch[0].ownerId).toBe('15')
})
test('warehouse, purity, specification, material form and measurement are identity boundaries',()=>{
 const base=stock('1'),sources=[base,stock('2','15',{warehouseId:'10'}),stock('3','15',{identity:{...base.identity,purity:'98'}}),stock('4','15',{identity:{...base.identity,specification:'另规格'}}),stock('5','15',{identity:{...base.identity,form:'METAL'}})]
 expect(stockGroups(sources,'PRODUCTION_OUT')).toHaveLength(5)
 expect(stockGroups([base,stock('6','15',{identity:{...base.identity,purity:'99.000000'}})],'PRODUCTION_OUT')).toHaveLength(1)
})
test('weight allocation uses FIFO and exact cents; gross weight and impurities are not duplicated',()=>{
 const group=stockGroups([stock('2'),stock('1')],'PRODUCTION_OUT')[0]
 const parts=splitStockGroup(group,{...line('150.01'),grossWeight:'151.02',impurity:'1.01'},id)
 expect(parts.map(p=>[p.stockId,p.netWeight])).toEqual([['1','100.00'],['2','50.01']]);expect(parts.every(p=>p.allocations.length===1&&p.allocations[0].ownerId==='15')).toBe(true)
 const summary=groupedLines(parts);expect(summary).toHaveLength(1);expect(summary[0].netWeight).toBe('150.01');expect(summary[0].grossWeight).toBe('151.02');expect(summary[0].impurity).toBe('1.01');expect(new Set(parts.map(p=>p.reservationLineId)).size).toBe(2)
 expect(()=>splitStockGroup(group,line('200.01'),id)).toThrow('超过');expect(splitStockGroup(group,line('0.01'),id)[0].netWeight).toBe('0.01')
})
test('available physical and ownership limits exclude held stock',()=>{
 const held=stock('1','15',{availableWeight:'40.00',ownerships:[{ownerId:'15',availableWeight:'60.00'}]});const unavailable=stock('2','15',{availableWeight:'0.00'})
 const groups=stockGroups([held,unavailable],'PRODUCTION_OUT');expect(weightText(groups[0].weight)).toBe('40.00');expect(groups[0].members).toHaveLength(1);expect(()=>splitStockGroup(groups[0],line('40.01'),id)).toThrow()
})
for(const category of ['SEMI','PRODUCT'])test(`${category} pieces preserve total weight and quantity across batches`,()=>{
 const piece=(n:string)=>stock(n,'15',{category,productId:'20',materialId:undefined,measurementMode:'PIECE',quantity:100,availableQuantity:100,identity:{metalTypeId:'1',code:'P1',name:'产品',specification:'环',purity:'99',form:category,unit:'g',completedProcessIds:['1','2']},ownerships:[{ownerId:'15',ownerName:'客户甲',availableWeight:'100.00',availableQuantity:100}]})
 const group=stockGroups([piece('1'),piece('2')],category==='PRODUCT'?'PRODUCT_OUT':'PRODUCTION_OUT','15')[0];expect(group.quantity).toBe(200n)
 const parts=splitStockGroup(group,line('150.01','150'),id),summary=groupedLines(parts)[0];expect(summary.netWeight).toBe('150.01');expect(summary.quantity).toBe('150');expect(parts.every(p=>Number(p.netWeight)>0&&Number(p.netWeight)<=100&&Number(p.quantity)>0&&Number(p.quantity)<=100)).toBe(true)
 expect(()=>splitStockGroup(group,line('150.00','201'),id)).toThrow()
})
test('semi stages and production provenance do not get merged',()=>{
 const semi=(n:string,stage:string,origin:string)=>stock(n,'15',{category:'SEMI',productId:'20',identity:{code:'S1',form:'SEMI',originOrderId:origin,originItemId:'1',completedProcessIds:[stage]}})
 expect(stockGroups([semi('1','1','7'),semi('2','2','7'),semi('3','1','8')],'PRODUCTION_OUT')).toHaveLength(3)
})
test('one piece cannot be spread across multiple physical batches just to satisfy excessive weight',()=>{
 const make=(n:string)=>stock(n,'15',{category:'SEMI',productId:'20',measurementMode:'PIECE',quantity:1,ownerships:[{ownerId:'15',weight:'100.00',quantity:1}]})
 const group=stockGroups([make('1'),make('2')],'PRODUCTION_OUT')[0];expect(()=>splitStockGroup(group,line('150.00','1'),id)).toThrow('来源批次重量不足')
})
