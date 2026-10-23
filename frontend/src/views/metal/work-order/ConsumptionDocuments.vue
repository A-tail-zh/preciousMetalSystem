<template>
 <div :class="'consumption-'+level" class="consumption-detail">
  <p class="context-title">{{title}}</p>
  <p v-if="loading" role="status">正在加载{{levelName}}…</p>
  <div v-if="failed" role="alert">{{levelName}}加载失败 <el-button link type="primary" @click="load">重试</el-button></div>
  <el-table v-loading="loading" :data="rows" :row-key="idKey" border :empty-text="failed?'加载失败，请重试':loading?' ':'暂无'+levelName" @expand-change="expand">
   <el-table-column v-if="level==='batches'" type="expand" width="45"><template #default="{row}"><ConsumptionDocuments v-if="expanded.includes(row.batchId)" level="orders" :context="{...context,batchId:row.batchId}" :title="title+' / 批次：'+row.batchNo" :batch-scope="batchScope" :hide-report-action="hideReportAction"/></template></el-table-column>
   <el-table-column v-for="c in columns" :key="c.key" :label="c.label" :min-width="c.width||135" :align="c.numeric?'right':'left'"><template #default="{row}">{{cell(row,c.key)}}</template></el-table-column>
   <el-table-column v-if="level==='batches'" label="状态" min-width="160"><template #default="{row}"><el-tag :type="row.state==='REVIEW'?'warning':row.state==='VOID'?'danger':'info'">{{consumptionStates[row.state]||row.state}}</el-tag><el-tag v-if="row.status==='SETTLED'&&row.reportStatus&&!row.reportVoided" :type="row.reportStatus==='ABNORMAL'?'danger':'success'">{{stateNames[row.reportStatus]||row.reportStatus}}</el-tag><p v-for="w in row.warnings" :key="w">{{w}}</p><el-button v-if="row.reportId&&canReport&&!hideReportAction" size="small" link type="primary" @click="emit('report',row.batchId)">查看报工</el-button></template></el-table-column>
   <el-table-column v-if="level==='orders'" label="状态／操作" min-width="190"><template #default="{row}"><p>{{stateNames[row.status]||row.status}}<span v-if="!row.included"> · 不计入汇总</span></p><el-button size="small" type="primary" link @click="openLines(row)">物料明细</el-button></template></el-table-column>
  </el-table>
  <el-pagination v-if="total>0" :current-page="pageNum" :page-size="16" :total="total" layout="total,prev,pager,next" small @current-change="turnPage"/>
  <el-drawer v-model="lineVisible" :title="'单据物料：'+(selectedOrder?.orderNo||'')" size="min(1000px,96vw)" append-to-body destroy-on-close><ConsumptionDocuments v-if="lineVisible&&selectedOrder" level="order-lines" :context="{...context,orderId:selectedOrder.orderId}" :title="title+' / 单据：'+selectedOrder.orderNo" :batch-scope="batchScope" :hide-report-action="hideReportAction"/></el-drawer>
 </div>
