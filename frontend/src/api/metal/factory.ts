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
export function listFactories(params: { pageNum: number; pageSize: number }): Promise<{rows: Factory[]; total: number}> {
  return request({url: '/metal/factory/list', method: 'get', params})
}
export function getFactory(id: string, editing = false): Promise<{data: Factory}> {
  return request({url: `/metal/factory/${id}${editing?'/edit-context':''}`, method: 'get'})
}
export function lineOptions(factoryId?: string): Promise<{data: ProductionLine[]}> {
  return request({url: '/metal/factory/line-options', method: 'get', params: {factoryId}})
}
export function saveFactory(form: FactoryForm) {
  return request({url: '/metal/factory', method: form.factoryId ? 'put' : 'post', data: form})
}
export function deleteFactories(ids: string[]) {
  return request({url: `/metal/factory/${ids.join(',')}`, method: 'delete'})
}
