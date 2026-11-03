<template>
 <section class="customer-page">
  <BusinessFilters :key="sessionEpoch" kind="customer" @apply="applyFilters"><template #actions>
   <el-button v-if="permission.has('metal:customer:add')" class="add-button list-action list-action--create" @click="openEditor()">添加</el-button>
   <el-button v-if="permission.has('metal:customer:edit')" class="edit-button list-action list-action--edit" :disabled="selected.length!==1" @click="openEditor(selected[0])">编辑</el-button>
   <el-button v-if="permission.has('metal:customer:remove')" class="delete-button list-action list-action--delete" :disabled="!selected.length||deleting" @click="remove(selected)">删除</el-button>
  </template></BusinessFilters>
  <el-table ref="table" v-loading="loading" :data="rows" row-key="id" border @selection-change="selected=$event">
   <el-table-column v-if="permission.hasAny(['metal:customer:edit','metal:customer:remove'])" type="selection" width="46"/>
   <el-table-column v-for="field in fields" :key="field.key" :prop="field.key" :label="field.label" :min-width="field.key==='address'?220:140" show-overflow-tooltip/>
   <el-table-column label="所属工厂" prop="factoryName" min-width="140"/>
   <el-table-column label="创建时间" prop="createTime" width="168" :formatter="dateTimeCell"/>
   <el-table-column v-if="permission.hasAny(['metal:customer:edit','metal:customer:remove'])" label="操作" width="140" align="center">
    <template #default="{row}">
     <el-button v-if="permission.has('metal:customer:edit')" class="edit-button row-button list-action list-action--edit list-action--row" @click="openEditor(row)">编辑</el-button>
     <el-button v-if="permission.has('metal:customer:remove')" class="delete-button row-button list-action list-action--delete list-action--row" :disabled="deleting" @click="remove([row])">删除</el-button>
    </template>
   </el-table-column>
  </el-table>
  <p v-if="loadFailed||refreshFailed" role="alert">客户列表更新失败，当前显示可能过期 <el-button link type="primary" @click="load">重试</el-button></p>
  <div class="customer-pagination"><el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :page-sizes="[16,32,64]" :total="total" layout="total, sizes, prev, pager, next, jumper" @size-change="changeSize" @current-change="load"/></div>
  <el-dialog :key="sessionEpoch" v-model="dialogOpen" class="customer-dialog" :title="form.id?'编辑客户':'添加客户'" width="min(760px,94vw)" :close-on-click-modal="false" :close-on-press-escape="!saving&&!uncertain" :show-close="!saving&&!uncertain">
   <p v-if="editError" role="alert">客户资料加载失败 <el-button link @click="openEditor(editing)">重试</el-button></p>
   <el-form ref="formRef" v-loading="editLoading" :model="form" :rules="rules" label-width="128px" label-position="top" scroll-to-error :disabled="saving||editLoading||editError||needsReload||uncertain">
    <el-form-item label="所属工厂" prop="factoryId">
     <el-select v-if="permission.isAdmin.value&&!form.id" v-model="form.factoryId" aria-label="所属工厂" filterable placeholder="请选择所属工厂" style="width:100%"><el-option v-for="factory in factories" :key="factory.id" :label="factory.name" :value="factory.id"/></el-select>
     <el-input v-else :model-value="form.factoryName||factories.find(f=>f.id===form.factoryId)?.name||''" :validate-event="false" readonly aria-label="所属工厂"/>
    </el-form-item>
    <el-form-item v-for="field in fields" :key="field.key" :prop="field.key" :label="field.label">
     <el-input v-model="form[field.key]" :aria-label="field.label" :maxlength="field.max" :type="field.key==='address'?'textarea':'text'" :rows="2" :inputmode="field.key==='contactPhone'?'tel':undefined"/>
    </el-form-item>
   </el-form>
   <el-alert v-if="needsReload" title="资料已变化，请关闭后重新加载并核对，当前输入已保留" type="warning" :closable="false"/><el-alert v-if="uncertain" title="提交结果待确认，输入已保留，请先核实结果避免重复提交" type="warning" :closable="false"/><el-button v-if="uncertain" :disabled="saving" @click="acknowledgeUnknown">已核实结果，关闭草稿</el-button><template #footer><el-button :disabled="saving||uncertain" @click="dialogOpen=false">取消</el-button><el-button type="primary" :loading="saving" :disabled="editLoading||editError||needsReload||uncertain" @click="submit">保存</el-button></template>
  </el-dialog>
 </section>
