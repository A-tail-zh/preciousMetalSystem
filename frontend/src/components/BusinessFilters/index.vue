<template>
 <span class="business-filter-controls">
  <el-popover :visible="open" trigger="click" placement="bottom-start" :width="'min(900px,92vw)'">
   <template #reference><el-button class="filter-toggle" :aria-expanded="open" @click="open=!open">筛选条件<span v-if="activeCount">（{{activeCount}}）</span></el-button></template>
   <section class="business-filter-panel" aria-label="筛选条件面板">
    <el-form label-position="top" @submit.prevent="apply">
     <el-form-item v-for="field in fields" :key="field.key" :label="field.label">
      <el-autocomplete v-if="!field.options&&!field.remote" v-model="draft[field.key]" clearable :placeholder="field.label" :fetch-suggestions="(keyword:string,callback:SuggestionCallback)=>{void suggest(field,keyword,callback)}" :trigger-on-focus="false" :debounce="300" @keydown.enter.capture="textEnter($event)"/>
      <el-select v-else v-model="draft[field.key]" clearable filterable :remote="!!field.remote" :remote-method="(keyword:string)=>search(field,keyword)" :loading="busy[field.key]" :placeholder="field.label" @visible-change="v=>v&&field.remote&&search(field,'')" @change="field.key==='factoryId'&&factoryChanged()">
       <el-option v-for="option in field.options??choices[field.key]??[]" :key="option.id" :label="option.name" :value="option.id"/>
       <template v-if="more[field.key]" #footer><el-button link type="primary" @click="search(field,keywords[field.key]??'',true)">加载更多</el-button></template>
      </el-select>
     </el-form-item>
     <el-form-item :label="kind==='warehouse-order'?'单据日期':'创建日期'" class="filter-date"><el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期"/></el-form-item>
    </el-form>
    <div class="filter-panel-actions"><el-button @click="reset">重置</el-button><el-button type="primary" @click="apply">查询</el-button><el-button @click="open=false">收起</el-button></div>
   </section>
  </el-popover>
  <el-button v-if="allowExport" :loading="exporting" @click="exportCurrent">导出</el-button>
 </span>
