/** Exact decimal HALF_UP; never convert IDs or arbitrary text to numbers. */
export function roundDecimal(value:unknown):string {
 const text=String(value??'')
 if(!/^[+-]?\d+(\.\d+)?$/.test(text))return text
 const negative=text.startsWith('-'),[whole,fraction='']=text.replace(/^[+-]/,'').split('.')
 let cents=BigInt(whole)*100n+BigInt((fraction+'00').slice(0,2))
 if(Number((fraction+'000')[2])>=5)cents++
 const digits=cents.toString().padStart(3,'0')
 return (negative&&cents!==0n?'-':'')+digits.slice(0,-2)+'.'+digits.slice(-2)
}
export function decimalText(value:unknown){return value==null||value===''?'—':roundDecimal(value)}
const fields=new Set(['handoverDifference','keeperReceivedWeight','processorOutputWeight','processorReturnedWeight','plannedNetWeight','processorNetWeight','reservedWeight','bookWeight','area','volume','purity','weight','basePrice','piValue','density','powderPrice','nonPowderPrice','shortagePrice','lossPrice','netWeight','grossWeight','impurity','deltaWeight','deltaGrossWeight','deltaImpurity','balanceWeight','totalNetWeight','availableWeight','remainingWeight','releasedWeight','unreleasedWeight','outputWeight','issuedWeight','returnedWeight','lossWeight','averageLoss','theoreticalLoss','thresholdValue','thresholdMin','thresholdMax','tolerance','lossRatePercent','unitPrice','totalPrice','unitWeight','totalWeight','rawIssuedWeight','semiIssuedWeight','rawReturnedWeight','semiReturnedWeight','processRawReturnedWeight','netInputWeight','settledLossWeight','provisionalDifferenceWeight','settledNetInputWeight','balanceDifferenceWeight'])
export function businessValue(value:unknown,key:string){return fields.has(key)?decimalText(value):value??''}
export function decimalColumn(_row:unknown,column:{property?:string},value:unknown){return String(businessValue(value,column.property??''))}
