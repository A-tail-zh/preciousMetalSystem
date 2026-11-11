type Row=Record<string,any>
export interface StockShare {stock:Row;weight:bigint;quantity:bigint}
export interface StockGroup {key:string;ownerId:string;ownerName:string;companyName:string;category:string;code:string;name:string;measurementMode:string;weight:bigint;quantity:bigint;members:StockShare[]}
const cents=(v:unknown)=>{const s=String(v??'0');if(!/^\d+(?:\.\d{1,2})?$/.test(s))throw Error('库存重量格式无效，请刷新库存');const [w,d='']=s.split('.');return BigInt(w)*100n+BigInt(d.padEnd(2,'0'))}
export const weightText=(v:bigint)=>`${v/100n}.${String(v%100n).padStart(2,'0')}`
const min=(a:bigint,b:bigint)=>a<b?a:b
const numericText=(v:unknown)=>String(v??'').trim().replace(/(\.\d*?)0+$/,'$1').replace(/\.$/,'')
export function stockGroups(stocks:Row[],type:string,customerId?:string):StockGroup[]{
 const groups=new Map<string,StockGroup>()
 for(const stock of stocks){
  const identity=stock.identity||{},mode=stock.measurementMode
  if(!['WEIGHT','PIECE'].includes(mode))continue
  const reference=stock.category==='RAW'?(stock.materialId||identity.materialId):(stock.productId||identity.productId)
  if(!reference)continue
  for(const owner of stock.ownerships||[]){
   const ownerId=String(owner.ownerId)
   if(type!=='PRODUCTION_OUT'&&customerId&&ownerId!==String(customerId))continue
   const weight=min(cents(stock.availableWeight??stock.netWeight),cents(owner.availableWeight??owner.weight))
   const quantity=mode==='PIECE'?min(BigInt(stock.availableQuantity??stock.quantity??0),BigInt(owner.availableQuantity??owner.quantity??0)):0n
   if(weight<=0n||mode==='PIECE'&&quantity<=0n)continue
   const lineage=type==='PRODUCTION_OUT'&&stock.category==='SEMI'?[stock.originOrderId||identity.originOrderId,identity.originItemId]:[]
   const key=JSON.stringify([stock.factoryId,stock.warehouseId,ownerId,stock.category,String(reference),String(identity.metalTypeId??''),String(identity.specification??'').trim(),numericText(identity.purity),identity.form,mode,identity.unit,(identity.completedProcessIds||[]).map(String),...lineage])
   let group=groups.get(key)
   if(!group){group={key,ownerId,ownerName:ownerId==='0'?'工厂自有':owner.ownerName||`客户#${ownerId}`,companyName:owner.companyName||'',category:stock.category,code:identity.code||stock.code||'',name:identity.name||identity.materialType||stock.name||stock.materialType||'',measurementMode:mode,weight:0n,quantity:0n,members:[]};groups.set(key,group)}
   group.weight+=weight;group.quantity+=quantity;group.members.push({stock,weight,quantity})
  }
 }
 for(const group of groups.values())group.members.sort((a,b)=>{const date=String(a.stock.createTime||'').localeCompare(String(b.stock.createTime||''));return date||(BigInt(a.stock.id)<BigInt(b.stock.id)?-1:BigInt(a.stock.id)>BigInt(b.stock.id)?1:0)})
 return [...groups.values()]
}
// Integer cents preserve the entered total; bounded shares never exceed a source batch.
function distribute(total:bigint,ratios:bigint[],limits?:bigint[],positive=false):bigint[]{
 const values:bigint[]=ratios.map(()=>positive?1n:0n)
 let remaining=total-values.reduce((a,b)=>a+b,0n)
 if(remaining<0n)throw Error('重量过小，无法按原批次分配到0.01g，请调整重量或件数')
 const caps=limits||ratios.map(()=>total)
 if(caps.reduce((a,b)=>a+b,0n)<total)throw Error('所选件数对应的来源批次重量不足，请调整重量或件数')
 while(remaining>0n){
  const eligible=ratios.map((r,i)=>({r,i})).filter(({i})=>values[i]<caps[i]),denominator=eligible.reduce((a,{r})=>a+r,0n)
  if(!denominator)throw Error('无法按来源批次分配，请重新核对库存')
  const start=remaining
  for(const {r,i} of eligible){const take=min(caps[i]-values[i],start*r/denominator);values[i]+=take;remaining-=take}
  if(start===remaining){for(const {i} of eligible){if(!remaining)break;values[i]++;remaining--}}
 }
 return values
}
export function splitStockGroup(group:StockGroup,line:Row,newId:()=>string):Row[]{
 const requested=cents(line.netWeight),piece=group.measurementMode==='PIECE',requestedQuantity=piece?BigInt(line.quantity):0n
 if(requested<=0n||requested>group.weight||piece&&(requestedQuantity<=0n||requestedQuantity>group.quantity))throw Error('输入数量超过该客户的可用库存，请刷新后重新核对')
 const selected:{member:StockShare;weight:bigint;quantity:bigint}[]=[]
 let remaining=piece?requestedQuantity:requested
 for(const member of group.members){if(!remaining)break;const take=min(remaining,piece?member.quantity:member.weight);selected.push({member,weight:piece?0n:take,quantity:piece?take:0n});remaining-=take}
 if(remaining)throw Error('来源库存已变化，请刷新后重新核对')
 if(piece){const weights=distribute(requested,selected.map(s=>s.quantity),selected.map(s=>s.member.weight),true);selected.forEach((s,i)=>s.weight=weights[i])}
 const optional:Record<string,bigint[]>={}
 for(const key of ['grossWeight','impurity'])if(String(line[key]??'').trim())optional[key]=distribute(cents(line[key]),selected.map(s=>s.weight))
 const selectionGroupId=line.selectionGroupId||line.localId
 return selected.map(({member,weight,quantity},index)=>{
  const stock=member.stock,id=newId(),result:Row={...line,localId:id,reservationLineId:id,stockId:String(stock.id),materialId:stock.materialId,productId:stock.productId,batchNo:stock.batchNo,category:stock.category,measurementMode:group.measurementMode,netWeight:weightText(weight),quantity:piece?String(quantity):null,allocations:[{ownerId:group.ownerId,weight:weightText(weight),quantity:piece?String(quantity):0}],selectionGroupId,selectionKey:group.key,selectionOwnerName:group.ownerName,selectionCompanyName:group.companyName,selectionOwnerId:group.ownerId}
  delete result.id
  for(const key of Object.keys(optional))result[key]=weightText(optional[key][index])
  return result
 })
}
export function groupedLines(lines:Row[]):Row[]{
 const groups=new Map<string,Row>(),result:Row[]=[]
 for(const line of lines){
  if(!line.selectionGroupId){result.push(line);continue}
  let group=groups.get(line.selectionGroupId)
  if(!group){group={...line,localId:line.selectionGroupId,netWeight:'0.00',quantity:line.measurementMode==='PIECE'?'0':null,grossWeight:line.grossWeight?'0.00':'',impurity:line.impurity?'0.00':'',batchCount:0};groups.set(line.selectionGroupId,group);result.push(group)}
  group.netWeight=weightText(cents(group.netWeight)+cents(line.netWeight));if(group.quantity!=null)group.quantity=String(BigInt(group.quantity)+BigInt(line.quantity??0));group.batchCount++
  for(const key of ['grossWeight','impurity'])if(line[key])group[key]=weightText(cents(group[key]||0)+cents(line[key]))
 }
 return result
}
