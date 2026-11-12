<template>
 <el-dialog v-model="visible" title="结束本工序" width="600px" append-to-body :close-on-click-modal="false" :before-close="close">
  <el-alert v-if="result" title="本工序已结束" type="success" :closable="false"/>
  <el-alert v-else-if="operation.error.value" :title="operation.pending.value?'工序结束结果待核实':operation.error.value" type="error" :closable="false"/>
  <template v-if="result"><el-descriptions :column="2" border><el-descriptions-item v-for="c in columns" :key="c.key" :label="c.label">{{result[c.key]??'—'}}</el-descriptions-item></el-descriptions><el-alert v-if="refreshFailed" title="本工序已结束，流程刷新失败，请重试" type="warning" :closable="false"/><el-button v-if="refreshFailed" :loading="refreshing" @click="reload">刷新流程</el-button></template>
  <p v-else>确认已核对加工记录 {{batch?.number||batch?.id}}，所有产出、余料和回收均已登记？请核对本工序的物料平衡。</p>
  <el-button v-if="operation.error.value&&!operation.pending.value&&!result" @click="documents">查看关联仓储单据</el-button>
  <template #footer>
   <el-button :disabled="operation.busy.value||!!operation.pending.value" @click="close()">{{result?'关闭':'取消'}}</el-button>
   <template v-if="operation.pending.value&&!operation.busy.value"><el-button type="primary" @click="operation.verify">核实结果</el-button><el-button v-if="operation.notFound.value" @click="operation.retry">重试原请求</el-button></template>
   <el-button v-else-if="!result" type="primary" :loading="operation.busy.value" :disabled="!!operation.pending.value" @click="submit">{{operation.busy.value?'正在结束':'已核对，结束工序'}}</el-button>
  </template>
 </el-dialog>
</template>
<script setup lang="ts">
import {ref,watch,onBeforeUnmount} from 'vue'
import {useStorage,useOperation,type Row} from '../storage-live/client'
const props=defineProps<{factoryId:string;orderId:string;refresh:()=>Promise<boolean>}>(),emit=defineEmits<{documents:[]}>(),{context}=useStorage()
const visible=ref(false),batch=ref<Row>(),result=ref<Row>(),refreshFailed=ref(false),refreshing=ref(false)
const columns=[{key:'inputWeight',label:'投入（g）'},{key:'outputWeight',label:'产出（g）'},{key:'returnedWeight',label:'原样退回（g）'},{key:'recoveredWeight',label:'回收料（g）'},{key:'lossWeight',label:'损耗（g）'}]
const identity=()=>JSON.stringify([context(),props.factoryId,props.orderId,visible.value])
async function reload(){const current=identity();refreshing.value=true;try{const ok=await props.refresh();if(current===identity())refreshFailed.value=!ok}catch{if(current===identity())refreshFailed.value=true}finally{if(current===identity())refreshing.value=false}}
const operation=useOperation('/metal/production-order/executions',identity,async data=>{result.value=data;await reload()})
function open(row:Row){if(operation.busy.value||operation.pending.value)return;batch.value={...row};result.value=undefined;refreshFailed.value=false;visible.value=true;operation.clear()}
async function submit(){if(!batch.value||result.value)return;await operation.send(`/metal/production-order/executions/${batch.value.id}/close`,{factoryId:props.factoryId,version:batch.value.version,physicalChecked:true})}
function close(done?:()=>void){if(operation.busy.value||operation.pending.value)return;visible.value=false;done?.()}
function documents(){close();emit('documents')}
function requestClose(){close();return !visible.value}
watch(()=>[context(),props.factoryId,props.orderId],()=>{visible.value=false;result.value=undefined})
onBeforeUnmount(()=>{visible.value=false})
defineExpose({open,requestClose})
</script>
<style scoped>.el-descriptions{margin:16px 0}.el-alert+.el-button{margin-top:12px}</style>
