<template>
<section class="quote-page">
 <el-form class="factory-form" label-width="100px" label-position="left"><el-form-item label="所属工厂"><el-select v-if="permission.isAdmin.value" class="factory-control" v-model="factoryId" :loading="factoryLoading" :disabled="factoryLoading||saving||piSaving||!!deletingId||uncertain" placeholder="请选择工厂" aria-label="所属工厂"><el-option v-for="f in factories" :key="f.id" :value="f.id" :label="f.name"/></el-select><el-input v-else class="factory-control" :model-value="factories.find(f=>f.id===factoryId)?.name??''" readonly :placeholder="factoryLoading?'正在加载工厂…':'未配置所属工厂'" aria-label="所属工厂"/></el-form-item></el-form>
 <p v-if="factoryError" class="factory-status" role="alert">工厂加载失败，<el-button link type="primary" :disabled="factoryLoading" @click="initialize">重新加载工厂</el-button></p>
 <p v-else-if="!factoryLoading&&!factories.length" class="factory-status" role="status">暂无可用工厂，请联系管理员。</p>
 <el-alert v-if="uncertain" title="提交结果待确认，请先核对本厂配置；核对前不能切厂或重复保存。" type="warning" :closable="false"/><el-button v-if="uncertain" @click="acknowledge">已核对，重新加载配置</el-button>
 <section class="volume-config">
  <h3>本厂体积参数</h3>
  <el-form ref="piForm" :model="volume" :rules="piRules" label-width="60px" label-position="left" class="volume-form" :disabled="piSaving||piLoading||!factoryId||uncertain||!canEdit" scroll-to-error>
   <el-form-item label="π值" prop="piValue"><BusinessDecimalInput v-model="volume.piValue" :integer="5" :fraction="15" inputmode="decimal" aria-label="π值"/></el-form-item>
   <el-form-item label="系数配置" label-width="100px" prop="curvedBottomFactor"><BusinessDecimalInput v-model="volume.curvedBottomFactor" inputmode="decimal" aria-label="系数配置"/></el-form-item>
   <el-form-item class="volume-save" label-width="0"><el-button v-if="canEdit" type="primary" :loading="piSaving" :disabled="piLoading||piError" @click="savePi">保存参数</el-button></el-form-item>
  </el-form>
  <p v-if="piError" role="alert">本厂体积参数加载失败，<el-button link type="primary" @click="loadPi">重新加载</el-button></p>
 </section>
 <div class="rules-heading"><h3>金属报价规则</h3><el-button class="list-action list-action--create" v-if="canEdit" type="primary" :disabled="loading||saving||!factoryId||uncertain||!!deletingId" @click="openRule()">增加报价</el-button></div>
 <div class="rules-list" v-loading="loading">
  <article v-for="row in configuredRows" :key="row.metalTypeId" class="rule-card" :aria-label="row.metalTypeName+'报价规则'">
   <header><h4>{{row.metalTypeName}}</h4><span>密度（g/cm³） <strong>{{decimalText(row.density)}}</strong></span></header>
   <dl><div v-for="field in fields.slice(1)" :key="field.key"><dt>{{field.label}}</dt><dd>{{decimalText(row[field.key])}}</dd></div></dl>
   <footer v-if="canEdit"><el-button class="list-action list-action--edit list-action--row" link type="primary" :disabled="saving||loading||!!deletingId" @click="openRule(row)">编辑</el-button><el-button class="list-action list-action--delete list-action--row" link type="danger" :loading="deletingId===row.metalTypeId" :disabled="saving||loading||!!deletingId" @click="removeRule(row)">删除</el-button></footer>
  </article>
  <p v-if="loadError" role="alert">配置加载失败，<el-button link type="primary" @click="loadMetals">重新加载</el-button></p>
  <el-empty v-else-if="!loading&&!configuredRows.length" description="暂无报价规则"/>
 </div>
 <el-dialog v-model="ruleVisible" :title="editing?'编辑报价规则':'添加报价规则'" width="min(1150px,94vw)" append-to-body destroy-on-close :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving" class="quote-rule-dialog" @opened="focusRule">
  <el-form ref="ruleForm" :model="form" :rules="rules" label-width="175px" label-position="left" class="rule-form" :disabled="saving||loading||!canEdit" scroll-to-error>
   <el-form-item label="金属类型" prop="metalTypeId"><MetalTypeSelect v-model="form.metalTypeId" :factory-id="factoryId" :disabled="saving||loading||!canEdit||editing" :allow-add="!editing" @update:model-value="selectMetal"/></el-form-item>
   <el-form-item v-for="field in fields" :key="field.key" :label="field.label" :prop="field.key"><BusinessDecimalInput v-model="form[field.key]" inputmode="decimal" :aria-label="field.label"/></el-form-item>
   <el-form-item class="save-row"><el-button v-if="canEdit" type="primary" :loading="saving" @click="save">保存配置</el-button><el-button :disabled="saving" @click="ruleVisible=false">取消</el-button></el-form-item>
  </el-form>
 </el-dialog>
