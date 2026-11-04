<template>
 <div class="detail-card batch-progress">
  <div class="section-title">加工批次进度</div>
  <p>各批独立领料、交接及验收；待验收仍占用本工序计划，验收后的合格产出才能进入下一工序。</p>
  <p v-if="failed" role="alert">批次进度加载失败，当前数据可能过期。<el-button link @click="load()">重新加载</el-button></p>
  <el-table :data="steps" border><el-table-column prop="productName" label="物料／产品" min-width="130"/><el-table-column prop="processName" label="工序" min-width="110"/><el-table-column label="计划件数" min-width="110"><template #default="{row}">{{count(row,'plannedQuantity')}}</template></el-table-column><el-table-column label="已验收完成" min-width="110"><template #default="{row}">{{count(row,'completedQuantity')}}</template></el-table-column><el-table-column label="未结束批次占用" min-width="150"><template #default="{row}">{{count(row,'pendingQuantity')}}</template></el-table-column><el-table-column label="剩余可安排" min-width="130"><template #default="{row}">{{count(row,'availableQuantity')}}</template></el-table-column></el-table>
  <el-table :data="batches" border><el-table-column prop="processName" label="工序" min-width="110"/><el-table-column prop="productName" label="物料／产品" min-width="130"/><el-table-column prop="batchNo" label="加工批次号" min-width="240"/><el-table-column label="本批计划件数" min-width="130"><template #default="{row}">{{count(row,'plannedQuantity')}}</template></el-table-column><el-table-column label="本批完成件数" min-width="130"><template #default="{row}">{{count(row,'completedQuantity')}}</template></el-table-column><el-table-column prop="phase" label="交接／验收状态" min-width="150"/></el-table>
 </div>
</template>
<script setup lang="ts">
import {computed,ref,watch,onBeforeUnmount} from 'vue'
import request from '@/utils/request'
import {getToken} from '@/utils/auth'
import {useInventoryRefresh} from '@/composables/useInventoryRefresh'
import useUserStore from '@/store/modules/user'
import type {Row} from '@/api/metal/warehouse'
const props=defineProps<{workOrderId:string}>(),user=useUserStore(),steps=ref<Row[]>([]),failed=ref(false),loading=ref(false)
let epoch=0,disposed=false
const allowed=()=>!disposed&&user.sessionReady&&!!user.token&&user.token===getToken()&&(user.permissions.includes('*:*:*')||user.permissions.includes('metal:work-order:query'))
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
