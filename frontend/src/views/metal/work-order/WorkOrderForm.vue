<template>
 <el-dialog v-model="opened" :title="form.id?'编辑工单':'添加工单'" width="min(920px,94vw)" top="5vh" class="order-dialog" :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving">
  <el-form ref="formRef" :model="form" :rules="rules" label-width="120px" label-position="left" :disabled="saving||optionsLoading">
   <el-form-item label="单据编号"><el-input :model-value="form.orderNo" readonly/></el-form-item>
   <el-form-item label="客户" prop="customer"><el-input v-model="form.customer" maxlength="100" placeholder="请输入…"/></el-form-item>
   <el-form-item label="工厂" prop="factoryId"><el-select :model-value="form.factoryId" :disabled="!isAdmin" fit-input-width :teleported="false" @visible-change="resetSelectScroll" @change="changeFactory"><el-option v-for="option in factories" :key="option.id" :value="option.id" :label="option.name"/></el-select></el-form-item>
   <el-form-item label="产线" prop="productionLineId"><el-select :model-value="form.productionLineId" fit-input-width :teleported="false" :disabled="!form.factoryId" @visible-change="resetSelectScroll" @change="changeLine"><el-option v-for="option in lines" :key="option.id" :value="option.id" :label="option.name"/></el-select></el-form-item>
   <el-form-item label="开单日期" prop="orderDate"><el-date-picker v-model="form.orderDate" type="date" value-format="YYYY-MM-DD" format="YYYY-MM-DD" :teleported="true" placeholder="请选择日期"/></el-form-item>
   <el-form-item label="负责人" prop="ownerId"><el-select v-model="form.ownerId" filterable fit-input-width :teleported="false" :disabled="!form.factoryId" @visible-change="resetSelectScroll"><el-option v-for="option in owners" :key="option.id" :value="option.id" :label="ownerLabel(option)"/></el-select></el-form-item>
   <el-button class="add-product" :disabled="!form.productionLineId||form.items.length>=100" @click="form.items.push(blankItem())">添加产品</el-button>
   <div v-for="(item,index) in form.items" :key="item.key||item.id||index" class="order-item">
    <el-form-item :label="'选择产品'+(form.items.length>1?' '+(index+1):'')"><el-select :model-value="item.productId" fit-input-width :teleported="false" :disabled="!form.productionLineId" @visible-change="resetSelectScroll" @change="value=>changeProduct(item,value)"><el-option v-for="option in availableProducts" :key="option.id" :value="option.id" :label="option.name+(option.hasProcesses?'':'（未配置工序）')" :disabled="!option.hasProcesses"/></el-select><el-button v-if="form.items.length>1" link type="danger" @click="form.items.splice(index,1)">移除</el-button></el-form-item>
    <el-form-item label="产品数量" :prop="'items.'+index+'.quantity'" :rules="[{required:true,type:'number',min:1,max:1000000,message:'数量须为1～1000000的整数',trigger:'change'}]"><el-input v-model.number="item.quantity" inputmode="numeric" placeholder="请输入产品数量" style="width:170px"/></el-form-item>
    <div class="time-description">计划开始、结束时间</div><el-table :data="item.plans" border class="plan-table"><el-table-column label="工序名称" prop="processName" min-width="130"/><el-table-column label="开始时间" min-width="235"><template #default="{row}"><el-date-picker v-model="row.startTime" type="datetime" value-format="YYYY-MM-DD HH:mm:00" format="YYYY-MM-DD HH:mm" :teleported="true" placeholder="请选择开始时间"/></template></el-table-column><el-table-column label="结束时间" min-width="235"><template #default="{row}"><el-date-picker v-model="row.endTime" type="datetime" value-format="YYYY-MM-DD HH:mm:00" format="YYYY-MM-DD HH:mm" :teleported="true" placeholder="请选择结束时间"/></template></el-table-column></el-table>
    <div v-if="item.productId&&!item.plans.length" class="order-error">产品尚未配置工序，请先维护产品工艺</div>
   </div>
  </el-form>
  <template #footer><el-button :disabled="saving" @click="opened=false">取消</el-button><el-button type="primary" :loading="saving" @click="submit">确认</el-button></template>
 </el-dialog>
