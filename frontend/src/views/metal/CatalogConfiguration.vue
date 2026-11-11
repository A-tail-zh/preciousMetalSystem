<template>
 <template v-if="kind==='material'">
  <el-form-item label="材料形态" prop="storageConfiguration.form" :rules="[{required:true,message:'请选择材料形态',trigger:'change'}]"><el-select v-model="config.form" aria-label="材料形态" :disabled="locked" @change="config.form==='POWDER'&&(config.measurementMode='WEIGHT')"><el-option v-for="(label,value) in forms" :key="value" :label="label" :value="value"/></el-select></el-form-item>
  <el-form-item label="计量方式" prop="storageConfiguration.measurementMode" :rules="[{required:true,message:'请选择计量方式',trigger:'change'}]"><el-select v-model="config.measurementMode" aria-label="计量方式" :disabled="locked||config.form==='POWDER'"><el-option label="计重" value="WEIGHT"/><el-option label="计件" value="PIECE"/></el-select></el-form-item>
 </template>
 <template v-else-if="kind==='product'">
  <el-divider>工序与产出配置</el-divider>
  <div class="route-table-wrap">
   <table class="route-table" aria-label="工序与产出配置">
    <colgroup><col class="route-number"/><col class="route-process"/><col class="route-loss"/><col class="route-mode"/><col class="route-category"/><col class="route-operation"/></colgroup>
    <thead><tr><th scope="col">序号</th><th scope="col">加工工序</th><th scope="col">理论损耗(%)</th><th scope="col">计量方式</th><th scope="col">产出类别</th><th scope="col">操作</th></tr></thead>
    <tbody v-for="(step,index) in form.processes" :key="index" class="route-step">
     <template v-if="config.steps?.[index]">
      <tr class="route-main">
       <td class="route-sequence">{{index+1}}</td>
       <td><el-select v-model="step.processId" :aria-label="`第${index+1}道加工工序`" placeholder="请选择" filterable :disabled="locked" @change="changed(index)"><el-option v-if="step.processId&&!processOptions.some(p=>String(p.id)===String(step.processId))" :value="step.processId" :label="step.processName||'工序资料不可用'" disabled/><el-option v-for="p in processOptions" :key="p.id" :value="String(p.id)" :label="p.name"/></el-select></td>
       <td><BusinessDecimalInput v-model="step.loss" :readonly="locked||step.lossUnit==='G_PER_PIECE'" :max="step.lossUnit==='G_PER_PIECE'?undefined:100" :aria-label="`第${index+1}道工序理论损耗（%）`"/><template v-if="step.lossUnit==='G_PER_PIECE'"><span class="legacy-loss">历史损耗（g/件）</span><el-button v-if="!locked" link @click="step.loss='';step.lossUnit='PERCENT'">重新配置百分比</el-button></template></td>
       <td><el-input v-if="config.steps[index].outputCategory==='RAW'" :aria-label="`第${index+1}道工序计量方式`" :model-value="modes[config.steps[index].measurementMode]||'待选物料'" readonly/><el-select v-else v-model="config.steps[index].measurementMode" :aria-label="`第${index+1}道工序计量方式`" :disabled="locked" @change="syncModes"><el-option label="计件" value="PIECE"/><el-option label="计重" value="WEIGHT"/></el-select></td>
       <td><el-input v-if="fixed(index)" :aria-label="`第${index+1}道工序产出类别`" :model-value="categories[config.steps[index].outputCategory]||'未配置'" readonly/><el-select v-else v-model="config.steps[index].outputCategory" :aria-label="`第${index+1}道工序产出类别`" :disabled="locked" @change="materialChanged(index)"><el-option v-for="(label,value) in categories" :key="value" :value="value" :label="label" :disabled="value==='PRODUCT'"/></el-select></td>
       <td><div class="route-actions"><el-button :icon="ArrowUp" size="small" :aria-label="`上移第${index+1}道工序`" title="上移" :disabled="locked||index===0" @click="move(index,-1)"/><el-button :icon="ArrowDown" size="small" :aria-label="`下移第${index+1}道工序`" title="下移" :disabled="locked||index===(form.processes?.length||0)-1" @click="move(index,1)"/><el-button :icon="Delete" size="small" type="danger" plain :aria-label="`删除第${index+1}道工序`" title="删除" :disabled="locked" @click="remove(index)"/></div></td>
      </tr>
      <tr v-if="config.steps[index].outputCategory==='RAW'" class="route-output-details"><td colspan="6"><div class="route-output-grid">
       <el-form-item label="产出物料" required><el-select v-model="config.steps[index].outputMaterialId" :aria-label="`第${index+1}道工序产出物料`" filterable :disabled="locked" no-data-text="本厂暂无匹配物料，请先在物料管理维护" @change="materialChanged(index)"><el-option v-if="config.steps[index].outputMaterialId&&!candidates(index).some(m=>m.id===String(config.steps[index].outputMaterialId))" :value="config.steps[index].outputMaterialId" :label="readonly&&config.steps[index].outputMaterialName?`${config.steps[index].outputMaterialCode} / ${config.steps[index].outputMaterialName}`:'原产出物料不可用，请重新选择'" disabled/><el-option v-for="m in candidates(index)" :key="m.id" :value="m.id" :label="`${m.code} / ${m.name}`"/></el-select></el-form-item>
       <el-form-item label="产出形态"><el-input :aria-label="`第${index+1}道工序产出形态`" :model-value="forms[config.steps[index].outputForm]||'未配置'" readonly/></el-form-item>
      </div></td></tr>
     </template>
    </tbody>
    <tbody v-if="!form.processes?.length"><tr><td colspan="6" class="route-empty">暂无工序，请添加工序</td></tr></tbody>
    <tfoot v-if="!readonly"><tr><td colspan="6"><el-button :icon="Plus" link type="primary" :disabled="locked" @click="add">添加工序</el-button></td></tr></tfoot>
   </table>
  </div>
 </template>
