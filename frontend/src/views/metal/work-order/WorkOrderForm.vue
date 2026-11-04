<template>
 <div v-if="opening" role="status">正在加载工单资料… <el-button link @click="cancelOpen">取消加载</el-button></div>
 <div v-if="openError" role="alert">工单资料加载失败 <el-button link @click="retryOpen()">重试</el-button></div>
 <el-dialog :key="dialogGeneration" v-model="opened" :title="form.id?'编辑工单':'添加工单'" width="min(920px,94vw)" top="5vh" class="order-dialog" :close-on-click-modal="false" :close-on-press-escape="!saving&&!pendingSave&&!customerBlocked" :show-close="!saving&&!pendingSave&&!customerBlocked">
  <el-form ref="formRef" class="work-order-fields" :model="form" :rules="rules" scroll-to-error label-width="120px" label-position="left" :disabled="saving||optionsLoading||!!pendingSave">
   <el-form-item label="生产单据编号"><el-input :model-value="form.orderNo" readonly/></el-form-item>
   <el-form-item label="工厂名称" prop="factoryId"><el-select class="work-order-factory-select" popper-class="work-order-factory-options" :model-value="form.factoryId" :disabled="!isAdmin||!!form.id||customerBlocked" fit-input-width :teleported="true" @visible-change="resetSelectScroll" @change="changeFactory"><el-option v-for="option in factories" :key="option.id" :value="option.id" :label="option.name"/></el-select></el-form-item>
   <p class="time-description">{{raw?'独立原料加工任务仅安排前处理，不生产最终产品。':'选择最终产品及完整工艺路线；前处理、融定和后加工沿用同一工单编号，实际原料在领料时选择。'}}</p>
   <el-alert v-if="stale" title="数据已更新，请重新核对；当前草稿已保留，不能继续提交旧版本。" type="warning" :closable="false" show-icon><el-button link type="primary" @click="reloadDraft">重新加载资料</el-button></el-alert>
   <el-alert v-if="refreshFailed" title="自动更新失败，当前资料可能已过期；输入已保留。" type="warning" :closable="false"/>
   <el-alert v-if="pendingSave" title="提交结果尚未确认，请重试原请求，勿另建重复工单。" type="warning" :closable="false"/>
   <el-form-item v-if="!raw||form.processingType!=='DIRECT'" label="客户名称" prop="customerId"><el-select :model-value="form.customerId" filterable :disabled="!form.factoryId" :placeholder="legacyCustomer&&form.factoryId===originalFactory&&!form.customerId?form.customer+'（历史客户）':'请选择客户'" @change="changeCustomer"><template #label>{{form.customer}} / {{form.customerSnapshot?.companyName??'历史未记录'}}</template><el-option v-if="form.customerId&&!customers.some(c=>c.id===form.customerId)" :value="form.customerId" :label="form.customer+'（已删除，请重选）'" disabled/><el-option v-for="option in customers" :key="option.id" :value="option.id" :label="option.name+' / '+option.companyName"/></el-select><el-button link type="primary" :disabled="!form.factoryId||optionsLoading||saving" @click="refreshCustomers">刷新客户</el-button><WarehouseCustomerDialog v-if="hasFormAccess()&&!saving&&!pendingSave" :factory-id="form.factoryId" :factory-name="factories.find(f=>f.id===form.factoryId)?.name??''" :context-key="dialogGeneration" :is-context-current="customerContextCurrent" @created="customerCreated" @blocked="customerBlocked=$event"/></el-form-item>
   <el-form-item v-for="field in raw&&form.processingType==='DIRECT'?[]:customerFields" :key="field.key" :label="field.label"><el-input :model-value="customerText(field.key)" readonly/></el-form-item>
   <el-form-item label="加工类型" prop="processingType"><el-select v-model="form.processingType" @change="processingChanged" placeholder="请选择加工类型"><el-option v-for="(label,value) in raw?rawProcessingTypes:processingTypes" :key="value" :value="value" :label="label"/></el-select></el-form-item>
   <el-form-item label="产线" prop="productionLineId"><el-select :model-value="form.productionLineId" fit-input-width :teleported="false" :disabled="!form.factoryId||!!form.id" @visible-change="resetSelectScroll" @change="changeLine"><el-option v-for="option in lines" :key="option.id" :value="option.id" :label="option.name"/></el-select></el-form-item><p v-if="form.factoryId&&!optionsLoading&&!opening&&!lines.length" class="order-error">当前工厂暂无可用产线，请先在产线管理中创建并绑定</p>
   <el-form-item label="开单日期" prop="orderDate"><el-date-picker v-model="form.orderDate" type="date" value-format="YYYY-MM-DD" format="YYYY-MM-DD" :teleported="true" placeholder="请选择日期"/></el-form-item>
   <el-form-item label="负责人" prop="ownerId"><el-select class="person-select" popper-class="person-select-options" v-model="form.ownerId" filterable fit-input-width :teleported="false" :disabled="!form.factoryId" @visible-change="resetSelectScroll" :class="{'person-selected':Array.isArray(form.ownerId)?form.ownerId.length>0:!!form.ownerId}"><el-option v-for="option in owners" :key="option.id" :value="option.id" :label="ownerLabel(option)"/></el-select></el-form-item>
   <el-button class="add-product" :disabled="!!form.id||!form.productionLineId||form.items.length>=100" @click="form.items.push(blankItem())">{{raw?'添加原料':'添加产品'}}</el-button>
   <div v-for="(item,index) in form.items" :key="item.key||item.id||index" class="order-item">
    <el-form-item v-if="!raw" :label="'选择产品'+(form.items.length>1?' '+(index+1):'')" :prop="`items.${index}.productId`" :rules="[{required:true,message:'请选择产品',trigger:'change'}]"><el-select :model-value="item.productId" fit-input-width :teleported="false" :disabled="!form.productionLineId||!!form.id" @visible-change="resetSelectScroll" @change="value=>changeProduct(item,value)"><el-option v-for="option in availableProducts" :key="option.id" :value="option.id" :label="option.name+(option.hasProcesses?'':'（未配置工序）')" :disabled="!option.hasProcesses"/></el-select><el-button v-if="!form.id&&form.items.length>1" link type="danger" @click="form.items.splice(index,1)">移除</el-button></el-form-item>
    <el-form-item v-if="!raw" label="产品数量" :rules="[requiredRule('产品数量',true,false),...([fieldRule(v=>integerError(quantityInputs.get(item)??v,'产品数量'),['blur','change'])])]" :prop="'items.'+index+'.quantity'" ><el-input v-model="item.quantity" @input="value=>quantityInputs.set(item,String(value))" inputmode="numeric" placeholder="请输入产品数量" style="width:170px"/></el-form-item>
    <template v-if="raw">
     <el-form-item :label="'待处理原料 '+(index+1)" :prop="`items.${index}.materialId`" :rules="required('请选择待处理原料')"><el-select v-model="item.materialId" :disabled="!!form.id" filterable aria-label="待处理原料"><el-option v-for="m in availableMaterials" :key="m.id" :value="m.id" :label="(m.code||'')+' / '+m.name"/></el-select><el-button v-if="!form.id&&form.items.length>1" link type="danger" @click="form.items.splice(index,1)">移除</el-button></el-form-item>
     <el-form-item label="计划投入净重（g）" :prop="`items.${index}.plannedInputWeight`" :rules="[fieldRule(v=>decimalError(v,'计划投入净重',{positive:true}))]"><BusinessDecimalInput v-model="item.plannedInputWeight" aria-label="计划投入净重（g）"/></el-form-item>
     <el-button :disabled="!!form.id||item.plans.length>=100" @click="item.plans.push({processId:'',processName:'',loss:'0',theoreticalLoss:'0',lossUnit:'PERCENT',measurementMode:'WEIGHT',startTime:null,endTime:null})">添加前处理工序</el-button>
    </template>
    <div class="time-description">计划开始、结束时间</div><el-table :data="item.plans" border class="plan-table"><el-table-column label="工序名称" prop="processName" min-width="160"><template #default="{row,$index}"><el-select v-if="raw" :disabled="!!form.id" v-model="row.processId" :aria-label="'前处理工序 '+($index+1)" filterable><el-option v-for="p in rawProcesses" :key="p.id" :value="p.id" :label="p.name" :disabled="item.plans.some(s=>s!==row&&s.processId===p.id)"/></el-select><span v-else>{{row.processName}}</span></template></el-table-column><el-table-column v-if="raw" label="理论损耗（%）" min-width="150"><template #default="{row,$index}"><BusinessDecimalInput v-model="row.theoreticalLoss" :readonly="!!form.id" :max="100" :aria-label="'理论损耗 '+($index+1)"/></template></el-table-column><el-table-column v-if="raw&&!form.id" label="操作" width="70"><template #default="{$index}"><el-button link type="danger" @click="item.plans.splice($index,1)">移除</el-button></template></el-table-column><el-table-column label="产出阶段" min-width="110"><template #default="{row}">{{raw||row.outputStage==='RAW'?'原料':row.outputStage==='SEMI'?'半成品':row.outputStage==='FINISHED'?'成品':'历史未记录'}}</template></el-table-column><el-table-column label="产出计量方式" min-width="120"><template #default="{row}">{{row.measurementMode==='WEIGHT'?'按重量（g）':'按件（件）'}}</template></el-table-column><el-table-column label="开始时间" min-width="235"><template #default="{row,$index}"><el-form-item :prop="`items.${index}.plans.${$index}.startTime`" :rules="planRules(item,$index,'startTime')" label-width="0"><el-date-picker v-model="row.startTime" type="datetime" value-format="YYYY-MM-DD HH:mm:00" format="YYYY-MM-DD HH:mm" :teleported="true" placeholder="请选择开始时间"/></el-form-item></template></el-table-column><el-table-column label="结束时间" min-width="235"><template #default="{row,$index}"><el-form-item :prop="`items.${index}.plans.${$index}.endTime`" :rules="planRules(item,$index,'endTime')" label-width="0"><el-date-picker v-model="row.endTime" type="datetime" value-format="YYYY-MM-DD HH:mm:00" format="YYYY-MM-DD HH:mm" :teleported="true" placeholder="请选择结束时间"/></el-form-item></template></el-table-column></el-table>
    <div v-if="item.productId&&!item.plans.length" class="order-error">产品尚未配置工序，请先维护产品工艺</div>
   </div>
  </el-form>
  <template #footer><el-button :disabled="saving||customerBlocked||!!pendingSave" @click="opened=false">取消</el-button><el-button v-if="actionPermission.has(form.id?'metal:work-order:edit':'metal:work-order:add')" type="primary" :loading="saving" :disabled="stale&&!pendingSave" @click="submit">{{pendingSave?'重试原请求':'确认'}}</el-button></template>
 </el-dialog>