</template>
<script setup lang="ts">
import { useActionPermission } from '@/composables/useActionPermission'
const actionPermission = useActionPermission()
import {ref,computed,watch,onBeforeUnmount} from 'vue'
import useUserStore from '@/store/modules/user'
import {processConsumption,consumptionStates,stateNames,orderTypeNames,type Row} from '@/api/metal/warehouse'
import {displayDateTime} from '@/utils/display-date-time'
defineOptions({name:'ConsumptionDocuments'})
const props=defineProps<{level:'batches'|'orders'|'order-lines';context:Row;title:string;includeVoided?:boolean;batchScope?:'ALL'|'SETTLED';hideReportAction?:boolean}>(),emit=defineEmits<{report:[batchId:string]}>(),user=useUserStore()
const canReport=computed(()=>actionPermission.has('metal:work-report:query'))
const rows=ref<Row[]>([]),total=ref(0),pageNum=ref(1),loading=ref(false),failed=ref(false),expanded=ref<string[]>([]),lineVisible=ref(false),selectedOrder=ref<Row>()
const levelName=computed(()=>props.level==='batches'?'加工批次':props.level==='orders'?'来源单据':'物料明细'),idKey=computed(()=>props.level==='batches'?'batchId':props.level==='orders'?'orderId':'lineId')
type Column={key:string;label:string;width?:number;numeric?:boolean}
const allColumns=computed<Column[]>(()=>props.level==='batches'?[{key:'batchNo',label:'批次号',width:190},...['issuedWeight','rawReturnedWeight','semiReturnedWeight','outputWeight','netInputWeight','settledLossWeight','provisionalDifferenceWeight'].map((key,i)=>({key,label:['领料合计(g)','原料还库(g)','半成品还库(g)','产出(g)','实际投入(g)','已结算损耗(g)','暂算差额(g)'][i],numeric:true})),{key:'outputQuantity',label:'产出(件)',numeric:true},{key:'settledCompletedQuantity',label:'结算完成(件)',numeric:true},{key:'averageLoss',label:'平均损耗(g/件)',numeric:true},{key:'settledTime',label:'结算时间',width:165}]:props.level==='orders'?[{key:'orderNo',label:'单据编号',width:185},{key:'orderDate',label:'业务日期'},{key:'type',label:'单据类型'},{key:'netWeight',label:'净重合计(g)',numeric:true},{key:'quantity',label:'数量合计(件)',numeric:true},{key:'operatorName',label:'经办人'},{key:'warehouseName',label:'仓库'}]:[{key:'sequenceNo',label:'行号',width:60},{key:'code',label:'物料编号'},{key:'name',label:'名称'},{key:'category',label:'分类'},{key:'specification',label:'规格'},{key:'grossWeight',label:'毛重(g)',numeric:true},{key:'impurity',label:'杂质(g)',numeric:true},{key:'netWeight',label:'净重(g)',numeric:true},{key:'quantity',label:'数量(件)',numeric:true},{key:'organization',label:'单位名称'},{key:'sourceOrderNo',label:'原出库单号',width:185},{key:'releaseId',label:'释放记录ID'},{key:'sourceWorkOrderNo',label:'来源工单',width:185},{key:'sourceProcessName',label:'已完成来源工序'},{key:'purpose',label:'用途'},{key:'remark',label:'备注'}].filter(c=>props.batchScope!=='SETTLED'||c.key!=='provisionalDifferenceWeight'))
const columns=computed(()=>allColumns.value.filter(c=>props.batchScope!=='SETTLED'||c.key!=='provisionalDifferenceWeight'))
function cell(row:Row,key:string){const value=row[key];if(value==null||value==='')return '—';if(key==='type')return orderTypeNames[value]||value;if(key==='category')return ({RAW:'原料',SEMI:'半成品',FINISHED:'成品'} as Row)[value]||value;if(key==='settledTime')return row.status==='SETTLED'?displayDateTime(value):'—';return value}
let epoch=0,disposed=false
const signature=()=>JSON.stringify([props.level,props.context,props.includeVoided,props.batchScope])
async function load(){const token=++epoch,key=signature(),params:Row={...props.context,includeVoided:props.includeVoided||false,...(props.batchScope==='SETTLED'?{batchScope:props.batchScope}:{}),pageNum:pageNum.value,pageSize:16};const current=()=>!disposed&&token===epoch&&key===signature();rows.value=[];total.value=0;expanded.value=[];lineVisible.value=false;selectedOrder.value=undefined;loading.value=true;failed.value=false;try{const {data}=await processConsumption(props.level,params,current);if(!current())return;for(const field of ['workOrderId','itemId','processId','batchId','orderId'])if(params[field]&&data?.[field]!==params[field])throw Error('消耗明细上下文不匹配');if(props.level==='batches'&&props.batchScope==='SETTLED'&&(data?.batchScope!=='SETTLED'||data?.rows?.some((r:Row)=>r.status!=='SETTLED')))throw Error('批次范围不匹配');if(!Array.isArray(data?.rows)||!Number.isFinite(Number(data.total)))throw Error('消耗明细响应无效');rows.value=data.rows;total.value=Number(data.total)}catch{if(current())failed.value=true}finally{if(current())loading.value=false}}
function turnPage(n:number){pageNum.value=n;void load()}
function expand(_row:Row,opened:Row[]){expanded.value=opened.map(r=>r[idKey.value])}
function openLines(row:Row){selectedOrder.value=row;lineVisible.value=true}
watch(signature,()=>{pageNum.value=1;void load()},{immediate:true,flush:'sync'})
onBeforeUnmount(()=>{disposed=true;epoch++})
</script>
<style scoped>
.consumption-detail{min-width:0;max-width:100%}.context-title{color:#52657d;overflow-wrap:anywhere;font-weight:500;margin:0 0 12px}.consumption-detail :deep(.el-table){width:100%}.consumption-detail :deep(.cell){white-space:normal;overflow-wrap:anywhere}.consumption-detail :deep(.el-table__expanded-cell){background:#f4f7fb;padding:12px}.el-pagination{margin-top:12px;max-width:100%;overflow:auto}p{line-height:1.6}.consumption-detail :deep(.el-table__body-wrapper){overflow-x:auto}
</style>
