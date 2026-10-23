type StockOption={recoveryWorkOrderNo?:string;recoveryProcessName?:string;recoveryBatchNo?:string;skipPermitId?:string;skippedSequences?:number[];releaseId?:string;availableQuantity?:number|string;availableWeight?:number|string;category?:string;code?:string;name?:string;organization?:string;specification?:string;batchNo?:string;originBatchId?:string;originProcessName?:string|null;originProcessSequence?:number|string|null;originWorkOrderNo?:string|null}
const text=(value:unknown)=>String(value??'').trim()
export function stockOptionMain(stock:StockOption):string{
 const category=stock.category==='RAW'?'原料':stock.category==='SEMI'?'半成品':stock.category==='FINISHED'?'成品':'库存物料'
 const descriptor=stock.category==='RAW'?text(stock.organization)||'未填写单位名称':text(stock.specification)||'未填写规格型号'
 return `${category}：${text(stock.code)||'未填写编号'} / ${text(stock.name)||'未填写名称'} / ${descriptor}`
}
export function stockOptionDetail(stock:StockOption):string{
 const batch=text(stock.batchNo),origin=!!text(stock.originBatchId),order=text(stock.originWorkOrderNo)
 if(stock.category==='RAW')return [stock.recoveryWorkOrderNo?'加工余料来源：'+stock.recoveryWorkOrderNo:'',stock.recoveryProcessName?'工序：'+stock.recoveryProcessName:'',batch?'批次：'+batch:'',stock.availableWeight!=null?'可领净重：'+stock.availableWeight+'g':''].filter(Boolean).join(' / ')
 const parts:string[]=[]
 if(stock.category==='SEMI'){
  const name=text(stock.originProcessName),sequence=Number(stock.originProcessSequence)
  parts.push(!origin?'未关联生产工序':!name?'来源工序未记录':Number.isInteger(sequence)&&sequence>0?`已完成：第${sequence}道·${name}`:`已完成：${name}`)
 }
 if(order)parts.push(`工单：${order}`)
 else if(origin)parts.push('工单：未记录')
 else if(stock.category==='FINISHED')parts.push('未关联生产工单')
 parts.push(`批次：${batch||'未填写批次号'}`)
 if(stock.skipPermitId)parts.push('工艺许可跳步：第'+(stock.skippedSequences??[]).join('、')+'道');if(stock.releaseId)parts.push('已释放');if(stock.availableQuantity!=null&&stock.category==='SEMI')parts.push('可领：'+stock.availableQuantity+'件');if(stock.availableWeight!=null)parts.push('可领净重：'+stock.availableWeight+'g');return parts.join(' / ')
}
export function stockOptionLabel(stock:StockOption):string{
 const detail=stockOptionDetail(stock)
 return stockOptionMain(stock)+(detail?' / '+detail:'')
}
