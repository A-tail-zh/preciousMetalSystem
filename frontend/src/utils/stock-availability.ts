import {roundDecimal} from './business-decimal'
import {managesPieces} from './inventory-measurement'
type StockLine={inventoryId?:string;netWeight?:string|number;quantity?:string|number;category?:string;measurementMode?:string;referenceQuantity?:string|number|null}
type StockBalance={netWeight:string|number;quantity:string|number;category:string;measurementMode?:string;effectiveMeasurementMode?:string;reservedWeight?:string|number;reservedQuantity?:string|number}
function micro(value:string|number){const [integer,fraction='']=String(value).split('.');return BigInt(integer)*1000000n+BigInt(fraction.padEnd(6,'0'))}
function weight(value:bigint){const nonnegative=value<0n?0n:value;return roundDecimal((nonnegative/1000000n)+'.'+(nonnegative%1000000n).toString().padStart(6,'0'))}
/** Subtract submitted reservations before sharing the remaining stock among this draft's rows. */
export function stockAvailabilityError(stock:StockBalance,line:StockLine,lines:StockLine[],editingIndex=-1){
 const others=lines.filter((row,index)=>index!==editingIndex&&String(row.inventoryId)===String(line.inventoryId))
 const reservedWeight=others.reduce((sum,row)=>sum+micro(row.netWeight??'0'),0n),requestedWeight=micro(line.netWeight??'0'),availableWeight=micro(stock.netWeight)-micro(stock.reservedWeight??0)
 const errors:string[]=[]
 if(reservedWeight+requestedWeight>availableWeight)errors.push(`净重最多可用 ${weight(availableWeight-reservedWeight)}g，本次填写 ${weight(requestedWeight)}g（清单已占用 ${weight(reservedWeight)}g）`)
 if(managesPieces(stock)){
  const reserved=others.reduce((sum,row)=>sum+BigInt(row.quantity??0),0n),requested=BigInt(line.quantity??0),available=BigInt(stock.quantity)-BigInt(stock.reservedQuantity??0)
  if(reserved+requested>available)errors.push(`数量最多可用 ${available>reserved?available-reserved:0n}件，本次填写 ${requested}件（清单已占用 ${reserved}件）`)
 }
 return errors.length?'库存不足：'+errors.join('；'):''
}

export function releasedTransferError(balance:Pick<StockBalance,'quantity'|'netWeight'|'measurementMode'>,lines:StockLine[]){
 if(balance.measurementMode==='WEIGHT')return lines.reduce((sum,row)=>sum+micro(row.netWeight??'0'),0n)>micro(balance.netWeight)?'领用合计超过释放可用净重':''
 const quantity=lines.reduce((sum,row)=>sum+BigInt(row.quantity??0),0n),netWeight=lines.reduce((sum,row)=>sum+micro(row.netWeight??'0'),0n),availableQuantity=BigInt(balance.quantity),availableWeight=micro(balance.netWeight)
 if(balance.measurementMode==='WEIGHT')return netWeight>availableWeight?'领用合计超过释放可用净重':''
 if(quantity>availableQuantity||netWeight>availableWeight)return '领用合计超过释放可用余额'
 if((quantity===availableQuantity)!==(netWeight===availableWeight))return '全部件数与全部净重须同时领完；可用 '+availableQuantity+'件 / '+weight(availableWeight)+'g，清单合计 '+quantity+'件 / '+weight(netWeight)+'g'
 return ''
}

export function externalEntryError(capacity:string|number,lines:{quantity?:string|number;releaseId?:string|null;measurementMode?:string;referenceQuantity?:number;netWeight?:string}[]):string{
 const external=lines.filter(row=>row.releaseId!=null&&String(row.releaseId)!==''&&row.measurementMode!=='WEIGHT')
 if(!external.length)return ''
 if(!/^\d+$/.test(String(capacity)))return '共享接入额度未就绪，请刷新重试'
 if(external.some(row=>!/^\d+$/.test(String(row.quantity))||BigInt(row.quantity!)<1n))return '外部接入件数须为正整数'
 const requested=external.reduce((sum,row)=>sum+BigInt(row.quantity!),0n),available=BigInt(capacity)
 return requested>available?'外部接入共享额度不足：本工序可接入'+available+'件，本单合计'+requested+'件，超出'+(requested-available)+'件':''
}