</template>
<script setup lang="ts">
import {createUUID} from '@/utils/uuid'
import {ref,reactive,computed,nextTick} from 'vue'
import {ElMessage,ElMessageBox} from 'element-plus'
import type {FormInstance,FormRules} from 'element-plus'
import useUserStore from '@/store/modules/user'
import {resetSelectScroll} from '@/utils/reset-select-scroll'
import {reserveOrderNumber,orderDetail,orderOptions,orderProductPlans,orderSave} from '@/api/metal/work-order'
import type {WorkOrder,WorkOrderItem,OrderOption} from '@/api/metal/work-order'
const emit=defineEmits<{saved:[added:boolean]}>(),user=useUserStore(),isAdmin=computed(()=>String(user.id)==='1')
const opened=ref(false),saving=ref(false),optionsLoading=ref(false),formRef=ref<FormInstance>()
const ownerLabel=(option:OrderOption)=>`${option.roleNames||'未分配角色'}｜${option.name}｜${option.phone||'未填写手机号'}`
const blankItem=():WorkOrderItem=>({key:createUUID(),productId:'',quantity:1,plans:[]})
const today=()=>new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Shanghai'}).format(new Date())
const blank=():WorkOrder=>({requestId:createUUID(),customer:'',factoryId:'',productionLineId:'',ownerId:'',orderDate:today(),items:[blankItem()]})
const form=reactive<WorkOrder>(blank()),factories=ref<OrderOption[]>([]),lines=ref<OrderOption[]>([]),owners=ref<OrderOption[]>([]),availableProducts=ref<OrderOption[]>([])
const required=(message:string)=>[{required:true,message,trigger:'change'}]
const rules:FormRules={customer:[{required:true,whitespace:true,message:'请输入客户',trigger:'blur'}],factoryId:required('请选择工厂'),productionLineId:required('请选择产线'),ownerId:required('请选择负责人'),orderDate:required('请选择开单日期')}
async function loadFactoryOptions(){const result=await Promise.all([orderOptions('lines',{factoryId:form.factoryId}),orderOptions('owners',{factoryId:form.factoryId})]);lines.value=result[0].data;owners.value=result[1].data}
async function open(row?:WorkOrder){optionsLoading.value=true;try{for(const k of Object.keys(form))delete(form as unknown as Record<string,unknown>)[k];Object.assign(form,blank());if(!row?.id)form.orderNo=(await reserveOrderNumber(form.requestId!)).data;factories.value=(await orderOptions('factories')).data;if(row?.id){Object.assign(form,(await orderDetail(row.id)).data);form.items.forEach(item=>{item.key=createUUID()})}else if(!isAdmin.value)form.factoryId=factories.value[0]?.id??'';if(form.factoryId)await loadFactoryOptions();else{lines.value=[];owners.value=[]}availableProducts.value=form.productionLineId?(await orderOptions('products',{lineId:form.productionLineId})).data:[];if(form.ownerId&&!owners.value.some(o=>o.id===form.ownerId))form.ownerId='';opened.value=true;await nextTick();formRef.value?.clearValidate()}catch{}finally{optionsLoading.value=false}}
async function confirmReset(){if(!form.items.some(item=>item.productId||item.plans.length)&&!form.ownerId&&!form.productionLineId)return true;try{await ElMessageBox.confirm('变更工厂或产线将清空相关产品和计划时间，是否继续？','确认变更',{confirmButtonText:'继续',cancelButtonText:'取消',type:'warning'});return true}catch{return false}}
async function changeFactory(value:string){if(value===form.factoryId||!await confirmReset())return;optionsLoading.value=true;try{form.factoryId=value;form.productionLineId='';form.ownerId='';form.items=[blankItem()];availableProducts.value=[];await loadFactoryOptions()}catch{}finally{optionsLoading.value=false}}
async function changeLine(value:string){if(value===form.productionLineId)return;if(form.items.some(item=>item.productId)&&!await confirmReset())return;optionsLoading.value=true;try{form.productionLineId=value;form.items=[blankItem()];availableProducts.value=(await orderOptions('products',{lineId:value})).data}catch{}finally{optionsLoading.value=false}}
async function changeProduct(item:WorkOrderItem,value:string){if(item.productId===value&&item.plans.length)return;optionsLoading.value=true;try{const data=(await orderProductPlans(value,form.productionLineId)).data;item.productId=value;item.snapshot=data.snapshot;item.plans=data.plans}catch{}finally{optionsLoading.value=false}}
async function submit(){if(saving.value||optionsLoading.value||!await formRef.value?.validate().catch(()=>false))return;if(!form.items.length||form.items.length>100){ElMessage.error('须填写1～100项产品');return}for(const [index,item]of form.items.entries()){if(!item.productId||!item.plans.length||!Number.isInteger(item.quantity)){ElMessage.error('第'+(index+1)+'项须选择配置工序的产品并填写整数数量');return}let previous='';for(const plan of item.plans){if(!plan.startTime||!plan.endTime||plan.startTime>=plan.endTime){ElMessage.error('第'+(index+1)+'项的工序开始时间须早于结束时间');return}if(previous&&plan.startTime<previous){ElMessage.error('后一工序不能早于前一工序结束');return}previous=plan.endTime}}
 saving.value=true;try{const added=!form.id;await orderSave(form);ElMessage.success(added?'添加成功':'修改成功');opened.value=false;emit('saved',added)}catch{}finally{saving.value=false}}
defineExpose({open})
</script>
<style scoped>
.order-item{margin:18px 0 25px 36px}.order-item :deep(.el-form-item__label){white-space:nowrap}.order-item .el-button{margin-left:10px}.add-product{color:#577bef;border-color:#577bef}.plan-table{font-size:14px;--el-table-header-bg-color:#f4f4f4}.plan-table :deep(.el-input){width:220px}.plan-table :deep(th){height:56px}.time-description{font-size:14px;color:#50545c;margin:0 0 12px}.order-error{color:#c45656;font-size:14px;margin:10px}.order-dialog :deep(.el-select){width:100%}.order-dialog :deep(.el-select-dropdown__wrap){max-height:110px}.order-dialog :deep(.el-dialog__body){max-height:calc(var(--design-height,100vh) - 220px);overflow-y:auto}
</style>

<style>
.order-dialog .el-dialog__body{max-height:calc(var(--design-height,100vh) - 220px);overflow-y:auto;scrollbar-gutter:stable;padding-right:20px}.order-dialog .el-select-dropdown__wrap{max-height:110px}.order-dialog .plan-table th.el-table__cell{height:56px}.order-dialog .el-form-item__label{white-space:nowrap}
</style>