</template>

<script setup lang="ts" name="MetalCustomer">
import {computed,reactive,ref,onMounted,onBeforeUnmount,watch,nextTick} from 'vue'
import {useInventoryRefresh,type InventoryRefreshContext} from '@/composables/useInventoryRefresh'
import {getToken} from '@/utils/auth'
import useUserStore from '@/store/modules/user'
import {onBeforeRouteLeave,onBeforeRouteUpdate} from 'vue-router'
import {ElMessage,ElMessageBox,type FormInstance,type FormRules,type TableInstance} from 'element-plus'
import BusinessFilters from '@/components/BusinessFilters/index.vue'
import {useActionPermission} from '@/composables/useActionPermission'
import {applyFieldErrors,textRules,fieldRule,requiredRule} from '@/utils/input-validation'
import {dateTimeCell} from '@/utils/display-date-time'
import {customerList,customerEditContext,customerFactories,customerSave,customerRemove,type Customer,type CustomerForm} from '@/api/metal/customer'

const permission=useActionPermission(),user=useUserStore()
const sessionCurrent=()=>user.sessionReady&&!!user.token&&user.token===getToken()
const identity=()=>JSON.stringify([user.sessionReady,user.token,String(user.id),[...user.permissions].sort()])
const canRead=()=>sessionCurrent()&&permission.has('metal:customer:list')
const fields=[{key:'name',label:'客户名称',max:100},{key:'companyName',label:'客户公司名称',max:100},{key:'address',label:'客户地址',max:500},{key:'contactName',label:'联络人名称',max:100},{key:'contactPhone',label:'联络人电话',max:11},{key:'department',label:'部门',max:100}] as const
const blank=():CustomerForm=>({name:'',companyName:'',address:'',contactName:'',contactPhone:'',department:null})
const form=ref<CustomerForm>(blank()),formRef=ref<FormInstance>(),table=ref<TableInstance>()
const rows=ref<Customer[]>([]),selected=ref<Customer[]>([]),total=ref(0)
const loading=ref(false),loadFailed=ref(false),dialogOpen=ref(false),saving=ref(false),deleting=ref(false),editLoading=ref(false),editError=ref(false)
const factories=ref<{id:string;name:string}[]>([]),editing=ref<Customer>(),sessionEpoch=ref(0)
const query=reactive({pageNum:1,pageSize:16}),filters=ref<Record<string,string>>({})
const rules:FormRules=Object.fromEntries(fields.map(f=>[f.key,textRules(f.label,f.max,f.key!=='department')]))
rules.factoryId=[requiredRule('所属工厂',true,true)]
rules.contactPhone=[...textRules('联络人电话',11,true),fieldRule(value=>/^1[3-9]\d{9}$/.test(String(value??'').trim())?'':'联络人电话须为11位大陆手机号')]
let disposed=false,listEpoch=0,editEpoch=0,operationEpoch=0,manualPending=false,openedId='',openedVersion:number|undefined,openedFactories=''
const needsReload=ref(false),uncertain=ref(false)
function resetAccess(){++sessionEpoch.value;++listEpoch;++editEpoch;++operationEpoch;manualPending=false;dialogOpen.value=false;saving.value=false;deleting.value=false;editLoading.value=false;editError.value=false;loading.value=false;loadFailed.value=false;rows.value=[];selected.value=[];total.value=0;table.value?.clearSelection();form.value=blank();factories.value=[];filters.value={};query.pageNum=1;needsReload.value=false;uncertain.value=false}
function applyFilters(value:Record<string,string>){filters.value=value;query.pageNum=1;void load()}
function changeSize(){query.pageNum=1;void load()}
async function load(){if(loading.value){manualPending=true;return false}return readList(false)}
async function readList(background:boolean,refresh?:InventoryRefreshContext){
 if(!canRead()){resetAccess();return false}if(loading.value)return false
 const epoch=++listEpoch,account=identity(),params={...query,...filters.value},ids=selected.value.map(row=>row.id),valid=()=>!disposed&&epoch===listEpoch&&account===identity()&&canRead(),current=()=>valid()&&!manualPending&&JSON.stringify(params)===JSON.stringify({...query,...filters.value})&&(!refresh||refresh.isCurrent()&&!saving.value&&!deleting.value)
 loading.value=true
 try{const result=await customerList(params,current,background);if(!current())return false;rows.value=result.rows;total.value=result.total;selected.value=[];table.value?.clearSelection();if(background){await nextTick();if(!current())return false;for(const row of result.rows)if(ids.includes(row.id))table.value?.toggleRowSelection(row,true)}loadFailed.value=false;return true}
 catch{if(current())loadFailed.value=true;return false}finally{if(valid()){loading.value=false;if(manualPending){manualPending=false;void load()}}}
}
async function probeEditor(refresh:InventoryRefreshContext){const epoch=editEpoch,account=identity(),id=openedId,current=()=>refresh.isCurrent()&&!disposed&&epoch===editEpoch&&account===identity()&&sessionCurrent()&&dialogOpen.value&&!saving.value&&!uncertain.value;if(id){const result=await customerEditContext(id,current,true);if(current()&&result.data.version!==openedVersion)needsReload.value=true}else{const result=await customerFactories(current,true);if(current()&&JSON.stringify(result.data)!==openedFactories)needsReload.value=true}}
const {failed:refreshFailed,invalidate}=useInventoryRefresh(refresh=>readList(true,refresh),dialogOpen,{allowed:canRead,reset:resetAccess},{probe:probeEditor,paused:computed(()=>loading.value||saving.value||deleting.value||editLoading.value||uncertain.value)})
async function openEditor(row?:Customer){
 if(saving.value||uncertain.value||!sessionCurrent()||!permission.has(`metal:customer:${row?'edit':'add'}`))return
 const epoch=++editEpoch,account=identity(),current=()=>!disposed&&epoch===editEpoch&&account===identity()&&sessionCurrent()&&dialogOpen.value&&permission.has(`metal:customer:${row?'edit':'add'}`)
 needsReload.value=false;openedId=row?.id??'';openedVersion=undefined;openedFactories=''
 editing.value=row;form.value=blank();factories.value=[];editError.value=false;editLoading.value=true;dialogOpen.value=true
 await nextTick();formRef.value?.clearValidate()
 try{
  if(row){const result=await customerEditContext(row.id,current);if(current()){if(result.data.id!==row.id)throw Error('客户资料不匹配');openedVersion=result.data.version;form.value={id:result.data.id,version:result.data.version,factoryId:result.data.factoryId,factoryName:result.data.factoryName,...Object.fromEntries(fields.map(f=>[f.key,result.data[f.key]]))} as CustomerForm}}
  else{const result=await customerFactories(current);if(current()){factories.value=result.data;openedFactories=JSON.stringify(result.data);if(!permission.isAdmin.value)form.value.factoryId=result.data[0]?.id}}
  await nextTick();if(current())formRef.value?.clearValidate()
 }catch{if(current())editError.value=true}finally{if(current())editLoading.value=false}
}
async function submit(){
 if(saving.value||needsReload.value||uncertain.value||editLoading.value||editError.value||!dialogOpen.value)return
 const epoch=editEpoch,account=identity(),action=openedId?'edit':'add',current=()=>!disposed&&epoch===editEpoch&&account===identity()&&sessionCurrent()&&dialogOpen.value&&permission.has('metal:customer:'+action)
 if(!current())return
 saving.value=true
 try{
  for(const field of fields){const value=String(form.value[field.key]??'').trim();if(field.key==='department')form.value.department=value||null;else form.value[field.key]=value}
  if(!await formRef.value?.validate().catch(()=>false)||!current()||needsReload.value||String(form.value.id??'')!==openedId)return
  const payload:CustomerForm={...form.value};delete payload.factoryName
  if(openedId)payload.version=openedVersion
  await customerSave(payload,current)
  if(current()){ElMessage.success('客户保存成功');dialogOpen.value=false;invalidate()}
 }catch(error:any){if(current()){uncertain.value=!!error?.isAxiosError&&(!error.response||error.response.status>=500);if(/版本|重新加载|刷新/.test(error?.message??''))needsReload.value=true;applyFieldErrors(formRef.value,error)}}finally{if(!disposed&&epoch===editEpoch&&account===identity())saving.value=false}
}
async function remove(targets:Customer[]){
 if(deleting.value||saving.value||uncertain.value||!sessionCurrent()||!targets.length||!permission.has('metal:customer:remove'))return
 const round=++operationEpoch,account=identity(),ids=targets.map(row=>row.id),current=()=>!disposed&&round===operationEpoch&&account===identity()&&sessionCurrent()&&permission.has('metal:customer:remove')
 deleting.value=true
 try{
  await ElMessageBox.confirm(`确认删除选中的${ids.length}条客户记录？`,'删除确认',{type:'warning',confirmButtonText:'确定',cancelButtonText:'取消'})
  if(!current())return
  await customerRemove(ids,current)
  if(current()){ElMessage.success('客户删除成功');if(ids.length===rows.value.length&&query.pageNum>1)query.pageNum--;void load()}
 }catch{}finally{if(current())deleting.value=false}
}
watch(dialogOpen,open=>{if(!open){++editEpoch;editLoading.value=false;saving.value=false}},{flush:'sync'})
// shortcut: customer creation has no request-ID recovery, allow retry when its endpoint supports it.
function acknowledgeUnknown(){if(saving.value)return;uncertain.value=false;dialogOpen.value=false;invalidate()}
onBeforeRouteLeave(()=>{if(saving.value||uncertain.value)return false});onBeforeRouteUpdate(()=>{if(saving.value||uncertain.value)return false})
onMounted(load)
onBeforeUnmount(()=>{disposed=true;++listEpoch;++editEpoch;++operationEpoch})
</script>

