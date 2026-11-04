<template>
 <section v-loading="loading" class="detail-page">
  <div class="detail-heading"><el-button link @click="$emit('back')">← 查看详情</el-button><span> / 生产工单管理</span></div>
  <el-alert v-if="refreshFailed" title="自动更新失败，当前数据可能已过期，恢复连接后将重试。" type="warning" :closable="false"/>
  <template v-if="order">
   <div class="section-title">&gt;&gt;基本信息</div><div class="basic-card">
    <div>生产单据编号：{{order.orderNo}}</div><div>工厂名称：{{order.factoryName}}</div><div>客户名称：{{order.customer}}</div>
    <div v-for="field in customerFields" :key="field.key">{{field.label}}：{{customerText(field.key)}}</div>
    <div>产线：{{order.lineName}}</div><div>开单日期：{{order.orderDate}}</div><div>负责人：{{order.ownerName}}</div><div>任务类型：{{raw?'原料加工':'产品生产'}}</div><div>加工类型：{{processingTypes[order.processingType??'']??'历史未记录'}}</div>
    <div>工单状态：{{orderStatus[order.status??'']}}<span v-if="order.rejectReason">，原因：{{order.rejectReason}}</span></div><div>创建人：{{order.creatorName}}</div><div>创建时间：{{displayDateTime(order.createTime)}}</div>
   </div>
   <div class="detail-card product-list"><div class="section-title">&gt;&gt;{{raw?'原料加工明细':'产品清单'}}</div><el-table :data="order.items" border><el-table-column :label="raw?'物料编码':'产品编号'" min-width="130"><template #default="{row}">{{(row.materialSnapshot??row.snapshot)?.code??'历史未记录'}}</template></el-table-column><el-table-column :label="raw?'物料名称':'产品名称'" min-width="140"><template #default="{row}">{{(row.materialSnapshot??row.snapshot)?.name??'历史未记录'}}</template></el-table-column><el-table-column v-if="!raw" label="产品类型" min-width="120"><template #default="{row}">{{row.snapshot?.productType??row.snapshot?.category??'历史未记录'}}</template></el-table-column><el-table-column label="金属类型" min-width="110"><template #default="{row}">{{(row.materialSnapshot??row.snapshot)?.metalTypeName??'历史未记录'}}</template></el-table-column><el-table-column v-if="!raw" label="单件理论净重（g）" min-width="160"><template #default="{row}">{{decimalText(row.snapshot?.weight)}}</template></el-table-column><el-table-column v-if="!raw" label="产品数量（件）" prop="quantity" min-width="130"/><el-table-column v-if="raw" label="计划投入净重（g）" min-width="170"><template #default="{row}">{{decimalText(row.plannedInputWeight)}} <el-button v-if="has('metal:work-order:edit')" link type="primary" @click="openRawPlan(row)">变更计划</el-button></template></el-table-column></el-table></div>
   <div class="detail-card material-list"><div class="section-title">&gt;&gt;物料清单</div><p class="empty-note">按各工序有效原料领料累计，包含内部转序；新增投入及余额以整单重量总账为准，不把转序重复计入。</p><div v-if="materialsFailed" role="alert">物料清单加载失败 <el-button link type="primary" @click="loadMaterials">重试</el-button></div><el-table v-loading="materialsLoading" :data="materials" border :empty-text="materialsFailed?'加载失败，请重试':'暂无原材料领料记录'"><el-table-column label="物料编码" prop="code" min-width="140"/><el-table-column label="物料类型" prop="materialType" min-width="120"/><el-table-column label="规格型号" prop="specification" min-width="180"/><el-table-column label="物料净重（g）" prop="netWeight" :formatter="decimalColumn" min-width="150"/></el-table></div>
   <div class="detail-columns"><div class="detail-card progress"><div class="section-title">&gt;&gt;加工计划</div><el-table :data="allPlans" border><el-table-column :label="raw?'物料名称':'产品名称'" prop="productName" min-width="130"/><el-table-column label="工序名称" prop="processName" min-width="130"/><el-table-column label="阶段" min-width="120"><template #default="{row}">{{phaseName(row)}}</template></el-table-column><el-table-column label="执行进度" min-width="175"><template #default="{row}">{{row.actualEndTime?'已完成':row.actualStartTime?row.outputStage==='RAW'?'已投产，待结束闭环':'生产中':'暂无本单加工记录'}}</template></el-table-column><el-table-column v-for="field in timeFields" :key="field.key" :label="field.label" min-width="150"><template #default="{row}">{{displayDateTime(row[field.key])}}</template></el-table-column></el-table></div>
    <div v-if="canViewReports()" class="detail-card reports"><div class="section-title">&gt;&gt;损耗报工</div><p class="report-note">状态按精确损耗率判定，显示保留两位小数。</p><p v-if="reportsLoading" class="report-status" role="status">正在加载报工记录…</p><div v-if="reportState==='error'" class="report-error" role="alert">报工记录加载失败 <el-button link type="primary" @click="loadReports">重试</el-button></div><el-table v-loading="reportsLoading" :data="reports" border :empty-text="reportState==='success'?'暂无报工记录':' '"><el-table-column :label="raw?'物料名称':'产品名称'" prop="productName" min-width="130"/><el-table-column label="工序名称" prop="processName" min-width="120"/><el-table-column label="理论损耗（%）" min-width="150"><template #default="{row}">{{row.lossUnit==='PERCENT'?lossRateText(row.theoreticalLoss):decimalText(row.theoreticalLoss)+'（历史 g/件）'}}</template></el-table-column><el-table-column label="实际损耗（g）" prop="lossWeight" min-width="140" label-class-name="loss-weight-heading" :formatter="decimalColumn"/><el-table-column label="实际损耗率（%）" min-width="145"><template #default="{row}">{{lossRateText(row.lossRatePercent)}}<LossRateBasis :basis="row.lossRateBasis" :weight="row.lossRateDenominatorWeight"/></template></el-table-column><el-table-column label="状态" min-width="85"><template #default="{row}">{{row.status==='ABNORMAL'?'异常':'正常'}}</template></el-table-column><el-table-column label="时间" prop="settledTime" :formatter="dateTimeCell" min-width="150"/><el-table-column label="操作" width="160" align="center"><template #default="{row}"><div class="report-actions"><el-button type="primary" size="small" @click="openReport(row)">查看</el-button><el-button link type="primary" size="small" @click="openConsumption(row)">消耗追溯</el-button></div></template></el-table-column></el-table><p v-if="reportState==='success'&&!reports.length" class="empty-note">加工批次结算后自动生成报工记录</p></div>
   </div>
   <div v-if="order.reviews?.length" class="detail-card"><div class="section-title">操作记录</div><el-table :data="order.reviews" border><el-table-column prop="createTime" label="时间" :formatter="dateTimeCell"/><el-table-column prop="actorName" label="操作人"/><el-table-column label="操作"><template #default="{row}">{{reviewNames[row.action]||row.action}}</template></el-table-column><el-table-column prop="reason" label="说明"/></el-table></div>
   <WorkOrderResidualPanel v-if="order.id" :work-order-id="order.id"/>
   <WorkOrderWeightBalance v-if="order.id" :raw="raw" :work-order-id="order.id"/>
   <RouteSkipPermit v-if="!raw&&order.id&&has('metal:route-skip:configure')" :work-order-id="order.id" :items="order.items"/>
   <AutoSemiReleaseStatus v-if="!raw&&order.id" :work-order-id="order.id"/>
   <SemiFlowPanel v-if="order.id" :work-order-id="order.id" @changed="notifyInventoryChanged"/>
  </template>
  <div v-if="failed" class="detail-error">工单加载失败 <el-button link type="primary" @click="load">重试</el-button></div>
 <el-dialog :key="rawPlanDialogKey" v-model="rawPlanVisible" title="变更原料投入计划" width="min(520px,94vw)" :close-on-click-modal="false" :show-close="!rawPlanBusy&&!rawPlanPending" :close-on-press-escape="!rawPlanBusy&&!rawPlanPending"><el-form label-position="top" :disabled="rawPlanBusy||!!rawPlanPending"><el-form-item label="计划投入净重（g）"><BusinessDecimalInput v-model="rawPlanWeight"/></el-form-item><el-form-item label="变更原因"><el-input v-model="rawPlanReason" type="textarea" maxlength="500"/></el-form-item><el-alert v-if="rawPlanStale" title="数据已更新，请重新核对；当前计划草稿已保留，请关闭后重新打开核对。" type="warning" :closable="false"/><el-alert v-if="rawPlanPending" title="提交结果尚未确认，请重试原请求，勿重复登记。" type="warning" :closable="false"/><el-alert v-if="rawPlanError" :title="rawPlanError" type="error" :closable="false"/></el-form><template #footer><el-button :disabled="rawPlanBusy||!!rawPlanPending" @click="rawPlanVisible=false">取消</el-button><el-button type="primary" :loading="rawPlanBusy" :disabled="rawPlanStale&&!rawPlanPending" @click="saveRawPlan">{{rawPlanPending?'重试原提交':'保存计划变更'}}</el-button></template></el-dialog>
 <ProcessConsumptionDialog ref="consumptionDialog"/><WorkReportDialog ref="reportDialog" :work-order-id="props.id" @changed="loadReports"/>
 </section>
