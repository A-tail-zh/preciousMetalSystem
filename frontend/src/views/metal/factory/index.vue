<template>
  <section class="factory-page">
    <div class="factory-toolbar">
<BusinessFilters kind="factory" @apply="applyFilters"><template #actions>
<el-button v-if="canAdd"  class="add-button list-action list-action--create" @click="openAdd">添加</el-button>
      <el-button v-if="actionPermission.hasAny(['metal:factory:edit'])"  class="edit-button list-action list-action--edit" :disabled="selected.length !== 1" @click="openEdit(selected[0])">编辑</el-button>
      <el-button v-if="canRemove"  class="delete-button list-action list-action--delete" :disabled="!selected.length || deleting" @click="remove(selected)">删除</el-button>
</template></BusinessFilters>
    </div>
    <div v-if="editLoading" role="status">正在加载编辑资料… <el-button link @click="cancelEdit">取消加载</el-button></div>
    <div v-if="editError" role="alert">编辑资料加载失败 <el-button link @click="retryEdit()">重试</el-button></div>
    <el-table ref="tableRef" v-loading="loading" :data="rows" row-key="factoryId" border class="factory-table" @selection-change="selected = $event">
      <el-table-column v-if="showActions" type="selection" width="44" align="center" />
      <el-table-column label="工厂ID" width="64" align="center">
        <template #default="{$index}">{{ (query.pageNum - 1) * query.pageSize + $index + 1 }}</template>
      </el-table-column>
      <el-table-column label="工厂名称" prop="factoryName" min-width="150" align="left" />
      <el-table-column label="占地面积（万㎡）" min-width="150" align="center">
        <template #default="{row}">{{decimalText( displayArea(row.area) )}}</template>
      </el-table-column>
      <el-table-column label="产线名称" min-width="200" align="left">
        <template #default="{row}"><span v-if="!row.productionLines?.length">暂无产线</span><div v-for="(line,index) in row.productionLines" :key="line.productionLineId">{{ index + 1 }}. {{ line.productionLineName }}</div></template>
      </el-table-column>
      <el-table-column label="详细地址" prop="address" min-width="150" align="center" />
      <el-table-column label="工厂简介" prop="introduction" min-width="180" align="center" />
      <el-table-column label="更新时间" prop="updateTime" :formatter="dateTimeCell" width="168" align="center" />
      <el-table-column v-if="showActions" label="操作" width="140" :fixed="desktopActions?'right':false" align="center">
        <template #default="{row}">
          <el-button v-if="actionPermission.hasAny(['metal:factory:edit'])"  class="edit-button row-button list-action list-action--edit list-action--row" @click="openEdit(row)">编辑</el-button>
          <el-button v-if="canRemove"  class="delete-button row-button list-action list-action--delete list-action--row" :disabled="deleting" @click="remove([row])">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div v-if="loadFailed||refreshFailed" class="load-error">工厂列表更新失败，当前显示可能过期 <el-button link type="primary" @click="load">重试</el-button></div>
    <div class="factory-pagination">
      <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :page-sizes="[16,32,64]" :total="total"
        layout="total, sizes, prev, pager, next, jumper" @size-change="changeSize" @current-change="load" />
    </div>
    <el-dialog :key="sessionKey" class="base-info-form-dialog" v-model="dialogOpen" :title="form.factoryId ? '编辑工厂' : '添加工厂'" width="min(760px, 94vw)" :close-on-click-modal="false" :close-on-press-escape="!saving&&!uncertain" :show-close="!saving&&!uncertain" >
      <el-form ref="formRef" :model="form" :rules="rules" scroll-to-error label-width="160px" label-position="left" :disabled="saving||needsReload||uncertain">
        <el-form-item label="工厂名称" prop="factoryName"><el-input v-model="form.factoryName" maxlength="100" /></el-form-item>
        <el-form-item label="占地面积（万㎡）" prop="area"><BusinessDecimalInput v-model="form.area" :integer="10" :fraction="4" inputmode="decimal" placeholder="请输入大于0的面积，保留两位小数" /></el-form-item>
        <el-form-item label="详细地址" prop="address"><el-input v-model="form.address" type="textarea" :rows="2" maxlength="500" /></el-form-item>
        <el-form-item label="工厂简介" prop="introduction"><el-input v-model="form.introduction" type="textarea" :rows="4" maxlength="5000" show-word-limit /></el-form-item>
        <el-form-item v-if="updateTime" label="更新时间"><el-input :model-value="displayDateTime(updateTime)" disabled /></el-form-item>
      </el-form>
      <el-alert v-if="needsReload" title="资料已变化，请关闭后重新加载并核对，当前输入已保留" type="warning" :closable="false"/><el-alert v-if="uncertain" title="提交结果待确认，输入已保留，请先核实结果避免重复提交" type="warning" :closable="false"/><el-button v-if="uncertain" :disabled="saving" @click="acknowledgeUnknown">已核实结果，关闭草稿</el-button>
      <template #footer>
        <el-button :disabled="saving||uncertain" @click="dialogOpen = false">取消</el-button>
        <el-button v-if="form.factoryId?actionPermission.has('metal:factory:edit'):canAdd" type="primary" :loading="saving" :disabled="needsReload||uncertain" @click="submit">确定</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts" name="Factory">