<style scoped>
.customer-page{padding:16px;min-width:0}
.customer-page>.el-table{margin-top:12px;width:100%}
.customer-page :deep(.row-button){height:25px;font-size:13px;padding:0 12px;margin:0 2px;border-radius:2px}
.customer-page :deep(.filter-field .el-autocomplete){display:flex}
.customer-pagination{margin-top:16px;padding-top:0;display:flex;justify-content:center}
.customer-pagination :deep(.el-pagination){font-size:14px;flex-wrap:wrap;justify-content:center;color:#757575;--el-text-color-regular:#757575;--el-text-color-primary:#757575;--el-fill-color-blank:transparent;--el-disabled-bg-color:transparent}
.customer-pagination :deep(.el-pagination button),.customer-pagination :deep(.el-pager li){background:transparent;color:#757575}
.customer-pagination :deep(.el-pager li.is-active){color:#409eff}
.customer-pagination :deep(.el-select__wrapper),.customer-pagination :deep(.el-input__wrapper){background:transparent;border-radius:0}
.customer-page :deep(.customer-dialog .el-form-item__error){position:static;padding-top:5px;line-height:1.4}
.customer-page :deep(.customer-dialog .el-form-item){margin-bottom:16px}
.customer-page :deep(.customer-dialog .el-form-item__label){position:relative;justify-content:flex-start;text-align:left;padding-left:12px}
.customer-page :deep(.customer-dialog .el-form-item.is-required:not(.is-no-asterisk)>.el-form-item__label:before){position:absolute;left:0;margin-right:0}
@media(max-width:480px){.customer-page{padding:10px}}
</style>
