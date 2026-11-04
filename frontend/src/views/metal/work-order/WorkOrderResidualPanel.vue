<template>
 <section class="residual-panel" v-loading="loading">
  <h3>客户余料收尾与厂料自补</h3>
  <template v-if="context">
   <p>厂料累计领入：{{decimalText(context.factoryIssuedWeight)}} g；加工人确认厂料余料：{{decimalText(context.factoryReturnedWeight)}} g；厂料净投入：{{decimalText(context.factoryNetInputWeight)}} g。</p>
   <el-table :data="context.factorySupplement" border><el-table-column label="厂料业务单据" prop="orderNo" min-width="150"/><el-table-column label="物料编码" prop="code" min-width="130"/><el-table-column label="业务类型" min-width="120"><template #default="{row}">{{orderTypeNames[row.type]||row.type}}</template></el-table-column><el-table-column label="加工称重（g）" prop="processingWeight" :formatter="decimalColumn" min-width="145"/><el-table-column label="库管实物重量（g）" prop="keeperWeight" :formatter="decimalColumn" min-width="165"/><el-table-column label="状态"><template #default="{row}">{{stateNames[row.status]||row.status}}</template></el-table-column></el-table>
   <p>客户实际剩余余料：{{decimalText(context.customerRemainingWeight)}} g；厂料余料：{{decimalText(context.factoryRemainingWeight)}} g。厂料单独管理，不纳入客户收尾。</p>
   <p v-if="context.closeout">客户收尾：{{modes[context.closeout.mode]}} · {{statuses[context.closeout.status]||context.closeout.status}}</p>
   <el-table :data="context.lots" border><el-table-column label="物料编码" prop="code" min-width="130"/><el-table-column label="归属" min-width="100"><template #default="{row}">{{row.materialOwnership==='FACTORY'?'工厂自有':row.materialOwnership==='CUSTOMER'?'客户料':'待核对'}}</template></el-table-column><el-table-column label="来源还库单" prop="sourceOrderNo" min-width="155"/><el-table-column label="仓库" prop="warehouseName" min-width="130"/><el-table-column label="退回净重（g）" prop="initialWeight" :formatter="decimalColumn" min-width="140"/><el-table-column label="实际剩余（g）" prop="remainingWeight" :formatter="decimalColumn" min-width="140"/></el-table>
   <div class="residual-actions"><el-button v-for="flow in context.flows??[]" :key="flow.id" @click="flowDialog?.open(String(flow.id))">查看收尾交接 #{{flow.id}}</el-button></div>
   <el-form v-if="canCreate" label-position="top" :disabled="busy||needsReload||uncertain">
    <div class="residual-fields"><el-form-item label="本工单整笔收尾方向"><el-select v-model="mode" :disabled="!!context.closeout"><el-option v-for="(label,value) in modes" :key="value" :label="label" :value="value"/></el-select></el-form-item><el-form-item label="指定仓管员"><el-select v-model="keeperId" filterable><el-option v-for="p in people" :key="p.id" :value="p.id" :label="personLabel(p)"/></el-select></el-form-item><el-form-item v-if="mode==='OFFSET'" label="实际抵料净重（g）"><BusinessDecimalInput v-model="weight"/></el-form-item></div>
    <el-form-item v-if="mode==='OFFSET'" label="同厂同客户较早的欠料定制工单"><el-select v-model="offsetWorkOrderId" :disabled="!!context.closeout" filterable><el-option v-for="w in context.offsetOrders" :key="w.id" :value="w.id" :label="w.orderNo+' / '+w.orderDate"/></el-select></el-form-item>
    <section v-if="selectedPieceStocks.length" class="residual-piece-stocks">
     <p>按件原料须同时办理件数和实测净重。部分办理须填写实际件数；整库办理可留空，使用库存全部可用件数。</p>
     <el-table :data="selectedPieceStocks" row-key="inventoryId" border><el-table-column label="物料编码" prop="code" min-width="140"/><el-table-column label="本次选中净重（g）" prop="selectedWeight" :formatter="decimalColumn" min-width="160"/><el-table-column label="库存可用净重（g）" prop="availableWeight" :formatter="decimalColumn" min-width="160"/><el-table-column label="库存可用件数" prop="availableQuantity" min-width="130"/><el-table-column label="本次办理件数" min-width="235"><template #default="{row}"><el-input v-model="pieceInputs[row.inventoryId]" :aria-label="'收尾件数 '+row.code+' / '+row.inventoryId" inputmode="numeric" maxlength="16" :placeholder="row.full?'整库可留空':'部分办理须填写件数'"/></template></el-table-column></el-table>
    </section>
    <p>{{mode==='RETURN'?'由本单实际加工人员发起，指定库管确认实际交付后扣库。':mode==='STORAGE'?'保留客户库存，库管确认后登记代存，不再生成入库流水。':'按实际抵料重量将客户料转为工厂料；记录双边库存流水，不新增实物，不登记钱款或欠料结清。'}}</p>
    <el-form-item label="收尾原因"><el-input v-model="reason" type="textarea" maxlength="500" show-word-limit/></el-form-item><el-button type="primary" :loading="busy" @click="save">本人发起客户余料收尾</el-button>
   </el-form>
   <p v-else-if="!['COMPLETED','CLOSED_PARTIAL'].includes(context.status)">生产完成与客户余料收尾分别记录；生产完成后办理客户余料收尾。</p>
   <template v-if="context.closeout&&['DONE','PARTIAL'].includes(context.closeout.status)&&permission.has('metal:warehouse-order:reverse')"><el-input v-model="reverseReason" placeholder="整体撤回原因" maxlength="500" :disabled="busy||uncertain"/><el-button type="danger" :loading="busy" @click="reverse">整体撤回收尾</el-button></template>
  </template>
  <el-alert v-if="uncertain" title="提交结果待确认，原请求和称重已保留" type="warning" :closable="false"/><el-button v-if="uncertain" :loading="busy" @click="retryAttempt">重试原请求</el-button><el-alert v-if="refreshFailed" title="收尾资料更新失败，当前显示可能过期" type="warning" :closable="false"/><el-alert v-if="error" :title="error" type="error" :closable="false"/>
  <el-button :disabled="busy||loading||uncertain" @click="load">重新加载收尾资料</el-button>
  <WarehouseFlowDialog ref="flowDialog" @saved="load"/>
 </section>
