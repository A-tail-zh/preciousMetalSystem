<template>
 <section v-loading="loading" class="detail-page">
  <div class="detail-heading"><el-button link @click="$emit('back')">← 查看详情</el-button><span> / 工单管理</span></div>
  <template v-if="order">
   <div class="section-title">&gt;&gt;基本信息</div><div class="basic-card">
    <div>单据编号：{{order.orderNo}}</div><div>产品类型：{{categories}}</div><div>产品数量：{{quantity}}</div>
    <div>工厂名称：{{order.factoryName}}</div><div>产线名称：{{order.lineName}}</div><div>开单日期：{{order.orderDate}}</div>
    <div>负责人：{{order.ownerName}}</div><div>工单状态：{{orderStatus[order.status??'']}}<span v-if="order.rejectReason">，原因：{{order.rejectReason}}</span></div><div>创建人：{{order.creatorName}}</div><div>创建时间：{{order.createTime}}</div>
   </div>
   <div class="detail-columns"><div class="detail-card reports"><div class="section-title">&gt;&gt;报工记录</div><el-table :data="currentReports" border empty-text="暂无报工记录"><el-table-column label="工序名称" prop="processName"/><el-table-column label="损耗重量（g）" prop="lossWeight"/><el-table-column label="平均损耗（每件产品的损耗重量）" prop="averageLoss"/><el-table-column label="状态"><template #default="{row}">{{row.status==='ABNORMAL'?'异常':'正常'}}</template></el-table-column><el-table-column v-if="showReportActions" label="操作" width="132" align="center"><template #default="{row}"><div class="report-actions"><el-button v-hasPermi="['metal:work-report:query']" type="primary" size="small" @click="reportDialog?.open(row.itemId,row.processId,row.processName)">查看</el-button><el-button v-if="canEditReport" type="primary" size="small" @click="reportDialog?.open(row.itemId,row.processId,row.processName)">编辑</el-button></div></template></el-table-column></el-table><p v-if="!currentReports.length" class="empty-note">加工批次结算后自动生成报工记录</p></div>
    <div class="detail-card progress"><div class="section-title">&gt;&gt;工序进程</div><el-tabs v-model="activeItem" type="card"><el-tab-pane v-for="(item,index) in order.items" :key="item.id||index" :name="String(index)" :label="(item.snapshot?.name??'产品')+' '+(index+1)+'（'+item.quantity+'件）'"/></el-tabs><el-table :data="plans" border><el-table-column label="工序名称" prop="processName"/><el-table-column label="开始时间"><template #default="{row}">{{row.startTime||'—'}}</template></el-table-column><el-table-column label="结束时间"><template #default="{row}">{{row.endTime||'—'}}</template></el-table-column></el-table></div>
   </div>
   <div v-if="order.reviews?.length" class="detail-card"><div class="section-title">操作记录</div><el-table :data="order.reviews" border><el-table-column prop="createTime" label="时间"/><el-table-column prop="actorName" label="操作人"/><el-table-column label="操作"><template #default="{row}">{{reviewNames[row.action]||row.action}}</template></el-table-column><el-table-column prop="reason" label="说明"/></el-table></div>
  </template>
  <div v-if="failed" class="detail-error">工单加载失败 <el-button link type="primary" @click="load">重试</el-button></div>
 <WorkReportDialog ref="reportDialog" @changed="loadReports"/>
 </section>
</template>
<script setup lang="ts">
import {ref,computed,watch} from 'vue'
import useUserStore from '@/store/modules/user'
const user=useUserStore(),has=(p:string)=>user.permissions.includes('*:*:*')||user.permissions.includes(p),canEditReport=computed(()=>has('metal:work-report:query')&&has('metal:process-batch:reverse')),showReportActions=computed(()=>has('metal:work-report:query'))
import WorkReportDialog from '../warehouse-common/WorkReportDialog.vue'
import {reportSummary,type Row} from '@/api/metal/warehouse'
import {orderDetail,orderStatus} from '@/api/metal/work-order'
import type {WorkOrder} from '@/api/metal/work-order'
const reviewNames:Record<string,string>={CREATE:'创建',EDIT:'修改',DELETE:'删除',SUBMIT:'旧流程：提交审核',APPROVE:'旧流程：审核通过',REJECT:'旧流程：驳回',RESUBMIT:'旧流程：重新提交'}
const props=defineProps<{id:string}>();defineEmits<{back:[]}>()
const reports=ref<Row[]>([]),reportDialog=ref<InstanceType<typeof WorkReportDialog>>(),currentReports=computed(()=>reports.value.filter(r=>r.itemId===order.value?.items[Number(activeItem.value)]?.id))
async function loadReports(){reports.value=(await reportSummary(props.id)).data}
const order=ref<WorkOrder>(),loading=ref(false),failed=ref(false),activeItem=ref('0')
const categories=computed(()=>[...new Set(order.value?.items.map(i=>i.snapshot?.category))].filter(Boolean).join('、')),quantity=computed(()=>order.value?.items.reduce((sum,item)=>sum+(item.quantity??0),0)??0),plans=computed(()=>order.value?.items[Number(activeItem.value)]?.plans??[])
let epoch=0
async function load(){const current=++epoch;loading.value=true;failed.value=false;order.value=undefined;try{const response=await orderDetail(props.id);if(current!==epoch)return;order.value=response.data;activeItem.value='0';await loadReports()}catch{if(current===epoch)failed.value=true}finally{if(current===epoch)loading.value=false}}
watch(()=>props.id,load,{immediate:true})
</script>
<style scoped>
.detail-page{background:#f8fbff;min-height:calc(var(--design-height,100vh) - 82px);padding:0 8px 0 6px;color:#50545c;font-size:14px}.detail-heading{height:44px;display:flex;align-items:center;border-bottom:1px solid #e8ebf0}.section-title{padding:12px 8px}.basic-card{background:white;border:1px solid #e8ebf0;border-radius:8px;padding:24px;display:grid;grid-template-columns:repeat(3,1fr);gap:28px 20px;min-height:270px}.detail-columns{display:grid;grid-template-columns:46% 54%;margin-top:10px;gap:8px;padding-right:8px}.detail-card{background:white;border:1px solid #e8ebf0;border-radius:8px;padding:0 12px;min-height:max(400px,calc(var(--design-height,100vh) - 446px))}.detail-card :deep(.el-table){font-size:14px;--el-table-header-bg-color:#f4f4f4}.detail-card :deep(th){height:56px}.detail-card :deep(.cell){white-space:pre-wrap;overflow-wrap:anywhere}.progress :deep(.el-radio-group){margin-bottom:12px}.progress :deep(.el-tabs__header){margin-bottom:12px}.empty-note{color:#909399;font-size:13px}.detail-error{padding:30px;text-align:center}
.report-actions{display:flex;align-items:center;justify-content:center;gap:4px;flex-wrap:nowrap;white-space:nowrap}.report-actions :deep(.el-button){margin-left:0;flex-shrink:0;background:#577bef;border-color:#577bef;padding:5px 10px}
</style>
