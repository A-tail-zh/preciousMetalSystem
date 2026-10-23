<template>
  <section class="factory-page">
    <div class="factory-toolbar">
<BusinessFilters kind="factory" @apply="applyFilters"><template #actions>
<el-button v-if="canAdd"  class="add-button" @click="openAdd">添加</el-button>
      <el-button v-if="actionPermission.hasAny(['metal:factory:edit'])"  class="edit-button" :disabled="selected.length !== 1" @click="openEdit(selected[0])">编辑</el-button>
      <el-button v-if="canRemove"  class="delete-button" :disabled="!selected.length || deleting" @click="remove(selected)">删除</el-button>
</template></BusinessFilters>
    </div>
    <div v-if="editLoading" role="status">正在加载编辑资料… <el-button link @click="cancelEdit">取消加载</el-button></div>
    <div v-if="editError" role="alert">编辑资料加载失败 <el-button link @click="retryEdit()">重试</el-button></div>
    <el-table ref="tableRef" v-loading="loading" :data="rows" border class="factory-table" @selection-change="selected = $event">
      <el-table-column v-if="showActions" type="selection" width="54" align="center" />
      <el-table-column label="工厂ID" min-width="98" align="center">
        <template #default="{$index}">{{ (query.pageNum - 1) * query.pageSize + $index + 1 }}</template>
      </el-table-column>
      <el-table-column label="工厂名称" prop="factoryName" min-width="98" align="center" />
      <el-table-column label="占地面积（万㎡）" min-width="135" align="center">
        <template #default="{row}">{{ displayArea(row.area) }}</template>
      </el-table-column>
      <el-table-column label="产线名称" min-width="98" align="center">
        <template #default="{row}"><span v-if="!row.productionLines?.length">暂无产线</span><div v-for="(line,index) in row.productionLines" :key="line.productionLineId">{{ index + 1 }}. {{ line.productionLineName }}</div></template>
      </el-table-column>
      <el-table-column label="详细地址" prop="address" min-width="113" align="center" />
      <el-table-column label="工厂简介" prop="introduction" min-width="230" align="center" />
      <el-table-column label="更新时间" prop="updateTime" :formatter="dateTimeCell" width="168" align="center" />
      <el-table-column v-if="showActions" label="操作" width="163" align="center">
        <template #default="{row}">
          <el-button v-if="actionPermission.hasAny(['metal:factory:edit'])"  class="edit-button row-button" @click="openEdit(row)">编辑</el-button>
          <el-button v-if="canRemove"  class="delete-button row-button" :disabled="deleting" @click="remove([row])">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div v-if="loadFailed" class="load-error">工厂列表加载失败 <el-button link type="primary" @click="load">重试</el-button></div>
    <div class="factory-pagination">
      <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :page-sizes="[16,32,64]" :total="total"
        layout="total, sizes, prev, pager, next, jumper" @size-change="changeSize" @current-change="load" />
    </div>
    <el-dialog class="base-info-form-dialog" v-model="dialogOpen" :title="form.factoryId ? '编辑工厂' : '添加工厂'" width="min(760px, 94vw)" :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving" >
      <el-form ref="formRef" :model="form" :rules="rules" scroll-to-error label-width="160px" label-position="left" :disabled="saving">
        <el-form-item label="工厂名称" prop="factoryName"><el-input v-model="form.factoryName" maxlength="100" /></el-form-item>
        <el-form-item label="占地面积（万㎡）" prop="area"><el-input v-model="form.area" inputmode="decimal" placeholder="请输入大于0的面积，最多4位小数" /></el-form-item>
        <el-form-item label="详细地址" prop="address"><el-input v-model="form.address" type="textarea" :rows="2" maxlength="500" /></el-form-item>
        <el-form-item label="工厂简介" prop="introduction"><el-input v-model="form.introduction" type="textarea" :rows="4" maxlength="5000" show-word-limit /></el-form-item>
        <el-form-item v-if="updateTime" label="更新时间"><el-input :model-value="displayDateTime(updateTime)" disabled /></el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="saving" @click="dialogOpen = false">取消</el-button>
        <el-button v-if="form.factoryId?actionPermission.has('metal:factory:edit'):canAdd" type="primary" :loading="saving" @click="submit">确定</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts" name="Factory">
import {applyFieldErrors,decimalError} from '@/utils/input-validation'
import { useActionPermission } from '@/composables/useActionPermission'
const actionPermission = useActionPermission()
import {displayDateTime,dateTimeCell} from '@/utils/display-date-time'
import { computed, reactive, ref, onMounted, watch, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules, TableInstance } from 'element-plus'
import { listFactories, getFactory, saveFactory, deleteFactories } from '@/api/metal/factory'
import type { Factory, FactoryForm } from '@/api/metal/factory'

