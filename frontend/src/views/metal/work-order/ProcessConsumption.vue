<template>
 <div class="process-consumption" data-testid="process-consumption">
  <div class="consumption-heading"><strong>工序消耗明细</strong><el-button size="small" :loading="loading" @click="load">{{batchScope==='SETTLED'?'刷新':'刷新全部'}}</el-button></div>
  <p class="product-context">{{productLabel}}</p><p v-if="processMeta">当前工序：第{{processMeta.sequenceNo}}道·{{processMeta.processName}}</p><p v-if="batchScope==='SETTLED'" class="consumption-note">仅统计已结算批次</p>
  <p class="consumption-note">实际投入＝领料－原料还库－半成品还库－加工余料还库；损耗＝实际投入－产出。<template v-if="batchScope!=='SETTLED'">未结算差额仅供暂算，</template>各工序投入不能相加作为整单消耗。</p>
  <div v-if="batchScope!=='SETTLED'" class="consumption-options"><el-switch v-model="includeVoided" aria-label="包含已冲销批次" active-text="包含已冲销批次（不计入汇总）"/></div>
  <p v-if="loading" role="status">正在加载工序消耗…</p>
  <div v-if="failed" role="alert">工序消耗加载失败 <el-button link type="primary" @click="load">重试</el-button></div>
  <el-alert v-for="warning in warnings" :key="warning" :title="warning" type="warning" :closable="false"/>
  <el-table ref="table" v-loading="loading" :data="rows" row-key="processId" border class="consumption-steps" :empty-text="failed?'加载失败，请重试':loading?' ':batchScope==='SETTLED'?'当前工序暂无已结算批次':'暂无工序快照'" @expand-change="expand">
   <el-table-column v-if="canDrill" type="expand" width="45"><template #default="{row}"><ConsumptionDocuments v-if="expanded.includes(row.processId)" :key="generation+':'+row.processId" level="batches" :context="{workOrderId,itemId,processId:row.processId}" :title="productLabel+' / 第'+row.sequenceNo+'道·'+row.processName" :include-voided="includeVoided" :batch-scope="batchScope" :hide-report-action="batchScope==='SETTLED'" @report="(batchId:string)=>emit('report',row.processId,row.processName,batchId)"/></template></el-table-column>
   <el-table-column prop="sequenceNo" label="序号" width="62"/>
   <el-table-column prop="processName" label="工序" min-width="130"/>
   <el-table-column v-for="c in columns" :key="c.key" :prop="c.key" :label="c.label" :min-width="135" align="right"><template #default="{row}">{{row[c.key]??'—'}}</template></el-table-column>
   <el-table-column label="损耗(g)" min-width="190" align="right"><template #default="{row}"><div>已结算：{{row.settledLossWeight??'—'}}</div><div v-if="batchScope!=='SETTLED'&&row.openBatchCount">暂算差额：{{row.provisionalDifferenceWeight??'—'}}</div></template></el-table-column>
   <el-table-column prop="settledCompletedQuantity" label="结算完成(件)" min-width="115" align="right"><template #default="{row}">{{row.settledCompletedQuantity??'—'}}</template></el-table-column>
   <el-table-column prop="batchCount" label="有效批次" width="90" align="right"/>
   <el-table-column label="状态" min-width="155"><template #default="{row}"><el-tag :type="row.state==='REVIEW'?'warning':row.state==='SETTLED'?'success':'info'">{{consumptionStates[row.state]||row.state}}</el-tag><el-tag v-if="row.reportStatus" :type="row.reportStatus==='ABNORMAL'?'danger':'success'">{{row.reportStatus==='ABNORMAL'?'已结算批次含异常':'已结算批次正常'}}</el-tag><p v-for="w in row.warnings" :key="w">{{w}}</p></template></el-table-column>
  </el-table>
  <small v-if="readAt">读取时间：{{displayDateTime(readAt)}}；登记或冲销后请刷新全部。</small>
 </div>