import BusinessDecimalInput from '@/components/BusinessDecimalInput.vue'

import {decimalText,decimalColumn,businessValue} from '@/utils/business-decimal'

import {useMediaQuery} from '@vueuse/core'
import {applyFieldErrors,decimalError} from '@/utils/input-validation'
import { useActionPermission } from '@/composables/useActionPermission'
import {useInventoryRefresh,type InventoryRefreshContext} from '@/composables/useInventoryRefresh'
import {getToken} from '@/utils/auth'
import {onBeforeRouteLeave,onBeforeRouteUpdate} from 'vue-router'
const actionPermission = useActionPermission()
const desktopActions=useMediaQuery('(min-width:1101px)')
import {displayDateTime,dateTimeCell} from '@/utils/display-date-time'
import { computed, reactive, ref, nextTick, onMounted, watch, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules, TableInstance } from 'element-plus'
import { listFactories, getFactory, saveFactory, deleteFactories } from '@/api/metal/factory'
import type { Factory, FactoryForm } from '@/api/metal/factory'

import BusinessFilters from '@/components/BusinessFilters/index.vue'
import useUserStore from '@/store/modules/user'
const user=useUserStore(),canAdd=computed(()=>actionPermission.isAdmin.value&&actionPermission.has('metal:factory:add')),canRemove=computed(()=>actionPermission.isAdmin.value&&actionPermission.has('metal:factory:remove')),showActions=computed(()=>actionPermission.has('metal:factory:edit')||canRemove.value)
const sessionCurrent=()=>user.sessionReady&&!!user.token&&user.token===getToken()
const identity=()=>JSON.stringify([user.sessionReady,user.token,String(user.id),[...user.permissions].sort()])
const canRead=()=>sessionCurrent()&&actionPermission.has('metal:factory:list')
const rows = ref<Factory[]>([]), selected = ref<Factory[]>([])
const total = ref(0), loading = ref(false), loadFailed = ref(false), dialogOpen = ref(false), saving = ref(false), deleting = ref(false)
const updateTime = ref(''), tableRef = ref<TableInstance>(), formRef = ref<FormInstance>()
const appliedFilters=ref<Record<string,string>>({})
function applyFilters(filters:Record<string,string>){appliedFilters.value=filters;query.pageNum=1;selected.value=[];tableRef.value?.clearSelection();load()}
const query = reactive({pageNum: 1, pageSize: 16})
const blank = (): FactoryForm => ({factoryName: '', area: '', address: '', introduction: ''})
const form = reactive<FactoryForm>(blank())
const rules: FormRules = {
  factoryName: [{required: true, whitespace: true, message: '请输入工厂名称', trigger: 'blur'}, {max: 100, message: '工厂名称不能超过100个字符', trigger: 'blur'}],
  area: [{required:true,message:'请输入占地面积',trigger:'blur'},{validator: (_rule, value, callback) => {
    const s = String(value)
    if (!/^(0|[1-9]\d{0,9})(\.\d{1,4})?$/.test(s) || !/[1-9]/.test(s)) callback(new Error('面积须大于0，最多10位整数、4位小数'))
    else callback()
  }, trigger: 'blur'}],
  address: [{required: true, whitespace: true, message: '请输入详细地址', trigger: 'blur'}, {max: 500, message: '地址不能超过500个字符', trigger: 'blur'}],
  introduction: [{max: 5000, message: '简介不能超过5000个字符', trigger: 'blur'}]
}
let loadSequence=0,operationEpoch=0,manualPending=false
async function load(){if(loading.value){manualPending=true;return false}return readList(false)}
async function readList(background:boolean,refresh?:InventoryRefreshContext) {
  if(!canRead()){resetAccess();return false}if(loading.value)return false
  const sequence=++loadSequence,account=identity(),params={...query,...appliedFilters.value},ids=selected.value.map(row=>row.factoryId),valid=()=>!disposed&&sequence===loadSequence&&account===identity()&&canRead(),current=()=>valid()&&!manualPending&&JSON.stringify(params)===JSON.stringify({...query,...appliedFilters.value})&&(!refresh||refresh.isCurrent()&&!saving.value&&!deleting.value)
  loading.value = true
  try {
    const response=await listFactories(params,current,background)
    if(!current())return false
    rows.value = response.rows; total.value = response.total; loadFailed.value = false
    selected.value = []; tableRef.value?.clearSelection()
    if(background){await nextTick();if(!current())return false;for(const row of response.rows)if(ids.includes(row.factoryId))tableRef.value?.toggleRowSelection(row,true)}return true
  } catch { if(current())loadFailed.value=true;return false }
  finally { if(valid()){loading.value=false;if(manualPending){manualPending=false;void load()}} }
}
function displayArea(value: string) { return value.replace(/(\.\d*?[1-9])0+$|\.0+$/, '$1') }
function resetForm() {
  delete form.factoryId; delete form.version; Object.assign(form, blank()); updateTime.value = ''; formRef.value?.clearValidate()
}
const editLoading=ref(false),editError=ref(false)
let editEpoch=0,disposed=false,retryEdit:()=>void=()=>{},openedId='',openedVersion:number|undefined
const needsReload=ref(false),uncertain=ref(false),sessionKey=ref(0)
function resetAccess(){sessionKey.value++;++loadSequence;++editEpoch;++operationEpoch;manualPending=false;dialogOpen.value=false;editLoading.value=false;editError.value=false;saving.value=false;deleting.value=false;loading.value=false;loadFailed.value=false;rows.value=[];selected.value=[];total.value=0;tableRef.value?.clearSelection();appliedFilters.value={};query.pageNum=1;needsReload.value=false;uncertain.value=false;resetForm()}
async function probeEditor(refresh:InventoryRefreshContext){if(!openedId)return;const round=editEpoch,account=identity(),id=openedId,current=()=>refresh.isCurrent()&&!disposed&&round===editEpoch&&account===identity()&&sessionCurrent()&&dialogOpen.value&&!saving.value&&!uncertain.value;const result=await getFactory(id,!actionPermission.has('metal:factory:query'),current,true);if(current()&&result.data.version!==openedVersion)needsReload.value=true}
const {failed:refreshFailed,invalidate}=useInventoryRefresh(refresh=>readList(true,refresh),dialogOpen,{allowed:canRead,reset:resetAccess},{probe:probeEditor,paused:computed(()=>loading.value||saving.value||deleting.value||editLoading.value||uncertain.value)})
function cancelEdit(){if(saving.value||uncertain.value)return;++editEpoch;editLoading.value=false;editError.value=false;dialogOpen.value=false}
watch(dialogOpen,open=>{if(!open&&!saving.value){++editEpoch;editLoading.value=false}},{flush:'sync'})
onBeforeUnmount(()=>{disposed=true;++editEpoch;++loadSequence;++operationEpoch})
async function openEditor(row?:Factory){
 if(saving.value||uncertain.value||disposed||!sessionCurrent()||!(row?actionPermission.has('metal:factory:edit'):canAdd.value))return;dialogOpen.value=false;const epoch=++editEpoch,account=identity();editLoading.value=true;editError.value=false;needsReload.value=false;retryEdit=()=>{void openEditor(row)}
 const valid=()=>!disposed&&epoch===editEpoch&&account===identity()&&sessionCurrent()&&(row?actionPermission.has('metal:factory:edit'):canAdd.value)
 try{const detail=row?await getFactory(row.factoryId,!actionPermission.has('metal:factory:query'),valid):{data:blank()};if(!valid())return;if(row&&detail.data.factoryId!==row.factoryId)throw Error('工厂资料不匹配');resetForm();const {factoryId,factoryName,area,address,introduction,version}=detail.data;Object.assign(form,{factoryId,factoryName,area,address,introduction:introduction??'',version});openedId=factoryId??'';openedVersion=version;updateTime.value=row?(detail.data as Factory).updateTime:'';dialogOpen.value=true}catch{if(valid()){resetForm();editError.value=true}}finally{if(valid())editLoading.value=false}
}
async function openAdd(){await openEditor()}
async function openEdit(row:Factory){await openEditor(row)}
async function submit() {
  if (saving.value||needsReload.value||uncertain.value||editLoading.value||!dialogOpen.value||!(form.factoryId?actionPermission.has('metal:factory:edit'):canAdd.value))return
  const epoch=editEpoch,account=identity(),current=()=>!disposed&&epoch===editEpoch&&account===identity()&&sessionCurrent()&&dialogOpen.value&&(openedId?actionPermission.has('metal:factory:edit'):canAdd.value)
  saving.value = true
  try {
    if(!await formRef.value?.validate().catch(()=>false)||!current()||needsReload.value||String(form.factoryId??'')!==openedId)return
    const isNew=!openedId
    await saveFactory({factoryId:form.factoryId,version:openedVersion,factoryName:form.factoryName.trim(),area:form.area.trim(),address:form.address.trim(),introduction:form.introduction},current)
    if(!current())return
    ElMessage.success(isNew ? '添加成功' : '修改成功'); dialogOpen.value = false
    if (isNew) query.pageNum = Math.max(1, Math.ceil((total.value + 1) / query.pageSize))
    invalidate()
  } catch(error:any) { if(current()){uncertain.value=!!error?.isAxiosError&&(!error.response||error.response.status>=500);if(/版本|重新加载|刷新/.test(error?.message??''))needsReload.value=true;applyFieldErrors(formRef.value,error)} }
  finally { if(!disposed&&epoch===editEpoch&&account===identity())saving.value=false }
}
async function remove(targets: Factory[]) {
  if(!targets.length||deleting.value||saving.value||uncertain.value||!sessionCurrent()||!canRemove.value)return
  const ids=targets.map(f=>f.factoryId),round=++operationEpoch,account=identity(),current=()=>!disposed&&round===operationEpoch&&account===identity()&&sessionCurrent()&&canRemove.value
  deleting.value=true
  try { await ElMessageBox.confirm(targets.length === 1 ? `确认删除工厂“${targets[0].factoryName}”？` : `确认删除选中的${targets.length}个工厂？`, '删除确认', {type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消'}) }
  catch { if(current())deleting.value=false;return }
  if(!current())return
  try {
    await deleteFactories(ids,current);if(!current())return;ElMessage.success('删除成功')
    query.pageNum = Math.min(query.pageNum, Math.max(1, Math.ceil((total.value - ids.length) / query.pageSize)))
    await load()
  } catch { /* failed batch leaves all rows intact */ }
  finally { if(current())deleting.value=false }
}
// shortcut: factory creation has no request-ID recovery, allow retry when its endpoint supports it.
function acknowledgeUnknown(){if(saving.value)return;uncertain.value=false;dialogOpen.value=false;invalidate()}
onBeforeRouteLeave(()=>{if(saving.value||uncertain.value)return false});onBeforeRouteUpdate(()=>{if(saving.value||uncertain.value)return false})
function changeSize() { query.pageNum = 1; load() }
onMounted(load)
</script>

<style scoped>
.factory-page {min-width:0;max-width:100%;box-sizing:border-box; background:var(--business-canvas); min-height:calc(var(--design-height,100vh) - var(--business-header-height,56px)); padding:16px 16px 24px; display:flex; flex-direction:column; }
.factory-toolbar{min-width:0;max-width:100%;margin-bottom:16px;flex-shrink:0}
.factory-toolbar>*{flex-shrink:0}
.factory-toolbar>.el-button { width:89px; height:35px; padding:0; margin:0; font-size:14px; }
.factory-table { width:100%; font-size:14px; --el-table-header-bg-color:#f4f4f4; --el-table-border-color:#e8ebf0; --el-table-header-text-color:#303133; }
.factory-table :deep(th.el-table__cell) { height:44px; padding:4px 0; font-weight:600; }
.factory-table :deep(.cell) { font-size:14px; white-space:pre-wrap; overflow-wrap:anywhere; line-height:22px; padding:8px 10px; }
.factory-table :deep(td.el-table__cell) { padding:0; }
.factory-table :deep(.row-button) { height:25px; font-size:13px; padding:0 12px; margin:0 2px; border-radius:2px; }
.factory-pagination { margin-top:16px; padding-top:0; display:flex; justify-content:center; }
.factory-pagination :deep(.el-pagination) { font-size:14px; flex-wrap:wrap; justify-content:center; }
.factory-page :deep(.el-dialog) { --el-font-size-base:15px; }
.factory-page :deep(.el-dialog .el-select-dropdown__wrap) { max-height:180px; }
.factory-toolbar>.el-button:disabled { color:#fff; background:var(--el-button-bg-color); border-color:var(--el-button-border-color); opacity:.6; }
.factory-pagination :deep(.el-pagination) { color:#757575; --el-text-color-regular:#757575; --el-text-color-primary:#757575; --el-fill-color-blank:transparent; --el-disabled-bg-color:transparent; }
.factory-pagination :deep(.el-pagination button), .factory-pagination :deep(.el-pager li) { background:transparent; color:#757575; }
.factory-pagination :deep(.el-pager li.is-active) { color:#409eff; }
.factory-pagination :deep(.el-select__wrapper), .factory-pagination :deep(.el-input__wrapper) { background:transparent; border-radius:0; }
.load-error { margin:16px; text-align:center; color:#c45656; }
</style>

<style>
.base-info-form-dialog .el-form-item__label{position:relative;justify-content:flex-start;text-align:left;white-space:nowrap;padding-left:12px}
.base-info-form-dialog .el-form-item.is-required:not(.is-no-asterisk)>.el-form-item__label:before{position:absolute;left:0;margin-right:0}
</style>
