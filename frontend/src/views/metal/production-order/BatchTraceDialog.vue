<template>
 <el-dialog v-model="visible" title="生产批次双向追溯" width="min(1250px,96vw)" append-to-body :close-on-click-modal="false" :before-close="close">
  <div class="trace-toolbar"><strong>{{batchNumber||`生产批次 #${batchId}`}}</strong><el-select v-model="direction" aria-label="追溯方向" :disabled="loading" @change="reload"><el-option label="上游与下游" value="BOTH"/><el-option label="仅上游来源" value="UPSTREAM"/><el-option label="仅下游去向" value="DOWNSTREAM"/></el-select><el-button :disabled="loading" @click="reload">刷新追溯</el-button></div>
  <el-alert v-if="error" :title="error" type="error" :closable="false"/>
  <div v-loading="loading">
   <h3>生产批次节点</h3><el-table :data="nodes" border><el-table-column prop="number" label="生产批次" min-width="170"/><el-table-column prop="batchId" label="批次ID" width="110"/><el-table-column prop="plannedQuantity" label="计划件数"/><el-table-column label="状态"><template #default="{row}">{{states[row.lifecycle]||row.lifecycle}}</template></el-table-column></el-table>
   <h3>拆合来源与去向</h3><el-table :data="edges" border><el-table-column prop="operationId" label="操作ID" width="110"/><el-table-column label="来源批次"><template #default="{row}">{{numberOf(row.sourceBatchId)}}</template></el-table-column><el-table-column label="结果批次"><template #default="{row}">{{numberOf(row.targetBatchId)}}</template></el-table-column><el-table-column label="动作"><template #default="{row}">{{actions[row.action]||row.action}}</template></el-table-column><el-table-column prop="weight" label="贡献净重（g）"/><el-table-column label="实际件数"><template #default="{row}">{{row.quantity??'—'}}</template></el-table-column><el-table-column label="有效性"><template #default="{row}">{{row.reversed?'已撤销（历史）':'有效关系'}}</template></el-table-column></el-table>
   <h3>实际加工记录</h3><el-table :data="executions" border><el-table-column prop="executionId" label="加工记录" width="110"/><el-table-column label="生产批次"><template #default="{row}">{{numberOf(row.batchId)}}</template></el-table-column><el-table-column prop="orderStepId" label="路线节点"/><el-table-column label="状态"><template #default="{row}">{{executionStates[row.status]||row.status}}</template></el-table-column><el-table-column prop="inputWeight" label="真实投入（g）"/><el-table-column prop="outputWeight" label="真实产出（g）"/><el-table-column prop="carryInWeight" label="承接（g）"/><el-table-column prop="carryOutWeight" label="移交（g）"/></el-table>
   <h3>材料来源分配</h3><el-table :data="allocations" border><el-table-column prop="allocationId" label="分配ID" width="110"/><el-table-column label="分配用途"><template #default="{row}">{{purposes[row.purpose]||row.purpose}}</template></el-table-column><el-table-column prop="fromSourceId" label="来源明细"/><el-table-column prop="toSourceId" label="承接明细"/><el-table-column prop="weight" label="净重（g）"/><el-table-column label="件数"><template #default="{row}">{{row.quantity??'—'}}</template></el-table-column></el-table>
   <el-tabs class="real-facts"><el-tab-pane v-for="section in facts" :key="section.name" :label="section.name"><el-table :data="section.rows" border><el-table-column v-for="column in section.columns" :key="column.key" :label="column.label" min-width="125"><template #default="{row}">{{factCell(row,column.key)}}</template></el-table-column></el-table></el-tab-pane></el-tabs>
   <h3>拆合操作历史</h3><el-table :data="operations" border><el-table-column prop="operationId" label="操作ID" width="110"/><el-table-column label="动作"><template #default="{row}">{{actions[row.action]||row.action}}</template></el-table-column><el-table-column prop="reason" label="原因" min-width="190"/><el-table-column prop="createdAt" label="操作时间" min-width="175"/><el-table-column label="撤销状态" min-width="125"><template #default="{row}">{{row.reversedBy?`已撤销 / 操作${row.reversedBy}`:'未撤销'}}</template></el-table-column></el-table>
  </div>
  <el-alert v-if="truncated" title="当前追溯尚未全部加载，请继续加载后查看完整分支。" type="info" :closable="false"/>
  <el-button v-if="nextCursor" :loading="loading" :disabled="loading" @click="loadMore">加载更多追溯</el-button><el-alert v-else-if="truncated" title="服务端未提供后续游标，当前结果不完整，请刷新或缩小追溯方向。" type="warning" :closable="false"/>
  <template #footer><el-button @click="close()">关闭</el-button></template>

 </el-dialog>
