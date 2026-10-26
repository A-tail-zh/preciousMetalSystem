import {onMounted,onBeforeUnmount,watch,type Ref} from 'vue'
import {INVENTORY_CHANGED} from '@/utils/inventory-change'
/** Coalesce notifications and defer background/modal updates without losing invalidations. */
export function useInventoryRefresh(refresh:()=>Promise<void>,blocked:Ref<boolean>,access:{allowed:()=>boolean;reset:()=>void}){
 let pending=false,disposed=false,running=false,timer:ReturnType<typeof setTimeout>|undefined
 function available(){if(access.allowed())return true;pending=false;access.reset();return false}
 function schedule(){
  if(disposed||!pending||!available()||blocked.value||running||document.visibilityState==='hidden'||timer)return
  timer=setTimeout(async()=>{timer=undefined;if(disposed||!available()||blocked.value||document.visibilityState==='hidden')return
   pending=false;running=true
   try{await refresh()}finally{running=false;schedule()}
  },100)
 }
 function invalidate(){pending=true;schedule()}
 function storage(event:StorageEvent){if(event.key===INVENTORY_CHANGED&&event.newValue)invalidate()}
 function foreground(){if(document.visibilityState!=='hidden')invalidate()}
 watch(blocked,()=>schedule())
 onMounted(()=>{window.addEventListener(INVENTORY_CHANGED,invalidate);window.addEventListener('storage',storage);window.addEventListener('focus',foreground);document.addEventListener('visibilitychange',foreground)})
 onBeforeUnmount(()=>{disposed=true;if(timer)clearTimeout(timer);window.removeEventListener(INVENTORY_CHANGED,invalidate);window.removeEventListener('storage',storage);window.removeEventListener('focus',foreground);document.removeEventListener('visibilitychange',foreground)})
}
