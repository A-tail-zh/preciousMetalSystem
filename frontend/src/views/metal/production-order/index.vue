<template>
  <div class="app-container production-order">
    <div class="toolbar">
      <span>所属工厂</span>
      <el-select v-model="factoryId" aria-label="所属工厂" placeholder="请选择所属工厂" :disabled="!isAdmin || submitting || unknown || checkingSave || customerOpen || deleting || !!deletePending" :loading="factoryLoading">
        <el-option v-for="f in factories" :key="f.id" :label="f.name" :value="f.id" />
      </el-select>
      <el-button v-if="has('metal:work-order:add')" class="list-action list-action--create" :disabled="!factoryId || deleting || !!deletePending" @click="openAdd">添加</el-button>
      <el-button v-if="has('metal:work-order:edit')" class="list-action list-action--edit" :disabled="selected.length !== 1 || selected[0]?.canEdit===false || deleting || !!deletePending" @click="openDetail(selected[0].id, true)">编辑</el-button>
      <el-button v-if="has('metal:work-order:remove')" class="list-action list-action--delete" :disabled="!selected.length || selected.some(r=>r.canDelete===false) || deleting || !!deletePending" @click="removeOrders(selected)">删除</el-button>

    </div>
    <el-alert v-if="factoryError" type="error" :closable="false"><template #title>{{ factoryError }} <el-button link type="primary" @click="loadFactories">重试</el-button></template></el-alert>
    <el-alert v-if="listError" type="error" :closable="false"><template #title>{{ listError }} <el-button link type="primary" @click="loadList()">重试</el-button></template></el-alert>
    <el-alert v-if="deleteError" :title="deleteError" type="error" :closable="false" />
    <div v-if="deletePending"><el-button :loading="deleting" @click="verifyDelete">核实删除结果</el-button><el-button v-if="deleteNotFound" :disabled="deleting" @click="submitDelete">重新提交原删除请求</el-button></div>
    <el-table :data="records" v-loading="listLoading" border row-key="id" @selection-change="selected = $event">
      <el-table-column v-if="has('metal:work-order:edit') || has('metal:work-order:remove')" type="selection" width="46" />
      <el-table-column prop="number" label="工单号" min-width="160" />
      <el-table-column prop="customer" label="客户名称" min-width="130" />
      <el-table-column prop="factory" label="工厂名称" min-width="130" />
      <el-table-column prop="line" label="产线名称" min-width="130" />
      <el-table-column prop="owner" label="负责人" min-width="100" />
      <el-table-column prop="weight" label="客户来料净重（g）" min-width="160" align="left"><template #default="{ row }">{{ decimalText(row.weight) }}</template></el-table-column>
      <el-table-column label="状态" width="110"><template #default="{row}">{{orderStatus[row.status]||row.status}}</template></el-table-column>
      <el-table-column prop="creator" label="创建人" min-width="100" />
      <el-table-column prop="createdAt" label="创建时间" min-width="180" />
      <el-table-column label="操作" width="210"><template #default="{ row }"><el-button v-if="has('metal:work-order:query')" class="list-action list-action--primary list-action--row" @click="flow?.open(row.id)">查看</el-button><el-button v-if="has('metal:work-order:edit')" class="list-action list-action--edit list-action--row" :disabled="row.canEdit===false || deleting || !!deletePending" :title="row.disabledReason" @click="openDetail(row.id, true)">编辑</el-button><el-button v-if="has('metal:work-order:remove')" class="list-action list-action--delete list-action--row" :disabled="row.canDelete===false || deleting || !!deletePending" :title="row.disabledReason" @click="removeOrders([row])">删除</el-button></template></el-table-column>
    </el-table>
    <el-pagination v-model:current-page="page" :page-size="16" :total="total" layout="total, prev, pager, next" />

    <el-dialog v-model="dialog" :title="detailMode ? '查看工单' : editId ? '编辑工单' : '添加工单'" width="1100px" class="production-order-dialog" :close-on-click-modal="false" :before-close="closeDialog" destroy-on-close>
      <el-alert v-if="catalogError" type="error" :closable="false"><template #title>{{ catalogError }} <el-button link type="primary" @click="retryDialog">重试</el-button></template></el-alert>
      <el-alert v-if="saveError" type="error" :closable="false" :title="saveError" />
      <div v-if="unknown" class="submission-actions"><el-button :loading="submitting" @click="verifySubmission">核实保存结果</el-button><el-button :disabled="submitting || !notFound" @click="submitPayload">重新提交原请求</el-button></div>
      <el-alert v-if="!detailMode && selectionIssues" :title="selectionIssues" type="warning" :closable="false" />
      <el-form label-position="top" :disabled="detailMode || submitting || unknown || checkingSave || customerOpen" v-loading="loading && !loaded" @submit.prevent>
        <h3>基础信息</h3>
        <div class="form-grid">
          <el-form-item label="生产单据编号"><el-input :model-value="orderNumber || '保存后自动生成'" readonly /></el-form-item>
          <el-form-item label="工厂名称" required><el-input :model-value="factoryName" readonly /></el-form-item>
        </div>
        <h3>客户与加工信息</h3>
        <div class="form-grid">
          <el-form-item label="客户名称" required><div class="customer-control"><el-select v-model="draft.customerId" aria-label="客户名称" clearable filterable placeholder="请选择客户" :disabled="detailMode || submitting || unknown || checkingSave || customerOpen || !loaded" no-data-text="本厂暂无客户" @visible-change="visible => visible && autoRefresh([0])"><el-option v-for="c in options(customers, draft.customerId, 0)" :key="c.id" :value="c.id" :label="c.name" :disabled="!!c.invalid" /></el-select><el-button v-if="!detailMode && has('metal:customer:add')" :disabled="submitting || unknown || checkingSave || customerOpen || !factoryId || !!pendingCustomerId" @click="customerOpen = true">添加客户</el-button></div></el-form-item>
          <el-form-item label="加工类型" required><el-select v-model="draft.processingType" aria-label="加工类型" @change="draft.weight = ''; draft.arrivals=[]"><el-option label="来料定制" value="MATERIAL_CUSTOM" /><el-option label="直接定制" value="DIRECT_CUSTOM" /><el-option label="欠料定制" value="SHORTAGE_CUSTOM" /></el-select></el-form-item>
          <template v-if="selectedCustomer">
            <el-form-item v-for="field in customerFields" :key="field.key" :label="field.label"><el-input :model-value="String(selectedCustomer[field.key] || '')" readonly /></el-form-item>
          </template>
          <el-form-item v-if="draft.processingType === 'MATERIAL_CUSTOM'&&editId&&!draft.arrivals.length" label="历史客户来料净重（g）"><el-input v-model="draft.weight" class="incoming-weight" aria-label="历史客户来料净重（g）" inputmode="decimal" maxlength="17" @blur="normalizeWeight" /></el-form-item>
          <el-form-item label="产线" required><el-select :model-value="draft.lineId" aria-label="产线" filterable :disabled="detailMode || submitting || unknown || checkingSave || customerOpen || !loaded" placeholder="请选择产线" no-data-text="本厂暂无产线" @visible-change="visible => visible && autoRefresh([1, 3])" @change="changeLine"><el-option v-for="line in options(lines, draft.lineId, 1)" :key="line.id" :value="line.id" :label="line.name" :disabled="!!line.invalid" /></el-select></el-form-item>
          <el-form-item label="开单日期" required><el-date-picker v-model="draft.orderDate" aria-label="开单日期" type="date" value-format="YYYY-MM-DD" :clearable="false" /></el-form-item>
          <el-form-item label="负责人" required><el-select v-model="draft.ownerId" aria-label="负责人" filterable :disabled="detailMode || submitting || unknown || checkingSave || customerOpen || !loaded" placeholder="请选择负责人" no-data-text="本厂暂无启用人员" @visible-change="visible => visible && autoRefresh([2])"><el-option v-for="person in options(staff, draft.ownerId, 2)" :key="person.id" :value="person.id" :label="person.nickName" :disabled="!!person.invalid" /></el-select></el-form-item>
        </div>
        <ArrivalEditor v-if="draft.processingType==='MATERIAL_CUSTOM'&&(!editId||draft.arrivals.length>0)" ref="arrivalEditor" v-model="draft.arrivals" :factory-id="factoryId" :disabled="detailMode||submitting||unknown" @total="value=>draft.weight=value"/>
        <h3>产品配置</h3>
        <div v-for="(group, index) in draft.products" :key="group.key" class="product-group">
          <div class="product-heading"><span>产品 {{ index + 1 }}</span><el-button v-if="!detailMode" link type="danger" :disabled="submitting || unknown || checkingSave || customerOpen || draft.products.length === 1" @click="draft.products.splice(index, 1)">移除</el-button></div>
          <div class="form-grid">
            <el-form-item label="选择产品" required><el-select v-model="group.productId" :aria-label="`选择产品 ${index + 1}`" filterable :disabled="detailMode || submitting || unknown || checkingSave || customerOpen || !loaded || !draft.lineId" :placeholder="!draft.lineId?'请先选择产线':!loaded?'基础资料尚未加载完成':'请选择产品'" no-data-text="该产线未关联本厂产品" @visible-change="visible => visible && autoRefresh([1, 3])" @change="selectProduct(group)"><el-option v-for="product in options(lineProducts, group.productId, 3)" :key="product.id" :value="product.id" :label="`${product.name}（${product.code || '—'}）`" :disabled="!!product.invalid" /></el-select></el-form-item>
            <el-form-item label="产品数量" required><el-input-number v-model="group.quantity" :aria-label="`产品数量 ${index + 1}`" :min="1" :step="1" /></el-form-item>
          </div>
          <el-table :data="group.processes" border>
            <el-table-column prop="processName" label="工序名称" min-width="150" />
            <el-table-column label="开始时间" min-width="230"><template #default="{ row }"><el-date-picker v-model="row.start" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="选择开始时间" aria-label="开始时间" /></template></el-table-column>
            <el-table-column label="结束时间" min-width="230"><template #default="{ row }"><el-date-picker v-model="row.end" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="选择结束时间" aria-label="结束时间" /></template></el-table-column>
          </el-table>
        </div>
        <el-button v-if="!detailMode" type="primary" plain :disabled="submitting || unknown || checkingSave || customerOpen || draft.products.length >= 100" @click="draft.products.push(newGroup())">＋ 添加产品</el-button>
      </el-form>
      <template #footer><el-button :disabled="submitting || checkingSave || customerOpen" @click="closeDialog()">{{ detailMode ? '关闭' : '取消' }}</el-button><el-button v-if="!detailMode" type="primary" :loading="submitting || checkingSave" :disabled="!ready || unknown || checkingSave || customerOpen || !!selectionIssues || !has(editId ? 'metal:work-order:edit' : 'metal:work-order:add')" @click="saveOrder">确认</el-button></template>
    </el-dialog>
    <ProductionFlow ref="flow" :factory-id="factoryId" @changed="loadList()"/>
    <CustomerCreateDialog v-model="customerOpen" :factory-id="factoryId" :factory-name="factoryName" :is-context-current="customerContext" :find-customers="findCustomers" @created="customerCreated" />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { decimalText, roundDecimal } from '@/utils/business-decimal'
