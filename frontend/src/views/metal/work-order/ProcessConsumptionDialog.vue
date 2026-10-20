<template>
 <el-dialog v-model="visible" title="工序消耗明细" width="min(1500px, 96vw)" class="settled-consumption-dialog" :close-on-click-modal="false" destroy-on-close>
  <template v-if="visible&&target">
   <p class="target-process">工序：{{target.processName}}</p>
   <ProcessConsumption :key="generation" :work-order-id="target.workOrderId" :item-id="target.itemId" :process-id="target.processId" :product-label="target.productLabel" batch-scope="SETTLED"/>
  </template>
  <template #footer><el-button @click="close">关闭</el-button></template>
 </el-dialog>
</template>
<script setup lang="ts">
import {ref,watch,onBeforeUnmount} from 'vue'
import ProcessConsumption from './ProcessConsumption.vue'
interface Target{workOrderId:string;itemId:string;processId:string;processName:string;productLabel:string}
const visible=ref(false),target=ref<Readonly<Target>>(),generation=ref(0)
let disposed=false
function open(value:Target){if(disposed)return;generation.value++;target.value=Object.freeze({...value});visible.value=true}
function close(){visible.value=false;target.value=undefined;generation.value++}
watch(visible,value=>{if(!value){target.value=undefined;generation.value++}},{flush:'sync'})
onBeforeUnmount(()=>{disposed=true;close()})
defineExpose({open,close})
</script>
<style>
.settled-consumption-dialog{margin-top:4vh;max-height:92vh;display:flex;flex-direction:column}.settled-consumption-dialog .el-dialog__header,.settled-consumption-dialog .el-dialog__footer{flex-shrink:0}.settled-consumption-dialog .el-dialog__body{max-height:calc(92vh - 130px);overflow:auto;min-width:0;min-height:0;flex:1}.settled-consumption-dialog .target-process{font-weight:600;overflow-wrap:anywhere}.settled-consumption-dialog .process-consumption{width:100%;max-width:100%}
</style>