</template>
<script setup lang="ts">
import BusinessDecimalInput from '@/components/BusinessDecimalInput.vue'
import {requiredRule} from '@/utils/input-validation'
import {decimalError,integerRules,integerError,fieldRule,textRules,applyFieldErrors} from '@/utils/input-validation'
import { useActionPermission } from '@/composables/useActionPermission'
const actionPermission=useActionPermission()
import {personLabel} from '@/utils/person-label'

import {getToken} from '@/utils/auth'
import {createUUID} from '@/utils/uuid'
import WarehouseCustomerDialog from '../warehouse-common/WarehouseCustomerDialog.vue'
import {ref,reactive,computed,nextTick,watch,onBeforeUnmount} from 'vue'
import {useInventoryRefresh,type InventoryRefreshContext} from '@/composables/useInventoryRefresh'
import {onBeforeRouteLeave,onBeforeRouteUpdate} from 'vue-router'
import {notifyInventoryChanged} from '@/utils/inventory-change'
import {ElMessage,ElMessageBox} from 'element-plus'
import type {FormInstance,FormRules} from 'element-plus'
import useUserStore from '@/store/modules/user'
import {resetSelectScroll} from '@/utils/reset-select-scroll'
import {reserveOrderNumber,orderDetail,orderOptions,orderProductPlans,orderSave,orderCustomers,processingTypes} from '@/api/metal/work-order'
import type {WorkOrder,WorkOrderItem,OrderOption,CustomerOption,CustomerSnapshot} from '@/api/metal/work-order'
const emit=defineEmits<{saved:[added:boolean]}>(),user=useUserStore(),isAdmin=computed(()=>String(user.id)==='1')
const customerBlocked=ref(false),opened=ref(false),saving=ref(false),optionsLoading=ref(false),stale=ref(false),formRef=ref<FormInstance>()
const productVersions=new Map<string,number>()
const pendingSave=ref<WorkOrder>(),dialogGeneration=ref(0)
let submittedPayload:WorkOrder|undefined
const ownerLabel=personLabel
const blankItem=():WorkOrderItem=>({key:createUUID(),productId:'',materialId:'',plannedInputWeight:'',quantity:1,plans:[]})
const today=()=>new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Shanghai'}).format(new Date())
const raw= computed(()=>form?.taskType==='RAW_PROCESSING')
const rawProcessingTypes={DIRECT:'工厂自有料',MATERIAL:'客户来料'}
const blank=():WorkOrder=>({taskType:'PRODUCTION',requestId:createUUID(),customer:'',customerId:'',processingType:'',factoryId:'',productionLineId:'',ownerId:'',orderDate:today(),items:[blankItem()]})
const form=reactive<WorkOrder>(blank()),factories=ref<OrderOption[]>([]),lines=ref<OrderOption[]>([]),owners=ref<OrderOption[]>([]),availableProducts=ref<OrderOption[]>([])
const availableMaterials=ref<OrderOption[]>([]),rawProcesses=ref<OrderOption[]>([])
const customers=ref<CustomerOption[]>([])
function processingChanged(){if(raw.value&&form.processingType==='DIRECT'){form.customer='工厂自有';form.customerId='';form.customerVersion=undefined;form.customerSnapshot=undefined}}
const customerFields=[{key:'companyName',label:'客户公司名称'},{key:'address',label:'客户地址'},{key:'contactName',label:'联络人名称'},{key:'contactPhone',label:'联络人电话'},{key:'department',label:'部门（非必填）'}] as const
let originalCustomerId:string|undefined,originalCustomerSnapshot:CustomerSnapshot|undefined,legacyCustomer=false,legacyProcessing=false
function customerText(key:keyof CustomerSnapshot){return form.customerSnapshot?.[key]??(legacyCustomer&&form.factoryId===originalFactory&&!form.customerId?'历史未记录':'')}
function changeCustomer(value:string){const selected=customers.value.find(c=>c.id===value);if(!selected)return;form.customerId=value;form.customerVersion=selected.version;form.customerSnapshot=value===originalCustomerId&&originalCustomerSnapshot?originalCustomerSnapshot:{...selected};form.customer=form.customerSnapshot.name}
const required=(message:string)=>[{required:true,message,trigger:'change'}]
function planRules(item:WorkOrderItem,stepIndex:number,field:'startTime'|'endTime'){return [fieldRule(value=>{if(!value)return field==='startTime'?'请选择开始时间':'请选择结束时间';const step=item.plans[stepIndex];if(step.startTime&&step.endTime&&step.startTime>=step.endTime)return '开始时间须早于结束时间';if(field==='startTime'&&stepIndex>0&&item.plans[stepIndex-1].endTime&&(step.startTime??'')<(item.plans[stepIndex-1].endTime??''))return '不能早于上一工序结束时间';return ''},'change')]}
const quantityInputs=new WeakMap<object,string>()
const rules:FormRules={customerId:[fieldRule(v=>!(raw.value&&form.processingType==='DIRECT')&&!v&&(!legacyCustomer||form.factoryId!==originalFactory)?'请选择当前工厂的客户':'','change')],processingType:[fieldRule(v=>!v&&!legacyProcessing?'请选择加工类型':'','change')],factoryId:required('请选择工厂'),productionLineId:required('请选择产线'),ownerId:required('请选择负责人'),orderDate:required('请选择开单日期')}
const opening=ref(false),openError=ref(false)
let originalFactory=''
let epoch=0,dependencyEpoch=0,disposed=false,retryOpen:()=>void=()=>{},formAccess='',accessPermission=''
const identity=()=>JSON.stringify([user.sessionReady,user.token,String(user.id),[...user.permissions].sort()])
const sessionCurrent=()=>user.sessionReady&&!!user.token&&user.token===getToken()
const hasFormAccess=()=>!disposed&&sessionCurrent()&&!!formAccess&&formAccess===identity()&&actionPermission.has(accessPermission)
function invalidateSession(){customerBlocked.value=false;++epoch;++dependencyEpoch;dialogGeneration.value++;pendingSave.value=undefined;submittedPayload=undefined;stale.value=false;formAccess='';opened.value=false;opening.value=false;optionsLoading.value=false;openError.value=false;saving.value=false;Object.assign(form,blank());factories.value=[];lines.value=[];owners.value=[];availableProducts.value=[];customers.value=[];availableMaterials.value=[];rawProcesses.value=[]}

