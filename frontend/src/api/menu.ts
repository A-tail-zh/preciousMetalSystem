import type {DataResult} from '@/types/api/common'
import request from '@/utils/request'
import type { RouterVo, AjaxResult } from '@/types'

// 获取路由
export const getRouters = (): Promise<DataResult<RouterVo>> => {
  return request({
    url: '/getRouters',
    method: 'get'
  })
}
