<template>
 <el-dialog class="warehouse-flow-dialog" v-model="visible" :title="rawInbound?'原料入库验收':'仓储交接确认'" width="min(1180px,96vw)" :close-on-click-modal="false" :show-close="!busy&&!uncertain" :close-on-press-escape="!busy&&!uncertain" destroy-on-close @closed="closed">
  <div v-loading="loading" class="flow-dialog">
   <template v-if="!info">
    <el-form label-position="top"><el-form-item label="工厂"><el-select v-model="factoryId" @change="loadList"><el-option v-for="f in factories" :key="f.id" :value="f.id" :label="f.name"/></el-select></el-form-item></el-form>
    <p>最近100笔申请。待确认只占用库存，全部指定人员确认后统一生效。</p>
    <el-table :data="records" border><el-table-column label="单据" prop="orderNumbers" min-width="160"/><el-table-column label="业务"><template #default="{row}">{{kinds[row.kind]||row.kind}}</template></el-table-column><el-table-column label="工单" prop="workOrderNo"/><el-table-column label="状态"><template #default="{row}">{{stateNames[row.status]||row.status}}</template></el-table-column><el-table-column label="创建人" prop="creatorName"/><el-table-column label="操作" width="90"><template #default="{row}"><el-button link type="primary" @click="reload(String(row.id))">查看</el-button></template></el-table-column></el-table>
   </template>
   <template v-else>
    <div v-if="rawInbound" class="receipt-heading"><el-tag :type="info.status==='POSTED'?'success':pending?'warning':'info'">{{receiptStatus}}</el-tag><span>各仓由指定仓管员验收，全部仓库完成后统一入库。</span></div>
    <p v-else>{{kinds[info.kind]||info.kind}} · {{stateNames[info.status]||info.status}} · {{stages[info.stage]||info.stage}}</p>
    <p v-if="info.kind==='HANDOVER'">加工人员与库管称重分别保留；每行有差异必须说明。全部仓库验收后一次入库并结算。</p>
    <p v-if="info.kind==='OFFSET'">仅转移客户料与厂料归属，不改变实物总量，不登记欠料结清。</p>
    <p v-if="!rawInbound&&info.processors?.length">加工申报：<span v-for="p in info.processors" :key="p.actorId">{{p.name}}（{{p.submitted?'已提交':'待本人提交'}}） </span></p>
    <section v-for="group in info.groups" :key="group.id" class="flow-group">
     <h4>{{group.warehouseName||group.order?.warehouseId}} · {{group.orderNo}} · {{orderTypeNames[group.type]||group.type}}<template v-if="!rawInbound"> · {{group.status==='CONFIRMED'?'已确认':'待确认'}}</template></h4>
     <template v-if="rawInbound"><p class="receipt-people">指定验收人：{{keeperName(group)}}<span>实际验收人：{{group.confirmedBy?personName(group.confirmedByName,group.confirmedByDeleted):'尚未验收'}}</span></p><el-alert :title="acceptanceHint(group)" :type="canConfirm(group)?'success':'info'" :closable="false" show-icon/></template>
     <p v-else>领用人：{{group.recipientName||group.order?.recipientId||'—'}}；仓管员：{{group.keeperName||group.order?.keeperId}}；实际确认人：{{group.confirmedByName||group.confirmedBy||'—'}}</p>
     <section v-if="rawInbound"><div v-for="(entry,index) in group.lines" :key="entry.id"><h4>第{{index+1}}条原料客户资料（申请快照）</h4><CustomerSnapshot :snapshot="entry.line.customerSnapshot" :factory-owned="entry.line.materialOwnership==='FACTORY'"/></div></section>
     <el-table :data="group.lines" border>
      <el-table-column :label="rawInbound?'物料':'物料/产品'" min-width="150"><template #default="{row}"><template v-if="rawInbound"><strong>{{row.line.code||row.stock?.code||'历史未记录编码'}}</strong><div>{{row.line.name||row.stock?.name||'历史未记录名称'}}</div></template><template v-else>{{row.line.code||row.stock?.code||row.line.referenceId||row.line.sourceLineId}}</template></template></el-table-column>
      <el-table-column v-if="rawInbound" label="归属" min-width="150"><template #default="{row}">{{ownership(row)}}</template></el-table-column>
      <el-table-column :label="rawInbound?'申报净重（g）':info.kind==='HANDOVER'?'加工人员净重（g）':'计划净重（g）'" min-width="150"><template #default="{row}"><BusinessDecimalInput v-if="editing" v-model="row.editPlan" :disabled="busy||uncertain"/><span v-else>{{decimalText(row.processorWeight??row.plannedWeight)}}</span></template></el-table-column>
      <el-table-column v-if="!rawInbound&&group.lines.some((l:Row)=>!raw(l))" label="数量 / 参考" min-width="125"><template #default="{row}"><template v-if="!raw(row)"><el-input v-if="editing||canConfirm(group)&&row.stock?.measurementMode!=='WEIGHT'&&row.line.referenceQuantity==null" v-model="row.editQuantity" inputmode="numeric" :disabled="busy||needsReload||uncertain"/><span v-else>{{row.stock?.measurementMode==='WEIGHT'||row.line.referenceQuantity!=null?'参考：'+(row.line.referenceQuantity??'未记录'):row.line.quantity}}</span></template></template></el-table-column>
      <el-table-column :label="info.stage==='RECIPIENT'?'实际领入净重（g）':'库管实收净重（g）'" min-width="165"><template #default="{row}"><BusinessDecimalInput v-if="canConfirm(group)&&!editing" v-model="row.editWeight" :disabled="busy||needsReload||uncertain"/><span v-else>{{row.actualWeight==null?'待确认':decimalText(row.actualWeight)}}</span></template></el-table-column>
      <el-table-column label="逐条差异原因" min-width="210"><template #default="{row}"><el-input v-if="canConfirm(group)&&!editing" v-model="row.editReason" maxlength="500" :disabled="busy||needsReload||uncertain" placeholder="重量不同必须填写"/><span v-else>{{row.differenceReason||'—'}}</span></template></el-table-column>
     </el-table>
     <details v-if="group.lines.some((l:Row)=>l.line.grossWeight!=null||l.line.impurity!=null)"><summary>{{rawInbound?'原始申报称重依据':'原始计划称重依据'}}</summary><p v-for="line in group.lines" :key="line.id">{{line.line.code||line.stock?.code||line.id}}：{{rawInbound?'申报':'计划'}}毛重 {{weightEvidence(line.line.grossWeight)}}，杂质 {{weightEvidence(line.line.impurity)}}，净重 {{weightEvidence(line.plannedWeight)}}。</p></details>
     <el-button v-if="canConfirm(group)&&!editing&&(!rawInbound||!actionMode)" type="primary" :loading="busy" :disabled="needsReload||uncertain" @click="confirm(group)">{{info.stage==='RECIPIENT'?'本人实领确认':'本人库管验收'}}</el-button>
    </section>
    <el-form v-if="!rawInbound||editing||actionMode" label-position="top"><el-form-item :label="rawInbound?reasonLabel:'退回、修订、取消或冲销原因'"><el-input v-model="reason" type="textarea" maxlength="500" show-word-limit :disabled="busy||uncertain" :placeholder="rawInbound?'请填写'+reasonLabel:undefined"/></el-form-item></el-form>
    <details v-if="info.events?.length"><summary>{{rawInbound?'验收及修订历史':'交接及修订历史'}}</summary><el-table :data="info.events" border><el-table-column type="expand"><template #default="{row}"><p>{{eventReason(row)}}</p><el-table v-if="eventMeasurements(row).length" :data="eventMeasurements(row)" border><el-table-column label="明细"><template #default="{row}">{{rawInbound?historyMaterial(row.lineId):row.lineId}}</template></el-table-column><el-table-column label="实际称重（g）"><template #default="{row}">{{decimalText(row.netWeight)}}</template></el-table-column><el-table-column label="差异原因" prop="differenceReason"/></el-table></template></el-table-column><el-table-column label="时间" prop="createTime" min-width="175"/><el-table-column label="实际操作人" min-width="150"><template #default="{row}">{{rawInbound?personName(row.actorName,row.actorDeleted):row.actorName||row.actorId}}</template></el-table-column><el-table-column label="操作" min-width="120"><template #default="{row}">{{rawInbound?(eventNames[row.action]||'其他操作'):row.action}}</template></el-table-column><el-table-column v-if="!rawInbound" label="版本" prop="version"/></el-table><section v-for="g in info.revisions??[]" :key="g.id"><h4>{{rawInbound?'历史申请':'历史修订 '+g.revision}} · {{g.orderNo}} · {{g.warehouseName}}</h4><p v-for="line in g.lines" :key="line.id">{{line.line.code||'历史未记录编码'}}：{{rawInbound?'申报':'计划'}} {{decimalText(line.plannedWeight)}} g，实际 {{weightEvidence(line.actualWeight)}}<span v-if="!rawInbound&&!raw(line)">；{{line.line.referenceQuantity!=null?'参考：'+line.line.referenceQuantity:line.line.quantity+' 件'}}</span>；{{line.differenceReason||'—'}}</p></section></details>
   </template>
   <p v-if="refreshFailed" role="status">更新检查失败，当前资料可能过期。</p><el-alert v-if="error" :title="error" type="error" :closable="false" show-icon/>
  </div>
  <template #footer>
   <el-button v-if="uncertain" type="primary" :loading="busy" @click="retryRequest">重试原请求</el-button>
   <el-button :disabled="busy||uncertain" @click="visible=false">关闭</el-button><el-button v-if="info" :disabled="busy||uncertain" @click="reload(info.id)">重新加载</el-button>
   <template v-if="info&&pending&&!needsReload">
    <el-button v-if="canReject&&!editing&&(!rawInbound||info.status!=='RETURNED'&&(!actionMode||actionMode==='reject'))" :disabled="busy||uncertain" type="warning" @click="action('reject')">{{rawInbound&&actionMode==='reject'?'确认退回修订':'退回修订'}}</el-button>
    <el-button v-if="canRevise&&(!rawInbound||!actionMode)" :disabled="busy||uncertain" @click="editing?revise():editing=true">{{editing?'保存修订':rawInbound?'修订申报明细':'修订计划明细'}}</el-button>
    <el-button v-if="String(info.requestActor)===String(user.id)&&permission.has('metal:warehouse-order:'+ (info.kind==='HANDOVER'||info.closeoutId?'handover':'add'))&&(!rawInbound||!editing&&(!actionMode||actionMode==='cancel'))" :disabled="busy||uncertain" type="danger" @click="action('cancel')">{{rawInbound&&actionMode==='cancel'?'确认取消申请':'取消申请'}}</el-button>
   </template>
   <el-button v-if="info?.status==='POSTED'&&!info.batchId&&!info.closeoutId&&permission.has('metal:warehouse-order:reverse')" :disabled="busy||needsReload||uncertain" type="danger" @click="action('reverse')">{{rawInbound&&actionMode==='reverse'?'确认整体冲销':'整体冲销'}}</el-button>
   <el-button v-if="rawInbound&&(actionMode||editing)" :disabled="busy||uncertain" @click="actionMode='';editing=false">返回验收详情</el-button>
  </template>
 </el-dialog>
