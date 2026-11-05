<template>
 <el-dialog v-model="visible" title="查看报工" class="work-report-dialog" width="min(612px,94vw)" :close-on-click-modal="false" :close-on-press-escape="!reversing&&!correctionVisible" :show-close="!reversing">
  <div class="report-content" v-loading="loading"><p class="process-name">工序名称：{{processName}}</p>
   <el-select v-if="reports.length>1" v-model="reportId" :disabled="loading||reversing" class="batch-select" fit-input-width @change="loadDetail"><el-option v-for="r in reports" :key="r.id" :value="r.id" :label="(r.batchNo||('历史批次'+r.batchId))+(r.segmentNo?' / 第'+r.segmentNo+'次交接':'')+' / '+(r.voided?'已作废':stateNames[r.status])"/></el-select>
   <template v-if="report"><div class="report-summary"><p>理论损耗：{{report.lossUnit==='PERCENT'?decimalText(report.theoreticalLoss)+'%':decimalText(report.theoreticalLoss)+'（历史 g/件）'}}</p><p>实际损耗：{{decimalText(report.lossWeight)}} g；实际损耗率：{{report.lossRatePercent==null?'—':decimalText(report.lossRatePercent)+'%'}}</p><LossRateBasis :basis="report.lossRateBasis" :weight="report.lossRateDenominatorWeight??report.netInputWeight"/><p v-if="report.handoverDifference!=null">加工称产出 {{decimalText(report.processorOutputWeight)}} g，余料 {{decimalText(report.processorReturnedWeight)}} g；库管实收 {{decimalText(report.keeperReceivedWeight)}} g；交接差额 {{decimalText(report.handoverDifference)}} g（正数为短少）。交接差额不计入加工损耗。</p><p>状态：{{report.voided?'已作废':report.status==='ABNORMAL'?'异常':'正常'}}；结算时间：{{displayDateTime(report.createTime)}}</p></div><p v-if="report.taskType==='RAW_PROCESSING'">原料加工按净重计算损耗。</p><p v-else-if="noPieces">本次仅处理剩余在制余料，没有新增完成件数；件均损耗不适用。</p><p v-else-if="reportWeight">按重量管理；参考件数不作为完成量，件均损耗和件均预警不适用。</p><p v-else>{{report.handoverFlowId?'本次交接完成：':'本批完成：'}}{{report.completedQuantity}} 件；同时保留实测净重。</p><el-table :data="report.materials" border class="material-report"><el-table-column label="物料编码" prop="code" width="98"/><el-table-column label="物料名称" prop="name" width="98"/><el-table-column label="分摊损耗(g)" prop="lossWeight" width="116" :formatter="decimalColumn"/><el-table-column v-if="!reportWeight&&!noPieces" label="平均损耗" prop="averageLoss" :formatter="decimalColumn"/></el-table>
    <el-button v-show="false" link type="primary" @click="emit('consumption',String(report.processId));visible=false">查看工序消耗明细</el-button><el-collapse v-show="false" class="report-evidence"><el-collapse-item title="查看批次与领还料依据" name="evidence"><p>领料净重：{{decimalText(report.issuedWeight)}}g；原料还库净重：{{report.rawReturnedWeight??'—'}}g</p><p>半成品／成品产出净重：{{decimalText(report.outputWeight??'—')}}g；完成：{{report.completedQuantity}}件</p><p>损耗：{{decimalText(report.lossWeight)}}g；平均：{{decimalText(report.averageLoss)}}g/件；阈值：{{decimalText(report.thresholdValue)}}{{report.lossUnit==='PERCENT'?'%':'g/件（历史）'}}</p><p>损耗＝领料净重－原料还库净重－半成品还库净重－加工余料还库净重－半成品／成品产出净重；按领用净重比例分摊</p><p v-if="report.voided">报工已作废，拆分重量不展示</p><el-table :data="report.orders" border><el-table-column label="来源单据" prop="orderNo"/><el-table-column label="类型"><template #default="{row}">{{orderTypeNames[row.type]}}</template></el-table-column><el-table-column label="状态"><template #default="{row}">{{stateNames[row.status]}}</template></el-table-column></el-table><el-button v-if="canReverse&&!report.voided" type="danger" :disabled="loading" :loading="reversing" @click="reverse">{{report.handoverFlowId?'本次交接纠错（冲销）':'批次纠错（冲销）'}}</el-button></el-collapse-item></el-collapse>
   </template>
  </div><template #footer><el-button class="cancel-button" :disabled="reversing" @click="visible=false">取消</el-button><el-button type="primary" :disabled="reversing" @click="visible=false">确认</el-button></template>
 </el-dialog>
 <el-dialog v-model="correctionVisible" title="批次纠错" width="min(480px,94vw)" append-to-body :close-on-click-modal="false" :close-on-press-escape="!reversing" :show-close="!reversing">
  <p>批次编号：{{correctionBatchNo}}</p>
  <el-form label-position="top"><el-form-item label="冲销原因" :error="correctionError" :rules="[requiredRule('冲销原因',true,false),...([])]"><el-input v-model="correctionReason" type="textarea" :rows="3" maxlength="500" show-word-limit :disabled="reversing" placeholder="填写整体冲销原因"/></el-form-item></el-form>
  <template #footer><el-button :disabled="reversing" @click="cancelCorrection">取消</el-button><el-button v-if="canReverse" type="primary" :loading="reversing" :disabled="needsReload" @click="confirmCorrection">确定</el-button></template>
 </el-dialog>
