<template>
 <div class="factory-navigation">
<div class="brand"><span class="brand-mark"><i></i><b></b></span><strong>贵金属加工系统</strong></div>
        <nav aria-label="主导航">
 <router-link v-if="can('metal:dashboard:list')" @click="emit('navigate')" class="nav-item" :class="{active:route.path==='/index'}" to="/index">数据看板</router-link>
 <template v-for="group in groups" :key="group.key">
  <button v-if="group.items.length" class="nav-group" :aria-expanded="expanded[group.key]" @click="expanded[group.key]=!expanded[group.key]"><span class="group-arrow">{{expanded[group.key]?'⌄':'›'}}</span>{{group.title}}</button>
  <template v-if="group.items.length&&expanded[group.key]">
   <template v-for="item in group.items" :key="item.title">
    <router-link @click="emit('navigate')" class="nav-item child" :class="{active:route.path===item.to}" :to="item.to">{{item.title}}</router-link>
   </template>
  </template>
  <router-link v-if="group.entry" @click="emit('navigate')" class="nav-item" :class="{active:route.path===group.entry.to}" :to="group.entry.to">{{group.entry.title}}</router-link>
 </template>
</nav>
        <button class="logout" @click="emit('logout')">⇥　登出</button>
 </div>
</template>
<script setup lang="ts">
import {computed,reactive} from 'vue'
import {useRoute} from 'vue-router'
import {businessNavigation} from '@/utils/business-navigation'
import useUserStore from '@/store/modules/user'
const emit=defineEmits(['navigate','logout']),route=useRoute(),user=useUserStore()
const expanded=reactive<Record<string,boolean>>({base:true,quote:true,warehouse:true,user:true,alert:true,logs:true})
const groups=computed(()=>businessNavigation(user))
function can(permission: string) { return user.sessionReady&&user.permissions.some((p: string) => p === '*:*:*' || p === permission) }
</script>
<style scoped>
.factory-navigation{height:100%;min-height:0;display:flex;flex-direction:column}
.brand { height:var(--business-header-height,56px); display:flex; align-items:center; gap:8px; padding-left:20px; color:#102d50; font-size:16px; white-space:nowrap; }
.brand-mark { position:relative; display:block; width:22px; height:24px; }
.brand-mark i { position:absolute; left:0; width:8px; height:24px; background:#367bef; }
.brand-mark b { position:absolute; right:0; width:13px; height:24px; border-radius:14px 0 0 14px; background:#367bef; }
nav { padding:12px 10px 0; font-size:14px; color:#303133; flex:1; min-height:0; overflow-y:auto; }
.nav-item,.nav-group { height:36px; line-height:36px; white-space:nowrap; display:flex; align-items:center; padding:0 20px; text-decoration:none; color:inherit; width:100%; background:none; border:none; font-size:inherit; text-align:left; cursor:pointer; }
.child { padding-left:36px; }
.active { background:#eef5ff; color:#577cf8; }
.nav-group { padding-left:15px; }
.muted { cursor:pointer; }
.group-arrow { width:16px; color:#8d96a5; }
.logout { margin:16px 26px 24px; padding:0; background:none; border:none; text-align:left; color:#7d8797; font-size:14px; cursor:pointer; }

@media(max-width:768px){.brand{height:64px;flex-shrink:0}.logout{margin:16px 32px max(20px,env(safe-area-inset-bottom))}}
</style>
