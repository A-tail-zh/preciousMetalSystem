<template>
 <el-button v-if="permission.has('metal:customer:add')" link type="primary" :disabled="!factoryId" @click="open">添加客户</el-button>
 <el-dialog v-model="visible" title="添加客户" class="warehouse-customer-dialog" width="min(720px,94vw)" append-to-body :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving">
  <el-form ref="formRef" :model="form" :rules="rules" label-position="top" :disabled="saving" scroll-to-error class="customer-create-fields">
   <el-form-item label="所属工厂" class="customer-wide"><el-input :model-value="factoryName" aria-label="所属工厂" readonly/></el-form-item>
   <el-form-item v-for="field in fields" :key="field.key" :label="field.label" :prop="field.key" :class="{'customer-wide':field.key==='address'}">
    <el-input v-model="form[field.key]" :aria-label="field.label" :maxlength="field.max" :type="field.key==='address'?'textarea':'text'" :rows="2" :inputmode="field.key==='contactPhone'?'tel':undefined"/>
   </el-form-item>
  </el-form>
  <p v-if="saveError" role="alert" class="customer-create-error">{{saveError}}</p>
  <template #footer><el-button :disabled="saving" @click="visible=false">取消</el-button><el-button v-if="permission.has('metal:customer:add')" type="primary" :loading="saving" @click="save">保存</el-button></template>
 </el-dialog>
</template>
<script setup lang="ts">
import {reactive,ref,watch,onBeforeUnmount,nextTick} from 'vue'
import {ElMessage,type FormInstance,type FormRules} from 'element-plus'
import {useActionPermission} from '@/composables/useActionPermission'
import {customerSave,type CustomerForm} from '@/api/metal/customer'
import {textRules,fieldRule,applyFieldErrors} from '@/utils/input-validation'
import {userMessage} from '@/utils/user-message'

const props=defineProps<{factoryId:string;factoryName:string;contextKey:number;isContextCurrent:()=>boolean}>()
const emit=defineEmits<{created:[{id:string;factoryId:string;contextKey:number}]}>()
const permission=useActionPermission(),visible=ref(false),saving=ref(false),saveError=ref(''),formRef=ref<FormInstance>()
const fields=[{key:'name',label:'客户名称',max:100},{key:'companyName',label:'客户公司名称',max:100},{key:'address',label:'客户地址',max:500},{key:'contactName',label:'联络人名称',max:100},{key:'contactPhone',label:'联络人电话',max:11},{key:'department',label:'部门',max:100}] as const
const blank=():CustomerForm=>({name:'',companyName:'',address:'',contactName:'',contactPhone:'',department:null})
const form=reactive<CustomerForm>(blank())
const rules:FormRules=Object.fromEntries(fields.map(f=>[f.key,textRules(f.label,f.max,f.key!=='department')]))
rules.contactPhone=[...textRules('联络人电话',11,true),fieldRule(v=>/^1[3-9]\d{9}$/.test(String(v??'').trim())?'':'联络人电话须为11位大陆手机号')]
let epoch=0,disposed=false
function invalidate(){epoch++;visible.value=false;saving.value=false}
async function open(){
 if(saving.value||!props.factoryId||!permission.has('metal:customer:add')||!props.isContextCurrent())return
 epoch++;Object.assign(form,blank(),{factoryId:props.factoryId});saveError.value='';visible.value=true
 await nextTick();formRef.value?.clearValidate()
}
async function save(){
 if(saving.value||!visible.value||!permission.has('metal:customer:add')||!props.isContextCurrent())return
 const round=epoch,factory=props.factoryId,context=props.contextKey
 const current=()=>!disposed&&visible.value&&round===epoch&&factory===props.factoryId&&context===props.contextKey&&permission.has('metal:customer:add')&&props.isContextCurrent()
 saving.value=true;saveError.value=''
 try{
  for(const f of fields){const value=String(form[f.key]??'').trim();if(f.key==='department')form.department=value||null;else form[f.key]=value}
  if(!await formRef.value?.validate().catch(()=>false)||!current())return
  const response:any=await customerSave({...form,factoryId:factory},current)
  if(!current())return
  if(response.id==null)throw Error('客户已保存但未返回编号，请从客户列表核对后选择')
  emit('created',{id:String(response.id),factoryId:factory,contextKey:context})
  ElMessage.success('客户创建成功');visible.value=false
 }catch(error:any){if(current()){saveError.value=userMessage(error?.msg??error?.message,'客户创建失败，请核对后重试');applyFieldErrors(formRef.value,error)}}
 finally{if(!disposed&&round===epoch)saving.value=false}
}
watch(()=>[props.factoryId,props.contextKey],invalidate,{flush:'sync'})
permission.onChange(invalidate)
onBeforeUnmount(()=>{disposed=true;epoch++})
</script>
<style scoped>
.customer-create-fields{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:0 20px}
.customer-create-fields>.el-form-item{min-width:0;margin-bottom:16px}
.customer-wide{grid-column:1/-1}
.customer-create-fields :deep(.el-form-item__error){position:static;line-height:1.5}
.customer-create-error{color:var(--el-color-danger);overflow-wrap:anywhere}
@media(max-width:600px){.customer-create-fields{grid-template-columns:minmax(0,1fr)}}
</style>