</template>
<script setup lang="ts">
import {createUUID} from '@/utils/uuid'
import LossRateBasis from './LossRateBasis.vue'
import {decimalText,decimalColumn,businessValue} from '@/utils/business-decimal'

import {displayDateTime} from '@/utils/display-date-time'
import {requiredRule} from '@/utils/input-validation'
import { useActionPermission } from '@/composables/useActionPermission'
const actionPermission = useActionPermission()
import{ref,computed,watch,onBeforeUnmount}from'vue'
import{ElMessage}from'element-plus'
import useUserStore from '@/store/modules/user'
import {getToken} from '@/utils/auth'
import{stepReports,warehouseDetail,warehouseAction,warehouseFlowAction,stateNames,orderTypeNames,type Row}from'@/api/metal/warehouse'
const props=defineProps<{workOrderId:string}>()
const emit=defineEmits(['changed','consumption']),visible=ref(false),reportId=ref(''),processName=ref(''),reports=ref<Row[]>([]),report=ref<Row>(),user=useUserStore(),canReverse=computed(()=>actionPermission.has(report.value?.handoverFlowId?'metal:warehouse-order:reverse':'metal:process-batch:reverse'))
const loading=ref(false),reversing=ref(false)
const reportWeight=computed(()=>report.value?.taskType==='RAW_PROCESSING'||report.value?.outputStage==='RAW'&&Number(report.value?.measurementPolicyVersion??0)<3&&report.value?.effectiveMeasurementMode!=='PIECE'||(report.value?.effectiveMeasurementMode??report.value?.measurementMode)==='WEIGHT')
const noPieces=computed(()=>!!report.value?.handoverFlowId&&Number(report.value.completedQuantity)===0&&!reportWeight.value)
const correctionVisible=ref(false),correctionReason=ref(''),correctionError=ref(''),correctionBatchNo=ref(''),needsReload=ref(false)
const identity=()=>JSON.stringify([user.token,String(user.id),[...user.permissions].sort()]);let openedIdentity=''
const sessionCurrent=()=>user.sessionReady&&!!user.token&&user.token===getToken()&&openedIdentity===identity()
let detailEpoch=0,openEpoch=0,operationEpoch=0,disposed=false
interface CorrectionContext{ticket:number;view:number;workOrderId:string;reportId:string;batchId:string;version:number;flowId?:string;flowVersion?:number;requestId:string}
let operation:CorrectionContext|undefined
const isViewCurrent=(view:number,work:string)=>!disposed&&sessionCurrent()&&view===openEpoch&&work===props.workOrderId
function correctionCurrent(context:CorrectionContext){return operation===context&&context.ticket===operationEpoch&&isViewCurrent(context.view,context.workOrderId)&&visible.value&&reportId.value===context.reportId&&String(report.value?.id)===context.reportId&&String(report.value?.batchId)===context.batchId&&Number(report.value?.batchVersion)===context.version&&!report.value?.voided}
function invalidateCorrection(){operationEpoch++;operation=undefined;correctionVisible.value=false;reversing.value=false;correctionReason.value='';correctionError.value='';correctionBatchNo.value='';needsReload.value=false}
function close(){openEpoch++;detailEpoch++;invalidateCorrection();visible.value=false;report.value=undefined;reports.value=[];reportId.value='';loading.value=false}
function cancelCorrection(){if(!reversing.value)invalidateCorrection()}
async function open(itemId:string,processId:string,name:string,batchId?:string){if(disposed||reversing.value||!user.sessionReady||!user.token||user.token!==getToken())return;close();openedIdentity=identity();const current=++openEpoch,work=props.workOrderId;loading.value=true;processName.value=name;try{const response=await stepReports(itemId,processId);if(!isViewCurrent(current,work))return;reports.value=response.data;const selected=batchId?reports.value.find(r=>String(r.batchId)===batchId):undefined;if(batchId&&!selected)throw Error('当前批次报工不存在，请刷新');reportId.value=batchId?selected!.id:(reports.value.find(r=>!r.voided)?.id||reports.value[0]?.id||'');if(reportId.value)await loadDetail();if(isViewCurrent(current,work))visible.value=true}catch{if(isViewCurrent(current,work))ElMessage.error('报工加载失败，请重新打开重试')}finally{if(isViewCurrent(current,work))loading.value=false}}
async function loadDetail(){if(reversing.value||disposed)return;const current=++detailEpoch,id=reportId.value,view=openEpoch,work=props.workOrderId;invalidateCorrection();loading.value=true;report.value=undefined;try{const response=await warehouseDetail('work-report',id);if(isViewCurrent(view,work)&&current===detailEpoch&&id===reportId.value)report.value=response.data}finally{if(isViewCurrent(view,work)&&current===detailEpoch)loading.value=false}}
async function reverse(){
 if(disposed||!sessionCurrent()||!visible.value||correctionVisible.value||reversing.value||loading.value||!canReverse.value||!report.value||report.value.voided||String(report.value.id)!==reportId.value)return
 const target=report.value,version=Number(target.batchVersion)
 if(!Number.isInteger(version)||version<0){ElMessage.warning('请重新加载报工后再进行纠错');return}
 invalidateCorrection()
 operation={ticket:operationEpoch,view:openEpoch,workOrderId:props.workOrderId,reportId:reportId.value,batchId:String(target.batchId),version,flowId:target.handoverFlowId,flowVersion:target.handoverVersion,requestId:createUUID()}
 correctionBatchNo.value=target.batchNo||reports.value.find(r=>String(r.id)===reportId.value)?.batchNo||String(target.batchId)
 correctionVisible.value=true
}
async function confirmCorrection(){
 const context=operation
 if(reversing.value||!correctionVisible.value||needsReload.value||!context)return
 if(!correctionCurrent(context)){invalidateCorrection();return}
 const reason=correctionReason.value.trim()
 if(!reason){correctionError.value='请填写原因';return}
 if(reason.length>500){correctionError.value='原因最多500字';return}
 correctionError.value='';reversing.value=true
 try{
  if(context.flowId)await warehouseFlowAction(context.flowId+'/reverse',{requestId:context.requestId,version:context.flowVersion,reason},()=>correctionCurrent(context))
  else await warehouseAction('process-batch',context.batchId,'reverse',{version:context.version,reason},()=>correctionCurrent(context))
  if(!correctionCurrent(context))return
  close();ElMessage.success('批次已冲销，原报工保留作废记录');emit('changed')
 }catch(error:any){
  if(!correctionCurrent(context))return
  needsReload.value=/版本|已被修改|已修改|刷新/.test(String(error?.message??error))
  correctionError.value=needsReload.value?'批次已变化，请关闭并重新加载报工后再试':'冲销失败，原因已保留，请重试'
 }finally{if(sessionCurrent()&&operation===context&&context.ticket===operationEpoch)reversing.value=false}
}
watch(visible,value=>{if(!value)close()},{flush:'sync'})
watch(correctionVisible,value=>{if(!value&&operation&&!reversing.value)invalidateCorrection()},{flush:'sync'})
watch(()=>props.workOrderId,close,{flush:'sync'})
actionPermission.onChange(close)
onBeforeUnmount(()=>{disposed=true;close()})
defineExpose({open,close})
</script>
<style scoped>
.report-content{padding:0 28px}.process-name{font-weight:600;margin:6px 0 32px}.batch-select{width:100%;margin-bottom:16px}.material-report :deep(th){height:52px;background:#f4f4f4}.material-report :deep(td){height:44px}.material-report :deep(.cell){text-align:center;overflow-wrap:anywhere}.report-evidence{margin-top:16px}.report-evidence :deep(.el-collapse-item__header){font-size:12px;color:#909399}.report-evidence .el-button{margin-top:12px}:deep(.el-dialog__footer){display:flex;justify-content:flex-end;gap:36px;padding:6px 30px 10px}:deep(.el-dialog__footer .el-button){width:88px;height:34px;margin-left:0}:deep(.el-dialog__footer .el-button--primary){background:#577bef;border-color:#577bef}.cancel-button{color:#577bef;border-color:#577bef}
</style>
