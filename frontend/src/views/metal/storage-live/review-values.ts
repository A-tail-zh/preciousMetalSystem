type Line=Record<string,any>
const decimal=/^\d{1,14}(\.\d{1,2})?$/
function units(value:unknown,piece=false):bigint|null {
 const s=String(value??'').trim()
 if(!(piece?/^\d+$/:decimal).test(s))return null
 if(piece){const n=BigInt(s);return n<=2147483647n?n:null}
 const [whole,fraction='']=s.split('.');return BigInt(whole)*100n+BigInt((fraction+'00').slice(0,2))
}
function format(n:bigint,piece:boolean){return piece?String(n):`${n/100n}.${String(n%100n).padStart(2,'0')}`}
export function multipleSources(row:Line){return (row.allocations?.length||0)>1}
export function sourceValue(row:Line,a:Line,received:boolean,piece=false):unknown {
 if(!multipleSources(row))return received||row.differenceMode==='REWEIGH'?(piece?row.actualQuantity:row.actualNetWeight):(piece?row.issuedQuantity??row.quantity:row.issuedNetWeight??row.netWeight)
 return a[received||row.differenceMode==='REWEIGH'?(piece?'receivedQuantity':'receivedWeight'):(piece?'quantity':'weight')]
}
export function actualTotal(row:Line,received=true,piece=false):string {
 let sum=0n
 for(const a of row.allocations||[]){const value=units(sourceValue(row,a,received,piece),piece);if(value===null)return '';sum+=value}
 if(piece&&sum>2147483647n||!piece&&sum>9999999999999999n)return ''
 return format(sum,piece)
}
export function hasLoss(row:Line,a?:Line){
 if(row.differenceMode!=='LOSS')return false
 const sent=units(a?sourceValue(row,a,false):row.issuedNetWeight??row.netWeight),received=units(a?sourceValue(row,a,true):row.actualNetWeight)
 const sq=units(a?sourceValue(row,a,false,true):row.issuedQuantity??row.quantity,true),rq=units(a?sourceValue(row,a,true,true):row.actualQuantity,true)
 return sent!==null&&received!==null&&sent>received||row.measurementMode==='PIECE'&&sq!==null&&rq!==null&&sq>rq
}
export function reviewLines(lines:Line[],type:string):Line[]{
 if(['ADJUSTMENT','OWNERSHIP_RETURN','REVERSAL'].includes(type))return JSON.parse(JSON.stringify(lines))
 return lines.map((original,index)=>{
  const row=JSON.parse(JSON.stringify(original)),piece=row.measurementMode==='PIECE',fail=(msg:string):never=>{throw Error(`第${index+1}行：${msg}`)}
  if(!['PIECE','WEIGHT'].includes(row.measurementMode))fail('计量方式缺失，请重新加载并核对配置')
  if(multipleSources(row)){
   row.actualNetWeight=actualTotal(row);row.issuedNetWeight=actualTotal(row,false)
   if(piece){row.actualQuantity=actualTotal(row,true,true);row.issuedQuantity=actualTotal(row,false,true)}
   row.lossBearer=null
  }
  const received=units(row.actualNetWeight),sent=units(row.differenceMode==='LOSS'?row.issuedNetWeight??row.netWeight:row.actualNetWeight)
  if(received===null||sent===null)fail('重量须为非负数，最多两位小数')
  if(sent!<received!)fail('实际接收重量不能超过发出重量')
  const quantity=piece?units(row.actualQuantity,true):0n,sentQ=piece?units(row.differenceMode==='LOSS'?row.issuedQuantity??row.quantity:row.actualQuantity,true):0n
  if(quantity===null||sentQ===null)fail('件数须为数据库范围内的非负整数')
  if(sentQ!<quantity!)fail('实际接收件数不能超过发出件数')
  let difference=units(row.netWeight)!==received||sent!==received||piece&&(units(row.quantity,true)!==quantity||sentQ!==quantity)
  for(const a of row.allocations||[]){
   const sw=units(sourceValue(row,a,false)),rw=units(sourceValue(row,a,true)),sq=piece?units(sourceValue(row,a,false,true),true):0n,rq=piece?units(sourceValue(row,a,true,true),true):0n
   if(sw===null||rw===null)fail('请完整填写各来源重量，最多两位小数')
   if(sq===null||rq===null)fail('请完整填写各来源整数件数')
   if(sw!<rw!||sq!<rq!)fail('来源实收不能超过实发')
   const bearer=multipleSources(row)?a.lossBearer:row.lossBearer
   if(sw!==rw||sq!==rq){difference=true;if(!['CUSTOMER','FACTORY'].includes(bearer))fail('请选择有损失来源的承担方')}
   a.weight=format(sw!,false);a.receivedWeight=format(rw!,false);a.lossBearer=bearer
   if(piece){a.quantity=format(sq!,true);a.receivedQuantity=format(rq!,true)}
  }
  if(!multipleSources(row)&&hasLoss(row)&&!['CUSTOMER','FACTORY'].includes(row.lossBearer))fail('请选择交接损失承担方')
  if(difference&&!String(row.differenceReason||'').trim())fail('请填写本行差异原因')
  return row
 })
}
