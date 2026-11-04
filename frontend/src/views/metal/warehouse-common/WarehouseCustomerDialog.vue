<template>
 <el-button v-if="permission.has('metal:customer:add')" link type="primary" :disabled="!factoryId" @click="open">添加客户</el-button>
 <el-dialog v-model="visible" title="添加客户" class="warehouse-customer-dialog" width="min(720px,94vw)" append-to-body :close-on-click-modal="false" :close-on-press-escape="!saving&&!checking&&!uncertain" :show-close="!saving&&!checking&&!uncertain">
  <el-form ref="formRef" :model="form" :rules="rules" label-position="top" :disabled="saving||checking||uncertain" scroll-to-error class="customer-create-fields">
   <el-form-item label="所属工厂" class="customer-wide"><el-input :model-value="factoryName" aria-label="所属工厂" readonly/></el-form-item>
   <el-form-item v-for="field in fields" :key="field.key" :label="field.label" :prop="field.key" :class="{'customer-wide':field.key==='address'}">
    <el-input v-model="form[field.key]" :aria-label="field.label" :maxlength="field.max" :type="field.key==='address'?'textarea':'text'" :rows="2" :inputmode="field.key==='contactPhone'?'tel':undefined"/>
   </el-form-item>
  </el-form>
  <p v-if="saveError" role="alert" class="customer-create-error">{{saveError}}</p>
  <el-button v-if="uncertain" :loading="checking" :disabled="saving" @click="reconcile">核对创建结果</el-button>
  <el-table v-if="matches.length" :data="matches" max-height="240" class="customer-match-table"><el-table-column prop="name" label="客户" min-width="100"/><el-table-column prop="companyName" label="公司" min-width="140"/><el-table-column prop="address" label="地址" min-width="160"/><el-table-column label="联络人" min-width="160"><template #default="{row}">{{row.contactName}} / {{row.contactPhone}}</template></el-table-column><el-table-column label="操作" width="110"><template #default="{row}"><el-button :disabled="checking||saving" type="primary" size="small" @click="useExisting(row)">使用此客户</el-button></template></el-table-column></el-table>
 <template #footer><el-button :disabled="saving||checking||uncertain" @click="visible=false">取消</el-button><el-button v-if="permission.has('metal:customer:add')" type="primary" :loading="saving" :disabled="uncertain||checking" @click="save">保存</el-button></template>
 </el-dialog>
</template>
<script setup lang="ts">
import {reactive,ref,watch,onBeforeUnmount,nextTick} from 'vue'
import {ElMessage,type FormInstance,type FormRules} from 'element-plus'
import {useActionPermission} from '@/composables/useActionPermission'
import {customerSave,type CustomerForm} from '@/api/metal/customer'
import {textRules,fieldRule,applyFieldErrors} from '@/utils/input-validation'
import {userMessage} from '@/utils/user-message'

