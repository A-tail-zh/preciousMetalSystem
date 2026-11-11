import type {Row} from '../storage-live/client'
export type Kind='OUT'|'IN'|'RET'|'ADJUST'|'RETURN_RIGHTS'|'REVERSE'
export const kindNames:Record<Kind,string>={OUT:'出库单',IN:'入库单',RET:'还库单',ADJUST:'库存调整单',RETURN_RIGHTS:'料权归还单',REVERSE:'冲销单'}
export const kinds:Kind[]=['OUT','IN','RET']
export const typeNames:Record<string,string>={RAW_OUT:'原材料提料出库',PRODUCTION_OUT:'生产出库',PRODUCT_OUT:'产品出库',SEMI_OUT:'半成品出库',CUSTOMER_IN:'客户来料入库',RAW_IN:'原材料入库',PROCESS_RAW_IN:'加工原材料入库',SEMI_IN:'半成品入库',PRODUCT_IN:'成品入库',EXTERNAL_SEMI_IN:'外来半成品入库',RAW_RETURN:'原样余料还库',RECOVERY_RETURN:'回收料还库',ADJUSTMENT:'库存调整',OWNERSHIP_RETURN:'料权归还',REVERSAL:'单据冲销'}
export const statusNames:Record<string,string>={DRAFT:'草稿',RETURNED:'已退回',PENDING_REVIEW:'待复验',POSTED:'已过账',CANCELLED:'已取消',REVERSED:'已冲销'}
export const typesFor=(kind:Kind)=>({OUT:['PRODUCTION_OUT','RAW_OUT','PRODUCT_OUT','SEMI_OUT'],IN:['RAW_IN','PROCESS_RAW_IN','SEMI_IN','PRODUCT_IN','EXTERNAL_SEMI_IN'],RET:['RAW_RETURN','RECOVERY_RETURN'],ADJUST:['ADJUSTMENT'],RETURN_RIGHTS:['OWNERSHIP_RETURN'],REVERSE:['REVERSAL']}[kind])
export const editable=(row:Row)=>['DRAFT','RETURNED'].includes(row.status)
export const clone=<T>(value:T):T=>JSON.parse(JSON.stringify(value))
export const decimalValid=(value:unknown,required=false,max?:number)=>!String(value??'').trim()?!required:/^\d{1,14}(\.\d{1,2})?$/.test(String(value))&&(max===undefined||Number(value)<=max)
export const quantityValid=(value:unknown)=>/^[1-9]\d{0,9}$/.test(String(value??''))&&Number(value)<=2147483647
