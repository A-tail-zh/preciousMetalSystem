type Row=Record<string,any>
const units=(value:any):bigint=>{const text=String(value??'');if(!/^-?\d+(\.\d{1,6})?$/.test(text))throw Error('额度数据未加载，请重试');const negative=text.startsWith('-'),[a,b='']=text.replace('-','').split('.');const n=BigInt(a)*1000000n+BigInt(b.padEnd(6,'0'));return negative?-n:n}
const format=(n:bigint)=>`${n/1000000n}.${(n%1000000n).toString().padStart(6,'0')}`
export function rawReturnAvailable(source:Row,sources:Row[],drafts:Row[],editingIndex=-1):string{
 let row=units(source.remainingWeight),batch=units(source.batchRemainingWeight),cap=units(source.returnableWeight)
 for(const [index,draft]of drafts.entries()){
  if(index===editingIndex)continue
  const weight=units(draft.netWeight),origin=sources.find(s=>s.id===draft.sourceLineId)
  if(draft.sourceLineId===source.id)row-=weight
  if(!origin||origin.batchId===source.batchId)batch-=weight
 }
 return format([row,batch,cap].reduce((a,b)=>a<b?a:b)>0n?[row,batch,cap].reduce((a,b)=>a<b?a:b):0n)
}
export function rawReturnSourceUsable(source:Row):boolean{
 try{const valid=units(source.remainingWeight)>=0n&&units(source.batchRemainingWeight)>=0n&&units(source.returnableWeight)>=0n;return valid&&(source.batchStatus!=null?source.batchStatus==='OPEN':units(source.returnableWeight)>0n)}catch{return false}
}
export function rawReturnError(source:Row,sources:Row[],drafts:Row[],weight:any,editingIndex=-1):string{
 try{const available=rawReturnAvailable(source,sources,drafts,editingIndex);if(units(source.batchRemainingWeight)<0n||units(source.remainingWeight)<0n)return '历史重量异常，剩余可退净重为0，请人工核对';if(!rawReturnSourceUsable(source))return '来源批次已结算、已冲销或状态无效，请重新选择';if(units(weight)<0n)return '实测净重不能小于0';if(units(weight)>units(available))return `剩余可退净重最多${available}g（已扣除产出及本单其他待还明细）`;return ''}catch{return '额度数据未加载，请重试'}
}
