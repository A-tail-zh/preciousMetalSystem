<template>
 <el-button v-if="allowed('metal:process-type:add')||allowed('metal:process-type:remove')" :class="inline?'inline-process-type':'add-process-type list-action list-action--create'" link type="primary" :disabled="!props.factoryId||saving" @click="open">{{inline?'维护工序类型':'添加工序类型'}}</el-button>
 <el-dialog append-to-body v-model="opened" class="base-info-form-dialog" :title="inline?'维护工序类型':'添加工序类型'" width="min(480px,94vw)" :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving">
  <el-form v-if="allowed('metal:process-type:add')" ref="formRef" :model="form" :rules="rules" label-position="left" label-width="112px" scroll-to-error :disabled="saving" @submit.prevent="save">
   <el-form-item label="工序类型名称" prop="name"><el-input v-model="form.name" placeholder="请输入工序类型名称" maxlength="100" show-word-limit @keyup.enter="save"/></el-form-item>
  </el-form>
  <el-table :data="options" class="type-management-table" max-height="260" empty-text="暂无工序类型"><el-table-column prop="name" label="已有工序类型" min-width="140"/><el-table-column v-if="allowed('metal:process-type:remove')" label="操作" width="80"><template #default="{row}"><el-button type="danger" size="small" :disabled="saving" @click="remove(row)">删除</el-button></template></el-table-column></el-table>
  <template #footer><el-button :disabled="saving" @click="opened=false">取消</el-button><el-button v-if="allowed('metal:process-type:add')" type="primary" :loading="saving" @click="save">确定</el-button></template>
 </el-dialog>
</template>
<script setup lang="ts">
import {ref,reactive,watch,onMounted,onBeforeUnmount,nextTick} from 'vue'
import {ElMessage,ElMessageBox,type FormInstance} from 'element-plus'
import request from '@/utils/request'
import {getToken} from '@/utils/auth'
import {textRules} from '@/utils/input-validation'
import {useActionPermission} from '@/composables/useActionPermission'
import useUserStore from '@/store/modules/user'
import type {Option} from '@/api/metal/master'
const props=defineProps<{factoryId?:string|null;inline?:boolean}>()
const emit=defineEmits<{loaded:[Option[]];removed:[string];changed:[options:Option[],addedId?:string];open:[value:boolean]}>(),user=useUserStore(),permission=useActionPermission()
const opened=ref(false),saving=ref(false),formRef=ref<FormInstance>(),form=reactive({name:''})
const rules={name:textRules('工序类型名称',100,true)}
type ProcessTypeOption=Option&{version:number}
const options=ref<ProcessTypeOption[]>([])
let readEpoch=0,editEpoch=0,disposed=false,scheduled=false
const identity=()=>JSON.stringify([props.factoryId,user.sessionReady,user.token,String(user.id),[...user.permissions].sort()])
const session=()=>!disposed&&user.sessionReady&&!!user.token&&user.token===getToken()
const allowed=(p:string)=>session()&&permission.has(p)
watch(opened,value=>{emit('open',value);if(!value){++editEpoch;saving.value=false;form.name=''}},{flush:'sync'})
function invalidate(){++readEpoch;++editEpoch;opened.value=false;saving.value=false;options.value=[];emit('loaded',options.value)}
function scheduleLoad(){if(scheduled||disposed)return;scheduled=true;queueMicrotask(()=>{scheduled=false;void load()})}
async function load(){
 if(!props.factoryId||!session()||!permission.hasAny(['metal:process:list','metal:process:add','metal:process:edit','metal:process:remove','metal:process-type:list','metal:process-type:add','metal:process-type:remove']))return
 const current=++readEpoch,account=identity(),valid=()=>session()&&current===readEpoch&&account===identity()
 try{const result=await request({url:'/metal/process/types',params:{factoryId:props.factoryId},isContextCurrent:valid}) as unknown as {data:ProcessTypeOption[]};if(!valid())return;options.value=result.data??[];emit('loaded',options.value)}catch{}
}
async function open(){if(!props.factoryId||!session()||!permission.hasAny(['metal:process-type:add','metal:process-type:remove'])||saving.value)return;++editEpoch;form.name='';opened.value=true;await nextTick();formRef.value?.clearValidate();void load()}
async function save(){
 if(!allowed('metal:process-type:add')||saving.value||!opened.value)return
 const current=editEpoch,account=identity(),valid=()=>allowed('metal:process-type:add')&&opened.value&&current===editEpoch&&account===identity()
 saving.value=true
 try{
  if(!await formRef.value?.validate().catch(()=>false)||!valid())return
  const result=await request({url:'/metal/process/types',method:'post',data:{name:form.name.trim(),factoryId:props.factoryId},isContextCurrent:valid}) as unknown as {data:ProcessTypeOption}
  if(!valid())return
  ++readEpoch // Invalidate pre-creation reads before publishing the new option.
  options.value=[...options.value.filter(o=>o.id!==result.data.id),result.data].sort((a,b)=>Number(a.id)-Number(b.id));emit('loaded',options.value)
  emit('changed',options.value,result.data.id);ElMessage.success('工序类型添加成功');opened.value=false;void load()
 }catch{void load()}finally{if(current===editEpoch)saving.value=false}
}
async function remove(row:ProcessTypeOption){
 if(saving.value||!opened.value||!allowed('metal:process-type:remove'))return
 const current=editEpoch,account=identity(),valid=()=>allowed('metal:process-type:remove')&&opened.value&&current===editEpoch&&account===identity()
 saving.value=true
 try{
  await ElMessageBox.confirm(`确定删除工序类型“${row.name}”吗？`,'删除工序类型',{type:'warning',confirmButtonText:'删除',cancelButtonText:'取消'})
  if(!valid())return
  await request({url:`/metal/process/types/${row.id}`,method:'delete',params:{factoryId:props.factoryId,version:row.version},isContextCurrent:valid})
  if(!valid())return
  ++readEpoch;options.value=options.value.filter(o=>o.id!==row.id);emit('loaded',options.value);emit('removed',row.id);emit('changed',options.value)
  ElMessage.success('工序类型已删除');void load()
 }catch{void load()}finally{if(current===editEpoch)saving.value=false}
}
defineExpose({refresh:load})
function checkSession(){if(!session())invalidate();else scheduleLoad()}
permission.onChange(()=>{invalidate();scheduleLoad()})
watch(()=>props.factoryId,()=>{invalidate();scheduleLoad()})
onMounted(()=>{emit('loaded',options.value);scheduleLoad();window.addEventListener('focus',checkSession);window.addEventListener('storage',checkSession);document.addEventListener('visibilitychange',checkSession)})
onBeforeUnmount(()=>{disposed=true;invalidate();window.removeEventListener('focus',checkSession);window.removeEventListener('storage',checkSession);document.removeEventListener('visibilitychange',checkSession)})
</script>
<style scoped>
.inline-process-type{height:20px;padding:0;white-space:nowrap}.add-process-type{white-space:nowrap;height:35px;padding:0 8px}.type-management-table{margin-top:16px;width:100%}
</style>
