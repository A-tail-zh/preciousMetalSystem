/** Keep person choices distinguishable without exposing internal IDs. */
export function personLabel(person:{roleNames?:string;name?:string;phone?:string}){
 return `${person.roleNames||'未分配角色'}｜${person.name||'未填写姓名'}｜${person.phone||'未填写手机号'}`
}
