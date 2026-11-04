<template>
 <section class="batch-choice" aria-label="加工批次安排" v-loading="loading">
  <p v-if="info" role="status">{{info.measurementMode==='WEIGHT'?'本工序按实测净重管理':`本工序剩余可安排 ${info.availableQuantity} 件；待验收批次仍占用计划额度。`}}</p>
  <el-form-item label="批次安排">
   <el-radio-group :model-value="modelValue.batchAction" :disabled="disabled||loading" @change="changeAction(String($event))">
    <el-radio value="NEW">新开加工批次</el-radio><el-radio value="APPEND">当前批次补料</el-radio>
   </el-radio-group>
  </el-form-item>
  <template v-if="modelValue.batchAction==='NEW'">
   <el-form-item v-if="info?.measurementMode==='PIECE'" label="本次计划件数"><el-input :model-value="modelValue.plannedQuantity" :disabled="disabled||loading" inputmode="numeric" placeholder="填写本批件数，不是整单剩余件数" @update:model-value="emit('update:modelValue',{...modelValue,plannedQuantity:String($event)})"/></el-form-item>
   <p v-if="info&&!info.canCreate" role="alert">{{info.blockedReason}}</p>
  </template>
  <el-form-item v-else label="补料加工批次"><el-select :model-value="modelValue.batchId" :disabled="disabled||loading" placeholder="请选择尚未提交交接的批次" @change="chooseBatch(String($event))"><el-option v-for="b in appendable" :key="b.id" :value="b.id" :label="b.batchNo+' / '+(info?.measurementMode==='WEIGHT'?'按重量':b.plannedQuantity+'件')"/></el-select></el-form-item>
  <p v-if="info?.batches?.some((b:Row)=>b.handoverSubmitted)">前批已提交完整交接，可独立安排下一批；其产出验收后才能进入下一工序。</p>
  <p v-if="error" role="alert">{{error}}</p>
  <el-button link type="primary" :disabled="disabled||loading" @click="reload">重新核对批次额度</el-button>
 </section>
