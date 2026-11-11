<template>
 <el-dialog v-model="reviewOpen" title="单据复验" width="min(1400px,96vw)" class="storage-dialog review-dialog" :before-close="closeReview" :close-on-click-modal="false">
  <OperationStatus :operation="operation"/>
  <el-form v-if="review" :disabled="readonly||operation.busy.value||!!operation.pending.value" label-position="top">
   <div class="review-heading"><strong>{{review.number}}</strong><el-tag>{{typeNames[review.type]}}</el-tag></div>
   <p v-if="review.type==='REVERSAL'">冲销原单据：{{review.reverseDocumentNumber||review.reverseDocumentId}} · {{review.reason}}</p>
   <el-alert v-if="!['ADJUSTMENT','OWNERSHIP_RETURN','REVERSAL'].includes(review.type)&&review.lines.some((row:any)=>!['WEIGHT','PIECE'].includes(row.measurementMode))" title="明细计量方式缺失，请重新加载并核对配置" type="warning" :closable="false"/>
   <el-table :data="review.lines" row-key="id" :expand-row-keys="expandedReviewRows" @expand-change="(_row:any,rows:any[])=>expandedReviewRows=rows.map(r=>String(r.id))" border class="review-lines" :row-class-name="({row}:any)=>row.allocations?.length?'':'no-review-sources'">
    <el-table-column v-if="review.lines.some((row:any)=>row.allocations?.length)" type="expand" width="44"><template #default="{row}">
     <div v-if="row.allocations?.length" class="review-sources"><h3>来源料权与实收核对</h3>
      <el-table :data="row.allocations" border>
       <el-table-column label="来源料权" min-width="160"><template #default="{row:a}">{{a.ownerName||(String(a.ownerId)==='0'?'工厂自有':`客户 #${a.ownerId}`)}}</template></el-table-column>
       <el-table-column label="发出重量（g）" min-width="140"><template #default="{row:a}"><el-input v-if="multipleSources(row)&&row.differenceMode==='LOSS'" v-model="a.weight" inputmode="decimal"/><span v-else>{{decimalText(sourceValue(row,a,false))}}</span></template></el-table-column>
       <el-table-column label="发出件数" min-width="100"><template #default="{row:a}"><template v-if="row.measurementMode==='PIECE'"><el-input v-if="multipleSources(row)&&row.differenceMode==='LOSS'" v-model="a.quantity" inputmode="numeric"/><span v-else>{{sourceValue(row,a,false,true)??"—"}}</span></template><span v-else>—</span></template></el-table-column>
       <el-table-column label="实际接收重量（g）" min-width="155"><template #default="{row:a}"><el-input v-if="multipleSources(row)" v-model="a.receivedWeight" inputmode="decimal"/><span v-else>{{decimalText(sourceValue(row,a,true))}}</span></template></el-table-column>
       <el-table-column label="实际接收件数" min-width="120"><template #default="{row:a}"><template v-if="row.measurementMode==='PIECE'"><el-input v-if="multipleSources(row)" v-model="a.receivedQuantity" inputmode="numeric"/><span v-else>{{sourceValue(row,a,true,true)??"—"}}</span></template><span v-else>—</span></template></el-table-column>
       <el-table-column label="差异承担" min-width="130"><template #default="{row:a}"><el-select v-if="multipleSources(row)&&hasLoss(row,a)" v-model="a.lossBearer"><el-option label="客户" value="CUSTOMER"/><el-option label="工厂" value="FACTORY"/></el-select><span v-else>—</span></template></el-table-column>
      </el-table>
     </div>
    </template></el-table-column>
    <AdjustmentColumns v-if="review.type==='ADJUSTMENT'" :lines="review.lines"/><template v-else>
    <el-table-column label="物料" min-width="165"><template #default="{row}"><div class="review-material"><strong>{{row.name||row.code||'—'}}</strong><span v-if="row.code&&row.code!==row.name">{{row.code}}</span></div></template></el-table-column>
    <el-table-column v-if="review.type==='OWNERSHIP_RETURN'" prop="ownerId" label="料权方ID（0为工厂）" min-width="150"/>
    <el-table-column v-if="review.type==='OWNERSHIP_RETURN'" label="归还原欠料" min-width="180"><template #default="{row}"><div v-for="d in row.debtAllocations||[]" :key="d.debtId">原债 {{d.debtId}}：{{d.weight}} g / {{d.quantity||0}} 件</div></template></el-table-column>
    <el-table-column v-if="review.type==='RAW_RETURN'" label="工厂料实测归还重量（g）" min-width="180"><template #default="{row}"><el-input v-if="row.measurementMode==='PIECE'" v-model="row.factoryWeight" placeholder="混合料权须填写实测份额"/><span v-else>—</span></template></el-table-column>
    <el-table-column v-if="review.type==='PRODUCT_IN'&&review.lines.some((row:any)=>row.measurementMode==='WEIGHT')" label="参考完工件数" min-width="125"><template #default="{row}">{{row.referenceQuantity??'—'}}</template></el-table-column>
    <el-table-column label="申报件数" min-width="105"><template #default="{row}">{{row.measurementMode==='PIECE'?row.quantity:'—'}}</template></el-table-column>
    <el-table-column label="申报净重（g）" min-width="130"><template #default="{row}">{{decimalText(row.netWeight)}}</template></el-table-column>
    <template v-if="!['ADJUSTMENT','OWNERSHIP_RETURN','REVERSAL'].includes(review.type)">
     <el-table-column label="实际净重（g）" min-width="145"><template #default="{row}"><span v-if="multipleSources(row)">{{decimalText(actualTotal(row))}}</span><el-input v-else v-model="row.actualNetWeight" inputmode="decimal"/></template></el-table-column>
     <el-table-column label="实际件数" min-width="115"><template #default="{row}"><template v-if="row.measurementMode==='PIECE'"><span v-if="multipleSources(row)">{{actualTotal(row,true,true)||"—"}}</span><el-input v-else v-model="row.actualQuantity" inputmode="numeric"/></template><span v-else>—</span></template></el-table-column>
     <el-table-column label="差异处理" min-width="140"><template #default="{row}"><el-select v-model="row.differenceMode"><el-option label="重称修正" value="REWEIGH"/><el-option label="交接损失" value="LOSS"/></el-select></template></el-table-column>
     <el-table-column label="损失承担" min-width="125"><template #default="{row}"><span v-if="multipleSources(row)">{{row.allocations.some((a:any)=>hasLoss(row,a))?'按来源分配':'—'}}</span><el-select v-else-if="hasLoss(row)" v-model="row.lossBearer"><el-option label="客户" value="CUSTOMER"/><el-option label="工厂" value="FACTORY"/></el-select><span v-else>—</span></template></el-table-column>
     <el-table-column label="本行差异原因" min-width="160"><template #default="{row}"><el-input v-model="row.differenceReason" maxlength="500"/></template></el-table-column>
    </template>
    </template>
   </el-table>
   <el-form-item label="整单复验说明" class="review-reason"><el-input v-model="review.reason" type="textarea" maxlength="500" :rows="3"/></el-form-item>
  </el-form>
  <template #footer><el-button :disabled="operation.busy.value||!!operation.pending.value" @click="closeReview()">取消</el-button><el-button v-if="!readonly" :disabled="operation.busy.value||!!operation.pending.value" type="danger" @click="finishReview(false)">退回</el-button><el-button v-if="!readonly" :loading="operation.busy.value" :disabled="!!operation.pending.value" type="primary" @click="finishReview(true)">确认过账</el-button></template>
 </el-dialog>