</template>
<script setup lang="ts">
import {computed,watch,onMounted} from 'vue'
import {ArrowUp,ArrowDown,Delete,Plus} from '@element-plus/icons-vue'
import BusinessDecimalInput from '@/components/BusinessDecimalInput.vue'
import type {MasterRow,Option} from '@/api/metal/master'
const props=defineProps<{kind:string;form:Partial<MasterRow>;processOptions:Option[];materials:Record<string,any>[];readonly:boolean}>()
const forms:Record<string,string>={POWDER:'粉状',UNTREATED:'未处理料',METAL:'金属料'},categories:Record<string,string>={RAW:'原材料',SEMI:'半成品',PRODUCT:'成品'},modes:Record<string,string>={WEIGHT:'计重',PIECE:'计件'}
const config=computed(()=>props.form.storageConfiguration!),locked=computed(()=>props.readonly||!!config.value?.locked)
function sameId(a:unknown,b:unknown){return a!=null&&b!=null&&String(a)===String(b)}
function initialize(){if(!props.form.storageConfiguration)props.form.storageConfiguration=props.kind==='product'?{steps:[]}:{form:null,measurementMode:null};if(props.kind==='product'){const prior=config.value.steps||[];(props.form.processes||[]).forEach(s=>{if(s.processId!=null)s.processId=String(s.processId)});config.value.steps=(props.form.processes||[]).map((s,i)=>({...prior[i],processId:s.processId?String(s.processId):'',sequenceNo:i+1,measurementMode:prior[i]?.measurementMode||s.measurementMode||'PIECE'}));}}
watch(()=>props.form.storageConfiguration,()=>{if(!props.form.storageConfiguration)initialize()},{immediate:true})
function special(){return config.value.steps.some((s:any)=>['ACID_WASH','SMELTING'].includes(s.semanticCode))}
function fixed(index:number){return special()||index===(props.form.processes?.length||0)-1}
function syncModes(){config.value.steps.forEach((s:any,i:number)=>{const step=props.form.processes?.[i];if(step){step.measurementMode=s.measurementMode;if(s.processId)step.processId=String(s.processId)}})}
function normalize(){if(!config.value?.steps||locked.value)return;let melted=false;const all=config.value.steps;all.forEach((s:any,i:number)=>{const processId=String(props.form.processes?.[i]?.processId||s.processId||'');const p=props.processOptions.find(p=>sameId(p.id,processId));if(p)s.semanticCode=p.semanticCode;s.sequenceNo=i+1;s.processId=processId});const hasSpecial=special();all.forEach((s:any,i:number)=>{melted ||=s.semanticCode==='SMELTING';const current=String(s.outputCategory||'');s.outputCategory=s.semanticCode==='ACID_WASH'?'RAW':s.semanticCode==='SMELTING'?'SEMI':i===all.length-1?'PRODUCT':hasSpecial?(melted?'SEMI':'RAW'):current==='RAW'?'RAW':'SEMI';s.measurementMode ||= props.form.processes?.[i]?.measurementMode || 'PIECE';if(s.outputCategory==='RAW')materialChanged(i)});syncModes()}
onMounted(()=>{initialize();normalize()})
watch(()=>[props.form.processes?.map(s=>String(s.processId||'')).join(','),props.processOptions.map(p=>`${p.id}:${p.semanticCode||''}`).join('|'),props.form.storageConfiguration?.locked],()=>{initialize();normalize()})
function changed(index:number){const processId=String(props.form.processes![index].processId||'');config.value.steps[index]={processId,measurementMode:props.form.processes![index].measurementMode||'PIECE'};normalize()}
function candidates(index:number){return props.materials.filter(m=>['WEIGHT','PIECE'].includes(m.measurementMode)&&['POWDER','UNTREATED','METAL'].includes(m.form)&&(config.value.steps[index].semanticCode!=='ACID_WASH'||m.form==='METAL'))}
function materialChanged(index:number){const s=config.value.steps[index];if(s.outputCategory!=='RAW')return;const m=candidates(index).find(m=>sameId(m.id,s.outputMaterialId));if(m){s.measurementMode=m.measurementMode;s.outputForm=m.form;s.outputMaterialId=String(m.id)}else{s.measurementMode=null;s.outputForm=null};syncModes()}
function add(){props.form.processes??=[];props.form.processes.push({processId:'',loss:'0',lossUnit:'PERCENT',measurementMode:'PIECE'});config.value.steps.push({processId:'',measurementMode:'PIECE'});normalize()}
function remove(index:number){props.form.processes!.splice(index,1);config.value.steps.splice(index,1);normalize()}
function move(index:number,offset:number){const list=props.form.processes!,route=config.value.steps;[list[index],list[index+offset]]=[list[index+offset],list[index]];[route[index],route[index+offset]]=[route[index+offset],route[index]];normalize()}
</script>
<style scoped>
.route-table-wrap{width:100%;overflow-x:auto;border:1px solid var(--el-border-color-light);border-radius:4px}.route-table{width:100%;min-width:740px;table-layout:fixed;border-collapse:collapse;font-size:13px}.route-number{width:48px}.route-process{width:21%}.route-loss{width:116px}.route-mode{width:98px}.route-category{width:104px}.route-operation{width:110px}.route-table th{background:var(--el-fill-color-light);font-weight:600;white-space:nowrap}.route-table th,.route-table td{padding:10px 7px;border-bottom:1px solid var(--el-border-color-lighter);text-align:left;vertical-align:top}.route-table th:first-child,.route-sequence{text-align:center!important}.route-sequence{line-height:32px;color:var(--el-text-color-secondary)}.route-table :deep(.el-select){width:100%}.route-table :deep(.el-input__inner){text-align:left}.route-actions{display:flex;gap:4px;padding-top:2px}.route-actions :deep(.el-button){width:28px;height:28px;padding:0;margin:0}.route-table tfoot td{border-bottom:0;padding:12px}.route-output-details td{background:var(--el-fill-color-lighter);padding:12px 16px}.route-output-grid{display:grid;grid-template-columns:minmax(240px,2fr) minmax(140px,1fr);gap:20px}.route-output-grid :deep(.el-form-item){margin-bottom:0}.route-empty{text-align:center!important;color:var(--el-text-color-secondary);padding:24px!important}.legacy-loss{display:block;font-size:12px;color:var(--el-text-color-secondary);margin-top:4px}
</style>
