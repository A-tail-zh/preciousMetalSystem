<template>
 <el-button v-if="canMaintain" link type="primary" :disabled="disabled||!factoryId" @click="visible=true">维护物料类型</el-button>
 <el-dialog v-if="visible" :model-value="true" title="维护物料类型" width="min(1050px,94vw)" append-to-body :close-on-click-modal="false" :before-close="close">
  <MaterialTypeManager :key="factoryId??''" ref="manager" :factory-id="factoryId!" :factory-name="factoryName" @changed="name=>emit('changed',name)"/>
  <template #footer><el-button @click="close()">关闭</el-button></template>
 </el-dialog>
</template>
<script setup lang="ts">
import {computed,ref,watch} from 'vue'
import {useActionPermission} from '@/composables/useActionPermission'
import MaterialTypeManager from './MaterialTypeManager.vue'
const props=defineProps<{factoryId?:string|null;factoryName:string;disabled:boolean}>()
const emit=defineEmits<{changed:[name?:string];open:[value:boolean]}>()
const permission=useActionPermission(),visible=ref(false),manager=ref<InstanceType<typeof MaterialTypeManager>>()
const canMaintain=computed(()=>permission.has('metal:material-type:list')&&['add','edit','remove'].some(a=>permission.has(`metal:material-type:${a}`)))
watch(visible,v=>emit('open',v),{flush:'sync'})
watch(()=>props.factoryId,()=>{visible.value=false})
permission.onChange(()=>{visible.value=false})
async function close(done?:()=>void){if(await manager.value?.confirmLeave()){visible.value=false;done?.()}}
</script>
