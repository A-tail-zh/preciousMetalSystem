<template><section class="permits"><div class="heading"><strong>工艺跳步许可配置</strong><el-select v-model="itemId" placeholder="选择工单产品明细" :disabled="busy||loading||visible||uncertain" @change="load"><el-option v-for="(item,index) in items" :key="item.id" :value="item.id" :label="`${item.snapshot?.name??'产品'} ${index+1}`"/></el-select><el-button :disabled="loading||busy||visible||uncertain||!itemId||plans.length<3||failed" @click="create">新增许可</el-button><el-button :disabled="loading||busy||visible||uncertain||!itemId" @click="load">刷新</el-button></div><p>许可按产品、规格和完整路线适用，仅支持本工单同明细跳步。跳过不产生报工，带跳步轨迹的余量不作为跨工单完整路线释放。</p><p v-if="itemId&&plans.length<3" role="status">当前路线不足三道工序，无需配置跳步许可。</p><el-alert v-if="failed||refreshFailed" title="工艺许可更新失败，当前显示可能过期，请重试" type="error" :closable="false"/><div class="scroll"><el-table :data="rows" v-loading="loading" border><el-table-column label="来源已完成工序" min-width="170"><template #default="{row}">{{stepName(row.sourceSequence)}}</template></el-table-column><el-table-column label="允许进入工序" min-width="170"><template #default="{row}">{{stepName(row.targetSequence)}}</template></el-table-column><el-table-column label="状态" min-width="100"><template #default="{row}">{{row.enabled?'启用':'停用'}}</template></el-table-column><el-table-column prop="version" label="版本" width="80"/><el-table-column prop="reason" label="配置原因" min-width="180"/><el-table-column label="操作" width="90"><template #default="{row}"><el-button :disabled="busy" link type="primary" @click="edit(row)">修改</el-button></template></el-table-column></el-table></div><el-dialog v-model="visible" title="工艺跳步许可" width="min(650px,96vw)" append-to-body :show-close="!busy&&!uncertain" :close-on-click-modal="false" :close-on-press-escape="!busy&&!uncertain" :before-close="done=>{if(!busy&&!uncertain)done()}"><el-form ref="permitForm" :model="draft" :rules="rules" scroll-to-error :disabled="busy||uncertain||needsReload" label-width="120px"><el-form-item label="来源工序" prop="sourceSequence" :rules="[requiredRule('来源工序',true,true),...([])]"><el-select v-model="draft.sourceSequence" placeholder="请选择来源工序" @change="sourceChanged"><el-option v-for="(step,index) in plans" :key="step.processId" :label="stepName(index+1)" :value="index+1" :disabled="index+3>plans.length"/></el-select></el-form-item><el-form-item label="目标工序" prop="targetSequence" :rules="[requiredRule('目标工序',true,true),...([])]"><el-select v-model="draft.targetSequence" placeholder="请选择至少跨过一道的目标工序"><el-option v-for="(step,index) in plans" :key="step.processId" :label="stepName(index+1)" :value="index+1" :disabled="index+1<=draft.sourceSequence+1"/></el-select></el-form-item><el-form-item label="启用"><el-switch v-model="draft.enabled"/></el-form-item><el-form-item label="配置原因" prop="reason" :rules="[requiredRule('配置原因',true,false),...([])]"><el-input v-model="draft.reason" type="textarea" maxlength="500" show-word-limit/></el-form-item></el-form><el-alert v-if="needsReload" title="资料已变化，请关闭后重新加载并核对，当前输入已保留" type="warning" :closable="false"/><el-alert v-if="uncertain" title="提交结果待确认，原请求和输入已保留，请重试原请求" type="warning" :closable="false"/><template #footer><el-button :disabled="busy||uncertain" @click="visible=false">取消</el-button><el-button type="primary" :loading="busy" :disabled="needsReload" @click="save">{{uncertain?'重试原请求':'保存许可'}}</el-button></template></el-dialog></section></template>
<script setup lang="ts">
import {requiredRule,fieldRule,textRules,applyFieldErrors} from '@/utils/input-validation'
import type {FormInstance,FormRules} from 'element-plus'
import {ref,reactive,computed,watch,onBeforeUnmount} from 'vue'
import {onBeforeRouteLeave,onBeforeRouteUpdate} from 'vue-router'
import {ElMessage} from 'element-plus'
import request from '@/utils/request'
import {createUUID} from '@/utils/uuid'
import {useInventoryRefresh,type InventoryRefreshContext} from '@/composables/useInventoryRefresh'
import {useActionPermission} from '@/composables/useActionPermission'
import {getToken} from '@/utils/auth'
import useUserStore from '@/store/modules/user'
type R=Record<string,any>
const props=defineProps<{workOrderId:string;items:R[]}>(),itemId=ref(''),rows=ref<R[]>([]),loading=ref(false),failed=ref(false),busy=ref(false),visible=ref(false),draft=reactive<R>({}),plans=computed(()=>props.items.find(i=>i.id===itemId.value)?.plans??[])
const user=useUserStore(),permission=useActionPermission(),needsReload=ref(false),uncertain=ref(false),permitForm=ref<FormInstance>()
let epoch=0,dialogEpoch=0,disposed=false,openedId='',openedVersion:number|undefined,openedRows='',openedRoute='',attempt:R|undefined
const canRead=()=>permission.has('metal:route-skip:configure')&&!!user.token&&user.token===getToken()
const identity=()=>JSON.stringify([user.sessionReady,user.token,String(user.id),[...user.permissions].sort()])
const rules:FormRules={sourceSequence:[fieldRule(v=>Number.isInteger(v)&&Number(v)>=1&&Number(v)<=plans.value.length-2?'':'请选择具有可跳目标的来源工序','change')],targetSequence:[fieldRule(v=>Number.isInteger(v)&&Number(v)>draft.sourceSequence+1&&Number(v)<=plans.value.length?'':'目标工序必须至少跨过一道工序','change')],reason:textRules('配置原因',500,true)}
function sourceChanged(){if(draft.targetSequence<=draft.sourceSequence+1)draft.targetSequence=undefined;permitForm.value?.clearValidate('targetSequence')}
function stepName(seq:number){return `第${seq}道·${plans.value[seq-1]?.processName??'工序'}`}
function resetAccess(){epoch++;dialogEpoch++;rows.value=[];loading.value=false;failed.value=false;busy.value=false;visible.value=false;needsReload.value=false;uncertain.value=false;attempt=undefined;Object.keys(draft).forEach(k=>delete draft[k])}
async function load(){if(visible.value||busy.value||uncertain.value)return false;return readPermits(false)}
async function readPermits(background:boolean,refresh?:InventoryRefreshContext){
 if(!canRead()){resetAccess();return false}if(loading.value||!itemId.value)return false
 const ticket=++epoch,work=props.workOrderId,item=itemId.value,account=identity(),valid=()=>!disposed&&ticket===epoch&&work===props.workOrderId&&item===itemId.value&&account===identity()&&canRead(),current=()=>valid()&&(!refresh||refresh.isCurrent()&&!busy.value&&!uncertain.value)
 loading.value=true;if(!background){rows.value=[];failed.value=false}
 try{const r:any=await request({url:'/metal/route-skip-permits',params:{itemId:item},isContextCurrent:current,quiet:background});if(!current())return false;rows.value=r.data;failed.value=false;return true}
 catch{if(current())failed.value=true;return false}finally{if(valid())loading.value=false}
}
async function probe(refresh:InventoryRefreshContext){const item=itemId.value,work=props.workOrderId,round=dialogEpoch,current=()=>refresh.isCurrent()&&canRead()&&visible.value&&!busy.value&&!uncertain.value&&item===itemId.value&&work===props.workOrderId&&round===dialogEpoch;const r:any=await request({url:'/metal/route-skip-permits',params:{itemId:item},isContextCurrent:current,quiet:true});if(current()&&(JSON.stringify(r.data)!==openedRows||JSON.stringify(plans.value)!==openedRoute))needsReload.value=true}
const {failed:refreshFailed,invalidate}=useInventoryRefresh(refresh=>readPermits(true,refresh),visible,{allowed:canRead,reset:resetAccess},{probe,paused:computed(()=>loading.value||busy.value||uncertain.value)})
function open(row?:R){if(!canRead()||busy.value||loading.value||uncertain.value||!itemId.value||plans.value.length<3||failed.value)return;dialogEpoch++;permitForm.value?.clearValidate();Object.keys(draft).forEach(k=>delete draft[k]);Object.assign(draft,row??{sourceSequence:1,targetSequence:3,enabled:true},{requestId:createUUID(),reason:''});openedId=row?.permitId??'';openedVersion=row?.version;openedRows=JSON.stringify(rows.value);openedRoute=JSON.stringify(plans.value);needsReload.value=false;attempt=undefined;visible.value=true}
function create(){open()}
function edit(row:R){open(row)}
async function save(){
 if(!canRead()){resetAccess();return}if(busy.value||!visible.value||needsReload.value)return
 if(uncertain.value&&attempt){await submit(attempt);return}
 const round=dialogEpoch,item=itemId.value,account=identity();if(!await permitForm.value?.validate().catch(()=>false)||round!==dialogEpoch||account!==identity()||!canRead()||!visible.value||item!==itemId.value||needsReload.value)return
 attempt=JSON.parse(JSON.stringify({...draft,permitId:openedId||undefined,version:openedVersion,reason:draft.reason.trim(),itemId:item}));await submit(attempt!)
}
async function submit(body:R){
 const round=dialogEpoch,item=itemId.value,work=props.workOrderId,account=identity(),current=()=>!disposed&&round===dialogEpoch&&account===identity()&&work===props.workOrderId&&item===itemId.value&&canRead()
 busy.value=true
 try{await request({url:'/metal/route-skip-permits'+(body.permitId?'/'+body.permitId:''),method:body.permitId?'put':'post',data:body,headers:{repeatSubmit:false},isContextCurrent:current});if(!current())return;uncertain.value=false;attempt=undefined;visible.value=false;busy.value=false;ElMessage.success('工艺许可已保存');invalidate()}
 catch(error:any){if(current()){uncertain.value=!!error?.isAxiosError&&(!error.response||error.response.status>=500);if(/版本|重新加载|刷新/.test(error?.message??''))needsReload.value=true;applyFieldErrors(permitForm.value,error)}}
 finally{if(current())busy.value=false}
}
watch(visible,value=>{if(!value&&!busy.value){dialogEpoch++;attempt=undefined}})
watch(()=>props.workOrderId,()=>{resetAccess();itemId.value=props.items[0]?.id??'';void load()},{immediate:true,flush:'sync'})
watch(()=>JSON.stringify(props.items),()=>{if(visible.value){if(JSON.stringify(plans.value)!==openedRoute)needsReload.value=true;return}if(!props.items.some(i=>i.id===itemId.value)){epoch++;loading.value=false;itemId.value=props.items[0]?.id??'';void load()}})
onBeforeRouteLeave(()=>{if(busy.value||uncertain.value)return false});onBeforeRouteUpdate(()=>{if(busy.value||uncertain.value)return false})
onBeforeUnmount(()=>{disposed=true;epoch++;dialogEpoch++})
</script><style scoped>.permits{margin-top:16px;padding:16px;background:#fff;border:1px solid #e4e7ed;border-radius:10px;min-width:0}.heading{display:flex;align-items:center;flex-wrap:wrap;gap:12px}.heading .el-select{width:250px}p{font-size:13px;color:#667085;line-height:1.7}.scroll{max-width:100%;overflow-x:auto}@media(max-width:480px){.permits{padding:10px}.heading .el-select{width:100%}}</style>

<style scoped>.permits :deep(th.el-table__cell),.permits :deep(td.el-table__cell){text-align:center}</style>