</template>
<script setup lang="ts">
import {ref,computed,watch,onBeforeUnmount} from 'vue'
import {useInventoryRefresh,type InventoryRefreshContext} from '@/composables/useInventoryRefresh'
import {getToken} from '@/utils/auth'
import useUserStore from '@/store/modules/user'
import {onBeforeRouteLeave} from 'vue-router'
import {ElMessage} from 'element-plus'
import BusinessDecimalInput from '@/components/BusinessDecimalInput.vue'
import WarehouseFlowDialog from '../warehouse-common/WarehouseFlowDialog.vue'
import {useActionPermission} from '@/composables/useActionPermission'
import {warehouseCloseoutContext,warehouseFlowAction,warehouseOptions,orderTypeNames,stateNames,type Row} from '@/api/metal/warehouse'
import {decimalText,decimalColumn} from '@/utils/business-decimal'
import {personLabel} from '@/utils/person-label'
import {createUUID} from '@/utils/uuid'
import {managesPieces} from '@/utils/inventory-measurement'
const props=defineProps<{workOrderId:string}>(),permission=useActionPermission(),context=ref<Row>(),people=ref<Row[]>([]),loading=ref(false),busy=ref(false),needsReload=ref(false),mode=ref('RETURN'),keeperId=ref(''),offsetWorkOrderId=ref(''),weight=ref(''),reason=ref(''),reverseReason=ref(''),error=ref(''),flowDialog=ref<InstanceType<typeof WarehouseFlowDialog>>()
const modes:Record<string,string>={RETURN:'返还客户',STORAGE:'客户代存',OFFSET:'抵旧欠料'},statuses:Record<string,string>={WAITING:'待库管确认',PARTIAL:'部分完成',DONE:'已完成',VOID:'已撤回'}
const canCreate=computed(()=>context.value&&['COMPLETED','CLOSED_PARTIAL'].includes(context.value.status)&&Number(context.value.customerRemainingWeight)>0&&(!context.value.closeout||context.value.closeout.status==='PARTIAL')&&permission.has('metal:warehouse-order:handover'))
const user=useUserStore(),uncertain=ref(false),baseline=ref(''),hydrating=ref(false),pieceInputs=ref<Record<string,string>>({})
let epoch=0,operationEpoch=0,disposed=false,attemptBody='',attemptId='',attempt:{suffix:string;payload:Row}|undefined
const canRead=()=>user.sessionReady&&!!user.token&&user.token===getToken()&&permission.hasAny(['metal:work-order:query','metal:warehouse-order:query','metal:warehouse-order:handover','metal:warehouse-order:accept'])
const draftKey=()=>JSON.stringify([mode.value,keeperId.value,offsetWorkOrderId.value,weight.value,reason.value,reverseReason.value,Object.entries(pieceInputs.value).sort(([a],[b])=>a<b?-1:a>b?1:0)])
const dirty=computed(()=>!hydrating.value&&!!baseline.value&&draftKey()!==baseline.value)
function weightUnits(value:unknown){const text=String(value??'').trim();if(!/^\d{1,14}(\.\d{1,6})?$/.test(text))return null;const [whole,fraction='']=text.split('.');return BigInt(whole)*1000000n+BigInt(fraction.padEnd(6,'0'))}
function exactWeight(value:bigint){return String(value/1000000n)+'.'+String(value%1000000n).padStart(6,'0')}
type PieceStockSelection={inventoryId:string;code:string;selectedUnits:bigint;selectedWeight:string;availableUnits:bigint|null;availableWeight:unknown;availableQuantity:number;full:boolean}
const selectedPieceStocks=computed(()=>{
 const stocks=new Map<string,PieceStockSelection>();let left=mode.value==='OFFSET'?weightUnits(weight.value)??0n:null
 for(const lot of context.value?.lots??[]){
  if(lot.materialOwnership!=='CUSTOMER'||lot.status!=='OPEN'||context.value?.customerId!=null&&String(lot.customerId??'')!==String(context.value.customerId))continue
  if(left===0n)break
  const remaining=weightUnits(lot.remainingWeight);if(remaining==null||remaining<=0n)continue
  const take=left==null?remaining:left<remaining?left:remaining;if(left!=null)left-=take
  if(!managesPieces({category:'RAW',effectiveMeasurementMode:lot.effectiveMeasurementMode})||!(Number(lot.measurementPolicyVersion)>=3))continue
  const id=String(lot.inventoryId??'');let stock=stocks.get(id)
  if(!stock){stock={inventoryId:id,code:String(lot.code??id),selectedUnits:0n,selectedWeight:'0',availableUnits:weightUnits(lot.availableWeight),availableWeight:lot.availableWeight,availableQuantity:Number(lot.availableQuantity),full:false};stocks.set(id,stock)}
  stock.selectedUnits+=take
 }
 return [...stocks.values()].map(stock=>({...stock,selectedWeight:exactWeight(stock.selectedUnits),full:stock.availableUnits!=null&&stock.selectedUnits===stock.availableUnits}))
})
function closeoutPieceQuantities(){
 const quantities:Record<string,number>={};let supplied=false
 for(const stock of selectedPieceStocks.value){
  if(!/^\d+$/.test(stock.inventoryId)||stock.availableUnits==null||stock.availableUnits<=0n||!Number.isSafeInteger(stock.availableQuantity)||stock.availableQuantity<1)throw Error('按件库存资料不完整，请重新加载后核对')
  if(stock.selectedUnits>stock.availableUnits)throw Error('本次选中净重超过库存可用净重，请重新加载后核对')
  const text=String(pieceInputs.value[stock.inventoryId]??'').trim()
  if(!text){if(!stock.full)throw Error('部分办理须填写件数；不能按重量比例推算');quantities[stock.inventoryId]=stock.availableQuantity;continue}
  const quantity=Number(text)
  if(!/^\d+$/.test(text)||!Number.isSafeInteger(quantity)||quantity<1)throw Error('本次办理件数须为正整数')
  if(quantity>stock.availableQuantity)throw Error('本次办理件数超过库存可用件数')
  supplied=true;quantities[stock.inventoryId]=quantity
 }
 return supplied?quantities:undefined
}

