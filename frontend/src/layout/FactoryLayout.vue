<template>
  <div class="prototype-frame" :style="{width:'100%', '--design-height':`${height}px`}">
    
    <div class="prototype-shell">
      <aside class="prototype-sidebar">
        <div class="brand"><span class="brand-mark"><i></i><b></b></span><strong>贵金属加工系统</strong></div>
        <nav aria-label="主导航">
 <router-link class="nav-item" :class="{active:route.path==='/index'}" to="/index">数据看板</router-link>
 <template v-for="group in groups" :key="group.key">
  <button v-if="group.items.length" class="nav-group" :aria-expanded="expanded[group.key]" @click="expanded[group.key]=!expanded[group.key]"><span class="group-arrow">{{expanded[group.key]?'⌄':'›'}}</span>{{group.title}}</button>
  <template v-if="group.items.length&&expanded[group.key]">
   <template v-for="item in group.items" :key="item.title">
    <router-link v-if="item.to&&(!item.permission||can(item.permission))" class="nav-item child" :class="{active:route.path===item.to}" :to="item.to">{{item.title}}</router-link>
    <button v-else-if="!item.to" class="nav-item child muted" @click="unavailable(item.title)">{{item.title}}</button>
   </template>
  </template>
  <router-link v-if="group.key==='quote'&&can('metal:work-order:list')" class="nav-item" :class="{active:route.path==='/metal/work-order'}" to="/metal/work-order">生产工单管理</router-link>
 </template>
</nav>
        <button class="logout" @click="logout">⇥　登出</button>
      </aside>
      <div class="prototype-main">
        <header class="prototype-header"><el-dropdown trigger="click" placement="bottom-end" @command="logout"><button class="profile-menu-trigger" type="button" aria-label="账户菜单"><span class="avatar"><i></i><b></b></span><span>{{ user.nickName || user.name || '管理员' }}</span></button><template #dropdown><el-dropdown-menu><el-dropdown-item command="logout">退出登录</el-dropdown-item></el-dropdown-menu></template></el-dropdown></header>
        <main><slot /></main>
      </div>
    </div>
  </div>
</template>
<script setup lang="ts">
import {getToken} from '@/utils/auth'
import { useRouter, useRoute } from 'vue-router'
import { computed, reactive } from 'vue'
import { useWindowSize } from '@vueuse/core'
import { ElMessageBox, ElMessage } from 'element-plus'
import useUserStore from '@/store/modules/user'
const user = useUserStore(), router = useRouter(), route = useRoute()
const {height}=useWindowSize()
const expanded=reactive<Record<string,boolean>>({base:true,quote:true,warehouse:true,user:true,alert:true,logs:true})
const menuGroups=[
 {key:'base',title:'基础信息管理',items:[{title:'工厂管理',to:'/metal/factory',permission:'metal:factory:list'},{title:'产品管理',to:'/metal/product',permission:'metal:product:list'},{title:'产线管理',to:'/metal/production-line',permission:'metal:production-line:list'},{title:'物料管理',to:'/metal/material',permission:'metal:material:list'},{title:'工序管理',to:'/metal/process',permission:'metal:process:list'}]},
 {key:'quote',title:'报价管理',items:[{title:'报价规则管理',to:'/metal/quote-rule',permission:'metal:quote-rule:list'},{title:'自动报价单',to:'/metal/quotation',permission:'metal:quotation:list'}]},
 {key:'warehouse',title:'仓储管理',items:[{title:'库存管理',to:'/metal/inventory',permission:'metal:inventory:list'},{title:'仓储工单管理',to:'/metal/warehouse-order',permission:'metal:warehouse-order:list'},{title:'仓库管理',to:'/metal/warehouse',permission:'metal:warehouse:list'}]},
 {key:'user',title:'用户管理',items:[{title:'人员管理',to:'/system/user',permission:'system:user:list'},{title:'角色管理',to:'/system/role',permission:'system:role:list'}]},
 {key:'logs',title:'系统管理',items:[{title:'操作日志',to:'/audit/operations',permission:'monitor:operlog:list'},{title:'登录日志',to:'/audit/logins',permission:'monitor:logininfor:list'},{title:'系统运行日志',to:'/audit/runtime',permission:'monitor:runtime-log:list'},{title:'日志配置',to:'/audit/config',permission:'monitor:log-config:query'}]},
 {key:'alert',title:'预警管理',items:[{title:'预警记录',to:'/metal/warning-record',permission:'metal:warning-record:list'}]}
]
const groups=computed(()=>menuGroups.map(group=>({...group,items:group.items.filter(item=>can(item.permission)&&(group.key!=='logs'||item.title==='操作日志'||String(user.id)==='1'))})).filter(group=>group.items.length||(group.key==='quote'&&can('metal:work-order:list'))))
function unavailable(title:string){ElMessage.info(`${title}不在本期实施范围`)}
function can(permission: string) { return user.permissions.some((p: string) => p === '*:*:*' || p === permission) }
async function logout() {
  const token=getToken()
  try { await ElMessageBox.confirm('确认退出系统？', '提示', {confirmButtonText:'确定',cancelButtonText:'取消'}) } catch { return }
  if(await user.logOut(token))await router.push('/login')
}
</script>
<style scoped>
.profile-menu-trigger{display:flex;align-items:center;gap:10px;border:0;background:transparent;cursor:pointer;color:inherit;font:inherit;padding:4px}