</template>
<script setup lang="ts">
import {computed,ref,watch,onBeforeUnmount} from 'vue'
import request from '@/utils/request'
import {getToken} from '@/utils/auth'
import useUserStore from '@/store/modules/user'
import {useInventoryRefresh} from '@/composables/useInventoryRefresh'
import type {Row} from '@/api/metal/warehouse'
export type BatchChoice={batchAction:string;batchId:string|null;batchVersion:number|null;plannedQuantity:string}
const props=defineProps<{factoryId:string;workOrderId:string;itemId:string;processId:string;modelValue:BatchChoice;disabled:boolean;isContextCurrent:()=>boolean}>()
const emit=defineEmits<{ 'update:modelValue':[value:BatchChoice];stale:[] }>()
const info=ref<Row>(),loading=ref(false),error=ref(''),stale=ref(false),user=useUserStore()
const appendable=computed<Row[]>(()=>info.value?.batches?.filter((b:Row)=>b.canAppend)??[])
const context=()=>JSON.stringify([props.factoryId,props.workOrderId,props.itemId,props.processId,user.token,String(user.id)])
let epoch=0,disposed=false,baseline=''
function allowed(){return !disposed&&props.isContextCurrent()&&!!user.token&&user.token===getToken()}
async function fetchPlan(background=false){
 const ticket=epoch,key=context(),valid=()=>allowed()&&ticket===epoch&&key===context()&&(!background||!props.disabled)
 const response=await request({url:'/metal/warehouse/batch-planning',params:{factoryId:props.factoryId,workOrderId:props.workOrderId,itemId:props.itemId,processId:props.processId},isContextCurrent:valid,quiet:background})
 if(!valid())return
 const value=response.data
 if(!value||String(value.itemId)!==props.itemId||String(value.processId)!==props.processId||String(value.workOrderId)!==props.workOrderId||String(value.factoryId)!==props.factoryId)throw Error('批次关联响应不匹配')
 return value as Row
}
function changeAction(action:string){if(props.disabled||loading.value)return;error.value='';emit('update:modelValue',{batchAction:action,batchId:null,batchVersion:null,plannedQuantity:''});if(action==='APPEND'&&appendable.value.length===1)chooseBatch(appendable.value[0].id)}
function chooseBatch(id:string){const b=appendable.value.find(b=>b.id===id);if(!b||props.disabled)return;emit('update:modelValue',{batchAction:'APPEND',batchId:id,batchVersion:b.version,plannedQuantity:''})}
async function reload(){
 if(!allowed()||props.disabled||loading.value||![props.factoryId,props.workOrderId,props.itemId,props.processId].every(Boolean))return
 const ticket=++epoch;loading.value=true;error.value=''
 try{const value=await fetchPlan();if(!value||ticket!==epoch)return;info.value=value;baseline=JSON.stringify(value);stale.value=false
  if(props.modelValue.batchAction==='APPEND'){
   const b=appendable.value.find(b=>b.id===props.modelValue.batchId)
   if(b)emit('update:modelValue',{...props.modelValue,batchVersion:b.version})
   else if(!props.modelValue.batchId&&appendable.value.length===1)chooseBatch(appendable.value[0].id)
   else if(props.modelValue.batchId)error.value='原补料批次已变化，请重新选择；已填物料保留。'
  }else if(!value.canCreate&&appendable.value.length===1&&!props.modelValue.plannedQuantity)chooseBatch(appendable.value[0].id)
 }catch(e:any){if(ticket===epoch){stale.value=true;error.value=e.message||'批次额度加载失败，请重新核对'}}finally{if(ticket===epoch)loading.value=false}
}
function validate(){
 if(!allowed()||props.disabled||loading.value||!info.value||stale.value){error.value='请先重新核对批次额度';return false}
 if(props.modelValue.batchAction==='NEW'){
  if(!info.value.canCreate){error.value=info.value.blockedReason;return false}
  const qty=props.modelValue.plannedQuantity
  if(info.value.measurementMode==='PIECE'&&(!/^[1-9]\d*$/.test(qty)||Number(qty)>1000000||Number(qty)>Number(info.value.availableQuantity))){error.value='本次计划件数须为正整数，且不能超过工序剩余可安排件数';return false}
 }else if(!appendable.value.some(b=>b.id===props.modelValue.batchId&&b.version===props.modelValue.batchVersion)){error.value='请选择有效的补料批次并核对版本';return false}
 return true
}
watch(context,()=>{epoch++;loading.value=false;info.value=undefined;baseline='';stale.value=false;error.value='';emit('update:modelValue',{batchAction:'NEW',batchId:null,batchVersion:null,plannedQuantity:''});void reload()},{immediate:true})
watch(()=>props.disabled,v=>{if(!v&&!info.value)void reload()})
const {failed}=useInventoryRefresh(async()=>{},computed(()=>true),{allowed,reset:()=>{epoch++;info.value=undefined;stale.value=true}},{paused:computed(()=>props.disabled||loading.value),probe:async ctx=>{if(!info.value)return;const value=await fetchPlan(true);if(ctx.isCurrent()&&value&&JSON.stringify(value)!==baseline){stale.value=true;error.value='批次或额度已更新，请重新核对；本次计划和物料输入已保留。';emit('stale')}}})
watch(failed,v=>{if(v){stale.value=true;error.value='批次更新检查失败，请重新核对后提交'}})
onBeforeUnmount(()=>{disposed=true;epoch++})
defineExpose({validate,reload})
</script>
<style scoped>
.batch-choice{grid-column:1/-1;border:1px solid var(--el-border-color);border-radius:4px;padding:10px 14px;min-width:0}.batch-choice p{margin:4px 0 10px;line-height:1.7;overflow-wrap:anywhere}.batch-choice .el-form-item{margin-bottom:12px}.batch-choice .el-select,.batch-choice .el-input{width:min(460px,100%)}
</style>
