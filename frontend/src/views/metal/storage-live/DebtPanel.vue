<template>
 <el-dialog :model-value="modelValue" title="材料借还账" width="1250px" class="storage-dialog" append-to-body @update:model-value="emit('update:modelValue',$event)">
  <div class="filters">
   <el-select v-model="direction" aria-label="欠料方向" placeholder="全部方向" @change="search"><el-option label="全部方向" value=""/><el-option label="工厂欠客户" value="FACTORY_OWES"/><el-option label="客户欠工厂" value="CUSTOMER_OWES"/></el-select>
   <el-select v-model="customerId" clearable placeholder="客户" @change="search"><el-option v-for="c in customers" :key="c.id" :label="c.name" :value="c.id"/></el-select>
   <el-checkbox v-model="includeSettled" @change="search">包含已结清</el-checkbox><el-button @click="load">查询</el-button>
  </div>
  <el-alert v-if="error" :title="error" type="error" :closable="false"/>
  <el-table :data="rows" border v-loading="loading">
   <el-table-column prop="id" label="欠料记录" width="90"/>
   <el-table-column label="材料 / 规格 / 阶段" min-width="180"><template #default="{row}">{{row.identity?.name||row.identity?.code}} / {{row.identity?.specification||'—'}} / {{row.identity?.processName||row.identity?.form||'—'}}<div>{{row.measurementMode==='PIECE'?'按件':'按重量'}} · {{row.identity?.purity==null?'纯度未填':`${row.identity.purity}%`}}</div></template></el-table-column>
   <el-table-column label="债务关系" min-width="150"><template #default="{row}">{{row.debtorName||row.debtorId}} 欠 {{row.creditorName||row.creditorId}}<div>{{kindNames[row.debtKind]||row.debtKind}}</div></template></el-table-column>
   <el-table-column label="发生 / 已核销 / 未还" min-width="190"><template #default="{row}">{{row.originalWeight}} / {{row.settledWeight}} / {{row.remainingWeight}} g<div v-if="row.measurementMode==='PIECE'">{{row.originalQuantity}} / {{row.settledQuantity}} / {{row.remainingQuantity}} 件</div></template></el-table-column>
   <el-table-column label="来源" min-width="190"><template #default="{row}">工单 {{row.orderNumber||row.workOrderId||'—'}}<div>单据 {{row.documentNumber||row.documentId}}</div><div>批次 {{row.sourceBatchNo||row.stockId}} / 来源工单 {{row.sourceOrderId||'—'}}</div></template></el-table-column>
   <el-table-column label="状态" width="90"><template #default="{row}">{{row.status==='SETTLED'?'已结清':'未结清'}}</template></el-table-column>
   <el-table-column label="操作" width="160"><template #default="{row}"><el-button link @click="showDetail(row)">核销记录</el-button><el-button v-if="row.status==='OPEN'&&can('metal:material-debt:return')&&can('metal:warehouse-order:add')" link type="primary" @click="emit('repay',row)">办理归还</el-button></template></el-table-column>
  </el-table>
  <el-pagination v-model:current-page="page" :page-size="16" :total="total" layout="total,prev,pager,next" @change="load"/>
  <p>不同材料、阶段和计量方式分别记账。实际新来料先登记入库；在库归还只转移料权。工厂归还出借客户，不会自动减少生产客户欠工厂的材料。</p>
 </el-dialog>
 <el-dialog v-model="detailOpen" title="材料核销记录" width="850px" append-to-body>
  <el-table :data="detail?.settlements||[]" border><el-table-column prop="documentNumber" label="核销单据"/><el-table-column prop="weight" label="账面核销重量（g）"/><el-table-column prop="quantity" label="核销件数"/><el-table-column label="业务"><template #default="{row}">{{reasonNames[row.reason]||row.reason}} {{row.reversedBy?'（已冲销）':''}}</template></el-table-column></el-table>
  <p>按件债务以件数核销，账面重量按原债比例减少；实际归还重量以仓储单据为准。冲销以负向记录保留。</p>
 </el-dialog>
</template>
<script setup lang="ts">
import {ref,watch} from 'vue';import request from '@/utils/request';import {useStorage,message,type Row} from './client'
const props=defineProps<{modelValue:boolean;orderId?:string}>(),emit=defineEmits<{ 'update:modelValue':[boolean];repay:[Row]}>(),{state,context,can}=useStorage()
const rows=ref<Row[]>([]),customers=ref<Row[]>([]),direction=ref(''),customerId=ref(''),includeSettled=ref(false),page=ref(1),total=ref(0),loading=ref(false),error=ref(''),detailOpen=ref(false),detail=ref<Row>();let epoch=0,detailEpoch=0,panelEpoch=0
const kindNames:Record<string,string>={BORROW:'工厂借入',ADVANCE:'厂方垫料',COMPENSATION:'交接损失补偿'},reasonNames:Record<string,string>={RETURN:'原样退料',REPAYMENT:'材料归还',REVERSAL:'冲销'}
function search(){page.value=1;void load()}
async function load(){const id=++epoch,current=context();if(!state.factoryId)return;loading.value=true;error.value='';try{const r:any=await request({url:'/metal/material-debts/list',params:{factoryId:state.factoryId,orderId:props.orderId||undefined,direction:direction.value,customerId:customerId.value,includeSettled:includeSettled.value,pageNum:page.value,pageSize:16},quiet:true});if(id===epoch&&current===context()){rows.value=r.rows||[];total.value=r.total||0}}catch(e){if(id===epoch&&current===context())error.value=message(e)}finally{if(id===epoch)loading.value=false}}
async function showDetail(row:Row){const id=++detailEpoch,current=context();try{const r:any=await request({url:`/metal/material-debts/${row.id}`,params:{factoryId:state.factoryId},quiet:true});if(id===detailEpoch&&current===context()&&props.modelValue){detail.value=r.data;detailOpen.value=true}}catch(e){if(id===detailEpoch&&current===context())error.value=message(e)}}
watch(()=>[props.modelValue,props.orderId,context()],async()=>{
 const session=++panelEpoch;epoch++;detailEpoch++;detailOpen.value=false;detail.value=undefined;rows.value=[];customers.value=[];total.value=0;loading.value=false;error.value='';page.value=1;customerId.value=''
 if(props.modelValue&&state.factoryId){
  void load();const current=context()
  try{const r:any=await request({url:'/metal/options/customers',params:{factoryId:state.factoryId},quiet:true});if(session===panelEpoch&&current===context()&&props.modelValue)customers.value=r.data||[]}catch{}
 }
})
</script>
<style scoped>.filters{display:flex;gap:12px;margin-bottom:16px;align-items:center}.filters .el-select{width:180px}p{color:#909399}</style>
