<template>
  <div class="prototype-frame" :style="{width:'100%', '--design-height':`${height}px`}">
    
    <div class="prototype-shell">
      <aside v-if="!isMobile" class="prototype-sidebar"><FactoryNavigation @logout="logout"/></aside>
      <el-drawer v-else v-model="mobileMenuOpen" id="mobile-factory-navigation" class="mobile-factory-navigation" title="导航菜单" direction="ltr" size="min(280px, 88vw)" append-to-body destroy-on-close>
        <FactoryNavigation @navigate="mobileMenuOpen=false" @logout="logout"/>
      </el-drawer>
      <div class="prototype-main">
        <header class="prototype-header"><el-button v-if="isMobile" class="mobile-menu-toggle" aria-label="打开导航菜单" aria-haspopup="dialog" aria-controls="mobile-factory-navigation" :aria-expanded="mobileMenuOpen" @click="mobileMenuOpen=true"><el-icon><Menu/></el-icon><span>菜单</span></el-button><el-dropdown trigger="click" placement="bottom-end" @command="logout"><button class="profile-menu-trigger" type="button" aria-label="账户菜单"><span class="avatar"><i></i><b></b></span><span>{{ user.nickName || user.name || '管理员' }}</span></button><template #dropdown><el-dropdown-menu><el-dropdown-item command="logout">退出登录</el-dropdown-item></el-dropdown-menu></template></el-dropdown></header>
        <main><slot /></main>
      </div>
    </div>
  </div>
</template>
<script setup lang="ts">
import {getToken} from '@/utils/auth'
import { useRouter, useRoute } from 'vue-router'
import {ref,watch,nextTick} from 'vue'
import {Menu} from '@element-plus/icons-vue'
import FactoryNavigation from './FactoryNavigation.vue'
import {useWindowSize,useMediaQuery} from '@vueuse/core'
import {ElMessageBox} from 'element-plus'
import useUserStore from '@/store/modules/user'
const user = useUserStore(), router = useRouter(), route = useRoute()
const {height}=useWindowSize()
const isMobile=useMediaQuery('(max-width: 768px)'),isLandscape=useMediaQuery('(orientation: landscape)'),mobileMenuOpen=ref(false)
watch(()=>route.fullPath,()=>{mobileMenuOpen.value=false})
watch([isMobile,isLandscape],()=>{mobileMenuOpen.value=false})
async function logout() {
  const token=getToken()
  mobileMenuOpen.value=false
  await nextTick()
  try { await ElMessageBox.confirm('确认退出系统？', '提示', {confirmButtonText:'确定',cancelButtonText:'取消'}) } catch { return }
  if(await user.logOut(token))await router.push('/login')
}
</script>
<style scoped>
.profile-menu-trigger{display:flex;align-items:center;gap:10px;border:0;background:transparent;cursor:pointer;color:inherit;font:inherit;padding:4px}

.prototype-frame { background:#f8fbff; min-height:var(--design-height,100vh); padding:0; font-family:Arial,'Microsoft YaHei',sans-serif; }
.prototype-shell { display:flex; min-height:var(--design-height,100vh); background:#f8fbff; }
.prototype-sidebar { background:#fff; width:247px; padding:0; margin:0; border-radius:0; line-height:normal; font-family:inherit; height:var(--design-height,100vh); position:sticky; top:0; flex-shrink:0; display:flex; flex-direction:column; border:1px solid #ebeef3; }
.prototype-main { flex:1; min-width:0; }
.prototype-header { height:82px; background:#fff; display:flex; justify-content:flex-end; align-items:center; gap:8px; padding:0 34px; font-size:14px; }
.avatar { position:relative; background:#367ced; border-radius:50%; width:40px; height:40px; }
.avatar i { position:absolute; width:16px; height:21px; background:#9dbfff; left:10px; top:11px; border-radius:50%; transform:rotate(-30deg); }
.avatar b { position:absolute; width:9px; height:9px; background:#e2ecff; left:21px; top:12px; border-radius:50%; }
.prototype-main main { padding:0; }

.mobile-menu-toggle{min-height:44px;margin-right:auto;gap:6px}
.mobile-menu-toggle:focus-visible{outline:2px solid #367ced;outline-offset:3px}
:global(.mobile-factory-navigation .el-drawer__body){padding:0;min-height:0;overflow:hidden}
@media(max-width:768px){
 .prototype-header{height:64px;padding:0 16px}
 .prototype-header :deep(.el-dropdown){min-width:0;max-width:calc(100% - 108px)}
 .profile-menu-trigger{min-height:44px;min-width:0;max-width:100%}
 .avatar{flex-shrink:0}
 .profile-menu-trigger>span:last-child{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
 .prototype-main{width:100%}
}
</style>
