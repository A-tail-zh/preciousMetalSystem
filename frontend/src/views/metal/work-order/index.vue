<template>
 <WorkOrderDetail v-if="detailId" :id="detailId" @back="back"/>
 <section v-else class="work-order-page">
  <div class="order-toolbar"><el-button v-if="actionPermission.hasAny(['metal:work-order:add'])" class="list-action list-action--create blue" @click="edit()">添加</el-button><el-button v-if="actionPermission.hasAny(['metal:work-order:add'])" class="list-action list-action--primary secondary-action" @click="edit(undefined,'RAW_PROCESSING')">独立原料加工</el-button><el-button v-if="actionPermission.hasAny(['metal:work-order:edit'])"  class="list-action list-action--edit orange" :disabled="selected.length!==1||!orderEditable(selected[0])" @click="edit(selected[0])">编辑</el-button><el-button v-if="actionPermission.hasAny(['metal:work-order:remove'])"  class="list-action list-action--delete red" :disabled="!selected.length||selected.some(row=>row.canDelete!==true)||deleting" @click="remove(selected)">删除</el-button><el-button v-if="actionPermission.hasAny(['metal:warehouse-order:add','metal:warehouse-order:query'])" class="list-action list-action--primary secondary-action" title="查询、续办或冲销历史独立酸洗批次" @click="acidWash?.open()">历史酸洗记录</el-button></div>
  <p v-if="refreshFailed" role="status">列表更新失败，当前数据可能过期。<el-button link @click="load">重试</el-button></p>
  <el-table ref="tableRef" v-loading="loading" :data="rows" row-key="id" border class="order-table" @selection-change="selected=$event">
   <el-table-column v-if="can('metal:work-order:edit')||can('metal:work-order:remove')" type="selection" width="50" align="center"/><el-table-column label="工单号" prop="orderNo" min-width="150" align="center"/><el-table-column label="客户" prop="customer" min-width="75" align="center"/><el-table-column label="工厂名称" prop="factoryName" min-width="90" align="center"/><el-table-column label="产线名称" prop="lineName" min-width="90" align="center"/><el-table-column label="负责人" prop="ownerName" min-width="75" align="center"/>
   <el-table-column label="任务类型" min-width="110"><template #default="{row}">{{row.taskType==='RAW_PROCESSING'?'原料加工':'产品生产'}}</template></el-table-column><el-table-column label="状态" min-width="130" align="center"><template #default="{row}">{{orderStatus[row.status]}}<template v-if="row.status==='REJECTED'">，原因：{{row.rejectReason}}</template></template></el-table-column>
   <el-table-column label="创建人" prop="creatorName" min-width="65" align="center"/><el-table-column label="创建时间" prop="createTime" :formatter="dateTimeCell" width="140" align="center"/><el-table-column v-if="can('metal:work-order:query')||can('metal:work-order:edit')||can('metal:work-order:remove')" class-name="order-actions" label="操作" min-width="264" align="center"><template #default="{row}"><el-button v-if="actionPermission.hasAny(['metal:work-order:query'])"  class="list-action list-action--primary list-action--row blue row-button" @click="view(row)">查看</el-button><el-button v-if="actionPermission.hasAny(['metal:work-order:edit'])"  class="list-action list-action--edit list-action--row orange row-button" :disabled="!orderEditable(row)" :title="row.operationDisabledReason" @click="edit(row)">编辑</el-button><el-button v-if="actionPermission.hasAny(['metal:work-order:remove'])"  class="list-action list-action--delete list-action--row red row-button" :disabled="row.canDelete!==true||deleting" :title="row.operationDisabledReason" @click="remove([row])">删除</el-button></template></el-table-column>
  </el-table>
  <div v-if="failed" class="load-error">列表加载失败 <el-button link type="primary" @click="load">重试</el-button></div>
  <div class="order-pagination"><el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :page-sizes="[16,32,64]" :total="total" layout="total, sizes, prev, pager, next, jumper" @current-change="load" @size-change="sizeChanged"/></div>
  <WorkOrderForm ref="formRef" @saved="saved"/>
  <AcidWashDialog ref="acidWash"/>

 </section>
