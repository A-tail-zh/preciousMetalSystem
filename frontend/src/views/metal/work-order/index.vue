<template>
 <WorkOrderDetail v-if="detailId" :id="detailId" @back="back"/>
 <section v-else class="work-order-page">
  <div class="order-toolbar"><el-button v-hasPermi="['metal:work-order:add']" class="blue" @click="formRef?.open()">添加</el-button><el-button v-hasPermi="['metal:work-order:edit']" class="orange" :disabled="selected.length!==1||!orderEditable(selected[0])" @click="formRef?.open(selected[0])">编辑</el-button><el-button v-hasPermi="['metal:work-order:remove']" class="red" :disabled="!selected.length||selected.some(row=>row.canDelete!==true)||deleting" @click="remove(selected)">删除</el-button></div>
  <el-table ref="tableRef" v-loading="loading" :data="rows" border class="order-table" @selection-change="selected=$event">
   <el-table-column v-if="can('metal:work-order:edit')||can('metal:work-order:remove')" type="selection" width="50" align="center"/><el-table-column label="工单号" prop="orderNo" min-width="150" align="center"/><el-table-column label="客户" prop="customer" min-width="75" align="center"/><el-table-column label="工厂名称" prop="factoryName" min-width="90" align="center"/><el-table-column label="产线名称" prop="lineName" min-width="90" align="center"/><el-table-column label="负责人" prop="ownerName" min-width="75" align="center"/>
   <el-table-column label="状态" min-width="130" align="center"><template #default="{row}">{{orderStatus[row.status]}}<template v-if="row.status==='REJECTED'">，原因：{{row.rejectReason}}</template></template></el-table-column>
   <el-table-column label="创建人" prop="creatorName" min-width="65" align="center"/><el-table-column label="创建时间" prop="createTime" width="140" align="center"/><el-table-column v-if="can('metal:work-order:query')||can('metal:work-order:edit')||can('metal:work-order:remove')" class-name="order-actions" label="操作" min-width="264" align="center"><template #default="{row}"><el-button v-hasPermi="['metal:work-order:query']" class="blue row-button" @click="view(row)">查看</el-button><el-button v-hasPermi="['metal:work-order:edit']" class="orange row-button" :disabled="!orderEditable(row)" :title="row.operationDisabledReason" @click="formRef?.open(row)">编辑</el-button><el-button v-hasPermi="['metal:work-order:remove']" class="red row-button" :disabled="row.canDelete!==true||deleting" :title="row.operationDisabledReason" @click="remove([row])">删除</el-button></template></el-table-column>
  </el-table>
  <div v-if="failed" class="load-error">列表加载失败 <el-button link type="primary" @click="load">重试</el-button></div>
  <div class="order-pagination"><el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :page-sizes="[16,32,64]" :total="total" layout="total, sizes, prev, pager, next, jumper" @current-change="load" @size-change="sizeChanged"/></div>
  <WorkOrderForm ref="formRef" @saved="saved"/>

 </section>
</template>
<script setup lang="ts">
import {ref,reactive,computed,onMounted,watch} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {ElMessage,ElMessageBox} from 'element-plus'
import type {TableInstance} from 'element-plus'
import useUserStore from '@/store/modules/user'
import {orderList,orderDetail,orderDelete,orderStatus,orderEditable} from '@/api/metal/work-order'
import type {WorkOrder} from '@/api/metal/work-order'
import WorkOrderForm from './WorkOrderForm.vue'
import WorkOrderDetail from './WorkOrderDetail.vue'
const user=useUserStore(),route=useRoute(),router=useRouter(),detailId=computed(()=>typeof route.query.id==='string'?route.query.id:'')
const can=(permission:string)=>user.permissions.some((v:string)=>v==='*:*:*'||v===permission)
const query=reactive({pageNum:1,pageSize:16}),rows=ref<WorkOrder[]>([]),selected=ref<WorkOrder[]>([]),total=ref(0),loading=ref(false),failed=ref(false),deleting=ref(false)
const formRef=ref<InstanceType<typeof WorkOrderForm>>(),tableRef=ref<TableInstance>()
let epoch=0
async function load(){if(detailId.value)return;const current=++epoch;loading.value=true;try{const response=await orderList(query);if(current!==epoch)return;rows.value=response.rows;total.value=response.total;selected.value=[];tableRef.value?.clearSelection();failed.value=false}catch{if(current===epoch)failed.value=true}finally{if(current===epoch)loading.value=false}}
function sizeChanged(){query.pageNum=1;load()}
async function saved(added:boolean){if(added)query.pageNum=Math.max(1,Math.ceil((total.value+1)/query.pageSize));await load()}
function view(row:WorkOrder){router.push({path:'/metal/work-order',query:{id:row.id}})}
function back(){router.push({path:'/metal/work-order'})}
async function remove(targets:WorkOrder[]){if(deleting.value||!targets.length||targets.some(row=>row.canDelete!==true))return;try{await ElMessageBox.confirm('确认删除选中的'+targets.length+'张工单？','删除确认',{type:'warning',confirmButtonText:'确定',cancelButtonText:'取消'})}catch{return}deleting.value=true;try{await orderDelete(targets.map(r=>r.id!));ElMessage.success('删除成功');query.pageNum=Math.min(query.pageNum,Math.max(1,Math.ceil((total.value-targets.length)/query.pageSize)));await load()}catch{}finally{deleting.value=false}}
watch(detailId,(value)=>{if(!value)load()});onMounted(load)
</script>
<style scoped>
.work-order-page{min-height:calc(var(--design-height,100vh) - 82px);background:#f8fbff;padding:9px 23px 24px 6px;display:flex;flex-direction:column}.order-toolbar{display:flex;gap:18px;margin-bottom:30px}.order-toolbar .el-button{width:89px;height:35px;margin:0;font-size:14px}.blue{--el-button-bg-color:#577bef;--el-button-border-color:#577bef;--el-button-text-color:#fff}.orange{--el-button-bg-color:#ee9b58;--el-button-border-color:#ee9b58;--el-button-text-color:#fff}.red{--el-button-bg-color:#ee6658;--el-button-border-color:#ee6658;--el-button-text-color:#fff}.green{--el-button-bg-color:#39a052;--el-button-border-color:#39a052;--el-button-text-color:#fff}.order-toolbar .el-button:disabled,.row-button:disabled{background:var(--el-button-bg-color);border-color:var(--el-button-border-color);color:white;opacity:.6}.order-table{width:100%;font-size:14px;--el-table-header-bg-color:#f4f4f4;--el-table-border-color:#e8ebf0}.order-table :deep(th.el-table__cell){height:56px}.order-table :deep(.cell){white-space:pre-wrap;overflow-wrap:anywhere;line-height:18px;font-size:14px;padding:10px 5px}.order-table :deep(td.el-table__cell){padding:0}.order-table :deep(.row-button){height:25px;font-size:13px;padding:0 8px;border-radius:2px;margin:2px}.order-pagination{margin-top:auto;padding-top:30px;display:flex;justify-content:center}.load-error{padding:15px;text-align:center}.audit-history{margin-top:20px;border-top:1px solid #e8ebf0;padding-top:15px;font-size:13px;line-height:2}

.order-table :deep(td.order-actions .cell){display:flex;align-items:center;justify-content:center;gap:4px;flex-wrap:nowrap;white-space:nowrap;padding:10px 8px}
.order-table :deep(td.order-actions .row-button){width:58px;height:25px;flex:0 0 58px;margin:0;padding:0}
</style>
