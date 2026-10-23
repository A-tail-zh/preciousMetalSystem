import type {FormInstance,FormItemRule} from 'element-plus'
export type FieldError={field:string;code:string;message:string}
export function formPath(path:string){return path.replace(/\[(\d+)\]/g,'.$1').replace(/\.<[^>]+>/g,'')}
export function textError(value:unknown,label:string,max:number,required=false){const s=String(value??'');return required&&!s.trim()?`请输入${label}`:s.length>max?`${label}最多${max}个字符`:''}
export function integerError(value:unknown,label:string,min=1,max=1000000){const s=String(value??'');if(!/^-?\d+$/.test(s))return `${label}须为${min}～${max}的整数`;const n=Number(s);return !Number.isSafeInteger(n)||n<min||n>max?`${label}须为${min}～${max}的整数`:''}
export function decimalError(value:unknown,label:string,options:{optional?:boolean;positive?:boolean;signed?:boolean;integer?:number;fraction?:number;max?:number}={}){
 if(value==null||value==='')return options.optional?'':`请输入${label}`
 const s=String(value),fraction=options.fraction??6,integer=options.integer??14
 if(!(options.signed?/^[+-]?\d+(\.\d+)?$/:/^\d+(\.\d+)?$/).test(s))return `${label}须为${options.signed?'':'非负'}十进制数字，不支持科学计数法`
 const [whole,part='']=s.replace(/^[+-]/, '').split('.');if(whole.replace(/^0+(?=\d)/,'').length>integer||part.length>fraction)return `${label}最多${integer}位整数、${fraction}位小数`
 if(options.positive&&!/[1-9]/.test(s))return `${label}须大于0`
 if(options.max!=null&&Number(s)>options.max)return `${label}不能超过${options.max}`
 return ''
}
export function fieldRule(check:(value:unknown)=>string,trigger:string|string[]='blur'):FormItemRule{return {validator:(_r,value,done)=>{const message=check(value);done(message?new Error(message):undefined)},trigger}}
export function textRules(label:string,max:number,required=false){return [{...fieldRule(value=>textError(value,label,max,required)),required}]}
export function integerRules(label:string,min=1,max=1000000){return [{...fieldRule(value=>integerError(value,label,min,max),['blur','change']),required:true}]}
export function applyFieldErrors(form:FormInstance|undefined,error:any){
 const errors:FieldError[]=error?.fieldErrors??[];if(!form||!errors.length)return false
 let first='';for(const field of form.fields??[]){const prop=Array.isArray(field.prop)?field.prop.join('.'):String(field.prop??'');const problem=errors.find(e=>formPath(e.field)===formPath(prop));if(problem){field.validateState='error';field.validateMessage=problem.message;if(!first)first=prop}}
 if(first)form.scrollToField(first);return Boolean(first)
}

export function remapFieldErrors(error:any,map:(path:string)=>string){return {...error,message:error?.message,fieldErrors:(error?.fieldErrors??[]).map((field:FieldError)=>({...field,field:map(field.field)}))}}
export function returnFieldPath(path:string,groupRows:number[][]){
 const line=path.match(/^documents\[(\d+)\]\.lines\[(\d+)\](.*)$/)
 if(line){const index=groupRows[Number(line[1])]?.[Number(line[2])];return index==null?path:`lines.${index}${line[3]}`}
 const header=path.match(/^documents\[\d+\]\.(orderDate|operatorIds|keeperId|reason)$/)
 if(header)return {orderDate:'date',operatorIds:'operators',keeperId:'keeper',reason:'reason'}[header[1]]??path
 return path==='physicalChecked'?'checked':path
}

export function passwordError(value:unknown){return typeof value==='string'&&Boolean(value.trim())&&value.length>=8&&new TextEncoder().encode(value).length<=72?'':'密码至少8位，最多72个UTF-8字节'}
