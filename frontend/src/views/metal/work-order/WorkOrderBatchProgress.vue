<template>
 <div class="detail-card batch-progress">
  <div class="section-title">加工批次进度</div>
  <p>领料记录保留剩余在制，每次交接独立验收；已验收的部分即可转序，待验收和仍在制的部分继续占用计划。</p>
  <p v-if="failed" role="alert">批次进度加载失败，当前数据可能过期。<el-button link @click="load()">重新加载</el-button></p>
  <el-table :data="steps" border><el-table-column prop="productName" label="物料／产品" min-width="130"/><el-table-column prop="processName" label="工序" min-width="110"/><el-table-column label="计划件数" min-width="110"><template #default="{row}">{{count(row,'plannedQuantity')}}</template></el-table-column><el-table-column label="已验收完成" min-width="110"><template #default="{row}">{{count(row,'completedQuantity')}}</template></el-table-column><el-table-column label="待验收" min-width="110"><template #default="{row}">{{count(row,'pendingAcceptanceQuantity')}}</template></el-table-column><el-table-column label="仍在制／预留" min-width="130"><template #default="{row}">{{count(row,'inProgressQuantity')}}</template></el-table-column><el-table-column label="未结束批次占用" min-width="150"><template #default="{row}">{{count(row,'pendingQuantity')}}</template></el-table-column><el-table-column label="剩余可安排" min-width="130"><template #default="{row}">{{count(row,'availableQuantity')}}</template></el-table-column></el-table>
  <el-table :data="batches" border><el-table-column prop="processName" label="工序" min-width="110"/><el-table-column prop="productName" label="物料／产品" min-width="130"/><el-table-column prop="batchNo" label="加工批次号" min-width="240"/><el-table-column label="本批计划件数" min-width="130"><template #default="{row}">{{count(row,'plannedQuantity')}}</template></el-table-column><el-table-column label="本批完成件数" min-width="130"><template #default="{row}">{{count(row,'completedQuantity')}}</template></el-table-column><el-table-column label="当前在制净重（g）" min-width="150"><template #default="{row}">{{row.currentWipWeight==null?'—':decimalText(row.currentWipWeight)}}</template></el-table-column><el-table-column prop="phase" label="交接／验收状态" min-width="150"/></el-table>
  <el-table v-if="handovers.length" :data="handovers" border><el-table-column prop="processName" label="工序"/><el-table-column prop="batchNo" label="原加工记录" min-width="220"/><el-table-column prop="segmentNo" label="第几次交接"/><el-table-column prop="processorName" label="加工人员"/><el-table-column label="本次完成件数"><template #default="{row}">{{count(row,'outputQuantity')}}</template></el-table-column><el-table-column label="本次产出净重（g）"><template #default="{row}">{{row.outputWeight==null?'—':decimalText(row.outputWeight)}}</template></el-table-column><el-table-column label="本次损耗（g）"><template #default="{row}">{{row.lossWeight==null?'待验收':decimalText(row.lossWeight)}}</template></el-table-column><el-table-column label="验收状态"><template #default="{row}">{{states[row.status]??row.status}}</template></el-table-column></el-table>
 </div>
</template>
<script setup lang="ts">
import {computed,ref,watch,onBeforeUnmount} from 'vue'
import request from '@/utils/request'
import {getToken} from '@/utils/auth'
import {useInventoryRefresh} from '@/composables/useInventoryRefresh'
import useUserStore from '@/store/modules/user'
import {decimalText} from '@/utils/business-decimal'
import type {Row} from '@/api/metal/warehouse'
const props=defineProps<{workOrderId:string}>(),user=useUserStore(),steps=ref<Row[]>([]),failed=ref(false),loading=ref(false)
let epoch=0,disposed=false
const allowed=()=>!disposed&&user.sessionReady&&!!user.token&&user.token===getToken()&&(user.permissions.includes('*:*:*')||user.permissions.includes('metal:work-order:query'))
const states:Record<string,string>={POSTED:'已验收',WAITING:'待验收',COLLECTING:'申报中',RETURNED:'退回修订',CANCELLED:'已取消',VOID:'已冲销'}
const handovers=computed(()=>steps.value.flatMap(s=>(s.handovers??[]).map((h:Row)=>({...h,processName:s.processName,measurementMode:s.measurementMode}))))
const batches=computed(()=>steps.value.flatMap(s=>(s.batches??[]).map((b:Row)=>({...b,productName:s.productName,processName:s.processName,measurementMode:s.measurementMode}))))
function count(row:Row,key:string){return row.measurementMode==='WEIGHT'?'按重量':row[key]??'—'}
async function load(background=false,outer=()=>true){
 if(!allowed()||loading.value)return
 const ticket=++epoch,work=props.workOrderId,session=user.token,account=String(user.id),valid=()=>allowed()&&outer()&&ticket===epoch&&work===props.workOrderId&&session===user.token&&account===String(user.id)
 loading.value=true
 try{const r=await request({url:'/metal/work-order/'+work+'/batch-progress',isContextCurrent:valid,quiet:background});if(valid()){if(!Array.isArray(r.data))throw Error('批次进度响应无效');steps.value=r.data;failed.value=false}}
 catch{if(valid())failed.value=true}
 finally{if(ticket===epoch)loading.value=false}
}
useInventoryRefresh(ctx=>load(true,ctx.isCurrent),computed(()=>false),{allowed,reset:()=>{epoch++;steps.value=[];loading.value=false}},{paused:loading})
watch(()=>props.workOrderId,()=>{epoch++;steps.value=[];loading.value=false;void load()},{immediate:true})
onBeforeUnmount(()=>{disposed=true;epoch++})
</script>
<style scoped>
.batch-progress{margin-top:12px;padding:12px;background:var(--el-bg-color);min-width:0}.section-title{margin-bottom:12px}.batch-progress p{font-size:13px;color:var(--el-text-color-secondary);line-height:1.7}.batch-progress .el-table+.el-table{margin-top:12px}
</style>