</template>
<script setup lang="ts">
import CustomerSnapshot from './CustomerSnapshot.vue'
import {ref,computed,onBeforeUnmount} from 'vue'
import {onBeforeRouteLeave,onBeforeRouteUpdate} from 'vue-router'
import {ElMessage} from 'element-plus'
import BusinessDecimalInput from '@/components/BusinessDecimalInput.vue'
import useUserStore from '@/store/modules/user'
import {useActionPermission} from '@/composables/useActionPermission'
import {warehouseFlow,warehouseFlowList,warehouseFlowAction,warehouseOptions,stateNames,orderTypeNames,type Row} from '@/api/metal/warehouse'
import {decimalText} from '@/utils/business-decimal'
import {createUUID} from '@/utils/uuid'
import {getToken} from '@/utils/auth'
import {useInventoryRefresh} from '@/composables/useInventoryRefresh'
const emit=defineEmits<{saved:[]}>(),user=useUserStore(),permission=useActionPermission()
const visible=ref(false),loading=ref(false),busy=ref(false),info=ref<Row>(),records=ref<Row[]>([]),factories=ref<Row[]>([]),factoryId=ref(''),reason=ref(''),error=ref(''),needsReload=ref(false),editing=ref(false),actionMode=ref('')
const kinds:Record<string,string>={OUTBOUND:'出库实领',RAW_INBOUND:'原料入库',HANDOVER:'加工交接',PRODUCT_RETURN:'成品退回',CUSTOMER_RETURN:'返还客户',STORAGE:'客户余料代存',OFFSET:'客户余料抵料'},stages:Record<string,string>={RECIPIENT:'待指定领用人',PROCESSORS:'待加工人员完整申报',KEEPER:'待指定库管',DONE:'已完成'}
const identity=()=>JSON.stringify([user.token,String(user.id),[...user.permissions].sort()]);let openedIdentity=''
const sessionCurrent=()=>user.sessionReady&&!!user.token&&user.token===getToken()&&openedIdentity===identity()
let epoch=0,disposed=false;const attempts=new Map<string,string>()
const uncertain=ref(false);let pendingRequest:{suffix:string;payload:Row}|undefined,baseline=''
const stamp=(value:Row)=>JSON.stringify([value.version,value.status,value.stage,(value.groups??[]).map((g:Row)=>[g.id,g.status,g.confirmedBy,(g.lines??[]).map((l:Row)=>[l.id,l.stock?.version,l.stock?.netWeight,l.stock?.availableWeight])])])
const pending=computed(()=>!!info.value&&['WAITING','COLLECTING','RETURNED'].includes(info.value.status))
const rawInbound=computed(()=>info.value?.kind==='RAW_INBOUND')
const receiptStatus=computed(()=>({WAITING:'待库管验收',COLLECTING:'部分仓库已验收',RETURNED:'已退回待修订',POSTED:'已验收入库',CANCELLED:'已取消',VOID:'已冲销'}[String(info.value?.status)]||'验收状态待核对'))
const reasonLabel=computed(()=>editing.value?'修订原因':({reject:'退回原因',cancel:'取消原因',reverse:'冲销原因'}[actionMode.value]||'操作原因'))
const eventNames:Record<string,string>={CREATE:'创建申请',CONFIRM:'库管验收',POST:'验收入库',REJECT:'退回修订',REVISE:'修订申请',CANCEL:'取消申请',REVERSE:'冲销',HANDOVER:'提交加工交接',CLOSEOUT:'提交收尾申请'}
const canReject=computed(()=>info.value?.groups.some((g:Row)=>String(g[info.value?.stage==='RECIPIENT'?'recipientId':'keeperId'])===String(user.id))&&permission.has('metal:warehouse-order:'+(info.value?.stage==='RECIPIENT'?'receive':'accept')))
const canRevise=computed(()=>info.value&&!info.value.closeoutId&&info.value.kind!=='HANDOVER'&&permission.has('metal:warehouse-order:add')&&(String(info.value.requestActor)===String(user.id)||info.value.groups.some((g:Row)=>String(g.keeperId)===String(user.id))))
const raw=(line:Row)=>(line.stock?.category||line.line.category)==='RAW'
function personName(name:unknown,deleted:unknown){return String(name??'').trim()||(String(deleted)==='2'?'账号已删除':'历史未记录姓名')}
function keeperName(group:Row){return group.keeperName||(group.keeperId?'历史未记录姓名':'尚未指定')}
function weightEvidence(value:unknown){return value==null?'未填写':decimalText(value)+' g'}
function ownership(row:Row){return (row.line.materialOwnership||row.stock?.materialOwnership)==='FACTORY'?'工厂自有':row.line.organization||row.line.customerCompanyName||row.stock?.customerCompanyName||'历史未记录归属'}
function historyMaterial(id:unknown){for(const g of [...(info.value?.groups??[]),...(info.value?.revisions??[])]){const line=g.lines.find((l:Row)=>String(l.id)===String(id));if(line)return line.line.code||line.stock?.code||'历史未记录编码'}return '历史未记录物料'}
function acceptanceHint(group:Row){
 const status=info.value?.status
 if(status==='RETURNED')return '申请已退回，需修订后再验收'
 if(status==='CANCELLED')return '申请已取消，无需验收'
 if(status==='VOID')return '申请已冲销，无需验收'
 if(status==='POSTED')return '已验收入库'
 if(group.status==='CONFIRMED')return '本仓已验收，等待其他仓库完成'
 if(!group.keeperId)return '尚未指定仓管员，请先修订申请'
 if(!pending.value||group.status!=='WAITING'||info.value?.stage!=='KEEPER')return '当前状态暂不可验收，请重新加载核对'
 if(String(group.keeperId)!==String(user.id))return `请由仓管员${keeperName(group)}登录验收`
 if(!permission.has('metal:warehouse-order:accept'))return '当前账号缺少库管验收权限'
 if(needsReload.value)return '申请版本已变化，请重新加载后验收'
 return '当前账号可验收，请填写库管实收净重；每条重量差异须单独说明。'
}
function eventContent(row:Row){try{return JSON.parse(row.content)}catch{return {}}}
function eventReason(row:Row){return eventContent(row)?.reason||'—'}
function eventMeasurements(row:Row){const value=eventContent(row);return row.action==='CONFIRM'&&Array.isArray(value.lines)?value.lines:[]}
function canConfirm(group:Row){return pending.value&&info.value?.status!=='RETURNED'&&info.value?.stage!=='PROCESSORS'&&group.status==='WAITING'&&String(group[info.value?.stage==='RECIPIENT'?'recipientId':'keeperId'])===String(user.id)&&permission.has('metal:warehouse-order:'+(info.value?.stage==='RECIPIENT'?'receive':'accept'))}
function hydrate(value:Row){baseline=stamp(value);for(const group of value.groups)for(const line of group.lines)Object.assign(line,{editWeight:line.actualWeight??line.plannedWeight,editReason:line.differenceReason??'',editPlan:line.plannedWeight,editQuantity:line.line.quantity??0});info.value=value;actionMode.value=''}
function current(round:number){return ()=>!disposed&&visible.value&&round===epoch&&sessionCurrent()}
async function open(id?:string){if(busy.value||uncertain.value||!user.sessionReady||!user.token||user.token!==getToken())return;openedIdentity=identity();epoch++;visible.value=true;info.value=undefined;records.value=[];error.value='';reason.value='';actionMode.value='';editing.value=false;needsReload.value=false;attempts.clear();if(id){await reload(id);return}const round=epoch;loading.value=true;try{const r=await warehouseOptions('factories',undefined,undefined,current(round));if(!current(round)())return;factories.value=r.data;factoryId.value=r.data[0]?.id??'';await loadList()}catch(e:any){if(current(round)())error.value=e?.message||'资料加载失败'}finally{if(current(round)())loading.value=false}}
async function loadList(){if(!factoryId.value)return;const round=++epoch;loading.value=true;try{const r=await warehouseFlowList(factoryId.value,current(round));if(current(round)())records.value=r.data}catch(e:any){if(current(round)())error.value=e?.message||'待办加载失败'}finally{if(current(round)())loading.value=false}}
async function reload(id:string){if(busy.value||uncertain.value)return;const round=++epoch;loading.value=true;error.value='';try{const r=await warehouseFlow(id,current(round));if(!current(round)())return;hydrate(r.data);needsReload.value=false;editing.value=false;attempts.clear()}catch(e:any){if(current(round)())error.value=e?.message||'申请加载失败'}finally{if(current(round)())loading.value=false}}
async function submit(suffix:string,payload:Row){if(!current(epoch)()||busy.value||needsReload.value||uncertain.value)return;const key=JSON.stringify([suffix,payload]);if(!attempts.has(key))attempts.set(key,createUUID());pendingRequest={suffix,payload:JSON.parse(JSON.stringify({...payload,requestId:attempts.get(key)}))};await retryRequest()}
async function retryRequest(){if(!current(epoch)()||busy.value||!pendingRequest)return;const round=epoch,attempt=pendingRequest;busy.value=true;error.value='';try{const response=await warehouseFlowAction(attempt.suffix,attempt.payload,current(round));if(!current(round)()||pendingRequest!==attempt)return;uncertain.value=false;pendingRequest=undefined;hydrate(response.data);needsReload.value=false;editing.value=false;attempts.clear();emit('saved');ElMessage.success('操作已登记')}catch(e:any){if(current(round)()&&pendingRequest===attempt){uncertain.value=typeof e?.code!=='number';if(!uncertain.value)pendingRequest=undefined;error.value=uncertain.value?'提交结果未知，请重试原请求确认结果；当前输入已保留。':e?.message||'操作失败，输入已保留';if(!uncertain.value&&/版本|重新加载|刷新/.test(error.value))needsReload.value=true}}finally{if(current(round)())busy.value=false}}
const {failed:refreshFailed}=useInventoryRefresh(async ctx=>{if(!factoryId.value)return;const round=epoch,factory=factoryId.value,valid=()=>ctx.isCurrent()&&current(round)()&&!info.value&&factory===factoryId.value;const r=await warehouseFlowList(factory,valid,true);if(valid())records.value=r.data},computed(()=>!!info.value),{allowed:()=>visible.value&&permission.hasAny(['metal:warehouse-order:query','metal:warehouse-order:add','metal:warehouse-order:receive','metal:warehouse-order:accept','metal:warehouse-order:handover']),reset:()=>{epoch++;visible.value=false;info.value=undefined;busy.value=false;uncertain.value=false;pendingRequest=undefined}}, {paused:computed(()=>busy.value||loading.value||uncertain.value),probe:async ctx=>{if(!info.value)return;const round=epoch,id=info.value.id,valid=()=>ctx.isCurrent()&&current(round)()&&info.value?.id===id&&!busy.value&&!loading.value&&!uncertain.value;const r=await warehouseFlow(id,valid,true);if(valid()&&stamp(r.data)!==baseline){needsReload.value=true;error.value='业务资料已更新，请重新加载并核对；当前输入已保留。'}}})
async function confirm(group:Row){if(!info.value||!canConfirm(group))return;if(group.lines.some((l:Row)=>!raw(l)&&Number(l.editQuantity)!==Number(l.line.quantity??0))){error.value='实物件数不符，请填写退回原因并退回库管修订';return}if(group.lines.some((l:Row)=>Number(l.editWeight)!==Number(l.plannedWeight)&&!String(l.editReason).trim())){error.value='每一条重量差异均须填写原因';return}await submit(`${info.value.id}/groups/${group.id}/confirm`,{version:info.value.version,lines:group.lines.map((l:Row)=>({lineId:l.id,netWeight:l.editWeight,quantity:raw(l)?0:Number(l.editQuantity),differenceReason:l.editReason||null}))})}
async function action(name:string){if(rawInbound.value&&actionMode.value!==name){actionMode.value=name;reason.value='';error.value='';return}if(!info.value||!reason.value.trim()){error.value=rawInbound.value?'请填写'+reasonLabel.value:'请填写操作原因';return}await submit(`${info.value.id}/${name}`,{version:info.value.version,reason:reason.value.trim()})}
async function revise(){if(!info.value||!reason.value.trim()){error.value='请填写修订原因';return}const documents=info.value.groups.map((g:Row)=>({...g.order,lines:g.lines.map((l:Row)=>({...l.line,netWeight:l.editPlan,quantity:raw(l)?0:Number(l.editQuantity)}))}));await submit(`${info.value.id}/revise`,{version:info.value.version,reason:reason.value.trim(),documents})}
function closed(){if(visible.value||busy.value||uncertain.value)return;pendingRequest=undefined;epoch++;info.value=undefined;records.value=[];error.value='';attempts.clear()}
permission.onChange(()=>{pendingRequest=undefined;uncertain.value=false;busy.value=false;epoch++;visible.value=false;info.value=undefined;records.value=[];attempts.clear()})
onBeforeRouteLeave(()=>{if(busy.value||uncertain.value)return false;visible.value=false;closed()});onBeforeRouteUpdate(()=>{if(busy.value||uncertain.value)return false;visible.value=false;closed()});onBeforeUnmount(()=>{disposed=true;epoch++});defineExpose({open,isOpen:visible})
</script>
<style scoped>.flow-dialog{min-width:0}.receipt-heading{display:flex;align-items:center;flex-wrap:wrap;gap:10px;color:#606266}.receipt-people{display:flex;flex-wrap:wrap;gap:8px 24px}.flow-group{margin:18px 0;padding:14px;border:1px solid #e4e7ed;border-radius:6px}.flow-group h4{margin:0 0 10px;overflow-wrap:anywhere}.flow-group>.el-button{margin-top:12px}.flow-dialog .el-alert{margin:12px 0}.flow-dialog details{margin-top:14px}.flow-dialog summary{cursor:pointer;color:#606266}.flow-dialog :deep(.el-table__body-wrapper){overflow-x:auto}@media(max-width:600px){.flow-group{padding:8px}.flow-dialog p{overflow-wrap:anywhere}.flow-dialog :deep(.el-alert__content){min-width:0;overflow-wrap:anywhere}}</style>

<style scoped>.warehouse-flow-dialog :deep(.el-table .cell),.warehouse-flow-dialog :deep(.el-table input){text-align:center}</style>