import useUserStore from '@/store/modules/user'
import CustomerCreateDialog from '@/components/CustomerCreateDialog/index.vue'
import ArrivalEditor from './ArrivalEditor.vue'
import ProductionFlow from './ProductionFlow.vue'
import {useStorage} from '../storage-live/client'
import { useActionPermission } from '@/composables/useActionPermission'

function normalizeWeight() {
  const value = draft.value.weight.trim()
  if (/^\d{1,14}(\.\d{1,2})?$/.test(value)) draft.value.weight = roundDecimal(value)
}

type Factory = { id: string; name: string }
type CatalogRow = { id: string; factoryId: string; name: string; [key: string]: any }
type Process = { processId: string; processName: string; start?: string; end?: string }
type Group = { key: number; productId: string; quantity: number | undefined; processes: Process[] }
type Order = { canEdit?:boolean;canDelete?:boolean;disabledReason?:string;status:string;version: number; id: string; number: string; factoryId: string; factory: string; customer: string; line: string; owner: string; weight: string | null; creator: string; createdAt: string }
type Payload = { arrivals:any[];factoryId: string; requestId: string; customerId: string | null; processingType: string; weight: string | null; lineId: string; ownerId: string; orderDate: string; products: { productId: string; quantity: number; processes: { processId: string; start: string | null; end: string | null }[] }[] }
const orderStatus:Record<string,string>={CREATED:'已创建',IN_PROGRESS:'加工中',COMPLETED:'已完成',EXCEPTION_CLOSED:'异常关闭'}
const arrivalEditor=ref<InstanceType<typeof ArrivalEditor>>(),flow=ref<InstanceType<typeof ProductionFlow>>()
const {state:storageState}=useStorage()
const route = useRoute(), router = useRouter()
const user = useUserStore()
const { has, isAdmin, onChange } = useActionPermission()
const factories = ref<Factory[]>([]), factoryId = ref(''), factoryLoading = ref(false), factoryError = ref('')
const dialog = ref(false), loading = ref(false), catalogError = ref(''), loaded = ref(false), page = ref(1)
const customers = ref<CatalogRow[]>([]), lines = ref<CatalogRow[]>([]), staff = ref<CatalogRow[]>([]), products = ref<CatalogRow[]>([])
const records = ref<Order[]>([]), total = ref(0), listLoading = ref(false), listError = ref('')
const editId = ref(''), editVersion = ref(0)
const selected = ref<Order[]>([]), deleting = ref(false), deleteError = ref(''), deleteNotFound = ref(false)
const deletePending = ref<{factoryId: string; requestId: string; orders: {id: string; version: number}[]} | null>(null)
const detailMode = ref(false), detailId = ref(''), orderNumber = ref(''), detailFactory = ref('')
const customerOpen = ref(false), checkingSave = ref(false), pendingCustomerId = ref(''), refreshNotice = ref('')
const archives = [new Map<string, CatalogRow>(), new Map<string, CatalogRow>(), new Map<string, CatalogRow>(), new Map<string, CatalogRow>()]
const sourceErrors = new Map<number, string>(), sourceReady = new Set<number>()
let activeRefresh: { epoch: number; indices: number[]; promise: Promise<boolean> } | null = null
const submitting = ref(false), unknown = ref(false), notFound = ref(false), saveError = ref('')
let payload: Payload | null = null
let requestEpoch = 0, factoryEpoch = 0, listEpoch = 0, writeEpoch = 0, groupKey = 0, initialDraft = ''
const pad = (n: number) => String(n).padStart(2, '0')
function localTime(date = new Date()) { return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}` }
function newGroup(): Group { return { key: ++groupKey, productId: '', quantity: 1, processes: [] } }
function newDraft() { return { customerId: '', processingType: 'MATERIAL_CUSTOM', arrivals:[] as any[], weight: '', lineId: '', orderDate: localTime().slice(0, 10), ownerId: '', products: [newGroup()] } }
const draft = ref(newDraft())
const factoryName = computed(() => detailMode.value ? detailFactory.value : factories.value.find(f => f.id === factoryId.value)?.name || '')
const selectedCustomer = computed(() => customers.value.find(c => c.id === draft.value.customerId) || archives[0].get(draft.value.customerId))
const lineProducts = computed(() => { const ids = lines.value.find(l => l.id === draft.value.lineId)?.productIds || []; return products.value.filter(p => ids.map(String).includes(p.id)) })
const ready = computed(() => loaded.value && !loading.value && !catalogError.value)
const dirty = () => dialog.value && !detailMode.value && JSON.stringify(draft.value) !== initialDraft
const customerFields = [{ key: 'companyName', label: '客户公司名称' }, { key: 'address', label: '客户地址' }, { key: 'contactName', label: '联络人名称' }, { key: 'contactPhone', label: '联络人电话' }, { key: 'department', label: '部门' }]
const catalogSources = [
  { url: '/metal/customer/list', permission: 'metal:customer:list', label: '客户' },
  { url: '/metal/production-line/list', permission: 'metal:production-line:list', label: '产线' },
  { url: '/system/user/list', permission: 'system:user:list', label: '人员' },
  { url: '/metal/product/list', permission: 'metal:product:list', label: '产品' }
]
async function loadFactories() {
  const epoch = ++factoryEpoch
  factoryLoading.value = true; factoryError.value = ''
  try {
    const res = await request({ url: '/metal/options/factories', quiet: true, isContextCurrent: () => epoch === factoryEpoch })
    if (epoch !== factoryEpoch) return
    factories.value = (res.data || []).map((f: Factory) => ({ ...f, id: String(f.id) }))
    if (!isAdmin.value) factoryId.value = factories.value.length === 1 ? factories.value[0].id : ''
    else if (!factoryId.value && typeof route.query.factoryId === 'string' && factories.value.some(f => f.id === route.query.factoryId)) factoryId.value = route.query.factoryId
    if (!factories.value.some(f => f.id === factoryId.value)) factoryId.value = ''
  } catch (error: any) { if (epoch === factoryEpoch) factoryError.value = `工厂加载失败：${error.message || '请检查查看权限后重试'}` }
  finally { if (epoch === factoryEpoch) factoryLoading.value = false }
}
function options(rows: CatalogRow[], id: string, kind: number): CatalogRow[] {
  if (!id || rows.some(row => row.id === id)) return rows
  const old = archives[kind].get(id)
  return [...rows, { ...old, id, factoryId: factoryId.value, name: `${old?.name || id}（已失效）`, nickName: `${old?.nickName || id}（已失效）`, invalid: true }]
}
const selectionIssues = computed(() => {
  const issues: string[] = []
  for (const [id, rows, label] of [[draft.value.customerId, customers.value, '客户'], [draft.value.lineId, lines.value, '产线'], [draft.value.ownerId, staff.value, '负责人']] as const)
    if (id && !rows.some(row => row.id === id)) issues.push(`${label}已失效，请重新选择`)
  for (const group of draft.value.products) {
    if (!group.productId) continue
    const product = lineProducts.value.find(p => p.id === group.productId)
    if (!product) issues.push('产品已失效或不再关联本产线，请重新选择')
    else if (JSON.stringify(group.processes.map(p => p.processId)) !== JSON.stringify((product.processes || []).map((p: Process) => String(p.processId)))) issues.push('工序配置已变化，请展开产品下拉核对')
  }
  return [...new Set(issues)].join('；')
})
function selectedFingerprint() {
  return JSON.stringify([customers.value.find(c => c.id === draft.value.customerId), lines.value.find(l => l.id === draft.value.lineId), staff.value.find(p => p.id === draft.value.ownerId), draft.value.products.map(g => lineProducts.value.find(p => p.id === g.productId))])
}
async function readCatalog(index: number, factory: string, current: () => boolean): Promise<CatalogRow[]> {
  const source = catalogSources[index]
  if (!has(source.permission)) throw new Error(`缺少${source.label}查看权限，请联系管理员授权`)
  const rows: CatalogRow[] = []
  for (let pageNum = 1; ; pageNum++) {
    if (!current()) throw new Error('资料上下文已变化')
    const result = await request({ url: source.url, params: { factoryId: factory, pageNum, pageSize: 100 }, quiet: true, isContextCurrent: current })
    if (!current()) throw new Error('资料上下文已变化')
    if (!Array.isArray(result.rows) || !Number.isSafeInteger(result.total) || result.total < 0 || !result.rows.length && rows.length < result.total) throw new Error('基础资料分页响应异常，请重试')
    for (const row of result.rows) {
      if (String(row.factoryId) !== factory || rows.some(r => r.id === String(row.id))) throw new Error('基础资料归属或分页已变化，请重试')
      rows.push({ ...row, id: String(row.id) })
    }
    if (rows.length >= result.total) return rows
  }
}
function customerContext() { return dialog.value && !detailMode.value && !submitting.value && !unknown.value }
function findCustomers(current: () => boolean) { return readCatalog(0, factoryId.value, current) }
function autoRefresh(indices = [0, 1, 2, 3]) { if (!checkingSave.value) void loadCatalogs(indices) }
async function loadCatalogs(requested = [0, 1, 2, 3]): Promise<boolean> {
  if (!customerContext() || customerOpen.value) return false
  const indices = loaded.value ? [...new Set(requested)] : [0, 1, 2, 3]
  if (activeRefresh?.epoch === requestEpoch) {
    const active = activeRefresh
    if (indices.every(i => active.indices.includes(i))) return active.promise
    await active.promise
    return loadCatalogs(indices)
  }
  const epoch = requestEpoch, factory = factoryId.value
  const alive = () => epoch === requestEpoch && dialog.value && factory === factoryId.value && !detailMode.value
  const current = () => alive() && !submitting.value && !unknown.value && !customerOpen.value
  loading.value = true
  const promise = (async () => {
    try {
      const results = await Promise.allSettled(indices.map(i => readCatalog(i, factory, current)))
      if (!current()) return false
      const before = selectedFingerprint(), targets = [customers, lines, staff, products]
      results.forEach((result, n) => {
        const i = indices[n]
        if (result.status === 'rejected') { sourceErrors.set(i, `${catalogSources[i].label}资料加载失败：${result.reason?.message || '请重试'}`); return }
        targets[i].value.forEach(row => archives[i].set(row.id, row))
        targets[i].value = i === 2 ? result.value.filter(row => row.status === '0') : result.value
        targets[i].value.forEach(row => archives[i].set(row.id, row)); sourceErrors.delete(i); sourceReady.add(i)
      })
      let changed = before !== selectedFingerprint()
      if (indices.includes(1) || indices.includes(3)) {
        for (const group of draft.value.products) {
          const product = lineProducts.value.find(p => p.id === group.productId)
          if (!product) continue
          const next = (product.processes || []) as Process[], old = new Map(group.processes.map(p => [p.processId, p]))
          const lost = group.processes.filter(p => !next.some(n => String(n.processId) === p.processId) && (p.start || p.end))
          if (lost.length) {
            try { await ElMessageBox.confirm(`最新配置移除了${lost.map(p => p.processName).join('、')}，是否移除这些工序及其已填时间？`, '工序配置变化', { confirmButtonText: '应用新配置', cancelButtonText: '保留并核对', type: 'warning' }) }
            catch { if (current()) refreshNotice.value = '已保留原工序时间，请重新展开产品下拉核对配置'; continue }
          }
          if (!current() || !draft.value.products.includes(group) || group.productId !== product.id) return false
          const merged = next.map(p => ({ processId: String(p.processId), processName: p.processName, start: old.get(String(p.processId))?.start, end: old.get(String(p.processId))?.end }))
          if (JSON.stringify(merged) !== JSON.stringify(group.processes)) changed = true
          group.processes = merged
        }
      }
      if (!current()) return false
      if (pendingCustomerId.value && indices.includes(0) && !sourceErrors.has(0)) {
        if (customers.value.some(c => c.id === pendingCustomerId.value)) { draft.value.customerId = pendingCustomerId.value; pendingCustomerId.value = '' }
        else sourceErrors.set(0, '客户已创建，资料加载失败；请重试读取，勿重复创建')
      }
      catalogError.value = [...sourceErrors.values()].join('；')
      loaded.value = sourceReady.size === 4
      if (changed) refreshNotice.value = '所选资料已更新，请核对后再确认保存'
      return changed
    } finally { if (alive()) loading.value = false; if (activeRefresh?.epoch === epoch) activeRefresh = null }
  })()
  activeRefresh = { epoch, indices, promise }
  return promise
}
async function customerCreated(result: { id: string; factoryId: string }) {
  if (!customerContext() || result.factoryId !== factoryId.value) return
  pendingCustomerId.value = result.id
  await loadCatalogs([0])
  if (pendingCustomerId.value) catalogError.value = '客户已创建，资料加载失败；请重试读取，勿重复创建'
}

function openAdd() {
  if (!factoryId.value || !has('metal:work-order:add')) return
  requestEpoch++; activeRefresh = null; sourceErrors.clear(); sourceReady.clear(); archives.forEach(a => a.clear()); loaded.value = false; refreshNotice.value = ''; pendingCustomerId.value = ''
  editId.value = ''; detailMode.value = false; orderNumber.value = ''; saveError.value = ''; unknown.value = false; notFound.value = false; payload = null
  draft.value = newDraft(); initialDraft = JSON.stringify(draft.value)
  customers.value = []; lines.value = []; staff.value = []; products.value = []
  dialog.value = true; void loadCatalogs()
}
async function confirmDiscard() {
  if (submitting.value || checkingSave.value || customerOpen.value) return false
  if (unknown.value) { ElMessage.warning('保存结果尚未确认，请先核实保存结果'); return false }
  if (!dirty()) return true
  try { await ElMessageBox.confirm('放弃尚未保存的工单内容？', '确认关闭', { type: 'warning', confirmButtonText: '放弃', cancelButtonText: '继续填写' }); return true } catch { return false }
}
async function closeDialog(done?: () => void) { if (await confirmDiscard()) { requestEpoch++; dialog.value = false; done?.() } }
async function changeLine(id: string) {
  if (id === draft.value.lineId) return
  if (draft.value.products.some(g => g.productId || g.quantity !== 1)) {
    try { await ElMessageBox.confirm('更换产线将清空已配置的产品和工序时间，是否继续？', '更换产线', { confirmButtonText: '更换', cancelButtonText: '取消', type: 'warning' }) } catch { return }
  }
  if (!dialog.value) return
  draft.value.lineId = id; draft.value.products = [newGroup()]
}
function selectProduct(group: Group) {
  group.processes = (lineProducts.value.find(p => p.id === group.productId)?.processes || []).map((p: Process) => ({ processId: String(p.processId), processName: p.processName }))
}
async function saveOrder() {
  if (submitting.value || unknown.value || checkingSave.value || customerOpen.value || !ready.value || !factoryId.value || !has(editId.value ? 'metal:work-order:edit' : 'metal:work-order:add')) return
  const epoch = requestEpoch
  checkingSave.value = true
  let changed = false
  try { changed = await loadCatalogs() } finally { checkingSave.value = false }
  if (epoch !== requestEpoch || !dialog.value || !ready.value) return
  if (changed || selectionIssues.value) { refreshNotice.value = selectionIssues.value || '所选资料已更新，请核对后再次确认保存'; return }
  refreshNotice.value = ''
  const data = draft.value, line = lines.value.find(l => l.id === data.lineId), owner = staff.value.find(p => p.id === data.ownerId)
  let error = ''
  if (!line || !owner || !data.orderDate) error = '请选择产线、开单日期和负责人'
  else if (data.customerId && !selectedCustomer.value) error = '客户已失效，请重新选择'
  else if (!['MATERIAL_CUSTOM', 'DIRECT_CUSTOM', 'SHORTAGE_CUSTOM'].includes(data.processingType)) error = '请选择加工类型'
  else if (data.weight.trim() && !/^\d{1,14}(\.\d{1,2})?$/.test(data.weight.trim())) error = '客户来料净重须为非负数，最多14位整数和2位小数'
  else if (data.products.some(g => !lineProducts.value.some(p => p.id === g.productId))) error = '请为每组选择本产线的产品'
  else if (data.products.some(g => !Number.isSafeInteger(g.quantity) || Number(g.quantity) < 1 || Number(g.quantity) > 2147483647)) error = '产品数量须为1至2147483647的整数'
  else if (data.products.some(g => g.processes.some(p => p.start && p.end && p.end < p.start))) error = '工序结束时间不得早于开始时间'
  if(!error&&!data.customerId)error='生产工单必须选择客户'
  if(!error&&data.processingType==='MATERIAL_CUSTOM'&&(!editId.value||data.arrivals.length))error=arrivalEditor.value?.validate()||''
  if (error) { ElMessage.warning(error); return }
  const requestId = newRequestId()
  payload = { arrivals:data.arrivals, factoryId: factoryId.value, requestId, customerId: data.customerId || null, processingType: data.processingType, weight: data.processingType === 'MATERIAL_CUSTOM' && data.weight.trim() ? roundDecimal(data.weight.trim()) : null, lineId: data.lineId, ownerId: data.ownerId, orderDate: data.orderDate, products: data.products.map(g => ({ productId: g.productId, quantity: g.quantity!, processes: g.processes.map(p => ({ processId: p.processId, start: p.start || null, end: p.end || null })) })) }
  void submitPayload()
}
function newRequestId() {
  const bytes = crypto.getRandomValues(new Uint8Array(16)); bytes[6] = (bytes[6] & 15) | 64; bytes[8] = (bytes[8] & 63) | 128
  const hex = [...bytes].map(b => b.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}
async function removeOrders(rows: Order[]) {
  if (!factoryId.value || deleting.value || deletePending.value || !has('metal:work-order:remove')) return
  const factory = factoryId.value, epoch = listEpoch
  const orders = rows.map(r => ({id: r.id, version: r.version}))
  try { await ElMessageBox.confirm(`确认删除所选 ${orders.length} 张工单？删除后列表中不再显示。`, '删除工单', {type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消'}) } catch { return }
  if (factory !== factoryId.value || epoch !== listEpoch || !has('metal:work-order:remove')) return
  deletePending.value = {factoryId: factory, requestId: newRequestId(), orders}
  await submitDelete()
}
async function deleteSucceeded() {
  deletePending.value = null; deleteError.value = ''; deleteNotFound.value = false
  ElMessage.success('工单已删除'); page.value = 1; await loadList()
  if (listError.value) listError.value = '已删除，列表刷新失败'
}
async function submitDelete() {
  const input = deletePending.value
  if (!input || deleting.value || !has('metal:work-order:remove')) return
  const current = () => deletePending.value === input && factoryId.value === input.factoryId
  deleting.value = true; deleteNotFound.value = false; let verify = false
  try {
    await request({url: '/metal/production-order/batch-delete', method: 'post', data: input, quiet: true, headers: {repeatSubmit: false}, isContextCurrent: current})
    if (current()) await deleteSucceeded()
  } catch (e: any) {
    if (!current()) return
    const code = Number(e.response?.data?.code ?? e.response?.status ?? e.code)
    if ([400,403,404,409,422].includes(code)) { deletePending.value = null; deleteError.value = e.message || '删除失败，请刷新列表后核对'; await loadList() }
    else { deleteError.value = '删除结果待核实，请勿重复发起删除'; verify = true }
  } finally { deleting.value = false }
  if (verify) await verifyDelete()
}
async function verifyDelete() {
  const input = deletePending.value
  if (!input || deleting.value || !has('metal:work-order:remove')) return
  const current = () => deletePending.value === input && factoryId.value === input.factoryId
  deleting.value = true
  try {
    await request({url: `/metal/production-order/operations/${input.requestId}`, params: {factoryId: input.factoryId, action: 'remove'}, quiet: true, isContextCurrent: current})
    if (current()) await deleteSucceeded()
  } catch (e: any) { if (current()) { deleteNotFound.value = Number(e.response?.data?.code ?? e.response?.status ?? e.code) === 404; deleteError.value = '尚未确认删除结果，请稍后核实或重新提交原请求' } }
  finally { deleting.value = false }
}
async function loadList(saved = false) {
  const epoch = ++listEpoch, factory = factoryId.value
  selected.value = []; records.value = []; listError.value = ''
  if (!factory || !has('metal:work-order:list')) { total.value = 0; listLoading.value = false; return }
  listLoading.value = true
  const current = () => epoch === listEpoch && factory === factoryId.value
  try {
    const result = await request({ url: '/metal/production-order/list', params: { factoryId: factory, pageNum: page.value, pageSize: 16 }, quiet: true, isContextCurrent: current })
    if (current()) { records.value = result.rows; total.value = result.total }
  } catch (error: any) { if (current()) listError.value = saved ? '已保存，列表刷新失败' : `工单列表加载失败：${error.message || '请重试'}` }
  finally { if (current()) listLoading.value = false }
}
async function savedSuccessfully() {
  unknown.value = false; notFound.value = false; payload = null; saveError.value = ''; dialog.value = false; requestEpoch++
  ElMessage.success('工单已保存')
  if (page.value !== 1) page.value = 1
  await loadList(true)
}
async function submitPayload() {
  if (!payload || submitting.value || !has(editId.value ? 'metal:work-order:edit' : 'metal:work-order:add')) return
  const epoch = ++writeEpoch, submitted = payload
  const current = () => epoch === writeEpoch && dialog.value && factoryId.value === submitted.factoryId
  submitting.value = true; saveError.value = ''; notFound.value = false
  let verify = false, refresh = false
  try {
    await request({ url: editId.value ? `/metal/production-order/${editId.value}` : '/metal/production-order', method: editId.value ? 'put' : 'post', data: editId.value ? { version: editVersion.value, data: submitted } : submitted, headers: { repeatSubmit: false }, quiet: true, isContextCurrent: current })
    if (current()) await savedSuccessfully()
  } catch (error: any) {
    if (!current()) return
    const code = error.response?.data?.code ?? error.response?.status ?? error.code
    if ([400, 403, 404, 409, 422].includes(Number(code))) {
      unknown.value = false; saveError.value = error.message || error.response?.data?.msg || '保存校验失败'; payload = null; refresh = Number(code) === 409
    } else { unknown.value = true; saveError.value = '保存结果尚未确认，正在核实'; verify = true }
  } finally { if (epoch === writeEpoch) submitting.value = false }
  if (verify) await verifySubmission()
  else if (refresh) await loadCatalogs()
}
async function verifySubmission() {
  if (!payload || submitting.value) return
  const epoch = ++writeEpoch, submitted = payload
  const current = () => epoch === writeEpoch && dialog.value && factoryId.value === submitted.factoryId
  submitting.value = true
  try {
    await request({ url: `/metal/production-order/${editId.value ? 'operations' : 'requests'}/${submitted.requestId}`, params: { factoryId: submitted.factoryId, ...(editId.value ? { action: 'edit' } : {}) }, quiet: true, isContextCurrent: current })
    if (current()) await savedSuccessfully()
  } catch (error: any) {
    if (!current()) return
    notFound.value = Number(error.response?.data?.code ?? error.response?.status ?? error.code) === 404
    saveError.value = notFound.value ? '尚未查到此次保存，可以再次核实或重新提交原请求；不会重复执行' : '暂时无法核实保存结果，请稍后点击“核实保存结果”'
  } finally { if (epoch === writeEpoch) submitting.value = false }
}
async function openDetail(id: string, editing = false) {
  if (!factoryId.value || !has(editing ? 'metal:work-order:edit' : 'metal:work-order:query') || deleting.value || deletePending.value) return
  editId.value = editing ? id : ''; unknown.value = false; notFound.value = false; payload = null; sourceErrors.clear(); sourceReady.clear(); archives.forEach(a => a.clear()); activeRefresh = null; refreshNotice.value = ''; pendingCustomerId.value = ''
  detailMode.value = true; detailId.value = id; orderNumber.value = ''; detailFactory.value = ''; dialog.value = true; loaded.value = false; catalogError.value = ''; saveError.value = ''; draft.value = newDraft()
  customers.value = []; lines.value = []; staff.value = []; products.value = []
  const epoch = ++requestEpoch, factory = factoryId.value
  const current = () => epoch === requestEpoch && dialog.value && factory === factoryId.value
  loading.value = true
  try {
    const result = await request({ url: `/metal/production-order/${id}${editing ? '/edit-context' : ''}`, params: { factoryId: factory }, quiet: true, isContextCurrent: current })
    if (!current()) return
    const data = result.data
    orderNumber.value = data.number; detailFactory.value = data.factory
    customers.value = data.customerInfo ? [data.customerInfo] : []
    lines.value = [{ id: data.lineId, name: data.line, factoryId: factory, productIds: data.products.map((g: any) => g.productId) }]
    staff.value = [{ id: data.ownerId, name: data.owner, nickName: data.owner, factoryId: factory }]
    products.value = [...new Map<string, CatalogRow>(data.products.map((g: any) => [g.productId, { ...g.product, factoryId: factory }])).values()]
    draft.value = { arrivals:data.arrivals||[], customerId: data.customerId || '', processingType: data.processingType, weight: data.weight == null ? '' : roundDecimal(data.weight), lineId: data.lineId, ownerId: data.ownerId, orderDate: data.orderDate, products: data.products.map((g: any) => ({ key: ++groupKey, productId: g.productId, quantity: g.quantity, processes: g.processes })) }
    loaded.value = true
    if (editing) {
      editVersion.value = data.version; detailMode.value = false; loaded.value = false
      initialDraft = JSON.stringify(draft.value)
      await loadCatalogs()
    }
  } catch (error: any) { if (current()) catalogError.value = `详情加载失败：${error.message || '请重试'}` }
  finally { if (current()) loading.value = false }
}
function retryDialog() { if (detailMode.value) void openDetail(detailId.value, !!editId.value); else void loadCatalogs() }
let focusQueued = false
function onReturn() {
  if (focusQueued || document.visibilityState !== 'visible' || !customerContext() || customerOpen.value || checkingSave.value) return
  focusQueued = true
  queueMicrotask(() => { focusQueued = false; if (document.visibilityState === 'visible') autoRefresh() })
}
function stopRefreshListeners() { window.removeEventListener('focus', onReturn); document.removeEventListener('visibilitychange', onReturn) }
watch([dialog, detailMode], () => {
  stopRefreshListeners()
  if (dialog.value && !detailMode.value) { window.addEventListener('focus', onReturn); document.addEventListener('visibilitychange', onReturn) }
}, { flush: 'sync' })

watch(factoryId, () => {
  requestEpoch++; customerOpen.value = false; pendingCustomerId.value = ''; checkingSave.value = false; dialog.value = false; records.value = []; total.value = 0; catalogError.value = ''; page.value = 1
  void router.replace({ query: { ...route.query, factoryId: factoryId.value || undefined } })
  void loadList()
})
watch(page, () => { void loadList() }, { flush: 'sync' })
watch(() => route.query.factoryId, value => { if (isAdmin.value && !dialog.value && typeof value === 'string' && factories.value.some(f => f.id === value)) factoryId.value = value })
onBeforeRouteLeave(async () => { if (deleting.value || deletePending.value) { ElMessage.warning('请先核实删除结果'); return false }; return await confirmDiscard() })
onChange(() => { deletePending.value = null; deleting.value = false; selected.value = []; editId.value = ''; customerOpen.value = false; checkingSave.value = false; requestEpoch++; factoryEpoch++; listEpoch++; writeEpoch++; records.value = []; dialog.value = false; submitting.value = false; unknown.value = false; payload = null; factories.value = []; factoryId.value = ''; if (user.sessionReady) void loadFactories() })
watch(factoryId,value=>{storageState.factoryId=value})
onBeforeUnmount(() => { stopRefreshListeners(); requestEpoch++; factoryEpoch++; listEpoch++; writeEpoch++; records.value = []; payload = null })
onMounted(loadFactories)
</script>

<style scoped>
.toolbar { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; background: white; border: 1px solid #e2e8f0; border-radius: 8px; padding: 12px; margin-bottom: 16px; }
.toolbar .el-select { width: 230px; }
.toolbar .el-button + .el-button { margin-left: 0; }
.submission-actions { margin-bottom: 12px; }
:deep(.incoming-weight .el-input__inner) { text-align: left; }
.el-pagination { justify-content: center; margin-top: 20px; }
.el-alert { margin-bottom: 16px; }
.form-grid { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 0 28px; }
h3 { display: flex; align-items: center; gap: 10px; font-size: 15px; color: #25364a; margin: 6px 0 22px; }
h3::before { content: ''; width: 7px; height: 7px; border-radius: 50%; background: #409eff; }
h3::after { content: ''; height: 1px; background: #e2e8f0; flex: 1; }
.customer-control { display: flex; width: 100%; gap: 8px; }
.product-group { border: 1px solid #e2e8f0; padding: 14px; border-radius: 6px; margin-bottom: 14px; }
.product-heading { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; color: #606266; }
:deep(.el-form-item) { padding-left: 12px; }
:deep(.el-form-item__label) { position: relative; text-align: left; }
:deep(.el-form-item.is-required .el-form-item__label::before) { position: absolute; left: -12px; margin: 0; }
:deep(.el-select), :deep(.el-date-editor.el-input), :deep(.el-input-number) { width: 100%; }
@media (max-width: 650px) { .form-grid { grid-template-columns: minmax(0, 1fr); } .preview-note { margin-left: 0; } .product-group { padding: 8px; } }
</style>
<style>
.production-order-dialog.el-dialog { max-width: 94vw; max-height: 90vh; margin: 5vh auto; display: flex; flex-direction: column; }
.production-order-dialog .el-dialog__body { overflow-y: auto; padding: 16px 22px; }
.production-order-dialog .el-dialog__footer { border-top: 1px solid #e2e8f0; padding-top: 16px; }
</style>
