import {onMounted,onBeforeUnmount,readonly,ref,watch,type Ref} from 'vue'
import {INVENTORY_CHANGED} from '@/utils/inventory-change'
import {getToken} from '@/utils/auth'
import useUserStore from '@/store/modules/user'

export type InventoryRefreshContext={isCurrent:()=>boolean;quiet:true}
type Refresh=(context:InventoryRefreshContext)=>Promise<void|boolean>
/** Background reads never own a draft or mutation; callers compose this guard with their target/epoch. */
export function useInventoryRefresh(refresh:Refresh,blocked:Ref<boolean>,access:{allowed:()=>boolean;reset:()=>void},options:{probe?:Refresh;paused?:Ref<boolean>}={}){
 const user=useUserStore(),failed=ref(false)
 const identity=()=>JSON.stringify([user.sessionReady,user.token,String(user.id),[...user.permissions].sort()])
 let pending=false,disposed=false,mounted=false,running=false,generation=0,failures=0,retryAt=0,accessLost=false,timer:ReturnType<typeof setTimeout>|undefined
 function clearTimer(){if(timer!==undefined)clearTimeout(timer);timer=undefined}
 function available(){
  if(user.sessionReady&&!!user.token&&user.token===getToken()&&access.allowed()){accessLost=false;return true}
  if(!accessLost){accessLost=true;generation++;access.reset()}
  pending=false;return false
 }
 function schedule(delay=pending?100:5000){
  if(!mounted||disposed||!available()||running||timer!==undefined||document.visibilityState==='hidden'||options.paused?.value||blocked.value&&!options.probe)return
  timer=setTimeout(()=>{timer=undefined;void run()},Math.max(delay,retryAt-Date.now()))
 }
 async function run(){
  pending=true
  if(disposed||!available()||running||document.visibilityState==='hidden'||options.paused?.value||blocked.value&&!options.probe)return
  const round=generation,account=identity(),draft=blocked.value
  const isCurrent=()=>!disposed&&round===generation&&account===identity()&&user.sessionReady&&!!user.token&&user.token===getToken()&&access.allowed()&&draft===blocked.value
  pending=false;running=true
  try{
   const result=await (draft?options.probe!:refresh)({isCurrent,quiet:true})
   if(isCurrent()){failed.value=result===false;failures=result===false?failures+1:0;retryAt=result===false?Date.now()+Math.min(60000,5000*2**Math.min(failures,4)):0}
  }catch{
   if(isCurrent()){failed.value=true;failures++;retryAt=Date.now()+Math.min(60000,5000*2**Math.min(failures,4))}
  }finally{running=false;schedule()}
 }
 function invalidate(){pending=true;retryAt=0;clearTimer();schedule(100)}
 function storage(event:StorageEvent){if(event.key===INVENTORY_CHANGED&&event.newValue)invalidate();else if(!available())clearTimer()}
 function foreground(){clearTimer();if(disposed||!available())return;if(document.visibilityState!=='hidden')invalidate()}
 watch(identity,()=>{generation++;failures=0;retryAt=0;failed.value=false;access.reset();invalidate()},{flush:'sync'})
 watch(blocked,()=>{generation++;invalidate()},{flush:'sync'})
 watch(()=>options.paused?.value,()=>{clearTimer();schedule()},{flush:'sync'})
 onMounted(()=>{mounted=true;window.addEventListener(INVENTORY_CHANGED,invalidate);window.addEventListener('storage',storage);window.addEventListener('focus',foreground);document.addEventListener('visibilitychange',foreground);schedule()})
 onBeforeUnmount(()=>{disposed=true;generation++;clearTimer();window.removeEventListener(INVENTORY_CHANGED,invalidate);window.removeEventListener('storage',storage);window.removeEventListener('focus',foreground);document.removeEventListener('visibilitychange',foreground)})
 return {invalidate,failed:readonly(failed)}
}