</template>
<script setup lang="ts">
import {computed,ref,watch,onBeforeUnmount} from 'vue'
import request from '@/utils/request'
import {message,type Row} from '../storage-live/client'
const props=defineProps<{factoryId:string;identity:string}>()
const base='/metal/production-batches'
const visible=ref(false),batchId=ref(''),batchNumber=ref(''),direction=ref('BOTH'),loading=ref(false),error=ref(''),nextCursor=ref<string|null>(null),truncated=ref(false)
const nodes=ref<Row[]>([]),edges=ref<Row[]>([]),executions=ref<Row[]>([]),allocations=ref<Row[]>([]),operations=ref<Row[]>([])
const productionEvents=ref<Row[]>([]),storageEvents=ref<Row[]>([]),documents=ref<Row[]>([]),stockSlices=ref<Row[]>([]),materialSources=ref<Row[]>([])
const states:Record<string,string>={ACTIVE:'生产中',SPLIT:'已拆分',MERGED:'已合并',COMPLETED:'生产完成',CANCELLED:'已取消',EXCEPTION_TERMINATED:'异常终止'}
const executionStates:Record<string,string>={OPEN:'加工中',CLOSED:'已结束',CANCELLED:'已取消',SPLIT_TRANSFERRED:'已移交'}
const actions:Record<string,string>={SPLIT:'拆分',MERGE:'合并',REVERSE_TRANSFORM:'撤销拆合',RETURN_RESIDUAL:'退料承接',REVERSE_RETURN_RESIDUAL:'冲销退料承接'}
const purposes:Record<string,string>={IDENTITY_TRANSFER:'库存身份转移',WIP_TRANSFER:'在制移交',ISSUE:'真实领料',OUTPUT:'实际产出',RETURN:'原样退回',RECOVERY:'回收归库',LOSS:'已确认损耗',DISPATCH:'出厂交付',ADJUSTMENT:'库存调整',REVERSAL:'反向恢复'}
const facts=computed(()=>[
 {name:'真实生产事件',rows:productionEvents.value,columns:[{key:'id',label:'事件ID'},{key:'executionId',label:'加工记录'},{key:'eventType',label:'业务动作'},{key:'documentId',label:'真实单据ID'},{key:'lineId',label:'真实明细ID'},{key:'stockId',label:'实际库存ID'},{key:'weight',label:'净重（g）'},{key:'quantity',label:'件数'},{key:'reversed',label:'有效性'}]},
 {name:'真实仓储单据',rows:documents.value,columns:[{key:'number',label:'单据号'},{key:'id',label:'单据ID'},{key:'type',label:'业务类型'},{key:'status',label:'单据状态'},{key:'batchId',label:'生产批次'},{key:'executionId',label:'加工记录'}]},
 {name:'真实仓储事件',rows:storageEvents.value,columns:[{key:'id',label:'事件ID'},{key:'action',label:'业务动作'},{key:'documentId',label:'单据ID'},{key:'lineId',label:'明细ID'},{key:'stockSliceId',label:'实际库存份额'},{key:'reversedBy',label:'冲销关联'}]},
 {name:'实际库存份额',rows:stockSlices.value,columns:[{key:'stockNumber',label:'实物库存号'},{key:'stockId',label:'实际库存ID'},{key:'stockSliceId',label:'份额ID'},{key:'batchId',label:'生产批次'},{key:'originDocumentId',label:'原入库单据'},{key:'originLineId',label:'原入库明细'},{key:'originStepId',label:'实物完成节点'},{key:'weight',label:'剩余净重（g）'},{key:'quantity',label:'剩余件数'}]},
 {name:'逐来源与原领料',rows:materialSources.value,columns:[{key:'sourceAllocationId',label:'当前来源明细'},{key:'parentSourceAllocationId',label:'上游来源明细'},{key:'rootIssueAllocationId',label:'原领料分配'},{key:'issueDocumentId',label:'原领料单据'},{key:'issueLineId',label:'原领料明细'},{key:'sourceKind',label:'历史供料构成'},{key:'sourceOwnerId',label:'历史供料方'},{key:'currentOwnerId',label:'当前料权方'},{key:'weight',label:'当前责任净重（g）'},{key:'quantity',label:'件数'}]}
])
let generation=0
function numberOf(id:any){return nodes.value.find(n=>String(n.batchId)===String(id))?.number||`#${id}`}
function factCell(row:Row,key:string){const value=row[key];if(value==null)return '—';if(key==='batchId')return numberOf(value);if(key==='reversed')return value?'已冲销（历史）':'有效事件';if(key==='sourceKind')return value==='CUSTOMER'?'客户供料':value==='FACTORY'?'厂方供料':value;if(key==='currentOwnerId'||key==='sourceOwnerId')return String(value)==='0'?'工厂':`客户 #${value}`;if(key==='eventType')return ({INPUT:'领料投入',OUTPUT:'产出入库',RETURN:'原样退回',RECOVERY:'回收归库'} as Record<string,string>)[value]||value;if(key==='action')return ({INBOUND:'真实入库',ISSUE:'真实领料',RETURN:'原样退回',REVERSAL:'冲销',DISPATCH:'出厂交付',ADJUSTMENT:'调整',REPAYMENT:'料权归还'} as Record<string,string>)[value]||value;return value}
function append(current:Row[],incoming:Row[],key:string){const values=new Map(current.map(r=>[String(r[key]),r]));for(const row of incoming)values.set(String(row[key]),row);return [...values.values()]}
async function load(cursor?:string){
 const n=generation,current=props.identity;loading.value=true;error.value=''
 try{const r:any=await request({url:`${base}/${batchId.value}/trace`,params:{factoryId:props.factoryId,direction:direction.value,pageSize:50,...(cursor?{cursor}:{})},quiet:true});if(n!==generation||current!==props.identity||!visible.value)return;const data=r.data;nodes.value=append(nodes.value,data.nodes||[],'batchId');edges.value=append(edges.value,data.edges||[],'id');executions.value=append(executions.value,data.executions||[],'executionId');allocations.value=append(allocations.value,data.allocations||[],'allocationId');operations.value=append(operations.value,data.operations||[],'operationId');productionEvents.value=append(productionEvents.value,data.productionEvents||[],'id');storageEvents.value=append(storageEvents.value,data.storageEvents||[],'id');documents.value=append(documents.value,data.documents||[],'id');stockSlices.value=append(stockSlices.value,data.stockSlices||[],'stockSliceId');materialSources.value=append(materialSources.value,data.materialSources||[],'sourceAllocationId');nextCursor.value=data.nextCursor||null;truncated.value=!!data.truncated}
 catch(e){if(n===generation&&current===props.identity)error.value=message(e)}finally{if(n===generation&&current===props.identity)loading.value=false}
}
async function reload(){await refreshTrace()}
async function refreshTrace(){generation++;nodes.value=[];edges.value=[];executions.value=[];allocations.value=[];operations.value=[];productionEvents.value=[];storageEvents.value=[];documents.value=[];stockSlices.value=[];materialSources.value=[];nextCursor.value=null;truncated.value=false;await load()}
async function open(row:Row){batchId.value=String(row.batchId||row.id);batchNumber.value=row.number||'';direction.value='BOTH';visible.value=true;await reload()}
async function loadMore(){if(!loading.value&&nextCursor.value)await load(nextCursor.value)}
function close(done?:()=>void){generation++;visible.value=false;loading.value=false;done?.()}
function requestClose(){close();return true}
watch(()=>props.identity,()=>{generation++;visible.value=false;loading.value=false})
onBeforeUnmount(()=>{generation++})
defineExpose({open,requestClose})
</script>
<style scoped>.trace-toolbar{display:flex;align-items:center;gap:14px;flex-wrap:wrap}.trace-toolbar strong{margin-right:auto}.el-select{width:190px}h3{font-size:15px;margin:20px 0 10px}p{color:var(--el-text-color-secondary);line-height:1.6}.el-alert{margin:10px 0}.real-facts{margin-top:20px}</style>
