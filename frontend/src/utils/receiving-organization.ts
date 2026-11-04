/** Display fallback only; keep the separately entered receiving unit unchanged. */
export function receivingOrganizationText(row:{receivingOrganization?:unknown;organization?:unknown}){
 const receiving=String(row.receivingOrganization??'')
 return receiving.trim()?receiving:String(row.organization??'')
}
