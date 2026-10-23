import request from '@/utils/request'
export interface MasterRow {
  id: string; version: number; createBy: string; createTime: string; updateTime: string
  code?: string; name?: string; description?: string; processType?: number
  materialType?: string; specification?: string; category?: string; dimensions?: Record<string,string>
  volume?: string | null; purity?: string | null; weight?: string | null
  processes?: {processId:string; loss:string; processName?:string}[]
  factoryId?:string | null; factoryName?:string | null; productIds?:string[]; productNames?:string[]
  phonenumber?:string; nickName?:string; roleIds?:string[]; roleNames?:string[]; password?:string
  roleName?:string; roleSort?:number; menuIds?:string[]; status?:string
}
export interface Option {id:string; name:string; parentId?:string; perms?:string; children?:Option[]}
export function masterList(path:string,params:{pageNum:number;pageSize:number}):Promise<{rows:MasterRow[];total:number}>{return request({url:`${path}/list`,method:'get',params})}
export function masterDetail(path:string,id:string,editing=false):Promise<{data:MasterRow}>{return request({url:`${path}/${id}${editing?'/edit-context':''}`,method:'get'})}
export function masterSave(path:string,data:Partial<MasterRow>){return request({url:path,method:data.id?'put':'post',data})}
export function masterRemove(path:string,ids:string[]){return request({url:`${path}/${ids.join(',')}`,method:'delete'})}
export function masterAction(path:string,action:string,data:Partial<MasterRow>){return request({url:`${path}/${action}`,method:'put',data})}
export function masterOptions(kind:string,params?:Record<string,string>):Promise<{data:Option[]}>{return request({url:`/metal/options/${kind}`,method:'get',params})}