.prototype-frame { background:#f8fbff; min-height:var(--design-height,100vh); padding:0; font-family:Arial,'Microsoft YaHei',sans-serif; }
.prototype-shell { display:flex; min-height:var(--design-height,100vh); background:#f8fbff; }
.prototype-sidebar { background:#fff; width:247px; padding:0; margin:0; border-radius:0; line-height:normal; font-family:inherit; height:var(--design-height,100vh); position:sticky; top:0; flex-shrink:0; display:flex; flex-direction:column; border:1px solid #ebeef3; }
.brand { height:82px; display:flex; align-items:center; gap:8px; padding-left:29px; color:#102d50; font-size:16px; white-space:nowrap; }
.brand-mark { position:relative; display:block; width:22px; height:24px; }
.brand-mark i { position:absolute; left:0; width:8px; height:24px; background:#367bef; }
.brand-mark b { position:absolute; right:0; width:13px; height:24px; border-radius:14px 0 0 14px; background:#367bef; }
nav { padding:10px 11px 0; font-size:14px; color:#303133; flex:1; min-height:0; overflow-y:auto; }
.nav-item,.nav-group { height:36px; line-height:36px; white-space:nowrap; display:flex; align-items:center; padding:0 30px; text-decoration:none; color:inherit; width:100%; background:none; border:none; font-size:inherit; text-align:left; cursor:pointer; }
.child { padding-left:51px; }
.active { background:#eef5ff; color:#577cf8; }
.nav-group { padding-left:15px; }
.muted { cursor:pointer; }
.group-arrow { width:16px; color:#8d96a5; }
.logout { margin:24px 32px 42px; padding:0; background:none; border:none; text-align:left; color:#7d8797; font-size:14px; cursor:pointer; }
.prototype-main { flex:1; min-width:0; }
.prototype-header { height:82px; background:#fff; display:flex; justify-content:flex-end; align-items:center; gap:8px; padding:0 34px; font-size:14px; }
.avatar { position:relative; background:#367ced; border-radius:50%; width:40px; height:40px; }
.avatar i { position:absolute; width:16px; height:21px; background:#9dbfff; left:10px; top:11px; border-radius:50%; transform:rotate(-30deg); }
.avatar b { position:absolute; width:9px; height:9px; background:#e2ecff; left:21px; top:12px; border-radius:50%; }
.prototype-main main { padding:0; }
</style>