function cancelOpen(){if(saving.value)return;++epoch;++dependencyEpoch;opening.value=false;optionsLoading.value=false;openError.value=false;opened.value=false}
watch(opened,value=>{if(!value&&!saving.value){++epoch;++dependencyEpoch;optionsLoading.value=false;opening.value=false}},{flush:'sync'})
actionPermission.onChange(invalidateSession)
onBeforeUnmount(()=>{disposed=true;++epoch;++dependencyEpoch})
async function loadFactoryOptions(factoryId:string,isCurrent:()=>boolean){return Promise.all([orderOptions('lines',{factoryId},isCurrent),orderOptions('owners',{factoryId},isCurrent),orderCustomers(factoryId,isCurrent),orderOptions('materials',{factoryId},isCurrent),orderOptions('raw-processes',{factoryId},isCurrent)])}
async function open(row?:WorkOrder,taskType:'PRODUCTION'|'RAW_PROCESSING'='PRODUCTION'){
 if(saving.value||disposed||!sessionCurrent()||!actionPermission.has(row?'metal:work-order:edit':'metal:work-order:add'))return;if(pendingSave.value){opened.value=true;return}dialogGeneration.value++;opened.value=false;stale.value=false;productVersions.clear();formAccess=identity();accessPermission=row?'metal:work-order:edit':'metal:work-order:add';const current=++epoch;++dependencyEpoch;opening.value=true;optionsLoading.value=true;openError.value=false;retryOpen=()=>{void open(row,taskType)}
 const valid=()=>!disposed&&current===epoch&&hasFormAccess()
 try{
  const draft=row?.id?(await orderDetail(row.id,valid,!actionPermission.has('metal:work-order:query'))).data:blank()
  if(!row?.id){draft.taskType=taskType;if(taskType==='RAW_PROCESSING'){draft.processingType='DIRECT';draft.customer='工厂自有'}}
  if(!valid())return
  if(!row?.id)draft.orderNo=(await reserveOrderNumber(draft.requestId!,valid)).data
  if(!valid())return
  const factoryList=(await orderOptions('factories',undefined,valid)).data
  if(!valid())return
  if(!row?.id&&!isAdmin.value)draft.factoryId=factoryList[0]?.id??''
  const dependent=draft.factoryId?await loadFactoryOptions(draft.factoryId,valid):[{data:[]},{data:[]},{data:[]},{data:[]},{data:[]}]
  if(!valid())return
  const productList=draft.productionLineId?(await orderOptions('products',{lineId:draft.productionLineId},valid)).data:[]
  if(!valid())return
  draft.taskType??='PRODUCTION';draft.items.forEach(item=>{item.key=createUUID();if(draft.taskType==='RAW_PROCESSING')for(const plan of item.plans)plan.theoreticalLoss??=plan.loss});if(draft.ownerId&&!dependent[1].data.some(o=>o.id===draft.ownerId))draft.ownerId=''
  for(const key of Object.keys(form))delete(form as unknown as Record<string,unknown>)[key]
  originalFactory=draft.factoryId;originalCustomerId=draft.customerId;originalCustomerSnapshot=draft.customerSnapshot;legacyCustomer=!!draft.id&&!draft.customerId;legacyProcessing=!!draft.id&&!draft.processingType
  availableMaterials.value=dependent[3].data;rawProcesses.value=dependent[4].data;customers.value=dependent[2].data as CustomerOption[]
  if(draft.customerId)draft.customerVersion=customers.value.find(c=>c.id===draft.customerId)?.version
  Object.assign(form,draft);factories.value=factoryList;lines.value=dependent[0].data;owners.value=dependent[1].data;availableProducts.value=productList;opened.value=true
  await nextTick();if(valid())formRef.value?.clearValidate()
 }catch{if(valid()){Object.assign(form,blank());lines.value=[];owners.value=[];availableProducts.value=[];openError.value=true}}finally{if(valid()){opening.value=false;optionsLoading.value=false}}
}
function customerContextCurrent(){return opened.value&&hasFormAccess()&&!saving.value&&!pendingSave.value&&!stale.value}
async function customerCreated(result:{id:string;factoryId:string;contextKey:number}){
 if(!customerContextCurrent()||result.factoryId!==form.factoryId||result.contextKey!==dialogGeneration.value)return
 await refreshCustomers()
 if(customerContextCurrent()&&result.factoryId===form.factoryId&&result.contextKey===dialogGeneration.value)changeCustomer(result.id)
}
async function refreshCustomers(){
 if(!hasFormAccess()||saving.value||optionsLoading.value||!form.factoryId)return
 const current=epoch,ticket=++dependencyEpoch,factory=form.factoryId;const valid=()=>current===epoch&&ticket===dependencyEpoch&&hasFormAccess()&&form.factoryId===factory
 optionsLoading.value=true
 try{const result=await orderCustomers(factory,valid);if(!valid())return;customers.value=result.data;const selected=result.data.find(c=>c.id===form.customerId);if(selected){form.customerVersion=selected.version;if(form.customerId!==originalCustomerId){form.customer=selected.name;form.customerSnapshot={...selected}}}else if(form.customerId)form.customerVersion=undefined}catch{}finally{if(valid())optionsLoading.value=false}
}
async function confirmReset(){if(!form.items.some(item=>item.productId||item.materialId||item.plans.length)&&!form.ownerId&&!form.productionLineId)return true;try{await ElMessageBox.confirm('变更工厂或产线将清空相关产品和计划时间，是否继续？','确认变更',{confirmButtonText:'继续',cancelButtonText:'取消',type:'warning'});return true}catch{return false}}
async function changeFactory(value:string){
 if(!hasFormAccess()||saving.value||customerBlocked.value||value===form.factoryId)return;const current=epoch,ticket=++dependencyEpoch;if(!await confirmReset()||disposed||current!==epoch||!hasFormAccess()||ticket!==dependencyEpoch)return
 optionsLoading.value=true;form.factoryId=value;form.customer='';form.customerId='';form.customerVersion=undefined;form.customerSnapshot=undefined;customers.value=[];processingChanged();form.productionLineId='';form.ownerId='';form.items=[blankItem()];availableProducts.value=[];lines.value=[];owners.value=[]
 try{const result=await loadFactoryOptions(value,()=>current===epoch&&ticket===dependencyEpoch&&hasFormAccess());if(!disposed&&current===epoch&&hasFormAccess()&&ticket===dependencyEpoch&&form.factoryId===value){lines.value=result[0].data;owners.value=result[1].data;customers.value=result[2].data as CustomerOption[];availableMaterials.value=result[3].data;rawProcesses.value=result[4].data}}catch{}finally{if(current===epoch&&ticket===dependencyEpoch)optionsLoading.value=false}
}
async function changeLine(value:string){
 if(!hasFormAccess()||saving.value||value===form.productionLineId)return;const current=epoch,ticket=++dependencyEpoch,factory=form.factoryId;if(form.items.some(i=>i.productId||i.materialId)&&!await confirmReset())return;if(disposed||current!==epoch||!hasFormAccess()||ticket!==dependencyEpoch)return
 optionsLoading.value=true;form.productionLineId=value;form.items=[blankItem()];availableProducts.value=[]
 try{const result=(await orderOptions('products',{lineId:value},()=>current===epoch&&ticket===dependencyEpoch&&hasFormAccess())).data;if(!disposed&&current===epoch&&hasFormAccess()&&ticket===dependencyEpoch&&factory===form.factoryId&&value===form.productionLineId)availableProducts.value=result}catch{}finally{if(current===epoch&&ticket===dependencyEpoch)optionsLoading.value=false}
}
async function changeProduct(item:WorkOrderItem,value:string){
 if(!hasFormAccess()||saving.value||item.productId===value&&item.plans.length)return;const current=epoch,ticket=++dependencyEpoch,line=form.productionLineId;optionsLoading.value=true;item.productId=value;item.plans=[];item.snapshot=undefined
 try{const data=(await orderProductPlans(value,line,()=>current===epoch&&ticket===dependencyEpoch&&hasFormAccess())).data;if(!disposed&&current===epoch&&hasFormAccess()&&ticket===dependencyEpoch&&line===form.productionLineId&&form.items.includes(item)&&item.productId===value){item.snapshot=data.snapshot;item.plans=data.plans;const version=data.version??availableProducts.value.find(p=>p.id===value)?.version;if(version!=null)productVersions.set(value,version)}}catch{}finally{if(current===epoch&&ticket===dependencyEpoch)optionsLoading.value=false}
}
async function submit(){if(!hasFormAccess()||saving.value||customerBlocked.value||stale.value&&!pendingSave.value||optionsLoading.value||!opened.value||!actionPermission.has(form.id?'metal:work-order:edit':'metal:work-order:add'))return;const current=epoch;saving.value=true;try{if(!await formRef.value?.validate().catch(()=>false)||disposed||current!==epoch||!hasFormAccess()||!actionPermission.has(form.id?'metal:work-order:edit':'metal:work-order:add'))return;if(!form.items.length||form.items.length>100){ElMessage.error('须填写1～100项明细');return}for(const [index,item]of form.items.entries()){item.quantity=raw.value?0:Number(item.quantity);if(raw.value?(!item.materialId||!!decimalError(item.plannedInputWeight,'计划投入净重',{positive:true})||!item.plans.length||item.plans.some(p=>!rawProcesses.value.some(o=>o.id===p.processId)||!!decimalError(p.theoreticalLoss,'理论损耗',{max:100}))||new Set(item.plans.map(p=>p.processId)).size!==item.plans.length):(!item.productId||!item.plans.length||!Number.isInteger(item.quantity))){ElMessage.error(raw.value?'请选择待处理原料、正数投入净重及不重复的前处理工序，损耗须在0至100之间':'第'+(index+1)+'项须选择配置工序的产品并填写整数数量');return}let previous='';for(const plan of item.plans){if(!plan.startTime||!plan.endTime||plan.startTime>=plan.endTime){ElMessage.error('第'+(index+1)+'项的工序开始时间须早于结束时间');return}if(previous&&plan.startTime<previous){ElMessage.error('后一工序不能早于前一工序结束');return}previous=plan.endTime}}
 const payload=pendingSave.value??JSON.parse(JSON.stringify(form)) as WorkOrder,added=!payload.id;submittedPayload=payload;await orderSave(payload,()=>current===epoch&&hasFormAccess());if(disposed||current!==epoch||!hasFormAccess())return;pendingSave.value=undefined;submittedPayload=undefined;ElMessage.success(added?'添加成功':'修改成功');opened.value=false;notifyInventoryChanged();emit('saved',added)}catch(error:any){if(!disposed&&current===epoch&&hasFormAccess()){if(submittedPayload&&typeof error.code!=='number')pendingSave.value=submittedPayload;else{pendingSave.value=undefined;if(/版本|刷新|重新加载/.test(error.message??''))stale.value=true}applyFieldErrors(formRef.value,error)}}finally{if(current===epoch)saving.value=false}}