</template>
<script setup lang="ts">
import {computed,reactive,ref,watch,onBeforeUnmount} from 'vue'
import {ElMessage} from 'element-plus'
import request from '@/utils/request'
import {productDimensions,processTypes,materialTypes} from '@/views/metal/master-config'
import {orderTypeNames} from '@/api/metal/warehouse'
type SuggestionCallback=(rows:{value:string}[])=>void
type Option={id:string;name:string};type Field={key:string;label:string;remote?:string;options?:Option[]}
const props=defineProps<{kind:string;allowExport?:boolean;exporting?:boolean;orderKind?:string}>()
const emit=defineEmits<{apply:[Record<string,string>];export:[Record<string,string>]}>()
const open=ref(false),draft=reactive<Record<string,string>>({}),range=ref<string[]|null>([]),applied=ref<Record<string,string>>({})
const choices=reactive<Record<string,Option[]>>({}),busy=reactive<Record<string,boolean>>({}),more=reactive<Record<string,boolean>>({}),keywords=reactive<Record<string,string>>({}),pages=reactive<Record<string,number>>({}),epochs:Record<string,number>={}
const text=(key:string,label:string):Field=>({key,label}),remote=(key:string,label:string,source:string):Field=>({key,label,remote:source}),select=(key:string,label:string,values:string[]):Field=>({key,label,options:values.map(v=>({id:v,name:v}))})
const fields=computed<Field[]>(()=>{
 switch(props.kind){
 case 'factory':return [text('name','工厂名称'),text('address','详细地址')]
 case 'product':return [text('code','产品编号'),text('name','产品名称'),select('category','产品类别',Object.keys(productDimensions))]
 case 'material':return [text('code','物料编码'),select('materialType','物料类型',materialTypes),text('specification','规格型号')]
 case 'process':return [text('name','工序名称'),{key:'processType',label:'工序类型',options:processTypes.map((name,i)=>({id:String(i+1),name}))}]
 case 'line':return [text('code','产线编号'),text('name','产线名称'),remote('factoryId','所属工厂','factories'),remote('productId','关联产品','products')]
 case 'staff':return [remote('roleId','角色','roles'),remote('factoryId','所属工厂','factories')]
 case 'role':return [text('name','角色名称'),remote('factoryId','所属工厂','factories')]
 default:{const types=props.orderKind==='IN'?['RAW_IN','SEMI_IN','FINISHED_IN','OPENING']:props.orderKind==='RET'?['RAW_RETURN','PRODUCT_RETURN']:['PRODUCTION_OUT','PRODUCT_OUT','EXCHANGE_OUT','SEMI_OUT'];return [text('orderNo','单据编号'),text('workOrderNo','生产工单号'),remote('warehouseId','仓库','warehouses'),{key:'type',label:'类型',options:types.map(id=>({id,name:orderTypeNames[id]}))},remote('operatorId','经办人','people'),remote('recipientId','领用人','people'),remote('keeperId','仓管人','people')]}
 }
})
const suggestionEpochs:Record<string,number>={},pendingSuggestions:Record<string,SuggestionCallback>={}
function cancelSuggestions(){for(const key of Object.keys(pendingSuggestions)){suggestionEpochs[key]=(suggestionEpochs[key]??0)+1;pendingSuggestions[key]([]);delete pendingSuggestions[key]}}
async function suggest(field:Field,keyword:string,callback:SuggestionCallback){
 const key=field.key,current=suggestionEpochs[key]=(suggestionEpochs[key]??0)+1
 pendingSuggestions[key]?.([]);pendingSuggestions[key]=callback
 if(!keyword.trim()){callback([]);delete pendingSuggestions[key];return}
 try{const response=await request({url:'/metal/filter-options/suggestions',params:{module:props.kind,field:key,keyword,factoryId:draft.factoryId}}) as unknown as {rows:{value:string}[]};if(current===suggestionEpochs[key])callback(response.rows)}catch{if(current===suggestionEpochs[key])callback([])}finally{if(current===suggestionEpochs[key])delete pendingSuggestions[key]}
}
function textEnter(event:KeyboardEvent){const input=event.target as HTMLInputElement;if(input.getAttribute('aria-activedescendant'))return;apply()}
watch(open,value=>{if(!value)cancelSuggestions()})
watch(()=>draft.factoryId,()=>cancelSuggestions())
onBeforeUnmount(cancelSuggestions)
function values(){const result=Object.fromEntries(Object.entries(draft).filter(([,v])=>v!=null&&String(v).trim()!=='').map(([k,v])=>[k,String(v).trim()]));if(range.value?.length===2){result.startDate=range.value[0];result.endDate=range.value[1]}return result}
const canonical=(v:Record<string,string>)=>JSON.stringify(Object.entries(v).sort(([a],[b])=>a.localeCompare(b)))
const activeCount=computed(()=>Object.keys(applied.value).filter(k=>k!=='endDate').length)
function apply(){const next=values();if(next.startDate&&next.endDate&&next.startDate>next.endDate){ElMessage.warning('开始日期不能晚于结束日期');return}applied.value={...next};emit('apply',{...next});open.value=false}
function reset(){Object.keys(draft).forEach(k=>delete draft[k]);range.value=[];applied.value={};factoryChanged();emit('apply',{});open.value=false}
function exportCurrent(){if(canonical(values())!==canonical(applied.value)){ElMessage.warning('筛选条件已修改，请先点击查询');return}emit('export',{...applied.value})}
function factoryChanged(){for(const field of fields.value.filter(f=>['roles','warehouses','people'].includes(f.remote??''))){delete draft[field.key];choices[field.key]=[];more[field.key]=false;epochs[field.key]=(epochs[field.key]??0)+1}}
async function search(field:Field,keyword:string,append=false){if(!field.remote)return;const key=field.key,current=epochs[key]=(epochs[key]??0)+1;busy[key]=true;const pageNum=append?(pages[key]??1)+1:1;keywords[key]=keyword;try{const response=await request({url:'/metal/filter-options/'+field.remote,params:{module:props.kind,keyword,pageNum,factoryId:draft.factoryId}}) as unknown as {rows:Option[];hasMore:boolean};if(current!==epochs[key])return;const selected=choices[key]?.find(o=>o.id===draft[key]);const merged=append?[...(choices[key]??[]),...response.rows]:response.rows;if(selected&&!merged.some(o=>o.id===selected.id))merged.unshift(selected);choices[key]=merged;more[key]=response.hasMore;pages[key]=pageNum}catch{}finally{if(current===epochs[key])busy[key]=false}}
watch(()=>props.orderKind,()=>{delete draft.type;delete applied.value.type;emit('apply',{...applied.value})})
defineExpose({reset})
</script>
<style scoped>
.business-filter-controls{display:inline-flex;align-items:center;gap:12px;flex-shrink:0}.business-filter-controls .el-button{margin:0;min-width:89px;height:35px}.business-filter-panel{max-height:65vh;overflow:auto;padding:8px}.business-filter-panel .el-form{display:grid;grid-template-columns:repeat(auto-fit,minmax(190px,1fr));gap:0 16px}.business-filter-panel .el-form-item{min-width:0;margin-bottom:16px}.business-filter-panel .el-autocomplete{width:100%}.business-filter-panel .el-select{width:100%}.filter-date{grid-column:1/-1}.filter-date :deep(.el-date-editor){max-width:100%;width:100%}.filter-panel-actions{display:flex;justify-content:flex-end;gap:10px}.filter-panel-actions .el-button{margin:0}
</style>
