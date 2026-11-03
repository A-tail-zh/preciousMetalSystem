<template>
 <el-button v-if="allowed('metal:process:add')" class="add-process-type list-action list-action--create" link type="primary" @click="open">添加工序类型</el-button>
 <el-dialog append-to-body v-model="opened" title="添加工序类型" width="min(480px,94vw)" :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving">
  <el-form ref="formRef" :model="form" :rules="rules" label-position="top" scroll-to-error :disabled="saving" @submit.prevent="save">
   <el-form-item label="工序类型" prop="name"><el-input v-model="form.name" placeholder="请输入工序类型名称" maxlength="100" show-word-limit @keyup.enter="save"/></el-form-item>
   <p class="type-help">保存后可在工序筛选及新增、编辑工序时选择。</p>
  </el-form>
  <template #footer><el-button :disabled="saving" @click="opened=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">确定</el-button></template>
 </el-dialog>
</template>
<script setup lang="ts">
import {ref,reactive,watch,onMounted,onBeforeUnmount,nextTick} from 'vue'
import {ElMessage,type FormInstance} from 'element-plus'
import request from '@/utils/request'
import {getToken} from '@/utils/auth'
import {textRules} from '@/utils/input-validation'
import {useActionPermission} from '@/composables/useActionPermission'
import useUserStore from '@/store/modules/user'
import {processTypes} from './master-config'
import type {Option} from '@/api/metal/master'
const emit=defineEmits<{loaded:[Option[]]}>(),user=useUserStore(),permission=useActionPermission()
const opened=ref(false),saving=ref(false),formRef=ref<FormInstance>(),form=reactive({name:''})
const rules={name:textRules('工序类型名称',100,true)}
const builtins=()=>processTypes.map((name,index)=>({id:String(index+1),name}))
let options:Option[]=builtins(),readEpoch=0,editEpoch=0,disposed=false,scheduled=false
const identity=()=>JSON.stringify([user.sessionReady,user.token,String(user.id),[...user.permissions].sort()])
const session=()=>!disposed&&user.sessionReady&&!!user.token&&user.token===getToken()
const allowed=(p:string)=>session()&&permission.has(p)
watch(opened,value=>{if(!value){++editEpoch;saving.value=false;form.name=''}},{flush:'sync'})
function invalidate(){++readEpoch;++editEpoch;opened.value=false;saving.value=false;options=builtins();emit('loaded',options)}
function scheduleLoad(){if(scheduled||disposed)return;scheduled=true;queueMicrotask(()=>{scheduled=false;void load()})}
async function load(){
 if(!session()||!permission.hasAny(['metal:process:list','metal:process:add','metal:process:edit']))return
 const current=++readEpoch,account=identity(),valid=()=>session()&&current===readEpoch&&account===identity()
 try{const result=await request({url:'/metal/process/types',isContextCurrent:valid}) as unknown as {data:Option[]};if(!valid())return;options=result.data?.length?result.data:builtins();emit('loaded',options)}catch{}
}
async function open(){if(!allowed('metal:process:add')||saving.value)return;++editEpoch;form.name='';opened.value=true;await nextTick();formRef.value?.clearValidate()}
async function save(){
 if(!allowed('metal:process:add')||saving.value||!opened.value)return
 const current=editEpoch,account=identity(),valid=()=>allowed('metal:process:add')&&opened.value&&current===editEpoch&&account===identity()
 saving.value=true
 try{
  if(!await formRef.value?.validate().catch(()=>false)||!valid())return
  const result=await request({url:'/metal/process/types',method:'post',data:{name:form.name.trim()},isContextCurrent:valid}) as unknown as {data:Option}
  if(!valid())return
  ++readEpoch // Invalidate pre-creation reads before publishing the new option.
  options=[...options.filter(o=>o.id!==result.data.id),result.data].sort((a,b)=>Number(a.id)-Number(b.id));emit('loaded',options)
  ElMessage.success('工序类型添加成功');opened.value=false;void load()
 }catch{}finally{if(current===editEpoch)saving.value=false}
}
function checkSession(){if(!session())invalidate()}
permission.onChange(()=>{invalidate();scheduleLoad()})
onMounted(()=>{emit('loaded',options);scheduleLoad();window.addEventListener('focus',checkSession);window.addEventListener('storage',checkSession);document.addEventListener('visibilitychange',checkSession)})
onBeforeUnmount(()=>{disposed=true;invalidate();window.removeEventListener('focus',checkSession);window.removeEventListener('storage',checkSession);document.removeEventListener('visibilitychange',checkSession)})
</script>
<style scoped>
.add-process-type{white-space:nowrap;height:35px;padding:0 8px}.type-help{margin:0;color:var(--el-text-color-secondary);font-size:13px;line-height:1.6}
</style>
