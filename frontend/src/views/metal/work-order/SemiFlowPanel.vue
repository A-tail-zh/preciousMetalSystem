<template>
<section class="semi-panel" v-loading="loading">
 <div class="semi-heading"><strong>半成品流转与工单关闭</strong><el-button :disabled="loading||busy" @click="load">刷新</el-button><el-button v-if="active&&has('metal:work-order:partial-close')" type="warning" :disabled="busy||loading" @click="open('close')">部分完工关闭</el-button><el-button v-if="data?.workOrder.status==='CLOSED_PARTIAL'&&has('metal:work-order:reopen')" :disabled="busy||loading" @click="open('reopen')">撤销部分关闭</el-button></div>
 <p v-if="failed" role="alert">半成品流转信息加载失败，请刷新重试。</p>
 <template v-if="data">
 <p class="semi-help">半成品还库只恢复原库存，不自动释放。新工单正常完成后，系统按策略核对并自动释放适用余量；历史工单和部分关闭仍需单独确认。有效释放后的余量可供同厂、同客户且路线匹配的其他工单领用。</p>
 <template v-if="data.progress?.length"><h4>工序数量覆盖</h4><el-table :data="data.progress" border><el-table-column prop="processName" label="工序"/><el-table-column prop="localQuantity" label="本单加工(件)"/><el-table-column prop="externalQuantity" label="外部半成品接入(件)"/><el-table-column prop="skippedQuantity" label="许可跳过(件)"/><el-table-column prop="coveredQuantity" label="数量覆盖合计(件)"/></el-table></template>
 <template v-if="data.items?.length"><h4>产品完成情况</h4><el-table :data="data.items" border><el-table-column label="产品"><template #default="{row}">{{productName(row)}}</template></el-table-column><el-table-column prop="plannedQuantity" label="原计划(件)"/><el-table-column prop="completedQuantity" label="最终已结算完成(件)"/><el-table-column prop="cancelledQuantity" :label="data.workOrder.status==='CLOSED_PARTIAL'?'取消继续加工(件)':'尚未完成(件)'"/></el-table></template>
 <template v-if="data.stocks?.length"><h4>剩余半成品库存</h4><el-table :data="data.stocks" border><el-table-column prop="name" label="产品"/><el-table-column prop="processName" label="已完成工序"/><el-table-column prop="batchNo" label="来源批次" min-width="180"/><el-table-column prop="quantity" label="实物库存(件)"/><el-table-column prop="netWeight" label="实物净重(g)"/><el-table-column prop="releasedQuantity" label="已释放未领(件)"/><el-table-column prop="unreleasedQuantity" label="尚未释放(件)"/><el-table-column v-if="has('metal:semi-release:add')" label="操作" width="90"><template #default="{row}"><el-button v-if="closed&&has('metal:semi-release:add')&&Number(row.unreleasedQuantity)>0" size="small" type="primary" :disabled="busy" @click="open('release',row)">释放</el-button></template></el-table-column></el-table></template>
 <template v-if="data.releases?.length"><h4>释放记录</h4><el-table :data="data.releases" border><el-table-column prop="releaseId" label="释放记录"/><el-table-column prop="name" label="产品"/><el-table-column prop="quantity" label="释放(件)"/><el-table-column prop="netWeight" label="释放净重(g)"/><el-table-column prop="availableQuantity" label="可领(件)"/><el-table-column prop="availableWeight" label="可领净重(g)"/><el-table-column label="状态"><template #default="{row}">{{row.status==='ACTIVE'?'有效':'已撤销'}}</template></el-table-column><el-table-column v-if="has('metal:semi-release:cancel')" label="操作"><template #default="{row}"><el-button v-if="row.status==='ACTIVE'&&has('metal:semi-release:cancel')" size="small" :disabled="busy" @click="open('cancel',row)">撤销释放</el-button></template></el-table-column></el-table></template>
 <template v-if="data.uses?.length"><h4>跨工单来源与去向</h4><el-table :data="data.uses" border><el-table-column prop="releaseId" label="来源释放记录"/><el-table-column prop="orderNo" label="生产出库单"/><el-table-column prop="targetWorkOrderNo" label="领用工单"/><el-table-column prop="quantity" label="原领用(件)"/><el-table-column prop="netWeight" label="原领用净重(g)"/><el-table-column prop="status" label="单据状态"/></el-table></template>
 <template v-if="data.closures?.length"><h4>关闭操作记录</h4><el-table :data="data.closures" border><el-table-column prop="createTime" label="时间"/><el-table-column label="操作"><template #default="{row}">{{row.action==='CLOSE'?'部分完工关闭':'撤销部分关闭'}}</template></el-table-column><el-table-column prop="reason" label="原因"/></el-table></template>
 </template>
 <el-dialog v-model="visible" :title="title" width="min(900px,96vw)" :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy" :before-close="beforeClose" append-to-body>
 <el-form ref="flowForm" :model="draft" :rules="rules" scroll-to-error label-width="120px" :disabled="busy" v-if="data&&target">
  <template v-if="mode==='close'"><el-table :data="data.items" border><el-table-column label="产品"><template #default="{row}">{{productName(row)}}</template></el-table-column><el-table-column prop="plannedQuantity" label="计划(件)"/><el-table-column prop="completedQuantity" label="实际完成(件)"/><el-table-column prop="cancelledQuantity" label="取消继续加工(件)"/></el-table><p>关闭保留各工序真实加工记录，不补造完成数量。请先处理所有未结算批次。</p><el-checkbox v-model="draft.confirmed" :disabled="busy">已核对实物余料，已领未加工半成品已还库</el-checkbox></template>
  <template v-if="mode==='release'"><el-alert :closable="false" type="info" title="还库不会自动释放；本次释放不增加实物库存，原来源与历史损耗保持不变。"/><p>释放后可供同厂、同客户且工序匹配的其他工单领用。</p><p>可释放：{{target.unreleasedQuantity}}件 / {{target.unreleasedWeight}}g</p><div><el-form-item label="释放件数" prop="quantity" required><el-input v-model="draft.quantity" @input="v=>quantityInput=String(v)" inputmode="numeric"/></el-form-item><el-form-item label="释放净重(g)" prop="netWeight" required><el-input v-model="draft.netWeight" inputmode="decimal"/></el-form-item></div></template>
  <p v-if="mode==='cancel'">仅尚未被有效领用的释放记录可以撤销，不改变实物库存。</p><p v-if="mode==='reopen'">请先撤销有效释放，存在下游领用依赖时不能重新打开。</p>
  <el-form-item label="操作原因" prop="reason" required><el-input v-model="draft.reason" type="textarea" placeholder="填写操作原因" maxlength="500" show-word-limit :disabled="busy"/></el-form-item>
 </el-form>
 <template #footer><el-button :disabled="busy" @click="visible=false">取消</el-button><el-button v-if="has(modePermission)" type="primary" :loading="busy" :disabled="!target" @click="save">确认</el-button></template>
 </el-dialog>
