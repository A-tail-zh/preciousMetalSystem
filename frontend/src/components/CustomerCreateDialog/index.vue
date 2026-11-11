<template>
  <el-dialog :model-value="modelValue" title="添加客户" width="min(760px,94vw)" class="inline-customer-dialog" append-to-body :close-on-click-modal="false" :before-close="close" :close-on-press-escape="!busy && !uncertain" :show-close="!busy && !uncertain" destroy-on-close>
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" :disabled="busy || uncertain" class="customer-fields" scroll-to-error>
      <el-form-item label="所属工厂" prop="factoryId" class="wide"><el-input :model-value="factoryName" readonly aria-label="所属工厂" /></el-form-item>
      <el-form-item v-for="field in fields" :key="field.key" :prop="field.key" :label="field.label" :class="{ wide: field.key === 'address' }">
        <el-input v-model="form[field.key]" :aria-label="field.label" :maxlength="field.max" :type="field.key === 'address' ? 'textarea' : 'text'" :inputmode="field.key === 'contactPhone' ? 'tel' : undefined" :rows="2" />
      </el-form-item>
    </el-form>
    <el-alert v-if="error" :title="error" type="warning" :closable="false" />
    <template v-if="uncertain">
      <el-button :loading="busy" @click="checkCustomers">查询本厂客户</el-button>
      <el-table v-if="matches.length" :data="matches" max-height="220">
        <el-table-column prop="name" label="客户名称" /><el-table-column prop="companyName" label="公司" /><el-table-column prop="address" label="地址" /><el-table-column prop="contactName" label="联络人" /><el-table-column prop="contactPhone" label="电话" />
        <el-table-column label="操作"><template #default="{ row }"><el-button link type="primary" :disabled="busy" @click="choose(row)">使用此客户</el-button></template></el-table-column>
      </el-table>
      <el-button :disabled="busy" @click="acknowledge">已核实结果，关闭草稿</el-button>
    </template>
    <template #footer><el-button :disabled="busy || uncertain" @click="close()">取消</el-button><el-button type="primary" :loading="busy" :disabled="uncertain || !has('metal:customer:add')" @click="save">保存</el-button></template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { customerSave, type CustomerForm } from '@/api/metal/customer'
