import request from '@/utils/request'

export interface ProductionLine {
  productionLineId: string
  productionLineName: string
  factoryId: string | null
}
export interface Factory {
  factoryId: string
  factoryName: string
  area: string
  address: string
  introduction: string | null
  version: number
  updateTime: string
  productionLines: ProductionLine[]
  productionLineIds: string[]
}
export interface FactoryForm {
  factoryId?: string
  factoryName: string
  area: string
  address: string
  introduction: string
  productionLineIds?: string[]
  version?: number
}
export function listFactories(params: { pageNum: number; pageSize: number },isContextCurrent?:()=>boolean,background=false): Promise<{rows: Factory[]; total: number}> {
  return request({url: '/metal/factory/list', method: 'get', params,isContextCurrent,quiet:background})
}
export function getFactory(id: string, editing = false,isContextCurrent?:()=>boolean,background=false): Promise<{data: Factory}> {
  return request({url: `/metal/factory/${id}${editing?'/edit-context':''}`, method: 'get',isContextCurrent,quiet:background})
}
export function lineOptions(factoryId?: string,isContextCurrent?:()=>boolean,background=false): Promise<{data: ProductionLine[]}> {
  return request({url: '/metal/factory/line-options', method: 'get', params: {factoryId},isContextCurrent,quiet:background})
}
export function saveFactory(form: FactoryForm,isContextCurrent?:()=>boolean) {
  return request({url: '/metal/factory', method: form.factoryId ? 'put' : 'post', data: form,isContextCurrent})
}
export function deleteFactories(ids: string[],isContextCurrent?:()=>boolean) {
  return request({url: `/metal/factory/${ids.join(',')}`, method: 'delete',isContextCurrent})
}
