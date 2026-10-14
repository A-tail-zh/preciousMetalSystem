type Fraction={n:bigint,d:bigint}
const add=(a:Fraction,b:Fraction):Fraction=>({n:a.n*b.d+b.n*a.d,d:a.d*b.d})
const mul=(a:Fraction,b:Fraction):Fraction=>({n:a.n*b.n,d:a.d*b.d})
const neg=(a:Fraction):Fraction=>({...a,n:-a.n})
const num=(s:string):Fraction=>{const parts=s.split('.');return{n:BigInt(parts.join('')),d:10n**BigInt(parts[1]?.length??0)}}
const two=num('2'),half={n:1n,d:2n},sq=(a:Fraction)=>mul(a,a)
export const supportedVolumeCategories=['铱坩埚平底','铱环','铱杆实心','铱杆空心']
export function productVolume(category:string,dimensions:Record<string,string>,piValue:string):string|null{
 if(!supportedVolumeCategories.includes(category)||!/^\d+(\.\d{1,15})?$/.test(piValue)||Number(piValue)<=0)return null
 const keys=category==='铱杆实心'?['diameter','length']:category==='铱杆空心'?['outerDiameter','innerDiameter','length']:category==='铱环'?['outerDiameter','innerDiameter','height']:['innerDiameter','innerHeight','bottomThickness','wallThickness']
 if(keys.some(k=>!/^(0|[1-9]\d{0,13})(\.\d{1,6})?$/.test(dimensions[k]??'')||Number(dimensions[k])<=0))return null
 if(keys.includes('outerDiameter')){const outer=num(dimensions.outerDiameter),inner=num(dimensions.innerDiameter);if(outer.n*inner.d<=inner.n*outer.d)return null}
 const d=Object.fromEntries(keys.map(k=>[k,num(dimensions[k])])),pi=num(piValue);let v:Fraction
 if(category==='铱杆实心')v=mul(mul(pi,sq(mul(d.diameter,half))),d.length)
 else if(category==='铱杆空心')v=mul(mul(pi,sq(add(d.outerDiameter,neg(d.innerDiameter)))),d.length)
 else if(category==='铱环')v=mul(mul(pi,add(sq(mul(d.outerDiameter,half)),neg(sq(mul(d.innerDiameter,half))))),d.height)
 else{const r=mul(d.innerDiameter,half);v=add(mul(mul(pi,sq(r)),d.bottomThickness),mul(mul(mul(mul(two,pi),r),d.innerHeight),d.wallThickness))}
 const scaled=(v.n*1000000n*2n+v.d)/(2n*v.d),s=scaled.toString().padStart(7,'0');return s.slice(0,-6)+'.'+s.slice(-6)
}
