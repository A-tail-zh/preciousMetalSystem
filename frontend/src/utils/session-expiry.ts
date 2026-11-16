import {defineComponent,h,ref,render} from 'vue'
import {ElButton,ElDialog,ElMessage} from 'element-plus'

// A dedicated dialog lets us dismiss only the expired session's prompt.
export const isRelogin={show:false}
let active:{token:string;close:()=>void}|undefined
export function sessionChanged(token?:string){if(active&&active.token!==token)active.close();if(!active)isRelogin.show=false}
export function showSessionExpiry(token:string,isCurrent:()=>boolean,logout:()=>Promise<boolean>){
 if(active?.token===token)return
 active?.close()
 const host=document.createElement('div');document.body.appendChild(host)
 const owner={token,close:()=>{if(active!==owner)return;active=undefined;isRelogin.show=false;render(null,host);host.remove()}}
 active=owner;isRelogin.show=true
 const Prompt=defineComponent({setup(){const busy=ref(false)
  async function confirm(){if(busy.value)return;if(!isCurrent()){owner.close();return}busy.value=true;try{const cleared=await logout();if(cleared)location.href='/index'}catch{if(active===owner&&isCurrent())ElMessage.error('退出失败，请重试')}finally{busy.value=false;if(active===owner&&!isCurrent())owner.close()}}
  return()=>h(ElDialog,{modelValue:true,title:'系统提示',width:'min(440px,94vw)',closeOnClickModal:false,closeOnPressEscape:!busy.value,showClose:!busy.value,'onUpdate:modelValue':()=>{if(!busy.value)owner.close()}},{default:()=>h('p','登录状态已过期，您可以继续留在该页面，或者重新登录'),footer:()=>[h(ElButton,{disabled:busy.value,onClick:owner.close},()=> '取消'),h(ElButton,{type:'primary',loading:busy.value,onClick:confirm},()=> '重新登录')]})
 }})
 render(h(Prompt),host)
}
