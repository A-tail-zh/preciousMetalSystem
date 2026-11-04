const signedDecimal=/^[+-]?\d{1,14}(\.\d{1,6})?$/
export function adjustmentNet(gross:unknown,impurity:unknown):string|null {
 const a=String(gross??'').trim(),b=String(impurity??'').trim()
 if(!signedDecimal.test(a)||!signedDecimal.test(b))return null
 const scaled=(s:string)=>{const negative=s.startsWith('-');const [integer,fraction='']=s.replace(/^[+-]/,'').split('.');const value=BigInt(integer)*1000000n+BigInt(fraction.padEnd(6,'0'));return negative?-value:value}
 const delta=scaled(a)-scaled(b),absolute=delta<0n?-delta:delta
 if(absolute>=100000000000000000000n)return null
 return (delta<0n?'-':'')+(absolute/1000000n).toString()+'.'+(absolute%1000000n).toString().padStart(6,'0')
}