import { customerFields as fields, customerRules } from '@/utils/customer-form'
import { applyFieldErrors } from '@/utils/input-validation'
import { useActionPermission } from '@/composables/useActionPermission'
const props = defineProps<{ modelValue: boolean; factoryId: string; factoryName: string; isContextCurrent: () => boolean; findCustomers: (current: () => boolean) => Promise<Record<string, any>[]> }>()
const emit = defineEmits<{ 'update:modelValue': [boolean]; created: [{ id: string; factoryId: string }] }>()
const { has, onChange } = useActionPermission()
const blank = (): CustomerForm => ({ factoryId: props.factoryId, name: '', companyName: '', address: '', contactName: '', contactPhone: '', department: null })
const form = ref(blank()), formRef = ref<FormInstance>(), rules = customerRules()
const busy = ref(false), uncertain = ref(false), error = ref(''), matches = ref<Record<string, any>[]>([])
let epoch = 0, initial = '', disposed = false
function reset() { epoch++; busy.value = false; uncertain.value = false; error.value = ''; matches.value = []; form.value = blank(); initial = JSON.stringify(form.value) }
watch(() => props.modelValue, open => { if (open) reset(); else epoch++ }, { flush: 'sync' })
watch(() => props.factoryId, () => { reset(); emit('update:modelValue', false) }, { flush: 'sync' })
onChange(() => { reset(); emit('update:modelValue', false) })
onBeforeUnmount(() => { disposed = true; epoch++ })
function context(round: number, factory: string) { return !disposed && round === epoch && props.modelValue && factory === props.factoryId && has('metal:customer:add') && props.isContextCurrent() }
async function close(done?: () => void) {
  if (busy.value || uncertain.value) return
  if (JSON.stringify(form.value) !== initial) {
    try { await ElMessageBox.confirm('放弃未保存的客户资料？', '确认关闭', { confirmButtonText: '放弃', cancelButtonText: '继续填写' }) } catch { return }
  }
  emit('update:modelValue', false); done?.()
}
function complete(id: string) { const factoryId = props.factoryId; emit('update:modelValue', false); emit('created', { id, factoryId }) }
async function save() {
  if (busy.value || uncertain.value || !has('metal:customer:add') || !props.isContextCurrent()) return
  const round = epoch, factory = props.factoryId, current = () => context(round, factory)
  busy.value = true; error.value = ''
  try {
    for (const field of fields) { const text = String(form.value[field.key] ?? '').trim(); if (field.key === 'department') form.value.department = text || null; else form.value[field.key] = text }
    if (!await formRef.value?.validate().catch(() => false) || !current()) return
    const result: any = await customerSave({ ...form.value, factoryId: factory }, current)
    if (!current()) return
    if (!/^[1-9]\d*$/.test(String(result.id))) { uncertain.value = true; error.value = '客户创建结果待核实，请查询本厂客户后确认'; return }
    ElMessage.success('客户已创建'); complete(String(result.id))
  } catch (e: any) {
    if (!current()) return
    const code = Number(e.response?.data?.code ?? e.response?.status ?? e.code)
    uncertain.value = !e.fieldErrors?.length && (e.isAxiosError && (!e.response || e.response.status >= 500) || code === 500)
    error.value = uncertain.value ? '客户创建结果待核实，暂勿重复创建；请查询本厂客户后确认' : e.message || '客户保存失败，请检查输入'
    applyFieldErrors(formRef.value, e)
  } finally { if (round === epoch) busy.value = false }
}
async function checkCustomers() {
  if (busy.value || !uncertain.value) return
  const round = epoch, factory = props.factoryId, current = () => context(round, factory)
  busy.value = true
  try {
    const rows = await props.findCustomers(current)
    if (!current()) return
    matches.value = rows.filter(r => String(r.factoryId) === factory && r.name === form.value.name && r.companyName === form.value.companyName)
    error.value = matches.value.length ? '请核对完整资料后选择使用；同名客户不代表此次创建已成功' : '尚未查到匹配客户，结果仍待核实，请稍后再查询，勿重复创建'
  } catch { if (current()) error.value = '客户查询失败，结果仍待核实，请稍后重试' }
  finally { if (round === epoch) busy.value = false }
}
async function choose(row: Record<string, any>) {
  const round = epoch, factory = props.factoryId
  try { await ElMessageBox.confirm('已核对资料，使用这个客户继续填写工单？', '选择客户', { confirmButtonText: '使用', cancelButtonText: '取消' }) } catch { return }
  if (context(round, factory) && matches.value.includes(row)) complete(String(row.id))
}
async function acknowledge() {
  const round = epoch, factory = props.factoryId
  try { await ElMessageBox.confirm('请确认已在客户管理或通过管理员核实创建结果。关闭草稿不会撤销已创建的客户。', '核实创建结果', { confirmButtonText: '已核实，关闭', cancelButtonText: '继续核实' }) } catch { return }
  if (context(round, factory)) emit('update:modelValue', false)
}
</script>

<style scoped>
.customer-fields { display: grid; grid-template-columns: minmax(0,1fr) minmax(0,1fr); gap: 0 20px; }
.wide { grid-column: 1/-1; }
:deep(.el-form-item) { padding-left: 12px; min-width: 0; }
:deep(.el-form-item__label) { position: relative; text-align: left; }
:deep(.el-form-item.is-required .el-form-item__label::before) { position: absolute; left: -12px; margin: 0; }
:deep(.el-form-item__error) { position: static; }
.el-alert { margin-bottom: 12px; }
@media(max-width:600px) { .customer-fields { grid-template-columns: minmax(0,1fr); } }
</style>