</section>
</template>
<script setup lang="ts">
import type {FormInstance} from 'element-plus'
import {fieldRule,textRules,integerError,decimalError,applyFieldErrors} from '@/utils/input-validation'
import { useActionPermission } from '@/composables/useActionPermission'
const actionPermission = useActionPermission()
import {ref,reactive,computed,watch,onBeforeUnmount} from 'vue'
import {ElMessage} from 'element-plus'
import useUserStore from '@/store/modules/user'
import {semiFlowOverview,semiFlowAction,type Row} from '@/api/metal/warehouse'
import {createUUID} from '@/utils/uuid'
const props=defineProps<{workOrderId:string}>(),emit=defineEmits(['changed']),user=useUserStore()
const has=(p:string)=>actionPermission.has(p)
const modePermission=computed(()=>({close:'metal:work-order:partial-close',reopen:'metal:work-order:reopen',release:'metal:semi-release:add',cancel:'metal:semi-release:cancel'}[mode.value]||''))
const data=ref<Row>(),loading=ref(false),failed=ref(false),busy=ref(false),visible=ref(false),target=ref<Row>(),mode=ref('')
const draft=reactive<Row>({}),title=computed(()=>({close:'部分完工关闭',reopen:'撤销部分关闭',release:'释放剩余半成品',cancel:'撤销释放'}[mode.value]||''))
const active=computed(()=>['WAITING_MATERIAL','IN_PROGRESS'].includes(data.value?.workOrder.status)),closed=computed(()=>['COMPLETED','CLOSED_PARTIAL'].includes(data.value?.workOrder.status))
const flowForm=ref<FormInstance>(),quantityInput=ref<string>()
const rules={reason:textRules('操作原因',500,true),quantity:[fieldRule(v=>integerError(quantityInput.value??v,'释放件数'))],netWeight:[fieldRule(v=>decimalError(v,'释放净重',{positive:true}))]}
let epoch=0,dialogEpoch=0,disposed=false
function productName(row:Row){try{return JSON.parse(row.productSnapshot).name||'产品'}catch{return '产品'}}
async function load(){const round=++epoch,work=props.workOrderId,current=()=>!disposed&&epoch===round&&work===props.workOrderId;loading.value=true;failed.value=false;data.value=undefined;try{const r=await semiFlowOverview(work,current);if(!current())return;if(!r.data?.workOrder)throw Error('invalid context');data.value=r.data}catch{if(current())failed.value=true}finally{if(current())loading.value=false}}
function open(action:string,row?:Row){quantityInput.value=undefined;flowForm.value?.clearValidate();if(busy.value||!data.value)return;dialogEpoch++;mode.value=action;target.value=JSON.parse(JSON.stringify(row||data.value.workOrder));Object.keys(draft).forEach(k=>delete draft[k]);Object.assign(draft,{requestId:createUUID(),reason:'',confirmed:false,quantity:row?.unreleasedQuantity,netWeight:row?.unreleasedWeight});visible.value=true}
function beforeClose(done:()=>void){if(!busy.value)done()}
async function save(){if(!has(modePermission.value)||busy.value||!visible.value)return;const validationRound=dialogEpoch;if(!await flowForm.value?.validate().catch(()=>false)||validationRound!==dialogEpoch||!visible.value||!has(modePermission.value))return;if(mode.value==='release')draft.quantity=Number(draft.quantity);if(busy.value||!target.value||!draft.reason?.trim()){ElMessage.error('请填写操作原因');return}if(mode.value==='close'&&!draft.confirmed){ElMessage.error('请先核对并确认实物余料');return}if(mode.value==='release'&&(!Number.isInteger(draft.quantity)||draft.quantity<1||!/^\d+(\.\d{1,6})?$/.test(String(draft.netWeight))||Number(draft.netWeight)<=0)){ElMessage.error('请填写正整数件数和最多6位小数的正净重');return}
 const work=props.workOrderId,round=dialogEpoch,selected=JSON.parse(JSON.stringify(target.value)),action=mode.value,body=JSON.parse(JSON.stringify({...draft,version:selected.version})),current=()=>!disposed&&work===props.workOrderId&&round===dialogEpoch;
 const url=action==='close'||action==='reopen'?'/metal/work-order/'+work+'/'+(action==='close'?'partial-close':'reopen'):action==='release'?'/metal/warehouse/inventory/'+selected.inventoryId+'/semi-release':'/metal/warehouse/semi-release/'+selected.releaseId+'/cancel';busy.value=true;
 try{await semiFlowAction(url,body,current);if(!current())return;visible.value=false;ElMessage.success('操作成功');emit('changed');await load()}catch(error){if(current())applyFieldErrors(flowForm.value,error)}finally{if(current())busy.value=false}
}
watch(visible,v=>{if(!v&&!busy.value){dialogEpoch++;target.value=undefined}})
watch(()=>props.workOrderId,()=>{epoch++;dialogEpoch++;visible.value=false;target.value=undefined;busy.value=false;void load()},{immediate:true,flush:'sync'})
onBeforeUnmount(()=>{disposed=true;epoch++;dialogEpoch++;visible.value=false;target.value=undefined})
</script>
<style scoped>
.semi-panel{margin:16px 0;padding:16px;background:white;border:1px solid #e8ebf0;border-radius:8px;min-width:0}.semi-heading{display:flex;align-items:center;gap:12px;flex-wrap:wrap}.semi-help{color:#606266;line-height:1.7}.semi-panel h4{margin:20px 0 10px}.semi-panel :deep(.el-table){width:100%}.semi-panel :deep(.cell){overflow-wrap:anywhere} @media(max-width:480px){.semi-panel{padding:10px}.semi-heading{gap:8px}}
</style>
