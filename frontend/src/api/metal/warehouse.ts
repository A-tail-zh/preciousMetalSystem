import request from '@/utils/request'
export type Row=Record<string,any>
export const warehouseList=(kind:string,params:Row):Promise<{rows:Row[],total:number}>=>request({url:'/metal/'+kind+'/list',params})
export const warehouseDetail=(kind:string,id:string,editing=false):Promise<{data:Row}>=>request({url:'/metal/'+kind+'/'+id+(editing?'/edit-context':'')})
export const batchCompletionContext=(id:string,isContextCurrent?:()=>boolean):Promise<{data:Row}>=>request({url:'/metal/process-batch/'+id+'/completion-context',...(isContextCurrent?{isContextCurrent}:{})})
export const confirmBatchReturn=(id:string,data:Row,isContextCurrent?:()=>boolean):Promise<{data:Row}>=>request({url:'/metal/process-batch/'+id+'/confirm-return',method:'post',data,...(isContextCurrent?{isContextCurrent}:{})})
export const warehouseSave=(kind:string,data:Row)=>request({url:'/metal/'+kind,method:data.id?'put':'post',data})
export const warehouseRemove=(kind:string,ids:string[])=>request({url:'/metal/'+kind+'/'+ids.join(','),method:'delete'})
export const warehouseAction=(kind:string,id:string,action:string,data:Row,isContextCurrent?:()=>boolean)=>request({url:'/metal/'+kind+'/'+id+'/'+action,method:'post',data,...(isContextCurrent?{isContextCurrent}:{})})
export const warehouseOptions=(kind:string,factoryId?:string,referenceId?:string,isContextCurrent?:()=>boolean):Promise<{data:Row[]}>=>request({url:'/metal/warehouse/options/'+kind,params:{factoryId,referenceId},...(isContextCurrent?{isContextCurrent}:{})})
export const reportSummary=(workOrderId:string,isContextCurrent?:()=>boolean):Promise<{data:Row[]}>=>request({url:'/metal/work-report/summary',params:{workOrderId},...(isContextCurrent?{isContextCurrent}:{})})
export const stepReports=(itemId:string,processId:string):Promise<{data:Row[]}>=>request({url:'/metal/work-report/step',params:{itemId,processId}})
export const importWarehouseLines=(file:File,context:Row)=>{const data=new FormData();data.append('file',file);for(const key of ['factoryId','warehouseId','type','workOrderId','itemId','processId','batchId'])if(context[key])data.append(key,String(context[key]));if(context.type==='PROCESS_RAW_RETURN')data.append('existingLines',JSON.stringify(context.lines??[]));return request({url:'/metal/warehouse-order/import',method:'post',data,headers:{'Content-Type':'multipart/form-data'}})}
export const orderTypeNames:Record<string,string>={PRODUCTION_OUT:'生产出库',PRODUCT_OUT:'产品出库',EXCHANGE_OUT:'还料出库',SEMI_OUT:'半成品出库',RAW_IN:'原材料入库',FINISHED_IN:'成品入库',SEMI_IN:'半成品入库',PROCESS_RAW_RETURN:'加工余料还库',RAW_RETURN:'原料还库',SEMI_RETURN:'半成品还库',PRODUCT_RETURN:'成品退还',OPENING:'期初登记',ADJUST:'库存调整'}
export const stateNames:Record<string,string>={POSTED:'已生效',VOID:'已冲销',OPEN:'未结算',SETTLED:'已结算',PENDING:'待处理',HANDLED:'已处理',NORMAL:'正常',ABNORMAL:'异常'}

export interface WarehouseWorkOrderContext {workOrderId:string;factoryId:string;customer:string;items:{id:string;productId:string;snapshot:{code:string;name:string;specification:string|null};plans:{processId:string;processName:string;sequenceNo:number}[]}[]}
export const warehouseWorkOrderContext=(factoryId:string,workOrderId:string,isContextCurrent?:()=>boolean):Promise<{data:WarehouseWorkOrderContext}>=>request({url:'/metal/warehouse/work-order-context',params:{factoryId,workOrderId},...(isContextCurrent?{isContextCurrent}:{})})

export const processConsumption=(level:'steps'|'batches'|'orders'|'order-lines',params:Row,isContextCurrent?:()=>boolean):Promise<{data:Row}>=>request({url:'/metal/work-report/consumption/'+level,params,...(isContextCurrent?{isContextCurrent}:{})})
export const consumptionStates:Record<string,string>={NOT_STARTED:'未开始',NO_ACTIVE:'无有效批次',OPEN:'未结算／暂算',PARTIAL:'部分结算',SETTLED:'已结算',REVIEW:'待核对',VOID:'已冲销'}

export interface EntryContext {factoryId:string;warehouseId:string;workOrderId:string;itemId:string;processId:string;externalEntryCapacity:string}
export interface ProductionStockResponse {data:Row[];entryContext?:EntryContext}
export const productionStockOptions=(params:Row,isContextCurrent?:()=>boolean):Promise<ProductionStockResponse>=>request({url:'/metal/warehouse/options/stocks',params,...(isContextCurrent?{isContextCurrent}:{})})
export const semiFlowOverview=(id:string,isContextCurrent?:()=>boolean):Promise<{data:Row}>=>request({url:'/metal/work-order/'+id+'/semi-flow',...(isContextCurrent?{isContextCurrent}:{})})
export const semiFlowAction=(url:string,data:Row,isContextCurrent?:()=>boolean)=>request({url,method:'post',data,...(isContextCurrent?{isContextCurrent}:{})})
