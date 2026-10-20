import axios from 'axios'
import { h } from 'vue'
import { ElNotification , ElMessageBox, ElMessage, ElLoading } from 'element-plus'
import { getToken } from '@/utils/auth'
import errorCode from '@/utils/errorCode'
import { tansParams, blobValidate } from '@/utils/ruoyi'
import cache from '@/plugins/cache'
import {copyText} from '@/utils/clipboard'
import {containsSensitiveData} from '@/utils/sensitive-request'
import { saveAs } from 'file-saver'
import useUserStore from '@/store/modules/user'

try { if (containsSensitiveData(cache.session.getJSON('sessionObj'))) cache.session.remove('sessionObj') } catch { cache.session.remove('sessionObj') }

let downloadLoadingInstance: ReturnType<typeof ElLoading.service>
// 是否显示重新登录
export {isRelogin} from './session-expiry'
import {isRelogin,showSessionExpiry} from './session-expiry'
function expired(config:any){
 const token=config?.sessionToken
 if(!token||token!==getToken()||location.pathname.startsWith('/login'))return
 useUserStore().sessionReady=false
 if(!isRelogin.show)showSessionExpiry(token,()=>getToken()===token,()=>useUserStore().logOut(token))
}

axios.defaults.headers['Content-Type'] = 'application/json;charset=utf-8'
// 创建axios实例
const service = axios.create({
  // axios中请求配置有baseURL选项，表示请求URL公共部分
  baseURL: import.meta.env.VITE_APP_BASE_API,
  // 超时
  timeout: 10000
})

// request拦截器
service.interceptors.request.use((config: any) => {
  config.sessionToken=config.headers?.isToken===false?undefined:(config.headers?.Authorization?.toString().replace(/^Bearer /,'')||getToken())
  // 是否需要设置 token
  const isToken = (config.headers || {}).isToken === false
  // 是否需要防止数据重复提交
  const isRepeatSubmit = (config.headers || {}).repeatSubmit === false
  // 间隔时间(ms)，小于此时间视为重复提交
  const interval = (config.headers || {}).interval || 1000
  if (getToken() && !isToken && !config.headers['Authorization']) {
    config.headers['Authorization'] = 'Bearer ' + getToken() // 让每个请求携带自定义token 请根据实际情况自行修改
  }
  // get请求映射params参数
  if (config.method === 'get' && config.params) {
    let url = config.url + '?' + tansParams(config.params)
    url = url.slice(0, -1)
    config.params = {}
    config.url = url
  }
  if (containsSensitiveData(config.data)) cache.session.remove('sessionObj')
  if (!isRepeatSubmit && !containsSensitiveData(config.data) && (config.method === 'post' || config.method === 'put')) {
    const requestObj = {
      url: config.url,
      data: typeof config.data === 'object' ? JSON.stringify(config.data) : config.data,
      time: new Date().getTime()
    }
    const requestSize = JSON.stringify(requestObj).length // 请求数据大小
    const limitSize = 5 * 1024 * 1024 // 限制存放数据5M
    if (requestSize >= limitSize) {
      console.warn(`[${config.url}]: ` + '请求数据大小超出允许的5M限制，无法进行防重复提交验证。')
      return config
    }
    const sessionObj = cache.session.getJSON('sessionObj')
    if (sessionObj === undefined || sessionObj === null || sessionObj === '') {
      cache.session.setJSON('sessionObj', requestObj)
    } else {
      const s_url = sessionObj.url                // 请求地址
      const s_data = sessionObj.data              // 请求数据
      const s_time = sessionObj.time              // 请求时间
      if (s_data === requestObj.data && requestObj.time - s_time < interval && s_url === requestObj.url) {
        const message = '数据正在处理，请勿重复提交'
        console.warn(`[${s_url}]: ` + message)
        return Promise.reject(new Error(message))
      } else {
        cache.session.setJSON('sessionObj', requestObj)
      }
    }
  }
  return config
}, (error: any) => {
    console.log(error)
    Promise.reject(error)
})

// Opt-in UI context guard. Session expiry remains authoritative even after a view is closed.
function staleContext(config:any){if(typeof config?.isContextCurrent!=='function')return false;try{return !config.isContextCurrent()}catch{return true}}

// 响应拦截器
service.interceptors.response.use((res: any) => {
    // 未设置状态码则默认成功状态
    const code = res.data.code || 200
    if(code!==401&&staleContext(res.config))return Promise.reject(new axios.CanceledError('请求上下文已失效'))
    const trace=res.data.traceId||res.headers?.['x-trace-id'];if(code!==200&&code!==401&&trace&&useUserStore().sessionReady&&!!getToken()&&res.config?.sessionToken===getToken()&&!/\/(login|logout|captchaImage|getInfo)(?:\?|$)/.test(res.config?.url??'')&&!location.pathname.startsWith('/login'))ElNotification({title:'故障追踪',message:h('button',{onClick:()=>copyText(trace).then(()=>ElMessage.success('追踪ID已复制')).catch(()=>ElMessage.warning('复制失败，请手动选择追踪ID'))},'复制追踪ID：'+trace),duration:10000})
    // 获取错误信息
    const msg = errorCode[code] || res.data.msg || errorCode['default']
    // 二进制数据则直接返回
    if (res.request.responseType ===  'blob' || res.request.responseType ===  'arraybuffer') {
      return res.data
    }
    if (code === 401) {
      expired(res.config)
      return Promise.reject('无效的会话，或者会话已过期，请重新登录。')
    } else if (code === 500) {
      ElMessage({ message: msg, type: 'error' })
      return Promise.reject(new Error(msg))
    } else if (code === 601) {
      ElMessage({ message: msg, type: 'warning' })
      return Promise.reject(new Error(msg))
    } else if (code !== 200) {
      ElNotification.error({ title: msg })
      return Promise.reject('error')
    } else {
      return  Promise.resolve(res.data)
    }
  },
  (error: any) => {
    if(error.response?.status===401){expired(error.config);return Promise.reject(error)}
    if(staleContext(error.config))return Promise.reject(error)
    console.log('err' + error)
    let { message } = error
    if (message == "Network Error") {
      message = "后端接口连接异常"
    } else if (message.includes("timeout")) {
      message = "系统接口请求超时"
    } else if (message.includes("Request failed with status code")) {
      message = "系统接口" + message.slice(-3) + "异常"
    }
    ElMessage({ message: message, type: 'error', duration: 5 * 1000 })
    return Promise.reject(error)
  }
)

// 通用下载方法
export function download(url: string, params: any, filename: string, config?: any) {
  downloadLoadingInstance = ElLoading.service({ text: "正在下载数据，请稍候", background: "rgba(0, 0, 0, 0.7)", })
  return service.post(url, params, {
    transformRequest: [(params: any) => { return tansParams(params) }],
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    responseType: 'blob',
    ...config
  }).then(async (data: any) => {
    const isBlob = blobValidate(data)
    if (isBlob) {
      const blob = new Blob([data])
      saveAs(blob, filename)
    } else {
      const resText = await data.text()
      const rspObj = JSON.parse(resText)
      const errMsg = errorCode[rspObj.code] || rspObj.msg || errorCode['default']
      ElMessage.error(errMsg)
    }
    downloadLoadingInstance.close()
  }).catch((r: any) => {
    console.error(r)
    ElMessage.error('下载文件出现错误，请联系管理员！')
    downloadLoadingInstance.close()
  })
}

export default service
