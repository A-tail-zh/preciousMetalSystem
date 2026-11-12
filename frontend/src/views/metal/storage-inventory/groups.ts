type Row=Record<string,any>
const cents=(value:unknown)=>{const text=String(value??'0');if(!/^\d+(\.\d{1,2})?$/.test(text))throw Error('库存重量格式无效，请刷新库存');const [whole,fraction='']=text.split('.');return BigInt(whole)*100n+BigInt(fraction.padEnd(2,'0'))}
const weightText=(value:bigint)=>`${value/100n}.${String(value%100n).padStart(2,'0')}`
export function rawInventoryGroups(stocks:Row[]):Row[]{
 const groups=new Map<string,Row>()
 for(const stock of stocks){
  const key=JSON.stringify([stock.factoryId,stock.warehouseId,stock.identityKey||`stock:${stock.id}`,stock.measurementMode,stock.identity?.unit])
  let group=groups.get(key)
  if(!group){group={...stock,groupKey:key,stocks:[],netWeight:'0.00',quantity:'0',sourceCount:0};groups.set(key,group)}
  group.stocks.push(stock);group.sourceCount++;group.netWeight=weightText(cents(group.netWeight)+cents(stock.netWeight));group.quantity=String(BigInt(group.quantity)+BigInt(stock.quantity||0))
 }
 return [...groups.values()]
}
export function inventoryOwners(stocks:Row[]):Row[]{
 const owners=new Map<string,Row>()
 for(const stock of stocks)for(const owner of stock.ownerships||[]){
  const key=String(owner.ownerId)
  let total=owners.get(key)
  if(!total){total={...owner,weight:'0.00',quantity:'0'} as Row;owners.set(key,total)}
  total.weight=weightText(cents(total.weight)+cents(owner.weight));total.quantity=String(BigInt(total.quantity)+BigInt(owner.quantity||0))
 }
 return [...owners.values()]
}