</template>
<script setup lang="ts">
import AdjustmentColumns from './AdjustmentColumns.vue'
import {actualTotal,sourceValue,multipleSources,hasLoss,reviewLines} from './review-values';import {decimalText} from '@/utils/business-decimal';import {ref,watch,onBeforeUnmount} from 'vue';import {ElMessageBox,ElMessage} from 'element-plus';import request from '@/utils/request';import {useStorage,useOperation,message,type Row} from './client';import OperationStatus from './OperationStatus.vue';import {typeNames} from '../storage-orders/model'
const emit=defineEmits<{saved:[]}>(),{state,user,context}=useStorage(),reviewOpen=ref(false),readonly=ref(false),review=ref<Row>();let initialReview='',epoch=0
const expandedReviewRows=ref<string[]>([])
const operation=useOperation('/metal/storage-orders',()=>JSON.stringify([context(),epoch,reviewOpen.value]),()=>{reviewOpen.value=false;window.dispatchEvent(new Event('storage-documents-changed'));emit('saved');ElMessage.success('复验已处理')})
async function open(id:string,taskId?:string){if(operation.pending.value||operation.busy.value)return;const current=context(),round=++epoch;try{const response:any=await request({url:taskId?'/metal/storage-review-tasks/'+taskId:'/metal/storage-orders/'+id,params:{factoryId:state.factoryId},quiet:true});if(current!==context()||round!==epoch)return;const data=taskId?response.data.document:response.data;readonly.value=!!taskId&&String(data.reviewerId)!==String(user.id);if(!readonly.value&&(data.status!=='PENDING_REVIEW'||String(data.reviewerId)!==String(user.id))){ElMessage.warning('该单据不属于当前账号的待复验任务');return}review.value={...data,lines:(data.lines||[]).map((l:any)=>({...l,actualNetWeight:l.actualNetWeight??l.netWeight,actualQuantity:l.actualQuantity??l.quantity,differenceMode:'REWEIGH',differenceReason:'',allocations:l.allocations?.map((a:any)=>({...a,receivedWeight:a.weight,receivedQuantity:a.quantity}))}))};expandedReviewRows.value=[];initialReview=JSON.stringify(review.value);operation.clear();reviewOpen.value=true}catch(e){ElMessage.error(message(e))}}
async function closeReview(done?:()=>void){if(operation.pending.value||operation.busy.value)return;if(reviewOpen.value&&JSON.stringify(review.value)!==initialReview)try{await ElMessageBox.confirm('放弃未提交的复验内容？','确认关闭')}catch{return}reviewOpen.value=false;done?.()}
async function finishReview(pass:boolean){if(!review.value||readonly.value)return;const r=review.value;if(!pass&&!String(r.reason||'').trim()){operation.error.value='请填写退回原因';return}try{const lines=pass?reviewLines(r.lines||[],r.type):r.lines;await operation.send(`/metal/storage-orders/${r.id}/${pass?'review':'return'}`,{factoryId:state.factoryId,version:r.version,reviewRound:r.reviewRound,lines,reason:r.reason})}catch(e){operation.error.value=message(e)}}
async function requestClose(){await closeReview();return !reviewOpen.value}
onBeforeUnmount(()=>{epoch++;reviewOpen.value=false});watch(()=>context(),()=>{epoch++;reviewOpen.value=false});defineExpose({open,requestClose})
</script>

<style scoped>
.review-lines :deep(.no-review-sources .el-table__expand-icon){display:none}.review-heading{display:flex;align-items:center;gap:12px;margin:0 0 16px;color:#303133}.review-heading strong{font-size:15px;font-weight:600}.review-lines :deep(.cell){word-break:normal;overflow-wrap:anywhere;padding:0 12px}.review-lines :deep(.el-table__cell){padding:12px 0}.review-material{display:flex;flex-direction:column;gap:5px}.review-material strong{font-weight:500}.review-material span{color:#909399;font-size:12px}.review-sources{padding:16px;background:#f8fafc}.review-sources h3{margin:0 0 12px;padding:0;border:0;font-size:14px;font-weight:600}.review-reason{margin:18px 0 0;padding-left:0}.review-reason :deep(.el-form-item__label){font-weight:600}.review-lines :deep(.el-input__inner){text-align:left}
</style>
