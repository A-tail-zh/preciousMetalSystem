<template>
 <section class="production-batches">
  <div class="toolbar"><h3>生产批次</h3><el-button :disabled="loading" @click="load">刷新批次</el-button></div>
  <el-alert v-if="error" :title="error" type="error" :closable="false"/>
  <el-table :data="rows" border v-loading="loading">
   <el-table-column prop="number" label="生产批次" min-width="170"/>
   <el-table-column label="产品组" min-width="150"><template #default="{row}">{{itemName(row.orderItemId)}}</template></el-table-column>
   <el-table-column prop="plannedQuantity" label="计划件数" width="105"/>
   <el-table-column label="状态" width="115"><template #default="{row}">{{states[row.lifecycle]||row.lifecycle}}</template></el-table-column>
   <el-table-column label="操作" min-width="220"><template #default="{row}">
    <el-button link @click="select(row)">查看加工记录</el-button><el-button link @click="traceDialog?.open(row)">双向追溯</el-button>
   </template></el-table-column>
  </el-table>
  <el-pagination v-if="total>pageSize" :current-page="page" :page-size="pageSize" :total="total" layout="total, prev, pager, next" @current-change="changePage"/>
  <el-card v-if="detail" shadow="never" class="batch-detail">
   <template #header>{{detail.number}} · {{detail.stageSummary}} <span class="stage">{{states[detail.lifecycle]||detail.lifecycle}}</span></template>
   <p v-if="detail.nextStep&&detail.lifecycle==='ACTIVE'">下一工序：{{detail.nextStep.processName}}。出库时由系统按实际来源自动衔接。</p>
   <el-table :data="records" border>
    <el-table-column prop="id" label="加工记录" width="110"/><el-table-column prop="processName" label="工序" min-width="130"/>
    <el-table-column label="加工人员" min-width="120"><template #default="{row}">{{state.people.find(p=>String(p.id)===String(row.processorId))?.name||row.processorId}}</template></el-table-column>
    <el-table-column label="状态" width="100"><template #default="{row}">{{row.status==='OPEN'?'加工中':row.status==='CLOSED'?'已结束':row.status==='SPLIT_TRANSFERRED'?'已移交':'已取消'}}</template></el-table-column>
    <el-table-column prop="inputWeight" label="投入（g）" min-width="125"/><el-table-column prop="outputWeight" label="本记录产出（g）" min-width="145"/><el-table-column prop="outputQuantity" label="本记录件数 / 参考" min-width="155"/>
    <el-table-column v-if="records.some(r=>Number(r.historicalOutputWeight)>0)" prop="historicalOutputWeight" label="历史承接产出（g）" min-width="160"/>
    <el-table-column v-if="records.some(r=>Number(r.historicalOutputWeight)>0)" prop="effectiveOutputWeight" label="阶段累计产出（g）" min-width="160"/>
    <el-table-column v-if="records.some(r=>Number(r.historicalOutputWeight)>0)" prop="effectiveOutputQuantity" label="阶段累计件数 / 参考" min-width="170"/>
    <el-table-column label="在制承接 / 移交（g）" min-width="150"><template #default="{row}">{{row.carryInWeight||'0.00'}} / {{row.carryOutWeight||'0.00'}}</template></el-table-column>

   </el-table>
  </el-card>
  <BatchTraceDialog ref="traceDialog" :factory-id="factoryId" :identity="identity()"/>
 </section>
</template>
<script setup lang="ts">
import {computed,ref,watch,onBeforeUnmount} from 'vue'
import request from '@/utils/request'
import {useStorage,message,type Row} from '../storage-live/client'
import BatchTraceDialog from './BatchTraceDialog.vue'
const props=defineProps<{factoryId:string;orderId:string;items:any[];disabled?:boolean}>()
const {state,context}=useStorage(),base='/metal/production-batches'
const rows=ref<Row[]>([]),total=ref(0),page=ref(1),pageSize=16,loading=ref(false),error=ref(''),detail=ref<any>(),selectedId=ref('')
const traceDialog=ref<InstanceType<typeof BatchTraceDialog>>()
const identity=()=>JSON.stringify([context(),props.factoryId,props.orderId])
const states:Record<string,string>={ACTIVE:'生产中',SPLIT:'已拆分',MERGED:'已合并',COMPLETED:'生产完成',CANCELLED:'已取消',EXCEPTION_TERMINATED:'异常终止'}
const records=computed<Row[]>(()=>(detail.value?.executions||[]).map((e:any)=>({...e,id:String(e.executionId),batchId:String(detail.value.id),batchVersion:detail.value.version,orderId:props.orderId})))
let generation=0,selection=0
function itemName(id:any){const item=props.items.find(i=>String(i.id)===String(id));return `${item?.product?.name||item?.productName||id} / ${id}`}
async function load(){
 if(!props.orderId||!props.factoryId)return
 const current=identity(),ticket=++generation;loading.value=true;error.value=''
 try{const r:any=await request({url:base+'/list',params:{factoryId:props.factoryId,orderId:props.orderId,lifecycle:'ALL',pageNum:page.value,pageSize},quiet:true});if(ticket!==generation||current!==identity())return;rows.value=r.rows||[];total.value=Number(r.total||0);if(selectedId.value)await select({id:selectedId.value} as Row)}catch(e){if(current===identity()&&ticket===generation)error.value=message(e)}finally{if(current===identity()&&ticket===generation)loading.value=false}
}
async function select(row:Row){const current=identity(),ticket=++selection;selectedId.value=String(row.id);try{const r:any=await request({url:`${base}/${row.id}`,params:{factoryId:props.factoryId},quiet:true});if(current===identity()&&ticket===selection)detail.value=r.data}catch(e){if(current===identity()&&ticket===selection)error.value=message(e)}}
function changePage(value:number){page.value=value;void load()}
function requestClose(){generation++;selection++;loading.value=false;return traceDialog.value?.requestClose()??true}
watch(identity,()=>{generation++;selection++;page.value=1;rows.value=[];total.value=0;detail.value=undefined;selectedId.value='';error.value='';loading.value=false;void load()},{immediate:true})
onBeforeUnmount(()=>{generation++;selection++})
defineExpose({load,requestClose,isLocked:()=>false})
</script>
<style scoped>.production-batches{margin:20px 0}.toolbar{display:flex;align-items:center;gap:12px;flex-wrap:wrap}.toolbar h3{margin-right:auto}.batch-detail{margin-top:16px}.stage{float:right;color:var(--el-text-color-secondary)}.el-select{width:100%}.el-pagination{margin-top:12px}</style>
