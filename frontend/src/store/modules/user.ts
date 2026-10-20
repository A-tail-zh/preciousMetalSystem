import router from '@/router'
import cache from '@/plugins/cache'
import { ElMessageBox } from 'element-plus'
import { login, logout, getInfo } from '@/api/login'
import { getToken, setToken, removeToken } from '@/utils/auth'
import { isHttp, isEmpty } from "@/utils/validate"
import useLockStore from '@/store/modules/lock'
import defAva from '@/assets/images/profile.jpg'

interface UserState {
  sessionReady: boolean
  token: string | undefined
  id: string | number
  name: string
  nickName: string
  avatar: string
  roles: string[]
  permissions: string[]
}

const useUserStore = defineStore(
  'user',
  {
    state: (): UserState => ({
      sessionReady: false,
      token: getToken(),
      id: '',
      name: '',
      nickName: '',
      avatar: '',
      roles: [],
      permissions: []
    }),
    actions: {
      // 登录
      login(userInfo: { username: string; password: string; code: string; uuid: string }) {
        this.sessionReady=false
        const username = userInfo.username.trim()
        const password = userInfo.password
        const code = userInfo.code
        const uuid = userInfo.uuid
        return new Promise<void>((resolve, reject) => {
          login(username, password, code, uuid).then(res => {
            setToken(res.token)
            this.token = res.token
            useLockStore().unlockScreen()
            resolve()
          }).catch(error => {
            reject(error)
          })
        })
      },
      // 获取用户信息
      getInfo() {
        const sessionToken=getToken()
        return new Promise((resolve, reject) => {
          if(!sessionToken){reject(new Error("会话已失效"));return}
          getInfo().then(res => {
            if(getToken()!==sessionToken){reject(new Error("会话已切换"));return}
            const user = res.user
            let avatar = user.avatar || ''
            if (!isHttp(avatar)) {
              avatar = (isEmpty(avatar)) ? defAva : import.meta.env.VITE_APP_BASE_API + avatar
            }
            if (res.roles && res.roles.length > 0) { // 验证返回的roles是否是一个非空数组
              this.roles = res.roles
              this.permissions = res.permissions
            } else {
              this.roles = ['ROLE_DEFAULT']
              this.permissions = []
            }
            this.id = user.userId || ''
            this.name = user.userName || ''
            this.nickName = user.nickName || ''
            this.avatar = avatar
            this.sessionReady=true
            cache.session.set('pwrChrtype', res.pwdChrtype || '')
            /* 初始密码提示 */
            if(res.isDefaultModifyPwd) {
              ElMessageBox.confirm('您的密码还是初始密码，请修改密码！',  '安全提示', {  confirmButtonText: '确定',  cancelButtonText: '取消',  type: 'warning' }).then(() => {
                if(getToken()===sessionToken)router.push({ name: 'Profile', params: { activeTab: 'resetPwd' } })
              }).catch(() => {})
            }
            /* 过期密码提示 */
            if(!res.isDefaultModifyPwd && res.isPasswordExpired) {
              ElMessageBox.confirm('您的密码已过期，请尽快修改密码！',  '安全提示', {  confirmButtonText: '确定',  cancelButtonText: '取消',  type: 'warning' }).then(() => {
                if(getToken()===sessionToken)router.push({ name: 'Profile', params: { activeTab: 'resetPwd' } })
              }).catch(() => {})
            }
            resolve(res)
          }).catch(error => {
            reject(error)
          })
        })
      },
      clearSession() { this.sessionReady=false; this.id=''; cache.session.remove('sessionObj'); this.token=''; this.roles=[]; this.permissions=[]; removeToken() },
      // 退出系统
      async logOut(expectedToken=getToken()):Promise<boolean> {
        if(!expectedToken||getToken()!==expectedToken)return false
        try{await logout(expectedToken)}catch(error:any){
          if(getToken()!==expectedToken)return false
          if(error?.response?.status!==401&&error!=='无效的会话，或者会话已过期，请重新登录。')throw error
        }
        if(getToken()!==expectedToken)return false
        this.clearSession()
        return true
      }
    }
  })

export default useUserStore
