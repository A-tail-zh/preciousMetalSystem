<template><div class="storage-filter"><span>所属工厂</span><el-select :model-value="state.factoryId" aria-label="所属工厂" placeholder="请选择所属工厂" :loading="state.factoriesLoading" :disabled="!isAdmin||disabled" @change="selectFactory"><el-option v-for="f in state.factories" :key="f.id" :label="f.name" :value="f.id"/></el-select><slot/></div><el-alert v-if="state.factoryError" type="error" :closable="false"><template #title>{{state.factoryError}} <el-button link @click="loadFactories">重试</el-button></template></el-alert></template>
<script setup lang="ts">
import {useStorage} from './client';import '../storage-preview/style.scss'
const props=defineProps<{beforeChange?:()=>Promise<boolean>;disabled?:boolean}>();const {state,isAdmin,changeFactory,loadFactories,context}=useStorage()
async function selectFactory(id:string){const current=context();if(id!==state.factoryId&&(!props.beforeChange||await props.beforeChange())&&current===context())changeFactory(id)}
</script>
