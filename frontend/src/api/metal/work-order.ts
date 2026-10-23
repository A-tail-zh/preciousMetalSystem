import request from '@/utils/request'
export interface WorkOrderPlan {processId:string;processName:string;loss:string;startTime:string|null;endTime:string|null;actualStartTime?:string|null;actualEndTime?:string|null}
export interface WorkOrderItem {id?:string;productId:string;quantity:number|null;snapshot?:{name:string;code:string;category:string;[key:string]:unknown};plans:WorkOrderPlan[];key?:string}
export interface WorkOrder {id?:string;version?:number;requestId?:string;orderNo?:string;customer:string;factoryId:string;productionLineId:string;ownerId:string;factoryName?:string;lineName?:string;ownerName?:string;orderDate:string;status?:string;canEdit?:boolean;canDelete?:boolean;operationDisabledReason?:string;rejectReason?:string;creatorName?:string;createTime?:string;items:WorkOrderItem[];reviews?:{id:string;action:string;actorName:string;createTime:string;reason?:string}[]}
export interface OrderOption {id:string;name:string;roleNames?:string;phone?:string;category?:string;hasProcesses?:boolean|number}
export const orderStatus:Record<string,string>={PENDING:'旧流程：待审核',REJECTED:'旧流程：已驳回',APPROVED:'旧流程：已通过',WAITING_MATERIAL:'待领料',IN_PROGRESS:'生产中',COMPLETED:'已完成',CLOSED_PARTIAL:'部分完工关闭'}
export const orderEditable=(row:WorkOrder)=>row.canEdit===true
const root='/metal/work-order'
export const orderList=(params:{pageNum:number;pageSize:number}):Promise<{rows:WorkOrder[];total:number}>=>request({url:root+'/list',params})
export const orderDetail=(id:string,isContextCurrent?:()=>boolean,editing=false):Promise<{data:WorkOrder}>=>request({url:root+'/'+id+(editing?'/edit-context':''),...(isContextCurrent?{isContextCurrent}:{})})
export const orderSave=(data:WorkOrder):Promise<{id?:string}>=>request({url:root,method:data.id?'put':'post',data:{id:data.id,version:data.version,requestId:data.requestId,customer:data.customer,factoryId:data.factoryId,productionLineId:data.productionLineId,ownerId:data.ownerId,orderDate:data.orderDate,items:data.items.map(item=>({id:item.id,productId:item.productId,quantity:item.quantity,plans:item.plans.map(step=>({processId:step.processId,startTime:step.startTime,endTime:step.endTime}))}))}})
export const orderDelete=(ids:string[])=>request({url:root+'/'+ids.join(','),method:'delete'})
export const orderAction=(id:string,action:string,version:number,reason?:string)=>request({url:root+'/'+id+'/'+action,method:'post',data:{version,reason}})
export const orderOptions=(kind:string,params?:Record<string,string>):Promise<{data:OrderOption[]}>=>request({url:root+'/options/'+kind,params})
export const orderProductPlans=(productId:string,lineId:string):Promise<{data:Pick<WorkOrderItem,'snapshot'|'plans'>}>=>request({url:root+'/options/products/'+productId+'/processes',params:{lineId}})

export const reserveOrderNumber=(requestId:string):Promise<{data:string}>=>request({url:root+'/number/reserve',method:'post',data:{requestId}})
