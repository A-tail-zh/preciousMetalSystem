import {onMounted,onBeforeUnmount} from 'vue'
import {useRouter} from 'vue-router'
import useUserStore from '@/store/modules/user'
import usePermissionStore from '@/store/modules/permission'
import {PERMISSION_CHANGED,isPermissionChange} from '@/utils/permission-change'

export function usePermissionSync(){
  const user=useUserStore(),permissions=usePermissionStore(),router=useRouter()
  let generation=0,applied=0,failures=0,retry:ReturnType<typeof setTimeout>|undefined
  async function refresh(){
    if(generation===applied||!user.sessionReady||document.visibilityState!=='visible')return
    const target=generation
    try{
      if(!await permissions.refreshPermissions()||generation!==target){
        if(generation!==target){void refresh();return}
        if(++failures>2){applied=target;return}
        clearTimeout(retry);retry=setTimeout(()=>void refresh(),2000);return
      }
      failures=0;applied=target
      const current=router.currentRoute.value,targetRoute=router.resolve(current.fullPath)
      await router.replace(targetRoute.matched.some(r=>r.path==='/:pathMatch(.*)*')?permissions.managementPath:current.fullPath)
    }catch{if(generation===target&&++failures<=2){clearTimeout(retry);retry=setTimeout(()=>void refresh(),2000)}else if(generation===target)applied=target}
  }
  function changed(){generation++;failures=0;void refresh()}
  function storage(event:StorageEvent){if(isPermissionChange(event))changed()}
  function visible(){if(document.visibilityState==='visible')void refresh()}
  onMounted(()=>{window.addEventListener(PERMISSION_CHANGED,changed);window.addEventListener('storage',storage);document.addEventListener('visibilitychange',visible)})
  onBeforeUnmount(()=>{clearTimeout(retry);window.removeEventListener(PERMISSION_CHANGED,changed);window.removeEventListener('storage',storage);document.removeEventListener('visibilitychange',visible)})
}