</template>
<script setup lang="ts">
import LossRateBasis from '../warehouse-common/LossRateBasis.vue'
import BusinessDecimalInput from '@/components/BusinessDecimalInput.vue'
import {createUUID} from '@/utils/uuid'
import {decimalError} from '@/utils/input-validation'
import {useInventoryRefresh,type InventoryRefreshContext} from '@/composables/useInventoryRefresh'
import {notifyInventoryChanged} from '@/utils/inventory-change'
import {onBeforeRouteLeave,onBeforeRouteUpdate} from 'vue-router'
import WorkOrderResidualPanel from './WorkOrderResidualPanel.vue'
import {decimalText,decimalColumn} from '@/utils/business-decimal'

import {getToken} from '@/utils/auth'
import {lossRateText} from '@/utils/report-metrics'
import { useActionPermission } from '@/composables/useActionPermission'
const actionPermission = useActionPermission()
import {displayDateTime,dateTimeCell} from '@/utils/display-date-time'
import {ref,computed,watch,onMounted,onBeforeUnmount} from 'vue'
import useUserStore from '@/store/modules/user'
const user=useUserStore(),has=(p:string)=>actionPermission.has(p)
import WorkOrderWeightBalance from './WorkOrderWeightBalance.vue'
import RouteSkipPermit from './RouteSkipPermit.vue'
import AutoSemiReleaseStatus from './AutoSemiReleaseStatus.vue'
import SemiFlowPanel from './SemiFlowPanel.vue'
import ProcessConsumptionDialog from './ProcessConsumptionDialog.vue'
import WorkReportDialog from '../warehouse-common/WorkReportDialog.vue'
import {type Row} from '@/api/metal/warehouse'
import {orderRawPlan,orderDetail,orderStatus,orderMaterials,orderReports,processingTypes} from '@/api/metal/work-order'
import type {WorkOrder,WorkOrderItem,CustomerSnapshot} from '@/api/metal/work-order'
const reviewNames:Record<string,string>={CREATE:'创建',EDIT:'修改',DELETE:'删除',SUBMIT:'旧流程：提交审核',APPROVE:'旧流程：审核通过',REJECT:'旧流程：驳回',RESUBMIT:'旧流程：重新提交'}
const props=defineProps<{id:string}>();defineEmits<{back:[]}>()
const reports=ref<Row[]>([]),reportDialog=ref<InstanceType<typeof WorkReportDialog>>()
const order=ref<WorkOrder>(),loading=ref(false),failed=ref(false)
const consumptionDialog=ref<InstanceType<typeof ProcessConsumptionDialog>>()
function canReadOrder(){return has('metal:work-order:query')&&!!user.token&&user.token===getToken()}
function canViewReports(){return canReadOrder()&&has('metal:work-report:query')}
const customerFields=[{key:'companyName',label:'客户公司名称'},{key:'address',label:'客户地址'},{key:'contactName',label:'联络人名称'},{key:'contactPhone',label:'联络人电话'},{key:'department',label:'部门（非必填）'}] as const
const timeFields=[{key:'startTime',label:'计划开始时间'},{key:'endTime',label:'计划结束时间'},{key:'actualStartTime',label:'实际开始时间'},{key:'actualEndTime',label:'实际结束时间'}]
const raw=computed(()=>order.value?.taskType==='RAW_PROCESSING')
const rawPlanVisible=ref(false),rawPlanBusy=ref(false),rawPlanWeight=ref(''),rawPlanReason=ref(''),rawPlanError=ref('')
const rawPlanDialogKey=ref(0),rawPlanStale=ref(false),rawPlanPending=ref<Parameters<typeof orderRawPlan>[1]>(),backgroundLoading=ref(false)
let rawPlanMutation=0
let rawPlanItem='',rawPlanRequest='',rawPlanBody='',rawPlanVersion=0,rawPlanWork=''
function openRawPlan(item:WorkOrderItem){if(!canReadOrder()||rawPlanBusy.value||!order.value?.id||!item.id||!has('metal:work-order:edit'))return;if(rawPlanPending.value){if(props.id===rawPlanWork)rawPlanVisible.value=true;return}rawPlanDialogKey.value++;rawPlanMutation++;rawPlanStale.value=false;rawPlanItem=item.id;rawPlanWork=order.value.id;rawPlanVersion=order.value.version??0;rawPlanWeight.value=item.plannedInputWeight??'';rawPlanReason.value='';rawPlanError.value='';rawPlanRequest='';rawPlanBody='';rawPlanVisible.value=true}
async function saveRawPlan(){if(!canReadOrder()||rawPlanBusy.value||!rawPlanVisible.value||rawPlanStale.value&&!rawPlanPending.value||!has('metal:work-order:edit')||props.id!==rawPlanWork)return;const error=decimalError(rawPlanWeight.value,'计划投入净重',{positive:true});if(error||!rawPlanReason.value.trim()){rawPlanError.value=error||'请填写变更原因';return}const body={version:rawPlanVersion,itemId:rawPlanItem,plannedInputWeight:rawPlanWeight.value,reason:rawPlanReason.value.trim()},serialized=JSON.stringify(body);if(!rawPlanPending.value&&serialized!==rawPlanBody){rawPlanBody=serialized;rawPlanRequest=createUUID()}const payload=rawPlanPending.value??{requestId:rawPlanRequest,...body},mutation=rawPlanMutation,session=user.token,account=String(user.id);const current=()=>!disposed&&mutation===rawPlanMutation&&canReadOrder()&&user.token===session&&String(user.id)===account&&props.id===rawPlanWork&&has('metal:work-order:edit');rawPlanBusy.value=true;try{await orderRawPlan(rawPlanWork,payload,current);if(current()){rawPlanPending.value=undefined;rawPlanVisible.value=false;notifyInventoryChanged()}}catch(e:any){if(current()){rawPlanError.value=e.message||'计划变更失败，请保留输入重试';if(typeof e.code!=='number')rawPlanPending.value=payload;else{rawPlanPending.value=undefined;if(/版本|刷新|重新加载/.test(rawPlanError.value))rawPlanStale.value=true}}}finally{if(current())rawPlanBusy.value=false}}
function phaseName(plan:{processRole?:string;outputStage?:string}){return plan.processRole==='SMELTING'?'融定':plan.outputStage==='RAW'?'原料前处理':plan.outputStage==='SEMI'?'半成品加工':plan.outputStage==='FINISHED'?'成品':'历史阶段未记录'}
const allPlans=computed(()=>order.value?.items.flatMap(item=>item.plans.map(plan=>({...plan,productName:(item.materialSnapshot??item.snapshot)?.name??'历史未记录'})))??[])
function customerText(key:keyof CustomerSnapshot){return !order.value?.customerSnapshot?'历史未记录':order.value.customerSnapshot[key]||'—'}
function openReport(row:Row){if(canViewReports())void reportDialog.value?.open(row.itemId,row.processId,row.processName,row.batchId)}
function openConsumption(row:Row){if(!canViewReports()){consumptionDialog.value?.close();return}consumptionDialog.value?.open({raw:raw.value||row.outputStage==='RAW',workOrderId:props.id,itemId:row.itemId,processId:row.processId,processName:row.processName,productLabel:row.productName??'当前产品明细'})}
let epoch=0,reportEpoch=0,materialEpoch=0,disposed=false
const materials=ref<Row[]>([]),materialsLoading=ref(false),materialsFailed=ref(false)
async function loadMaterials(){const work=props.id,ticket=++materialEpoch,detailRound=epoch,session=user.token,account=String(user.id);const current=()=>!disposed&&canReadOrder()&&order.value?.id===work&&props.id===work&&epoch===detailRound&&ticket===materialEpoch&&session===user.token&&account===String(user.id);if(!current())return;materialsLoading.value=true;materialsFailed.value=false;try{const response=await orderMaterials(work,current);if(current()){if(!Array.isArray(response.data))throw Error('物料响应无效');materials.value=response.data}}catch{if(current()){materials.value=[];materialsFailed.value=true}}finally{if(current())materialsLoading.value=false}}
const reportState=ref<'idle'|'loading'|'success'|'error'>('idle')
const reportsLoading=computed(()=>reportState.value==='loading')
function invalidateReports(){reportEpoch++;reports.value=[];reportState.value='idle'}
async function loadReports(){
 const work=props.id,detailRound=epoch
 if(disposed||!canViewReports()||order.value?.id!==work)return
 const ticket=++reportEpoch,session=user.token,account=String(user.id)
 const current=()=>!disposed&&canReadOrder()&&user.token===session&&String(user.id)===account&&ticket===reportEpoch&&detailRound===epoch&&work===props.id&&order.value?.id===work
 reportState.value='loading'
 try{
  const response=await orderReports(work,current)
  if(!current())return
  if(!Array.isArray(response.data))throw Error('报工汇总响应无效')
  reports.value=response.data
  reportState.value='success'
 }catch{
  if(!current())return
  reports.value=[]
  reportState.value='error'
 }
}
async function load(){
 if(disposed)return
 backgroundLoading.value=false
 if(!canReadOrder()){epoch++;invalidateReports();order.value=undefined;materialEpoch++;materials.value=[];materialsLoading.value=false;materialsFailed.value=false;loading.value=false;failed.value=false;return}
 const ticket=++epoch,work=props.id,session=user.token,account=String(user.id)
 const current=()=>!disposed&&canReadOrder()&&user.token===session&&String(user.id)===account&&ticket===epoch&&work===props.id
 invalidateReports();materialEpoch++;materials.value=[];materialsFailed.value=false;materialsLoading.value=false;loading.value=true;failed.value=false;order.value=undefined
 try{
  const response=await orderDetail(work,current)
  if(!current())return
  if(response.data?.id!==work)throw Error('工单详情响应不匹配')
  order.value=response.data
  void loadReports();void loadMaterials()
 }catch{if(current())failed.value=true}
 finally{if(current())loading.value=false}
}
async function backgroundRefresh(context:InventoryRefreshContext){
 const round=epoch,work=props.id,session=user.token,account=String(user.id),materialRound=materialEpoch,reportRound=reportEpoch
 const current=()=>context.isCurrent()&&!disposed&&round===epoch&&work===props.id&&canReadOrder()&&session===user.token&&account===String(user.id)&&!rawPlanVisible.value&&!rawPlanBusy.value
 if(!current())return
 backgroundLoading.value=true
 try{const [detail,materialResult,reportResult]=await Promise.all([orderDetail(work,current,false,true),orderMaterials(work,current,true),canViewReports()?orderReports(work,current,true):Promise.resolve({data:[] as Row[]})]);if(!current())return;if(detail.data?.id!==work)throw Error('工单详情响应不匹配');order.value=detail.data;if(materialRound===materialEpoch){materials.value=materialResult.data;materialsFailed.value=false}if(reportRound===reportEpoch){reports.value=reportResult.data;reportState.value='success'};failed.value=false}
 catch{if(current())return false}
 finally{if(round===epoch&&work===props.id&&session===user.token&&account===String(user.id))backgroundLoading.value=false}
}
async function probeRawPlan(context:InventoryRefreshContext){const round=rawPlanMutation,work=rawPlanWork;const current=()=>context.isCurrent()&&round===rawPlanMutation&&canReadOrder()&&rawPlanVisible.value&&!rawPlanBusy.value&&!rawPlanPending.value&&props.id===work;const result=await orderDetail(work,current,false,true);if(current()&&result.data.version!==rawPlanVersion)rawPlanStale.value=true}
const {failed:refreshFailed}=useInventoryRefresh(backgroundRefresh,rawPlanVisible,{allowed:canReadOrder,reset:()=>{rawPlanMutation++;rawPlanPending.value=undefined;rawPlanBusy.value=false;rawPlanVisible.value=false;epoch++;order.value=undefined;invalidateReports();materials.value=[]}},{paused:computed(()=>loading.value||backgroundLoading.value||materialsLoading.value||reportsLoading.value||rawPlanBusy.value||!!rawPlanPending.value),probe:probeRawPlan})
function invalidateAccess(){rawPlanDialogKey.value++;rawPlanMutation++;rawPlanPending.value=undefined;rawPlanBusy.value=false;rawPlanVisible.value=false;reportDialog.value?.close();consumptionDialog.value?.close();void load()}
function foreground(){if(!canReadOrder())invalidateAccess()}
actionPermission.onChange(invalidateAccess)
onMounted(()=>{window.addEventListener('focus',foreground);window.addEventListener('storage',foreground);document.addEventListener('visibilitychange',foreground)})
onBeforeRouteLeave(()=>rawPlanBusy.value||rawPlanPending.value?false:undefined);onBeforeRouteUpdate(()=>rawPlanBusy.value||rawPlanPending.value?false:undefined)
watch(()=>props.id,()=>{rawPlanDialogKey.value++;rawPlanMutation++;rawPlanPending.value=undefined;rawPlanBusy.value=false;rawPlanVisible.value=false;backgroundLoading.value=false;reportDialog.value?.close();consumptionDialog.value?.close();void load()},{immediate:true,flush:'sync'})
onBeforeUnmount(()=>{window.removeEventListener('focus',foreground);window.removeEventListener('storage',foreground);document.removeEventListener('visibilitychange',foreground);disposed=true;epoch++;invalidateReports();loading.value=false;reportDialog.value?.close();consumptionDialog.value?.close()})
</script>
<style scoped>
.detail-page{background:var(--business-canvas);min-height:calc(var(--design-height,100vh) - var(--business-header-height,56px));padding:0 16px 16px;color:#50545c;font-size:14px}.detail-heading{height:44px;display:flex;align-items:center;border-bottom:1px solid #e8ebf0}.section-title{padding:12px 8px}.basic-card{background:white;border:1px solid #e8ebf0;border-radius:8px;padding:16px;display:grid;grid-template-columns:repeat(3,1fr);gap:16px 20px}.detail-columns{display:grid;grid-auto-rows:auto;align-items:stretch;grid-template-columns:46% 54%;margin-top:10px;gap:8px;padding-right:8px}.detail-card{background:white;border:1px solid #e8ebf0;border-radius:8px;padding:0 12px 12px;min-height:0;height:auto;box-sizing:border-box}.detail-card :deep(.el-table){font-size:14px;--el-table-header-bg-color:#f4f4f4}.detail-card :deep(th){height:44px}.detail-card :deep(.cell){white-space:pre-wrap;overflow-wrap:anywhere}.progress :deep(.el-radio-group){margin-bottom:12px}.progress :deep(.el-tabs__header){margin-bottom:12px}.report-status{margin:0 0 10px;color:#606266;font-size:13px}.report-error{display:flex;align-items:center;gap:8px;padding:8px 0 12px;color:#c45656;font-size:13px}.empty-note{color:#909399;font-size:13px;margin-bottom:0}.detail-error{padding:30px;text-align:center}
.reports :deep(th.loss-weight-heading .cell){white-space:nowrap;overflow-wrap:normal}
.report-actions{display:flex;align-items:center;justify-content:center;gap:4px;flex-wrap:nowrap;white-space:nowrap}.report-actions :deep(.el-button){margin-left:0;flex-shrink:0;background:#577bef;border-color:#577bef;padding:5px 10px}
</style>

<style scoped>
.detail-columns{grid-template-columns:minmax(0,46fr) minmax(0,54fr)}.detail-card{min-width:0}.progress-heading{display:flex;align-items:center;gap:12px;flex-wrap:wrap;padding:10px 0}.progress-heading .section-title{padding:0 8px;flex-shrink:0}.progress-products{flex:1 1 180px;min-width:0}.progress-products :deep(.el-tabs__header){margin-bottom:0}.basic-card>div{min-width:0;overflow-wrap:anywhere}.detail-heading{flex-wrap:wrap;height:auto;min-height:44px}
@media(max-width:1100px){.detail-columns{grid-template-columns:minmax(0,1fr)}.basic-card{grid-template-columns:repeat(2,minmax(0,1fr))}}
@media(max-width:480px){.basic-card{grid-template-columns:minmax(0,1fr);padding:16px;gap:16px}.detail-page{padding:0 4px}.detail-columns{padding-right:0}.detail-card{padding:0 8px 12px}}
</style>

<style scoped>.metric-warning{display:block;font-size:12px;color:#a66b13;white-space:normal;overflow-wrap:anywhere;line-height:1.5}</style>

<style scoped>
.detail-columns{grid-template-columns:minmax(0,1fr)}.detail-card{margin-top:12px}.detail-card :deep(.el-table__body-wrapper){overflow-x:auto}.report-actions :deep(.el-button.is-link){background:transparent;border:0;color:#577bef}
</style>

<style scoped>.report-note{color:#687588;font-size:13px}</style>

<style scoped>
.detail-page :deep(.el-table .cell),.detail-page :deep(.el-table .el-input__inner){text-align:center}
</style>