</template>
<script setup lang="ts">
import { useActionPermission } from '@/composables/useActionPermission'
import {dateTimeCell} from '@/utils/display-date-time'
import {getToken} from '@/utils/auth'
import {ref,reactive,computed,onMounted,onBeforeUnmount,watch,nextTick} from 'vue'
import {useInventoryRefresh} from '@/composables/useInventoryRefresh'
import {useRoute,useRouter} from 'vue-router'
import {ElMessage,ElMessageBox} from 'element-plus'
import type {TableInstance} from 'element-plus'
import useUserStore from '@/store/modules/user'
import {orderList,orderDelete,orderStatus,orderEditable} from '@/api/metal/work-order'
import type {WorkOrder} from '@/api/metal/work-order'
import WorkOrderForm from './WorkOrderForm.vue'
import WorkOrderDetail from './WorkOrderDetail.vue'
import AcidWashDialog from '../warehouse-common/AcidWashDialog.vue'
const actionPermission=useActionPermission()
const user=useUserStore(),route=useRoute(),router=useRouter(),detailId=computed(()=>typeof route.query.id==='string'?route.query.id:'')
const query=reactive({pageNum:1,pageSize:16}),rows=ref<WorkOrder[]>([]),selected=ref<WorkOrder[]>([]),total=ref(0),loading=ref(false),failed=ref(false),deleting=ref(false)
const formRef=ref<InstanceType<typeof WorkOrderForm>>(),tableRef=ref<TableInstance>()
const acidWash=ref<InstanceType<typeof AcidWashDialog>>()
const reading=ref(false)
let epoch=0,operationEpoch=0,scheduled=false,disposed=false
const identity=()=>JSON.stringify([user.sessionReady,user.token,String(user.id),[...user.permissions].sort()])
const sessionCurrent=()=>user.sessionReady&&!!user.token&&user.token===getToken()
const can=(permission:string)=>sessionCurrent()&&actionPermission.has(permission)
const canReadList=()=>!disposed&&!detailId.value&&can('metal:work-order:list')
function clearList(){rows.value=[];selected.value=[];total.value=0;loading.value=false;failed.value=false;tableRef.value?.clearSelection()}
function invalidateAccess(){++epoch;++operationEpoch;clearList();reading.value=false;deleting.value=false;formRef.value?.invalidateSession()}
// Merge consecutive Pinia mutations and pagination events, but invalidate old reads immediately.
function load(){++epoch;if(scheduled||disposed)return;scheduled=true;queueMicrotask(()=>{scheduled=false;if(canReadList())void readList()})}
async function readList(background=false,contextCurrent=()=>true){
 const current=++epoch,account=identity(),path=route.fullPath,params={...query}
 const isCurrent=()=>contextCurrent()&&current===epoch&&canReadList()&&identity()===account&&route.fullPath===path&&(!background||!listBlocked.value&&!deleting.value)
 if(!isCurrent())return
 reading.value=true;if(!background)loading.value=true
 try{const response=await orderList(params,isCurrent,background);if(!isCurrent())return;const chosen=new Set(selected.value.map(row=>row.id));rows.value=response.rows;total.value=response.total;failed.value=false;await nextTick();if(!isCurrent())return;tableRef.value?.clearSelection();for(const row of rows.value)if(chosen.has(row.id))tableRef.value?.toggleRowSelection(row,true);return true}
 catch{if(isCurrent()&&!background)failed.value=true;return false}
 finally{if(current===epoch){loading.value=false;reading.value=false}}
}
const listBlocked=computed(()=>!!formRef.value?.isOpen||!!acidWash.value?.isOpen)
const {failed:refreshFailed}=useInventoryRefresh(context=>readList(true,context.isCurrent),listBlocked,{allowed:canReadList,reset:invalidateAccess},{paused:computed(()=>reading.value||deleting.value)})
function sizeChanged(){query.pageNum=1;load()}
function saved(added:boolean){if(!canReadList())return;if(added)query.pageNum=Math.max(1,Math.ceil((total.value+1)/query.pageSize));load()}
const currentRow=(row:WorkOrder)=>!!row.id&&rows.value.some(r=>r.id===row.id)
function edit(row?:WorkOrder,taskType:'PRODUCTION'|'RAW_PROCESSING'='PRODUCTION'){if(!canReadList()||!can(row?'metal:work-order:edit':'metal:work-order:add')||(row&&(!currentRow(row)||!orderEditable(row))))return;formRef.value?.open(row,taskType)}
function view(row:WorkOrder){if(canReadList()&&can('metal:work-order:query')&&currentRow(row))router.push({path:'/metal/work-order',query:{id:row.id}})}
function back(){router.push({path:'/metal/work-order'})}
async function remove(targets:WorkOrder[]){
 if(!canReadList()||!can('metal:work-order:remove')||deleting.value||!targets.length||targets.some(row=>!currentRow(row)||row.canDelete!==true))return
 const ids=targets.map(r=>r.id!),account=identity(),operation=++operationEpoch,path=route.fullPath
 // A separate ticket keeps ordinary list refreshes from invalidating a submitted mutation.
 const isCurrent=()=>operation===operationEpoch&&canReadList()&&can('metal:work-order:remove')&&identity()===account&&route.fullPath===path
 deleting.value=true
 try{
  await ElMessageBox.confirm('确认删除选中的'+ids.length+'张工单？','删除确认',{type:'warning',confirmButtonText:'确定',cancelButtonText:'取消'})
  if(!isCurrent())return
  await orderDelete(ids,isCurrent)
  if(!isCurrent())return
  ElMessage.success('删除成功')
  query.pageNum=Math.min(query.pageNum,Math.max(1,Math.ceil((total.value-ids.length)/query.pageSize)))
  load()
 }catch{}finally{if(isCurrent())deleting.value=false}
}
actionPermission.onChange(()=>{invalidateAccess();query.pageNum=1;load()})
watch(detailId,()=>{invalidateAccess();if(!detailId.value)load()},{flush:'sync'})
function checkSession(){if(!sessionCurrent())invalidateAccess()}
onMounted(()=>{window.addEventListener('focus',checkSession);window.addEventListener('storage',checkSession);document.addEventListener('visibilitychange',checkSession);load()})
onBeforeUnmount(()=>{disposed=true;invalidateAccess();window.removeEventListener('focus',checkSession);window.removeEventListener('storage',checkSession);document.removeEventListener('visibilitychange',checkSession)})
</script>
<style scoped>
.work-order-page{min-height:calc(var(--design-height,100vh) - var(--business-header-height,56px));background:var(--business-canvas);padding:16px 16px 24px;display:flex;flex-direction:column}.order-toolbar{display:flex;gap:8px;margin-bottom:16px}.order-toolbar .el-button{width:89px;height:35px;margin:0;font-size:14px}.blue{--el-button-bg-color:#577bef;--el-button-border-color:#577bef;--el-button-text-color:#fff}.orange{--el-button-bg-color:#ee9b58;--el-button-border-color:#ee9b58;--el-button-text-color:#fff}.red{--el-button-bg-color:#ee6658;--el-button-border-color:#ee6658;--el-button-text-color:#fff}.green{--el-button-bg-color:#39a052;--el-button-border-color:#39a052;--el-button-text-color:#fff}.order-toolbar .el-button:disabled,.row-button:disabled{background:var(--el-button-bg-color);border-color:var(--el-button-border-color);color:white;opacity:.6}.order-table{width:100%;font-size:14px;--el-table-header-bg-color:#f4f4f4;--el-table-border-color:#e8ebf0}.order-table :deep(th.el-table__cell){height:44px}.order-table :deep(.cell){white-space:pre-wrap;overflow-wrap:anywhere;line-height:18px;font-size:14px;padding:10px 5px}.order-table :deep(td.el-table__cell){padding:0}.order-table :deep(.row-button){height:25px;font-size:13px;padding:0 8px;border-radius:2px;margin:2px}.order-pagination{margin-top:16px;padding-top:0;display:flex;justify-content:center}.load-error{padding:15px;text-align:center}.audit-history{margin-top:20px;border-top:1px solid #e8ebf0;padding-top:15px;font-size:13px;line-height:2}

.order-table :deep(td.order-actions .cell){display:flex;align-items:center;justify-content:center;gap:4px;flex-wrap:nowrap;white-space:nowrap;padding:10px 8px}
.order-table :deep(td.order-actions .row-button){width:58px;height:25px;flex:0 0 58px;margin:0;padding:0}
.order-toolbar{flex-wrap:wrap}.order-toolbar .secondary-action{width:auto;min-width:116px}.work-order-page :deep(.el-table .cell),.work-order-page :deep(.el-table input){text-align:center}
@media(max-width:768px){.order-pagination :deep(.el-pagination){max-width:100%;flex-wrap:wrap;justify-content:center;row-gap:8px}}
</style>
