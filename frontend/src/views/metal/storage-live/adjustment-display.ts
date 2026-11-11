export function signedAdjustment(value:unknown,piece=false):string {
 const s=String(value??'').trim()
 if(!(piece?/^-?\d+$/:/^-?\d+(\.\d{1,2})?$/).test(s))return '—'
 const negative=s.startsWith('-'),[whole,fraction='']=s.replace(/^-/,'').split('.')
 const n=BigInt(whole)*(piece?1n:100n)+(piece?0n:BigInt((fraction+'00').slice(0,2)))
 return (n===0n?'':negative?'-':'+')+(piece?String(n):`${n/100n}.${String(n%100n).padStart(2,'0')}`)
}