type Candidate={id:string;version?:number;factoryId?:string;name?:string;companyName?:string;address?:string;contactName?:string;contactPhone?:string;department?:string|null}
const props=defineProps<{factoryId:string;factoryName:string;contextKey:number;isContextCurrent:()=>boolean;findCustomers:(factory:string,current:()=>boolean)=>Promise<{data:Record<string,any>[]}>}>()
const emit=defineEmits<{created:[{id:string;factoryId:string;contextKey:number}];blocked:[boolean]}>()
const permission=useActionPermission(),visible=ref(false),saving=ref(false),uncertain=ref(false),saveError=ref(''),formRef=ref<FormInstance>()
const fields=[{key:'name',label:'客户名称',max:100},{key:'companyName',label:'客户公司名称',max:100},{key:'address',label:'客户地址',max:500},{key:'contactName',label:'联络人名称',max:100},{key:'contactPhone',label:'联络人电话',max:11},{key:'department',label:'部门',max:100}] as const
const blank=():CustomerForm=>({name:'',companyName:'',address:'',contactName:'',contactPhone:'',department:null})
const form=reactive<CustomerForm>(blank())
const rules:FormRules=Object.fromEntries(fields.map(f=>[f.key,textRules(f.label,f.max,f.key!=='department')]))
rules.contactPhone=[...textRules('联络人电话',11,true),fieldRule(v=>/^1[3-9]\d{9}$/.test(String(v??'').trim())?'':'联络人电话须为11位大陆手机号')]
const checking=ref(false),matches=ref<Candidate[]>([]);let submitted:CustomerForm|undefined,checkedRetryBody:string|undefined
let epoch=0,disposed=false
function invalidate(){epoch++;visible.value=false;saving.value=false;checking.value=false;uncertain.value=false;matches.value=[];submitted=undefined;checkedRetryBody=undefined}
async function open(){
 if(saving.value||!props.factoryId||!permission.has('metal:customer:add')||!props.isContextCurrent())return
 epoch++;uncertain.value=false;checking.value=false;matches.value=[];submitted=undefined;checkedRetryBody=undefined;Object.assign(form,blank(),{factoryId:props.factoryId});saveError.value='';visible.value=true
 await nextTick();formRef.value?.clearValidate()
}
async function save(){
 if(uncertain.value||checking.value||saving.value||!visible.value||!permission.has('metal:customer:add')||!props.isContextCurrent())return
 const round=epoch,factory=props.factoryId,context=props.contextKey
 const current=()=>!disposed&&visible.value&&round===epoch&&factory===props.factoryId&&context===props.contextKey&&permission.has('metal:customer:add')&&props.isContextCurrent()
 saving.value=true;saveError.value=''
 try{
  for(const f of fields){const value=String(form[f.key]??'').trim();if(f.key==='department')form.department=value||null;else form[f.key]=value}
  if(!await formRef.value?.validate().catch(()=>false)||!current())return
  submitted={...form,factoryId:factory}
  const retryAfterEmptyLookup=checkedRetryBody===JSON.stringify(submitted);checkedRetryBody=undefined
  const response:any=await customerSave(submitted,current,{retryAfterEmptyLookup})
  if(!current())return
  if(!/^[1-9]\d*$/.test(String(response.id??''))){uncertain.value=true;throw Error('客户创建结果需核对')}
  complete(String(response.id),'客户创建成功')
 }catch(error:any){if(current()){uncertain.value=uncertain.value||!!error?.isAxiosError&&(!error.response||error.response.status>=500);saveError.value=uncertain.value?'创建结果待确认，请先核对客户列表，避免重复创建。':userMessage(error?.msg??error?.message,'客户创建失败，请核对后重试');applyFieldErrors(formRef.value,error);if(uncertain.value)console.warn('客户创建请求未确认',{time:new Date().toISOString(),url:'/metal/customer',kind:String(error?.code??'INVALID_RESPONSE'),status:error?.response?.status??null,traceId:error?.response?.headers?.['x-trace-id']??null})}}
 finally{if(!disposed&&round===epoch)saving.value=false}
}
function complete(id:string,message:string){emit('created',{id,factoryId:props.factoryId,contextKey:props.contextKey});uncertain.value=false;matches.value=[];submitted=undefined;checkedRetryBody=undefined;visible.value=false;ElMessage.success(message)}
const normalized=(v:unknown)=>String(v??'').trim()
async function reconcile(){
 if(checking.value||saving.value||!uncertain.value||!submitted||!visible.value||!permission.has('metal:customer:add')||!props.isContextCurrent())return
 const round=epoch,factory=props.factoryId,context=props.contextKey,draft=submitted
 const current=()=>!disposed&&visible.value&&round===epoch&&factory===props.factoryId&&context===props.contextKey&&permission.has('metal:customer:add')&&props.isContextCurrent()
 checking.value=true;matches.value=[]
 try{
  const result=await props.findCustomers(factory,current);if(!current())return
  const candidates=result.data.filter(c=>(c.factoryId==null||String(c.factoryId)===factory)&&/^[1-9]\d*$/.test(String(c.id))&&normalized(c.name)===draft.name&&normalized(c.companyName)===draft.companyName).map(c=>c as Candidate)
  const exact=candidates.filter(c=>fields.every(f=>normalized(c[f.key])===normalized(draft[f.key])))
  if(exact.length===1){complete(exact[0].id,'已核对客户，可继续填写入库或工单');return}
  if(candidates.length){matches.value=candidates;saveError.value='找到同名同公司客户，但资料不完全一致，请核对后选择使用。';return}
  checkedRetryBody=JSON.stringify(draft);uncertain.value=false;submitted=undefined;saveError.value='未查到匹配客户，输入已保留，请核对后再次保存。'
 }catch{if(current())saveError.value='核对失败，创建结果仍待确认，请稍后重试核对。'}
 finally{if(!disposed&&round===epoch)checking.value=false}
}
function useExisting(row:Candidate){if(checking.value||saving.value||!uncertain.value||!visible.value||!matches.value.includes(row)||!permission.has('metal:customer:add')||!props.isContextCurrent())return;complete(row.id,'已选择现有客户')}
watch([visible,saving,checking,uncertain],()=>emit('blocked',visible.value||saving.value||checking.value||uncertain.value),{flush:'sync'})
watch(()=>[props.factoryId,props.contextKey],invalidate,{flush:'sync'})
permission.onChange(invalidate)
onBeforeUnmount(()=>{disposed=true;epoch++;emit('blocked',false)})
</script>
<style scoped>
.customer-create-fields{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:0 20px}
.customer-create-fields>.el-form-item{min-width:0;margin-bottom:16px}
.customer-wide{grid-column:1/-1}
.customer-create-fields :deep(.el-form-item__error){position:static;line-height:1.5}
.customer-match-table{margin-top:12px;width:100%}.customer-create-error{color:var(--el-color-danger);overflow-wrap:anywhere}
@media(max-width:600px){.customer-create-fields{grid-template-columns:minmax(0,1fr)}}
</style>