</section>
</template>
<script setup lang="ts">
import BusinessDecimalInput from '@/components/BusinessDecimalInput.vue'
import {reactive,ref,computed,onMounted,onBeforeUnmount,watch} from 'vue'
import type {FormInstance} from 'element-plus'
import {ElMessage,ElMessageBox} from 'element-plus'
import request from '@/utils/request'
import {useActionPermission} from '@/composables/useActionPermission'
import useUserStore from '@/store/modules/user'
import {getToken} from '@/utils/auth'
import {fieldRule,decimalError,requiredRule,applyFieldErrors} from '@/utils/input-validation'
import {roundDecimal,decimalText} from '@/utils/business-decimal'
import MetalTypeSelect from '../MetalTypeSelect.vue'
const user=useUserStore(),permission=useActionPermission(),uncertain=ref(false),factoryId=ref(''),factories=ref<{id:string;name:string}[]>([]),factoryLoading=ref(false),factoryError=ref(false),canEdit=computed(()=>!!factoryId.value&&!uncertain.value&&permission.has('metal:quote-rule:edit'))
const fields=[{key:'density',label:'密度（g/cm³）',positive:true},{key:'powderPrice',label:'粉料加工单价（元/g）'},{key:'nonPowderPrice',label:'非粉料加工单价（元/g）'},{key:'shortagePrice',label:'欠料加工单价（元/g）'},{key:'lossPrice',label:'理论损失单价（元/g）'}]
const blank=()=>({metalTypeId:'',density:'',powderPrice:'',nonPowderPrice:'',shortagePrice:'',lossPrice:'',version:0})
const form=reactive<Record<string,any>>(blank()),rows=ref<Record<string,any>[]>([]),loading=ref(false),saving=ref(false),loadError=ref(false),ruleForm=ref<FormInstance>(),ruleVisible=ref(false),editing=ref(false),deletingId=ref('')
const volume=reactive({piValue:'',curvedBottomFactor:'',version:0}),piForm=ref<FormInstance>(),piLoading=ref(false),piSaving=ref(false),piError=ref(false)
const configuredRows=computed(()=>rows.value.filter(r=>r.configured==null?r.density!=null:Number(r.configured)===1))
const rules={metalTypeId:[requiredRule('金属类型',true,true)],...Object.fromEntries(fields.map(f=>[f.key,[fieldRule(v=>decimalError(v,f.label,{positive:f.positive}))]]))}
const piRules={piValue:[fieldRule(v=>decimalError(v,'π值',{positive:true,integer:5,fraction:15}))],curvedBottomFactor:[fieldRule(v=>decimalError(v,'系数配置',{positive:true}))]}
let epoch=0,metalRequest=0,piRequest=0,factoryRequest=0,disposed=false,ruleIdentity='',volumeIdentity=''
const identity=()=>JSON.stringify([user.sessionReady,user.token,String(user.id),[...user.permissions].sort(),factoryId.value])
const sessionCurrent=()=>!disposed&&user.sessionReady&&!!user.token&&user.token===getToken()
const editable=()=>sessionCurrent()&&permission.has('metal:quote-rule:edit')&&!!factoryId.value&&!uncertain.value
function selectMetal(){const id=form.metalTypeId,row=rows.value.find(r=>String(r.metalTypeId)===String(id));Object.assign(form,blank(),row??{},{metalTypeId:id});for(const f of fields)form[f.key]=row?.[f.key]??'';ruleForm.value?.clearValidate()}
function openRule(row?:Record<string,any>){
 if(saving.value||loading.value||deletingId.value||!editable())return
 ruleIdentity=identity();editing.value=!!row;Object.assign(form,blank());form.metalTypeId=row?.metalTypeId??'';selectMetal();ruleVisible.value=true
}
function focusRule(){ruleForm.value?.$el.querySelector('input[aria-label="密度（g/cm³）"]')?.focus()}
async function removeRule(row:Record<string,any>){
 if(saving.value||loading.value||deletingId.value||!editable())return
 const session=epoch,account=identity(),current=()=>sessionCurrent()&&account===identity()&&session===epoch&&editable();deletingId.value=row.metalTypeId
 try{
  await ElMessageBox.confirm('确认删除“'+row.metalTypeName+'”的报价规则？金属目录、产品及历史快照将保留。','删除报价规则',{confirmButtonText:'删除',cancelButtonText:'取消',type:'warning'})
  if(!current())return
  await request({url:'/metal/quote-rule/'+row.metalTypeId,method:'delete',data:{version:row.version},isContextCurrent:current})
  if(!current())return
  ElMessage.success('报价规则已删除');await loadMetals()
 }catch(e:any){if(current())uncertain.value=!!e?.isAxiosError&&(!e.response||e.response.status>=500)}finally{if(session===epoch)deletingId.value=''}
}
async function loadMetals(){if(!factoryId.value||!sessionCurrent())return;const session=epoch,account=identity(),ticket=++metalRequest,current=()=>sessionCurrent()&&account===identity()&&session===epoch&&ticket===metalRequest;loading.value=true;loadError.value=false;try{const r:any=await request({url:'/metal/quote-rule/list',params:{factoryId:factoryId.value},isContextCurrent:current});if(!current())return;rows.value=r.data}catch{if(current())loadError.value=true}finally{if(current())loading.value=false}}
async function loadPi(){if(!factoryId.value||!sessionCurrent())return;const session=epoch,account=identity(),ticket=++piRequest,current=()=>sessionCurrent()&&account===identity()&&session===epoch&&ticket===piRequest;piLoading.value=true;piError.value=false;try{const r:any=await request({url:'/metal/volume-rule',params:{factoryId:factoryId.value},isContextCurrent:current});if(current()){Object.assign(volume,r.data);volumeIdentity=account}}catch{if(current())piError.value=true}finally{if(current())piLoading.value=false}}
async function save(){if(saving.value||loading.value||!ruleVisible.value||!editable())return;const session=epoch,account=ruleIdentity,current=()=>sessionCurrent()&&account===identity()&&session===epoch&&editable();if(!current()||!await ruleForm.value?.validate().catch(()=>false)||!current())return;const payload={metalTypeId:form.metalTypeId,version:form.version,...Object.fromEntries(fields.map(f=>[f.key,roundDecimal(form[f.key])]))};saving.value=true;try{await request({url:'/metal/quote-rule',method:'put',data:payload,isContextCurrent:current});if(!current())return;ElMessage.success('金属配置已保存');ruleVisible.value=false;await loadMetals()}catch(e:any){if(current()){uncertain.value=!!e?.isAxiosError&&(!e.response||e.response.status>=500);applyFieldErrors(ruleForm.value,e)}}finally{if(!disposed&&session===epoch)saving.value=false}}
async function savePi(){if(piSaving.value||piLoading.value||piError.value||!editable())return;const session=epoch,account=volumeIdentity,current=()=>sessionCurrent()&&account===identity()&&session===epoch&&editable();if(!current()||!await piForm.value?.validate().catch(()=>false)||!current())return;piSaving.value=true;try{await request({url:'/metal/volume-rule',method:'put',params:{factoryId:factoryId.value},data:{piValue:roundDecimal(volume.piValue),curvedBottomFactor:roundDecimal(volume.curvedBottomFactor),version:volume.version},isContextCurrent:current});if(!current())return;ElMessage.success('本厂体积参数已保存');await loadPi()}catch(e:any){if(current()){uncertain.value=!!e?.isAxiosError&&(!e.response||e.response.status>=500);applyFieldErrors(piForm.value,e)}}finally{if(!disposed&&session===epoch)piSaving.value=false}}
function load(){void loadMetals();void loadPi()}
async function initialize(){
 if(factoryLoading.value||!sessionCurrent())return
 const account=identity(),ticket=++factoryRequest,current=()=>sessionCurrent()&&account===identity()&&ticket===factoryRequest;factoryLoading.value=true;factoryError.value=false
 try{const r:any=await request({url:'/metal/options/factories',isContextCurrent:current});if(!current())return;factories.value=r.data;if(!permission.isAdmin.value)factoryId.value=r.data[0]?.id??'';else load()}
 catch{if(current())factoryError.value=true}finally{if(!disposed&&ticket===factoryRequest)factoryLoading.value=false}
}
function acknowledge(){uncertain.value=false;ruleVisible.value=false;load()}
watch(factoryId,()=>{epoch++;metalRequest++;piRequest++;rows.value=[];ruleVisible.value=false;loading.value=false;piLoading.value=false;Object.assign(form,blank());Object.assign(volume,{piValue:'',curvedBottomFactor:'',version:0});load()})
permission.onChange(()=>{epoch++;factoryRequest++;factoryLoading.value=false;factoryError.value=false;ruleVisible.value=false;deletingId.value='';Object.assign(form,blank());Object.assign(volume,{piValue:'',curvedBottomFactor:'',version:0});rows.value=[];saving.value=false;piSaving.value=false;loading.value=false;piLoading.value=false;factoryId.value='';factories.value=[];uncertain.value=false;if(permission.has('metal:quote-rule:list'))void initialize()});onMounted(initialize);onBeforeUnmount(()=>{disposed=true;epoch++})
</script>
<style scoped>
.quote-page{background:var(--business-canvas);padding:16px;min-height:calc(var(--design-height,100vh) - 56px);box-sizing:border-box}h3{font-size:16px;margin:0}.factory-form :deep(.el-form-item__content){min-width:0}.factory-control{width:240px;max-width:100%}.factory-status{font-size:13px;color:#606266;margin:0 0 18px}.volume-config{max-width:1100px;box-sizing:border-box;margin-bottom:32px}.volume-config h3{margin-bottom:28px}.volume-form,.rule-form{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:0 28px;align-items:start}.volume-form{grid-template-columns:minmax(0,200px) minmax(0,280px) max-content;column-gap:40px}.volume-save{margin-bottom:0}.rule-form{padding:8px}.quote-rule-dialog :deep(.el-dialog__body){max-height:calc(100vh - 180px);overflow-y:auto}.rule-form .el-form-item,.volume-form .el-form-item{min-width:0}.rule-form :deep(.el-form-item__content),.volume-form :deep(.el-form-item__content){min-width:0}.save-row{grid-column:1/-1;margin-bottom:0}.rules-heading{display:flex;align-items:center;gap:24px;margin-bottom:16px}.rules-list{display:grid;grid-template-columns:repeat(auto-fill,minmax(min(100%,480px),1fr));gap:16px}.rule-card{padding:20px 24px;background:#fff;border:1px solid var(--business-border);border-radius:8px;min-width:0}.rule-card header{display:flex;align-items:baseline;justify-content:space-between;gap:16px;flex-wrap:wrap}.rule-card h4{font-size:16px;margin:0;overflow-wrap:anywhere}.rule-card header span{font-size:13px;color:#606266}.rule-card strong{font-weight:500;color:#303133;margin-left:8px}.rule-card dl{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px 24px;margin:20px 0 0}.rule-card dt{font-size:13px;color:#606266}.rule-card dd{font-size:16px;margin:6px 0 0;font-variant-numeric:tabular-nums;overflow-wrap:anywhere}.rule-card footer{display:flex;gap:20px;margin-top:18px}.rule-card footer .el-button+.el-button{margin-left:0}.volume-config p,.rules-list p{color:#606266;font-size:13px;line-height:1.6}@media(max-width:900px){.rule-form{grid-template-columns:minmax(0,1fr)}}@media(max-width:740px){.volume-form{grid-template-columns:minmax(0,1fr)}}@media(max-width:480px){.quote-page{padding:12px}.rule-card{padding:16px}.rule-card dl{grid-template-columns:minmax(0,1fr)}.quote-page :deep(.el-form-item),.rule-form .el-form-item{display:block}.quote-page :deep(.el-form-item__label),.rule-form :deep(.el-form-item__label){display:block;float:none;width:auto!important;line-height:30px}.quote-page :deep(.el-form-item__content),.rule-form :deep(.el-form-item__content){margin-left:0!important}}
</style>
