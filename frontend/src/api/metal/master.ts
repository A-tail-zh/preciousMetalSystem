import request from '@/utils/request'
export interface MasterRow {
  id: string; version: number; createBy: string; createTime: string; updateTime: string
  metalTypeId?:string; metalTypeName?:string; productType?:string; calculationVersion?:number;
  code?: string; name?: string; description?: string; processType?: number
  classificationLocked?: boolean; materialType?: string; specification?: string; category?: string; dimensions?: Record<string,string>
  volume?: string | null; purity?: string | null; weight?: string | null
  processes?: {processId:string; loss:string;lossUnit?:"PERCENT"|"G_PER_PIECE"; processName?:string;measurementMode?:'PIECE'|'WEIGHT'}[]
  factoryId?:string | null; factoryName?:string | null; productIds?:string[]; productNames?:string[]
  phonenumber?:string; nickName?:string; roleIds?:string[]; roleNames?:string[]; password?:string
  roleName?:string; roleSort?:number; menuIds?:string[]; status?:string
}
export interface Option {id:string; name:string; processTypeName?:string; parentId?:string; perms?:string; children?:Option[]}
export function masterList(path:string,params:{pageNum:number;pageSize:number},isContextCurrent?:()=>boolean,background=false):Promise<{rows:MasterRow[];total:number}>{return request({url:`${path}/list`,method:'get',params,isContextCurrent,quiet:background})}
export function masterDetail(path:string,id:string,editing=false,isContextCurrent?:()=>boolean,background=false):Promise<{data:MasterRow}>{return request({url:`${path}/${id}${editing?'/edit-context':''}`,method:'get',isContextCurrent,quiet:background})}
export function masterSave(path:string,data:Partial<MasterRow>,isContextCurrent?:()=>boolean){return request({url:path,method:data.id?'put':'post',data,isContextCurrent})}
export function masterRemove(path:string,ids:string[],isContextCurrent?:()=>boolean){return request({url:`${path}/${ids.join(',')}`,method:'delete',isContextCurrent})}
export function masterAction(path:string,action:string,data:Partial<MasterRow>,isContextCurrent?:()=>boolean){return request({url:`${path}/${action}`,method:'put',data,isContextCurrent})}
export function masterOptions(kind:string,params?:Record<string,string>,isContextCurrent?:()=>boolean,background=false):Promise<{data:Option[]}>{return request({url:`/metal/options/${kind}`,method:'get',params,isContextCurrent,quiet:background})}
