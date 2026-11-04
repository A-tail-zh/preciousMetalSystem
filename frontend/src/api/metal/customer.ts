import request from '@/utils/request'

export interface CustomerForm {
  id?:string; version?:number; factoryId?:string; factoryName?:string
  name:string; companyName:string; address:string; contactName:string; contactPhone:string; department:string|null
}
export interface Customer extends CustomerForm {id:string; version:number; createTime:string}
export const customerList=(params:Record<string,string|number>,isContextCurrent:()=>boolean,background=false):Promise<{rows:Customer[];total:number}>=>request({url:'/metal/customer/list',params,isContextCurrent,quiet:background})
export const customerEditContext=(id:string,isContextCurrent:()=>boolean,background=false):Promise<{data:Customer}>=>request({url:`/metal/customer/${id}/edit-context`,isContextCurrent,quiet:background})
export const customerFactories=(isContextCurrent:()=>boolean,background=false):Promise<{data:{id:string;name:string}[]}>=>request({url:'/metal/options/factories',isContextCurrent,quiet:background})
export const customerSave=(data:CustomerForm,isContextCurrent:()=>boolean,options?:{retryAfterEmptyLookup?:boolean})=>request({url:'/metal/customer',method:data.id?'put':'post',data,isContextCurrent,...(options?.retryAfterEmptyLookup&&!data.id?{headers:{repeatSubmit:false}}:{})})
export const customerRemove=(ids:string[],isContextCurrent:()=>boolean)=>request({url:`/metal/customer/${ids.join(',')}`,method:'delete',isContextCurrent})