import BusinessFilters from '@/components/BusinessFilters/index.vue'
import useUserStore from '@/store/modules/user'
const user=useUserStore(),canAdd=computed(()=>actionPermission.isAdmin.value&&actionPermission.has('metal:factory:add')),canRemove=computed(()=>actionPermission.isAdmin.value&&actionPermission.has('metal:factory:remove')),showActions=computed(()=>actionPermission.has('metal:factory:edit')||canRemove.value)
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
let loadSequence = 0
async function load() {
  const sequence = ++loadSequence
  loading.value = true
  try {
    const response = await listFactories({...query,...appliedFilters.value})
    if (sequence !== loadSequence) return
    rows.value = response.rows; total.value = response.total; loadFailed.value = false
    selected.value = []; tableRef.value?.clearSelection()
  } catch { if (sequence === loadSequence) loadFailed.value = true }
  finally { if (sequence === loadSequence) loading.value = false }
}
function displayArea(value: string) { return value.replace(/(\.\d*?[1-9])0+$|\.0+$/, '$1') }
function resetForm() {
  delete form.factoryId; delete form.version; Object.assign(form, blank()); updateTime.value = ''; formRef.value?.clearValidate()
}
const editLoading=ref(false),editError=ref(false)
let editEpoch=0,disposed=false,retryEdit:()=>void=()=>{}
function cancelEdit(){if(saving.value)return;++editEpoch;editLoading.value=false;editError.value=false;dialogOpen.value=false}
watch(dialogOpen,open=>{if(!open&&!saving.value){++editEpoch;editLoading.value=false}},{flush:'sync'})
actionPermission.onChange(()=>{++editEpoch;editLoading.value=false;editError.value=false;dialogOpen.value=false;resetForm()})
onBeforeUnmount(()=>{disposed=true;++editEpoch;++loadSequence})
async function openEditor(row?:Factory){
 if(saving.value||disposed||!(row?actionPermission.has('metal:factory:edit'):canAdd.value))return;dialogOpen.value=false;const epoch=++editEpoch;editLoading.value=true;editError.value=false;retryEdit=()=>{void openEditor(row)}
 const valid=()=>!disposed&&epoch===editEpoch
 try{const detail=row?await getFactory(row.factoryId,!actionPermission.has('metal:factory:query')):{data:blank()};if(!valid())return;resetForm();const {factoryId,factoryName,area,address,introduction,version}=detail.data;Object.assign(form,{factoryId,factoryName,area,address,introduction:introduction??'',version});updateTime.value=row?(detail.data as Factory).updateTime:'';dialogOpen.value=true}catch{if(valid()){resetForm();editError.value=true}}finally{if(valid())editLoading.value=false}
}
async function openAdd(){await openEditor()}
async function openEdit(row:Factory){await openEditor(row)}
async function submit() {
  if (saving.value || editLoading.value || !dialogOpen.value || !(form.factoryId?actionPermission.has('metal:factory:edit'):canAdd.value)) return
  const epoch=editEpoch
  saving.value = true
  try {
    if(!await formRef.value?.validate().catch(()=>false)||disposed||epoch!==editEpoch||!(form.factoryId?actionPermission.has('metal:factory:edit'):canAdd.value))return
    const isNew = !form.factoryId
    await saveFactory({factoryId: form.factoryId, version: form.version, factoryName: form.factoryName.trim(), area: form.area.trim(),
      address: form.address.trim(), introduction: form.introduction})
    if(disposed||epoch!==editEpoch)return
    ElMessage.success(isNew ? '添加成功' : '修改成功'); dialogOpen.value = false
    if (isNew) query.pageNum = Math.max(1, Math.ceil((total.value + 1) / query.pageSize))
    await load()
  } catch(error) { if(!disposed&&epoch===editEpoch)applyFieldErrors(formRef.value,error) }
  finally { saving.value = false }
}
async function remove(targets: Factory[]) {
  if (!targets.length || deleting.value || !canRemove.value) return
  const ids = targets.map(f => f.factoryId)
  try { await ElMessageBox.confirm(targets.length === 1 ? `确认删除工厂“${targets[0].factoryName}”？` : `确认删除选中的${targets.length}个工厂？`, '删除确认', {type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消'}) }
  catch { return }
  if(disposed||!canRemove.value)return
  deleting.value = true
  try {
    await deleteFactories(ids); ElMessage.success('删除成功')
    query.pageNum = Math.min(query.pageNum, Math.max(1, Math.ceil((total.value - ids.length) / query.pageSize)))
    await load()
  } catch { /* failed batch leaves all rows intact */ }
  finally { deleting.value = false }
}
function changeSize() { query.pageNum = 1; load() }
onMounted(load)
</script>

<style scoped>
.factory-page {min-width:0;max-width:100%;box-sizing:border-box; background:#f8fbff; min-height:calc(var(--design-height,100vh) - 82px); padding:9px 23px 24px 6px; display:flex; flex-direction:column; }
.factory-toolbar{min-width:0;max-width:100%;margin-bottom:16px;flex-shrink:0}
.factory-toolbar>*{flex-shrink:0}
.factory-toolbar>.el-button { width:89px; height:35px; padding:0; margin:0; font-size:14px; }
.add-button { --el-button-bg-color:#577bef; --el-button-border-color:#577bef; --el-button-text-color:white; --el-button-hover-bg-color:#7795ff; --el-button-hover-text-color:white; }
.edit-button { --el-button-bg-color:#ee9b58; --el-button-border-color:#ee9b58; --el-button-text-color:white; --el-button-hover-bg-color:#f8b47f; --el-button-hover-text-color:white; }
.delete-button { --el-button-bg-color:#ee6658; --el-button-border-color:#ee6658; --el-button-text-color:white; --el-button-hover-bg-color:#fc887f; --el-button-hover-text-color:white; }
.factory-table { width:100%; font-size:14px; --el-table-header-bg-color:#f4f4f4; --el-table-border-color:#e8ebf0; --el-table-header-text-color:#303133; }
.factory-table :deep(th.el-table__cell) { height:56px; font-weight:600; }
.factory-table :deep(.cell) { font-size:14px; white-space:pre-wrap; overflow-wrap:anywhere; line-height:16px; padding:10px 6px; }
.factory-table :deep(td.el-table__cell) { padding:0; }
.factory-table :deep(.row-button) { height:25px; font-size:13px; padding:0 12px; margin:0 2px; border-radius:2px; }
.factory-pagination { margin-top:auto; padding-top:30px; display:flex; justify-content:center; }
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
