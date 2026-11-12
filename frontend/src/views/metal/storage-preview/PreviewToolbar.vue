<template>
  <div class="storage-filter">
    <span>所属工厂</span><el-select :model-value="state.factoryId" aria-label="所属工厂" placeholder="请选择所属工厂" :disabled="!isAdmin" @change="selectFactory"><el-option v-for="f in state.factories" :key="f.id" :label="f.name" :value="f.id" /></el-select>
    <el-tag type="info" effect="plain">前端预览</el-tag>
    <slot />
  </div>
</template>
<script setup lang="ts">
import {useStoragePreview} from './state'
import './style.scss'
const props=defineProps<{beforeChange?:()=>Promise<boolean>}>()
const {state,isAdmin,changeFactory}=useStoragePreview()
async function selectFactory(id:string){const context=state.factories;if(id!==state.factoryId&&(!props.beforeChange||await props.beforeChange())&&context===state.factories)changeFactory(id)}
</script>