async function probeDraft(context:InventoryRefreshContext){
 const round=epoch,dependency=dependencyEpoch,factory=form.factoryId,line=form.productionLineId
 const valid=()=>context.isCurrent()&&round===epoch&&dependency===dependencyEpoch&&hasFormAccess()&&opened.value&&!saving.value&&!optionsLoading.value&&factory===form.factoryId&&line===form.productionLineId
 if(!valid())return
 if(form.id){const response=await orderDetail(form.id,valid,!actionPermission.has('metal:work-order:query'),true);if(valid()&&(response.data.version!==form.version||response.data.canEdit===false))stale.value=true;return}
 if(!factory)return
 const [clientList,ownerList,lineList,productList,materialList,processList]=await Promise.all([orderCustomers(factory,valid,true),orderOptions('owners',{factoryId:factory},valid,true),orderOptions('lines',{factoryId:factory},valid,true),line?orderOptions('products',{lineId:line},valid,true):Promise.resolve({data:[] as OrderOption[]}),raw.value?orderOptions('materials',{factoryId:factory},valid,true):Promise.resolve({data:availableMaterials.value}),raw.value?orderOptions('raw-processes',{factoryId:factory},valid,true):Promise.resolve({data:rawProcesses.value})])
 if(!valid())return
 if(form.customerId&&!clientList.data.some(c=>c.id===form.customerId&&c.version===form.customerVersion)||form.ownerId&&!ownerList.data.some(o=>o.id===form.ownerId)||line&&!lineList.data.some(o=>o.id===line))stale.value=true
 for(const item of form.items)if(item.productId){const candidate=productList.data.find(p=>p.id===item.productId);if(!candidate||productVersions.has(item.productId)&&candidate.version!=null&&candidate.version!==productVersions.get(item.productId))stale.value=true}
 if(raw.value)for(const item of form.items){if(item.materialId&&JSON.stringify(materialList.data.find(m=>m.id===item.materialId))!==JSON.stringify(availableMaterials.value.find(m=>m.id===item.materialId))||item.plans.some(p=>p.processId&&!processList.data.some(option=>option.id===p.processId)))stale.value=true}
 customers.value=clientList.data;owners.value=ownerList.data;lines.value=lineList.data;availableProducts.value=productList.data;availableMaterials.value=materialList.data;rawProcesses.value=processList.data
}
async function reloadDraft(){if(saving.value)return;try{await ElMessageBox.confirm('重新加载会替换当前草稿，请先记录需要保留的修改。是否重新加载？','重新核对',{confirmButtonText:'重新加载',cancelButtonText:'保留草稿'});await open(form.id?{id:form.id} as WorkOrder:undefined,form.taskType)}catch{}}
const {failed:refreshFailed}=useInventoryRefresh(probeDraft,ref(false),{allowed:hasFormAccess,reset:invalidateSession},{paused:computed(()=>!opened.value||saving.value||opening.value||optionsLoading.value||!!pendingSave.value)})
onBeforeRouteLeave(()=>saving.value||customerBlocked.value||pendingSave.value?false:undefined);onBeforeRouteUpdate(()=>saving.value||customerBlocked.value||pendingSave.value?false:undefined)
defineExpose({open,invalidateSession,isOpen:computed(()=>opened.value||opening.value)})
</script>
<style scoped>
.work-order-fields{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));column-gap:24px}.work-order-fields>.el-form-item{min-width:0}.work-order-fields>.el-form-item :deep(.el-input),.work-order-fields>.el-form-item :deep(.el-select){width:100%;max-width:100%;min-width:0}.work-order-fields>:not(.el-form-item){grid-column:1/-1}.work-order-fields>.el-button{justify-self:start}.order-item{margin:16px 0 20px}
@media(max-width:760px){.work-order-fields{grid-template-columns:minmax(0,1fr)}}.order-item :deep(.el-form-item__label){white-space:nowrap}.order-item .el-button{margin-left:10px}.add-product{color:#577bef;border-color:#577bef}.plan-table{font-size:14px;--el-table-header-bg-color:#f4f4f4}.plan-table :deep(.el-input){width:220px}.plan-table :deep(th){height:44px}.time-description{font-size:14px;color:#50545c;margin:0 0 12px}.order-error{color:#c45656;font-size:14px;margin:10px}.order-dialog :deep(.el-select){width:100%}.order-dialog :deep(.el-select-dropdown__wrap){max-height:110px}.order-dialog :deep(.el-dialog__body){max-height:calc(var(--design-height,100vh) - 220px);overflow-y:auto}
</style>

<style scoped>
.order-dialog :deep(.el-table .cell),.order-dialog :deep(.el-table .el-input__inner){text-align:center}
</style>

<style>
.order-dialog .el-dialog__body{max-height:calc(var(--design-height,100vh) - 220px);overflow-y:auto;scrollbar-gutter:stable;padding-right:20px}.order-dialog .el-select-dropdown__wrap{max-height:110px}.order-dialog .plan-table th.el-table__cell{height:44px}.order-dialog .el-form-item__label{white-space:nowrap}
.work-order-factory-options .el-select-dropdown__wrap{max-height:min(340px,45vh)}
</style>