function resetAccess(){pieceInputs.value={};epoch++;operationEpoch++;context.value=undefined;people.value=[];loading.value=false;busy.value=false;uncertain.value=false;needsReload.value=false;attempt=undefined;attemptBody='';attemptId='';baseline.value=''}
async function load(){if(busy.value||uncertain.value)return false;return readContext(false)}
async function readContext(background:boolean,refresh?:InventoryRefreshContext){if(!canRead()){resetAccess();return false}if(loading.value)return false;const round=++epoch,work=props.workOrderId,session=user.token,account=String(user.id),valid=()=>!disposed&&round===epoch&&work===props.workOrderId&&session===user.token&&account===String(user.id)&&canRead(),current=()=>valid()&&(!refresh||refresh.isCurrent()&&!busy.value&&!uncertain.value);loading.value=true;if(!background)error.value='';try{const response=await warehouseCloseoutContext(work,current,background);if(!current())return false;const list=permission.has('metal:warehouse-order:handover')?await warehouseOptions('people',response.data.factoryId,undefined,current,undefined,background):{data:[]};if(!current())return false;hydrating.value=true;pieceInputs.value={};context.value=response.data;people.value=list.data;mode.value=response.data.closeout?.mode??'RETURN';offsetWorkOrderId.value=response.data.closeout?.offsetWorkOrderId??'';weight.value=String(response.data.customerRemainingWeight);needsReload.value=false;if(!background){attempt=undefined;attemptBody='';attemptId=''}baseline.value=draftKey();hydrating.value=false;return true}catch(e:any){if(current())error.value=background?'收尾资料更新失败，当前显示可能过期':e?.message||'收尾资料加载失败';return false}finally{if(valid())loading.value=false}}
async function probe(refresh:InventoryRefreshContext){const work=props.workOrderId,round=epoch,current=()=>refresh.isCurrent()&&canRead()&&work===props.workOrderId&&round===epoch&&!busy.value&&!uncertain.value;const response=await warehouseCloseoutContext(work,current,true);if(current()&&JSON.stringify(response.data)!==JSON.stringify(context.value)){needsReload.value=true;error.value='资料已变化，请重新加载后核对，当前输入已保留'}}
async function submit(suffix:string,payload:Row){if(!canRead()||busy.value||loading.value)return;const serialized=JSON.stringify([suffix,payload]);if(attemptBody!==serialized){attemptBody=serialized;attemptId=createUUID()}attempt={suffix,payload:JSON.parse(JSON.stringify(payload))};const round=++operationEpoch,work=props.workOrderId,session=user.token,account=String(user.id),current=()=>!disposed&&round===operationEpoch&&work===props.workOrderId&&session===user.token&&account===String(user.id)&&canRead();busy.value=true;error.value='';try{const response=await warehouseFlowAction(suffix,{requestId:attemptId,...payload},current);if(!current())return;uncertain.value=false;attempt=undefined;if(response.data?.id)flowDialog.value?.open(String(response.data.id));busy.value=false;baseline.value='';needsReload.value=false;invalidateRefresh();if(current())ElMessage.success('收尾操作已登记')}catch(e:any){if(current()){uncertain.value=!!e?.isAxiosError&&(!e.response||e.response.status>=500);error.value=e?.message||'操作失败，输入已保留';if(/版本|刷新|重新加载/.test(error.value))needsReload.value=true}}finally{if(current())busy.value=false}}
async function retryAttempt(){if(uncertain.value&&attempt)await submit(attempt.suffix,attempt.payload)}
async function save(){if(!context.value||busy.value||loading.value||uncertain.value||needsReload.value)return;const offsetWeight=weightUnits(weight.value);if(!keeperId.value||!reason.value.trim()||mode.value==='OFFSET'&&(!offsetWorkOrderId.value||offsetWeight==null||offsetWeight<=0n)){error.value='请选择仓管员、方向并填写原因；抵料须选择旧欠料工单及实际净重';return}let pieceQuantities:Record<string,number>|undefined;try{pieceQuantities=closeoutPieceQuantities()}catch(e:any){error.value=e.message;return}await submit('closeout/'+props.workOrderId,{...(pieceQuantities?{pieceQuantities}:{}),version:context.value.version,mode:mode.value,keeperId:keeperId.value,offsetWorkOrderId:mode.value==='OFFSET'?offsetWorkOrderId.value:null,weight:mode.value==='OFFSET'?weight.value:context.value.customerRemainingWeight,reason:reason.value})}
async function reverse(){if(!context.value?.closeout||busy.value||loading.value||uncertain.value||needsReload.value)return;if(!reverseReason.value.trim()){error.value='请填写整体撤回原因';return}await submit('closeout/'+props.workOrderId+'/reverse',{version:context.value.version,reason:reverseReason.value})}
const {failed:refreshFailed,invalidate:invalidateRefresh}=useInventoryRefresh(refresh=>readContext(true,refresh),computed(()=>dirty.value||needsReload.value),{allowed:canRead,reset:resetAccess},{probe,paused:computed(()=>busy.value||loading.value||uncertain.value)})
watch(()=>props.workOrderId,()=>{resetAccess();void load()},{immediate:true});onBeforeRouteLeave(()=>{if(busy.value||uncertain.value)return false});onBeforeUnmount(()=>{disposed=true;epoch++;operationEpoch++})
</script>
<style scoped>.residual-panel{margin-top:18px;padding:18px;border:1px solid #e4e7ed;border-radius:6px;background:white}.residual-fields{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:14px}.residual-actions{display:flex;flex-wrap:wrap;gap:10px;margin:16px 0}.residual-panel>.el-button{margin-top:12px}@media(max-width:768px){.residual-fields{grid-template-columns:1fr}.residual-panel{padding:12px}}</style>

<style scoped>.residual-panel :deep(th.el-table__cell),.residual-panel :deep(td.el-table__cell){text-align:center}</style>

<style scoped>.residual-piece-stocks{margin:16px 0}.residual-piece-stocks :deep(input){text-align:center}</style>
