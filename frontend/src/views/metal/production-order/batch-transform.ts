export type Allocation = Record<string, any>
export function allocationRows(context: any, kind: 'STOCK'|'WIP', full = false): Allocation[] {
 return (kind === 'STOCK' ? context.stockSlices || [] : context.wipSources || []).filter((s:any)=>Number(s.netWeight ?? s.weight)>0).map((s:any)=>({
  ...s, balanceWeight:String(s.netWeight??s.weight),balanceQuantity:s.quantity,availableReturnableWeight:s.returnableWeight,availableReturnableQuantity:s.returnableQuantity,returnableWeight:'0.00',returnableQuantity:'0',netWeight:full?String(s.netWeight ?? s.weight):'', quantity:full&&s.measurementMode!=='WEIGHT'?String(s.quantity??''):'',
  ownerAllocations:(s.owners||[]).map((o:any)=>({...o,netWeight:full?String(o.netWeight??o.weight):'',quantity:full?String(o.quantity??''):''})),
  sourceAllocations:(s.sourceAllocations||[]).map((o:any)=>({...o,netWeight:full?String(o.netWeight??o.weight):'',quantity:full?String(o.quantity??''):''}))
 }))
}
const positive=(value:any)=>/^\d+(\.\d{1,2})?$/.test(String(value))&&Number(value)>0
const quantity=(value:any,mode:any)=>mode==='WEIGHT'?0:(/^\d+$/.test(String(value))?Number(value):NaN)
export function stockAllocations(rows: Allocation[]): Allocation[] {
 return rows.filter(r=>String(r.netWeight)!=='').map(r=>{
  if(!positive(r.netWeight)||!Number.isSafeInteger(quantity(r.quantity,r.measurementMode))||quantity(r.quantity,r.measurementMode)<(r.measurementMode==='WEIGHT'?0:1))throw Error('请填写合法净重及实际件数，净重最多两位小数')
  const out:Allocation={stockId:String(r.stockId),stockVersion:r.stockVersion,stockSliceId:String(r.stockSliceId),sliceVersion:r.sliceVersion,netWeight:String(r.netWeight),quantity:quantity(r.quantity,r.measurementMode),allocationBasis:'CONFIRMED_BALANCE'}
  if(r.outputEventId)out.outputEventId=String(r.outputEventId)
  for(const key of ['ownerAllocations','sourceAllocations']) {
   const list=r[key]||[]
   if(list.length){const selected=list.length===1?list:list.filter((p:any)=>Number(p.netWeight)>0||Number(p.quantity)>0)
    if(!selected.length)throw Error('请逐项填写料权和来源分配；不能按比例猜测')
    out[key]=selected.map((p:any)=>{
    const weight=list.length===1?String(r.netWeight):String(p.netWeight),q=list.length===1?quantity(r.quantity,r.measurementMode):quantity(p.quantity,r.measurementMode)
    if(!positive(weight)||!Number.isSafeInteger(q)||q<0)throw Error('请逐项填写料权和来源分配；不能按比例猜测')
    return {...(key==='ownerAllocations'?{ownerId:String(p.ownerId)}:{sourceAllocationId:String(p.sourceAllocationId??p.id),currentOwnerId:String(p.currentOwnerId??p.ownerId)}),netWeight:weight,quantity:q}
    })
   }
  }
  return out
 })
}
export function wipAllocations(rows: Allocation[]): Allocation[] {
 return rows.filter(r=>String(r.netWeight)!=='').map(r=>{
  const q=quantity(r.quantity,r.measurementMode)
  if(!positive(r.netWeight)||!Number.isSafeInteger(q)||q<0)throw Error('请填写合法在制来源分配，不从重量推算件数')
  const rw=String(r.returnableWeight??'0.00'),rq=quantity(r.returnableQuantity??0,r.measurementMode)
  if(!/^\d+(\.\d{1,2})?$/.test(rw)||!Number.isSafeInteger(rq)||rq<0||Number(rw)>Number(r.netWeight)||rq>q||(r.availableReturnableWeight!=null&&Number(rw)>Number(r.availableReturnableWeight))||(r.availableReturnableQuantity!=null&&rq>Number(r.availableReturnableQuantity)))throw Error('未加工可退分配不能超过本次承接量及原来源可退上限')
  return {sourceAllocationId:String(r.sourceAllocationId??r.id),netWeight:String(r.netWeight),quantity:q,returnableWeight:rw,returnableQuantity:rq,allocationBasis:'CONFIRMED_BALANCE'}
 })
}