</template>
<script setup lang="ts">
import { useActionPermission } from '@/composables/useActionPermission'
const actionPermission = useActionPermission()
import {ref,computed,watch,onBeforeUnmount,nextTick} from 'vue'
import useUserStore from '@/store/modules/user'
import {processConsumption,consumptionStates,type Row} from '@/api/metal/warehouse'
import {displayDateTime} from '@/utils/display-date-time'
import ConsumptionDocuments from './ConsumptionDocuments.vue'
const props=withDefaults(defineProps<{workOrderId:string;itemId:string;productLabel:string;processId?:string;batchScope?:'ALL'|'SETTLED'}>(),{batchScope:'ALL'}),emit=defineEmits<{report:[processId:string,name:string,batchId?:string]}>(),user=useUserStore()
const canDrill=computed(()=>actionPermission.hasAny(['metal:work-order:query','metal:work-report:query']))
const processMeta=ref<Row>();const table=ref<any>();const rows=ref<Row[]>([]),warnings=ref<string[]>([]),readAt=ref(''),loading=ref(false),failed=ref(false),includeVoided=ref(false),expanded=ref<string[]>([]),generation=ref(0)
const columns=[{key:'rawIssuedWeight',label:'原料领用(g)'},{key:'semiIssuedWeight',label:'半成品领用(g)'},{key:'issuedWeight',label:'领料合计(g)'},{key:'rawReturnedWeight',label:'原料还库(g)'},{key:'semiReturnedWeight',label:'半成品还库(g)'},{key:'processRawReturnedWeight',label:'加工余料还库(g)'},{key:'outputWeight',label:'产出(g)'},{key:'netInputWeight',label:'实际投入(g)'}]
let ticket=0,disposed=false
const signature=()=>JSON.stringify([props.workOrderId,props.itemId,props.processId,props.batchScope])
async function load(){
 const id=++ticket,key=signature(),work=props.workOrderId,item=props.itemId,process=props.processId,scope=props.batchScope
 const current=()=>!disposed&&id===ticket&&key===signature()
 rows.value=[];warnings.value=[];processMeta.value=undefined;expanded.value=[];generation.value++;failed.value=false;loading.value=true;readAt.value=''
 try{
  const {data}=await processConsumption('steps',{workOrderId:work,itemId:item,...(process?{processId:process}:{}),...(scope==='SETTLED'?{batchScope:scope}:{})},current)
  if(!current())return
  if(data?.workOrderId!==work||data?.itemId!==item||!Array.isArray(data.rows)||(process&&(data.processId!==process||data.rows.some((r:Row)=>r.processId!==process)))||(scope==='SETTLED'&&data.batchScope!=='SETTLED'))throw Error('工序消耗上下文不匹配')
  processMeta.value=process?data.rows[0]:undefined
  rows.value=scope==='SETTLED'?data.rows.filter((r:Row)=>Number(r.batchCount)>0):data.rows
  warnings.value=data.warnings||[];readAt.value=data.readAt||''
  if(process){await nextTick();if(current()&&rows.value[0])table.value?.toggleRowExpansion(rows.value[0],true)}
 }catch{if(current())failed.value=true}finally{if(current())loading.value=false}
}
function expand(_row:Row,opened:Row[]){expanded.value=opened.map(r=>r.processId)}
watch(signature,()=>void load(),{immediate:true,flush:'sync'})
onBeforeUnmount(()=>{disposed=true;ticket++})
function showProcess(id:string){const row=rows.value.find(r=>r.processId===id);if(row){expanded.value=[...new Set([...expanded.value,id])];table.value?.toggleRowExpansion(row,true);table.value?.$el?.scrollIntoView({behavior:'smooth',block:'nearest'})}}
defineExpose({refresh:load,showProcess})
</script>
<style scoped>
.process-consumption{min-width:0;padding-bottom:14px}.consumption-heading{display:flex;flex-wrap:wrap;align-items:center;justify-content:space-between;gap:12px;padding:12px 0}.product-context{font-weight:500;overflow-wrap:anywhere}.consumption-note{color:#687588;font-size:13px;line-height:1.7}.consumption-options{margin:8px 0}.consumption-options :deep(.el-switch){max-width:100%;height:auto;align-items:flex-start}.consumption-options :deep(.el-switch__label){height:auto;white-space:normal;line-height:1.6;min-width:0}.consumption-options :deep(.el-switch__label span){white-space:normal;overflow-wrap:anywhere}.consumption-options :deep(.el-switch__core){flex-shrink:0}.process-consumption :deep(.el-table){width:100%;font-size:13px}.process-consumption :deep(.el-table__expanded-cell){padding:12px;background:#f8fafc}.process-consumption :deep(.cell){overflow-wrap:anywhere;white-space:normal}small{display:block;color:#7a8393;margin-top:10px}p{line-height:1.6}
</style>
